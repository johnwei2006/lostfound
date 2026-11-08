package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * 发布失物/拾物后按 类别(40) + 关键词相似度(30) + 空间距离(20) + 时间接近度(10)
 * 计算匹配得分，>=50 分写入 match_record 并向双方发送站内消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final double SCORE_CATEGORY = 40;
    private static final double SCORE_KEYWORD = 30;
    private static final double SCORE_DISTANCE = 20;
    private static final double SCORE_TIME = 10;
    private static final double THRESHOLD = 50;
    private static final int MAX_MATCHES = 10;

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
            double score = computeScore(
                    titleDesc(found.getTitle(), found.getDescription()),
                    titleDesc(lost.getTitle(), lost.getDescription()),
                    found.getLongitude(), found.getLatitude(),
                    lost.getLongitude(), lost.getLatitude(),
                    found.getFoundTime() != null ? found.getFoundTime() : found.getCreateTime(),
                    lost.getLostTime() != null ? lost.getLostTime() : lost.getCreateTime());
            if (score < THRESHOLD || saved >= MAX_MATCHES) continue;
            saveMatch(found.getId(), lost.getId(), score, found.getUserId(), lost.getUserId(), found.getTitle(), lost.getTitle());
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
            double score = computeScore(
                    titleDesc(found.getTitle(), found.getDescription()),
                    titleDesc(lost.getTitle(), lost.getDescription()),
                    found.getLongitude(), found.getLatitude(),
                    lost.getLongitude(), lost.getLatitude(),
                    found.getFoundTime() != null ? found.getFoundTime() : found.getCreateTime(),
                    lost.getLostTime() != null ? lost.getLostTime() : lost.getCreateTime());
            if (score < THRESHOLD || saved >= MAX_MATCHES) continue;
            saveMatch(found.getId(), lost.getId(), score, found.getUserId(), lost.getUserId(), found.getTitle(), lost.getTitle());
            saved++;
        }
        if (saved > 0) {
            log.info("失物[{}] 触发匹配 {} 条", lost.getId(), saved);
        }
    }

    private void saveMatch(Long foundId, Long lostId, double score,
                           Long foundUserId, Long lostUserId,
                           String foundTitle, String lostTitle) {
        MatchRecord record = new MatchRecord();
        record.setFoundId(foundId);
        record.setLostId(lostId);
        record.setScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        record.setReason(buildReason(foundTitle, lostTitle, score));
        record.setStatus(MatchRecord.STATUS_UNREAD);
        record.setCreateTime(LocalDateTime.now());
        try {
            matchRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return; // 已存在匹配记录
        }
        String text = String.format("发现一条疑似匹配：拾物「%s」与失物「%s」相似度 %.0f%%，请查看详情确认。",
                foundTitle, lostTitle, score);
        messageService.send(foundUserId, "匹配推荐", text, "MATCH", record.getId());
        messageService.send(lostUserId, "匹配推荐", text, "MATCH", record.getId());
    }

    private String buildReason(String foundTitle, String lostTitle, double score) {
        StringBuilder sb = new StringBuilder("同类物品，");
        if (score >= 80) {
            sb.append("标题与描述高度相似");
        } else if (score >= 65) {
            sb.append("描述较为相似");
        } else {
            sb.append("存在部分相似特征");
        }
        return sb.append("。").toString();
    }

    /** 综合得分计算 */
    double computeScore(String textA, String textB,
                        BigDecimal lngA, BigDecimal latA, BigDecimal lngB, BigDecimal latB,
                        LocalDateTime timeA, LocalDateTime timeB) {
        double score = SCORE_CATEGORY; // 候选已按类别过滤

        // 关键词相似度
        double kw = bigramOverlap(textA, textB);
        score += SCORE_KEYWORD * kw;

        // 空间距离
        if (lngA != null && latA != null && lngB != null && latB != null) {
            double km = haversine(latA.doubleValue(), lngA.doubleValue(), latB.doubleValue(), lngB.doubleValue());
            if (km <= 1) score += SCORE_DISTANCE;
            else if (km <= 5) score += SCORE_DISTANCE * 0.75;
            else if (km <= 20) score += SCORE_DISTANCE * 0.5;
            else if (km <= 50) score += SCORE_DISTANCE * 0.25;
        } else {
            score += SCORE_DISTANCE * 0.5; // 缺失坐标给中性分
        }

        // 时间接近度
        if (timeA != null && timeB != null) {
            long days = Math.abs(ChronoUnit.DAYS.between(timeA, timeB));
            if (days <= 3) score += SCORE_TIME;
            else if (days <= 7) score += SCORE_TIME * 0.7;
            else if (days <= 15) score += SCORE_TIME * 0.4;
            else if (days <= 30) score += SCORE_TIME * 0.2;
        } else {
            score += SCORE_TIME * 0.5;
        }
        return Math.min(score, 100);
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
