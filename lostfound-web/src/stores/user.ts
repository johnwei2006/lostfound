import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { MeVO, User } from '@/types'

type SessionUser = User & Partial<Pick<MeVO, 'managedSiteIds' | 'siteManager'>>

/** 站点管理员的视图身份：仅影响菜单显示，不改变真实权限 */
export type ViewMode = 'manager' | 'personal'
const VIEW_MODE_KEY = 'lf_view_mode'

/** 单 key 存 { userId, mode }，读取时校验用户 id，避免换账号后读到上一位用户的选择 */
function readViewMode(userId?: number): ViewMode | null {
  try {
    const raw = localStorage.getItem(VIEW_MODE_KEY)
    if (!raw) return null
    const saved = JSON.parse(raw) as { userId?: number; mode?: string }
    if (saved.userId !== userId) return null
    return saved.mode === 'manager' || saved.mode === 'personal' ? saved.mode : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lf_token') || '',
    user: JSON.parse(localStorage.getItem('lf_user') || 'null') as SessionUser | null,
    /** /api/auth/me 是否已刷新过站点管理范围（登录接口只返回基础 User） */
    siteLoaded: false,
    /** 站点管理员选择的视图身份；null 表示尚未选择（需弹窗） */
    viewMode: null as ViewMode | null,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN',
    managedSiteIds: (s) => s.user?.managedSiteIds ?? [],
    isSiteManager: (s) =>
      s.user?.role === 'ADMIN' ||
      s.user?.siteManager === true ||
      (s.user?.managedSiteIds?.length ?? 0) > 0,
    canManageSite: (s) => (siteId: number) =>
      s.user?.role === 'ADMIN' || (s.user?.managedSiteIds ?? []).includes(siteId),
    /** 管理员身份视图：仅站点管理员（非 ADMIN）且已选择管理员身份。
     *  Pinia getter 参数是 state，不能引用其它 getter，故直接按 state 判断 */
    isManagerView: (s) =>
      s.viewMode === 'manager' &&
      s.user?.role !== 'ADMIN' &&
      (s.user?.siteManager === true || (s.user?.managedSiteIds?.length ?? 0) > 0),
    /** 是否需要在登录后弹身份选择：必须等 /me 拿到站点范围后再判断 */
    needsIdentityChoice: (s) =>
      !!s.token &&
      s.siteLoaded &&
      s.viewMode === null &&
      s.user?.role !== 'ADMIN' &&
      (s.user?.siteManager === true || (s.user?.managedSiteIds?.length ?? 0) > 0),
  },
  actions: {
    /** 读取当前用户的身份选择（按 user.id 隔离） */
    initViewMode() {
      this.viewMode = readViewMode(this.user?.id)
    },
    /** 选择/切换身份并持久化；仅影响菜单显示，不改后端权限 */
    setViewMode(mode: ViewMode) {
      this.viewMode = mode
      localStorage.setItem(VIEW_MODE_KEY, JSON.stringify({ userId: this.user?.id, mode }))
    },
    /** 账号登录与微信扫码登录共用的会话写入口：登录后立即补齐站点管理范围 */
    async setSession(data: { token: string; user: SessionUser }) {
      this.token = data.token
      this.user = data.user
      this.siteLoaded = false
      localStorage.setItem('lf_token', data.token)
      localStorage.setItem('lf_user', JSON.stringify(data.user))
      this.initViewMode()
      // 登录/扫码接口只返回基础 User，必须再取一次 /me 才能显示「招领站管理」入口
      await this.refresh({ logoutOnError: false })
    },
    async login(username: string, password: string) {
      await this.setSession(await authApi.login({ username, password }))
    },
    async register(form: { username: string; password: string; nickname: string; phone?: string }) {
      await this.setSession(await authApi.register(form))
    },
    async refresh(options: { logoutOnError?: boolean } = {}) {
      if (!this.token) return
      const logoutOnError = options.logoutOnError !== false
      try {
        this.user = await authApi.me()
        localStorage.setItem('lf_user', JSON.stringify(this.user))
        this.siteLoaded = true
        this.initViewMode()
      } catch {
        // 新登录刚写完 token，偶发失败不应把用户踢下线；页面启动时仍按过期处理
        if (logoutOnError) this.logout()
      }
    },
    logout() {
      this.token = ''
      this.user = null
      this.siteLoaded = false
      this.viewMode = null
      localStorage.removeItem('lf_token')
      localStorage.removeItem('lf_user')
      localStorage.removeItem(VIEW_MODE_KEY)
    },
  },
})
