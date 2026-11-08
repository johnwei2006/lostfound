import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { User } from '@/types'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('lf_token') || '',
    user: JSON.parse(localStorage.getItem('lf_user') || 'null') as User | null,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN',
  },
  actions: {
    async login(username: string, password: string) {
      const data = await authApi.login({ username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem('lf_token', data.token)
      localStorage.setItem('lf_user', JSON.stringify(data.user))
    },
    async register(form: { username: string; password: string; nickname: string; phone?: string }) {
      const data = await authApi.register(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('lf_token', data.token)
      localStorage.setItem('lf_user', JSON.stringify(data.user))
    },
    async refresh() {
      if (!this.token) return
      try {
        this.user = await authApi.me()
        localStorage.setItem('lf_user', JSON.stringify(this.user))
      } catch {
        this.logout()
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('lf_token')
      localStorage.removeItem('lf_user')
    },
  },
})
