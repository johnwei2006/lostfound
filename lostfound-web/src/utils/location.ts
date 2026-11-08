import { shallowRef, watch } from 'vue'

/**
 * Web 端当前位置
 *
 * - 浏览器 Geolocation 返回 WGS84，库中物品坐标为高德 GCJ02，
 *   自动定位成功后换算为 GCJ02，避免约 500m 的系统性偏差；手动选点坐标已是 GCJ02，不再换算
 * - 自动定位结果缓存到 localStorage（10 分钟），供刷新后复用
 * - 「地图选点」设置会话级手动位置：只保存在模块内存中，不落盘，
 *   刷新页面或新开会话后自动失效并恢复自动定位
 * - 读取优先级：手动选点 > 自动定位缓存 > 默认城市
 * - currentLocation 是模块级响应式单例，所有页面统一读取并 watch 变化
 */

export interface WebLocation {
  latitude: number
  longitude: number
  available: boolean
  source: 'browser' | 'cached' | 'default' | 'manual'
  address?: string
}

export const DEFAULT_CITY = '武汉'
export const DEFAULT_LOCATION = { latitude: 30.593, longitude: 114.305 } as const

const STORAGE_KEY = 'lf_web_location'
const CACHE_TTL = 10 * 60 * 1000
const GEO_TIMEOUT = 8000

interface CachedLocation {
  latitude: number
  longitude: number
  ts: number
}

function isValidCoordinates(lat: number, lng: number) {
  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180
  )
}

function readCache(): CachedLocation | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const c = JSON.parse(raw) as CachedLocation
    if (!isValidCoordinates(Number(c.latitude), Number(c.longitude))) return null
    if (Date.now() - c.ts > CACHE_TTL) return null
    return c
  } catch {
    return null
  }
}

function writeCache(latitude: number, longitude: number) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ latitude, longitude, ts: Date.now() }))
  } catch {
    // 隐私模式或禁用存储时忽略，定位结果仍在本次会话内可用
  }
}

const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lng: number, lat: number) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x: number, y: number) {
  let ret = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2) / 3
  ret += ((20 * Math.sin(y * PI) + 40 * Math.sin((y / 3) * PI)) * 2) / 3
  ret += ((160 * Math.sin((y / 12) * PI) + 320 * Math.sin((y * PI) / 30)) * 2) / 3
  return ret
}

function transformLng(x: number, y: number) {
  let ret = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2) / 3
  ret += ((20 * Math.sin(x * PI) + 40 * Math.sin((x / 3) * PI)) * 2) / 3
  ret += ((150 * Math.sin((x / 12) * PI) + 300 * Math.sin((x / 30) * PI)) * 2) / 3
  return ret
}

/** WGS84 → GCJ02，境外坐标原样返回 */
export function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105, lat - 35)
  let dLng = transformLng(lng - 105, lat - 35)
  const radLat = (lat / 180) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  return [lng + dLng, lat + dLat]
}

function fallback(): WebLocation {
  return {
    latitude: DEFAULT_LOCATION.latitude,
    longitude: DEFAULT_LOCATION.longitude,
    available: false,
    source: 'default',
  }
}

function autoLocation(): WebLocation {
  const c = readCache()
  return c
    ? { latitude: c.latitude, longitude: c.longitude, available: true, source: 'cached' }
    : fallback()
}

/** 当前生效位置的响应式单例（只读使用，修改请走 setManualLocation / clearManualLocation） */
export const currentLocation = shallowRef<WebLocation>(autoLocation())

/** 会话级手动覆盖的权威状态，与展示用的 currentLocation 分离，避免自动定位结果反向覆盖 */
let manualOverride: WebLocation | null = null
let pending: Promise<WebLocation> | null = null

/** 自动定位结果仅在无手动覆盖时发布；force 用于定位失败时强制通知页面降级 */
function publish(loc: WebLocation, force = false) {
  if (manualOverride) return
  if (!force) {
    const prev = currentLocation.value
    if (
      prev.latitude === loc.latitude &&
      prev.longitude === loc.longitude &&
      prev.available === loc.available &&
      prev.source === loc.source
    ) {
      return
    }
  }
  currentLocation.value = { ...loc }
}

/** 同步读取当前生效位置：手动选点 > 自动定位缓存 > 默认城市 */
export function getCurrentLocation(): WebLocation {
  return currentLocation.value
}

/** 设置会话级手动位置（GCJ02 坐标），不写入 localStorage */
export function setManualLocation(loc: {
  latitude: number
  longitude: number
  address?: string
}): boolean {
  const latitude = Number(loc.latitude)
  const longitude = Number(loc.longitude)
  if (!isValidCoordinates(latitude, longitude)) return false
  manualOverride = {
    latitude,
    longitude,
    address: loc.address || '',
    available: true,
    source: 'manual',
  }
  currentLocation.value = { ...manualOverride }
  return true
}

/** 清除手动位置，回到自动定位结果 */
export function clearManualLocation(): WebLocation {
  manualOverride = null
  const loc = autoLocation()
  currentLocation.value = { ...loc }
  return loc
}

/** 订阅位置变化，返回取消监听函数 */
export function subscribeLocation(cb: (loc: WebLocation) => void) {
  return watch(currentLocation, (loc) => cb(loc))
}

function requestBrowserLocation(): Promise<WebLocation> {
  return new Promise((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      resolve(fallback())
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const [longitude, latitude] = wgs84ToGcj02(pos.coords.longitude, pos.coords.latitude)
        writeCache(latitude, longitude)
        resolve({ latitude, longitude, available: true, source: 'browser' })
      },
      () => resolve(fallback()),
      { enableHighAccuracy: false, timeout: GEO_TIMEOUT, maximumAge: 5 * 60 * 1000 },
    )
  })
}

/** 确保自动定位完成；已有手动位置时直接返回，不打扰用户 */
export function ensureLocation(): Promise<WebLocation> {
  if (manualOverride) return Promise.resolve(getCurrentLocation())
  const c = readCache()
  if (c) {
    publish({ latitude: c.latitude, longitude: c.longitude, available: true, source: 'cached' })
    return Promise.resolve(getCurrentLocation())
  }
  if (!pending) {
    pending = requestBrowserLocation()
      .then((loc) => {
        publish(loc, !loc.available)
        return getCurrentLocation()
      })
      .finally(() => {
        pending = null
      })
  }
  return pending
}

/** 恢复自动定位：清手动覆盖 + 清自动缓存 + 重新定位，只广播一次结果 */
export function refreshLocation(): Promise<WebLocation> {
  manualOverride = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 隐私模式下忽略
  }
  pending = requestBrowserLocation()
    .then((loc) => {
      // 定位期间用户可能又手动选了点，此时不得用自动结果覆盖手动覆盖
      if (manualOverride) return getCurrentLocation()
      currentLocation.value = { ...loc }
      return getCurrentLocation()
    })
    .finally(() => {
      pending = null
    })
  return pending
}

/** 兼容旧调用：返回当前生效位置（含手动覆盖） */
export function getCachedLocation(): WebLocation {
  return getCurrentLocation()
}
