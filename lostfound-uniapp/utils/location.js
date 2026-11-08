import { DEFAULT_LOCATION, LOCATION_KEY } from './config'

/**
 * 当前位置模块
 *
 * - 启动时自动定位一次，自动定位结果缓存到 storage（下次冷启动可复用）
 * - 「地图选点」设置会话级手动位置：只保存在模块内存中，不落盘，
 *   冷启动后自动失效并恢复为自动定位
 * - 读取优先级：手动选点 > 自动定位缓存 > 默认城市
 * - 任何位置变化通过 uni 全局事件广播，页面订阅后重置分页并刷新列表
 */
const CHANGE_EVENT = 'lf:location-change'
let locationPromise = null
let manualOverride = null

function isValid(loc) {
  if (!loc) return false
  const lat = Number(loc.latitude)
  const lng = Number(loc.longitude)
  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= -90 &&
    lat <= 90 &&
    lng >= -180 &&
    lng <= 180
  )
}

function fallback() {
  return { ...DEFAULT_LOCATION, available: false, source: 'default' }
}

function readCache() {
  const cached = uni.getStorageSync(LOCATION_KEY)
  if (!isValid(cached)) return null
  return {
    latitude: Number(cached.latitude),
    longitude: Number(cached.longitude),
    available: cached.available !== false,
    source: 'auto',
  }
}

function autoLocation() {
  return readCache() || fallback()
}

function emitChange(loc) {
  // 部分基础库或非页面上下文可能没有全局事件总线，降级为仅更新模块状态
  if (typeof uni.$emit === 'function') uni.$emit(CHANGE_EVENT, loc)
}

/** 同步读取当前生效位置：手动选点 > 自动定位缓存 > 默认城市 */
export function getCurrentLocation() {
  return manualOverride ? { ...manualOverride } : autoLocation()
}

/** 设置会话级手动位置（地图选点），坐标范围校验通过后广播；不写入 storage */
export function setManualLocation(loc) {
  if (!isValid(loc)) return false
  manualOverride = {
    latitude: Number(loc.latitude),
    longitude: Number(loc.longitude),
    address: loc.address || '',
    available: true,
    source: 'manual',
  }
  emitChange({ ...manualOverride })
  return true
}

/** 清除手动位置，回到自动定位结果；silent 用于合并 refreshLocation 的广播 */
export function clearManualLocation(options) {
  manualOverride = null
  const loc = autoLocation()
  if (!options || !options.silent) emitChange({ ...loc })
  return loc
}

/** 订阅位置变化，返回取消订阅函数（页面卸载时必须调用） */
export function subscribeLocation(cb) {
  const handler = (loc) => cb(loc || getCurrentLocation())
  if (typeof uni.$on === 'function') uni.$on(CHANGE_EVENT, handler)
  return () => {
    if (typeof uni.$off === 'function') uni.$off(CHANGE_EVENT, handler)
  }
}

/**
 * 微信隐私协议兼容：基础库 2.32.3+ 且开启 __usePrivacyCheck__ 时，
 * 地理位置接口调用前需先完成隐私授权；未配置隐私协议或用户拒绝时回退。
 * 导出供 uni.chooseLocation 复用。
 */
export function ensurePrivacyAuthorize() {
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
      fail: () => resolve(true), // 无法判断时继续尝试，由接口自身兜底
    })
  })
}

function requestLocation() {
  return new Promise(async (resolve) => {
    if (manualOverride) return resolve({ ...manualOverride })
    if (!(await ensurePrivacyAuthorize())) {
      console.warn('[location] 隐私授权未完成，回退默认位置')
      const loc = fallback()
      emitChange({ ...loc })
      return resolve(loc)
    }
    uni.getLocation({
      type: 'gcj02',
      success: (res) => {
        const value = { latitude: res.latitude, longitude: res.longitude, available: true }
        uni.setStorageSync(LOCATION_KEY, value)
        const loc = { ...value, source: 'auto' }
        if (!manualOverride) emitChange({ ...loc })
        resolve(manualOverride ? { ...manualOverride } : loc)
      },
      fail: (err) => {
        console.warn('[location] 定位不可用:', err && err.errMsg)
        // 不缓存回退结果，下次冷启动仍有机会重新授权
        const loc = fallback()
        if (!manualOverride) emitChange({ ...loc })
        resolve(manualOverride ? { ...manualOverride } : loc)
      },
    })
  })
}

/** 获取当前位置（同一次启动内复用同一个 Promise；手动位置优先） */
export function ensureLocation() {
  if (manualOverride) return Promise.resolve(getCurrentLocation())
  if (locationPromise) return locationPromise
  const cached = readCache()
  if (cached) {
    locationPromise = Promise.resolve(cached)
    return locationPromise
  }
  locationPromise = requestLocation()
  return locationPromise
}

/** 恢复自动定位：清手动覆盖 + 清自动缓存 + 重新定位，只广播一次结果 */
export function refreshLocation() {
  clearManualLocation({ silent: true })
  uni.removeStorageSync(LOCATION_KEY)
  locationPromise = requestLocation()
  return locationPromise
}

/** 兼容旧调用：返回当前生效位置（含手动覆盖） */
export function getCachedLocation() {
  return getCurrentLocation()
}
