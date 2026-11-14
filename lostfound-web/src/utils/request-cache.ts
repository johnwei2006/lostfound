/**
 * 轻量请求去重：同一份规范化查询条件在极短 TTL 内只触发一次加载。
 *
 * 用于合并「onMounted + 定位 watch」「路由 watch + 首屏」等挂载瞬间的重复请求。
 * 只做去重，不缓存响应数据，页面内容始终来自最新一次真实请求。
 */
export interface DedupeGuard {
  /** 返回 true 表示本次调用与最近一次相同条件重复，调用方应直接跳过 */
  shouldSkip(key: string): boolean
  /** 筛选条件变化时强制放行下一次请求 */
  reset(): void
}

export function createDedupeGuard(ttlMs = 600): DedupeGuard {
  let lastKey = ''
  let lastAt = 0
  let forced = false

  return {
    shouldSkip(key: string): boolean {
      const now = Date.now()
      if (!forced && key === lastKey && now - lastAt < ttlMs) return true
      lastKey = key
      lastAt = now
      forced = false
      return false
    },
    reset(): void {
      forced = true
    },
  }
}
