import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 统一响应体 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: '/',
  timeout: 15000,
})

/** 同一批 401 只提示一次，避免并发请求刷屏打断匿名浏览 */
let lastAuthWarnAt = 0

/**
 * 相同 GET（url + params）只保留最后一个在飞请求：
 * 定位回调、watch immediate、路由变化常在同一瞬间触发多次完全相同的列表请求，
 * 旧请求直接 abort，既省带宽也避免后发先至覆盖新结果。
 */
const inflight = new Map<string, AbortController>()

type GuardedConfig = InternalAxiosRequestConfig & {
  __lfKey?: string
  __lfController?: AbortController
}

const dedupeKey = (config: InternalAxiosRequestConfig): string | null => {
  if ((config.method || 'get').toLowerCase() !== 'get') return null
  try {
    return `${config.url}|${JSON.stringify(config.params ?? {})}`
  } catch {
    return null
  }
}

const release = (config: GuardedConfig | undefined) => {
  if (!config || !config.__lfKey) return
  if (inflight.get(config.__lfKey) === config.__lfController) {
    inflight.delete(config.__lfKey)
  }
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('lf_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const key = dedupeKey(config)
  if (key) {
    const prev = inflight.get(key)
    if (prev) prev.abort()
    const controller = new AbortController()
    const guarded = config as GuardedConfig
    guarded.signal = controller.signal
    guarded.__lfKey = key
    guarded.__lfController = controller
    inflight.set(key, controller)
  }
  return config
})

request.interceptors.response.use(
  (resp): any => {
    release(resp.config as GuardedConfig)
    const body = resp.data as ApiResult
    if (body.code !== 0) {
      if (body.code === 401) {
        // 匿名浏览优先：只静默清除过期登录态，由 App.vue 同步 Pinia，不整页跳登录
        localStorage.removeItem('lf_token')
        localStorage.removeItem('lf_user')
        window.dispatchEvent(new CustomEvent('lf:auth-expired'))
        const now = Date.now()
        if (now - lastAuthWarnAt > 5000) {
          lastAuthWarnAt = now
          ElMessage.warning('登录已过期，可继续浏览，需要时再登录')
        }
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    release(error.config as GuardedConfig)
    // 被去重机制取消的请求静默悬挂，由同 key 的新请求负责写入页面数据，
    // 不弹错误提示、不执行旧 load 的赋值，避免竞态覆盖。
    if (axios.isCancel(error)) return new Promise(() => {})
    ElMessage.error(error.message === 'Network Error' ? '网络异常，请检查后端服务' : error.message)
    return Promise.reject(error)
  },
)

export default request
