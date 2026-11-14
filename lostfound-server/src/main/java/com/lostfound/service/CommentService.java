package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.common.BusinessException;
import com.lostfound.dto.CommentDTO;
import com.lostfound.entity.FoundComment;
import com.lostfound.entity.LostComment;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundCommentMapper;
import com.lostfound.mapper.LostCommentMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务（失物/拾物通用）
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final FoundCommentMapper foundCommentMapper;
    private final LostCommentMapper lostCommentMapper;
    private final UserMapper userMapper;
    private final SitePermissionService sitePermission;
    private final ItemService itemService;

    /** 评论树（两层：根评论 + 子回复） */
    public List<CommentVO> list(String type, Long itemId) {
        List<?> raw;
        if ("FOUND".equals(type)) {
            raw = foundCommentMapper.selectList(new QueryWrapper<FoundComment>()
                    .eq("item_id", itemId).orderByAsc("create_time").last("limit 500"));
        } else {
            raw = lostCommentMapper.selectList(new QueryWrapper<LostComment>()
                    .eq("item_id", itemId).orderByAsc("create_time").last("limit 500"));
        }

        Set<Long> userIds = raw.stream().map(c -> c instanceof FoundComment fc ? fc.getUserId() : ((LostComment) c).getUserId())
                .collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<CommentVO> vos = raw.stream().map(c -> {
            CommentVO vo = new CommentVO();
            if (c instanceof FoundComment fc) {
                vo.setId(fc.getId());
                vo.setItemId(fc.getItemId());
                vo.setUserId(fc.getUserId());
                vo.setParentId(fc.getParentId());
                vo.setContent(fc.getContent());
                vo.setCreateTime(fc.getCreateTime());
            } else {
                LostComment lc = (LostComment) c;
                vo.setId(lc.getId());
                vo.setItemId(lc.getItemId());
                vo.setUserId(lc.getUserId());
                vo.setParentId(lc.getParentId());
                vo.setContent(lc.getContent());
                vo.setCreateTime(lc.getCreateTime());
            }
            User u = users.get(vo.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatar(u.getAvatar());
            }
            return vo;
        }).sorted(Comparator.comparing(CommentVO::getCreateTime)).toList();

        // 组装两层树
        Map<Long, CommentVO> byId = vos.stream().collect(Collectors.toMap(CommentVO::getId, Function.identity()));
        List<CommentVO> roots = new java.util.ArrayList<>();
        for (CommentVO vo : vos) {
            if (vo.getParentId() == null || !byId.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                byId.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    public Long add(String type, Long itemId, Long userId, CommentDTO dto) {
        if ("FOUND".equals(type)) {
            itemService.requireFound(itemId);
            FoundComment c = new FoundComment();
            c.setItemId(itemId);
            c.setUserId(userId);
            c.setParentId(dto.getParentId());
            c.setContent(dto.getContent());
            c.setDeleted(0);
            c.setCreateTime(LocalDateTime.now());
            foundCommentMapper.insert(c);
            return c.getId();
        }
        itemService.requireLost(itemId);
        LostComment c = new LostComment();
        c.setItemId(itemId);
        c.setUserId(userId);
        c.setParentId(dto.getParentId());
        c.setContent(dto.getContent());
        c.setDeleted(0);
        c.setCreateTime(LocalDateTime.now());
        lostCommentMapper.insert(c);
        return c.getId();
    }

    public void delete(String type, Long id, Long userId, boolean isAdmin) {
        if ("FOUND".equals(type)) {
            FoundComment c = foundCommentMapper.selectById(id);
            if (c == null) throw new BusinessException("评论不存在");
            if (!Objects.equals(c.getUserId(), userId) && !isAdmin) {
                throw new BusinessException(403, "无权删除他人评论");
            }
            foundCommentMapper.deleteById(id);
        } else {
            LostComment c = lostCommentMapper.selectById(id);
            if (c == null) throw new BusinessException("评论不存在");
            if (!Objects.equals(c.getUserId(), userId) && !isAdmin) {
                throw new BusinessException(403, "无权删除他人评论");
            }
            lostCommentMapper.deleteById(id);
        }
    }

    /** 是否招领站管理员（统一委托 SitePermissionService，消除重复逻辑） */
    public boolean isSiteManager(Long siteId, Long userId) {
        return sitePermission.isManager(userId, siteId);
    }
}
