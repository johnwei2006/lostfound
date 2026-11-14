<template>
  <view>
    <view class="ss-card lf-card" @tap="visible = true">
      <view class="ss-left">
        <text class="ss-label">当前站点</text>
        <text class="ss-name">{{ site ? site.name : '暂无可管理站点' }}</text>
      </view>
      <view class="ss-right">
        <text v-if="owner" class="lf-tag lf-tag--owner">主管理员</text>
        <text v-else-if="site" class="lf-tag lf-tag--pending">共同管理员</text>
        <text class="ss-arrow">切换 ›</text>
      </view>
    </view>

    <view v-if="visible" class="ss-mask" @tap="visible = false">
      <view class="ss-sheet" @tap.stop>
        <text class="ss-title">切换招领站</text>
        <scroll-view scroll-y class="ss-list">
          <view v-for="s in sites" :key="s.id" class="ss-item" @tap="pick(s)">
            <text class="ss-item-name">{{ s.name }}</text>
            <text v-if="s.id === currentId" class="ss-item-current">当前</text>
          </view>
          <view v-if="!sites.length" class="lf-empty">暂无可管理招领站</view>
        </scroll-view>
        <button class="ss-close" @tap="visible = false">关闭</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  site: { type: Object, default: null },
  sites: { type: Array, default: () => [] },
  currentId: { type: [Number, String], default: null },
  owner: { type: Boolean, default: false },
})
const emit = defineEmits(['change'])
const visible = ref(false)

const pick = (s) => {
  visible.value = false
  if (s.id !== props.currentId) emit('change', s.id)
}
</script>

<style scoped>
.ss-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  background: linear-gradient(135deg, #ffffff 0%, #eff6ff 100%);
  border: 2rpx solid #dbeafe;
}
.ss-left { display: flex; flex-direction: column; gap: 6rpx; min-width: 0; }
.ss-label { color: #94a3b8; font-size: 22rpx; letter-spacing: 2rpx; }
.ss-name { color: #1e3a8a; font-size: 30rpx; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ss-right { display: flex; align-items: center; gap: 10rpx; flex-shrink: 0; }
.ss-arrow { color: #2563eb; font-size: 24rpx; }
.ss-mask { position: fixed; inset: 0; z-index: 99; background: rgba(15, 23, 42, .45); display: flex; align-items: flex-end; }
.ss-sheet { width: 100%; box-sizing: border-box; background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 34rpx 32rpx calc(28rpx + env(safe-area-inset-bottom)); }
.ss-title { display: block; font-size: 32rpx; font-weight: 700; color: #1f2937; margin-bottom: 20rpx; }
.ss-list { max-height: 50vh; }
.ss-item { display: flex; align-items: center; justify-content: space-between; padding: 26rpx 8rpx; border-bottom: 2rpx solid #f1f5f9; font-size: 28rpx; }
.ss-item-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ss-item-current { color: #2563eb; font-size: 23rpx; margin-left: 16rpx; }
.ss-close { margin-top: 24rpx; background: #f1f5f9; color: #475569; border-radius: 999rpx; font-size: 28rpx; }
.ss-close::after { border: none; }
</style>
