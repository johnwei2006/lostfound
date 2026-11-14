package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.entity.ClaimRequest;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.entity.User;
import com.lostfound.mapper.ClaimRequestMapper;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.LostMapper;
import com.lostfound.mapper.MatchRecordMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.AdminStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端服务：审核 / 用户管理 / 统计
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final FoundMapper foundMapper;
    private final LostMapper lostMapper;
    private final UserMapper userMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final ClaimRequestMapper claimRequestMapper;
    private final MessageService messageService;

    /** 纯单元测试里可不注入，缺失时自动降级为直查 */
    @Autowired(required = false)
    private CacheManager cacheManager;

    /** 审核后主动失效统计与列表缓存，避免 60 秒窗口内展示旧值 */
    @Autowired(required = false)
    private CacheEvictService cacheEvictService;

    /** 物品管理分页（type = FOUND / LOST） */
    public PageResult<Map<String, Object>> itemPage(String type, String reviewStatus, String status,
                                                    String keyword, long page, long size) {
        boolean isFound = !"LOST".equals(type);
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setCurrent(page);
        result.setSize(size);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (isFound) {
            QueryWrapper<Found> qw = new QueryWrapper<>();
            if (reviewStatus != null && !reviewStatus.isBlank()) qw.eq("review_status", reviewStatus);
            if (status != null && !status.isBlank()) qw.eq("status", status);
            if (keyword != null && !keyword.isBlank()) qw.like("title", keyword);
            qw.orderByDesc("create_time");
            Page<Found> p = foundMapper.selectPage(new Page<>(page, size), qw);
            result.setTotal(p.getTotal());
            result.setPages(p.getPages());
            p.getRecords().forEach(f -> rows.add(itemRow("FOUND", f.getId(), f.getTitle(), f.getCategory(),
                    f.getStatus(), f.getReviewStatus(), f.getUserId(), f.getCreateTime())));
        } else {
            QueryWrapper<Lost> qw = new QueryWrapper<>();
            if (reviewStatus != null && !reviewStatus.isBlank()) qw.eq("review_status", reviewStatus);
            if (status != null && !status.isBlank()) qw.eq("status", status);
            if (keyword != null && !keyword.isBlank()) qw.like("title", keyword);
            qw.orderByDesc("create_time");
            Page<Lost> p = lostMapper.selectPage(new Page<>(page, size), qw);
            result.setTotal(p.getTotal());
            result.setPages(p.getPages());
            p.getRecords().forEach(l -> rows.add(itemRow("LOST", l.getId(), l.getTitle(), l.getCategory(),
                    l.getStatus(), l.getReviewStatus(), l.getUserId(), l.getCreateTime())));
        }
        result.setRecords(rows);
        return result;
    }

    private Map<String, Object> itemRow(String type, Long id, String title, String category,
                                        String status, String reviewStatus, Long userId, LocalDateTime createTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("type", type);
        row.put("title", title);
        row.put("category", category);
        row.put("status", status);
        row.put("reviewStatus", reviewStatus);
        row.put("userId", userId);
        row.put("createTime", createTime);
        return row;
    }

    /** 审核：通过/驳回（驳回同时下架） */
    public void review(String type, Long id, boolean approve) {
        if ("FOUND".equals(type)) {
            Found f = foundMapper.selectById(id);
            if (f == null) throw new BusinessException("信息不存在");
            f.setReviewStatus(approve ? "APPROVED" : "REJECTED");
            if (!approve) f.setStatus(Found.STATUS_OFFLINE);
            foundMapper.updateById(f);
            messageService.send(f.getUserId(), approve ? "信息审核通过" : "信息审核未通过",
                    approve ? "您发布的「" + f.getTitle() + "」已通过审核，正式对外展示。"
                            : "您发布的「" + f.getTitle() + "」未通过审核，已被下架。",
                    "SYSTEM", id);
        } else {
            Lost l = lostMapper.selectById(id);
            if (l == null) throw new BusinessException("信息不存在");
            l.setReviewStatus(approve ? "APPROVED" : "REJECTED");
            if (!approve) l.setStatus(Lost.STATUS_OFFLINE);
            lostMapper.updateById(l);
            messageService.send(l.getUserId(), approve ? "信息审核通过" : "信息审核未通过",
                    approve ? "您发布的「" + l.getTitle() + "」已通过审核，正式对外展示。"
                            : "您发布的「" + l.getTitle() + "」未通过审核，已被下架。",
                    "SYSTEM", id);
        }
        if (cacheEvictService != null) cacheEvictService.evictAll();
    }

    /** 用户分页 */
    public PageResult<User> userPage(String keyword, long page, long size) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("username", keyword).or().like("nickname", keyword).or().like("phone", keyword));
        }
        qw.orderByDesc("create_time");
        return PageResult.of(userMapper.selectPage(new Page<>(page, size), qw));
    }

    /** 启用/禁用用户 */
    public void changeUserStatus(Long userId, boolean enable) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if ("ADMIN".equals(user.getRole())) throw new BusinessException("不能禁用管理员账号");
        user.setStatus(enable ? 1 : 0);
        userMapper.updateById(user);
    }

    /**
     * 统计概览：短 TTL 手写缓存（不用 @Cacheable，避免缓存命中绕过权限校验）。
     * SQL 次数由 39 条降到 9 条：单表条件聚合 + 一次日期分组替代 14 天循环 COUNT。
     */
    public AdminStatsVO stats() {
        Cache cache = cacheManager == null ? null : cacheManager.getCache("adminOverview");
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(CacheEvictService.OVERVIEW_CACHE_KEY);
            if (wrapper != null && wrapper.get() instanceof AdminStatsVO) {
                return (AdminStatsVO) wrapper.get();
            }
        }
        AdminStatsVO vo = computeStats();
        if (cache != null) cache.put(CacheEvictService.OVERVIEW_CACHE_KEY, vo);
        return vo;
    }

    private AdminStatsVO computeStats() {
        AdminStatsVO vo = new AdminStatsVO();
        Map<String, Object> fa = conditionalAgg(true);
        Map<String, Object> la = conditionalAgg(false);
        vo.setUserCount(userMapper.selectCount(null));
        vo.setFoundCount(asLong(fa.get("total")));
        vo.setLostCount(asLong(la.get("total")));
        vo.setWaitingFound(asLong(fa.get("waiting")));
        vo.setWaitingLost(asLong(la.get("waiting")));
        vo.setPendingReview(asLong(fa.get("pending")) + asLong(la.get("pending")));
        vo.setMatchCount(matchRecordMapper.selectCount(null));
        vo.setClaimPending(claimRequestMapper.selectCount(
                new QueryWrapper<ClaimRequest>().eq("status", ClaimRequest.STATUS_PENDING)));
        vo.setTrend(trend());
        vo.setCategoryStats(categoryStats());
        return vo;
    }

    /** 单表一次扫描得到总数、进行中数量与待审核数量；状态值来自代码常量，不来自请求参数 */
    private Map<String, Object> conditionalAgg(boolean found) {
        String waiting = found ? Found.STATUS_WAITING : Lost.STATUS_WAITING;
        String waitingExpr = "SUM(CASE WHEN status = '" + waiting + "' THEN 1 ELSE 0 END) AS waiting";
        String pendingExpr = "SUM(CASE WHEN review_status = 'PENDING' THEN 1 ELSE 0 END) AS pending";
        List<Map<String, Object>> rows = found
                ? foundMapper.selectMaps(new QueryWrapper<Found>()
                        .select("COUNT(*) AS total", waitingExpr, pendingExpr))
                : lostMapper.selectMaps(new QueryWrapper<Lost>()
                        .select("COUNT(*) AS total", waitingExpr, pendingExpr));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 近 14 天趋势：两张表各一次 GROUP BY，缺失日期在应用层补 0 */
    private List<Map<String, Object>> trend() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(13).atStartOfDay();
        List<Map<String, Object>> foundRows = foundMapper.selectMaps(new QueryWrapper<Found>()
                .select("DATE(create_time) AS d", "COUNT(*) AS cnt")
                .ge("create_time", from).groupBy("DATE(create_time)"));
        List<Map<String, Object>> lostRows = lostMapper.selectMaps(new QueryWrapper<Lost>()
                .select("DATE(create_time) AS d", "COUNT(*) AS cnt")
                .ge("create_time", from).groupBy("DATE(create_time)"));
        Map<String, Long> foundByDay = dayCount(foundRows);
        Map<String, Long> lostByDay = dayCount(lostRows);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", key.substring(5));
            row.put("found", foundByDay.getOrDefault(key, 0L));
            row.put("lost", lostByDay.getOrDefault(key, 0L));
            trend.add(row);
        }
        return trend;
    }

    private static Map<String, Long> dayCount(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Object day = r.get("d");
            if (day != null) map.put(day.toString(), asLong(r.get("cnt")));
        }
        return map;
    }

    /** 类别分布（失物 + 拾物合并），原本就是两次批量 GROUP BY，保持不变 */
    private List<Map<String, Object>> categoryStats() {
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        foundMapper.selectMaps(new QueryWrapper<Found>()
                        .select("category", "COUNT(*) AS cnt").groupBy("category"))
                .forEach(r -> categoryCount.merge(String.valueOf(r.get("category")),
                        asLong(r.get("cnt")), Long::sum));
        lostMapper.selectMaps(new QueryWrapper<Lost>()
                        .select("category", "COUNT(*) AS cnt").groupBy("category"))
                .forEach(r -> categoryCount.merge(String.valueOf(r.get("category")),
                        asLong(r.get("cnt")), Long::sum));
        return categoryCount.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Long.compare((long) b.get("count"), (long) a.get("count")))
                .collect(ArrayList::new, (list, m) -> list.add(m), ArrayList::addAll);
    }

    /** MySQL COUNT/SUM 可能返回 Long / BigDecimal / BigInteger，统一安全转换 */
    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        return new BigDecimal(value.toString()).longValue();
    }
}
