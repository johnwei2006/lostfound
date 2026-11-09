import { defineStore } from 'pinia'
import { siteApi } from '@/api'
import type { FoundSite } from '@/types'

/** 站点管理后台共享的站点切换状态，三个页面共用一次加载结果 */
export const useSiteStore = defineStore('site', {
  state: () => ({
    sites: [] as FoundSite[],
    currentSiteId: null as number | null,
    loading: false,
    error: '',
  }),
  getters: {
    currentSite: (s): FoundSite | null =>
      s.sites.find((site) => site.id === s.currentSiteId) ?? null,
  },
  actions: {
    async load(force = false) {
      if (this.loading) return
      if (!force && this.sites.length) return
      this.loading = true
      this.error = ''
      try {
        this.sites = await siteApi.mine()
        if (!this.sites.some((site) => site.id === this.currentSiteId)) {
          this.currentSiteId = this.sites[0]?.id ?? null
        }
      } catch (e) {
        this.error = e instanceof Error ? e.message : '招领站加载失败'
      } finally {
        this.loading = false
      }
    },
    select(id: number) {
      this.currentSiteId = id
    },
  },
})
