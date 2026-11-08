import { DEFAULT_LOCATION, LOCATION_KEY } from './config'

/**
 * 当前位置模块：启动时只请求一次定位，结果缓存到 storage。
 * 定位被拒绝或失败时统一回退默认城市，并标记 available=false，
 * 首页据此自动切换为时间排序，地图仍可正常显示。
 */
let locationPromise = null

function isValid(loc) {
  return loc && Number.isFinite(Number(loc.latitude)) && Number.isFinite(Number(loc.longitude))
}

function fallback() {
  return { ...DEFAULT_LOCATION, available: false }
}

function readCache() {
  const cached = uni.getStorageSync(LOCATION_KEY)
  if (!isValid(cached)) return null
  return {
    latitude: Number(cached.latitude),
    longitude: Number(cached.longitude),
    available: cached.available !== false,
  }
}

/**
 * 微信隐私协议兼容：基础库 2.32.3+ 且开启 __usePrivacyCheck__ 时，
 * getLocation 前需先完成隐私授权；未配置隐私协议或用户拒绝时回退默认位置。
 */
function ensurePrivacyAuthorize() {
  return new Promise((resolve) => {
    if (typeof uni.getPrivacySetting !== 'function') return resolve(true)
    uni.getPrivacySetting({
      success: (res) => {
        if (!res.needAuthorization) return resolve(true)
        if (typeof uni.requirePrivacyAuthorize !== 'function') return resolve(false)
        uni.requirePrivacyAuthorize({
          success: () => resolve(true),
          fail: () => resolve(false),
        })
      },
      fail: () => resolve(true), // 无法判断时继续尝试，由 getLocation 自身兜底
    })
  })
}

function requestLocation() {
  return new Promise(async (resolve) => {
    if (!(await ensurePrivacyAuthorize())) {
      console.warn('[location] 隐私授权未完成，回退默认位置')
      return resolve(fallback())
    }
    uni.getLocation({
      type: 'gcj02',
      success: (res) => {
        const loc = { latitude: res.latitude, longitude: res.longitude, available: true }
        uni.setStorageSync(LOCATION_KEY, loc)
        resolve(loc)
      },
      fail: (err) => {
        console.warn('[location] 定位不可用:', err && err.errMsg)
        // 不缓存回退结果，下次冷启动仍有机会重新授权
        resolve(fallback())
      },
    })
  })
}

/** 获取当前位置（同一次启动内复用同一个 Promise） */
export function ensureLocation() {
  if (locationPromise) return locationPromise
  const cached = readCache()
  if (cached) {
    locationPromise = Promise.resolve(cached)
    return locationPromise
  }
  locationPromise = requestLocation()
  return locationPromise
}

/** 重新定位（地图页「重新定位」按钮） */
export function refreshLocation() {
  uni.removeStorageSync(LOCATION_KEY)
  locationPromise = requestLocation()
  return locationPromise
}

/** 同步读取缓存；未定位时返回默认城市且 available=false */
export function getCachedLocation() {
  return readCache() || fallback()
}
