/**
 * 招领站距离计算（前端本地，坐标均为 gcj02，无需换算）。
 * 线上站点规模很小（size:50 一次取全量），本地排序即可，避免改动公开分页接口契约。
 */
const EARTH_RADIUS_KM = 6371
const toRad = (deg) => (deg * Math.PI) / 180

function validPoint(lat, lng) {
  // Number(null) === 0、Number('') === 0，缺失坐标会被误判为合法点，必须先显式排除
  if (lat === null || lat === undefined || lat === '' ||
      lng === null || lng === undefined || lng === '') return false
  const a = Number(lat)
  const b = Number(lng)
  return (
    Number.isFinite(a) && Number.isFinite(b) &&
    a >= -90 && a <= 90 && b >= -180 && b <= 180 &&
    !(a === 0 && b === 0)
  )
}

/** 两点球面距离（公里）；坐标无效返回 null */
export function distanceKm(lat1, lng1, lat2, lng2) {
  if (!validPoint(lat1, lng1) || !validPoint(lat2, lng2)) return null
  const dLat = toRad(Number(lat2) - Number(lat1))
  const dLng = toRad(Number(lng2) - Number(lng1))
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(Number(lat1))) * Math.cos(toRad(Number(lat2))) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)))
}

/** 格式化展示：小于 1km 用米，其余保留 1 位小数 */
export function formatDistance(km) {
  if (km === null || km === undefined || !Number.isFinite(Number(km))) return ''
  const v = Number(km)
  return v < 1 ? `距您 ${Math.round(v * 1000)} m` : `距您 ${v.toFixed(1)} km`
}

/**
 * 按距离升序排序；返回新数组，不修改入参。
 * - location 为空 / 定位不可用 / 坐标无效 → 返回原顺序（后端 create_time 倒序）
 * - 站点无坐标或坐标为 0 → 排末尾，并保持它们之间的原有相对顺序
 * - 距离相同 → 保持原有相对顺序（稳定排序）
 */
export function sortByDistance(sites, location) {
  const list = Array.isArray(sites) ? sites.slice() : []
  const lat = location && location.latitude
  const lng = location && location.longitude
  if (!location || location.available === false || !validPoint(lat, lng)) return list

  return list
    .map((site, index) => ({
      site,
      index,
      km: distanceKm(lat, lng, site && site.latitude, site && site.longitude),
    }))
    .sort((a, b) => {
      const aMissing = a.km === null
      const bMissing = b.km === null
      if (aMissing !== bMissing) return aMissing ? 1 : -1
      if (aMissing) return a.index - b.index
      if (a.km === b.km) return a.index - b.index
      return a.km - b.km
    })
    .map((x) => x.site)
}

/** 给站点列表附加 distance / distanceText 字段，供模板直接渲染 */
export function withDistance(sites, location) {
  const lat = location && location.latitude
  const lng = location && location.longitude
  const usable = location && location.available !== false && validPoint(lat, lng)
  const sorted = sortByDistance(sites, usable ? location : null)
  return sorted.map((site) => {
    const km = usable ? distanceKm(lat, lng, site && site.latitude, site && site.longitude) : null
    return { ...site, distance: km, distanceText: formatDistance(km) }
  })
}

export default { distanceKm, formatDistance, sortByDistance, withDistance }
