<template>
  <view class="mine">
    <!-- 用户卡片：点击头像或资料区进入编辑弹层 -->
    <view class="user-card lf-card" v-if="user">
      <view class="avatar" @tap="openProfile">{{ (user.nickname || 'U').charAt(0) }}</view>
      <view style="flex: 1" @tap="openProfile">
        <text class="nickname">{{ user.nickname }}</text>
        <text class="lf-muted" style="display: block">{{ user.username }} · {{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</text>
        <text class="lf-muted contact-line" style="display: block">联系方式：{{ user.contact || '未填写，点此编辑' }}</text>
      </view>
      <text class="logout" @tap.stop="logout">退出</text>
    </view>
    <view class="lf-card" v-else @tap="goLogin">
      <text style="font-size: 30rpx; color: #2563eb; font-weight: 600">点击登录 / 注册</text>
    </view>

    <!-- 功能入口 -->
    <view class="menu lf-card" v-if="user">
      <view class="menu-item" @tap="openProfile">
        <text>✏️ 编辑个人资料</text>
        <text class="arrow">›</text>
      </view>
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
      <view class="menu-item" @tap="goTransfer">
        <text>🚚 我的转交</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="goSites">
        <text>🏠 附近招领站</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 招领站服务：申请、审核进度、站点管理与邀请码加入 -->
    <view class="menu lf-card" v-if="user">
      <view class="lf-group-title" style="margin: 24rpx 0 4rpx">招领站服务</view>
      <view class="menu-item" @tap="go('/pages/site-apply/site-apply')">
        <text>申请创建招领站</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="go('/pages/site-applications/site-applications')">
        <text>我的建站申请</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" v-if="isSiteManager" @tap="go('/pages/manage/index')">
        <text>招领站管理</text>
        <text
          v-if="siteCount"
          style="margin-left: auto; margin-right: 12rpx; padding: 2rpx 14rpx; border-radius: 999rpx; background: #eaf1fe; color: #2563eb; font-size: 22rpx"
        >{{ siteCount }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @tap="go('/pages/manage/invite-accept')">
        <text>邀请码加入站点</text>
        <text class="arrow">›</text>
      </view>
    </view>
    <view class="menu lf-card" v-else @tap="goLogin">
      <view class="lf-group-title" style="margin: 24rpx 0 8rpx">招领站服务</view>
      <view class="menu-item lf-muted">登录后即可申请创建招领站、加入站点管理</view>
    </view>

    <view style="text-align: center; margin-top: 30rpx" class="lf-muted">
      百觅 · SpringBoot + Vue3 重构版
    </view>

    <!-- 个人资料编辑弹层 -->
    <view v-if="showProfile" class="profile-mask" @tap="closeProfile">
      <view class="profile-sheet" @tap.stop>
        <view class="sheet-title">编辑个人资料</view>
        <text class="lf-muted sheet-sub">
          保存后发布失物/拾物会自动带入昵称与联系方式；联系方式可填微信号、手机号或邮箱。
        </text>
        <text class="field-label">个人昵称</text>
        <input v-model="profileForm.nickname" class="sheet-input" placeholder="请输入昵称" maxlength="50" />
        <text class="field-label">联系方式</text>
        <input v-model="profileForm.contact" class="sheet-input" placeholder="微信号 / 手机号 / 邮箱（选填）" maxlength="100" />
        <button
          class="lf-btn sheet-submit"
          :class="{ 'sheet-submit--busy': saving }"
          @tap="saveProfile"
        >{{ saving ? '保存中…' : '保存' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { authApi } from '@/utils/api'
import siteStore from '@/utils/site-store'

const user = ref(null)
const showProfile = ref(false)
const saving = ref(false)
const profileForm = reactive({ nickname: '', contact: '' })

const meInfo = ref(null)
let meLoadedAt = 0
const isSiteManager = computed(() =>
  !!(meInfo.value?.siteManager || (meInfo.value?.managedSiteIds || []).length))
const siteCount = computed(() => (meInfo.value?.managedSiteIds || []).length)
const go = (url) => uni.navigateTo({ url })

onShow(async () => {
  user.value = uni.getStorageSync('lf_user') || null
  if (!user.value) {
    meInfo.value = null
    siteStore.reset()
    return
  }
  // 切 tab 会频繁触发，60 秒内复用 /me 结果；审核通过后回到本页会重新拉取
  if (meInfo.value && Date.now() - meLoadedAt < 60_000) return
  try {
    meInfo.value = await authApi.me()
    meLoadedAt = Date.now()
  } catch (e) {
    console.warn('[mine] 获取站点管理范围失败', e)
  }
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
      siteStore.reset()
      meInfo.value = null
      user.value = null
      uni.showToast({ title: '已退出', icon: 'none' })
    },
  })
}

const openProfile = () => {
  if (!user.value) return goLogin()
  profileForm.nickname = user.value.nickname || ''
  profileForm.contact = user.value.contact || ''
  showProfile.value = true
}
const closeProfile = () => {
  if (!saving.value) showProfile.value = false
}
const saveProfile = async () => {
  const nickname = (profileForm.nickname || '').trim()
  if (!nickname) return uni.showToast({ title: '昵称不能为空', icon: 'none' })
  if (saving.value) return
  saving.value = true
  try {
    await authApi.updateProfile({ nickname, contact: (profileForm.contact || '').trim() })
    const me = await authApi.me()
    uni.setStorageSync('lf_user', me)
    user.value = me
    showProfile.value = false
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

const goLogin = () => uni.navigateTo({ url: '/pages/login/login' })
const goList = (mode) => uni.navigateTo({ url: `/pages/list/list?mode=${mode}` })
const goMatch = () => uni.navigateTo({ url: '/pages/list/list?mode=match' })
const goTransfer = () => uni.navigateTo({ url: '/pages/transfer/transfer' })
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
.contact-line {
  margin-top: 6rpx;
  font-size: 24rpx;
}
.profile-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 99;
  display: flex;
  align-items: flex-end;
}
.profile-sheet {
  width: 100%;
  box-sizing: border-box;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 34rpx 32rpx calc(34rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 700;
}
.sheet-sub {
  display: block;
  margin-top: 10rpx;
  line-height: 1.6;
  font-size: 25rpx;
}
.field-label {
  display: block;
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #6b7280;
}
.sheet-input {
  margin-top: 10rpx;
  background: #f5f7fa;
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 18rpx 22rpx;
  font-size: 28rpx;
}
.sheet-submit {
  margin-top: 30rpx;
  padding: 20rpx 0;
}
.sheet-submit--busy {
  opacity: 0.6;
}
</style>
