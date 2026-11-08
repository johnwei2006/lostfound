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
        localStorage.removeItem('lf_token')
        localStorage.removeItem('lf_user')
        if (!location.pathname.startsWith('/login')) {
          ElMessage.warning('登录已过期，请重新登录')
          setTimeout(() => (location.href = '/login'), 800)
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
