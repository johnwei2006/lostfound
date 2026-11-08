/**
 * 网络请求封装
 * BASE_URL：开发环境指向本地后端；小程序真机调试需改为局域网 IP 或线上域名
 */
export const BASE_URL = 'http://localhost:8080'

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
