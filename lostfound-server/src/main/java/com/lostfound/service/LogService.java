package com.lostfound.service;

import com.lostfound.entity.User;
import com.lostfound.mapper.OperateLogMapper;
import com.lostfound.entity.OperateLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final OperateLogMapper operateLogMapper;

    public void record(Long userId, String action, String detail, String ip) {
        try {
            OperateLog log = new OperateLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setDetail(detail);
            log.setIp(ip);
            log.setCreateTime(LocalDateTime.now());
            operateLogMapper.insert(log);
        } catch (Exception ignored) {
            // 日志记录失败不影响主流程
        }
    }
}
