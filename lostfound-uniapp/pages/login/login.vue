<template>
  <view class="login">
    <view class="brand">
      <text class="brand-title">寻物志</text>
      <text class="brand-sub">让每一份遗失都有归途</text>
    </view>

    <!-- 微信一键登录 -->
    <view class="wx-panel lf-card" v-if="mode === 'wx'">
      <button
        class="lf-btn wx-btn"
        :loading="loading"
        :disabled="loading"
        @tap="wxLogin"
      >微信一键登录</button>
      <text class="wx-tip">首次登录将自动创建账号</text>
      <text class="switch" @tap="mode = 'account'">使用账号密码登录 →</text>
    </view>

    <!-- 账号密码登录/注册 -->
    <view class="form lf-card" v-else>
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input" type="password" password placeholder="密码" v-if="isLogin" />
      <input v-model="form.nickname" class="input" placeholder="昵称" v-if="!isLogin" />
      <input v-model="form.password" class="input" type="password" password placeholder="密码（6-32位）" v-if="!isLogin" />
      <input v-model="form.phone" class="input" placeholder="手机号（选填）" v-if="!isLogin" />

      <button class="lf-btn" :loading="loading" @tap="submit">{{ isLogin ? '登 录' : '注 册' }}</button>
      <text class="switch" @tap="isLogin = !isLogin">
        {{ isLogin ? '没有账号？去注册 →' : '已有账号？去登录 →' }}
      </text>
      <text class="switch back" @tap="mode = 'wx'">← 返回微信登录</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { authApi } from '@/utils/api'

const mode = ref('wx') // wx-微信登录 | account-账号登录
const isLogin = ref(true)
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', phone: '' })

const saveSession = (data) => {
  uni.setStorageSync('lf_token', data.token)
  uni.setStorageSync('lf_user', data.user)
}

const toHome = () => {
  uni.showToast({ title: '登录成功', icon: 'success' })
  setTimeout(() => uni.switchTab({ url: '/pages/home/home' }), 800)
}

/** 微信一键登录：wx.login 获取 code，后端换取 openid 并签发 token */
const wxLogin = () => {
  loading.value = true
  uni.login({
    provider: 'weixin',
    success: async (res) => {
      try {
        const data = await authApi.wxLogin({ code: res.code })
        saveSession(data)
        toHome()
      } catch (e) {
        uni.showToast({ title: e.message || '微信登录失败', icon: 'none' })
      } finally {
        loading.value = false
      }
    },
    fail: () => {
      loading.value = false
      uni.showToast({ title: '微信登录调用失败', icon: 'none' })
    },
  })
}

const submit = async () => {
  if (!form.username || !form.password) {
    return uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
  }
  loading.value = true
  try {
    const data = isLogin.value
      ? await authApi.login({ username: form.username, password: form.password })
      : await authApi.register({
          username: form.username,
          password: form.password,
          nickname: form.nickname || form.username,
          phone: form.phone,
        })
    saveSession(data)
    toHome()
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  background: linear-gradient(160deg, #1e40af, #2563eb 60%, #3b82f6);
  padding: 120rpx 40rpx;
}
.brand {
  text-align: center;
  color: #fff;
  margin-bottom: 60rpx;
}
.brand-title {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
}
.brand-sub {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.75);
  font-size: 26rpx;
  letter-spacing: 2rpx;
}
.form,
.wx-panel {
  padding: 40rpx 32rpx;
}
.input {
  background: #f5f7fa;
  border-radius: 14rpx;
  padding: 22rpx 26rpx;
  font-size: 28rpx;
  margin-bottom: 22rpx;
}
.switch {
  display: block;
  text-align: center;
  margin-top: 24rpx;
  color: #2563eb;
  font-size: 26rpx;
}
.back {
  color: #9ca3af;
}
.wx-btn {
  background: #07c160;
}
.wx-tip {
  display: block;
  text-align: center;
  margin-top: 20rpx;
  color: #9ca3af;
  font-size: 24rpx;
}
</style>
