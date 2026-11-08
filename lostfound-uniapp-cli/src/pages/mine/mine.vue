<template>
  <view class="mine">
    <!-- 用户卡片 -->
    <view class="user-card lf-card" v-if="user">
      <view class="avatar">{{ (user.nickname || 'U').charAt(0) }}</view>
      <view style="flex: 1">
        <text class="nickname">{{ user.nickname }}</text>
        <text class="lf-muted" style="display: block">{{ user.username }} · {{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</text>
      </view>
      <text class="logout" @tap="logout">退出</text>
    </view>
    <view class="lf-card" v-else @tap="goLogin">
      <text style="font-size: 30rpx; color: #2563eb; font-weight: 600">点击登录 / 注册</text>
    </view>

    <!-- 功能入口 -->
    <view class="menu lf-card" v-if="user">
      <view class="menu-item" @tap="goList('published')">
        <text>📦 我的发布</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="goList('focused')">
        <text>⭐ 我的关注</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="goMatch">
        <text>🔗 智能匹配推荐</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="goSites">
        <text>🏠 招领站</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <view style="text-align: center; margin-top: 30rpx" class="lf-muted">
     百觅 · SpringBoot + Vue3 重构版
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'

const user = ref(null)

onShow(() => {
  user.value = uni.getStorageSync('lf_user') || null
})
onPullDownRefresh(() => {
  uni.stopPullDownRefresh()
})

const logout = () => {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: (res) => {
      if (!res.confirm) return
      uni.removeStorageSync('lf_token')
      uni.removeStorageSync('lf_user')
      user.value = null
      uni.showToast({ title: '已退出', icon: 'none' })
    },
  })
}

const goLogin = () => uni.navigateTo({ url: '/pages/login/login' })
const goList = (mode) => uni.navigateTo({ url: `/pages/list/list?mode=${mode}` })
const goMatch = () => uni.navigateTo({ url: '/pages/list/list?mode=match' })
const goSites = () => uni.navigateTo({ url: '/pages/sites/sites' })
</script>

<style scoped>
.mine {
  padding: 24rpx;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #eaf1fe;
  color: #2563eb;
  text-align: center;
  line-height: 100rpx;
  font-size: 44rpx;
  font-weight: 600;
}
.nickname {
  font-size: 34rpx;
  font-weight: 600;
}
.logout {
  color: #ef4444;
  font-size: 26rpx;
}
.menu {
  padding: 0 24rpx;
}
.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 2rpx solid #f0f2f7;
  font-size: 29rpx;
}
.menu-item:last-child {
  border-bottom: none;
}
.arrow {
  color: #c0c8d4;
  font-size: 34rpx;
}
</style>
