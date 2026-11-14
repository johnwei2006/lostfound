<template>
  <view class="apps">
    <view class="filters lf-card">
      <view
        v-for="f in filters"
        :key="f.value"
        class="filter"
        :class="{ active: filter === f.value }"
        @tap="filter = f.value"
      >{{ f.label }}</view>
    </view>

    <view v-if="loading && !list.length" class="lf-empty">加载中…</view>
    <view v-else-if="!filtered.length" class="lf-empty">暂无{{ filter ? '该状态的' : '' }}建站申请</view>

    <view v-for="a in filtered" :key="a.id" class="app lf-card">
      <view class="app__head" @tap="toggle(a.id)">
        <view class="app__title-row">
          <text class="app__name">{{ a.name }}</text>
          <text class="lf-tag" :class="statusClass(a.status)">{{ statusLabel(a.status) }}</text>
        </view>
        <text class="lf-muted app__addr">{{ a.address }}</text>
        <text class="lf-muted app__time">{{ (a.createTime || '').slice(0, 16) }}</text>
      </view>

      <view v-if="expanded === a.id" class="app__detail">
        <view class="lf-field"><text class="lf-field__label">联系电话</text><text class="lf-field__value">{{ a.phone || '未填写' }}</text></view>
        <view class="lf-field"><text class="lf-field__label">开放时间</text><text class="lf-field__value">{{ a.openTime || '未填写' }}</text></view>
        <view class="lf-field"><text class="lf-field__label">站点简介</text><text class="lf-field__value">{{ a.description || '未填写' }}</text></view>
        <view class="lf-field"><text class="lf-field__label">申请理由</text><text class="lf-field__value">{{ a.reason }}</text></view>
        <view class="lf-field" v-if="a.reviewRemark"><text class="lf-field__label">审核意见</text><text class="lf-field__value">{{ a.reviewRemark }}</text></view>
        <view class="lf-field" v-if="a.reviewTime"><text class="lf-field__label">审核时间</text><text class="lf-field__value">{{ String(a.reviewTime).slice(0, 16) }}</text></view>
      </view>

      <view class="app__actions">
        <button v-if="a.status === 'PENDING'" class="mini-btn" @tap="withdraw(a)">撤回申请</button>
        <button v-if="a.status === 'REJECTED'" class="mini-btn mini-btn--primary" @tap="reapply(a)">修改后重提</button>
        <button v-if="a.status === 'APPROVED' && a.createdSiteId" class="mini-btn mini-btn--primary" @tap="openSite(a)">进入站点管理</button>
      </view>
    </view>

    <view class="footer-bar">
      <button class="lf-primary-btn" @tap="goNew">申请新的招领站</button>
      <text class="lf-muted footer-hint">审核通过后可继续申请下一站点；同时仅保留一条待审申请</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { siteApplicationApi } from '@/utils/api'

const filters = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
]
const filter = ref('')
const list = ref([])
const expanded = ref(null)
const loading = ref(false)

const filtered = computed(() =>
  filter.value ? list.value.filter((a) => a.status === filter.value) : list.value)

const statusLabel = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s || '未知')
const statusClass = (s) => ({
  PENDING: 'lf-tag--pending', APPROVED: 'lf-tag--approved', REJECTED: 'lf-tag--rejected',
}[s] || 'lf-tag--muted')

const load = async () => {
  loading.value = true
  try {
    list.value = (await siteApplicationApi.mine()) || []
  } catch (e) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const toggle = (id) => { expanded.value = expanded.value === id ? null : id }

const withdraw = (a) => {
  uni.showModal({
    title: '撤回申请',
    content: '撤回后本次申请将不再审核，确定继续吗？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await siteApplicationApi.withdraw(a.id)
        uni.showToast({ title: '已撤回', icon: 'success' })
        load()
      } catch (e) {
        uni.showToast({ title: e?.message || '撤回失败', icon: 'none' })
      }
    },
  })
}

const reapply = (a) => {
  const keys = ['name', 'address', 'longitude', 'latitude', 'phone', 'openTime', 'description', 'reason', 'contact']
  const draft = {}
  keys.forEach((k) => { draft[k] = a[k] })
  uni.setStorageSync('lf_site_apply_draft', draft)
  uni.navigateTo({ url: '/pages/site-apply/site-apply' })
}

const goNew = () => {
  if (list.value.some((a) => a.status === 'PENDING')) {
    uni.showToast({ title: '已有待审申请，请等待审核', icon: 'none' })
    return
  }
  uni.navigateTo({ url: '/pages/site-apply/site-apply' })
}

const openSite = (a) => uni.navigateTo({ url: `/pages/manage/index?site=${a.createdSiteId}` })

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.apps { padding: 20rpx 20rpx 200rpx; }
.filters { display: flex; gap: 12rpx; padding: 18rpx; }
.filter { flex: 1; text-align: center; padding: 12rpx 0; border-radius: 999rpx; font-size: 25rpx; color: #64748b; background: #f8fafc; transition: all .2s; }
.filter.active { background: #2563eb; color: #fff; font-weight: 600; }
.app__head { display: flex; flex-direction: column; gap: 8rpx; }
.app__title-row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.app__name { font-size: 31rpx; font-weight: 600; flex: 1; }
.app__addr { display: block; }
.app__time { display: block; }
.app__detail { margin-top: 20rpx; padding-top: 10rpx; border-top: 2rpx dashed #e2e8f0; }
.app__actions { display: flex; gap: 16rpx; margin-top: 20rpx; }
.mini-btn { flex: 1; background: #f1f5f9; color: #475569; border-radius: 999rpx; font-size: 25rpx; padding: 14rpx 0; }
.mini-btn--primary { background: #eaf1fe; color: #2563eb; }
.mini-btn::after { border: none; }
.footer-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 24rpx rgba(15, 23, 42, .06); }
.footer-hint { display: block; text-align: center; margin-top: 12rpx; font-size: 21rpx; }
</style>
