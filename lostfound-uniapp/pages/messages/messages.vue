<template>
  <view class="messages">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: filter === t.value }"
        @tap="switchTab(t.value)"
      >
        {{ t.label }}
      </view>
      <view class="read-all" @tap="readAll">全部已读</view>
    </view>

    <view class="list">
      <view
        v-for="m in list"
        :key="m.id"
        class="msg lf-card"
        :class="{ unread: !m.isRead }"
        @tap="open(m)"
      >
        <view class="msg-top">
          <text class="msg-type" :style="{ background: colorOf(m.type) + '1a', color: colorOf(m.type) }">
            {{ labelOf(m.type) }}
          </text>
          <text class="lf-muted">{{ (m.createTime || '').slice(5, 16) }}</text>
        </view>
        <text class="msg-title">{{ m.title }}</text>
        <text class="msg-content lf-muted">{{ m.content }}</text>
      </view>
      <view v-if="!loading && list.length === 0" class="lf-empty">暂无消息</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { messageApi } from '@/utils/api'

const tabs = [
  { label: '全部', value: '' },
  { label: '匹配推荐', value: 'MATCH' },
  { label: '认领动态', value: 'CLAIM' },
  { label: '系统', value: 'SYSTEM' },
]
const filter = ref('')
const list = ref([])
const loading = ref(false)

const load = async () => {
  if (!uni.getStorageSync('lf_token')) return
  loading.value = true
  try {
    const data = await messageApi.page({ type: filter.value || undefined, page: 1, size: 30 })
    list.value = data.records
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}
const switchTab = (v) => {
  filter.value = v
  load()
}
const open = async (m) => {
  if (!m.isRead) {
    await messageApi.read(m.id)
    m.isRead = 1
  }
  if (m.type === 'MATCH') {
    uni.navigateTo({ url: '/pages/list/list?mode=match' })
  }
}
const readAll = async () => {
  await messageApi.readAll()
  load()
}

const labelOf = (t) => ({ MATCH: '匹配', CLAIM: '认领', SYSTEM: '系统' }[t] || '通知')
const colorOf = (t) => ({ MATCH: '#2563eb', CLAIM: '#f97316', SYSTEM: '#6b7280' }[t] || '#6b7280')

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.messages {
  padding: 20rpx;
}
.tabs {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}
.tab {
  background: #fff;
  border-radius: 999rpx;
  padding: 12rpx 26rpx;
  font-size: 25rpx;
  color: #6b7280;
  border: 2rpx solid #e5e9f0;
}
.tab.active {
  background: #eaf1fe;
  color: #2563eb;
  border-color: #2563eb;
}
.read-all {
  margin-left: auto;
  color: #2563eb;
  font-size: 25rpx;
}
.msg {
  padding: 22rpx;
}
.msg.unread {
  border-left: 6rpx solid #2563eb;
}
.msg-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.msg-type {
  font-size: 22rpx;
  padding: 2rpx 16rpx;
  border-radius: 999rpx;
  font-weight: 600;
}
.msg-title {
  display: block;
  margin-top: 10rpx;
  font-size: 29rpx;
  font-weight: 600;
}
.msg-content {
  display: block;
  margin-top: 6rpx;
  line-height: 1.6;
}
</style>
