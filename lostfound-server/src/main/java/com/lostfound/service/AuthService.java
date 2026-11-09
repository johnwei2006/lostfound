package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.auth.JwtUtil;
import com.lostfound.common.BusinessException;
import com.lostfound.dto.ChangePasswordDTO;
import com.lostfound.dto.LoginDTO;
import com.lostfound.dto.RegisterDTO;
import com.lostfound.dto.UpdateProfileDTO;
import com.lostfound.dto.WxLoginDTO;
import com.lostfound.entity.User;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.MeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final WxAuthService wxAuthService;
    private final SitePermissionService sitePermission;

    public Map<String, Object> register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已被占用");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return login(LoginDTO.of(dto.getUsername(), dto.getPassword()));
    }

    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        String token = jwtUtil.createToken(user.getId(), user.getRole());
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("user", user);
        return map;
    }

    /**
     * 微信小程序登录：code 换 openid，存在则登录，不存在则自动注册
     */
    public Map<String, Object> wxLogin(WxLoginDTO dto) {
        String openid = wxAuthService.code2Openid(dto.getCode());
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (user == null) {
            User fresh = new User();
            // 生成唯一用户名与随机密码，保证微信用户也兼容账号密码体系
            String username = "wx_" + openid.substring(Math.max(0, openid.length() - 12)).toLowerCase();
            if (userMapper.selectCount(new QueryWrapper<User>().eq("username", username)) > 0) {
                username = username + "_" + System.currentTimeMillis();
            }
            fresh.setUsername(username);
            fresh.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            fresh.setNickname("微信用户" + openid.substring(Math.max(0, openid.length() - 6)));
            fresh.setOpenid(openid);
            fresh.setRole("USER");
            fresh.setStatus(1);
            fresh.setCreateTime(LocalDateTime.now());
            fresh.setUpdateTime(LocalDateTime.now());
            try {
                userMapper.insert(fresh);
                user = fresh;
            } catch (DuplicateKeyException e) {
                // 启动静默登录可能与其它请求并发，uk_openid 冲突时改用已建账号，不重复建号
                user = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
                if (user == null) throw e;
            }
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        String token = jwtUtil.createToken(user.getId(), user.getRole());
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("user", user);
        return map;
    }

    /** /api/auth/me：携带当前用户可管理的招领站，供 Web 端菜单与路由守卫判定 */
    public MeVO profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        MeVO vo = new MeVO();
        BeanUtils.copyProperties(user, vo);
        vo.setManagedSiteIds(sitePermission.mySiteIds(userId));
        vo.setSiteManager(sitePermission.isSiteManager(userId));
        return vo;
    }

    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
