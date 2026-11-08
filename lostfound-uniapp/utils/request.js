import { BASE_URL } from './config'
import { clearSession, ensureLogin, relogin } from './session'

// 保持原有导出，其他文件无需改动
export { BASE_URL }

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/wx-login']
const isAuthRequest = (url) => AUTH_PATHS.some((p) => url.startsWith(p))

/**
 * 微信小程序的 wx.request 会把 data 中的 undefined 序列化成字符串 "undefined"，
 * 后端会把它当成真实筛选值（如 keyword=undefined），从而过滤出空列表。
 * 统一剔除未设置的可选参数，保证 GET 查询串干净。
 */
function normalizeData(data) {
  if (Object.prototype.toString.call(data) !== '[object Object]') return data
  const cleaned = {}
  Object.keys(data).forEach((key) => {
    if (data[key] !== undefined) cleaned[key] = data[key]
  })
  return cleaned
}

function doRequest(options, retried = false) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('lf_token')
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: normalizeData(options.data),
      header: {
        Authorization: token ? `Bearer ${token}` : '',
        ...(options.header || {}),
      },
      success: async (res) => {
        const body = res.data || {}
        if (body.code !== 0) {
          // 令牌过期：先静默重登并重试一次，不再直接打断用户跳登录页
          if (body.code === 401 && !retried && !isAuthRequest(options.url)) {
            const session = await relogin()
            if (session) {
              try {
                resolve(await doRequest(options, true))
                return
              } catch (e) {
                return reject(e)
              }
            }
          }
          if (body.code === 401) {
            clearSession()
            uni.showToast({ title: '请先登录', icon: 'none' })
            setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 600)
          }
          return reject(new Error(body.message || '请求失败'))
        }
        resolve(body.data)
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请检查后端服务', icon: 'none' })
        reject(err)
      },
    })
  })
}

/**
 * 列表/地图等公开接口后端已支持匿名访问，不必等待冷启动的静默登录：
 * 否则 wx.login 慢或失败时，首页首屏请求会一直挂在登录 Promise 上，表现为"初次加载没数据"。
 * 需要登录的接口仍等待同一次登录；即便误判，401 时 doRequest 也会静默重登并重试一次。
 */
const PUBLIC_GET = /^\/api\/(found|lost)\/(page|latest|map)$|^\/api\/foundsite\/page$/
const isPublicGet = (options) =>
  (options.method || 'GET').toUpperCase() === 'GET' && PUBLIC_GET.test(options.url.split('?')[0])

const request = (options) =>
  isPublicGet(options) ? doRequest(options) : ensureLogin().then(() => doRequest(options))

function doUpload(filePath, retried = false) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('lf_token')
    uni.uploadFile({
      url: BASE_URL + '/api/file/upload',
      filePath,
      name: 'file',
      header: { Authorization: token ? `Bearer ${token}` : '' },
      success: async (res) => {
        let body
        try {
          body = JSON.parse(res.data)
        } catch {
          return reject(new Error('上传响应解析失败'))
        }
        if (body.code === 0) return resolve(body.data.url)
        if (body.code === 401 && !retried) {
          const session = await relogin()
          if (session) {
            try {
              resolve(await doUpload(filePath, true))
              return
            } catch (e) {
              return reject(e)
            }
          }
        }
        reject(new Error(body.message || '上传失败'))
      },
      fail: reject,
    })
  })
}

/** 上传文件：同样等待静默登录，401 静默重登重试一次 */
export const uploadFile = (filePath) => ensureLogin().then(() => doUpload(filePath))

export default request
