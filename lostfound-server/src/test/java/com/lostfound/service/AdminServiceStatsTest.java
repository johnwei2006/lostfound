package com.lostfound.service;

import com.lostfound.mapper.ClaimRequestMapper;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.LostMapper;
import com.lostfound.mapper.MatchRecordMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.AdminStatsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 统计合并 SQL、短 TTL 缓存与按 key 失效的等价性断言。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceStatsTest {

    @Mock private FoundMapper foundMapper;
    @Mock private LostMapper lostMapper;
    @Mock private UserMapper userMapper;
    @Mock private MatchRecordMapper matchRecordMapper;
    @Mock private ClaimRequestMapper claimRequestMapper;
    @Mock private MessageService messageService;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @InjectMocks private AdminService service;

    @Test
    void cachedOverviewSkipsAllStatisticsQueries() {
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager);
        AdminStatsVO cached = new AdminStatsVO();
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(cached);
        when(cacheManager.getCache("adminOverview")).thenReturn(cache);
        when(cache.get(CacheEvictService.OVERVIEW_CACHE_KEY)).thenReturn(wrapper);

        assertSame(cached, service.stats());
        verify(foundMapper, never()).selectMaps(any());
        verify(lostMapper, never()).selectMaps(any());
        verify(userMapper, never()).selectCount(any());
    }

    @Test
    void mergedAggregationKeepsContractAndUsesFewQueries() {
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager);
        when(cacheManager.getCache("adminOverview")).thenReturn(cache);
        when(cache.get(any())).thenReturn(null);

        Map<String, Object> foundAgg = Map.of("total", 12L, "waiting", 7L, "pending", 2L);
        Map<String, Object> lostAgg = Map.of("total", 9L, "waiting", 4L, "pending", 3L);
        List<Map<String, Object>> empty = List.of();
        List<Map<String, Object>> categories = List.of(Map.of("category", "手机通讯", "cnt", 5L));

        when(foundMapper.selectMaps(any())).thenReturn(List.of(foundAgg), empty, categories);
        when(lostMapper.selectMaps(any())).thenReturn(List.of(lostAgg), empty, empty);
        when(userMapper.selectCount(isNull())).thenReturn(3L);
        when(matchRecordMapper.selectCount(isNull())).thenReturn(4L);
        when(claimRequestMapper.selectCount(any())).thenReturn(5L);

        AdminStatsVO vo = service.stats();

        assertEquals(3L, vo.getUserCount());
        assertEquals(12L, vo.getFoundCount());
        assertEquals(9L, vo.getLostCount());
        assertEquals(7L, vo.getWaitingFound());
        assertEquals(4L, vo.getWaitingLost());
        assertEquals(5L, vo.getPendingReview());
        assertEquals(4L, vo.getMatchCount());
        assertEquals(5L, vo.getClaimPending());
        assertEquals(14, vo.getTrend().size(), "近 14 天趋势必须补齐缺失日期");
        String lastDate = String.valueOf(vo.getTrend().get(13).get("date"));
        assertTrue(lastDate.matches("\\d{2}-\\d{2}"), "日期格式保持 MM-dd，实际=" + lastDate);
        assertEquals(1, vo.getCategoryStats().size());
        assertEquals(5L, vo.getCategoryStats().get(0).get("count"));

        // 每张表只发 3 条统计 SQL（条件聚合 / 日期分组 / 类别分组），替代原先 39 条
        verify(foundMapper, times(3)).selectMaps(any());
        verify(lostMapper, times(3)).selectMaps(any());
        verify(cache).put(eq(CacheEvictService.OVERVIEW_CACHE_KEY), any());
    }

    @Test
    void evictAdminOverviewAndSiteStatsUseTheirOwnKeys() {
        CacheManager cm = mock(CacheManager.class);
        Cache overview = mock(Cache.class);
        Cache siteStats = mock(Cache.class);
        when(cm.getCache("adminOverview")).thenReturn(overview);
        when(cm.getCache("siteStats")).thenReturn(siteStats);
        CacheEvictService svc = new CacheEvictService(cm);

        svc.evictAdminOverview();
        verify(overview).evict(CacheEvictService.OVERVIEW_CACHE_KEY);

        svc.evictSiteStats(7L);
        verify(siteStats).evict(7L);
    }

    @Test
    void evictAllClearsEveryBusinessCache() {
        CacheManager cm = mock(CacheManager.class);
        Cache c = mock(Cache.class);
        when(cm.getCache(anyString())).thenReturn(c);
        CacheEvictService svc = new CacheEvictService(cm);

        svc.evictLatest();

        verify(cm, times(4)).getCache(anyString());
        verify(c, times(4)).clear();
    }
}
