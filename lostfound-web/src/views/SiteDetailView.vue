<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Location, Phone, User } from '@element-plus/icons-vue'
import { siteApi } from '@/api'
import type { FoundSite, ItemVO, SiteManager } from '@/types'
import { errMsg } from '@/utils/format'
import ItemCard from '@/components/ItemCard.vue'
import MapPicker from '@/components/MapPicker.vue'

const route = useRoute()
const siteId = Number(route.params.id)
const site = ref<FoundSite | null>(null)
const managers = ref<SiteManager[]>([])
const items = ref<ItemVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(true)
const error = ref('')

const loadItems = async () => {
  try {
    const data = await siteApi.items(siteId, { page: page.value, size })
    items.value = data.records
    total.value = data.total
  } catch (e) { ElMessage.error(errMsg(e, '站内物品加载失败')) }
}

onMounted(async () => {
  try {
    site.value = await siteApi.detail(siteId)
    const [m] = await Promise.all([siteApi.managers(siteId), loadItems()])
    managers.value = m
  } catch (e) {
    error.value = errMsg(e, '招领站信息加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="lf-container site-detail">
    <div v-if="error" class="lf-card error-panel" role="alert">
      <el-empty :description="error" :image="'/empty.svg'" />
    </div>

    <template v-else-if="site">
      <div class="site-hero lf-card">
        <div class="site-main">
          <h1>{{ site.name }}</h1>
          <p><el-icon><Location /></el-icon>{{ site.address }}</p>
          <p v-if="site.phone"><el-icon><Phone /></el-icon>{{ site.phone }}</p>
          <p><el-icon><Clock /></el-icon>{{ site.openTime || '全天开放' }}</p>
          <p v-if="site.description" class="site-desc">{{ site.description }}</p>
          <div v-if="managers.length" class="managers">
            <el-icon><User /></el-icon>
            站点管理员：{{ managers.map((m) => m.nickname).join('、') }}
          </div>
        </div>
      </div>

      <div v-if="site.longitude && site.latitude" class="lf-card map-panel">
        <h3>站点位置</h3>
        <MapPicker :longitude="site.longitude" :latitude="site.latitude" :address="site.address" />
      </div>

      <section class="items-section">
        <h2 class="lf-page-title">站内托管物品 <span class="item-count">{{ total }}</span></h2>
        <p class="items-hint">仅展示站点直接发布或经站点管理员审批通过入库的物品。</p>
        <div v-if="items.length" class="grid">
          <ItemCard v-for="it in items" :key="it.id" :item="it" />
        </div>
        <el-empty v-else description="站内暂无托管物品" :image="'/empty.svg'" />
        <div v-if="total > size" class="pager">
          <el-pagination layout="prev, pager, next" :total="total" :page-size="size" :current-page="page"
            @current-change="(p: number) => { page = p; loadItems() }" />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.site-detail { padding: 30px 20px 50px; }
.site-hero { padding: 30px; }
.site-main h1 { font-size: 24px; }
.site-main p { margin-top: 10px; color: var(--lf-text-2); display: flex; align-items: center; gap: 7px; font-size: 14px; }
.site-desc { margin-top: 14px !important; line-height: 1.8; }
.managers { margin-top: 16px; display: inline-flex; align-items: center; gap: 7px; background: var(--lf-accent-soft); color: var(--lf-accent); padding: 6px 14px; border-radius: 999px; font-size: 13px; }
.map-panel { margin-top: 20px; padding: 20px; }
.map-panel h3 { margin-bottom: 12px; font-size: 16px; }
.items-section { margin-top: 30px; }
.item-count { margin-left: 8px; font-size: 14px; color: var(--lf-text-3); font-weight: 400; }
.items-hint { margin: 8px 0 18px; font-size: 13px; color: var(--lf-text-3); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 18px; }
.pager { display: flex; justify-content: center; margin-top: 22px; }
.error-panel { padding: 40px 20px; }
</style>
