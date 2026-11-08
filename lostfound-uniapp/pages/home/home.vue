<template>
  <view class="home">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索物品名称…"
        confirm-type="search"
        @confirm="goSearch"
      />
      <view class="search-btn lf-accent" @tap="goMap">地图</view>
    </view>

    <!-- 类型切换 -->
    <view class="type-tabs">
      <view class="tab" :class="{ active: mode === 'found' }" @tap="switchMode('found')">拾物招领</view>
      <view class="tab" :class="{ active: mode === 'lost' }" @tap="switchMode('lost')">寻物启事</view>
    </view>

    <!-- 分类横滑 -->
    <scroll-view scroll-x class="cate-scroll">
      <view
        v-for="c in categories"
        :key="c"
        class="cate-item"
        :class="{ active: category === c }"
        @tap="selectCategory(c)"
      >
        {{ c }}
      </view>
    </scroll-view>

    <!-- 列表 -->
    <view class="list">
      <item-card v-for="it in list" :key="it.id" :item="it" />
      <view v-if="!loading && list.length === 0" class="lf-empty">暂无{{ mode === 'found' ? '拾物' : '失物' }}信息</view>
      <view v-if="loading" class="lf-empty">加载中…</view>
      <view v-if="!loading && finished && list.length > 0" class="lf-empty">— 没有更多了 —</view>
    </view>

    <!-- 发布浮层 -->
    <view class="fab" @tap="goPublish">＋</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { itemApi, CATEGORIES } from '@/utils/api'

const keyword = ref('')
const mode = ref('found')
const category = ref('')
const list = ref([])
const page = ref(1)
const finished = ref(false)
const loading = ref(false)
const categories = ['', ...CATEGORIES]

const load = async (reset = false) => {
  if (loading.value) return
  if (reset) {
    page.value = 1
    finished.value = false
    list.value = []
  }
  loading.value = true
  try {
    const data = await itemApi.page(mode.value, {
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      category: category.value || undefined,
    })
    list.value = reset ? data.records : list.value.concat(data.records)
    finished.value = list.value.length >= data.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const switchMode = (m) => {
  mode.value = m
  load(true)
}
const selectCategory = (c) => {
  category.value = category.value === c ? '' : c
  load(true)
}
const goSearch = () => load(true)
const goPublish = () => uni.switchTab({ url: '/pages/publish/publish' })
const goMap = () => uni.navigateTo({ url: '/pages/map/map' })

onShow(() => load(true))
onPullDownRefresh(() => load(true))
onReachBottom(() => {
  if (!finished.value) {
    page.value++
    load()
  }
})
</script>

<style scoped>
.home {
  padding: 20rpx;
}
.search-bar {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.search-input {
  flex: 1;
  background: #fff;
  border-radius: 999rpx;
  padding: 16rpx 30rpx;
  font-size: 28rpx;
}
.search-btn {
  background: #fff;
  border-radius: 999rpx;
  padding: 16rpx 34rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.type-tabs {
  display: flex;
  background: #fff;
  border-radius: 999rpx;
  padding: 6rpx;
  margin-bottom: 20rpx;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #6b7280;
  transition: all 0.2s;
}
.tab.active {
  background: #2563eb;
  color: #fff;
  font-weight: 600;
}
.cate-scroll {
  white-space: nowrap;
  margin-bottom: 20rpx;
}
.cate-item {
  display: inline-block;
  background: #fff;
  border-radius: 999rpx;
  padding: 10rpx 30rpx;
  margin-right: 14rpx;
  font-size: 25rpx;
  color: #6b7280;
  border: 2rpx solid #e5e9f0;
}
.cate-item.active {
  background: #eaf1fe;
  color: #2563eb;
  border-color: #2563eb;
}
.fab {
  position: fixed;
  right: 40rpx;
  bottom: 140rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #f97316;
  color: #fff;
  font-size: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 30rpx rgba(249, 115, 22, 0.4);
  z-index: 10;
}
</style>
