<template>
  <view class="map-page">
    <view class="tabs">
      <view class="tab" :class="{ active: mode === 'found' }" @tap="switchMode('found')">拾物</view>
      <view class="tab" :class="{ active: mode === 'lost' }" @tap="switchMode('lost')">失物</view>
    </view>

    <map
      class="big-map"
      :latitude="centerLat"
      :longitude="centerLng"
      :markers="markers"
      show-location
      @markertap="onMarkerTap"
    />

    <view class="locate-btn" @tap="relocate">{{ located ? '回到当前位置' : '重新定位' }}</view>

    <!-- 底部信息卡 -->
    <view class="info-card lf-card" v-if="active" @tap="goDetail">
      <image v-if="active.images && active.images[0]" :src="active.images[0]" mode="aspectFill" class="info-img" />
      <view style="flex: 1; min-width: 0">
        <text style="font-weight: 600; font-size: 29rpx">{{ active.title }}</text>
        <text class="lf-muted" style="display: block; margin-top: 6rpx">
          {{ active.address || active.city || active.category }}
        </text>
      </view>
      <text class="lf-primary" style="font-size: 25rpx">查看详情 ›</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { itemApi } from '@/utils/api'
import { ensureLocation, getCachedLocation, refreshLocation } from '@/utils/location'

const mode = ref('found')
const items = ref([])
const active = ref(null)

// 默认以启动时获取到的当前位置为中心；点击标记或重新定位时再更新中心点
const cached = getCachedLocation()
const centerLat = ref(cached.latitude)
const centerLng = ref(cached.longitude)
const located = ref(cached.available)

const applyCurrentLocation = async () => {
  const loc = await ensureLocation()
  centerLat.value = loc.latitude
  centerLng.value = loc.longitude
  located.value = loc.available
}

const relocate = async () => {
  const loc = await refreshLocation()
  centerLat.value = loc.latitude
  centerLng.value = loc.longitude
  located.value = loc.available
  active.value = null
  uni.showToast({
    title: loc.available ? '已定位到当前位置' : '定位不可用，显示默认位置',
    icon: 'none',
  })
}

const markers = computed(() =>
  items.value
    .filter((i) => i.longitude && i.latitude)
    .map((i, idx) => ({
      id: idx,
      latitude: Number(i.latitude),
      longitude: Number(i.longitude),
      width: 26,
      height: 34,
      title: i.title,
      callout: { content: i.title, display: 'BYCLICK', borderRadius: 8, padding: 6 },
    })),
)

const load = async () => {
  try {
    items.value = await itemApi.mapItems(mode.value, 300)
    active.value = null
  } catch (e) {
    console.error(e)
  }
}

const switchMode = (m) => {
  mode.value = m
  load()
}

const onMarkerTap = (e) => {
  const marker = markers.value.find((mk) => mk.id === e.detail.markerId)
  if (marker) {
    active.value = items.value.filter((i) => i.longitude && i.latitude)[e.detail.markerId]
    centerLat.value = Number(marker.latitude)
    centerLng.value = Number(marker.longitude)
  }
}

const goDetail = () => {
  if (!active.value) return
  uni.navigateTo({ url: `/pages/detail/detail?type=${mode.value}&id=${active.value.id}` })
}

onLoad(() => {
  applyCurrentLocation()
  load()
})
</script>

<style scoped>
.map-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: relative;
}
.locate-btn {
  position: absolute;
  right: 24rpx;
  top: 110rpx;
  background: #fff;
  color: #2563eb;
  font-size: 24rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.12);
}
.tabs {
  display: flex;
  gap: 14rpx;
  padding: 16rpx 20rpx;
}
.tab {
  background: #fff;
  border-radius: 999rpx;
  padding: 12rpx 40rpx;
  font-size: 27rpx;
  color: #6b7280;
  border: 2rpx solid #e5e9f0;
}
.tab.active {
  background: #eaf1fe;
  color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
}
.big-map {
  flex: 1;
  width: 100%;
}
.info-card {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 20rpx;
}
.info-img {
  width: 100rpx;
  height: 100rpx;
  border-radius: 14rpx;
  flex-shrink: 0;
}
</style>
