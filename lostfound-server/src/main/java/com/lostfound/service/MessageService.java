package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.PageResult;
import com.lostfound.entity.Message;
import com.lostfound.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    public void send(Long userId, String title, String content, String type, Long relatedId) {
        Message m = new Message();
        m.setUserId(userId);
        m.setTitle(title);
        m.setContent(content);
        m.setType(type);
        m.setRelatedId(relatedId);
        m.setIsRead(0);
        m.setDeleted(0);
        messageMapper.insert(m);
    }

    public PageResult<Message> page(Long userId, String type, Integer isRead, long page, long size) {
        QueryWrapper<Message> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        if (type != null && !type.isBlank()) {
            qw.eq("type", type);
        }
        if (isRead != null) {
            qw.eq("is_read", isRead);
        }
        qw.orderByDesc("create_time");
        return PageResult.of(messageMapper.selectPage(new Page<>(page, size), qw));
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(new QueryWrapper<Message>().eq("user_id", userId).eq("is_read", 0));
    }

    public void read(Long id, Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getId, id).eq(Message::getUserId, userId).set(Message::getIsRead, 1));
    }

    public void readAll(Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId).eq(Message::getIsRead, 0).set(Message::getIsRead, 1));
    }
}
