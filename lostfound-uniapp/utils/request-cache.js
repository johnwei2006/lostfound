/**
 * 页面级短 TTL 请求去重：同一 key 在 ttlMs 内只放行一次。
 *
 * 用于 onShow / 定位订阅 / tab 切换等触发时机叠加造成的重复加载。
 * 只做去重，不缓存响应数据；下拉刷新等显式操作调用 reset() 强制放行。
 *
 * @param {number} ttlMs 去重窗口，默认 1500ms
 */
export function createDedupeGuard(ttlMs = 1500) {
  const last = new Map()
  return {
    run(key, task) {
      const now = Date.now()
      const prev = last.get(key)
      if (prev !== undefined && now - prev < ttlMs) return null
      last.set(key, now)
      return task()
    },
    reset() {
      last.clear()
    },
  }
}

export default { createDedupeGuard }
