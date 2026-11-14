<template>
  <view class="sites">
    <site-context-bar />
    <view v-if="locationAvailable" class="lf-muted sort-hint">已按距离由近到远排序</view>
    <view v-for="s in list" :key="s.id" class="lf-card">
      <view style="display: flex; align-items: center; gap: 14rpx">
        <view class="site-icon">🏠</view>
        <view style="flex: 1">
          <text style="font-size: 30rpx; font-weight: 600">{{ s.name }}</text>
          <text class="lf-muted" style="display: block; margin-top: 4rpx">🕐 {{ s.openTime || '全天开放' }}</text>
        </view>
        <text v-if="s.distanceText" class="distance-badge">{{ s.distanceText }}</text>
      </view>
      <text class="lf-muted" style="display: block; margin-top: 14rpx">📍 {{ s.address }}</text>
      <text v-if="s.description" class="lf-muted" style="display: block; margin-top: 8rpx">{{ s.description }}</text>
      <view class="site-ops" v-if="s.longitude && s.latitude">
        <text class="nav-btn" @tap="navigate(s)">地图导航</text>
      </view>
    </view>
    <view v-if="!loading && list.length === 0" class="lf-empty">暂无招领站</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onUnload } from '@dcloudio/uni-app'
import { siteApi } from '@/utils/api'
import { createDedupeGuard } from '@/utils/request-cache'
import { getCurrentLocation, ensureLocation, subscribeLocation } from '@/utils/location'
import { withDistance } from '@/utils/distance'

const guard = createDedupeGuard(1500)
const list = ref([])
const loading = ref(false)
const locationAvailable = ref(false)

const load = async (force = false) => {
  loading.value = true
  try {
    // 复用已缓存的 gcj02 定位，不逐项触发定位；失败/未授权时降级为原顺序
    const loc = force ? await ensureLocation() : getCurrentLocation()
    locationAvailable.value = !!(loc && loc.available !== false)
    const data = await siteApi.page({ page: 1, size: 50 })
    list.value = withDistance(data.records || [], locationAvailable.value ? loc : null)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

// 用户手动选点或定位更新后立即重排，保持与原列表页一致的位置响应
const unsubscribe = subscribeLocation(() => { guard.reset(); load() })

const navigate = (s) => {
  uni.openLocation({
    latitude: Number(s.latitude),
    longitude: Number(s.longitude),
    name: s.name,
    address: s.address,
  })
}

onShow(() => guard.run('sites', () => load()))
onPullDownRefresh(() => { guard.reset(); load(true) })
onUnload(() => unsubscribe())
</script>

<style scoped>
.sites {
  padding: 20rpx;
}
.site-icon {
  width: 70rpx;
  height: 70rpx;
  border-radius: 18rpx;
  background: #eaf1fe;
  text-align: center;
  line-height: 70rpx;
  font-size: 34rpx;
}
.site-ops {
  margin-top: 14rpx;
  text-align: right;
}
.nav-btn {
  color: #2563eb;
  font-size: 26rpx;
  border: 2rpx solid #2563eb;
  border-radius: 999rpx;
  padding: 6rpx 28rpx;
}
.sort-hint { display: block; font-size: 23rpx; padding: 4rpx 8rpx 14rpx; }
.distance-badge {
  flex-shrink: 0;
  color: #1d4ed8;
  font-size: 23rpx;
  font-weight: 600;
  background: #eff6ff;
  border: 2rpx solid #dbeafe;
  border-radius: 999rpx;
  padding: 8rpx 18rpx;
}
</style>
