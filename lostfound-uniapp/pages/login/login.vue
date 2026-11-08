<template>
  <view class="login">
    <view class="brand">
      <text class="brand-title">寻物志</text>
      <text class="brand-sub">让每一份遗失都有归途</text>
    </view>

    <view class="form lf-card">
      <input v-model="form.username" class="input" placeholder="用户名" />
      <input v-model="form.password" class="input" type="password" password placeholder="密码" v-if="isLogin" />
      <input v-model="form.nickname" class="input" placeholder="昵称" v-else />
      <input v-model="form.password" class="input" type="password" password placeholder="密码（6-32位）" v-if="!isLogin" />
      <input v-model="form.phone" class="input" placeholder="手机号（选填）" v-if="!isLogin" />

      <button class="lf-btn" :loading="loading" @tap="submit">{{ isLogin ? '登 录' : '注 册' }}</button>
      <text class="switch" @tap="isLogin = !isLogin">
        {{ isLogin ? '没有账号？去注册 →' : '已有账号？去登录 →' }}
      </text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { authApi } from '@/utils/api'

const isLogin = ref(true)
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', phone: '' })

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
    uni.setStorageSync('lf_token', data.token)
    uni.setStorageSync('lf_user', data.user)
    uni.showToast({ title: '成功', icon: 'success' })
    setTimeout(() => uni.switchTab({ url: '/pages/home/home' }), 800)
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
.form {
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
</style>
