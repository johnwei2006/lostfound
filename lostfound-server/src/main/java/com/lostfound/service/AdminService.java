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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** 统计概览 */
    public AdminStatsVO stats() {
        AdminStatsVO vo = new AdminStatsVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setFoundCount(foundMapper.selectCount(null));
        vo.setLostCount(lostMapper.selectCount(null));
        vo.setWaitingFound(foundMapper.selectCount(new QueryWrapper<Found>().eq("status", Found.STATUS_WAITING)));
        vo.setWaitingLost(lostMapper.selectCount(new QueryWrapper<Lost>().eq("status", Lost.STATUS_WAITING)));
        vo.setMatchCount(matchRecordMapper.selectCount(null));
        vo.setClaimPending(claimRequestMapper.selectCount(
                new QueryWrapper<ClaimRequest>().eq("status", ClaimRequest.STATUS_PENDING)));
        vo.setPendingReview(
                foundMapper.selectCount(new QueryWrapper<Found>().eq("review_status", "PENDING"))
                        + lostMapper.selectCount(new QueryWrapper<Lost>().eq("review_status", "PENDING")));

        // 近 14 天发布趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toString().substring(5));
            row.put("found", foundMapper.selectCount(new QueryWrapper<Found>()
                    .ge("create_time", start).lt("create_time", end)));
            row.put("lost", lostMapper.selectCount(new QueryWrapper<Lost>()
                    .ge("create_time", start).lt("create_time", end)));
            trend.add(row);
        }
        vo.setTrend(trend);

        // 类别分布（失物 + 拾物合并）
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        foundMapper.selectMaps(new QueryWrapper<Found>()
                        .select("category", "COUNT(*) AS cnt").groupBy("category"))
                .forEach(r -> categoryCount.merge(String.valueOf(r.get("category")),
                        Long.valueOf(r.get("cnt").toString()), Long::sum));
        lostMapper.selectMaps(new QueryWrapper<Lost>()
                        .select("category", "COUNT(*) AS cnt").groupBy("category"))
                .forEach(r -> categoryCount.merge(String.valueOf(r.get("category")),
                        Long.valueOf(r.get("cnt").toString()), Long::sum));
        List<Map<String, Object>> categoryStats = categoryCount.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Long.compare((long) b.get("count"), (long) a.get("count")))
                .collect(ArrayList::new, (list, m) -> list.add(m), ArrayList::addAll);
        vo.setCategoryStats(categoryStats);
        return vo;
    }
}
