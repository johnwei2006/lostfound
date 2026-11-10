package com.lostfound.service;

import com.lostfound.auth.JwtUtil;
import com.lostfound.common.BusinessException;
import com.lostfound.dto.UpdateProfileDTO;
import com.lostfound.entity.User;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.MeVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 个人资料更新单测：contact 字段、null 不覆盖、空串可清空、me 返回新字段 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private WxAuthService wxAuthService;
    @Mock private SitePermissionService sitePermission;

    @InjectMocks private AuthService service;

    private User existingUser() {
        User u = new User();
        u.setId(10L);
        u.setUsername("tester");
        u.setNickname("旧昵称");
        u.setPhone("13800000000");
        u.setEmail("old@example.com");
        u.setContact("old-wx-id");
        return u;
    }

    @Test
    void updateProfileSavesNicknameAndContact() {
        User u = existingUser();
        when(userMapper.selectById(10L)).thenReturn(u);
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setNickname("新昵称");
        dto.setContact("wx_new_id");

        service.updateProfile(10L, dto);

        assertEquals("新昵称", u.getNickname());
        assertEquals("wx_new_id", u.getContact());
        assertEquals("13800000000", u.getPhone(), "未传字段不应被覆盖");
        assertEquals("old@example.com", u.getEmail());
        assertNotNull(u.getUpdateTime());
        verify(userMapper).updateById(u);
    }

    @Test
    void updateProfileAllowsClearingContactWithEmptyString() {
        User u = existingUser();
        when(userMapper.selectById(10L)).thenReturn(u);
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setContact("");

        service.updateProfile(10L, dto);

        assertEquals("", u.getContact(), "传空串表示清空联系方式");
        assertEquals("旧昵称", u.getNickname());
        verify(userMapper).updateById(u);
    }

    @Test
    void updateProfileRejectsUnknownUser() {
        when(userMapper.selectById(99L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateProfile(99L, new UpdateProfileDTO()));
        assertEquals(400, e.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void profileReturnsContact() {
        when(userMapper.selectById(10L)).thenReturn(existingUser());
        when(sitePermission.mySiteIds(10L)).thenReturn(java.util.List.of());

        MeVO vo = service.profile(10L);

        assertEquals("old-wx-id", vo.getContact());
        assertEquals("旧昵称", vo.getNickname());
    }
}
