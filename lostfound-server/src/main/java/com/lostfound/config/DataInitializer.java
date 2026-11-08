package com.lostfound.config;

import com.lostfound.entity.User;
import com.lostfound.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据初始化：确保存在默认管理员（admin / admin123）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder().encode("admin123"));
            admin.setNickname("系统管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setDeleted(0);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            userMapper.insert(admin);
            log.info("已创建默认管理员账号 admin / admin123（请尽快修改密码）");
        }
    }
}
