<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Location, Refresh } from '@element-plus/icons-vue'
import { itemApi } from '@/api'
import type { ItemQuery, ItemVO } from '@/types'
import { CATEGORIES } from '@/types'
import ItemCard from '@/components/ItemCard.vue'
import {
  DEFAULT_CITY,
  ensureLocation,
  getCachedLocation,
  refreshLocation,
  type WebLocation,
} from '@/utils/location'

const props = defineProps<{ type: 'found' | 'lost' }>()
const route = useRoute()
const router = useRouter()

const isFound = computed(() => props.type === 'found')
const query = ref<ItemQuery>({
  keyword: (route.query.keyword as string) || '',
  category: (route.query.category as string) || '',
  city: '',
  status: '',
  days: undefined,
  sort: 'distance',
  page: 1,
  size: 12,
})

const list = ref<ItemVO[]>([])
const total = ref(0)
const loading = ref(false)
const locating = ref(false)
const location = ref<WebLocation>(getCachedLocation())
/** 用户手动切换过排序后，不再因定位结果自动改动其选择 */
const sortTouched = ref(false)

const load = async () => {
  loading.value = true
  try {
    const params: ItemQuery = { ...query.value, keyword: query.value.keyword || undefined }
    if (params.sort === 'distance') {
      params.userLat = location.value.latitude
      params.userLng = location.value.longitude
    } else {
      params.userLat = undefined
      params.userLng = undefined
    }
    const data = await itemApi.page(props.type, params)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const locationHint = computed(() => {
  if (location.value.available) {
    return query.value.sort === 'distance'
      ? '已按当前位置排序，距离由近到远'
      : '已获取当前位置，可切换为「距离最近」查看附近物品'
  }
  return query.value.sort === 'distance'
    ? `未获取到定位，暂按${DEFAULT_CITY}位置计算距离`
    : '未获取到定位，已切换为按发布时间排序'
})

/** 定位结果回来后刷新列表；首次无缓存时先渲染默认坐标，避免首屏等待授权 */
const applyLocation = async (force = false) => {
  if (locating.value) return
  locating.value = true
  try {
    const loc = force ? await refreshLocation() : await ensureLocation()
    location.value = loc
    if (!sortTouched.value) query.value.sort = loc.available ? 'distance' : 'time'
    load()
  } finally {
    locating.value = false
  }
}
const relocate = () => void applyLocation(true)

onMounted(() => {
  load()
  if (!location.value.available) void applyLocation(false)
})
watch(() => route.query, () => {
  query.value.keyword = (route.query.keyword as string) || ''
  query.value.category = (route.query.category as string) || ''
  query.value.page = 1
  load()
})
watch(() => props.type, load)

const onPageChange = (page: number) => {
  query.value.page = page
  load()
}
const onFilter = () => {
  query.value.page = 1
  load()
}
const onSortChange = () => {
  sortTouched.value = true
  onFilter()
}
const resetFilter = () => {
  sortTouched.value = false
  query.value = { keyword: '', category: '', city: '', status: '', days: undefined, sort: 'distance', page: 1, size: 12 }
  load()
}
</script>

<template>
  <div class="lf-container list-page">
    <div class="list-head">
      <div>
        <h1 class="lf-page-title">{{ isFound ? '找失主 · 拾物招领' : '寻失物 · 失物登记' }}</h1>
        <p class="lf-page-sub">
          {{ isFound ? '以下物品已被好心人拾获，等待失主认领' : '以下物品已丢失，如有线索请联系失主' }}
        </p>
      </div>
      <el-button type="primary" round @click="router.push(`/publish?type=${type}`)">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>
        发布{{ isFound ? '拾物' : '失物' }}
      </el-button>
    </div>

    <div class="loc-hint" :class="{ 'loc-hint--warn': !location.available }" role="status" aria-live="polite">
      <el-icon :size="15"><Location /></el-icon>
      <span class="loc-hint__text">{{ locationHint }}</span>
      <el-button link type="primary" :loading="locating" @click="relocate">
        <el-icon style="margin-right: 4px"><Refresh /></el-icon>重新定位
      </el-button>
    </div>

    <div class="list-layout">
      <!-- 筛选器 -->
      <aside class="filter-panel lf-card">
        <h4 class="filter-title">筛选条件</h4>
        <div class="filter-group">
          <label>关键词</label>
          <el-input v-model="query.keyword" placeholder="物品名称/描述" clearable @keyup.enter="onFilter" @clear="onFilter" />
        </div>
        <div class="filter-group">
          <label>类别</label>
          <el-select v-model="query.category" placeholder="全部类别" clearable @change="onFilter">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>状态</label>
          <el-select v-model="query.status" placeholder="全部状态" clearable @change="onFilter">
            <el-option label="待认领" value="WAITING" />
            <el-option label="待确认" value="CLAIMED" />
            <el-option label="已完成" value="FINISHED" />
          </el-select>
        </div>
        <div class="filter-group">
          <label>时间范围</label>
          <el-select v-model="query.days" placeholder="全部时间" clearable @change="onFilter">
            <el-option label="最近 3 天" :value="3" />
            <el-option label="最近 7 天" :value="7" />
            <el-option label="最近 30 天" :value="30" />
          </el-select>
        </div>
        <el-button style="width: 100%" @click="resetFilter">重置筛选</el-button>
      </aside>

      <!-- 列表 -->
      <section class="list-main">
        <div class="list-toolbar">
          <span class="list-count">共 <b>{{ total }}</b> 条{{ isFound ? '拾物' : '失物' }}记录</span>
          <el-radio-group v-model="query.sort" @change="onSortChange">
            <el-radio-button value="distance">距离最近</el-radio-button>
            <el-radio-button value="time">最新发布</el-radio-button>
            <el-radio-button value="hot">最多浏览</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="loading" class="grid">
          <div v-for="i in 8" :key="i" class="lf-card"><div class="lf-skeleton" style="height: 280px; border-radius: 14px" /></div>
        </div>
        <template v-else>
          <div v-if="list.length" class="grid">
            <ItemCard v-for="it in list" :key="it.id" :item="it" />
          </div>
          <el-empty v-else description="没有符合条件的物品" :image="'/empty.svg'">
            <el-button type="primary" round @click="resetFilter">重置筛选</el-button>
          </el-empty>
          <div v-if="total > (query.size ?? 12)" class="pager">
            <el-pagination
              layout="prev, pager, next, total"
              :total="total"
              :page-size="query.size"
              :current-page="query.page"
              @current-change="onPageChange"
            />
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.list-page {
  padding: 34px 20px 50px;
}
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}
.loc-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 9px 16px;
  margin-bottom: 18px;
  border: 1px solid #d7e5fd;
  border-radius: var(--lf-radius-sm);
  background: var(--lf-primary-soft);
  color: var(--lf-text);
  font-size: 13px;
  line-height: 1.5;
}
.loc-hint--warn {
  background: var(--lf-accent-soft);
  border-color: #fbd9b8;
  color: #7c3a12;
}
.loc-hint__text {
  flex: 1;
  min-width: 200px;
}
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.list-count {
  font-size: 13px;
  color: #475569;
}
.list-count b {
  color: var(--lf-primary);
  font-weight: 600;
}
.list-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.filter-panel {
  width: 240px;
  padding: 20px;
  position: sticky;
  top: 82px;
  flex-shrink: 0;
}
.filter-title {
  font-size: 15px;
  margin-bottom: 16px;
}
.filter-group {
  margin-bottom: 16px;
}
.filter-group label {
  display: block;
  font-size: 12.5px;
  color: var(--lf-text-2);
  margin-bottom: 6px;
}
.filter-group :deep(.el-select),
.filter-group :deep(.el-input) {
  width: 100%;
}
.list-main {
  flex: 1;
  min-width: 0;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}
.pager {
  margin-top: 26px;
  display: flex;
  justify-content: center;
}
@media (max-width: 860px) {
  .list-layout {
    flex-direction: column;
  }
  .filter-panel {
    width: 100%;
    position: static;
  }
}
@media (max-width: 520px) {
  .list-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
  .loc-hint {
    align-items: flex-start;
  }
}
</style>
