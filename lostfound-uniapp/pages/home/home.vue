<template>
  <view class="home">
    <site-context-bar />

    <!-- 站点管理员工作台：隐藏公共信息流与地图/附近入口 -->
    <template v-if="managerView">
      <view class="wb-hero">
        <view class="wb-hero__label">站点工作台</view>
        <view class="wb-hero__name">{{ siteName || '请选择招领站' }}</view>
        <view class="wb-hero__desc">只管理本站物品与待办，其它发布信息不在此展示</view>
        <view class="wb-hero__glow" />
      </view>

      <view class="wb-stats">
        <view v-for="s in statCards" :key="s.key" class="wb-stat" @tap="goManage(s.path)">
          <text class="wb-stat__num">{{ s.value }}</text>
          <text class="wb-stat__label">{{ s.label }}</text>
          <view class="wb-stat__bar" :style="{ background: s.color }" />
        </view>
      </view>

      <view class="lf-group-title wb-title">日常管理</view>
      <view class="wb-entries">
        <view v-for="e in entries" :key="e.path" class="wb-entry" @tap="goManage(e.path)">
          <view class="wb-entry__icon" :style="{ background: e.tint }">
            <text :style="{ color: e.color }">{{ e.mark }}</text>
          </view>
          <view class="wb-entry__body">
            <text class="wb-entry__name">{{ e.name }}</text>
            <text class="wb-entry__desc">{{ e.desc }}</text>
          </view>
          <text class="wb-entry__arrow">›</text>
        </view>
      </view>

      <view class="lf-group-title wb-title">本站最近物品</view>
      <view v-if="recent.length" class="wb-recent">
        <view v-for="it in recent" :key="it.id" class="wb-recent__item">
          <view class="wb-recent__main">
            <text class="wb-recent__name">{{ it.title }}</text>
            <text class="lf-muted">{{ it.category }} · {{ formatTime(it.createTime) }}</text>
          </view>
          <view class="wb-recent__ops">
            <text class="lf-tag" :class="statusCls(it.status)">{{ statusText(it.status) }}</text>
            <text class="wb-recent__btn" @tap="toggleShelf(it)">
              {{ it.status === 'OFFLINE' ? '上架' : '下架' }}
            </text>
          </view>
        </view>
      </view>
      <view v-else-if="!wbLoading" class="lf-empty">本站暂无物品，可在「发布」页代发布</view>
      <view v-if="wbLoading" class="lf-empty">加载中…</view>
      <view class="wb-foot">切换身份或招领站：使用页面顶部站点条 / 「我的」页</view>
    </template>

    <!-- 普通用户视图：与原有首页完全一致 -->
    <template v-else>
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
    </template>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow, onUnload } from '@dcloudio/uni-app'
import { itemApi, siteApi, CATEGORIES, STATUS_MAP } from '@/utils/api'
import { getCurrentLocation, subscribeLocation } from '@/utils/location'
import { createDedupeGuard } from '@/utils/request-cache'
import siteStore, { CONTEXT_CHANGE_EVENT } from '@/utils/site-store'
import { viewMode, syncViewMode } from '@/utils/view-mode'

const guard = createDedupeGuard(800)

/* ---------------- 站点管理员工作台 ---------------- */
const managerView = computed(() => viewMode.value === 'manager')
const wbRevision = ref(0)
const siteName = computed(() => { wbRevision.value; return siteStore.currentSiteName })
const wbLoading = ref(false)
const stats = ref(null)
const recent = ref([])

const statCards = computed(() => {
  const s = stats.value || {}
  return [
    { key: 'items', label: '本站物品', value: s.siteItems || 0, color: '#2563EB', path: '/pages/manage/items' },
    { key: 'transfers', label: '待接收转交', value: s.pendingTransfers || 0, color: '#F59E0B', path: '/pages/manage/transfers' },
    { key: 'claims', label: '待审批认领', value: s.pendingClaims || 0, color: '#DC2626', path: '/pages/manage/claims' },
    { key: 'returned', label: '已归还', value: s.returnedItems || 0, color: '#16A34A', path: '/pages/manage/returns' },
  ]
})
const entries = [
  { name: '接收转交物品', desc: '审批用户转交到本站的物品', mark: '收', color: '#1D4ED8', tint: '#EAF1FE', path: '/pages/manage/transfers' },
  { name: '物品上下架', desc: '查询、代发布与上架/下架', mark: '物', color: '#B45309', tint: '#FFF7E6', path: '/pages/manage/items' },
  { name: '认领申请审批', desc: '通过或驳回认领申请', mark: '审', color: '#16A34A', tint: '#E7F8F1', path: '/pages/manage/claims' },
  { name: '现场登记归还', desc: '登记领取人并查询归还记录', mark: '还', color: '#DC2626', tint: '#FEECEC', path: '/pages/manage/returns' },
]

const statusText = (s) => (STATUS_MAP[s] && STATUS_MAP[s].text) || s || '—'
const statusCls = (s) => (STATUS_MAP[s] && STATUS_MAP[s].cls) || 'lf-tag--muted'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const goManage = (path) => uni.navigateTo({ url: path })

let wbBusy = false
const loadWorkbench = async () => {
  if (wbBusy) return
  const siteId = siteStore.currentSiteId
  if (!siteId) {
    stats.value = null
    recent.value = []
    return
  }
  wbBusy = true
  wbLoading.value = true
  try {
    // 工作台一次概览 + 一次最近物品，不并发拉四个队列
    const [s, items] = await Promise.all([
      siteApi.stats(siteId),
      siteApi.items(siteId, { page: 1, size: 5 }),
    ])
    stats.value = s || null
    recent.value = (items && items.records) || []
  } catch (e) {
    console.warn('[home] 工作台加载失败', e)
  } finally {
    wbBusy = false
    wbLoading.value = false
    uni.stopPullDownRefresh()
  }
}

const toggleShelf = async (it) => {
  const next = it.status === 'OFFLINE' ? 'WAITING' : 'OFFLINE'
  try {
    await itemApi.changeStatus('found', it.id, next)
    uni.showToast({ title: next === 'OFFLINE' ? '已下架' : '已上架', icon: 'none' })
    loadWorkbench()
  } catch (e) {
    console.warn('[home] 上下架失败', e)
  }
}

const onContextChange = () => {
  syncViewMode()
  wbRevision.value += 1
  guard.reset()
  if (managerView.value) loadWorkbench()
  else load(true)
}

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

onShow(() => {
  syncViewMode()
  wbRevision.value += 1
  if (managerView.value) {
    // 站点列表可能尚未加载，ensureLoaded 内部只在管理员身份下发一次请求
    siteStore.ensureLoaded().then(loadWorkbench)
  } else {
    guard.run('home', () => load(true))
  }
})
onPullDownRefresh(() => {
  guard.reset()
  if (managerView.value) loadWorkbench()
  else load(true)
})
onReachBottom(() => { if (!managerView.value) load() })
onMounted(() => {
  if (typeof uni.$on === 'function') uni.$on(CONTEXT_CHANGE_EVENT, onContextChange)
})
onUnmounted(() => {
  if (typeof uni.$off === 'function') uni.$off(CONTEXT_CHANGE_EVENT, onContextChange)
})
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

/* 站点管理员工作台 */
.wb-hero {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
  padding: 34rpx 30rpx;
  margin-bottom: 22rpx;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 55%, #3b82f6 100%);
  box-shadow: 0 12rpx 30rpx rgba(37, 99, 235, .22);
}
.wb-hero__label { color: rgba(255, 255, 255, .72); font-size: 22rpx; letter-spacing: 4rpx; }
.wb-hero__name { display: block; margin-top: 10rpx; color: #fff; font-size: 40rpx; font-weight: 700; }
.wb-hero__desc { display: block; margin-top: 8rpx; color: rgba(255, 255, 255, .82); font-size: 23rpx; }
.wb-hero__glow {
  position: absolute; right: -50rpx; top: -60rpx;
  width: 220rpx; height: 220rpx; border-radius: 50%;
  background: rgba(255, 255, 255, .10);
}
.wb-stats { display: flex; gap: 14rpx; margin-bottom: 28rpx; }
.wb-stat {
  position: relative; flex: 1; min-width: 0; overflow: hidden;
  background: #fff; border-radius: 18rpx; padding: 24rpx 12rpx 20rpx;
  text-align: center; box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, .05);
}
.wb-stat__num { display: block; font-size: 42rpx; font-weight: 700; color: #1d4ed8; line-height: 1.2; }
.wb-stat__label { display: block; margin-top: 6rpx; color: #6b7280; font-size: 21rpx; }
.wb-stat__bar { position: absolute; left: 26rpx; right: 26rpx; bottom: 0; height: 5rpx; border-radius: 6rpx 6rpx 0 0; opacity: .85; }
.wb-title { margin: 0 0 14rpx; }
.wb-entries { margin-bottom: 28rpx; }
.wb-entry {
  display: flex; align-items: center; gap: 20rpx;
  background: #fff; border-radius: 18rpx; padding: 22rpx 24rpx; margin-bottom: 14rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, .05);
  transition: transform .15s ease;
}
.wb-entry:active { transform: scale(.985); }
.wb-entry__icon {
  width: 68rpx; height: 68rpx; border-radius: 18rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 28rpx; font-weight: 700;
}
.wb-entry__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.wb-entry__name { font-size: 29rpx; font-weight: 600; color: #1f2937; }
.wb-entry__desc { font-size: 22rpx; color: #9ca3af; }
.wb-entry__arrow { color: #c0c8d4; font-size: 36rpx; }
.wb-recent { background: #fff; border-radius: 18rpx; padding: 6rpx 24rpx; margin-bottom: 20rpx; }
.wb-recent__item { display: flex; align-items: center; gap: 16rpx; padding: 22rpx 0; border-bottom: 2rpx solid #f1f5f9; }
.wb-recent__item:last-child { border-bottom: none; }
.wb-recent__main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.wb-recent__name { font-size: 28rpx; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wb-recent__ops { flex-shrink: 0; display: flex; align-items: center; gap: 12rpx; }
.wb-recent__btn { color: #2563eb; font-size: 24rpx; padding: 6rpx 18rpx; border: 2rpx solid #bfdbfe; border-radius: 999rpx; }
.wb-foot { text-align: center; color: #9ca3af; font-size: 22rpx; padding: 20rpx 0 10rpx; }
</style>
