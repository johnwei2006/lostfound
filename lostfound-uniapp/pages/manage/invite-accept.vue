<template>
  <view class="accept">
    <view class="accept__card lf-card">
      <view class="accept__icon">邀</view>
      <text class="accept__title">加入招领站</text>
      <text class="accept__desc">输入主管理员提供的邀请码，确认后即可成为该站共同管理员，协助管理站内物品、转交审批与归还登记。</text>

      <text class="field-label">邀请码</text>
      <input
        v-model="code"
        class="lf-input code-input"
        placeholder="请输入 8 位邀请码"
        maxlength="32"
        :disabled="status === 'loading' || status === 'done'"
        @input="onInput"
        @confirm="preview"
      />
      <button class="verify-btn" @tap="preview">验证邀请码</button>

      <view v-if="previewData" class="preview">
        <view class="lf-field"><text class="lf-field__label">招领站</text><text class="lf-field__value">{{ previewData.siteName }}</text></view>
        <view class="lf-field"><text class="lf-field__label">邀请人</text><text class="lf-field__value">{{ previewData.inviterNickname }}</text></view>
        <view class="lf-field"><text class="lf-field__label">状态</text><text class="lf-field__value">{{ statusLabel(previewData.status) }}</text></view>
        <view class="lf-field"><text class="lf-field__label">有效期至</text><text class="lf-field__value">{{ formatTime(previewData.expireTime) }}</text></view>
        <text v-if="previewData.alreadyManager" class="preview__warn">您已是该站管理员，无需重复加入</text>
      </view>

      <view v-if="message" class="notice" :class="{ 'notice--ok': status === 'done' }">{{ message }}</view>

      <button class="lf-primary-btn accept__btn" :disabled="!canSubmit" @tap="submit">
        {{ status === 'loading' ? '处理中…' : status === 'done' ? '已加入' : '确认加入' }}
      </button>
      <button class="accept__cancel" @tap="goHome">暂不加入，返回首页</button>
    </view>
    <text class="accept__hint">仅通过主管理员发放的邀请码加入，平台不会向他人公开您的资料</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { siteManagerApi } from '@/utils/api'
import siteStore from '@/utils/site-store'

const code = ref('')
const previewData = ref(null)
// ready | loading | done | error
const status = ref('ready')
const message = ref('')

const canSubmit = computed(() =>
  code.value.trim().length > 0
  && status.value !== 'loading'
  && status.value !== 'done'
  && !!previewData.value
  && previewData.value.status === 'PENDING'
  && !previewData.value.alreadyManager)

const formatTime = (t) => t ? String(t).slice(0, 16).replace('T', ' ') : '—'
const statusLabel = (s) => ({ PENDING: '待使用', USED: '已使用', REVOKED: '已撤销', EXPIRED: '已过期' }[s] || s || '未知')

onLoad((options) => {
  let scene = options?.scene || options?.code || ''
  try { scene = decodeURIComponent(scene) } catch (e) { /* 已是明文 */ }
  if (scene) {
    code.value = scene
    preview()
  }
})

const onInput = () => {
  previewData.value = null
  message.value = ''
  if (status.value !== 'loading') status.value = 'ready'
}

const preview = async () => {
  const value = code.value.trim().toUpperCase()
  if (!value) return
  status.value = 'ready'
  message.value = ''
  try {
    previewData.value = await siteManagerApi.previewInvite(value)
    if (previewData.value.status !== 'PENDING') message.value = '该邀请码' + statusLabel(previewData.value.status) + '，请联系主管理员重新生成'
    else if (previewData.value.alreadyManager) message.value = '您已是该招领站的管理员'
  } catch (e) {
    previewData.value = null
    message.value = e?.message || '邀请码无效'
    status.value = 'error'
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  status.value = 'loading'
  try {
    const siteId = await siteManagerApi.acceptInvite(code.value.trim().toUpperCase())
    status.value = 'done'
    message.value = '已成功加入，正在进入站点工作台…'
    // 清空站点缓存，让工作台强制重新拉取刚加入的站点
    siteStore.reset()
    uni.showToast({ title: '加入成功', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: `/pages/manage/index?site=${siteId}` }), 800)
  } catch (e) {
    status.value = 'error'
    message.value = e?.message || '加入失败，请稍后重试'
  }
}

const goHome = () => uni.switchTab({ url: '/pages/home/home' })
</script>

<style scoped>
.accept { min-height: 100vh; padding: 60rpx 32rpx; box-sizing: border-box; display: flex; align-items: center; flex-direction: column; }
.accept__card { width: 100%; max-width: 640rpx; padding: 56rpx 40rpx 40rpx; display: flex; align-items: center; flex-direction: column; border-radius: 24rpx; box-shadow: 0 12rpx 40rpx rgba(37, 99, 235, .08); }
.accept__icon { width: 104rpx; height: 104rpx; border-radius: 50%; background: #eaf1fe; color: #2563eb; font-size: 44rpx; font-weight: 700; text-align: center; line-height: 104rpx; }
.accept__title { margin-top: 26rpx; font-size: 34rpx; font-weight: 700; }
.accept__desc { margin-top: 18rpx; font-size: 25rpx; line-height: 1.8; color: #64748b; text-align: center; }
.field-label { align-self: flex-start; margin-top: 32rpx; font-size: 26rpx; color: #64748b; }
.code-input { margin-top: 12rpx; text-align: center; letter-spacing: 6rpx; font-size: 34rpx; font-weight: 600; }
.verify-btn { width: 100%; margin-top: 18rpx; padding: 16rpx 0; border-radius: 999rpx; background: #f1f5f9; color: #475569; font-size: 26rpx; }
.verify-btn::after { border: none; }
.preview { width: 100%; margin-top: 24rpx; padding-top: 8rpx; border-top: 2rpx dashed #e2e8f0; }
.preview__warn { display: block; margin-top: 16rpx; color: #b45309; font-size: 24rpx; }
.notice { width: 100%; margin-top: 24rpx; padding: 18rpx 22rpx; border-radius: 14rpx; background: #fff3e8; color: #9a4a12; font-size: 24rpx; line-height: 1.7; box-sizing: border-box; }
.notice--ok { background: #eaf1fe; color: #1d4ed8; }
.accept__btn { width: 100%; margin-top: 32rpx; }
.accept__cancel { width: 100%; margin-top: 18rpx; padding: 16rpx 0; border: none; background: transparent; color: #64748b; font-size: 26rpx; }
.accept__cancel::after { border: none; }
.accept__hint { margin-top: 30rpx; font-size: 22rpx; color: #94a3b8; text-align: center; }
</style>
