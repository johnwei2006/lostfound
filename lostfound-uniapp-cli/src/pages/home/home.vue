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

    <!-- 当前位置：自动定位 / 地图选点 / 恢复 -->
    <location-bar />

    <!-- 排序切换：默认距离最近，可切换为发布时间最近 -->
    <view class="sort-bar">
      <text class="sort-label">排序</text>
      <view class="sort-item" :class="{ active: sort === 'distance' }" @tap="switchSort('distance')">距离最近</view>
      <view class="sort-item" :class="{ active: sort === 'time' }" @tap="switchSort('time')">最新发布</view>
    </view>

    <!-- 分类横滑 -->
    <scroll-view scroll-x class="cate-scroll">
      <view
        v-for="c in categories"
        :key="c.value"
        class="cate-item"
        :class="{ active: category === c.value }"
        @tap="selectCategory(c.value)"
      >
        {{ c.label }}
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
import { onPullDownRefresh, onReachBottom, onShow, onUnload } from '@dcloudio/uni-app'
import { itemApi, CATEGORIES } from '@/utils/api'
import { getCurrentLocation, subscribeLocation } from '@/utils/location'

const keyword = ref('')
const mode = ref('found')
const category = ref('')
// 定位不可用时先按时间排序；自动定位 / 手动选点完成后再由位置广播恢复距离排序
const sort = ref(getCurrentLocation().available ? 'distance' : 'time')
/** 用户主动切换过排序后，不再因位置变化自动改动其选择 */
const sortTouched = ref(false)
const list = ref([])
const page = ref(1)
const finished = ref(false)
const loading = ref(false)
const categories = [{ label: '全部', value: '' }, ...CATEGORIES.map((c) => ({ label: c, value: c }))]
/** 请求序号：位置切换等新请求发出后，丢弃仍在飞行的旧响应，避免列表回滚 */
let requestSeq = 0

const load = async (reset = false) => {
  const seq = ++requestSeq
  // reset 由位置变化 / 筛选触发，必须能打断旧请求；仅触底翻页需要防重复
  if (reset) {
    page.value = 1
    finished.value = false
    list.value = []
  } else if (loading.value) {
    return
  }
  loading.value = true
  try {
    // 统一读取位置（手动选点 > 自动定位 > 默认城市）
    const loc = getCurrentLocation()
    const query = {
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      category: category.value || undefined,
      sort: sort.value,
    }
    if (sort.value === 'distance') {
      query.userLat = loc.latitude
      query.userLng = loc.longitude
    }
    const data = await itemApi.page(mode.value, query)
    if (seq !== requestSeq) return
    list.value = reset ? data.records : list.value.concat(data.records)
    finished.value = list.value.length >= data.total
  } catch (e) {
    if (seq === requestSeq) console.error(e)
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

const switchSort = (s) => {
  if (s === 'distance' && !getCurrentLocation().available) {
    return uni.showToast({ title: '未获取到位置，已按时间排序', icon: 'none' })
  }
  if (sort.value === s) return
  sortTouched.value = true
  sort.value = s
  load(true)
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

// 选点 / 恢复 / 自动定位完成后，重置分页并按新坐标重排；用户主动选过排序则尊重其选择
const unsubscribe = subscribeLocation((next) => {
  const loc = next || getCurrentLocation()
  if (!sortTouched.value) sort.value = loc.available ? 'distance' : 'time'
  load(true)
})

onShow(() => load(true))
onPullDownRefresh(() => load(true))
onReachBottom(() => {
  if (!finished.value && !loading.value) {
    page.value++
    load()
  }
})
onUnload(() => unsubscribe())
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
.sort-bar {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-bottom: 20rpx;
}
.sort-label {
  font-size: 25rpx;
  color: #9ca3af;
}
.sort-item {
  padding: 8rpx 26rpx;
  border-radius: 999rpx;
  font-size: 25rpx;
  color: #6b7280;
  background: #fff;
  border: 2rpx solid #e5e9f0;
}
.sort-item.active {
  color: #2563eb;
  border-color: #2563eb;
  background: #eaf1fe;
  font-weight: 600;
}
.cate-scroll {
  width: 100%;
  white-space: nowrap;
  overflow: hidden;
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
