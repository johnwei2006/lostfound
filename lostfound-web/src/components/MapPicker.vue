<script setup lang="ts">
/**
 * 高德地图选点组件
 * 需在高德开放平台申请 Web 端 JS API Key，配置到 VITE_AMAP_KEY。
 * 未配置时自动降级为手动输入经纬度。
 */
import { onMounted, ref, watch } from 'vue'
import { Warning } from '@element-plus/icons-vue'

const props = defineProps<{
  longitude?: number | null
  latitude?: number | null
  address?: string
}>()

const emit = defineEmits<{
  (e: 'update', payload: { longitude: number | null; latitude: number | null; address: string }): void
}>()

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY as string | undefined
const mapFailed = ref(!AMAP_KEY)
const mapContainer = ref<HTMLElement>()
const marker = ref<any>(null)
const mapObj = ref<any>(null)

const loadAMap = () =>
  new Promise<void>((resolve, reject) => {
    if ((window as any).AMap) return resolve()
    const cbName = '_lfAmapCb' + Date.now()
    ;(window as any)[cbName] = () => resolve()
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}&callback=${cbName}`
    script.onerror = () => reject(new Error('AMap load failed'))
    document.head.appendChild(script)
  })

const initMap = async () => {
  try {
    await loadAMap()
    const AMap = (window as any).AMap
    const center = [props.longitude || 116.397428, props.latitude || 39.90923]
    mapObj.value = new AMap.Map(mapContainer.value, {
      zoom: 13,
      center,
    })
    mapObj.value.on('click', (e: any) => {
      const lng = Number(e.lnglat.getLng().toFixed(6))
      const lat = Number(e.lnglat.getLat().toFixed(6))
      setMarker(lng, lat)
      reverseGeocode(lng, lat)
    })
    if (props.longitude && props.latitude) {
      setMarker(props.longitude, props.latitude)
    }
  } catch {
    mapFailed.value = true
  }
}

const setMarker = (lng: number, lat: number) => {
  const AMap = (window as any).AMap
  if (marker.value) {
    mapObj.value.remove(marker.value)
  }
  marker.value = new AMap.Marker({ position: [lng, lat] })
  mapObj.value.add(marker.value)
}

const reverseGeocode = (lng: number, lat: number) => {
  const AMap = (window as any).AMap
  AMap.plugin('AMap.Geocoder', () => {
    const geocoder = new AMap.Geocoder()
    geocoder.getAddress([lng, lat], (status: string, result: any) => {
      const addr = status === 'complete' && result.regeocode ? result.regeocode.formattedAddress : ''
      emit('update', { longitude: lng, latitude: lat, address: addr })
    })
  })
}

const searchPlace = (kw: string) => {
  if (!kw.trim() || !mapObj.value) return
  const AMap = (window as any).AMap
  AMap.plugin('AMap.PlaceSearch', () => {
    const ps = new AMap.PlaceSearch({ map: mapObj.value })
    ps.search(kw)
  })
}

watch(
  () => [props.longitude, props.latitude],
  ([lng, lat]) => {
    if (lng && lat && mapObj.value) setMarker(lng, lat)
  },
)

onMounted(initMap)

defineExpose({ searchPlace })
</script>

<template>
  <div class="map-picker">
    <div v-if="mapFailed" class="map-fallback">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          <span>未配置高德地图 Key（VITE_AMAP_KEY），已降级为手动输入坐标</span>
        </template>
      </el-alert>
      <div class="fallback-form">
        <el-input-number
          :model-value="longitude ?? undefined"
          placeholder="经度，如 116.397"
          :precision="6"
          :controls="false"
          @update:model-value="(v: number | undefined) => emit('update', { longitude: v ?? null, latitude: latitude ?? null, address: address || '' })"
        />
        <el-input-number
          :model-value="latitude ?? undefined"
          placeholder="纬度，如 39.909"
          :precision="6"
          :controls="false"
          @update:model-value="(v: number | undefined) => emit('update', { longitude: longitude ?? null, latitude: v ?? null, address: address || '' })"
        />
      </div>
    </div>
    <div v-else ref="mapContainer" class="map-container">
      <div class="map-tip">
        <el-icon><Warning /></el-icon>
        点击地图选择位置
      </div>
    </div>
    <p v-if="address" class="map-address">📍 {{ address }}</p>
  </div>
</template>

<style scoped>
.map-container {
  height: 320px;
  border-radius: var(--lf-radius-sm);
  overflow: hidden;
  position: relative;
  border: 1px solid var(--lf-border);
}
.map-tip {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
  background: rgba(255, 255, 255, 0.92);
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--lf-primary);
  display: flex;
  align-items: center;
  gap: 5px;
  box-shadow: var(--lf-shadow-sm);
}
.map-fallback .fallback-form {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
.map-address {
  margin-top: 8px;
  font-size: 13px;
  color: var(--lf-text-2);
}
</style>
