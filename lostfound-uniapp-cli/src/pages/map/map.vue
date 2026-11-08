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

const mode = ref('found')
const items = ref([])
const active = ref(null)

const centerLat = computed(() => active.value?.latitude || 30.593)
const centerLng = computed(() => active.value?.longitude || 114.305)

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
  }
}

const goDetail = () => {
  if (!active.value) return
  uni.navigateTo({ url: `/pages/detail/detail?type=${mode.value}&id=${active.value.id}` })
}

onLoad(load)
</script>

<style scoped>
.map-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
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
