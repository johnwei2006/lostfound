package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.common.ItemCategory;
import com.lostfound.common.PageResult;
import com.lostfound.dto.PublishItemDTO;
import com.lostfound.dto.SiteDTO;
import com.lostfound.dto.SiteEndorsementDTO;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.FoundTransfer;
import com.lostfound.entity.Message;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.FoundTransferMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.ItemVO;
import com.lostfound.vo.SiteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招领站服务：站点 CRUD、管理员配置、站内物品清单与站点代发布。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoundSiteService {

    private final FoundSiteMapper foundSiteMapper;
    private final FoundSiteManagerMapper foundSiteManagerMapper;
    private final FoundTransferMapper foundTransferMapper;
    private final UserMapper userMapper;
    private final FoundMapper foundMapper;
    private final ItemService itemService;
    private final SitePermissionService sitePermission;
    private final CacheEvictService cacheEvict;
    private final MessageService messageService;

    public PageResult<SiteVO> page(String keyword, long page, long size) {
        QueryWrapper<FoundSite> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like("name", keyword).or().like("address", keyword);
        }
        qw.orderByDesc("create_time");
        Page<FoundSite> p = foundSiteMapper.selectPage(new Page<>(page, size), qw);
        List<Long> ids = p.getRecords().stream().map(FoundSite::getId).toList();
        Map<Long, Long> managers = countBySite(foundSiteManagerMapper, "site_id", ids);
        Map<Long, Long> items = countBySite(foundMapper, "site_id", ids);

        List<SiteVO> records = p.getRecords().stream().map(site -> {
            SiteVO vo = new SiteVO();
            BeanUtils.copyProperties(site, vo);
            vo.setManagerCount(managers.getOrDefault(site.getId(), 0L));
            vo.setItemCount(items.getOrDefault(site.getId(), 0L));
            vo.setHasEndorsement(hasEndorsement(site));
            return vo;
        }).toList();
        PageResult<SiteVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(records);
        return result;
    }

    /** 一次 GROUP BY 批量取各站点计数，避免列表页 N+1 查询 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<Long, Long> countBySite(com.baomidou.mybatisplus.core.mapper.BaseMapper mapper,
                                        String column, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        QueryWrapper<Object> qw = new QueryWrapper<>();
        qw.select(column, "COUNT(*) AS cnt");
        qw.in(column, ids);
        qw.groupBy(column);
        Map<Long, Long> map = new java.util.HashMap<>();
        for (Object row : mapper.selectMaps(qw)) {
            Map<String, Object> m = (Map<String, Object>) row;
            Object key = m.get(column);
            if (key != null) map.put(Long.valueOf(key.toString()), Long.valueOf(m.get("cnt").toString()));
        }
        return map;
    }

    public FoundSite detail(Long id) {
        FoundSite site = foundSiteMapper.selectById(id);
        if (site == null) throw new BusinessException("招领站不存在");
        return site;
    }

    /** 当前用户可管理的站点（全局 ADMIN 返回全部） */
    public List<FoundSite> mine(Long userId) {
        return sitePermission.mySites(userId);
    }

    public List<Map<String, Object>> managers(Long siteId) {
        List<FoundSiteManager> list = foundSiteManagerMapper.selectList(
                new QueryWrapper<FoundSiteManager>().eq("site_id", siteId));
        if (list.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(
                        list.stream().map(FoundSiteManager::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        // 追加 isOwner 供 Web 超管名单标识主管理员；原三个 key 保持不变，公开详情页不受影响
        Long ownerId = detail(siteId).getOwnerUserId();
        return list.stream().map(m -> {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", m.getId());
            row.put("userId", m.getUserId());
            row.put("nickname", users.containsKey(m.getUserId())
                    ? users.get(m.getUserId()).getNickname() : "未知用户");
            row.put("isOwner", Objects.equals(ownerId, m.getUserId()));
            return row;
        }).collect(Collectors.toList());
    }

    /**
     * 站内物品清单：仅展示站点直接发布或转交审批通过（transfer_status=APPROVED）的物品。
     * manage=true 时要求站点管理员权限，并额外展示已下架物品。
     */
    public PageResult<ItemVO> items(Long siteId, String keyword, String category, String status,
                                    String sourceType, boolean manage, long page, long size,
                                    Long currentUserId) {
        FoundSite site = foundSiteMapper.selectById(siteId);
        if (site == null) throw new BusinessException("招领站不存在");
        if (manage) sitePermission.requireManager(siteId);

        QueryWrapper<Found> qw = new QueryWrapper<Found>()
                .eq("site_id", siteId)
                .and(w -> w.eq("source_type", Found.SOURCE_SITE)
                        .or().eq("transfer_status", Found.TRANSFER_APPROVED));
        String kw = clean(keyword);
        if (kw != null) qw.and(w -> w.like("title", kw).or().like("description", kw));
        // 站点清单同样按新字典精确匹配，旧类别不再做别名转换
        String cat = ItemCategory.normalize(clean(category));
        if (cat != null) qw.eq("category", cat);
        String st = clean(status);
        if (st != null) qw.eq("status", st.toUpperCase());
        else if (!manage) qw.ne("status", Found.STATUS_OFFLINE);
        String src = clean(sourceType);
        if (src != null) qw.eq("source_type", src.toUpperCase());
        qw.orderByDesc("create_time");

        Page<Found> p = foundMapper.selectPage(new Page<>(page, size), qw);
        PageResult<ItemVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(itemService.convertFound(p.getRecords(), currentUserId));
        return result;
    }

    /** 站点管理员代发布：直接置为站点来源，无需转交审批 */
    public Long publishItem(Long siteId, Long userId, PublishItemDTO dto) {
        sitePermission.requireManager(siteId);
        detail(siteId);
        Long id = itemService.publishForSite(siteId, userId, dto);
        log.warn("站点代发布 siteId={} foundId={} operatorId={}", siteId, id, userId);
        return id;
    }

    public Long create(SiteDTO dto) {
        FoundSite site = new FoundSite();
        applyDto(site, dto);
        foundSiteMapper.insert(site);
        return site.getId();
    }

    public void update(Long id, SiteDTO dto) {
        FoundSite site = foundSiteMapper.selectById(id);
        if (site == null) throw new BusinessException("招领站不存在");
        applyDto(site, dto);
        foundSiteMapper.updateById(site);
        // 站点改名会影响物品 VO 中的 siteName，需失效首页/列表缓存
        cacheEvict.evictLatest();
    }

    @Transactional
    public void delete(Long id) {
        detail(id);
        foundSiteMapper.deleteById(id);
        foundSiteManagerMapper.delete(new QueryWrapper<FoundSiteManager>().eq("site_id", id));
        // 待审批的转交申请随站点删除一并撤销，避免悬空引用
        foundTransferMapper.update(null, new LambdaUpdateWrapper<FoundTransfer>()
                .eq(FoundTransfer::getSiteId, id).eq(FoundTransfer::getStatus, FoundTransfer.STATUS_PENDING)
                .set(FoundTransfer::getStatus, FoundTransfer.STATUS_CANCELLED)
                .set(FoundTransfer::getRemark, "招领站已删除，申请自动撤销")
                .set(FoundTransfer::getUpdateTime, LocalDateTime.now()));
        // 站内物品解除关联
        foundMapper.update(null, new LambdaUpdateWrapper<Found>()
                .eq(Found::getSiteId, id).set(Found::getSiteId, null));
        cacheEvict.evictLatest();
        log.warn("招领站删除 siteId={}", id);
    }

    /**
     * 全量覆盖式管理员名单：去重、校验用户存在，并通知新增管理员。
     * 主管理员行始终保留，前端即使移除了标签也会被强制补回；
     * 历史站点 owner_user_id 为空时，把名单首位指定为主管理员。
     */
    @Transactional
    public void setManagers(Long siteId, List<Long> userIds) {
        FoundSite site = detail(siteId);
        Set<Long> target = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long uid : userIds) {
                if (uid != null && userMapper.selectById(uid) != null) target.add(uid);
            }
        }
        Long ownerId = site.getOwnerUserId();
        if (ownerId == null && !target.isEmpty()) {
            ownerId = target.iterator().next();
            foundSiteMapper.update(null, new LambdaUpdateWrapper<FoundSite>()
                    .eq(FoundSite::getId, siteId)
                    .set(FoundSite::getOwnerUserId, ownerId));
            site.setOwnerUserId(ownerId);
        }
        if (ownerId != null) target.add(ownerId);

        Set<Long> existing = foundSiteManagerMapper.selectList(new QueryWrapper<FoundSiteManager>()
                        .select("user_id").eq("site_id", siteId))
                .stream().map(FoundSiteManager::getUserId).collect(Collectors.toSet());
        QueryWrapper<FoundSiteManager> deleteQuery = new QueryWrapper<FoundSiteManager>()
                .eq("site_id", siteId);
        if (ownerId != null) deleteQuery.notIn("user_id", List.of(ownerId));
        foundSiteManagerMapper.delete(deleteQuery);

        LocalDateTime now = LocalDateTime.now();
        for (Long uid : target) {
            // 主管理员行保留原有加入时间；仅在数据异常缺失时补插
            if (Objects.equals(uid, ownerId) && existing.contains(ownerId)) continue;
            FoundSiteManager m = new FoundSiteManager();
            m.setSiteId(siteId);
            m.setUserId(uid);
            m.setCreateTime(now);
            foundSiteManagerMapper.insert(m);
        }
        for (Long uid : target) {
            if (!existing.contains(uid)) {
                messageService.send(uid, "您已被设为招领站管理员",
                        "您现在可以管理「" + site.getName() + "」的物品、转交审批与归还登记。",
                        Message.TYPE_SITE, siteId);
            }
        }
        log.warn("招领站管理员更新 siteId={} count={} ownerId={}", siteId, target.size(), ownerId);
    }

    private void applyDto(FoundSite site, SiteDTO dto) {
        site.setName(dto.getName());
        site.setAddress(dto.getAddress());
        site.setLongitude(dto.getLongitude());
        site.setLatitude(dto.getLatitude());
        site.setPhone(dto.getPhone());
        site.setOpenTime(dto.getOpenTime());
        site.setDescription(dto.getDescription());
        String org = clean(dto.getEndorsementOrg());
        String text = clean(dto.getEndorsementText());
        if (!Objects.equals(site.getEndorsementOrg(), org)
                || !Objects.equals(site.getEndorsementText(), text)) {
            site.setEndorsementOrg(org);
            site.setEndorsementText(text);
            site.setEndorsementUpdatedAt(LocalDateTime.now());
            site.setEndorsementUpdatedBy(UserContext.getUserId());
        }
    }

    /** 自定义背书文案：全局 ADMIN 或对应站点管理员可维护，普通用户只读 */
    public void updateEndorsement(Long siteId, SiteEndorsementDTO dto) {
        sitePermission.requireManager(siteId);
        FoundSite site = detail(siteId);
        site.setEndorsementOrg(clean(dto.getEndorsementOrg()));
        site.setEndorsementText(clean(dto.getEndorsementText()));
        site.setEndorsementUpdatedAt(LocalDateTime.now());
        site.setEndorsementUpdatedBy(UserContext.getUserId());
        foundSiteMapper.updateById(site);
        log.warn("招领站背书更新 siteId={} operatorId={}", siteId, UserContext.getUserId());
    }

    private boolean hasEndorsement(FoundSite site) {
        return clean(site.getEndorsementOrg()) != null || clean(site.getEndorsementText()) != null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || "undefined".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v)
                || "ALL".equalsIgnoreCase(v)) return null;
        return v;
    }
}
