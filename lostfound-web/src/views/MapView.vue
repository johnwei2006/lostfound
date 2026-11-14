<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { itemApi } from '@/api'
import type { ItemVO } from '@/types'

const router = useRouter()
const mode = ref<'found' | 'lost'>('found')
const items = ref<ItemVO[]>([])
const loading = ref(false)
const mapFailed = ref(false)
const mapContainer = ref<HTMLElement>()
const mapObj = ref<any>(null)
const markers = ref<any[]>([])
const activeItem = ref<ItemVO | null>(null)

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY as string | undefined

const loadAMap = () =>
  new Promise<void>((resolve, reject) => {
    if ((window as any).AMap) return resolve()
    if (!AMAP_KEY) return reject(new Error('no key'))
    const cbName = '_lfAmapCb' + Date.now()
    ;(window as any)[cbName] = () => resolve()
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}&callback=${cbName}`
    script.onerror = () => reject(new Error('AMap load failed'))
    document.head.appendChild(script)
  })

const renderMarkers = () => {
  if (!mapObj.value) return
  const AMap = (window as any).AMap
  markers.value.forEach((m) => mapObj.value.remove(m))
  markers.value = items.value
    .filter((i) => i.longitude && i.latitude)
    .map((i) => {
      const marker = new AMap.Marker({
        position: [i.longitude, i.latitude],
        title: i.title,
      })
      marker.on('click', () => (activeItem.value = i))
      mapObj.value.add(marker)
      return marker
    })
}

const load = async () => {
  loading.value = true
  activeItem.value = null
  try {
    items.value = await itemApi.mapItems(mode.value, 100)
    renderMarkers()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await loadAMap()
    mapObj.value = new (window as any).AMap.Map(mapContainer.value, { zoom: 12, center: [114.305, 30.593] })
    load()
  } catch {
    mapFailed.value = true
    load()
  }
})

watch(mode, load)
</script>

<template>
  <div class="map-page">
    <div class="lf-container map-head">
      <div>
        <h1 class="lf-page-title">地图视图</h1>
        <p class="lf-page-sub">在地图上查看{{ mode === 'found' ? '拾获' : '丢失' }}物品的位置分布</p>
      </div>
      <el-radio-group v-model="mode">
        <el-radio-button value="found">拾物</el-radio-button>
        <el-radio-button value="lost">失物</el-radio-button>
      </el-radio-group>
    </div>

    <el-alert v-if="mapFailed" type="warning" :closable="false" class="lf-container" style="margin-bottom: 14px">
      未配置高德地图 Key（VITE_AMAP_KEY），已降级为列表模式
    </el-alert>

    <div class="lf-container map-layout" v-if="!mapFailed">
      <div ref="mapContainer" class="big-map lf-card"></div>
      <aside class="map-side">
        <div v-if="activeItem" class="map-pop lf-card lf-card--hover" @click="router.push(`/${activeItem.type.toLowerCase()}/${activeItem.id}`)">
          <img v-if="activeItem.images?.length" :src="activeItem.images[0]" />
          <div>
            <h4>{{ activeItem.title }}</h4>
            <p>{{ activeItem.address || activeItem.city }}</p>
          </div>
        </div>
        <h4 style="margin: 4px 0 10px">附近物品（{{ items.length }}）</h4>
        <div class="map-list">
          <button
            v-for="it in items"
            :key="it.id"
            class="map-item"
            :class="{ active: activeItem?.id === it.id }"
            @click="activeItem = it"
          >
            <b>{{ it.title }}</b>
            <span>{{ it.address || it.city || '未标注位置' }}</span>
          </button>
        </div>
      </aside>
    </div>

    <div v-else class="lf-container">
      <div class="grid">
        <div v-for="it in items" :key="it.id" class="map-item lf-card lf-card--hover" style="padding: 14px; cursor: pointer"
             @click="router.push(`/${it.type.toLowerCase()}/${it.id}`)">
          <b>{{ it.title }}</b>
          <span style="display: block; color: var(--lf-text-3); font-size: 12px; margin-top: 4px">
            {{ it.address || it.city || '未标注位置' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.map-page {
  padding: 30px 0 50px;
}
.map-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 18px;
}
.map-layout {
  display: flex;
  gap: 18px;
  align-items: stretch;
}
.big-map {
  flex: 1;
  height: 560px;
  min-width: 0;
}
.map-side {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.map-pop {
  display: flex;
  gap: 10px;
  padding: 10px;
  margin-bottom: 12px;
  cursor: pointer;
}
.map-pop img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
}
.map-pop h4 {
  font-size: 14px;
}
.map-pop p {
  font-size: 12px;
  color: var(--lf-text-3);
  margin-top: 4px;
}
.map-list {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 500px;
}
.map-item {
  text-align: left;
  border: 1px solid var(--lf-border);
  background: #fff;
  border-radius: var(--lf-radius-sm);
  padding: 10px 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.map-item:hover,
.map-item.active {
  border-color: var(--lf-primary);
  background: var(--lf-primary-soft);
}
.map-item b {
  font-size: 13.5px;
  display: block;
}
.map-item span {
  font-size: 12px;
  color: var(--lf-text-3);
}
@media (max-width: 860px) {
  .map-layout {
    flex-direction: column;
  }
  .map-side {
    width: 100%;
  }
  .big-map {
    height: 400px;
  }
}
</style>
