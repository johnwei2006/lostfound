<template>
  <view class="mng-page">
    <site-context-bar />

    <view class="mng-head">
      <text class="mng-head__site">{{ siteName || '认领申请审批' }}</text>
      <text class="mng-head__desc">仅处理本站物品的认领申请；通过后物品自动完成归还登记。</text>
    </view>

    <view class="mng-filters">
      <text v-for="s in statusOptions" :key="s.value" class="mng-chip" :class="{ 'mng-chip--on': status === s.value }" @tap="setStatus(s.value)">
        {{ s.label }}
      </text>
    </view>

    <view v-if="error" class="mng-card" @tap="reload">
      <text class="mng-card__meta">{{ error }}</text>
      <text class="mng-card__meta">点击重试</text>
    </view>

    <view v-for="c in items" :key="c.id" class="mng-card">
      <view class="mng-card__top">
        <view class="mng-card__main">
          <text class="mng-card__title">{{ c.itemTitle || '招领物品 #' + c.itemId }}</text>
          <text class="mng-card__meta">申请人 {{ c.nickname || '—' }} · {{ formatTime(c.createTime) }}</text>
        </view>
        <text class="lf-tag" :class="tagCls(c.status)">{{ statusText(c.status) }}</text>
      </view>
      <text v-if="c.description" class="mng-card__body">认领说明：{{ c.description }}</text>
      <text v-if="c.contact" class="mng-card__body">联系方式：{{ c.contact }}</text>
      <view v-if="c.status === 'PENDING'" class="mng-card__ops">
        <button class="mng-btn mng-btn--danger" :disabled="busyId === c.id" @tap="review(c, false)">驳回</button>
        <button class="mng-btn mng-btn--primary" :disabled="busyId === c.id" @tap="review(c, true)">通过认领</button>
      </view>
    </view>

    <view v-if="loading" class="mng-foot">加载中…</view>
    <view v-else-if="!items.length && !error" class="lf-empty">暂无认领申请</view>
    <view v-else-if="noMore && items.length" class="mng-foot">共 {{ total }} 条，已全部加载</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { siteApi, claimApi } from '@/utils/api'
import { useSitePagedList } from '@/utils/use-site-list'

const status = ref('PENDING')
const busyId = ref(null)
const statusOptions = [
  { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '全部', value: '' },
]
const STATUS_TEXT = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }
const STATUS_CLS = { PENDING: 'lf-tag--waiting', APPROVED: 'lf-tag--finished', REJECTED: 'lf-tag--offline' }

const fetcher = (siteId, page, size) => siteApi.claims(siteId, {
  page, size, status: status.value || undefined,
})
const { siteName, items, total, loading, noMore, error, reload } = useSitePagedList(fetcher)

const statusText = (s) => STATUS_TEXT[s] || s || '—'
const tagCls = (s) => STATUS_CLS[s] || 'lf-tag--muted'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const setStatus = (v) => { status.value = v; reload() }

const review = (c, approve) => {
  if (busyId.value) return
  uni.showModal({
    title: approve ? '通过认领' : '驳回认领',
    content: approve
      ? `确认将「${c.itemTitle || '该物品'}」交予 ${c.nickname || '申请人'}？`
      : `确认驳回 ${c.nickname || '申请人'} 对「${c.itemTitle || '该物品'}」的认领？`,
    success: (r) => { if (r.confirm) doReview(c, approve) },
  })
}

const doReview = async (c, approve) => {
  busyId.value = c.id
  try {
    // 站点认领队列只返回 found 类型物品
    await claimApi.review(c.itemType || 'found', c.id, approve)
    uni.showToast({ title: approve ? '已通过' : '已驳回', icon: 'success' })
    reload()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    busyId.value = null
  }
}
</script>
