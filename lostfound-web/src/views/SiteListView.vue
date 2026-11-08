<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { OfficeBuilding, Location, Phone, Clock } from '@element-plus/icons-vue'
import { siteApi } from '@/api'
import type { FoundSite } from '@/types'

const router = useRouter()
const list = ref<FoundSite[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const data = await siteApi.page({ keyword: keyword.value || undefined, page: page.value, size: 9 })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="lf-container site-page">
    <div class="page-head">
      <div>
        <h1 class="lf-page-title">招领站网络</h1>
        <p class="lf-page-sub">拾获物品可就近交至招领站托管，失主可前往认领</p>
      </div>
      <el-input
        v-model="keyword"
        placeholder="搜索站点名称/地址"
        style="width: 260px"
        clearable
        @keyup.enter="page = 1; load()"
      />
    </div>

    <div v-if="loading">加载中…</div>
    <div v-else-if="list.length" class="site-grid">
      <div v-for="s in list" :key="s.id" class="site-card lf-card lf-card--hover" @click="router.push('/sites/' + s.id)">
        <div class="site-top">
          <div class="site-icon"><el-icon :size="22"><OfficeBuilding /></el-icon></div>
          <div>
            <h3>{{ s.name }}</h3>
            <p class="site-open"><el-icon :size="12"><Clock /></el-icon>{{ s.openTime || '全天开放' }}</p>
          </div>
        </div>
        <p class="site-row"><el-icon><Location /></el-icon>{{ s.address }}</p>
        <p v-if="s.phone" class="site-row"><el-icon><Phone /></el-icon>{{ s.phone }}</p>
        <p v-if="s.description" class="site-desc">{{ s.description }}</p>
      </div>
    </div>
    <el-empty v-else description="暂无招领站" :image="'/empty.svg'" />

    <div v-if="total > 9" style="display: flex; justify-content: center; margin-top: 26px">
      <el-pagination layout="prev, pager, next" :total="total" :page-size="9" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </div>
</template>

<style scoped>
.site-page {
  padding: 34px 20px 50px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}
.site-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
}
.site-card {
  padding: 22px;
  cursor: pointer;
}
.site-top {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
}
.site-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.site-top h3 {
  font-size: 16px;
}
.site-open {
  font-size: 12px;
  color: var(--lf-text-3);
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}
.site-row {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--lf-text-2);
  margin-top: 7px;
}
.site-desc {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--lf-border);
  font-size: 12.5px;
  color: var(--lf-text-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
