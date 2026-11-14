import { siteApi } from './api'

/**
 * 小程序端「当前站点」状态。仿 Web useSiteStore，但用模块级状态 + storage 持久化。
 * 持久化按 userId 隔离，切换账号后不会读到上一位用户的站点；页面在 onShow 主动重载。
 */
const KEY = 'lf_site_context'

let state = { sites: [], currentSiteId: null, loaded: false }

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
    const sites = await siteApi.mine()
    state.sites = Array.isArray(sites) ? sites : []
    if (!state.sites.some((s) => Number(s.id) === Number(state.currentSiteId))) {
      state.currentSiteId = state.sites.length ? state.sites[0].id : null
    }
    state.loaded = true
    persist()
    return state.sites
  },
  select(id) {
    state.currentSiteId = id
    persist()
  },
  /** 退出登录或切换账号时清空，避免跨账号数据串扰 */
  reset() {
    state = { sites: [], currentSiteId: null, loaded: false }
    try {
      uni.removeStorageSync(KEY)
    } catch (e) {
      // ignore
    }
  },
}

export default siteStore
