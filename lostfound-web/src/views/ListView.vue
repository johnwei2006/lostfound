<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { itemApi } from '@/api'
import type { ItemQuery, ItemVO } from '@/types'
import { CATEGORIES } from '@/types'
import ItemCard from '@/components/ItemCard.vue'

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
  sort: 'new',
  page: 1,
  size: 12,
})

const list = ref<ItemVO[]>([])
const total = ref(0)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const data = await itemApi.page(props.type, { ...query.value, keyword: query.value.keyword || undefined })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
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
const resetFilter = () => {
  query.value = { keyword: '', category: '', city: '', status: '', days: undefined, sort: 'new', page: 1, size: 12 }
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
        <div class="filter-group">
          <label>排序</label>
          <el-radio-group v-model="query.sort" @change="onFilter">
            <el-radio-button value="new">最新</el-radio-button>
            <el-radio-button value="hot">最热</el-radio-button>
          </el-radio-group>
        </div>
        <el-button style="width: 100%" @click="resetFilter">重置筛选</el-button>
      </aside>

      <!-- 列表 -->
      <section class="list-main">
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
</style>
