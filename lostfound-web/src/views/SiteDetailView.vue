<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Location, Phone, Clock, User } from '@element-plus/icons-vue'
import { siteApi, itemApi } from '@/api'
import type { FoundSite, ItemVO } from '@/types'
import ItemCard from '@/components/ItemCard.vue'
import MapPicker from '@/components/MapPicker.vue'

const route = useRoute()
const site = ref<FoundSite | null>(null)
const managers = ref<{ userId: number; nickname: string }[]>([])
const items = ref<ItemVO[]>([])
const loading = ref(true)

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    site.value = await siteApi.detail(id)
    const [m, page] = await Promise.all([
      siteApi.managers(id),
      itemApi.page('found', { siteId: id, page: 1, size: 12 }),
    ])
    managers.value = m
    items.value = page.records
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="lf-container site-detail" v-if="site">
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

    <div v-if="site.longitude && site.latitude" class="lf-card" style="margin-top: 20px; padding: 20px">
      <h3 style="margin-bottom: 12px">站点位置</h3>
      <MapPicker :longitude="site.longitude" :latitude="site.latitude" :address="site.address" />
    </div>

    <section style="margin-top: 30px">
      <h2 class="lf-page-title" style="margin-bottom: 16px">站内托管物品</h2>
      <div v-if="items.length" class="grid">
        <ItemCard v-for="it in items" :key="it.id" :item="it" />
      </div>
      <el-empty v-else description="站内暂无托管物品" :image="'/empty.svg'" />
    </section>
  </div>
</template>

<style scoped>
.site-detail {
  padding: 30px 20px 50px;
}
.site-hero {
  padding: 30px;
}
.site-main h1 {
  font-size: 24px;
}
.site-main p {
  margin-top: 10px;
  color: var(--lf-text-2);
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
}
.site-desc {
  margin-top: 14px !important;
  line-height: 1.8;
}
.managers {
  margin-top: 16px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: var(--lf-accent-soft);
  color: var(--lf-accent);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}
</style>
