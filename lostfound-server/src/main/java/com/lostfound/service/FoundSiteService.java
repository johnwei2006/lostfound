package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.dto.SiteDTO;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招领站服务
 */
@Service
@RequiredArgsConstructor
public class FoundSiteService {

    private final FoundSiteMapper foundSiteMapper;
    private final FoundSiteManagerMapper foundSiteManagerMapper;
    private final UserMapper userMapper;
    private final FoundMapper foundMapper;

    public PageResult<FoundSite> page(String keyword, long page, long size) {
        QueryWrapper<FoundSite> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like("name", keyword).or().like("address", keyword);
        }
        qw.orderByDesc("create_time");
        return PageResult.of(foundSiteMapper.selectPage(new Page<>(page, size), qw));
    }

    public FoundSite detail(Long id) {
        FoundSite site = foundSiteMapper.selectById(id);
        if (site == null) throw new BusinessException("招领站不存在");
        return site;
    }

    public List<Map<String, Object>> managers(Long siteId) {
        List<FoundSiteManager> list = foundSiteManagerMapper.selectList(
                new QueryWrapper<FoundSiteManager>().eq("site_id", siteId));
        if (list.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(
                        list.stream().map(FoundSiteManager::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(),
                "userId", m.getUserId(),
                "nickname", users.containsKey(m.getUserId()) ? users.get(m.getUserId()).getNickname() : "未知用户"
        )).collect(Collectors.toList());
    }

    /** 招领站内物品列表 */
    public PageResult<?> items(Long siteId, long page, long size, Long currentUserId) {
        FoundSite site = foundSiteMapper.selectById(siteId);
        if (site == null) throw new BusinessException("招领站不存在");
        Page<Found> p = foundMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Found>().eq("site_id", siteId).orderByDesc("create_time"));
        return PageResult.of(p);
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
    }

    @Transactional
    public void delete(Long id) {
        foundSiteMapper.deleteById(id);
        foundSiteManagerMapper.delete(new QueryWrapper<FoundSiteManager>().eq("site_id", id));
        // 站内物品解除关联
        foundMapper.update(null, new LambdaUpdateWrapper<Found>()
                .eq(Found::getSiteId, id).set(Found::getSiteId, null));
    }

    @Transactional
    public void setManagers(Long siteId, List<Long> userIds) {
        foundSiteManagerMapper.delete(new QueryWrapper<FoundSiteManager>().eq("site_id", siteId));
        for (Long uid : userIds) {
            if (userMapper.selectById(uid) == null) continue;
            FoundSiteManager m = new FoundSiteManager();
            m.setSiteId(siteId);
            m.setUserId(uid);
            m.setCreateTime(LocalDateTime.now());
            foundSiteManagerMapper.insert(m);
        }
    }

    private void applyDto(FoundSite site, SiteDTO dto) {
        site.setName(dto.getName());
        site.setAddress(dto.getAddress());
        site.setLongitude(dto.getLongitude());
        site.setLatitude(dto.getLatitude());
        site.setPhone(dto.getPhone());
        site.setOpenTime(dto.getOpenTime());
        site.setDescription(dto.getDescription());
    }
}
