import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { MeVO, User } from '@/types'

type SessionUser = User & Partial<Pick<MeVO, 'managedSiteIds' | 'siteManager'>>

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lf_token') || '',
    user: JSON.parse(localStorage.getItem('lf_user') || 'null') as SessionUser | null,
    /** /api/auth/me 是否已刷新过站点管理范围（登录接口只返回基础 User） */
    siteLoaded: false,
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
  },
  actions: {
    /** 账号登录与微信扫码登录共用的会话写入口：登录后立即补齐站点管理范围 */
    async setSession(data: { token: string; user: SessionUser }) {
      this.token = data.token
      this.user = data.user
      this.siteLoaded = false
      localStorage.setItem('lf_token', data.token)
      localStorage.setItem('lf_user', JSON.stringify(data.user))
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
      } catch {
        // 新登录刚写完 token，偶发失败不应把用户踢下线；页面启动时仍按过期处理
        if (logoutOnError) this.logout()
      }
    },
    logout() {
      this.token = ''
      this.user = null
      this.siteLoaded = false
      localStorage.removeItem('lf_token')
      localStorage.removeItem('lf_user')
    },
  },
})
