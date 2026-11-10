package com.lostfound.service;

import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundTransfer;
import com.lostfound.mapper.ClaimRequestMapper;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundReturnMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.FoundTransferMapper;
import com.lostfound.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 转交申请校验单测：覆盖路径版（foundId 走 URL）与体版（POST /api/transfer）两类入口的兜底校验。
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private FoundTransferMapper transferMapper;
    @Mock private FoundReturnMapper returnMapper;
    @Mock private ReturnService returnService;
    @Mock private FoundMapper foundMapper;
    @Mock private FoundSiteMapper foundSiteMapper;
    @Mock private FoundSiteManagerMapper managerMapper;
    @Mock private ClaimRequestMapper claimRequestMapper;
    @Mock private UserMapper userMapper;
    @Mock private MessageService messageService;
    @Mock private SitePermissionService sitePermission;
    @Mock private CacheEvictService cacheEvict;

    @InjectMocks private TransferService service;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    private Found waitingFound() {
        Found f = new Found();
        f.setId(583L);
        f.setUserId(10L);
        f.setStatus(Found.STATUS_WAITING);
        f.setTransferStatus(Found.TRANSFER_NONE);
        return f;
    }

    private FoundSite site() {
        FoundSite s = new FoundSite();
        s.setId(39L);
        return s;
    }

    @Test
    void applyRejectsNullFoundId() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(null, 39L, 10L, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void applyRejectsNullSiteId() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(583L, null, 10L, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void applyRejectsNonOwner() {
        UserContext.set(99L, "USER");
        when(foundMapper.selectById(583L)).thenReturn(waitingFound());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(583L, 39L, 99L, null));
        assertEquals(403, e.getCode());
    }

    @Test
    void applyRejectsAlreadyApproved() {
        Found f = waitingFound();
        f.setTransferStatus(FoundTransfer.STATUS_APPROVED);
        when(foundMapper.selectById(583L)).thenReturn(f);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(583L, 39L, 10L, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void applyRejectsFinishedItem() {
        Found f = waitingFound();
        f.setStatus(Found.STATUS_FINISHED);
        when(foundMapper.selectById(583L)).thenReturn(f);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.apply(583L, 39L, 10L, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void applyByOwnerCreatesPendingTransfer() {
        UserContext.set(10L, "USER");
        when(foundMapper.selectById(583L)).thenReturn(waitingFound());
        when(foundSiteMapper.selectById(39L)).thenReturn(site());
        lenient().when(transferMapper.selectCount(any())).thenReturn(0L);
        lenient().when(managerMapper.selectList(any())).thenReturn(java.util.List.of());
        lenient().when(transferMapper.insert(any(FoundTransfer.class))).thenReturn(1);
        lenient().when(foundMapper.updateById(any(Found.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.apply(583L, 39L, 10L, "备注"));
    }
}
