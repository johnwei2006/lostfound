import { siteApi } from './api'

/**
 * 小程序端「当前站点」状态。仿 Web useSiteStore，但用模块级状态 + storage 持久化。
 * 持久化按 userId 隔离，切换账号后不会读到上一位用户的站点；页面在 onShow 主动重载。
 */
const KEY = 'lf_site_context'
/** 与 utils/view-mode.js 的 CONTEXT_CHANGE_EVENT 保持一致；此处不 import 以避免循环依赖 */
export const CONTEXT_CHANGE_EVENT = 'lf:context-changed'

let state = { sites: [], currentSiteId: null, loaded: false }
/** 站点名条挂在多个页面，模块内 guard 保证同一时刻只有一次 /api/foundsite/mine 请求 */
let loadingPromise = null

function notify() {
  if (typeof uni.$emit === 'function') uni.$emit(CONTEXT_CHANGE_EVENT)
}

/** 仅在已登录且处于管理员身份时才需要预加载站点，避免公开页匿名 401 */
function needLoad() {
  try {
    if (!uni.getStorageSync('lf_token')) return false
    const raw = uni.getStorageSync('lf_view_mode')
    const uid = currentUserId()
    return uid !== null && raw && Number(raw.userId) === uid && raw.mode === 'manager'
  } catch (e) {
    return false
  }
}

const currentUserId = () => {
  const u = uni.getStorageSync('lf_user')
  return u && u.id ? Number(u.id) : null
}

function read() {
  const uid = currentUserId()
  try {
    const raw = uni.getStorageSync(KEY)
    if (raw && Number(raw.userId) === uid) {
      state.sites = Array.isArray(raw.sites) ? raw.sites : []
      state.currentSiteId = raw.currentSiteId ?? null
      state.loaded = true
    } else {
      state = { sites: [], currentSiteId: null, loaded: false }
    }
  } catch (e) {
    state = { sites: [], currentSiteId: null, loaded: false }
  }
}

function persist() {
  try {
    uni.setStorageSync(KEY, {
      userId: currentUserId(),
      sites: state.sites,
      currentSiteId: state.currentSiteId,
    })
  } catch (e) {
    // storage 不可用时仍保留内存态，不影响本次会话
  }
}

export const siteStore = {
  get sites() {
    return state.sites
  },
  get currentSiteId() {
    return state.currentSiteId
  },
  get currentSite() {
    return state.sites.find((s) => Number(s.id) === Number(state.currentSiteId)) || null
  },
  get loaded() {
    return state.loaded
  },
  /** 站点名称；未选择或无权限时返回空串，供各页面标题/提示直接使用 */
  get currentSiteName() {
    const site = this.currentSite
    return site && site.name ? String(site.name) : ''
  },
  /** 返回当前用户是否为当前站点的主管理员 */
  isOwner() {
    const site = this.currentSite
    const uid = currentUserId()
    return !!site && uid !== null && Number(site.ownerUserId) === uid
  },
  /** force=true 用于审核通过/加入新站点后强制刷新 */
  async load(force = false) {
    read()
    if (!force && state.loaded) return state.sites
    if (loadingPromise) return loadingPromise
    loadingPromise = (async () => {
      const sites = await siteApi.mine()
      state.sites = Array.isArray(sites) ? sites : []
      if (!state.sites.some((s) => Number(s.id) === Number(state.currentSiteId))) {
        state.currentSiteId = state.sites.length ? state.sites[0].id : null
      }
      state.loaded = true
      persist()
      notify()
      return state.sites
    })()
    try {
      return await loadingPromise
    } finally {
      loadingPromise = null
    }
  },
  /** 管理员身份下由站点名条 lazy 调用；非管理员身份不发任何请求 */
  async ensureLoaded() {
    if (!needLoad()) return null
    if (state.loaded && !loadingPromise) return this.currentSite
    try {
      await this.load()
    } catch (e) {
      console.warn('[site-store] 加载可管理站点失败', e)
      // 失败也要广播一次，让站点名条从加载态切到降级态
      notify()
    }
    return this.currentSite
  },
  select(id) {
    state.currentSiteId = id
    persist()
    notify()
  },
  /** 退出登录或切换账号时清空，避免跨账号数据串扰 */
  reset() {
    state = { sites: [], currentSiteId: null, loaded: false }
    loadingPromise = null
    try {
      uni.removeStorageSync(KEY)
    } catch (e) {
      // ignore
    }
    notify()
  },
}

export default siteStore
