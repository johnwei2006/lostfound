package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.entity.MatchRecord;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.LostMapper;
import com.lostfound.mapper.MatchRecordMapper;
import com.lostfound.vo.ItemVO;
import com.lostfound.vo.MatchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能匹配服务：
 * 候选集仍按「类别完全相同」硬过滤，排序区分度交给三个连续衰减维度：
 * 类别(30，同类别固定满分) + 时间接近度(25，<=3 天满分，3~30 天线性衰减)
 * + 空间距离(25，<=2 km 满分，2~50 km 线性衰减) + 关键词相似度(20)。
 * 得分 >= {@link #THRESHOLD} 写入 match_record，并向双方发送站内消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final double SCORE_CATEGORY = 30;
    private static final double SCORE_KEYWORD = 20;
    private static final double SCORE_DISTANCE = 25;
    private static final double SCORE_TIME = 25;
    private static final double THRESHOLD = 45;
    private static final int MAX_MATCHES = 10;

    /** 时间接近度满分区间（天），之后线性衰减到 {@link #TIME_FALLOFF_DAYS} 归零 */
    private static final long TIME_FULL_DAYS = 3;
    private static final long TIME_FALLOFF_DAYS = 30;
    /** 距离接近度满分区间（公里），之后线性衰减到 {@link #DIST_FALLOFF_KM} 归零 */
    private static final double DIST_FULL_KM = 2;
    private static final double DIST_FALLOFF_KM = 50;
    /** 缺失坐标/时间不加分：避免「纯类别匹配」在信息缺失时自动入围 */
    private static final double MISSING_FACTOR = 0;

    /** 一次打分的完整分解：总分、关键词相似度、公里距离（可空）、天数差（可空） */
    record MatchScore(double total, double keyword, Double km, Long days) {}

    private final FoundMapper foundMapper;
    private final LostMapper lostMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final MessageService messageService;

    /** 拾物发布后，寻找可能的失物 */
    public void triggerOnFound(Found found) {
        List<Lost> candidates = lostMapper.selectList(new QueryWrapper<Lost>()
                .eq("category", found.getCategory())
                .eq("status", Lost.STATUS_WAITING)
                .orderByDesc("create_time")
                .last("limit 200"));
        int saved = 0;
        for (Lost lost : candidates) {
            MatchScore s = score(found, lost);
            if (s.total() < THRESHOLD || saved >= MAX_MATCHES) continue;
            saveMatch(found, lost, s, true);
            saved++;
        }
        if (saved > 0) {
            log.info("拾物[{}] 触发匹配 {} 条", found.getId(), saved);
        }
    }

    /** 失物发布后，寻找可能的拾物 */
    public void triggerOnLost(Lost lost) {
        List<Found> candidates = foundMapper.selectList(new QueryWrapper<Found>()
                .eq("category", lost.getCategory())
                .eq("status", Found.STATUS_WAITING)
                .orderByDesc("create_time")
                .last("limit 200"));
        int saved = 0;
        for (Found found : candidates) {
            MatchScore s = score(found, lost);
            if (s.total() < THRESHOLD || saved >= MAX_MATCHES) continue;
            saveMatch(found, lost, s, true);
            saved++;
        }
        if (saved > 0) {
            log.info("失物[{}] 触发匹配 {} 条", lost.getId(), saved);
        }
    }

    private MatchScore score(Found found, Lost lost) {
        return computeDetail(
                titleDesc(found.getTitle(), found.getDescription()),
                titleDesc(lost.getTitle(), lost.getDescription()),
                found.getLongitude(), found.getLatitude(),
                lost.getLongitude(), lost.getLatitude(),
                found.getFoundTime() != null ? found.getFoundTime() : found.getCreateTime(),
                lost.getLostTime() != null ? lost.getLostTime() : lost.getCreateTime());
    }

    /**
     * 保存一条匹配记录（唯一键防重，已存在时静默跳过）。
     * @param notify 是否向双方发送站内消息；存量回刷传 false，避免消息轰炸
     * @return true 表示新建成功，false 表示记录已存在
     */
    private boolean saveMatch(Found found, Lost lost, MatchScore s, boolean notify) {
        MatchRecord record = new MatchRecord();
        record.setFoundId(found.getId());
        record.setLostId(lost.getId());
        record.setScore(BigDecimal.valueOf(s.total()).setScale(2, RoundingMode.HALF_UP));
        record.setReason(buildReason(found.getTitle(), lost.getTitle(), s));
        record.setStatus(MatchRecord.STATUS_UNREAD);
        record.setCreateTime(LocalDateTime.now());
        try {
            matchRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return false; // 已存在匹配记录
        }
        if (notify) {
            String text = String.format("发现一条疑似匹配：拾物「%s」与失物「%s」相似度 %.0f%%，请查看详情确认。",
                    found.getTitle(), lost.getTitle(), s.total());
            messageService.send(found.getUserId(), "匹配推荐", text, "MATCH", record.getId());
            messageService.send(lost.getUserId(), "匹配推荐", text, "MATCH", record.getId());
        }
        return true;
    }

    private String buildReason(String foundTitle, String lostTitle, MatchScore s) {
        StringBuilder sb = new StringBuilder("同类物品");
        if (s.days() != null) {
            if (s.days() == 0) {
                sb.append("，拾获与丢失发生在同一天");
            } else {
                sb.append("，拾获与丢失时间相差 ").append(s.days()).append(" 天");
            }
        } else {
            sb.append("，时间信息不全");
        }
        if (s.km() != null) {
            sb.append(String.format("，地点相距约 %.1f 公里", s.km()));
        } else {
            sb.append("，缺少位置信息");
        }
        if (foundTitle != null && lostTitle != null && !foundTitle.isBlank() && !lostTitle.isBlank()) {
            if (s.keyword() >= 0.5) {
                sb.append("，标题与描述高度相似");
            } else if (s.keyword() >= 0.2) {
                sb.append("，描述部分相似");
            }
        }
        return sb.append("。").toString();
    }

    /** 综合得分计算（兼容旧测试入口） */
    double computeScore(String textA, String textB,
                        BigDecimal lngA, BigDecimal latA, BigDecimal lngB, BigDecimal latB,
                        LocalDateTime timeA, LocalDateTime timeB) {
        return computeDetail(textA, textB, lngA, latA, lngB, latB, timeA, timeB).total();
    }

    /** 完整打分：类别满分底座 + 关键词/距离/时间三个连续衰减维度 */
    MatchScore computeDetail(String textA, String textB,
                             BigDecimal lngA, BigDecimal latA, BigDecimal lngB, BigDecimal latB,
                             LocalDateTime timeA, LocalDateTime timeB) {
        double score = SCORE_CATEGORY; // 候选已按类别过滤，固定满分底座

        // 关键词相似度
        double kw = bigramOverlap(textA, textB);
        score += SCORE_KEYWORD * kw;

        // 空间距离：<=2km 满分，2~50km 线性衰减，缺失坐标给中性分
        Double km = null;
        if (lngA != null && latA != null && lngB != null && latB != null) {
            km = haversine(latA.doubleValue(), lngA.doubleValue(), latB.doubleValue(), lngB.doubleValue());
            score += SCORE_DISTANCE * distFactor(km);
        } else {
            score += SCORE_DISTANCE * MISSING_FACTOR;
        }

        // 时间接近度：<=3 天满分，3~30 天线性衰减，缺失时间给中性分
        Long days = null;
        if (timeA != null && timeB != null) {
            days = Math.abs(ChronoUnit.DAYS.between(timeA, timeB));
            score += SCORE_TIME * timeFactor(days);
        } else {
            score += SCORE_TIME * MISSING_FACTOR;
        }
        return new MatchScore(Math.min(score, 100), kw, km, days);
    }

    /** 距离得分系数：<=2km 满分，随后到 50km 线性衰减为 0 */
    double distFactor(double km) {
        if (km <= DIST_FULL_KM) return 1;
        if (km >= DIST_FALLOFF_KM) return 0;
        return (DIST_FALLOFF_KM - km) / (DIST_FALLOFF_KM - DIST_FULL_KM);
    }

    /** 时间得分系数：<=3 天满分，随后到 30 天线性衰减为 0 */
    double timeFactor(long days) {
        if (days <= TIME_FULL_DAYS) return 1;
        if (days >= TIME_FALLOFF_DAYS) return 0;
        return (double) (TIME_FALLOFF_DAYS - days) / (TIME_FALLOFF_DAYS - TIME_FULL_DAYS);
    }

    /** 2-gram 相似度（适合中文短文本） */
    double bigramOverlap(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0;
        Set<String> ga = grams(a);
        Set<String> gb = grams(b);
        if (ga.isEmpty() || gb.isEmpty()) return 0;
        long overlap = ga.stream().filter(gb::contains).count();
        return (double) overlap / Math.min(ga.size(), gb.size());
    }

    private Set<String> grams(String text) {
        String clean = text.replaceAll("\\s+", "");
        Set<String> set = new HashSet<>();
        for (int i = 0; i < clean.length() - 1; i++) {
            set.add(clean.substring(i, i + 2));
        }
        return set;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private String titleDesc(String title, String desc) {
        return (title == null ? "" : title) + " " + (desc == null ? "" : desc);
    }

    // ------------------------------------------------------------------
    // 存量回刷
    // ------------------------------------------------------------------

    /**
     * ADMIN：对全部 WAITING 物品按当前算法重跑匹配。
     * 幂等：命中唯一键的记录静默跳过，旧低分记录保留不删；回刷不发送消息，避免打扰用户。
     */
    public Map<String, Integer> rebuildAll() {
        if (!UserContext.isAdmin()) {
            throw new BusinessException(403, "仅平台管理员可执行匹配回刷");
        }
        List<Found> founds = foundMapper.selectList(new QueryWrapper<Found>()
                .eq("status", Found.STATUS_WAITING)
                .orderByDesc("create_time"));
        List<Lost> losts = lostMapper.selectList(new QueryWrapper<Lost>()
                .eq("status", Lost.STATUS_WAITING)
                .orderByDesc("create_time"));
        Map<String, List<Lost>> byCategory = losts.stream()
                .filter(l -> l.getCategory() != null)
                .collect(Collectors.groupingBy(Lost::getCategory));

        int considered = 0;
        int created = 0;
        int skipped = 0;
        for (Found found : founds) {
            for (Lost lost : byCategory.getOrDefault(found.getCategory(), List.of())) {
                MatchScore s = score(found, lost);
                if (s.total() < THRESHOLD) continue;
                considered++;
                if (saveMatch(found, lost, s, false)) {
                    created++;
                } else {
                    skipped++;
                }
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("waitingFound", founds.size());
        result.put("waitingLost", losts.size());
        result.put("considered", considered);
        result.put("created", created);
        result.put("skippedExisting", skipped);
        log.info("匹配回刷完成：WAITING 拾物 {} 条、失物 {} 条，入围 {}，新建 {}，已存在 {}",
                founds.size(), losts.size(), considered, created, skipped);
        return result;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 与我相关的匹配推荐 */
    public List<MatchVO> myMatches(Long userId) {
        List<Long> myFoundIds = foundMapper.selectList(new QueryWrapper<Found>()
                        .eq("user_id", userId).select("id"))
                .stream().map(Found::getId).toList();
        List<Long> myLostIds = lostMapper.selectList(new QueryWrapper<Lost>()
                        .eq("user_id", userId).select("id"))
                .stream().map(Lost::getId).toList();
        if (myFoundIds.isEmpty() && myLostIds.isEmpty()) return List.of();

        QueryWrapper<MatchRecord> qw = new QueryWrapper<>();
        qw.and(w -> {
            if (!myFoundIds.isEmpty()) w.or().in("found_id", myFoundIds);
            if (!myLostIds.isEmpty()) w.or().in("lost_id", myLostIds);
        }).orderByDesc("create_time").last("limit 50");
        List<MatchRecord> records = matchRecordMapper.selectList(qw);

        Set<Long> foundIds = records.stream().map(MatchRecord::getFoundId).collect(Collectors.toSet());
        Set<Long> lostIds = records.stream().map(MatchRecord::getLostId).collect(Collectors.toSet());
        Map<Long, Found> foundMap = foundIds.isEmpty() ? Map.of()
                : foundMapper.selectBatchIds(foundIds).stream().collect(Collectors.toMap(Found::getId, f -> f));
        Map<Long, Lost> lostMap = lostIds.isEmpty() ? Map.of()
                : lostMapper.selectBatchIds(lostIds).stream().collect(Collectors.toMap(Lost::getId, l -> l));

        return records.stream().map(r -> {
            MatchVO vo = new MatchVO();
            vo.setId(r.getId());
            vo.setScore(r.getScore());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setCreateTime(r.getCreateTime());
            Found f = foundMap.get(r.getFoundId());
            Lost l = lostMap.get(r.getLostId());
            if (f != null) vo.setFound(toBrief("FOUND", f.getId(), f.getTitle(), f.getImages(),
                    f.getCategory(), f.getAddress(), f.getStatus(), f.getCreateTime()));
            if (l != null) vo.setLost(toBrief("LOST", l.getId(), l.getTitle(), l.getImages(),
                    l.getCategory(), l.getAddress(), l.getStatus(), l.getCreateTime()));
            return vo;
        }).collect(Collectors.toList());
    }

    private ItemVO toBrief(String type, Long id, String title, String images, String category,
                           String address, String status, LocalDateTime createTime) {
        ItemVO vo = new ItemVO();
        vo.setType(type);
        vo.setId(id);
        vo.setTitle(title);
        vo.setCategory(category);
        vo.setAddress(address);
        vo.setStatus(status);
        vo.setCreateTime(createTime);
        if (images != null && !images.isBlank()) {
            try {
                vo.setImages(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(images, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                        }));
            } catch (Exception ignored) {
            }
        }
        return vo;
    }

    public void setStatus(Long matchId, Long userId, String status) {
        MatchRecord r = matchRecordMapper.selectById(matchId);
        if (r == null) throw new BusinessException("匹配记录不存在");
        Found f = foundMapper.selectById(r.getFoundId());
        Lost l = lostMapper.selectById(r.getLostId());
        boolean participant = (f != null && Objects.equals(f.getUserId(), userId))
                || (l != null && Objects.equals(l.getUserId(), userId));
        if (!participant) throw new BusinessException(403, "无权操作该匹配记录");
        r.setStatus(status);
        matchRecordMapper.updateById(r);
    }
}
