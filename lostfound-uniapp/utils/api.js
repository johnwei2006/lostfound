import request from './request'

/* ---------------- 认证 ---------------- */
export const authApi = {
  login: (data) => request({ url: '/api/auth/login', method: 'POST', data }),
  register: (data) => request({ url: '/api/auth/register', method: 'POST', data }),
  wxLogin: (data) => request({ url: '/api/auth/wx-login', method: 'POST', data }),
  me: () => request({ url: '/api/auth/me' }),
  updateProfile: (data) => request({ url: '/api/auth/profile', method: 'PUT', data }),
  /** 扫码登录网页：在小程序内确认票据，request.js 自动携带当前用户 JWT */
  confirmWebLogin: (ticket) => request({ url: '/api/auth/qr/confirm', method: 'POST', data: { ticket } }),
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
  /** 我可管理的站点；返回项含 ownerUserId，用于判断主/共同管理员身份 */
  mine: () => request({ url: '/api/foundsite/mine' }),
  /** 站点概览统计（后端按 requireManager 校验站点权限） */
  stats: (siteId) => request({ url: `/api/foundsite/${siteId}/stats` }),
}

/* ---------------- 建站申请 ---------------- */
export const siteApplicationApi = {
  submit: (data) => request({ url: '/api/site-applications', method: 'POST', data }),
  mine: () => request({ url: '/api/site-applications/mine' }),
  withdraw: (id) => request({ url: `/api/site-applications/${id}`, method: 'DELETE' }),
}

/* ---------------- 站点主/共同管理员 ---------------- */
export const siteManagerApi = {
  managers: (siteId) => request({ url: `/api/site-managers/${siteId}` }),
  detail: (siteId, userId) => request({ url: `/api/site-managers/${siteId}/members/${userId}` }),
  invites: (siteId) => request({ url: `/api/site-managers/${siteId}/invites` }),
  createInvite: (siteId, ttlHours = 24) =>
    request({ url: `/api/site-managers/${siteId}/invites`, method: 'POST', data: { ttlHours } }),
  revokeInvite: (siteId, inviteId) =>
    request({ url: `/api/site-managers/${siteId}/invites/${inviteId}`, method: 'DELETE' }),
  previewInvite: (code) => request({ url: '/api/site-managers/invites/preview', data: { code } }),
  acceptInvite: (code) => request({ url: '/api/site-managers/invites/accept', method: 'POST', data: { code } }),
  remove: (siteId, userId) =>
    request({ url: `/api/site-managers/${siteId}/members/${userId}`, method: 'DELETE' }),
  transfer: (siteId, targetUserId) =>
    request({ url: `/api/site-managers/${siteId}/transfer`, method: 'POST', data: { targetUserId } }),
}

/* ---------------- 站点转交 ---------------- */
export const transferApi = {
  mine: (page = 1, size = 10) => request({ url: '/api/transfer/mine', data: { page, size } }),
  /** 发起转交：物品 ID 走路径，body 传 siteId 与 remark */
  create: (foundId, data) => request({ url: `/api/found/${foundId}/transfer`, method: 'POST', data }),
  /** 按物品撤回当前待审批的转交申请 */
  cancelByItem: (foundId) => request({ url: `/api/found/${foundId}/transfer`, method: 'DELETE' }),
  /** 按转交单撤回（我的转交列表用） */
  cancel: (transferId) => request({ url: `/api/transfer/${transferId}/cancel`, method: 'PUT' }),
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

// CATEGORY_DICT_BEGIN
export const CATEGORIES = ['证件证照', '卡券票据', '手机通讯', '数码电子', '服饰配饰', '钥匙锁具', '钱包卡包', '箱包行李', '书刊文件', '生活用品', '宠物活体', '其他']
// CATEGORY_DICT_END
export const STATUS_MAP = {
  WAITING: { text: '待认领', cls: 'lf-tag--waiting' },
  CLAIMED: { text: '待确认', cls: 'lf-tag--claimed' },
  FINISHED: { text: '已完成', cls: 'lf-tag--finished' },
  OFFLINE: { text: '已下架', cls: 'lf-tag--offline' },
}
