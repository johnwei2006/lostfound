<template>
  <view class="transfer-page">
    <view v-for="t in list" :key="t.id" class="lf-card">
      <view class="head">
        <text class="lf-tag" :class="statusCls(t.status)">{{ statusText(t.status) }}</text>
        <text class="lf-muted">{{ (t.createTime || '').slice(0, 16) }}</text>
      </view>
      <view class="item-row" @tap="goItem(t)">
        <text class="title">{{ t.itemTitle }}</text>
        <text class="lf-muted site">目标站点：{{ t.siteName || ('站点 #' + t.siteId) }}</text>
      </view>
      <text v-if="t.remark" class="lf-muted remark">转交备注：{{ t.remark }}</text>
      <text v-if="t.status === 'REJECTED' && t.reviewerName" class="lf-muted remark">
        {{ t.reviewerName }} 于 {{ (t.reviewTime || '').slice(0, 16) }} 审批
      </text>

      <view v-if="t.timeline && t.timeline.length" class="timeline">
        <view v-for="(node, i) in t.timeline" :key="i" class="tl-node">
          <view class="tl-dot" :class="'tl-dot--' + (node.state || 'pending')"></view>
          <view style="flex: 1">
            <text class="tl-title" :class="{ dim: node.state === 'pending' }">{{ node.title }}</text>
            <text v-if="node.detail" class="tl-detail">{{ node.detail }}</text>
            <text v-if="node.time" class="lf-muted tl-time">{{ (node.time || '').slice(0, 16) }}</text>
          </view>
        </view>
      </view>

      <view v-if="t.status === 'PENDING'" class="ops">
        <button class="lf-btn lf-btn--plain" style="padding: 12rpx 0; font-size: 25rpx" @tap="cancel(t)">撤回申请</button>
      </view>
    </view>

    <view v-if="!loading && list.length === 0" class="lf-empty">
      <text>还没有转交申请。</text>
      <text style="display: block; margin-top: 8rpx">在「我的发布」中把捡到的物品转交给招领站，由站点管理员审批入库。</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { transferApi } from '@/utils/api'

const list = ref([])
const loading = ref(false)

const STATUS = {
  PENDING: { text: '待站点审批', cls: 'lf-tag--waiting' },
  APPROVED: { text: '已转入站点', cls: 'lf-tag--finished' },
  REJECTED: { text: '已驳回', cls: 'lf-tag--offline' },
  CANCELLED: { text: '已撤回', cls: 'lf-tag--offline' },
}
const statusText = (s) => (STATUS[s] || { text: s || '未知' }).text
const statusCls = (s) => (STATUS[s] || { cls: 'lf-tag--offline' }).cls

const load = async () => {
  if (!uni.getStorageSync('lf_token')) return
  loading.value = true
  try {
    const data = await transferApi.mine(1, 50)
    list.value = data.records || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const goItem = (t) => {
  if (!t.foundId) return
  uni.navigateTo({ url: `/pages/detail/detail?type=found&id=${t.foundId}` })
}

const cancel = (t) => {
  uni.showModal({
    title: '撤回转交申请',
    content: '确定撤回该拾物的转交申请？撤回后可重新发起。',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await transferApi.cancel(t.id)
        uni.showToast({ title: '已撤回', icon: 'none' })
        load()
      } catch (e) {
        console.error(e)
      }
    },
  })
}

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.transfer-page {
  padding: 20rpx;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.item-row {
  margin-top: 16rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 600;
}
.site {
  display: block;
  margin-top: 6rpx;
}
.remark {
  display: block;
  margin-top: 8rpx;
  line-height: 1.6;
}
.timeline {
  margin-top: 20rpx;
  border-top: 2rpx solid #f0f2f7;
  padding-top: 18rpx;
}
.tl-node {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 0;
}
.tl-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-top: 10rpx;
  background: #e5e9f0;
  flex-shrink: 0;
}
.tl-dot--done {
  background: #16a34a;
}
.tl-dot--current {
  background: #2563eb;
}
.tl-dot--reject {
  background: #ef4444;
}
.tl-title {
  font-size: 26rpx;
  font-weight: 600;
  display: block;
}
.tl-title.dim {
  color: #94a3b8;
  font-weight: 400;
}
.tl-detail {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
  margin-top: 2rpx;
  line-height: 1.5;
}
.tl-time {
  display: block;
  font-size: 22rpx;
  margin-top: 2rpx;
}
.ops {
  margin-top: 18rpx;
  display: flex;
  justify-content: flex-end;
}
</style>
