<template>
  <view class="list-page">
    <!-- 我的发布 / 我的关注 / 匹配推荐 -->
    <template v-if="mode === 'match'">
      <view v-for="m in matches" :key="m.id" class="lf-card">
        <view class="match-head">
          <view class="score" :class="{ high: m.score >= 80 }">{{ Math.round(m.score) }}%</view>
          <view style="flex: 1">
            <text style="font-weight: 600">匹配推荐</text>
            <text class="lf-muted" style="display: block; margin-top: 4rpx">{{ m.reason }}</text>
          </view>
          <text v-if="m.status === 'UNREAD'" class="lf-tag lf-tag--waiting">待处理</text>
          <text v-else-if="m.status === 'ACCEPTED'" class="lf-tag lf-tag--finished">已采纳</text>
          <text v-else class="lf-tag lf-tag--offline">已忽略</text>
        </view>
        <view class="match-pair">
          <view class="pair" v-if="m.found" @tap="goDetail('found', m.found.id)">
            <text class="lf-tag lf-tag--finished">拾物</text>
            <text style="font-weight: 600; margin-top: 8rpx; display: block">{{ m.found.title }}</text>
          </view>
          <view class="pair" v-if="m.lost" @tap="goDetail('lost', m.lost.id)">
            <text class="lf-tag lf-tag--claimed">失物</text>
            <text style="font-weight: 600; margin-top: 8rpx; display: block">{{ m.lost.title }}</text>
          </view>
        </view>
        <view v-if="m.status === 'UNREAD'" class="match-ops">
          <button class="lf-btn" style="flex: 1; padding: 14rpx 0; font-size: 26rpx" @tap="act(m, 'ACCEPTED')">采纳</button>
          <button class="lf-btn lf-btn--plain" style="flex: 1; padding: 14rpx 0; font-size: 26rpx" @tap="act(m, 'IGNORED')">忽略</button>
        </view>
      </view>
      <view v-if="!loading && matches.length === 0" class="lf-empty">
        暂无匹配推荐，发布失物/拾物后系统将自动配对
      </view>
    </template>

    <template v-else>
      <view class="tabs">
        <view class="tab" :class="{ active: curType === 'found' }" @tap="switchType('found')">我的拾物</view>
        <view class="tab" :class="{ active: curType === 'lost' }" @tap="switchType('lost')">我的失物</view>
      </view>
      <item-card v-for="it in list" :key="it.id" :item="it" />
      <view v-if="!loading && list.length === 0" class="lf-empty">
        {{ mode === 'published' ? '暂无发布记录' : '暂无关注内容' }}
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { itemApi, focusApi, matchApi } from '@/utils/api'

const mode = ref('published')
const curType = ref('lost')
const list = ref([])
const matches = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    if (mode.value === 'match') {
      matches.value = await matchApi.mine()
    } else if (mode.value === 'published') {
      const data = await itemApi.mine(curType.value, 1, 50)
      list.value = data.records
    } else {
      const data = await focusApi.mine(curType.value.toUpperCase(), 1, 50)
      list.value = data.records
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const switchType = (t) => {
  curType.value = t
  load()
}
const goDetail = (type, id) => uni.navigateTo({ url: `/pages/detail/detail?type=${type}&id=${id}` })
const act = async (m, status) => {
  await matchApi.setStatus(m.id, status)
  uni.showToast({ title: '已处理', icon: 'none' })
  load()
}

onLoad((options) => {
  mode.value = options.mode || 'published'
})
onShow(load)
</script>

<style scoped>
.list-page {
  padding: 20rpx;
}
.tabs {
  display: flex;
  gap: 14rpx;
  margin-bottom: 20rpx;
}
.tab {
  background: #fff;
  border-radius: 999rpx;
  padding: 14rpx 36rpx;
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
.match-head {
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.score {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #eaf1fe;
  color: #2563eb;
  text-align: center;
  line-height: 88rpx;
  font-weight: 700;
  font-size: 26rpx;
  flex-shrink: 0;
}
.score.high {
  background: #fff3e8;
  color: #f97316;
}
.match-pair {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
}
.pair {
  flex: 1;
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 16rpx;
}
.match-ops {
  display: flex;
  gap: 14rpx;
  margin-top: 18rpx;
}
</style>
