/**
 * 全局配置：抽离出来供 request.js / session.js 共用，避免循环依赖。
 * 线上后端 HTTPS 域名需在微信公众平台配置为 request/uploadFile 合法域名。
 */
export const BASE_URL = 'https://www.globalfinding.com'

/** 定位不可用时的默认中心（武汉），以及存储键 */
export const DEFAULT_LOCATION = { latitude: 30.593, longitude: 114.305 }
export const LOCATION_KEY = 'lf_location'
