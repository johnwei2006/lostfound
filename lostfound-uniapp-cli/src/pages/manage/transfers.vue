<template>
  <view class="mng-page">
    <site-context-bar />

    <view class="mng-head">
      <text class="mng-head__site">{{ siteName || '接收转交物品' }}</text>
      <text class="mng-head__desc">审批用户申请转交到本站的招领物品；通过后物品归入本站。</text>
    </view>

    <view class="mng-filters">
      <input class="lf-input mng-search" v-model="keyword" placeholder="搜索物品名称" @confirm="reload" />
      <view class="mng-filters" style="margin:0; flex-basis:100%">
        <text v-for="s in statusOptions" :key="s.value" class="mng-chip" :class="{ 'mng-chip--on': status === s.value }" @tap="setStatus(s.value)">
          {{ s.label }}
        </text>
      </view>
    </view>

    <view v-if="error" class="mng-card" @tap="reload">
      <text class="mng-card__meta">{{ error }}</text>
      <text class="mng-card__meta">点击重试</text>
    </view>

    <view v-for="t in items" :key="t.id" class="mng-card">
      <view class="mng-card__top">
        <view class="mng-card__main">
          <text class="mng-card__title">{{ t.itemTitle || '招领物品 #' + t.foundId }}</text>
          <text class="mng-card__meta">申请人 {{ t.fromNickname || '—' }} · {{ formatTime(t.createTime) }}</text>
        </view>
        <text class="lf-tag" :class="tagCls(t.status)">{{ statusText(t.status) }}</text>
      </view>
      <text v-if="t.remark" class="mng-card__body">申请备注：{{ t.remark }}</text>
      <text v-if="t.reviewerName" class="mng-card__meta">处理人 {{ t.reviewerName }} · {{ formatTime(t.reviewTime) }}</text>
      <view v-if="t.status === 'PENDING'" class="mng-card__ops">
        <button class="mng-btn mng-btn--danger" :disabled="busyId === t.id" @tap="review(t, false)">驳回</button>
        <button class="mng-btn mng-btn--primary" :disabled="busyId === t.id" @tap="review(t, true)">同意接收</button>
      </view>
    </view>

    <view v-if="loading" class="mng-foot">加载中…</view>
    <view v-else-if="!items.length && !error" class="lf-empty">暂无转交申请</view>
    <view v-else-if="noMore && items.length" class="mng-foot">共 {{ total }} 条，已全部加载</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { siteApi, transferApi } from '@/utils/api'
import { useSitePagedList } from '@/utils/use-site-list'

const keyword = ref('')
const status = ref('PENDING')
const busyId = ref(null)
const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '已接收', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已撤销', value: 'CANCELLED' },
  { label: '全部', value: '' },
]
const STATUS_TEXT = {
  PENDING: '待处理', APPROVED: '已接收', REJECTED: '已驳回', CANCELLED: '已撤销',
}
const STATUS_CLS = {
  PENDING: 'lf-tag--waiting', APPROVED: 'lf-tag--finished',
  REJECTED: 'lf-tag--offline', CANCELLED: 'lf-tag--offline',
}

const fetcher = (siteId, page, size) => siteApi.transfers(siteId, {
  page, size, status: status.value || undefined, keyword: keyword.value.trim() || undefined,
})
const { siteName, items, total, loading, noMore, error, reload } = useSitePagedList(fetcher)

const statusText = (s) => STATUS_TEXT[s] || s || '—'
const tagCls = (s) => STATUS_CLS[s] || 'lf-tag--muted'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const setStatus = (v) => { status.value = v; reload() }

const review = (t, approve) => {
  if (busyId.value) return
  if (approve) {
    uni.showModal({
      title: '同意接收',
      content: `确认将「${t.itemTitle || '该物品'}」接收进本站？`,
      success: (r) => { if (r.confirm) doReview(t, true, '') },
    })
    return
  }
  uni.showModal({
    title: '驳回申请',
    editable: true,
    placeholderText: '请填写驳回原因（选填）',
    success: (r) => { if (r.confirm) doReview(t, false, (r.content || '').trim()) },
  })
}

const doReview = async (t, approve, remark) => {
  busyId.value = t.id
  try {
    await transferApi.review(t.id, approve, remark)
    uni.showToast({ title: approve ? '已接收' : '已驳回', icon: 'success' })
    reload()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    busyId.value = null
  }
}
</script>
