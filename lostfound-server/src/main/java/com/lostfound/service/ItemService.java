package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.common.BusinessException;
import com.lostfound.common.ItemCategory;
import com.lostfound.common.PageResult;
import com.lostfound.dto.ItemQuery;
import com.lostfound.dto.PublishItemDTO;
import com.lostfound.entity.*;
import com.lostfound.mapper.*;
import com.lostfound.vo.ItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 失物/拾物通用业务服务（type = FOUND / LOST）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final FoundMapper foundMapper;
    private final LostMapper lostMapper;
    private final UserMapper userMapper;
    private final FoundSiteMapper foundSiteMapper;
    private final FoundFocusMapper foundFocusMapper;
    private final LostFocusMapper lostFocusMapper;
    private final FoundCommentMapper foundCommentMapper;
    private final LostCommentMapper lostCommentMapper;
    private final MatchService matchService;
    private final ObjectMapper objectMapper;
    private final CacheEvictService cacheEvictService;
    private final TransferService transferService;

    @Value("${lostfound.review.required:false}")
    private boolean reviewRequired;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public PageResult<ItemVO> page(String type, ItemQuery q, Long currentUserId) {
        q.setCurrentUserId(currentUserId);
        // 只有显式请求 distance 且坐标合法时才按距离排序；
        // 其余调用（/latest、/map、我的发布、管理后台）保持原有时间/热度排序不变。
        boolean distanceSort = "distance".equalsIgnoreCase(q.getSort()) && q.hasValidCoordinates();
        PageResult<ItemVO> result = new PageResult<>();
        if ("FOUND".equals(type)) {
            QueryWrapper<Found> qw = buildQuery(q, reviewRequired);
            applySiteVisibility(qw, q);
            List<Found> records;
            long total;
            long pages;
            if (distanceSort) {
                // .last() 是裸 SQL，会被带入自动生成的 count 语句，故关闭自动 count 后单独统计
                qw.last(distanceOrderBy(q));
                records = foundMapper.selectPage(new Page<>(q.getPage(), q.getSize(), false), qw).getRecords();
                total = foundMapper.selectCount(buildQuery(q, reviewRequired));
                pages = (total + q.getSize() - 1) / q.getSize();
            } else {
                applyDefaultOrder(qw, q.getSort());
                Page<Found> p = foundMapper.selectPage(new Page<>(q.getPage(), q.getSize()), qw);
                records = p.getRecords();
                total = p.getTotal();
                pages = p.getPages();
            }
            result.setTotal(total);
            result.setPages(pages);
            result.setCurrent(q.getPage());
            result.setSize(q.getSize());
            result.setRecords(convertFound(records, currentUserId));
        } else {
            QueryWrapper<Lost> qw = buildQuery(q, reviewRequired);
            List<Lost> records;
            long total;
            long pages;
            if (distanceSort) {
                qw.last(distanceOrderBy(q));
                records = lostMapper.selectPage(new Page<>(q.getPage(), q.getSize(), false), qw).getRecords();
                total = lostMapper.selectCount(buildQuery(q, reviewRequired));
                pages = (total + q.getSize() - 1) / q.getSize();
            } else {
                applyDefaultOrder(qw, q.getSort());
                Page<Lost> p = lostMapper.selectPage(new Page<>(q.getPage(), q.getSize()), qw);
                records = p.getRecords();
                total = p.getTotal();
                pages = p.getPages();
            }
            result.setTotal(total);
            result.setPages(pages);
            result.setCurrent(q.getPage());
            result.setSize(q.getSize());
            result.setRecords(convertLost(records, currentUserId));
        }
        if (distanceSort) fillDistance(result.getRecords(), q);
        return result;
    }

    private <T> void applyDefaultOrder(QueryWrapper<T> qw, String sort) {
        qw.orderByDesc("hot".equalsIgnoreCase(sort) ? "view_count" : "create_time");
    }

    /**
     * 站点相关过滤只作用于 found（lost 表无这些列）：
     * 按站点查询时仅展示站点直接发布或转交审批通过的物品，避免 PENDING 转交单提前曝光。
     */
    private void applySiteVisibility(QueryWrapper<Found> qw, ItemQuery q) {
        String sourceType = param(q.getSourceType());
        if (sourceType != null) qw.eq("source_type", sourceType.toUpperCase());
        String transferStatus = param(q.getTransferStatus());
        if (transferStatus != null) qw.eq("transfer_status", transferStatus.toUpperCase());
        if (q.getSiteId() != null) {
            qw.and(w -> w.eq("source_type", Found.SOURCE_SITE)
                    .or().eq("transfer_status", Found.TRANSFER_APPROVED));
        }
    }

    /**
     * Haversine 球面距离排序（MySQL 5.6 无空间函数）。
     * 坐标为 NULL 的记录排最后，create_time 作为并列时的稳定次序。
     * 经纬度已在 ItemQuery.hasValidCoordinates() 做过范围校验，此处只拼接数值字面量。
     */
    private String distanceOrderBy(ItemQuery q) {
        String lat = q.getUserLat().toPlainString();
        String lng = q.getUserLng().toPlainString();
        return " ORDER BY (latitude IS NULL OR longitude IS NULL) ASC,"
                + " 6371 * ACOS(LEAST(1, GREATEST(-1,"
                + " COS(RADIANS(" + lat + ")) * COS(RADIANS(latitude)) * COS(RADIANS(longitude - " + lng + "))"
                + " + SIN(RADIANS(" + lat + ")) * SIN(RADIANS(latitude))"
                + "))) ASC, create_time DESC";
    }

    /** 按与 SQL 相同的球面公式回填公里数，保留两位小数 */
    private void fillDistance(List<ItemVO> list, ItemQuery q) {
        double lat1 = q.getUserLat().doubleValue();
        double lng1 = q.getUserLng().doubleValue();
        for (ItemVO vo : list) {
            if (vo.getLatitude() == null || vo.getLongitude() == null) continue;
            double dLat = Math.toRadians(vo.getLatitude().doubleValue() - lat1);
            double dLng = Math.toRadians(vo.getLongitude().doubleValue() - lng1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(vo.getLatitude().doubleValue()))
                    * Math.sin(dLng / 2) * Math.sin(dLng / 2);
            double km = 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
            vo.setDistance(Math.round(km * 100) / 100.0);
        }
    }

    /**
     * 兼容小程序端把未设置的参数序列化成字符串 "undefined" / "null" 的情况：
     * 这些值等同于未传，不能作为真实筛选条件，否则列表会返回 0 条。
     */
    private static String param(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || "undefined".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v)) return null;
        return v;
    }

    private <T> QueryWrapper<T> buildQuery(ItemQuery q, boolean reviewRequired) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        String keyword = param(q.getKeyword());
        if (keyword != null) {
            qw.and(w -> w.like("title", keyword).or().like("description", keyword));
        }
        // 兼容历史分享链接里的 objectType 数字代码/旧别名，统一转成界面 8 类后再精确匹配
        String category = ItemCategory.normalize(param(q.getCategory()));
        if (category != null) {
            qw.eq("category", category);
        }
        String city = param(q.getCity());
        if (city != null) {
            qw.eq("city", city);
        }
        String status = param(q.getStatus());
        if (status != null) {
            qw.eq("status", status);
        } else {
            qw.ne("status", "OFFLINE");
        }
        if (q.getDays() != null && q.getDays() > 0) {
            qw.ge("create_time", LocalDateTime.now().minusDays(q.getDays()));
        }
        if (q.getSiteId() != null) {
            qw.eq("site_id", q.getSiteId());
        }
        if (Boolean.TRUE.equals(q.getMine()) && q.getCurrentUserId() != null) {
            qw.eq("user_id", q.getCurrentUserId());
        } else if (reviewRequired) {
            qw.eq("review_status", "APPROVED");
        }
        return qw;
    }

    public ItemVO detail(String type, Long id, Long currentUserId) {
        ItemVO vo;
        if ("FOUND".equals(type)) {
            foundMapper.incrementView(id);
            Found found = foundMapper.selectById(id);
            if (found == null) throw new BusinessException("信息不存在");
            vo = convertFound(List.of(found), currentUserId).get(0);
        } else {
            lostMapper.incrementView(id);
            Lost lost = lostMapper.selectById(id);
            if (lost == null) throw new BusinessException("信息不存在");
            vo = convertLost(List.of(lost), currentUserId).get(0);
        }
        // 非本人且未过审，仅发布者与管理员可见
        if (!"APPROVED".equals(vo.getReviewStatus())
                && !Objects.equals(vo.getUserId(), currentUserId)
                && !"ADMIN".equals(roleOf(currentUserId))) {
            throw new BusinessException("信息审核中，暂不可见");
        }
        return vo;
    }

    public PageResult<ItemVO> mine(String type, Long userId, long page, long size) {
        ItemQuery q = new ItemQuery();
        q.setPage(page);
        q.setSize(size);
        q.setMine(true);
        return page(type, q, userId);
    }

    /** 首页最新物品（缓存 5 分钟，不含个人关注态） */
    @Cacheable(cacheNames = "latestItems", key = "#type + ':' + #limit")
    public List<ItemVO> latest(String type, int limit, Long currentUserId) {
        ItemQuery q = new ItemQuery();
        q.setSize(limit);
        q.setPage(1);
        // 缓存公共数据，不携带个人关注态
        return page(type, q, null).getRecords();
    }

    // ------------------------------------------------------------------
    // 发布 / 编辑 / 删除
    // ------------------------------------------------------------------

    @Transactional
    public Long publish(String type, Long userId, PublishItemDTO dto) {
        Long id;
        if ("FOUND".equals(type)) {
            Found f = new Found();
            applyDto(f, dto);
            f.setUserId(userId);
            f.setFoundTime(dto.getItemTime());
            f.setStatus(Found.STATUS_WAITING);
            f.setReviewStatus(reviewRequired ? "PENDING" : "APPROVED");
            f.setViewCount(0);
            f.setSubmitUserId(userId);
            f.setSourceType(Found.SOURCE_PERSONAL);
            f.setTransferStatus(Found.TRANSFER_NONE);
            if (dto.getSiteId() != null) {
                // siteId 语义为「申请转交目标站点」，先校验再在同一事务内建转交单
                transferService.requireSite(dto.getSiteId());
                f.setTransferStatus(Found.TRANSFER_PENDING);
            }
            foundMapper.insert(f);
            id = f.getId();
            if (dto.getSiteId() != null) {
                transferService.applyForPublish(f, dto.getSiteId(), userId, dto.getTransferRemark());
            }
            matchService.triggerOnFound(f);
        } else {
            Lost l = new Lost();
            applyDto(l, dto);
            l.setUserId(userId);
            l.setLostTime(dto.getItemTime());
            l.setStatus(Lost.STATUS_WAITING);
            l.setReviewStatus(reviewRequired ? "PENDING" : "APPROVED");
            l.setViewCount(0);
            lostMapper.insert(l);
            id = l.getId();
            matchService.triggerOnLost(l);
        }
        evictLatest();
        return id;
    }

    /** 站点管理员代发布：直接标记为站点来源，无需转交审批 */
    @Transactional
    public Long publishForSite(Long siteId, Long userId, PublishItemDTO dto) {
        Found f = new Found();
        applyDto(f, dto);
        f.setUserId(userId);
        f.setSiteId(siteId);
        f.setFoundTime(dto.getItemTime());
        f.setStatus(Found.STATUS_WAITING);
        f.setReviewStatus(reviewRequired ? "PENDING" : "APPROVED");
        f.setViewCount(0);
        f.setSubmitUserId(userId);
        f.setSourceType(Found.SOURCE_SITE);
        f.setTransferStatus(Found.TRANSFER_APPROVED);
        f.setSiteApprovedBy(userId);
        f.setSiteApprovedAt(LocalDateTime.now());
        foundMapper.insert(f);
        matchService.triggerOnFound(f);
        evictLatest();
        return f.getId();
    }

    public void update(String type, Long id, Long userId, PublishItemDTO dto) {
        if ("FOUND".equals(type)) {
            Found f = requireFound(id);
            checkOwner(f.getUserId(), userId);
            // 站点归属与转交状态只能由转交流程修改，编辑接口不得绕过审批
            Long siteId = f.getSiteId();
            String sourceType = f.getSourceType();
            String transferStatus = f.getTransferStatus();
            Long submitUserId = f.getSubmitUserId();
            applyDto(f, dto);
            f.setSiteId(siteId);
            f.setSourceType(sourceType);
            f.setTransferStatus(transferStatus);
            f.setSubmitUserId(submitUserId);
            f.setFoundTime(dto.getItemTime());
            foundMapper.updateById(f);
        } else {
            Lost l = requireLost(id);
            checkOwner(l.getUserId(), userId);
            applyDto(l, dto);
            l.setLostTime(dto.getItemTime());
            lostMapper.updateById(l);
        }
        evictLatest();
    }

    public void delete(String type, Long id, Long userId, boolean isAdmin) {
        if ("FOUND".equals(type)) {
            Found f = requireFound(id);
            checkOwner(f.getUserId(), userId, isAdmin);
            foundMapper.deleteById(id);
        } else {
            Lost l = requireLost(id);
            checkOwner(l.getUserId(), userId, isAdmin);
            lostMapper.deleteById(id);
        }
        evictLatest();
    }

    public void changeStatus(String type, Long id, Long userId, boolean isAdmin, String status) {
        if ("FOUND".equals(type)) {
            Found f = requireFound(id);
            checkOwner(f.getUserId(), userId, isAdmin);
            f.setStatus(status);
            foundMapper.updateById(f);
        } else {
            Lost l = requireLost(id);
            checkOwner(l.getUserId(), userId, isAdmin);
            l.setStatus(status);
            lostMapper.updateById(l);
        }
        evictLatest();
    }

    // ------------------------------------------------------------------
    // VO 转换
    // ------------------------------------------------------------------

    /** 拾物 VO 转换（供站点清单等其它服务复用） */
    public List<ItemVO> convertFound(List<Found> list, Long currentUserId) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Found::getId).toList();
        Map<Long, Long> focusCounts = focusCounts(ids, true);
        Map<Long, Long> commentCounts = commentCounts(ids, true);
        Set<Long> focusedIds = currentUserId == null ? Set.of() :
                foundFocusMapper.selectList(new QueryWrapper<FoundFocus>()
                                .eq("user_id", currentUserId).in("item_id", ids))
                        .stream().map(FoundFocus::getItemId).collect(Collectors.toSet());
        Set<Long> userIds = list.stream().map(Found::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userMap(userIds);
        Map<Long, String> siteNames = list.stream().map(Found::getSiteId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toMap(Function.identity(),
                        sid -> Optional.ofNullable(foundSiteMapper.selectById(sid)).map(FoundSite::getName).orElse(""),
                        (a, b) -> a));

        return list.stream().map(f -> {
            ItemVO vo = new ItemVO();
            vo.setType("FOUND");
            vo.setId(f.getId());
            vo.setUserId(f.getUserId());
            User u = users.get(f.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatar(u.getAvatar());
            }
            vo.setSiteId(f.getSiteId());
            vo.setSiteName(f.getSiteId() == null ? null : siteNames.get(f.getSiteId()));
            vo.setTitle(f.getTitle());
            vo.setCategory(f.getCategory());
            vo.setDescription(f.getDescription());
            vo.setImages(parseImages(f.getImages()));
            vo.setProvince(f.getProvince());
            vo.setCity(f.getCity());
            vo.setDistrict(f.getDistrict());
            vo.setAddress(f.getAddress());
            vo.setLongitude(f.getLongitude());
            vo.setLatitude(f.getLatitude());
            vo.setItemTime(f.getFoundTime());
            vo.setContact(f.getContact());
            vo.setStatus(f.getStatus());
            vo.setReviewStatus(f.getReviewStatus());
            vo.setSourceType(f.getSourceType());
            vo.setTransferStatus(f.getTransferStatus());
            vo.setSubmitUserId(f.getSubmitUserId());
            vo.setViewCount(f.getViewCount());
            vo.setFocusCount(focusCounts.getOrDefault(f.getId(), 0L));
            vo.setCommentCount(commentCounts.getOrDefault(f.getId(), 0L));
            vo.setFocused(focusedIds.contains(f.getId()));
            vo.setCreateTime(f.getCreateTime());
            vo.setUpdateTime(f.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<ItemVO> convertLost(List<Lost> list, Long currentUserId) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Lost::getId).toList();
        Map<Long, Long> focusCounts = focusCounts(ids, false);
        Map<Long, Long> commentCounts = commentCounts(ids, false);
        Set<Long> focusedIds = currentUserId == null ? Set.of() :
                lostFocusMapper.selectList(new QueryWrapper<LostFocus>()
                                .eq("user_id", currentUserId).in("item_id", ids))
                        .stream().map(LostFocus::getItemId).collect(Collectors.toSet());
        Map<Long, User> users = userMap(list.stream().map(Lost::getUserId).collect(Collectors.toSet()));

        return list.stream().map(l -> {
            ItemVO vo = new ItemVO();
            vo.setType("LOST");
            vo.setId(l.getId());
            vo.setUserId(l.getUserId());
            User u = users.get(l.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatar(u.getAvatar());
            }
            vo.setTitle(l.getTitle());
            vo.setCategory(l.getCategory());
            vo.setDescription(l.getDescription());
            vo.setImages(parseImages(l.getImages()));
            vo.setProvince(l.getProvince());
            vo.setCity(l.getCity());
            vo.setDistrict(l.getDistrict());
            vo.setAddress(l.getAddress());
            vo.setLongitude(l.getLongitude());
            vo.setLatitude(l.getLatitude());
            vo.setItemTime(l.getLostTime());
            vo.setContact(l.getContact());
            vo.setStatus(l.getStatus());
            vo.setReviewStatus(l.getReviewStatus());
            vo.setViewCount(l.getViewCount());
            vo.setFocusCount(focusCounts.getOrDefault(l.getId(), 0L));
            vo.setCommentCount(commentCounts.getOrDefault(l.getId(), 0L));
            vo.setFocused(focusedIds.contains(l.getId()));
            vo.setCreateTime(l.getCreateTime());
            vo.setUpdateTime(l.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, User> userMap(Set<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Long> focusCounts(List<Long> ids, boolean found) {
        if (ids.isEmpty()) return Map.of();
        return found ? groupCount(foundFocusMapper, ids) : groupCount(lostFocusMapper, ids);
    }

    private Map<Long, Long> commentCounts(List<Long> ids, boolean found) {
        if (ids.isEmpty()) return Map.of();
        return found ? groupCount(foundCommentMapper, ids) : groupCount(lostCommentMapper, ids);
    }

    private <T> Map<Long, Long> groupCount(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, List<Long> ids) {
        QueryWrapper<T> qw = new QueryWrapper<>();
        qw.select("item_id", "COUNT(*) AS cnt").in("item_id", ids).groupBy("item_id");
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : mapper.selectMaps(qw)) {
            map.put(Long.valueOf(row.get("item_id").toString()), Long.valueOf(row.get("cnt").toString()));
        }
        return map;
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 按 ID 批量查询物品（保持入参顺序） */
    public List<ItemVO> listByIds(String type, List<Long> ids, Long currentUserId) {
        if (ids == null || ids.isEmpty()) return List.of();
        if ("FOUND".equals(type)) {
            return convertFound(foundMapper.selectBatchIds(ids), currentUserId);
        }
        return convertLost(lostMapper.selectBatchIds(ids), currentUserId);
    }

    public Found requireFound(Long id) {
        Found f = foundMapper.selectById(id);
        if (f == null) throw new BusinessException("拾物信息不存在");
        return f;
    }

    public Lost requireLost(Long id) {
        Lost l = lostMapper.selectById(id);
        if (l == null) throw new BusinessException("失物信息不存在");
        return l;
    }

    private void checkOwner(Long ownerId, Long userId) {
        checkOwner(ownerId, userId, false);
    }

    private void checkOwner(Long ownerId, Long userId, boolean isAdmin) {
        if (!Objects.equals(ownerId, userId) && !isAdmin) {
            throw new BusinessException(403, "无权操作他人信息");
        }
    }

    private void applyDto(Found f, PublishItemDTO dto) {
        f.setTitle(dto.getTitle());
        f.setCategory(ItemCategory.requireValid(dto.getCategory()));
        f.setDescription(dto.getDescription());
        f.setImages(toJson(dto.getImages()));
        f.setProvince(dto.getProvince());
        f.setCity(dto.getCity());
        f.setDistrict(dto.getDistrict());
        f.setAddress(dto.getAddress());
        f.setLongitude(dto.getLongitude());
        f.setLatitude(dto.getLatitude());
        f.setContact(dto.getContact());
        f.setSiteId(dto.getSiteId());
    }

    private void applyDto(Lost l, PublishItemDTO dto) {
        l.setTitle(dto.getTitle());
        l.setCategory(ItemCategory.requireValid(dto.getCategory()));
        l.setDescription(dto.getDescription());
        l.setImages(toJson(dto.getImages()));
        l.setProvince(dto.getProvince());
        l.setCity(dto.getCity());
        l.setDistrict(dto.getDistrict());
        l.setAddress(dto.getAddress());
        l.setLongitude(dto.getLongitude());
        l.setLatitude(dto.getLatitude());
        l.setContact(dto.getContact());
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void evictLatest() {
        cacheEvictService.evictLatest();
    }

    private String roleOf(Long userId) {
        if (userId == null) return null;
        User u = userMapper.selectById(userId);
        return u == null ? null : u.getRole();
    }
}
