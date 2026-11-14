package com.lostfound.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 站点物品上架/下架权限（小程序站点管理员日常管理依赖此放宽）。
 * 覆盖：本人物品 / 本站他人转交物品 / 其它站物品 / 普通用户 / 全局 ADMIN / 非法状态 / LOST 分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemStatusPermissionTest {

    @Mock private FoundMapper foundMapper;
    @Mock private LostMapper lostMapper;
    @Mock private UserMapper userMapper;
    @Mock private FoundSiteMapper foundSiteMapper;
    @Mock private FoundFocusMapper foundFocusMapper;
    @Mock private LostFocusMapper lostFocusMapper;
    @Mock private FoundCommentMapper foundCommentMapper;
    @Mock private LostCommentMapper lostCommentMapper;
    @Mock private MatchService matchService;
    @Mock private ObjectMapper objectMapper;
    @Mock private CacheEvictService cacheEvictService;
    @Mock private TransferService transferService;
    @Mock private SitePermissionService sitePermission;
    @Mock private ItemCountCache itemCountCache;

    @InjectMocks private ItemService service;

    private static final Long ITEM_ID = 583L;
    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 99L;
    private static final Long SITE_ID = 39L;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private Found found(Long ownerId, Long siteId) {
        Found f = new Found();
        f.setId(ITEM_ID);
        f.setUserId(ownerId);
        f.setSiteId(siteId);
        f.setStatus(Found.STATUS_OFFLINE);
        return f;
    }

    private Lost lost(Long ownerId) {
        Lost l = new Lost();
        l.setId(ITEM_ID);
        l.setUserId(ownerId);
        l.setStatus(Lost.STATUS_OFFLINE);
        return l;
    }

    private BusinessException expectDenied(Runnable action, int code) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getCode());
        return e;
    }

    @Test
    void ownerCanToggleOwnItem() {
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, null));

        service.changeShelfStatus("FOUND", ITEM_ID, OWNER_ID, false, Found.STATUS_WAITING);

        verify(foundMapper).updateById(any(Found.class));
        verifyNoInteractions(sitePermission);
        verify(cacheEvictService).evictLatest();
    }

    @Test
    void siteManagerCanToggleTransferredItemOwnedBySomeoneElse() {
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, SITE_ID));
        when(sitePermission.canManage(SITE_ID)).thenReturn(true);

        service.changeShelfStatus("FOUND", ITEM_ID, OTHER_ID, false, Found.STATUS_WAITING);

        verify(foundMapper).updateById(argThat((Found f) -> Found.STATUS_WAITING.equals(f.getStatus())));
        verify(sitePermission).canManage(SITE_ID);
        verify(cacheEvictService).evictLatest();
    }

    @Test
    void managerOfAnotherSiteIsDenied() {
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, SITE_ID));
        when(sitePermission.canManage(SITE_ID)).thenReturn(false);

        BusinessException e = expectDenied(
                () -> service.changeShelfStatus("FOUND", ITEM_ID, OTHER_ID, false, Found.STATUS_WAITING), 403);
        assertTrue(e.getMessage().contains("无权"));

        verify(foundMapper, never()).updateById(any(Found.class));
        verify(cacheEvictService, never()).evictLatest();
    }

    @Test
    void plainUserCannotToggleItemWithoutSite() {
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, null));

        expectDenied(
                () -> service.changeShelfStatus("FOUND", ITEM_ID, OTHER_ID, false, Found.STATUS_WAITING), 403);

        verifyNoInteractions(sitePermission);
        verify(foundMapper, never()).updateById(any(Found.class));
    }

    @Test
    void globalAdminCanToggleAnyItem() {
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, SITE_ID));

        service.changeShelfStatus("FOUND", ITEM_ID, OTHER_ID, true, Found.STATUS_OFFLINE);

        verify(foundMapper).updateById(any(Found.class));
        verifyNoInteractions(sitePermission);
    }

    @Test
    void invalidStatusIsRejectedBeforeAnyQuery() {
        for (String bad : new String[]{"FINISHED", "CLAIMED", "", "ANY", null}) {
            expectDenied(
                    () -> service.changeShelfStatus("FOUND", ITEM_ID, OWNER_ID, false, bad), 400);
        }
        verifyNoInteractions(foundMapper, sitePermission, cacheEvictService);
    }

    @Test
    void lostBranchStillRequiresOwnership() {
        when(lostMapper.selectById(ITEM_ID)).thenReturn(lost(OWNER_ID));

        expectDenied(
                () -> service.changeShelfStatus("LOST", ITEM_ID, OTHER_ID, false, Lost.STATUS_WAITING), 403);

        verify(lostMapper, never()).updateById(any(Lost.class));
        verifyNoInteractions(sitePermission);
    }

    @Test
    void claimApprovalStillCanSetFinishedViaLegacyMethod() {
        // ClaimService.review(approve=true) 走 changeStatus(..., "FINISHED")，不受上下架白名单影响
        when(foundMapper.selectById(ITEM_ID)).thenReturn(found(OWNER_ID, SITE_ID));

        service.changeStatus("FOUND", ITEM_ID, OTHER_ID, true, Found.STATUS_FINISHED);

        verify(foundMapper).updateById(argThat((Found f) -> Found.STATUS_FINISHED.equals(f.getStatus())));
        verify(cacheEvictService).evictLatest();
    }
}
