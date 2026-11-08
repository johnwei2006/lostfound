/**
 * Web 端定位：与小程序 utils/location.js 保持一致的降级策略。
 *
 * 浏览器 Geolocation 返回 WGS84 坐标，而库中物品坐标来自高德（GCJ02），
 * 因此定位成功后换算为 GCJ02，避免产生约 500m 的系统性偏差。
 * 拒绝授权、超时或接口不可用时回退默认城市，由列表页自动切换为时间排序。
 */

export interface WebLocation {
  latitude: number
  longitude: number
  available: boolean
  source: 'browser' | 'cached' | 'default'
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

function readCache(): CachedLocation | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const c = JSON.parse(raw) as CachedLocation
    if (!Number.isFinite(c.latitude) || !Number.isFinite(c.longitude)) return null
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
  return { latitude: DEFAULT_LOCATION.latitude, longitude: DEFAULT_LOCATION.longitude, available: false, source: 'default' }
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

/** 同步读取缓存；无缓存时返回默认城市且 available=false */
export function getCachedLocation(): WebLocation {
  const c = readCache()
  return c
    ? { latitude: c.latitude, longitude: c.longitude, available: true, source: 'cached' }
    : fallback()
}

let pending: Promise<WebLocation> | null = null

/** 并发请求共享同一个 Promise，避免重复弹出定位授权 */
export function ensureLocation(): Promise<WebLocation> {
  const c = readCache()
  if (c) return Promise.resolve({ latitude: c.latitude, longitude: c.longitude, available: true, source: 'cached' })
  if (!pending) {
    pending = requestBrowserLocation().finally(() => {
      pending = null
    })
  }
  return pending
}

/** 「重新定位」：跳过缓存，强制重新请求浏览器定位 */
export function refreshLocation(): Promise<WebLocation> {
  return requestBrowserLocation()
}
