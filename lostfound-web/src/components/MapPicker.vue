<script setup lang="ts">
/**
 * 发布页地点定位组件
 *
 * 高德地图不可用时（Key 未配置、域名白名单不符、缺少 securityJsCode、CDN 超时）
 * 会自动降级，避免出现「一整块空白且无法操作」的区域。
 * 无论地图是否可用，都提供：地点名称搜索定位、浏览器当前位置、手动填写 GCJ02 坐标。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim, Location, Warning } from '@element-plus/icons-vue'
import { wgs84ToGcj02 } from '@/utils/location'

const props = defineProps<{
  longitude?: number | null
  latitude?: number | null
  address?: string
}>()

const emit = defineEmits<{
  (e: 'update', payload: { longitude: number | null; latitude: number | null; address: string }): void
}>()

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY as string | undefined
const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE as string | undefined

const mapFailed = ref(!AMAP_KEY)
const mapReady = ref(false)
const mapContainer = ref<HTMLElement>()
const marker = ref<any>(null)
const mapObj = ref<any>(null)
const keyword = ref('')
const searching = ref(false)
const locating = ref(false)

let fallbackTimer: number | undefined
let searchTimer: number | undefined

const emitLocation = (longitude: number | null, latitude: number | null, address = '') =>
  emit('update', { longitude, latitude, address })

function loadAMap() {
  return new Promise<void>((resolve, reject) => {
    if ((window as any).AMap) return resolve()
    if (AMAP_SECURITY_CODE) {
      ;(window as any)._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE }
    }
    const cbName = '_lfAmapCb' + Date.now()
    const cleanup = () => {
      window.clearTimeout(timer)
      delete (window as any)[cbName]
    }
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('高德地图加载超时'))
    }, 8000)
    ;(window as any)[cbName] = () => {
      cleanup()
      resolve()
    }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}&callback=${cbName}`
    script.onerror = () => {
      cleanup()
      reject(new Error('高德地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })
}

function degrade(reason: string) {
  if (mapFailed.value) return
  console.warn('[MapPicker] 地图不可用，已降级：' + reason)
  if (mapObj.value) {
    try {
      mapObj.value.destroy?.()
    } catch {
      // 地图可能只初始化了一半
    }
  }
  mapObj.value = null
  mapFailed.value = true
}

function setMarker(lng: number, lat: number) {
  const AMap = (window as any).AMap
  if (!AMap || !mapObj.value) return
  if (marker.value) mapObj.value.remove(marker.value)
  marker.value = new AMap.Marker({ position: [lng, lat] })
  mapObj.value.add(marker.value)
}

function reverseGeocode(lng: number, lat: number) {
  // 先把坐标写回表单：逆地理编码依赖高德 Web 服务，缺少安全密钥时可能一直不回调
  emitLocation(lng, lat)
  const AMap = (window as any).AMap
  if (!AMap) return
  AMap.plugin('AMap.Geocoder', () => {
    const geocoder = new AMap.Geocoder()
    geocoder.getAddress([lng, lat], (status: string, result: any) => {
      const addr = status === 'complete' && result?.regeocode ? result.regeocode.formattedAddress : ''
      if (addr) emitLocation(lng, lat, addr)
    })
  })
}

async function initMap() {
  try {
    await loadAMap()
    const AMap = (window as any).AMap
    const center = [props.longitude || 116.397428, props.latitude || 39.90923]
    mapObj.value = new AMap.Map(mapContainer.value, {
      zoom: 13,
      center,
      resizeEnable: true,
    })
    // 底图真正加载完成才算可用；Key 域名白名单不符时容器会一直空白，必须超时降级
    fallbackTimer = window.setTimeout(() => {
      if (!mapReady.value) degrade('底图渲染超时，请检查高德 Key 的域名白名单与安全密钥')
    }, 10000)
    mapObj.value.on('complete', () => {
      mapReady.value = true
      window.clearTimeout(fallbackTimer)
    })
    mapObj.value.on('click', (e: any) => {
      const lng = Number(e.lnglat.getLng().toFixed(6))
      const lat = Number(e.lnglat.getLat().toFixed(6))
      setMarker(lng, lat)
      reverseGeocode(lng, lat)
    })
    if (props.longitude && props.latitude) setMarker(props.longitude, props.latitude)
  } catch (e: any) {
    degrade(e?.message || '地图初始化失败')
  }
}

/** 输入地点名称定位（依赖高德 Web 服务；不可用时给出提示，不影响其它定位方式） */
function searchPlace(kw?: string) {
  const q = (kw ?? keyword.value).trim()
  if (!q) return ElMessage.warning('请输入地点名称')
  if (!mapObj.value || !mapReady.value) {
    return ElMessage.warning('地图尚未就绪，请改用「当前位置」或手动填写坐标')
  }
  const AMap = (window as any).AMap
  searching.value = true
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    if (searching.value) {
      searching.value = false
      ElMessage.warning('地点搜索不可用，请点击地图、使用当前位置或手动填写坐标')
    }
  }, 8000)
  AMap.plugin('AMap.PlaceSearch', () => {
    const ps = new AMap.PlaceSearch({ pageSize: 5 })
    ps.search(q, (status: string, result: any) => {
      window.clearTimeout(searchTimer)
      searching.value = false
      const pois = status === 'complete' ? result?.poiList?.pois : null
      if (!pois?.length) {
        ElMessage.warning('未搜索到该地点，可点击地图选点或使用当前位置')
        return
      }
      const poi = pois[0]
      const lng = Number(poi.location.lng.toFixed(6))
      const lat = Number(poi.location.lat.toFixed(6))
      mapObj.value.setCenter([lng, lat])
      setMarker(lng, lat)
      const addr = [poi.cityname, poi.adname, poi.name].filter(Boolean).join('') || poi.name || q
      emitLocation(lng, lat, addr)
      ElMessage.success('已定位到：' + addr)
    })
  })
}

/** 浏览器原生定位：不依赖高德 Key，WGS84 转 GCJ02 后写回 */
function useCurrentLocation() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return ElMessage.warning('浏览器不支持定位，请手动填写坐标')
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      locating.value = false
      const [lng, lat] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude)
      const gcjLng = Number(lng.toFixed(6))
      const gcjLat = Number(lat.toFixed(6))
      if (mapObj.value && mapReady.value) {
        mapObj.value.setCenter([gcjLng, gcjLat])
        setMarker(gcjLng, gcjLat)
      }
      emitLocation(gcjLng, gcjLat)
      ElMessage.success('已使用当前位置，请补充下方的详细地址')
    },
    () => {
      locating.value = false
      ElMessage.warning('定位失败（需 HTTPS 并允许浏览器定位），请手动填写坐标')
    },
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 5 * 60 * 1000 },
  )
}

watch(
  () => [props.longitude, props.latitude],
  ([lng, lat]) => {
    if (lng && lat && mapObj.value && mapReady.value) setMarker(lng as number, lat as number)
  },
)

onMounted(initMap)
onBeforeUnmount(() => {
  window.clearTimeout(fallbackTimer)
  window.clearTimeout(searchTimer)
  if (mapObj.value) {
    try {
      mapObj.value.destroy?.()
    } catch {
      // 忽略卸载时的清理异常
    }
  }
})

defineExpose({ searchPlace })
</script>

<template>
  <div class="map-picker">
    <div class="map-tools">
      <el-input
        v-model="keyword"
        placeholder="输入地点名称，如 武汉大学"
        clearable
        @keyup.enter="searchPlace()"
      />
      <el-button :loading="searching" @click="searchPlace()">搜索定位</el-button>
      <el-button :loading="locating" @click="useCurrentLocation()">
        <el-icon><Aim /></el-icon>
        <span>当前位置</span>
      </el-button>
    </div>

    <div v-if="mapFailed" class="map-fallback">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          <span>地图暂不可用，可用「当前位置」或手动填写 GCJ02 坐标，详细地址在下方表单填写</span>
        </template>
      </el-alert>
      <div class="fallback-form">
        <label class="fallback-field">
          <span>经度（GCJ02，-180 ~ 180，如 114.305）</span>
          <el-input-number
            :model-value="longitude ?? undefined"
            placeholder="经度"
            :precision="6"
            :min="-180"
            :max="180"
            :controls="false"
            @update:model-value="(v: number | undefined) => emitLocation(v ?? null, latitude ?? null, address || '')"
          />
        </label>
        <label class="fallback-field">
          <span>纬度（GCJ02，-90 ~ 90，如 30.593）</span>
          <el-input-number
            :model-value="latitude ?? undefined"
            placeholder="纬度"
            :precision="6"
            :min="-90"
            :max="90"
            :controls="false"
            @update:model-value="(v: number | undefined) => emitLocation(longitude ?? null, v ?? null, address || '')"
          />
        </label>
      </div>
    </div>
    <div v-else ref="mapContainer" class="map-container">
      <div class="map-tip">
        <el-icon><Warning /></el-icon>
        点击地图选择位置
      </div>
    </div>

    <p class="map-coords">
      已选坐标：
      <template v-if="longitude != null && latitude != null">{{ longitude }}, {{ latitude }}（GCJ02）</template>
      <span v-else class="map-none">尚未设置，发布后无法按距离排序</span>
    </p>
    <p v-if="address" class="map-address"><el-icon><Location /></el-icon>{{ address }}</p>
  </div>
</template>

<style scoped>
.map-tools {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.map-tools :deep(.el-input) {
  flex: 1;
  min-width: 200px;
}
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
  z-index: 10;
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
  flex-wrap: wrap;
  margin-top: 12px;
}
.fallback-field {
  display: flex;
  flex: 1;
  min-width: 180px;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--lf-text-2);
}
.fallback-field :deep(.el-input-number) {
  width: 100%;
}
.map-coords {
  margin-top: 8px;
  font-size: 12px;
  color: var(--lf-text-2);
}
.map-none {
  color: var(--lf-danger, #f56c6c);
}
.map-address {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--lf-text-2);
}
</style>
