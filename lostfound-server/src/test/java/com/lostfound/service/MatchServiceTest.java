package com.lostfound.service;

import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.entity.MatchRecord;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.LostMapper;
import com.lostfound.mapper.MatchRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 匹配打分与存量回刷单测：
 * 类别 30 固定底座 + 时间 25（<=3 天满分线性衰减至 30 天）+ 距离 25（<=2km 满分线性衰减至 50km）
 * + 关键词 20，阈值 45；缺失维度不加分。
 */
@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock private FoundMapper foundMapper;
    @Mock private LostMapper lostMapper;
    @Mock private MatchRecordMapper matchRecordMapper;
    @Mock private MessageService messageService;

    @InjectMocks private MatchService service;

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final BigDecimal LNG = bd(114.305421);
    private static final BigDecimal LAT = bd(30.592935);
    private static final double THRESHOLD = 45;

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private Found found(String title, String desc) {
        Found f = new Found();
        f.setId(1L);
        f.setUserId(10L);
        f.setTitle(title);
        f.setDescription(desc);
        f.setCategory("bag");
        f.setLongitude(LNG);
        f.setLatitude(LAT);
        f.setFoundTime(BASE);
        f.setCreateTime(BASE);
        return f;
    }

    private Lost lost(Long id, Long userId, String title, String desc) {
        Lost l = new Lost();
        l.setId(id);
        l.setUserId(userId);
        l.setTitle(title);
        l.setDescription(desc);
        l.setCategory("bag");
        l.setLongitude(LNG);
        l.setLatitude(LAT);
        l.setLostTime(BASE.minusDays(1));
        l.setCreateTime(BASE.minusDays(1));
        return l;
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ---------------- 打分维度 ----------------

    @Test
    void closeTimeAndPlaceScoreCappedAt100() {
        double score = service.computeScore("黑色双肩背包", "黑色双肩背包",
                LNG, LAT, LNG, LAT, BASE, BASE.minusDays(1));
        assertEquals(100.0, score, 0.01, "时间近+地点近+关键词完全相似应封顶 100");
    }

    @Test
    void distanceFactorDecaysContinuously() {
        assertEquals(1.0, service.distFactor(2), 0.0001);
        double at10 = service.distFactor(10);
        double at20 = service.distFactor(20);
        double at40 = service.distFactor(40);
        assertTrue(at10 > at20 && at20 > at40 && at40 > 0, "2~50km 之间应单调连续衰减");
        assertEquals(0.0, service.distFactor(50), 0.0001);
        assertEquals(0.0, service.distFactor(80), 0.0001);
    }

    @Test
    void timeFactorDecaysContinuously() {
        assertEquals(1.0, service.timeFactor(3), 0.0001);
        double at10 = service.timeFactor(10);
        double at20 = service.timeFactor(20);
        assertTrue(at10 > at20 && at20 > 0, "3~30 天之间应单调连续衰减");
        assertEquals(0.0, service.timeFactor(30), 0.0001);
        assertEquals(0.0, service.timeFactor(45), 0.0001);
    }

    @Test
    void missingDimensionsGiveNoBonus() {
        double score = service.computeScore("蓝色雨伞", "红色水杯",
                null, null, null, null, null, null);
        assertEquals(30.0, score, 0.01, "缺失时间与位置不应给中性分");
        assertTrue(score < THRESHOLD, "纯类别匹配不应入围");
    }

    @Test
    void farApartPairBelowThreshold() {
        double score = service.computeScore("蓝色雨伞", "红色水杯",
                LNG, LAT, bd(116.407), bd(39.904), BASE.minusDays(60), BASE);
        assertTrue(score < THRESHOLD, "距离远+时间远+关键词弱不应入围: " + score);
    }

    @Test
    void closeDateAloneStillQualifies() {
        double score = service.computeScore("黑色钱包", "棕色钱包",
                null, null, null, null, BASE, BASE.minusDays(2));
        assertTrue(score >= THRESHOLD, "日期相近应可独立支撑入围: " + score);
    }

    @Test
    void closePlaceAloneStillQualifies() {
        double score = service.computeScore("黑色钱包", "棕色钱包",
                LNG, LAT, LNG, LAT, BASE.minusDays(60), BASE);
        assertTrue(score >= THRESHOLD, "位置接近应可独立支撑入围: " + score);
    }

    @Test
    void bigramOverlapEdges() {
        assertEquals(0, service.bigramOverlap(null, "黑色背包"), 0.0001);
        assertEquals(0, service.bigramOverlap("  ", "黑色背包"), 0.0001);
        assertEquals(1.0, service.bigramOverlap("黑色背包", "黑色背包"), 0.0001);
        assertTrue(service.bigramOverlap("黑色双肩背包", "深蓝色单肩背包") > 0);
    }

    // ---------------- 发布触发 ----------------

    @Test
    void triggerOnFoundSavesAndNotifiesBothSides() {
        Lost lost = lost(2L, 20L, "黑色双肩背包", null);
        when(lostMapper.selectList(any())).thenReturn(List.of(lost));
        when(matchRecordMapper.insert(any(MatchRecord.class))).thenReturn(1);

        service.triggerOnFound(found("黑色双肩背包", null));

        ArgumentCaptor<MatchRecord> captor = ArgumentCaptor.forClass(MatchRecord.class);
        verify(matchRecordMapper).insert(captor.capture());
        assertEquals(100.00, captor.getValue().getScore().doubleValue(), 0.01);
        assertTrue(captor.getValue().getReason().contains("时间相差 1 天"),
                "reason 应体现日期维度: " + captor.getValue().getReason());
        assertTrue(captor.getValue().getReason().contains("地点相距约 0.0 公里"),
                "reason 应体现位置维度: " + captor.getValue().getReason());
        verify(messageService, times(2)).send(any(), any(), any(), any(), any());
    }

    @Test
    void triggerOnFoundSkipsBelowThreshold() {
        Lost lost = lost(2L, 20L, "完全无关的物品标题", null);
        lost.setLongitude(bd(116.407));
        lost.setLatitude(bd(39.904));
        lost.setLostTime(BASE.minusDays(60));
        when(lostMapper.selectList(any())).thenReturn(List.of(lost));

        service.triggerOnFound(found("黑色双肩背包", null));

        verify(matchRecordMapper, never()).insert(any(MatchRecord.class));
        verify(messageService, never()).send(any(), any(), any(), any(), any());
    }

    // ---------------- 存量回刷 ----------------

    @Test
    void rebuildRejectsNonAdmin() {
        UserContext.set(10L, "USER");
        BusinessException e = assertThrows(BusinessException.class, () -> service.rebuildAll());
        assertEquals(403, e.getCode());
        verify(foundMapper, never()).selectList(any());
        verify(lostMapper, never()).selectList(any());
    }

    @Test
    void rebuildIsIdempotentAndSilent() {
        UserContext.set(9L, "ADMIN");
        Found f = found("黑色双肩背包", null);
        Lost l = lost(2L, 20L, "黑色双肩背包", null);
        when(foundMapper.selectList(any())).thenReturn(List.of(f));
        when(lostMapper.selectList(any())).thenReturn(List.of(l));
        // 第一轮插入成功；第二轮命中唯一键冲突 → 幂等跳过
        when(matchRecordMapper.insert(any(MatchRecord.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("uk_found_lost"));

        Map<String, Integer> first = service.rebuildAll();
        assertEquals(1, first.get("waitingFound"));
        assertEquals(1, first.get("waitingLost"));
        assertEquals(1, first.get("considered"));
        assertEquals(1, first.get("created"));
        assertEquals(0, first.get("skippedExisting"));
        verify(messageService, never()).send(any(), any(), any(), any(), any());

        Map<String, Integer> second = service.rebuildAll();
        assertEquals(0, second.get("created"));
        assertEquals(1, second.get("skippedExisting"));
    }

    @Test
    void rebuildIgnoresBelowThresholdPairs() {
        UserContext.set(9L, "ADMIN");
        Found f = found("黑色双肩背包", null);
        Lost l = lost(2L, 20L, "完全无关的物品标题", null);
        l.setLongitude(bd(116.407));
        l.setLatitude(bd(39.904));
        l.setLostTime(BASE.minusDays(60));
        when(foundMapper.selectList(any())).thenReturn(List.of(f));
        when(lostMapper.selectList(any())).thenReturn(List.of(l));

        Map<String, Integer> result = service.rebuildAll();
        assertEquals(0, result.get("considered"));
        assertEquals(0, result.get("created"));
        verify(matchRecordMapper, never()).insert(any(MatchRecord.class));
    }
}
