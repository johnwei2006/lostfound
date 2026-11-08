import axios from 'axios'
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

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('lf_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (resp): any => {
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
    ElMessage.error(error.message === 'Network Error' ? '网络异常，请检查后端服务' : error.message)
    return Promise.reject(error)
  },
)

export default request
