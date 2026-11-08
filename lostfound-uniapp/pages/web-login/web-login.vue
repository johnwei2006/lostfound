<template>
  <view class="web-login">
    <view class="web-login__card lf-card">
      <view class="web-login__icon"><text class="web-login__icon-text">寻</text></view>
      <text class="web-login__title">确认登录网页版</text>
      <text class="web-login__desc">
        确认后将使用当前微信账号登录「百觅」网页版，与小程序共用同一个账号，发布、关注与消息会自动同步。
      </text>

      <view v-if="status === 'error'" class="web-login__notice">{{ message }}</view>
      <view v-else-if="status === 'done'" class="web-login__notice web-login__notice--ok">
        已登录网页，请返回浏览器继续操作
      </view>

      <button
        class="lf-btn web-login__confirm"
        :loading="status === 'loading'"
        :disabled="!ticket || status === 'loading' || status === 'done'"
        @tap="confirm"
      >{{ status === 'done' ? '已完成' : '确认登录网页' }}</button>
      <button class="web-login__cancel" @tap="cancel">{{ status === 'done' ? '返回首页' : '取消' }}</button>
    </view>
    <text class="web-login__hint">若非本人操作，请直接关闭页面</text>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi } from '@/utils/api'

const ticket = ref('')
// ready | loading | done | error
const status = ref('ready')
const message = ref('')

/** 小程序码通过 scene 传票据；普通调试链接可用 ticket 参数 */
onLoad((options) => {
  let scene = options?.scene || options?.ticket || ''
  try {
    scene = decodeURIComponent(scene)
  } catch (e) {
    // 场景值本身已是明文时保持原值
  }
  ticket.value = scene
  if (!scene) {
    status.value = 'error'
    message.value = '未获取到登录票据，请回到网页刷新二维码后重新扫码'
  }
})

const confirm = async () => {
  if (!ticket.value || status.value === 'loading' || status.value === 'done') return
  status.value = 'loading'
  try {
    // request.js 会等待静默登录并携带当前用户 JWT，后端据此绑定同一个 user.id
    await authApi.confirmWebLogin(ticket.value)
    status.value = 'done'
    uni.showToast({ title: '已登录网页', icon: 'success' })
  } catch (e) {
    status.value = 'error'
    message.value = e?.message || '确认失败，请回到网页刷新二维码'
    uni.showToast({ title: '确认失败', icon: 'none' })
  }
}

const cancel = () => {
  uni.switchTab({ url: '/pages/home/home' })
}
</script>

<style scoped>
.web-login {
  min-height: 100vh;
  padding: 60rpx 32rpx;
  background: var(--lf-bg, #f5f7fa);
  display: flex;
  align-items: center;
  flex-direction: column;
}
.web-login__card {
  width: 100%;
  max-width: 620rpx;
  padding: 56rpx 40rpx 40rpx;
  display: flex;
  align-items: center;
  flex-direction: column;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 40rpx rgba(37, 99, 235, 0.08);
}
.web-login__icon {
  width: 104rpx;
  height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eaf1fe;
}
.web-login__icon-text {
  font-size: 44rpx;
  font-weight: 700;
  color: #2563eb;
}
.web-login__title {
  margin-top: 28rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #1f2937;
}
.web-login__desc {
  margin-top: 20rpx;
  font-size: 26rpx;
  line-height: 1.8;
  color: #475569;
  text-align: center;
}
.web-login__notice {
  width: 100%;
  margin-top: 28rpx;
  padding: 20rpx 24rpx;
  border-radius: 14rpx;
  background: #fff3e8;
  color: #9a4a12;
  font-size: 25rpx;
  line-height: 1.7;
}
.web-login__notice--ok {
  background: #eaf1fe;
  color: #1d4ed8;
}
.web-login__confirm {
  width: 100%;
  margin-top: 40rpx;
}
.web-login__cancel {
  width: 100%;
  margin-top: 20rpx;
  padding: 18rpx 0;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 27rpx;
  line-height: 1.5;
}
.web-login__cancel::after {
  border: none;
}
.web-login__hint {
  margin-top: 32rpx;
  font-size: 24rpx;
  color: #94a3b8;
}
</style>
