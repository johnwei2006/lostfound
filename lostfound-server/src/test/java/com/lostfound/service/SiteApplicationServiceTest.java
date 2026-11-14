package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.dto.SiteApplicationDTO;
import com.lostfound.dto.SiteApplicationReviewDTO;
import com.lostfound.dto.SiteDTO;
import com.lostfound.dto.SiteEndorsementDTO;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.Message;
import com.lostfound.entity.SiteApplication;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.FoundTransferMapper;
import com.lostfound.mapper.SiteApplicationMapper;
import com.lostfound.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteApplicationServiceTest {

    @Mock private SiteApplicationMapper applicationMapper;
    @Mock private UserMapper userMapper;
    @Mock private FoundSiteManagerMapper foundSiteManagerMapper;
    @Mock private FoundSiteService foundSiteService;
    @Mock private MessageService messageService;

    @Mock private FoundSiteMapper foundSiteMapper;
    @Mock private FoundTransferMapper foundTransferMapper;
    @Mock private FoundMapper foundMapper;
    @Mock private ItemService itemService;
    @Mock private SitePermissionService sitePermission;
    @Mock private CacheEvictService cacheEvict;

    @InjectMocks private SiteApplicationService service;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void submitRejectsDuplicatePending() {
        when(applicationMapper.selectCount(any())).thenReturn(1L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.submit(7L, applicationDTO()));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("已提交过申请"));
        verify(applicationMapper, never()).insert(any(SiteApplication.class));
    }

    @Test
    void reviewRejectsProcessedApplication() {
        SiteApplication app = pendingApplication();
        app.setStatus(SiteApplication.STATUS_APPROVED);
        when(applicationMapper.selectById(1L)).thenReturn(app);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.review(1L, review(true, "同意"), 9L));
        assertEquals("该申请已处理", e.getMessage());
        verify(foundSiteService, never()).create(any(SiteDTO.class));
    }

    @Test
    void approveCreatesSiteAndBindsApplicant() {
        SiteApplication app = pendingApplication();
        when(applicationMapper.selectById(1L)).thenReturn(app);
        when(applicationMapper.update(isNull(), any())).thenReturn(1);
        when(applicationMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        User applicant = new User();
        applicant.setId(7L);
        applicant.setStatus(1);
        applicant.setDeleted(0);
        when(userMapper.selectById(7L)).thenReturn(applicant);
        when(foundSiteService.create(any(SiteDTO.class))).thenReturn(88L);

        Long siteId = service.review(1L, review(true, "同意"), 9L);

        assertEquals(88L, siteId);
        verify(foundSiteService).create(any(SiteDTO.class));
        verify(foundSiteMapper).updateById(argThat((FoundSite s) ->
                Long.valueOf(88L).equals(s.getId()) && Long.valueOf(7L).equals(s.getOwnerUserId())));
        verify(foundSiteManagerMapper).insert(argThat((FoundSiteManager m) ->
                Long.valueOf(88L).equals(m.getSiteId()) && Long.valueOf(7L).equals(m.getUserId())));
        verify(messageService).send(eq(7L), contains("已通过"), anyString(),
                eq(Message.TYPE_SITE), eq(88L));
    }

    @Test
    void rejectDoesNotCreateSiteOrManager() {
        when(applicationMapper.selectById(1L)).thenReturn(pendingApplication());
        when(applicationMapper.update(isNull(), any())).thenReturn(1);

        Long siteId = service.review(1L, review(false, "资料不足"), 9L);

        assertNull(siteId);
        verify(foundSiteService, never()).create(any(SiteDTO.class));
        verify(foundSiteManagerMapper, never()).insert(any(FoundSiteManager.class));
        verify(messageService).send(eq(7L), contains("未通过"), contains("资料不足"),
                eq(Message.TYPE_SITE), isNull());
    }

    @Test
    void updateEndorsementRequiresManager() {
        FoundSiteService realService = new FoundSiteService(foundSiteMapper, foundSiteManagerMapper,
                foundTransferMapper, userMapper, foundMapper, itemService, sitePermission,
                cacheEvict, messageService);
        doThrow(new BusinessException(403, "无权管理该招领站"))
                .when(sitePermission).requireManager(99L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> realService.updateEndorsement(99L, endorsementDTO()));
        assertEquals(403, e.getCode());
        verify(foundSiteMapper, never()).updateById(any(FoundSite.class));
    }

    @Test
    void updateEndorsementStoresCustomCopyForManager() {
        FoundSiteService realService = new FoundSiteService(foundSiteMapper, foundSiteManagerMapper,
                foundTransferMapper, userMapper, foundMapper, itemService, sitePermission,
                cacheEvict, messageService);
        FoundSite site = new FoundSite();
        site.setId(1L);
        when(foundSiteMapper.selectById(1L)).thenReturn(site);
        UserContext.set(7L, "USER");

        realService.updateEndorsement(1L, endorsementDTO());

        assertEquals("社区服务中心", site.getEndorsementOrg());
        assertEquals("长期协助失物临时保管与归还登记", site.getEndorsementText());
        assertNotNull(site.getEndorsementUpdatedAt());
        assertEquals(7L, site.getEndorsementUpdatedBy());
        verify(foundSiteMapper).updateById(site);
    }

    private static SiteApplicationDTO applicationDTO() {
        SiteApplicationDTO dto = new SiteApplicationDTO();
        dto.setName("测试招领站");
        dto.setAddress("测试地址 1 号");
        dto.setReason("服务社区居民");
        dto.setContact("contact@example.com");
        return dto;
    }

    private static SiteApplicationReviewDTO review(boolean approve, String remark) {
        SiteApplicationReviewDTO dto = new SiteApplicationReviewDTO();
        dto.setApprove(approve);
        dto.setRemark(remark);
        return dto;
    }

    private static SiteEndorsementDTO endorsementDTO() {
        SiteEndorsementDTO dto = new SiteEndorsementDTO();
        dto.setEndorsementOrg("社区服务中心");
        dto.setEndorsementText("长期协助失物临时保管与归还登记");
        return dto;
    }

    private static SiteApplication pendingApplication() {
        SiteApplication app = new SiteApplication();
        app.setId(1L);
        app.setApplicantUserId(7L);
        app.setName("测试招领站");
        app.setAddress("测试地址 1 号");
        app.setReason("服务社区居民");
        app.setStatus(SiteApplication.STATUS_PENDING);
        return app;
    }
}
