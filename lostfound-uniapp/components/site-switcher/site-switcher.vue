<template>
  <view>
    <!-- 全站站点名条：常驻页面顶部，显式提示当前站点与管理角色 -->
    <view v-if="variant === 'bar'" class="scb-bar" @tap="visible = true">
      <text class="scb-dot" />
      <view class="scb-main">
        <text class="scb-line">
          当前站点：<text class="scb-name">{{ site ? site.name : '暂无可管理站点' }}</text>
        </text>
        <text v-if="site" class="scb-role">{{ owner ? '主管理员' : '共同管理员' }} · 站点管理视图</text>
        <text v-else class="scb-role">请先在「我的」页确认站点管理权限</text>
      </view>
      <text class="scb-action">切换 ›</text>
    </view>
    <view v-else class="ss-card lf-card" @tap="visible = true">
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
  /** 'card' 保持原有卡片样式；'bar' 为全站常驻站点名条 */
  variant: { type: String, default: 'card' },
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

/* 全站站点名条 */
.scb-bar {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 16rpx 24rpx;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border-bottom: 2rpx solid #bfdbfe;
}
.scb-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 6rpx rgba(37, 99, 235, .12);
  flex-shrink: 0;
}
.scb-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.scb-line { font-size: 26rpx; color: #1e40af; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scb-name { font-weight: 700; color: #1d4ed8; }
.scb-role { font-size: 21rpx; color: #64748b; }
.scb-action { flex-shrink: 0; font-size: 24rpx; color: #1d4ed8; font-weight: 600; }
</style>
