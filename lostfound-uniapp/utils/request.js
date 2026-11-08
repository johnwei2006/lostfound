/**
 * 网络请求封装
 * BASE_URL：线上后端 HTTPS 域名（需在微信公众平台配置为 request/uploadFile 合法域名）
 * 本地调试时可临时改回 http://localhost:8080 并关闭 urlCheck
 */
export const BASE_URL = 'https://www.globalfinding.com'

const request = (options) => {
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
      success: (res) => {
        const body = res.data
        if (body.code !== 0) {
          if (body.code === 401) {
            uni.removeStorageSync('lf_token')
            uni.removeStorageSync('lf_user')
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

/** 上传文件 */
export const uploadFile = (filePath) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('lf_token')
    uni.uploadFile({
      url: BASE_URL + '/api/file/upload',
      filePath,
      name: 'file',
      header: { Authorization: token ? `Bearer ${token}` : '' },
      success: (res) => {
        const body = JSON.parse(res.data)
        if (body.code === 0) {
          resolve(body.data.url)
        } else {
          reject(new Error(body.message))
        }
      },
      fail: reject,
    })
  })
}

export default request
