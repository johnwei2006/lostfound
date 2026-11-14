import { onMounted, onUnmounted, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import siteStore, { CONTEXT_CHANGE_EVENT } from '@/utils/site-store'
import { syncViewMode } from '@/utils/view-mode'
import { createDedupeGuard } from '@/utils/request-cache'

const SIZE = 20

/**
 * 站点管理列表通用逻辑：当前站点上下文、分页、身份/站点切换后强制刷新、在飞请求竞态防护。
 * @param {(siteId: number, page: number, size: number) => Promise<{records: Array, total: number}>} fetcher
 * @param {{ ttl?: number }} options
 */
export function useSitePagedList(fetcher, options = {}) {
  const guard = createDedupeGuard(options.ttl ?? 800)
  const siteId = ref(null)
  const siteName = ref('')
  const items = ref([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  const noMore = ref(false)
  const error = ref('')
  let seq = 0

  const syncContext = () => {
    siteId.value = siteStore.currentSiteId
    siteName.value = siteStore.currentSiteName
  }

  const load = async (reset = false, dedupeKey = 'list') => {
    if (!siteId.value) syncContext()
    if (!siteId.value) {
      items.value = []
      total.value = 0
      noMore.value = true
      return
    }
    // 同一上下文下的重复 onShow / 事件叠加只放行一次；先判定再清空，避免被去重后留下空列表
    const key = `${siteId.value}|${dedupeKey}|${reset ? 1 : page.value}`
    if (reset && guard.run(key, () => true) === null) return
    if (reset) {
      page.value = 1
      items.value = []
      noMore.value = false
    }
    await doLoad()
  }

  const doLoad = async () => {
    const mySeq = ++seq
    loading.value = true
    error.value = ''
    try {
      const data = await fetcher(siteId.value, page.value, SIZE)
      // 站点或身份已切换：丢弃过期响应，避免串数据
      if (mySeq !== seq) return
      const records = (data && data.records) || []
      items.value = page.value === 1 ? records : items.value.concat(records)
      total.value = Number((data && data.total) || 0)
      noMore.value = items.value.length >= total.value
    } catch (e) {
      if (mySeq !== seq) return
      error.value = (e && e.message) || '加载失败，请重试'
    } finally {
      if (mySeq === seq) {
        loading.value = false
        uni.stopPullDownRefresh()
      }
    }
  }

  const reload = () => {
    guard.reset()
    load(true)
  }
  const more = () => {
    if (loading.value || noMore.value || !siteId.value) return
    page.value += 1
    load(false)
  }
  const onContextChange = () => {
    syncViewMode()
    const previous = siteId.value
    syncContext()
    // 当前站点变了才重新拉取，单纯身份广播不制造额外请求
    if (Number(previous) !== Number(siteId.value)) reload()
  }

  onShow(async () => {
    syncViewMode()
    // 管理页也可能从「我的」直接跳转（此时未必是管理员身份），仍需站点上下文
    if (!siteStore.loaded) {
      try {
        await siteStore.load()
      } catch (e) {
        error.value = (e && e.message) || '站点信息加载失败'
      }
    }
    syncContext()
    load(true)
  })
  onPullDownRefresh(() => reload())
  onReachBottom(more)
  onMounted(() => {
    if (typeof uni.$on === 'function') uni.$on(CONTEXT_CHANGE_EVENT, onContextChange)
  })
  onUnmounted(() => {
    if (typeof uni.$off === 'function') uni.$off(CONTEXT_CHANGE_EVENT, onContextChange)
  })

  return { siteId, siteName, items, total, page, loading, noMore, error, load, reload, more, SIZE }
}
