<template>
  <view class="loc-bar" :class="barClass">
    <view class="loc-main">
      <view class="loc-dot"><view class="loc-dot__inner" /></view>
      <view class="loc-copy">
        <view class="loc-head">
          <text class="loc-label">当前位置</text>
          <text class="loc-source" :class="'loc-source--' + sourceKind">{{ sourceText }}</text>
        </view>
        <text class="loc-value">{{ displayText }}</text>
      </view>
    </view>
    <view class="loc-actions">
      <view class="loc-btn loc-btn--primary" :class="{ 'is-disabled': loading }" @tap="chooseLocation">
        {{ loading ? '处理中…' : '地图选点' }}
      </view>
      <view class="loc-btn" @tap="restore">{{ loc.source === 'manual' ? '恢复当前位置' : '重新定位' }}</view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ensureLocation,
  ensurePrivacyAuthorize,
  getCurrentLocation,
  refreshLocation,
  setManualLocation,
  subscribeLocation,
} from '@/utils/location'

const loc = ref(getCurrentLocation())
const loading = ref(false)
let unsubscribe = null

const sourceKind = computed(() => {
  if (loc.value.source === 'manual') return 'manual'
  return loc.value.available ? 'auto' : 'default'
})

const sourceText = computed(() => {
  if (loc.value.source === 'manual') return '手动选点'
  return loc.value.available ? '自动定位' : '默认城市'
})

const displayText = computed(() => {
  const l = loc.value
  if (l.address) return l.address
  if (!l.available) return '未获取到定位，暂用默认城市'
  const coord = `${Number(l.latitude).toFixed(4)}, ${Number(l.longitude).toFixed(4)}`
  return l.source === 'manual' ? `已选位置 ${coord}` : `当前定位 ${coord}`
})

const barClass = computed(() => {
  if (loc.value.source === 'manual') return 'loc-bar--manual'
  return loc.value.available ? '' : 'loc-bar--warn'
})

const chooseLocation = async () => {
  if (loading.value) return
  if (!(await ensurePrivacyAuthorize())) {
    return uni.showToast({ title: '请先同意隐私协议后再选点', icon: 'none' })
  }
  uni.chooseLocation({
    latitude: loc.value.latitude,
    longitude: loc.value.longitude,
    success: (res) => {
      const ok = setManualLocation({
        latitude: res && res.latitude,
        longitude: res && res.longitude,
        address: (res && (res.name || res.address)) || '',
      })
      uni.showToast({ title: ok ? '已切换当前位置' : '选点坐标不合法', icon: 'none' })
    },
    fail: (err) => {
      // 用户主动取消选点不改动既有位置
      if (err && /cancel/i.test(err.errMsg || '')) return
      uni.showToast({ title: '选点失败，请重试', icon: 'none' })
    },
  })
}

const restore = () => {
  if (loading.value) return
  loading.value = true
  refreshLocation()
    .then((l) => {
      uni.showToast({ title: l.available ? '已恢复当前位置' : '定位不可用，使用默认城市', icon: 'none' })
    })
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  loc.value = getCurrentLocation()
  unsubscribe = subscribeLocation((next) => {
    loc.value = next
  })
  // 与 App.onLaunch 共用同一次定位 Promise，不重复弹出授权；
  // 定位期间用户可能已完成选点，必须重读生效位置，避免旧结果覆盖手动选点
  ensureLocation().then(() => {
    loc.value = getCurrentLocation()
  })
})

onUnmounted(() => {
  if (unsubscribe) unsubscribe()
})
</script>

<style scoped>
.loc-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  margin: 0 20rpx 16rpx;
  padding: 12rpx 14rpx;
  border-radius: 16rpx;
  background: #eaf1fe;
  border: 2rpx solid #d7e5fd;
}
.loc-bar--manual {
  background: #eaf1fe;
  border-color: #2563eb;
}
.loc-bar--warn {
  background: #fff3e8;
  border-color: #fbd9b8;
}
.loc-main {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex: 1 1 240rpx;
  min-width: 0;
}
.loc-dot {
  width: 26rpx;
  height: 26rpx;
  border-radius: 50%;
  border: 3rpx solid #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.loc-dot__inner {
  width: 9rpx;
  height: 9rpx;
  border-radius: 50%;
  background: #2563eb;
}
.loc-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}
.loc-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.loc-label {
  font-size: 20rpx;
  line-height: 1.2;
  color: #6b7280;
}
.loc-value {
  font-size: 25rpx;
  line-height: 1.35;
  color: #1f2937;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.loc-source {
  font-size: 19rpx;
  line-height: 1.2;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #2563eb;
  flex-shrink: 0;
}
.loc-source--default {
  color: #92400e;
}
.loc-actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex: 0 0 auto;
}
.loc-btn {
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  line-height: 1.2;
  white-space: nowrap;
  color: #475569;
  background: #fff;
  border: 2rpx solid #e5e9f0;
}
.loc-btn--primary {
  color: #fff;
  background: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
}
.loc-btn.is-disabled {
  opacity: 0.6;
}
</style>
