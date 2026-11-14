<template>
  <view class="mng-page">
    <site-context-bar />

    <view class="mng-head">
      <text class="mng-head__site">{{ siteName || '站点物品管理' }}</text>
      <text class="mng-head__desc">仅展示本站已入库物品（站点代发布或转交审批通过），含已下架物品。</text>
    </view>

    <view class="mng-filters">
      <input class="lf-input mng-search" v-model="keyword" placeholder="搜索物品名称 / 描述" @confirm="reload" />
      <view class="mng-filters" style="margin:0; flex-basis:100%">
        <text v-for="s in statusOptions" :key="s.value" class="mng-chip" :class="{ 'mng-chip--on': status === s.value }" @tap="setStatus(s.value)">
          {{ s.label }}
        </text>
      </view>
      <view class="mng-filters" style="margin:0; flex-basis:100%">
        <text v-for="c in categoryOptions" :key="c.value" class="mng-chip" :class="{ 'mng-chip--on': category === c.value }" @tap="setCategory(c.value)">
          {{ c.label }}
        </text>
      </view>
    </view>

    <view v-if="error" class="mng-card" @tap="reload">
      <text class="mng-card__meta">{{ error }}</text>
      <text class="mng-card__meta">点击重试</text>
    </view>

    <view v-for="it in items" :key="it.id" class="mng-card">
      <view class="mng-card__top">
        <view class="mng-card__main">
          <text class="mng-card__title">{{ it.title }}</text>
          <text class="mng-card__meta">{{ it.category || '未分类' }} · {{ it.sourceType === 'SITE' ? '站点代发布' : '转交入库' }} · {{ formatTime(it.createTime) }}</text>
        </view>
        <text class="lf-tag" :class="statusCls(it.status)">{{ statusText(it.status) }}</text>
      </view>
      <text v-if="it.address" class="mng-card__body">{{ it.address }}</text>
      <view class="mng-card__ops">
        <text class="mng-card__meta" style="margin-right:auto">浏览 {{ it.viewCount || 0 }} 次</text>
        <button class="mng-btn mng-btn--ghost" :disabled="busyId === it.id" @tap="toggleShelf(it)">
          {{ it.status === 'OFFLINE' ? '上架' : '下架' }}
        </button>
      </view>
    </view>

    <view v-if="loading" class="mng-foot">加载中…</view>
    <view v-else-if="!items.length && !error" class="lf-empty">暂无符合条件的物品</view>
    <view v-else-if="noMore && items.length" class="mng-foot">共 {{ total }} 条，已全部加载</view>

    <view class="mng-fab" @tap="goPublish">＋ 代发布</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { siteApi, itemApi, CATEGORIES, STATUS_MAP } from '@/utils/api'
import { useSitePagedList } from '@/utils/use-site-list'

const keyword = ref('')
const status = ref('')
const category = ref('')
const busyId = ref(null)
const statusOptions = [
  { label: '全部', value: '' },
  { label: '在架', value: 'WAITING' },
  { label: '已下架', value: 'OFFLINE' },
  { label: '已完成', value: 'FINISHED' },
]
const categoryOptions = [{ label: '全类别', value: '' }].concat(
  (CATEGORIES || []).map((c) => ({ label: typeof c === 'string' ? c : c.name, value: typeof c === 'string' ? c : c.value }))
)

const fetcher = (siteId, page, size) => siteApi.items(siteId, {
  page, size,
  keyword: keyword.value.trim() || undefined,
  status: status.value || undefined,
  category: category.value || undefined,
})
const { siteName, items, total, loading, noMore, error, reload } = useSitePagedList(fetcher)

const statusText = (s) => (STATUS_MAP[s] && STATUS_MAP[s].text) || s || '—'
const statusCls = (s) => (STATUS_MAP[s] && STATUS_MAP[s].cls) || 'lf-tag--muted'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const setStatus = (v) => { status.value = v; reload() }
const setCategory = (v) => { category.value = v; reload() }
const goPublish = () => uni.switchTab({ url: '/pages/publish/publish' })

const toggleShelf = async (it) => {
  if (busyId.value) return
  const next = it.status === 'OFFLINE' ? 'WAITING' : 'OFFLINE'
  busyId.value = it.id
  try {
    await itemApi.changeStatus('found', it.id, next)
    uni.showToast({ title: next === 'OFFLINE' ? '已下架' : '已上架', icon: 'none' })
    reload()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    busyId.value = null
  }
}
</script>
