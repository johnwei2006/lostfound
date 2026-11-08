<template>
  <view class="sites">
    <view v-for="s in list" :key="s.id" class="lf-card">
      <view style="display: flex; align-items: center; gap: 14rpx">
        <view class="site-icon">🏠</view>
        <view style="flex: 1">
          <text style="font-size: 30rpx; font-weight: 600">{{ s.name }}</text>
          <text class="lf-muted" style="display: block; margin-top: 4rpx">🕐 {{ s.openTime || '全天开放' }}</text>
        </view>
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
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { siteApi } from '@/utils/api'

const list = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const data = await siteApi.page({ page: 1, size: 50 })
    list.value = data.records
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const navigate = (s) => {
  uni.openLocation({
    latitude: Number(s.latitude),
    longitude: Number(s.longitude),
    name: s.name,
    address: s.address,
  })
}

onShow(load)
onPullDownRefresh(load)
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
</style>
