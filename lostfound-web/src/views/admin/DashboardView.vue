<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { AdminStats } from '@/types'

const stats = ref<AdminStats | null>(null)
const trendEl = ref<HTMLElement>()
const pieEl = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

onMounted(async () => {
  stats.value = await adminApi.stats()
  if (stats.value) {
    initTrend(stats.value.trend)
    initPie(stats.value.categoryStats)
  }
})

const initTrend = (trend: AdminStats['trend']) => {
  if (!trendEl.value) return
  trendChart = echarts.init(trendEl.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['拾物', '失物'], right: 10, top: 0 },
    grid: { left: 40, right: 20, top: 34, bottom: 28 },
    xAxis: { type: 'category', data: trend.map((t) => t.date), axisLine: { lineStyle: { color: '#d3dae6' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eef1f6' } } },
    series: [
      {
        name: '拾物',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: trend.map((t) => t.found),
        itemStyle: { color: '#10b981' },
        areaStyle: { color: 'rgba(16,185,129,0.10)' },
      },
      {
        name: '失物',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        data: trend.map((t) => t.lost),
        itemStyle: { color: '#f97316' },
        areaStyle: { color: 'rgba(249,115,22,0.10)' },
      },
    ],
  })
}

const initPie = (categoryStats: AdminStats['categoryStats']) => {
  if (!pieEl.value) return
  pieChart = echarts.init(pieEl.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
    legend: { orient: 'vertical', right: 0, top: 'center', textStyle: { fontSize: 12 } },
    color: ['#2563eb', '#3b82f6', '#f97316', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444', '#64748b'],
    series: [
      {
        type: 'pie',
        radius: ['44%', '70%'],
        center: ['38%', '50%'],
        label: { show: false },
        data: categoryStats.map((c) => ({ name: c.category, value: c.count })),
      },
    ],
  })
}

const resize = () => {
  trendChart?.resize()
  pieChart?.resize()
}
window.addEventListener('resize', resize)
onBeforeUnmount(() => window.removeEventListener('resize', resize))

const cards = (s: AdminStats) => [
  { label: '注册用户', value: s.userCount, color: '#2563eb' },
  { label: '拾物总数', value: s.foundCount, color: '#10b981' },
  { label: '失物总数', value: s.lostCount, color: '#f97316' },
  { label: '待审核', value: s.pendingReview, color: '#f59e0b' },
  { label: '匹配记录', value: s.matchCount, color: '#8b5cf6' },
  { label: '待处理申请', value: s.claimPending, color: '#ef4444' },
]
</script>

<template>
  <div v-if="stats">
    <h2 style="margin-bottom: 18px">数据统计</h2>
    <div class="stat-cards">
      <div v-for="c in cards(stats)" :key="c.label" class="stat-card lf-card lf-card--hover">
        <span class="stat-dot" :style="{ background: c.color }"></span>
        <div>
          <b>{{ c.value }}</b>
          <p>{{ c.label }}</p>
        </div>
      </div>
    </div>
    <div class="charts">
      <div class="lf-card chart-card">
        <h4>近 14 天发布趋势</h4>
        <div ref="trendEl" class="chart"></div>
      </div>
      <div class="lf-card chart-card">
        <h4>物品类别分布</h4>
        <div ref="pieEl" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}
.stat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.stat-card b {
  font-size: 22px;
  line-height: 1.2;
}
.stat-card p {
  font-size: 12.5px;
  color: var(--lf-text-3);
}
.charts {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}
.chart-card {
  padding: 18px;
}
.chart-card h4 {
  font-size: 14.5px;
  margin-bottom: 8px;
}
.chart {
  height: 320px;
}
@media (max-width: 960px) {
  .charts {
    grid-template-columns: 1fr;
  }
}
</style>
