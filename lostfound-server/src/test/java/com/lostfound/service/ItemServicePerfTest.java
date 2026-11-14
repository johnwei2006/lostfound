package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.dto.ItemQuery;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.Lost;
import com.lostfound.mapper.*;
import com.lostfound.vo.ItemVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 查询性能优化的契约与降级路径单测：
 * 默认开关全关、COUNT 缓存命中不查库、站点名一次批量、bbox 边界正确。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemServicePerfTest {

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
    @Mock private ItemCountCache itemCountCache;

    @InjectMocks private ItemService service;

    private static ItemQuery query() {
        ItemQuery q = new ItemQuery();
        q.setPage(1);
        q.setSize(10);
        return q;
    }

    @Test
    void fulltextIsDisabledByDefault() {
        ItemQuery q = query();
        q.setKeyword("手机");
        assertFalse(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(service, "shouldUseFulltext", q)));
    }

    @Test
    void fulltextFallsBackForShortOrSpecialKeywords() {
        ReflectionTestUtils.setField(service, "fulltextEnabled", true);
        ReflectionTestUtils.setField(service, "fulltextMinKeywordLength", 2);

        ItemQuery shortKw = query();
        shortKw.setKeyword("a");
        assertFalse(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(service, "shouldUseFulltext", shortKw)));

        ItemQuery special = query();
        special.setKeyword("手機!");
        assertFalse(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(service, "shouldUseFulltext", special)));

        ItemQuery normal = query();
        normal.setKeyword("手机");
        assertTrue(Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(service, "shouldUseFulltext", normal)));
    }

    @Test
    void countCacheKeyIsScopedByMineUser() {
        ItemQuery a = query();
        a.setMine(true);
        a.setCurrentUserId(1L);
        ItemQuery b = query();
        b.setMine(true);
        b.setCurrentUserId(2L);
        String ka = ReflectionTestUtils.invokeMethod(service, "countCacheKey", "FOUND", a, false);
        String kb = ReflectionTestUtils.invokeMethod(service, "countCacheKey", "FOUND", b, false);
        assertNotEquals(ka, kb, "只看我的场景必须按用户隔离，避免跨用户串 total");

        ItemQuery c = query();
        String kc = ReflectionTestUtils.invokeMethod(service, "countCacheKey", "FOUND", c, false);
        assertNotEquals(ka, kc);
    }

    @Test
    void countCacheHitAvoidsCountQueryAndKeepsExactTotal() {
        ReflectionTestUtils.setField(service, "itemCountCache", itemCountCache);
        ReflectionTestUtils.setField(service, "countCacheSeconds", 30L);
        when(itemCountCache.get(any())).thenReturn(5L);
        when(foundMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        com.lostfound.common.PageResult<ItemVO> result = service.page("FOUND", query(), null);

        assertEquals(5L, result.getTotal());
        assertEquals(1L, result.getPages());
        verify(foundMapper, never()).selectCount(any());
        verify(itemCountCache, never()).put(any(), anyLong());
    }

    @Test
    void convertFoundLoadsSiteNamesInOneBatch() {
        Found f1 = new Found();
        f1.setId(1L);
        f1.setUserId(10L);
        f1.setSiteId(100L);
        Found f2 = new Found();
        f2.setId(2L);
        f2.setUserId(10L);
        f2.setSiteId(200L);

        FoundSite s1 = new FoundSite();
        s1.setId(100L);
        s1.setName("一号招领站");
        FoundSite s2 = new FoundSite();
        s2.setId(200L);
        s2.setName("二号招领站");

        when(foundFocusMapper.selectMaps(any())).thenReturn(List.of());
        when(foundCommentMapper.selectMaps(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        when(foundSiteMapper.selectBatchIds(any())).thenReturn(List.of(s1, s2));

        List<ItemVO> vos = service.convertFound(List.of(f1, f2), null);

        assertEquals(2, vos.size());
        assertEquals("一号招领站", vos.get(0).getSiteName());
        assertEquals("二号招领站", vos.get(1).getSiteName());
        verify(foundSiteMapper, times(1)).selectBatchIds(any());
        verify(foundSiteMapper, never()).selectById(any());
    }

    @Test
    void boundingBoxKeepsNullCoordinatesAndSplitsAntimeridian() {
        ReflectionTestUtils.setField(service, "distanceBboxKm", 50.0);

        QueryWrapper<Found> cross = new QueryWrapper<>();
        ItemQuery qCross = query();
        qCross.setUserLat(BigDecimal.valueOf(0));
        qCross.setUserLng(BigDecimal.valueOf(179.9));
        ReflectionTestUtils.invokeMethod(service, "applyBoundingBox", cross, qCross);
        String crossSql = cross.getSqlSegment();
        assertTrue(crossSql.contains("IS NULL"), "无坐标记录必须保留");
        assertTrue(crossSql.contains("OR"), "需要 OR 分支保留无坐标并拆跨经度");
        int betweenCount = crossSql.split("BETWEEN", -1).length - 1;
        assertTrue(betweenCount >= 3, "跨 ±180 时经度应拆成两段 BETWEEN，实际=" + betweenCount);

        QueryWrapper<Lost> polar = new QueryWrapper<>();
        ItemQuery qPolar = query();
        qPolar.setUserLat(BigDecimal.valueOf(89.9));
        qPolar.setUserLng(BigDecimal.ZERO);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "applyBoundingBox", polar, qPolar));
        assertTrue(polar.getSqlSegment().contains("BETWEEN"));
    }
}
