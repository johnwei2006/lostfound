import { BASE_URL } from './config'

/**
 * 微信静默登录会话：App.onLaunch 与所有请求共用同一个 Promise，
 * 避免首屏并发触发多次 wx.login 或多次自动建号。
 * 本模块使用原生 uni.request，不依赖 request.js（后者会反向依赖本模块）。
 */
const TOKEN_KEY = 'lf_token'
const USER_KEY = 'lf_user'
const RETRY_INTERVAL = 30 * 1000

let loginPromise = null
let reloginPromise = null
let lastFailAt = 0

function readSession() {
  const token = uni.getStorageSync(TOKEN_KEY)
  const user = uni.getStorageSync(USER_KEY)
  return token && user ? { token, user } : null
}

export function getCachedSession() {
  return readSession()
}

/** 账号密码登录成功后写入会话，并让后续请求沿用该会话 */
export function setSession(data) {
  if (!data || !data.token) return null
  uni.setStorageSync(TOKEN_KEY, data.token)
  uni.setStorageSync(USER_KEY, data.user || null)
  const session = { token: data.token, user: data.user }
  loginPromise = Promise.resolve(session)
  return session
}

export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
  loginPromise = null
}

function silentLogin() {
  return new Promise((resolve) => {
    uni.login({
      provider: 'weixin',
      success: ({ code }) => {
        uni.request({
          url: BASE_URL + '/api/auth/wx-login',
          method: 'POST',
          data: { code },
          header: { 'Content-Type': 'application/json' },
          success: (res) => {
            const body = res.data || {}
            if (body.code === 0 && body.data && body.data.token) {
              resolve(setSession(body.data))
            } else {
              console.warn('[session] 微信登录未成功:', body.code, body.message)
              resolve(null)
            }
          },
          fail: (err) => {
            console.warn('[session] 微信登录请求失败:', err && err.errMsg)
            resolve(null)
          },
        })
      },
      fail: (err) => {
        console.warn('[session] wx.login 调用失败:', err && err.errMsg)
        resolve(null)
      },
    })
  })
}

/** 确保已登录：已有会话直接返回，否则静默 wx.login 换 openid 自动建号/登录 */
export function ensureLogin() {
  if (loginPromise) return loginPromise
  const cached = readSession()
  if (cached) {
    loginPromise = Promise.resolve(cached)
    return loginPromise
  }
  // 失败后短时间内不重复发起 wx.login，避免每个请求都触发一次
  if (Date.now() - lastFailAt < RETRY_INTERVAL) return Promise.resolve(null)
  loginPromise = silentLogin().then((session) => {
    if (!session) {
      lastFailAt = Date.now()
      loginPromise = null
    }
    return session
  })
  return loginPromise
}

/** 401 时强制重新登录（去重），仍为静默登录，不打断用户浏览 */
export function relogin() {
  if (reloginPromise) return reloginPromise
  clearSession()
  lastFailAt = 0
  reloginPromise = ensureLogin().finally(() => {
    reloginPromise = null
  })
  return reloginPromise
}
