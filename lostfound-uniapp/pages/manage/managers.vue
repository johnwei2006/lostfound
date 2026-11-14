<template>
  <view class="managers">
    <site-switcher
      :site="site"
      :sites="sites"
      :current-id="site ? site.id : null"
      :owner="owner"
      @change="onSwitch"
    />

    <view v-if="error" class="lf-card" @tap="load"><text class="lf-muted">{{ error }} · 点击重试</text></view>
    <view v-else-if="loading && !list.length" class="lf-empty">加载中…</view>
    <view v-else-if="!list.length" class="lf-empty">本站暂无管理员</view>

    <view v-for="m in list" :key="m.userId" class="mgr lf-card" @tap="openDetail(m)">
      <view class="mgr__avatar">{{ (m.nickname || '?').charAt(0) }}</view>
      <view class="mgr__body">
        <view class="mgr__line">
          <text class="mgr__name">{{ m.nickname || '未知用户' }}</text>
          <text v-if="m.isOwner" class="lf-tag lf-tag--owner">主管理员</text>
          <text v-else class="lf-tag lf-tag--pending">共同管理员</text>
        </view>
        <text class="lf-muted">加入时间 {{ formatTime(m.joinTime) }}</text>
      </view>
      <view v-if="owner && !m.isOwner" class="mgr__remove" @tap.stop="remove(m)">移除</view>
      <text v-else class="mgr__arrow">›</text>
    </view>

    <view class="footer-bar">
      <button v-if="owner" class="lf-primary-btn" @tap="goInvite">生成邀请码</button>
      <text class="lf-muted footer-hint">仅主管理员可邀请、移除管理员或转让主管理员身份</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { siteManagerApi } from '@/utils/api'
import siteStore from '@/utils/site-store'
import SiteSwitcher from '@/components/site-switcher/site-switcher.vue'

const sites = ref([])
const site = ref(null)
const owner = ref(false)
const list = ref([])
const loading = ref(false)
const error = ref('')

const formatTime = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '—'

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    await siteStore.load()
    sites.value = siteStore.sites
    site.value = siteStore.currentSite
    if (!site.value) { list.value = []; return }
    owner.value = siteStore.isOwner()
    list.value = (await siteManagerApi.managers(site.value.id)) || []
  } catch (e) {
    error.value = e?.message || '管理员列表加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const onSwitch = (id) => { siteStore.select(id); load() }
const openDetail = (m) => uni.navigateTo({ url: `/pages/manage/manager-detail?userId=${m.userId}` })
const goInvite = () => uni.navigateTo({ url: '/pages/manage/invite' })

const remove = (m) => {
  uni.showModal({
    title: '移除共同管理员',
    content: `确定将「${m.nickname || '该用户'}」移出本站管理员吗？对方将立即失去本站管理权限。`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await siteManagerApi.remove(site.value.id, m.userId)
        uni.showToast({ title: '已移除', icon: 'success' })
        load()
      } catch (e) {
        uni.showToast({ title: e?.message || '移除失败', icon: 'none' })
      }
    },
  })
}

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.managers { padding: 20rpx 20rpx 200rpx; }
.mgr { display: flex; align-items: center; gap: 20rpx; }
.mgr__avatar { width: 84rpx; height: 84rpx; border-radius: 50%; background: #eaf1fe; color: #2563eb; font-size: 34rpx; font-weight: 600; text-align: center; line-height: 84rpx; flex-shrink: 0; }
.mgr__body { flex: 1; display: flex; flex-direction: column; gap: 8rpx; min-width: 0; }
.mgr__line { display: flex; align-items: center; gap: 12rpx; }
.mgr__name { font-size: 29rpx; font-weight: 600; }
.mgr__remove { color: #dc2626; font-size: 25rpx; padding: 10rpx 4rpx 10rpx 16rpx; }
.mgr__arrow { color: #c0c8d4; font-size: 36rpx; }
.footer-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 24rpx rgba(15, 23, 42, .06); }
.footer-hint { display: block; text-align: center; margin-top: 12rpx; font-size: 21rpx; }
</style>
