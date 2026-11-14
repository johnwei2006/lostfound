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
      <view v-if="!loading && list.length === 0" class="lf-empty" @tap="load(true)">
        {{ loadError ? '加载失败，点击重试' : (mode === 'found' ? '暂无拾物信息' : '暂无失物信息') }}
      </view>
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
import { createDedupeGuard } from '@/utils/request-cache'

const guard = createDedupeGuard(800)
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
/** 请求序号：只有真正发出的请求才占用；activeSeq 为最新一次筛选/位置重置 */
let requestSeq = 0
let activeSeq = 0
/** 在飞行的请求数：确保 loading 一定会被释放，不会卡在「加载中」 */
let pending = 0
let hasLoaded = false
let autoRetried = false
let retryTimer = null
const loadError = ref(false)

const load = async (reset = false) => {
  // 页面内容不足一屏时小程序会连续触发 onReachBottom；列表为空 / 加载中 / 已到底时一律不发请求，
  // 否则页码会被推到很大，且空转的调用会占用序号，导致首屏响应被整体丢弃
  if (!reset && (loading.value || finished.value || list.value.length === 0)) return

  const seq = ++requestSeq
  if (reset) {
    activeSeq = seq
    page.value = 1
    finished.value = false
    // 不清空已有列表：新请求成功后再整体替换，任一次请求失败都不会留下空白首页
  } else {
    page.value += 1
  }
  pending += 1
  loading.value = true
  try {
    // 统一读取位置（手动选点 > 自动定位 > 默认城市）
    const loc = getCurrentLocation()
    // 未选中的筛选项不要放进 data：小程序端 undefined 可能被序列化成字符串 "undefined"，
    // 后端会当成真实的 keyword/category 条件，导致列表返回 0 条
    const query = { page: page.value, size: 10, sort: sort.value }
    if (keyword.value) query.keyword = keyword.value
    if (category.value) query.category = category.value
    if (sort.value === 'distance') {
      query.userLat = loc.latitude
      query.userLng = loc.longitude
    }
    console.log('[home] page query', mode.value, query)
    const data = await itemApi.page(mode.value, query)
    // 只丢弃「已被更新的筛选/位置重置取代」的旧响应，避免并发请求把首屏数据全部丢掉
    if (seq < activeSeq) return
    hasLoaded = true
    loadError.value = false
    let records = data.records || []
    // 分页接口被异常条件过滤成空时，用最新物品接口兜底，保证首屏有内容可看
    if (reset && page.value === 1 && records.length === 0 && !keyword.value && !category.value) {
      try {
        const latest = await itemApi.latest(mode.value, 20)
        if (seq < activeSeq) return
        if (Array.isArray(latest) && latest.length) records = latest
      } catch (err) {
        console.warn('[home] latest fallback failed', err)
      }
    }
    console.log('[home] page result', data && data.total, records.length)
    if (reset) {
      if (seq === activeSeq) list.value = records
    } else {
      list.value = list.value.concat(records)
    }
    console.log('[home] list set', list.value.length, 'loading', loading.value)
    finished.value = list.value.length >= (data.total || records.length)
  } catch (e) {
    if (seq >= activeSeq) {
      console.error('[home] page failed', e)
      loadError.value = true
      // 首屏请求偶发失败（网络抖动 / 域名未就绪）自动重试一次，避免列表一直空白
      if (!hasLoaded && !autoRetried) {
        autoRetried = true
        retryTimer = setTimeout(() => {
          retryTimer = null
          load(true)
        }, 800)
      }
    }
  } finally {
    pending -= 1
    if (pending === 0) {
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
  guard.reset()
  load(true)
})

onShow(() => guard.run('home', () => load(true)))
onPullDownRefresh(() => { guard.reset(); load(true) })
onReachBottom(() => load())
onUnload(() => {
  if (retryTimer) clearTimeout(retryTimer)
  unsubscribe()
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
