<template>
  <view class="detail">
    <view v-if="error" class="lf-card" @tap="load"><text class="lf-muted">{{ error }} · 点击重试</text></view>
    <view v-else-if="!manager" class="lf-empty">加载中…</view>

    <template v-else>
      <view class="hero lf-card">
        <view class="hero__avatar">{{ (manager.nickname || '?').charAt(0) }}</view>
        <text class="hero__name">{{ manager.nickname || '未知用户' }}</text>
        <text class="lf-tag" :class="manager.isOwner ? 'lf-tag--owner' : 'lf-tag--pending'">
          {{ manager.isOwner ? '主管理员' : '共同管理员' }}
        </text>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">基本信息</text>
        <view class="lf-field"><text class="lf-field__label">用户 ID</text><text class="lf-field__value">{{ manager.userId }}</text></view>
        <view class="lf-field"><text class="lf-field__label">加入时间</text><text class="lf-field__value">{{ formatTime(manager.joinTime) }}</text></view>
        <view class="lf-field"><text class="lf-field__label">联系方式</text><text class="lf-field__value">{{ manager.contactMasked || '未填写（已脱敏展示）' }}</text></view>
        <view class="lf-field"><text class="lf-field__label">当前站点</text><text class="lf-field__value">{{ site ? site.name : '—' }}</text></view>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">权限说明</text>
        <text class="perm lf-muted">
          共同管理员可查看管理员与站点概览，并在网页版后台管理物品、转交审批与归还登记；邀请其它管理员、移除管理员与转让主管理员仅主管理员可操作。
        </text>
      </view>

      <view v-if="owner && !manager.isOwner" class="danger-zone">
        <button class="lf-btn lf-btn--danger" @tap="remove">移除该管理员</button>
        <button class="lf-btn lf-btn--ghost danger-btn" @tap="transfer">转让主管理员给 TA</button>
      </view>
      <view v-else class="note lf-muted">仅本站主管理员可在此页面执行管理操作</view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { siteManagerApi } from '@/utils/api'
import siteStore from '@/utils/site-store'

const manager = ref(null)
const site = ref(null)
const owner = ref(false)
const error = ref('')
let userId = null

const formatTime = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '—'

onLoad((options) => { userId = Number(options?.userId) || null })

const load = async () => {
  error.value = ''
  try {
    await siteStore.load()
    site.value = siteStore.currentSite
    owner.value = siteStore.isOwner()
    if (!site.value || !userId) { error.value = '缺少站点或管理员信息'; return }
    manager.value = await siteManagerApi.detail(site.value.id, userId)
  } catch (e) {
    error.value = e?.message || '管理员详情加载失败'
  }
}

const remove = () => {
  uni.showModal({
    title: '移除共同管理员',
    content: `确定将「${manager.value?.nickname || '该用户'}」移出本站吗？`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await siteManagerApi.remove(site.value.id, userId)
        uni.showToast({ title: '已移除', icon: 'success' })
        setTimeout(() => uni.navigateBack({ delta: 1 }), 600)
      } catch (e) {
        uni.showToast({ title: e?.message || '移除失败', icon: 'none' })
      }
    },
  })
}

const transfer = () => {
  uni.showModal({
    title: '转让主管理员',
    content: `确定把「${site.value?.name || '本站'}」的主管理员身份转让给「${manager.value?.nickname || '该用户'}」吗？转让后您将变为共同管理员，且不能再邀请或移除管理员。`,
    confirmColor: '#dc2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await siteManagerApi.transfer(site.value.id, userId)
        uni.showToast({ title: '转让成功', icon: 'success' })
        setTimeout(() => uni.redirectTo({ url: '/pages/manage/index' }), 700)
      } catch (e) {
        uni.showToast({ title: e?.message || '转让失败', icon: 'none' })
      }
    },
  })
}

onShow(load)
</script>

<style scoped>
.detail { padding: 20rpx; }
.hero { display: flex; align-items: center; flex-direction: column; padding: 44rpx 24rpx; }
.hero__avatar { width: 120rpx; height: 120rpx; border-radius: 50%; background: linear-gradient(135deg, #dbeafe, #eff6ff); color: #2563eb; font-size: 48rpx; font-weight: 600; text-align: center; line-height: 120rpx; }
.hero__name { margin-top: 20rpx; font-size: 34rpx; font-weight: 600; }
.hero .lf-tag { margin-top: 12rpx; }
.perm { display: block; margin-top: 16rpx; line-height: 1.8; }
.danger-zone { margin-top: 30rpx; }
.danger-btn { margin-top: 20rpx; color: #dc2626; }
.note { display: block; text-align: center; margin-top: 40rpx; }
</style>
