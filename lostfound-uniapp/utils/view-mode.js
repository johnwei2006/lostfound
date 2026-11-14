import { ref } from 'vue'
import siteStore from './site-store'

/**
 * 小程序身份态：'personal'（普通用户） | 'manager'（站点管理员）。
 * 与 Web stores/user.ts 的 lf_view_mode 语义一致：单 key 存 {userId, mode}，读取校验用户 id，登出清除。
 * 默认 personal —— 未登录、新用户、异常情况下都保持原有视图，天然可回退。
 */
const KEY = 'lf_view_mode'

/** 身份或当前站点变化时广播，页面据此刷新自己的数据 */
export const CONTEXT_CHANGE_EVENT = 'lf:context-changed'
export const viewMode = ref('personal')

const currentUserId = () => {
  const u = uni.getStorageSync('lf_user')
  return u && u.id ? Number(u.id) : null
}

function persist(mode) {
  try {
    uni.setStorageSync(KEY, { userId: currentUserId(), mode })
  } catch (e) {
    // storage 不可用时仅保留内存态
  }
}

export function notifyContextChange() {
  if (typeof uni.$emit === 'function') uni.$emit(CONTEXT_CHANGE_EVENT)
}

/** 从 storage 重读并校验用户 id；站点已加载且无可管理站点时强制回退个人身份 */
export function syncViewMode() {
  const uid = currentUserId()
  let mode = 'personal'
  try {
    const raw = uni.getStorageSync(KEY)
    if (uid !== null && raw && Number(raw.userId) === uid && raw.mode === 'manager') {
      mode = 'manager'
    }
  } catch (e) {
    mode = 'personal'
  }
  if (mode === 'manager' && siteStore.loaded && siteStore.sites.length === 0) {
    mode = 'personal'
    removeStored()
  }
  viewMode.value = mode
  return mode
}

function removeStored() {
  try {
    uni.removeStorageSync(KEY)
  } catch (e) {
    // ignore
  }
}

export function getViewMode() {
  return viewMode.value
}

export function isManagerView() {
  return viewMode.value === 'manager'
}

export function setViewMode(mode) {
  const next = mode === 'manager' ? 'manager' : 'personal'
  if (next === 'manager' && currentUserId() === null) return false
  viewMode.value = next
  if (next === 'manager') persist(next)
  else removeStored()
  notifyContextChange()
  return true
}

export function toggleViewMode() {
  return setViewMode(viewMode.value === 'manager' ? 'personal' : 'manager')
}

/** 退出登录 / 切换账号 */
export function resetViewMode() {
  viewMode.value = 'personal'
  removeStored()
  notifyContextChange()
}
