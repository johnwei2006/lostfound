package com.lostfound.service;

import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.FoundSiteManagerInvite;
import com.lostfound.entity.Message;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundSiteManagerInviteMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.SiteInviteVO;
import com.lostfound.vo.SiteManagerVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteManagerServiceTest {

    @Mock private FoundSiteMapper foundSiteMapper;
    @Mock private FoundSiteManagerMapper managerMapper;
    @Mock private FoundSiteManagerInviteMapper inviteMapper;
    @Mock private UserMapper userMapper;
    @Mock private SitePermissionService sitePermission;
    @Mock private MessageService messageService;
    @Mock private WxAuthService wxAuthService;

    @InjectMocks private SiteManagerService service;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void managersPutsOwnerFirstAndMasksContact() {
        FoundSite site = site(1L, 8L);
        when(foundSiteMapper.selectById(1L)).thenReturn(site);
        when(managerMapper.selectList(any())).thenReturn(List.of(manager(1L, 7L), manager(2L, 8L)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7L, "共同"), user(8L, "站长")));

        List<SiteManagerVO> vos = service.managers(1L);

        assertEquals(8L, vos.get(0).getUserId());
        assertTrue(vos.get(0).getIsOwner());
        assertFalse(vos.get(1).getIsOwner());
        assertNotEquals("wx_secret_123", vos.get(0).getContactMasked());
    }

    @Test
    void createInviteRequiresOwner() {
        doThrow(new BusinessException(403, "仅招领站主管理员可执行此操作"))
                .when(sitePermission).requireOwner(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.createInvite(1L, 24));

        assertEquals(403, e.getCode());
        verify(inviteMapper, never()).insert(any(FoundSiteManagerInvite.class));
    }

    @Test
    void createInviteDegradesToPlainCodeWhenQrFails() {
        when(foundSiteMapper.selectById(1L)).thenReturn(site(1L, 7L));
        when(inviteMapper.selectCount(any())).thenReturn(0L);
        when(wxAuthService.getMiniProgramCode(anyString(), any(), any()))
                .thenThrow(new BusinessException("生成小程序码失败"));

        SiteInviteVO vo = service.createInvite(1L, 24);

        assertNotNull(vo.getCode());
        assertEquals(8, vo.getCode().length());
        assertNull(vo.getQrCode());
        verify(inviteMapper).insert(argThat((FoundSiteManagerInvite i) ->
                i.getSiteId().equals(1L)
                        && FoundSiteManagerInvite.STATUS_PENDING.equals(i.getStatus())
                        && i.getExpireTime().isAfter(LocalDateTime.now())));
    }

    @Test
    void acceptInviteConsumesCodeOnceAndJoinsAsCoManager() {
        UserContext.set(9L, "USER");
        when(inviteMapper.update(isNull(), any())).thenReturn(1);
        when(inviteMapper.selectOne(any())).thenReturn(invite("CODE1234", 1L, 7L));
        when(foundSiteMapper.selectById(1L)).thenReturn(site(1L, 7L));

        Long siteId = service.acceptInvite("code1234");

        assertEquals(1L, siteId);
        verify(managerMapper).insert(argThat((FoundSiteManager m) ->
                m.getSiteId().equals(1L) && m.getUserId().equals(9L)));
        verify(messageService, times(2)).send(anyLong(), anyString(), anyString(),
                eq(Message.TYPE_SITE), eq(1L));
    }

    @Test
    void acceptInviteRejectsExpiredCode() {
        UserContext.set(9L, "USER");
        when(inviteMapper.update(isNull(), any())).thenReturn(0);
        FoundSiteManagerInvite expired = invite("CODE1234", 1L, 7L);
        expired.setExpireTime(LocalDateTime.now().minusHours(1));
        when(inviteMapper.selectOne(any())).thenReturn(expired);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.acceptInvite("CODE1234"));

        assertTrue(e.getMessage().contains("已过期"));
        verify(managerMapper, never()).insert(any(FoundSiteManager.class));
    }

    @Test
    void removeManagerRejectsOwner() {
        when(foundSiteMapper.selectById(1L)).thenReturn(site(1L, 7L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.removeManager(1L, 7L));

        assertTrue(e.getMessage().contains("不能移除主管理员"));
        verify(managerMapper, never()).delete(any());
    }

    @Test
    void transferOwnerKeepsOriginalManagerRow() {
        UserContext.set(7L, "USER");
        when(foundSiteMapper.selectById(1L)).thenReturn(site(1L, 7L));
        when(sitePermission.isManager(8L, 1L)).thenReturn(true);
        when(foundSiteMapper.update(isNull(), any())).thenReturn(1);

        service.transferOwner(1L, 8L);

        // 原主降为共同管理员：不删也不新增管理员关系行，只改 foundsite.owner_user_id
        verify(managerMapper, never()).delete(any());
        verify(managerMapper, never()).insert(any(FoundSiteManager.class));
        verify(foundSiteMapper).update(isNull(), any());
        verify(messageService, times(2)).send(anyLong(), anyString(), anyString(),
                eq(Message.TYPE_SITE), eq(1L));
    }

    @Test
    void transferOwnerRejectsNonManagerTarget() {
        when(foundSiteMapper.selectById(1L)).thenReturn(site(1L, 7L));
        when(sitePermission.isManager(99L, 1L)).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.transferOwner(1L, 99L));

        assertTrue(e.getMessage().contains("现有的共同管理员"));
        verify(foundSiteMapper, never()).update(isNull(), any());
    }

    private static FoundSite site(Long id, Long ownerId) {
        FoundSite s = new FoundSite();
        s.setId(id);
        s.setName("测试招领站");
        s.setOwnerUserId(ownerId);
        return s;
    }

    private static FoundSiteManager manager(Long id, Long userId) {
        FoundSiteManager m = new FoundSiteManager();
        m.setId(id);
        m.setSiteId(1L);
        m.setUserId(userId);
        m.setCreateTime(LocalDateTime.now().minusDays(1));
        return m;
    }

    private static FoundSiteManagerInvite invite(String code, Long siteId, Long inviterId) {
        FoundSiteManagerInvite i = new FoundSiteManagerInvite();
        i.setId(5L);
        i.setCode(code);
        i.setSiteId(siteId);
        i.setInviterUserId(inviterId);
        i.setStatus(FoundSiteManagerInvite.STATUS_PENDING);
        i.setExpireTime(LocalDateTime.now().plusHours(24));
        return i;
    }

    private static User user(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        u.setContact("wx_secret_123");
        return u;
    }
}
