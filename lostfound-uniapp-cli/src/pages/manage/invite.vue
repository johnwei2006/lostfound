<template>
  <view class="invite">
    <view class="lf-card">
      <text class="lf-group-title">当前站点</text>
      <text class="site-name">{{ site ? site.name : '—' }}</text>
      <text class="lf-muted">邀请码仅可邀请共同管理员；对方确认后自动加入，共同管理员不能再邀请他人。</text>
    </view>

    <view v-if="!owner" class="lf-card">
      <text class="lf-muted">仅主管理员可以生成与撤销邀请码。如需调整管理员，请联系本站主管理员。</text>
    </view>

    <template v-else>
      <view class="lf-card">
        <text class="lf-group-title">生成邀请码</text>
        <view class="ttls">
          <view v-for="h in ttlOptions" :key="h" class="ttl" :class="{ active: ttl === h }" @tap="ttl = h">
            {{ h }} 小时
          </view>
        </view>
        <button class="lf-primary-btn gen-btn" :class="{ 'lf-btn--busy': generating }" :disabled="generating" @tap="generate">
          {{ generating ? '生成中…' : '生成新的邀请码' }}
        </button>
        <text class="lf-muted gen-hint">小程序码依赖微信接口与新页面发布状态，生成失败时可直接复制邀请码</text>
      </view>

      <view v-if="latest" class="lf-card code-card">
        <text class="lf-group-title">最新邀请码</text>
        <text class="code" @tap="copy(latest.code)">{{ latest.code }}</text>
        <view class="code-actions">
          <button class="code-btn" @tap="copy(latest.code)">复制邀请码</button>
          <button v-if="latest.qrCode" class="code-btn code-btn--plain" @tap="copy(latest.code)">扫码也可加入</button>
        </view>
        <image
          v-if="latest.qrCode && !qrFailed"
          class="qr"
          :src="latest.qrCode"
          mode="widthFix"
          @error="qrFailed = true"
        />
        <text v-else-if="latest.qrCode && qrFailed" class="lf-muted qr-hint">小程序码暂无法显示，请直接复制邀请码发送给对方</text>
        <text v-else class="lf-muted qr-hint">小程序码未生成，已降级为纯邀请码，不影响使用</text>
        <view class="lf-field"><text class="lf-field__label">有效期至</text><text class="lf-field__value">{{ formatTime(latest.expireTime) }}</text></view>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">邀请码记录</text>
        <view v-if="!inviteList.length" class="lf-empty">暂无邀请码</view>
        <view v-for="i in inviteList" :key="i.id" class="inv-row">
          <view class="inv-row__main">
            <text class="inv-row__code">{{ i.code }}</text>
            <text class="lf-muted">{{ formatTime(i.createTime) }} 生成 · {{ formatTime(i.expireTime) }} 到期</text>
          </view>
          <text class="lf-tag" :class="statusClass(i.status)">{{ statusLabel(i.status) }}</text>
          <text v-if="i.status === 'PENDING'" class="inv-row__revoke" @tap="revoke(i)">撤销</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { siteManagerApi } from '@/utils/api'
import siteStore from '@/utils/site-store'

const ttlOptions = [24, 72, 168]
const ttl = ref(24)
const site = ref(null)
const owner = ref(false)
const latest = ref(null)
const qrFailed = ref(false)
const inviteList = ref([])
const generating = ref(false)

const formatTime = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '—'
const statusLabel = (s) => ({ PENDING: '待使用', USED: '已使用', REVOKED: '已撤销', EXPIRED: '已过期' }[s] || s)
const statusClass = (s) => ({
  PENDING: 'lf-tag--pending', USED: 'lf-tag--approved',
  REVOKED: 'lf-tag--muted', EXPIRED: 'lf-tag--muted',
}[s] || 'lf-tag--muted')

const loadInvites = async () => {
  if (!site.value || !owner.value) return
  inviteList.value = (await siteManagerApi.invites(site.value.id)) || []
}

const load = async () => {
  try {
    await siteStore.load()
    site.value = siteStore.currentSite
    owner.value = siteStore.isOwner()
    await loadInvites()
  } catch (e) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    uni.stopPullDownRefresh()
  }
}

const generate = async () => {
  if (generating.value || !site.value) return
  generating.value = true
  qrFailed.value = false
  try {
    latest.value = await siteManagerApi.createInvite(site.value.id, ttl.value)
    await loadInvites()
    uni.showToast({ title: '邀请码已生成', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: e?.message || '生成失败', icon: 'none' })
  } finally {
    generating.value = false
  }
}

const copy = (code) => {
  uni.setClipboardData({
    data: code,
    success: () => uni.showToast({ title: '已复制邀请码', icon: 'none' }),
  })
}

const revoke = (i) => {
  uni.showModal({
    title: '撤销邀请码',
    content: '撤销后该邀请码立即失效，确定继续吗？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await siteManagerApi.revokeInvite(site.value.id, i.id)
        if (latest.value && latest.value.id === i.id) latest.value = null
        await loadInvites()
        uni.showToast({ title: '已撤销', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: e?.message || '撤销失败', icon: 'none' })
      }
    },
  })
}

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.invite { padding: 20rpx; }
.site-name { display: block; margin: 16rpx 0 10rpx; font-size: 31rpx; font-weight: 600; color: #1e3a8a; }
.ttls { display: flex; gap: 14rpx; margin-top: 20rpx; }
.ttl { flex: 1; text-align: center; padding: 16rpx 0; border-radius: 14rpx; background: #f8fafc; color: #64748b; font-size: 25rpx; border: 2rpx solid #e2e8f0; }
.ttl.active { background: #eaf1fe; color: #2563eb; border-color: #2563eb; font-weight: 600; }
.gen-btn { margin-top: 26rpx; }
.gen-hint { display: block; margin-top: 16rpx; font-size: 21rpx; line-height: 1.6; }
.code-card { display: flex; flex-direction: column; }
.code { margin: 24rpx 0; text-align: center; font-size: 56rpx; font-weight: 700; letter-spacing: 8rpx; color: #1d4ed8; }
.code-actions { display: flex; gap: 16rpx; }
.code-btn { flex: 1; background: #2563eb; color: #fff; border-radius: 999rpx; font-size: 26rpx; padding: 16rpx 0; }
.code-btn--plain { background: #fff; color: #2563eb; border: 2rpx solid #dbeafe; }
.code-btn::after { border: none; }
.qr { width: 320rpx; align-self: center; margin-top: 26rpx; }
.qr-hint { display: block; text-align: center; margin-top: 20rpx; font-size: 22rpx; }
.inv-row { display: flex; align-items: center; gap: 12rpx; padding: 22rpx 0; border-bottom: 2rpx solid #f1f5f9; }
.inv-row:last-child { border-bottom: none; }
.inv-row__main { flex: 1; display: flex; flex-direction: column; gap: 6rpx; min-width: 0; }
.inv-row__code { font-size: 28rpx; font-weight: 600; letter-spacing: 2rpx; }
.inv-row__revoke { color: #dc2626; font-size: 24rpx; }
</style>
