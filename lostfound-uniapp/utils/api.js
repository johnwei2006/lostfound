import request from './request'

/* ---------------- 认证 ---------------- */
export const authApi = {
  login: (data) => request({ url: '/api/auth/login', method: 'POST', data }),
  register: (data) => request({ url: '/api/auth/register', method: 'POST', data }),
  me: () => request({ url: '/api/auth/me' }),
  updateProfile: (data) => request({ url: '/api/auth/profile', method: 'PUT', data }),
}

/* ---------------- 物品 ---------------- */
export const itemApi = {
  page: (type, query) => request({ url: `/api/${type}/page`, data: query }),
  latest: (type, limit = 10) => request({ url: `/api/${type}/latest`, data: { limit } }),
  detail: (type, id) => request({ url: `/api/${type}/${id}` }),
  publish: (type, data) => request({ url: `/api/${type}`, method: 'POST', data }),
  mine: (type, page = 1, size = 10) => request({ url: `/api/${type}/mine`, data: { page, size } }),
  mapItems: (type, limit = 200) => request({ url: `/api/${type}/map`, data: { limit } }),
}

/* ---------------- 认领 ---------------- */
export const claimApi = {
  submit: (type, itemId, data) => request({ url: `/api/${type}/${itemId}/claim`, method: 'POST', data }),
  listByItem: (type, itemId) => request({ url: `/api/${type}/${itemId}/claims` }),
  review: (type, claimId, approve) =>
    request({ url: `/api/${type}/claims/${claimId}/review`, method: 'PUT', data: { approve } }),
}

/* ---------------- 评论 ---------------- */
export const commentApi = {
  list: (type, itemId) => request({ url: `/api/comment/${type}/${itemId}` }),
  add: (type, itemId, data) => request({ url: `/api/comment/${type}/${itemId}`, method: 'POST', data }),
}

/* ---------------- 关注 ---------------- */
export const focusApi = {
  focus: (type, itemId) => request({ url: `/api/focus/${type}/${itemId}`, method: 'POST' }),
  unfocus: (type, itemId) => request({ url: `/api/focus/${type}/${itemId}`, method: 'DELETE' }),
  mine: (type, page = 1, size = 10) => request({ url: `/api/focus/${type}/mine`, data: { page, size } }),
}

/* ---------------- 招领站 ---------------- */
export const siteApi = {
  page: (query) => request({ url: '/api/foundsite/page', data: query }),
}

/* ---------------- 消息 ---------------- */
export const messageApi = {
  page: (query) => request({ url: '/api/message/page', data: query }),
  unreadCount: () => request({ url: '/api/message/unread-count' }),
  read: (id) => request({ url: `/api/message/${id}/read`, method: 'PUT' }),
  readAll: () => request({ url: '/api/message/read-all', method: 'PUT' }),
}

/* ---------------- 匹配 ---------------- */
export const matchApi = {
  mine: () => request({ url: '/api/match/mine' }),
  setStatus: (id, status) => request({ url: `/api/match/${id}/status`, method: 'PUT', data: { status } }),
}

export const CATEGORIES = ['证件', '钥匙', '手机', '电子产品', '钱包', '书籍', '衣物', '其他']
export const STATUS_MAP = {
  WAITING: { text: '待认领', cls: 'lf-tag--waiting' },
  CLAIMED: { text: '待确认', cls: 'lf-tag--claimed' },
  FINISHED: { text: '已完成', cls: 'lf-tag--finished' },
  OFFLINE: { text: '已下架', cls: 'lf-tag--offline' },
}
