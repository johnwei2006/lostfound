<template>
  <view class="detail" v-if="item">
    <!-- 图片轮播 -->
    <swiper v-if="item.images && item.images.length" class="swiper" circular indicator-dots>
      <swiper-item v-for="img in item.images" :key="img">
        <image :src="img" mode="aspectFill" class="swiper-img" @tap="preview(img)" />
      </swiper-item>
    </swiper>
    <view v-else class="swiper swiper--empty">
      <text class="lf-muted">暂无图片</text>
    </view>

    <!-- 信息卡 -->
    <view class="lf-card">
      <view class="title-row">
        <text class="title">{{ item.title }}</text>
        <text class="lf-tag" :class="status.cls">{{ status.text }}</text>
      </view>
      <view class="meta-row">
        <text class="lf-tag" :class="item.type === 'FOUND' ? 'lf-tag--finished' : 'lf-tag--claimed'">
          {{ item.type === 'FOUND' ? '拾物' : '失物' }}
        </text>
        <text class="lf-muted">{{ item.category }}</text>
        <text class="lf-muted" v-if="item.siteName">· {{ item.siteName }}</text>
      </view>
      <text class="desc">{{ item.description || '暂无描述' }}</text>
      <view class="info-list">
        <view class="info-row">
          <text class="k">{{ item.type === 'FOUND' ? '拾获时间' : '丢失时间' }}</text>
          <text>{{ item.itemTime || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="k">地点</text>
          <text>{{ [item.province, item.city, item.district, item.address].filter(Boolean).join(' ') || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="k">发布人</text>
          <text>{{ item.nickname }}</text>
        </view>
        <view class="info-row" v-if="isOwner || item.status !== 'WAITING'">
          <text class="k">联系方式</text>
          <text>{{ item.contact || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="k">浏览</text>
          <text>{{ item.viewCount }} · ⭐{{ item.focusCount }}</text>
        </view>
      </view>
    </view>

    <!-- 地图 -->
    <view class="lf-card" v-if="item.longitude && item.latitude">
      <map
        class="mini-map"
        :latitude="item.latitude"
        :longitude="item.longitude"
        :markers="[
          {
            id: item.id,
            latitude: item.latitude,
            longitude: item.longitude,
            width: 24,
            height: 32,
          },
        ]"
        :show-location="false"
      />
    </view>

    <!-- 认领申请管理（发布者） -->
    <view class="lf-card" v-if="isOwner">
      <view class="title-row">
        <text class="title" style="font-size: 30rpx">收到的认领申请</text>
        <text class="lf-muted" @tap="loadClaims">刷新</text>
      </view>
      <view v-for="c in claims" :key="c.id" class="claim-item">
        <view class="claim-head">
          <text style="font-weight: 600">{{ c.nickname }}</text>
          <text class="lf-tag" :class="{ 'lf-tag--waiting': c.status === 'PENDING', 'lf-tag--finished': c.status === 'APPROVED', 'lf-tag--offline': c.status === 'REJECTED' }">
            {{ c.status === 'PENDING' ? '待处理' : c.status === 'APPROVED' ? '已通过' : '已拒绝' }}
          </text>
        </view>
        <text class="lf-muted">{{ c.description }}</text>
        <view v-if="c.status === 'PENDING'" class="claim-ops">
          <button class="lf-btn" style="padding: 12rpx 0; font-size: 26rpx" @tap="review(c, true)">通过</button>
          <button class="lf-btn lf-btn--plain" style="padding: 12rpx 0; font-size: 26rpx" @tap="review(c, false)">拒绝</button>
        </view>
      </view>
      <view v-if="!claims.length" class="lf-empty" style="padding: 30rpx 0">暂无申请</view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bar-item" @tap="toggleFocus">
        <text>{{ item.focused ? '⭐ 已关注' : '☆ 关注' }}</text>
      </view>
      <button
        v-if="!isOwner && item.status === 'WAITING'"
        class="lf-btn bar-claim"
        @tap="showClaim = true"
      >
        {{ item.type === 'FOUND' ? '这是我的，申请认领' : '我捡到了，提供线索' }}
      </button>
      <button v-else class="lf-btn bar-claim" style="opacity: 0.5" disabled>
        {{ item.status === 'FINISHED' ? '已完成' : '暂不可认领' }}
      </button>
    </view>

    <!-- 认领弹窗 -->
    <view class="mask" v-if="showClaim" @tap="showClaim = false">
      <view class="modal" @tap.stop>
        <text style="font-weight: 600; font-size: 30rpx">申请{{ item.type === 'FOUND' ? '认领' : '归还' }}</text>
        <text class="lf-muted" style="margin: 12rpx 0">
          请描述物品特征（如图案、划痕、内容物），便于发布者核实
        </text>
        <textarea v-model="claimForm.description" class="textarea" :maxlength="1000" placeholder="描述物品特征…" />
        <input v-model="claimForm.contact" class="input" style="margin-top: 16rpx" placeholder="联系方式" />
        <button class="lf-btn" style="margin-top: 24rpx" @tap="submitClaim">提交申请</button>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { itemApi, claimApi, focusApi, STATUS_MAP } from '@/utils/api'

const type = ref('found')
const id = ref(0)
const item = ref(null)
const claims = ref([])
const showClaim = ref(false)
const claimForm = reactive({ description: '', contact: '' })

const status = computed(() => (item.value ? STATUS_MAP[item.value.status] : {}))
const isOwner = computed(() => {
  const user = uni.getStorageSync('lf_user')
  return item.value && user && item.value.userId === user.id
})

const load = async () => {
  try {
    item.value = await itemApi.detail(type.value, id.value)
    if (isOwner.value) loadClaims()
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
}

const loadClaims = async () => {
  try {
    claims.value = await claimApi.listByItem(type.value, id.value)
  } catch (e) {
    claims.value = []
  }
}

onLoad((options) => {
  type.value = options.type || 'found'
  id.value = Number(options.id)
  uni.setNavigationBarTitle({ title: type.value === 'found' ? '拾物详情' : '失物详情' })
  load()
})

const preview = (img) => uni.previewImage({ urls: item.value.images, current: img })

const toggleFocus = async () => {
  if (!uni.getStorageSync('lf_token')) {
    return uni.navigateTo({ url: '/pages/login/login' })
  }
  const t = type.value.toUpperCase()
  if (item.value.focused) {
    await focusApi.unfocus(t, id.value)
    item.value.focused = false
    item.value.focusCount--
  } else {
    await focusApi.focus(t, id.value)
    item.value.focused = true
    item.value.focusCount++
  }
}

const submitClaim = async () => {
  if (!claimForm.description.trim()) return uni.showToast({ title: '请描述物品特征', icon: 'none' })
  try {
    await claimApi.submit(type.value, id.value, claimForm)
    showClaim.value = false
    uni.showToast({ title: '申请已提交', icon: 'success' })
  } catch (e) {
    uni.showToast({ title: e.message, icon: 'none' })
  }
}

const review = async (claim, approve) => {
  uni.showModal({
    title: '确认',
    content: approve ? '通过该认领申请？通过后物品标记为已完成' : '拒绝该认领申请？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await claimApi.review(type.value, claim.id, approve)
        uni.showToast({ title: '已处理', icon: 'success' })
        load()
      } catch (e) {
        uni.showToast({ title: e.message, icon: 'none' })
      }
    },
  })
}
</script>

<style scoped>
.detail {
  padding: 20rpx 20rpx 140rpx;
}
.swiper {
  height: 420rpx;
  border-radius: 20rpx;
  overflow: hidden;
  margin-bottom: 20rpx;
}
.swiper--empty {
  background: #eef2f8;
  display: flex;
  align-items: center;
  justify-content: center;
}
.swiper-img {
  width: 100%;
  height: 100%;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.title {
  font-size: 36rpx;
  font-weight: 600;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 14rpx;
}
.desc {
  display: block;
  margin-top: 16rpx;
  color: #6b7280;
  line-height: 1.8;
  font-size: 27rpx;
}
.info-list {
  margin-top: 20rpx;
  border-top: 2rpx dashed #e5e9f0;
  padding-top: 16rpx;
}
.info-row {
  display: flex;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}
.info-row .k {
  width: 140rpx;
  color: #9ca3af;
  flex-shrink: 0;
}
.mini-map {
  width: 100%;
  height: 300rpx;
  border-radius: 16rpx;
}
.claim-item {
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 18rpx;
  margin-top: 16rpx;
}
.claim-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.claim-ops {
  display: flex;
  gap: 14rpx;
  margin-top: 14rpx;
}
.claim-ops .lf-btn {
  flex: 1;
  margin: 0;
}
.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -4rpx 20rpx rgba(31, 41, 55, 0.06);
  z-index: 20;
}
.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 22rpx;
  color: #6b7280;
  padding: 0 10rpx;
}
.bar-claim {
  flex: 1;
  margin: 0;
  padding: 18rpx 0;
  font-size: 28rpx;
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 30;
  display: flex;
  align-items: flex-end;
}
.modal {
  width: 100%;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 36rpx 32rpx calc(36rpx + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
}
</style>
