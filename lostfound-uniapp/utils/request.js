import { BASE_URL } from './config'
import { clearSession, ensureLogin, relogin } from './session'

// 保持原有导出，其他文件无需改动
export { BASE_URL }

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/wx-login']
const isAuthRequest = (url) => AUTH_PATHS.some((p) => url.startsWith(p))

function doRequest(options, retried = false) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('lf_token')
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
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

/** 所有请求先等待同一次静默登录完成，避免 App.onLaunch 与首屏请求竞态 */
const request = (options) => ensureLogin().then(() => doRequest(options))

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
