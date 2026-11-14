<template>
  <view class="transfer">
    <view class="warn lf-card">
      <text class="warn__title">转让主管理员身份</text>
      <text class="warn__desc">转让后您将变为本站共同管理员，不再拥有邀请共同管理员、移除管理员与再次转让的权限。每个站点始终只有一位主管理员。</text>
    </view>

    <view class="lf-card">
      <text class="lf-group-title">当前站点</text>
      <text class="site-name">{{ site ? site.name : '—' }}</text>
    </view>

    <view v-if="!owner" class="lf-card">
      <text class="lf-muted">仅主管理员可发起转让。</text>
    </view>
    <template v-else>
      <view class="lf-card">
        <text class="lf-group-title">选择受让的共同管理员</text>
        <view v-if="loading" class="lf-empty">加载中…</view>
        <view v-else-if="!candidates.length" class="lf-empty">本站暂无其它管理员，请先通过邀请码添加共同管理员</view>
        <view
          v-for="m in candidates"
          :key="m.userId"
          class="cand"
          :class="{ active: selected === m.userId }"
          @tap="selected = m.userId"
        >
          <view class="cand__avatar">{{ (m.nickname || '?').charAt(0) }}</view>
          <view class="cand__body">
            <text class="cand__name">{{ m.nickname || '未知用户' }}</text>
            <text class="lf-muted">加入时间 {{ formatTime(m.joinTime) }}</text>
          </view>
          <text class="cand__check">{{ selected === m.userId ? '✓' : '' }}</text>
        </view>
      </view>

      <view class="footer-bar">
        <button class="lf-btn lf-btn--danger" :disabled="!selected || submitting" @tap="confirm">
          {{ submitting ? '转让中…' : '确认转让' }}
        </button>
        <text class="lf-muted footer-hint">操作不可自行撤销，需由新的主管理员再次转让</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { siteManagerApi } from '@/utils/api'
import siteStore from '@/utils/site-store'

const site = ref(null)
const owner = ref(false)
const candidates = ref([])
const selected = ref(null)
const loading = ref(false)
const submitting = ref(false)

const formatTime = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '—'

const load = async () => {
  loading.value = true
  try {
    await siteStore.load()
    site.value = siteStore.currentSite
    owner.value = siteStore.isOwner()
    if (!site.value || !owner.value) { candidates.value = []; return }
    const all = (await siteManagerApi.managers(site.value.id)) || []
    candidates.value = all.filter((m) => !m.isOwner)
  } catch (e) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const confirm = () => {
  const target = candidates.value.find((m) => m.userId === selected.value)
  if (!target || submitting.value) return
  uni.showModal({
    title: '确认转让',
    content: `确定把「${site.value?.name || '本站'}」的主管理员身份转让给「${target.nickname || '该用户'}」吗？转让后您将变为共同管理员。`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await siteManagerApi.transfer(site.value.id, target.userId)
        uni.showToast({ title: '转让成功', icon: 'success' })
        setTimeout(() => uni.redirectTo({ url: '/pages/manage/index' }), 700)
      } catch (e) {
        uni.showToast({ title: e?.message || '转让失败', icon: 'none' })
      } finally {
        submitting.value = false
      }
    },
  })
}

onShow(load)
</script>

<style scoped>
.transfer { padding: 20rpx 20rpx 200rpx; }
.warn { background: #fff7ed; border-left: 8rpx solid #f97316; }
.warn__title { display: block; font-size: 30rpx; font-weight: 600; color: #9a4a12; }
.warn__desc { display: block; margin-top: 12rpx; font-size: 25rpx; line-height: 1.8; color: #9a4a12; }
.site-name { display: block; margin-top: 16rpx; font-size: 31rpx; font-weight: 600; color: #1e3a8a; }
.cand { display: flex; align-items: center; gap: 20rpx; padding: 22rpx 16rpx; margin-top: 16rpx; border-radius: 16rpx; border: 2rpx solid #e2e8f0; }
.cand.active { border-color: #2563eb; background: #eff6ff; }
.cand__avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #eaf1fe; color: #2563eb; font-size: 30rpx; font-weight: 600; text-align: center; line-height: 72rpx; }
.cand__body { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.cand__name { font-size: 29rpx; font-weight: 600; }
.cand__check { width: 40rpx; text-align: center; color: #2563eb; font-size: 32rpx; }
.footer-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 24rpx rgba(15, 23, 42, .06); }
.footer-hint { display: block; text-align: center; margin-top: 12rpx; font-size: 21rpx; }
.footer-bar .lf-btn::after { border: none; }
</style>
