<script setup>
import { computed } from 'vue'
import { STATUS_MAP } from '@/utils/api'

const props = defineProps({ item: { type: Object, required: true } })

const status = computed(() => STATUS_MAP[props.item.status] || STATUS_MAP.WAITING)
const cover = computed(() => (props.item.images && props.item.images[0]) || '')

const goDetail = () => {
  uni.navigateTo({ url: `/pages/detail/detail?type=${props.item.type.toLowerCase()}&id=${props.item.id}` })
}
</script>

<template>
  <view class="item-card lf-card" @tap="goDetail">
    <image v-if="cover" class="card-img" :src="cover" mode="aspectFill" />
    <view v-else class="card-img card-img--empty">
      <text class="lf-muted">暂无图片</text>
    </view>
    <view class="card-body">
      <view class="card-top">
        <text class="lf-tag" :class="item.type === 'FOUND' ? 'lf-tag--finished' : 'lf-tag--claimed'">
          {{ item.type === 'FOUND' ? '拾物' : '失物' }}
        </text>
        <text class="lf-tag" :class="status.cls">{{ status.text }}</text>
      </view>
      <text class="card-title">{{ item.title }}</text>
      <text class="card-desc lf-muted">{{ item.address || item.city || item.category }}</text>
      <view class="card-foot">
        <text class="lf-muted">{{ item.nickname }}</text>
        <text class="lf-muted">👁 {{ item.viewCount }} · ⭐ {{ item.focusCount }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.item-card {
  display: flex;
  overflow: hidden;
  padding: 0;
}
.card-img {
  width: 220rpx;
  height: 220rpx;
  flex-shrink: 0;
}
.card-img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f8;
}
.card-body {
  flex: 1;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.card-top {
  display: flex;
  gap: 10rpx;
}
.card-title {
  margin-top: 10rpx;
  font-size: 30rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  margin-top: 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-foot {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
}
</style>
