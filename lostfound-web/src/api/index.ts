import request from './request'
import type {
  AdminStats,
  ClaimVO,
  CommentVO,
  FoundSite,
  ItemQuery,
  ItemVO,
  MatchVO,
  Message,
  PageResult,
  QrStatusVO,
  QrTicketVO,
  User,
} from '@/types'

/* ---------------- 认证 ---------------- */
export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post<never, { token: string; user: User }>('/api/auth/login', data),
  register: (data: { username: string; password: string; nickname: string; phone?: string }) =>
    request.post<never, { token: string; user: User }>('/api/auth/register', data),
  me: () => request.get<never, User>('/api/auth/me'),
  updateProfile: (data: Partial<User>) => request.put('/api/auth/profile', data),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put('/api/auth/password', data),
  /** Web 扫码登录：创建票据、轮询状态；确认接口由小程序端调用 */
  qrTicket: () => request.post<never, QrTicketVO>('/api/auth/qr/ticket'),
  qrStatus: (ticket: string) =>
    request.get<never, QrStatusVO>('/api/auth/qr/status', { params: { ticket } }),
  qrConfirm: (ticket: string) => request.post<never, void>('/api/auth/qr/confirm', { ticket }),
}

/* ---------------- 物品（失物/拾物） ---------------- */
export const itemApi = {
  page: (type: 'found' | 'lost', query: ItemQuery) =>
    request.get<never, PageResult<ItemVO>>(`/api/${type}/page`, { params: query }),
  latest: (type: 'found' | 'lost', limit = 8) =>
    request.get<never, ItemVO[]>(`/api/${type}/latest`, { params: { limit } }),
  detail: (type: 'found' | 'lost', id: number) =>
    request.get<never, ItemVO>(`/api/${type}/${id}`),
  publish: (type: 'found' | 'lost', data: Record<string, unknown>) =>
    request.post<never, number>(`/api/${type}`, data),
  update: (type: 'found' | 'lost', id: number, data: Record<string, unknown>) =>
    request.put(`/api/${type}/${id}`, data),
  remove: (type: 'found' | 'lost', id: number) => request.delete(`/api/${type}/${id}`),
  changeStatus: (type: 'found' | 'lost', id: number, status: string) =>
    request.put(`/api/${type}/${id}/status`, null, { params: { status } }),
  mine: (type: 'found' | 'lost', page = 1, size = 10) =>
    request.get<never, PageResult<ItemVO>>(`/api/${type}/mine`, { params: { page, size } }),
  mapItems: (type: 'found' | 'lost', limit = 200) =>
    request.get<never, ItemVO[]>(`/api/${type}/map`, { params: { limit } }),
}

/* ---------------- 认领 ---------------- */
export const claimApi = {
  submit: (type: 'found' | 'lost', itemId: number, data: { description: string; contact?: string }) =>
    request.post<never, number>(`/api/${type}/${itemId}/claim`, data),
  listByItem: (type: 'found' | 'lost', itemId: number) =>
    request.get<never, ClaimVO[]>(`/api/${type}/${itemId}/claims`),
  review: (type: 'found' | 'lost', claimId: number, approve: boolean) =>
    request.put(`/api/${type}/claims/${claimId}/review`, null, { params: { approve } }),
  mine: () => request.get<never, ClaimVO[]>('/api/found/claims/received'),
}

/* ---------------- 评论 ---------------- */
export const commentApi = {
  list: (type: 'FOUND' | 'LOST', itemId: number) =>
    request.get<never, CommentVO[]>(`/api/comment/${type}/${itemId}`),
  add: (type: 'FOUND' | 'LOST', itemId: number, data: { content: string; parentId?: number }) =>
    request.post<never, number>(`/api/comment/${type}/${itemId}`, data),
  remove: (type: 'FOUND' | 'LOST', id: number) => request.delete(`/api/comment/${type}/${id}`),
}

/* ---------------- 关注 ---------------- */
export const focusApi = {
  focus: (type: 'FOUND' | 'LOST', itemId: number) =>
    request.post(`/api/focus/${type}/${itemId}`),
  unfocus: (type: 'FOUND' | 'LOST', itemId: number) =>
    request.delete(`/api/focus/${type}/${itemId}`),
  status: (type: 'FOUND' | 'LOST', itemId: number) =>
    request.get<never, boolean>(`/api/focus/${type}/${itemId}/status`),
  mine: (type: 'FOUND' | 'LOST', page = 1, size = 10) =>
    request.get<never, PageResult<ItemVO>>(`/api/focus/${type}/mine`, { params: { page, size } }),
}

/* ---------------- 招领站 ---------------- */
export const siteApi = {
  page: (params: { keyword?: string; page?: number; size?: number }) =>
    request.get<never, PageResult<FoundSite>>('/api/foundsite/page', { params }),
  detail: (id: number) => request.get<never, FoundSite>(`/api/foundsite/${id}`),
  managers: (id: number) =>
    request.get<never, { userId: number; nickname: string }[]>(`/api/foundsite/${id}/managers`),
  create: (data: Partial<FoundSite>) => request.post<never, number>('/api/foundsite', data),
  update: (id: number, data: Partial<FoundSite>) => request.put(`/api/foundsite/${id}`, data),
  remove: (id: number) => request.delete(`/api/foundsite/${id}`),
}

/* ---------------- 消息 ---------------- */
export const messageApi = {
  page: (params: { type?: string; isRead?: number; page?: number; size?: number }) =>
    request.get<never, PageResult<Message>>('/api/message/page', { params }),
  unreadCount: () => request.get<never, number>('/api/message/unread-count'),
  read: (id: number) => request.put(`/api/message/${id}/read`),
  readAll: () => request.put('/api/message/read-all'),
}

/* ---------------- 匹配 ---------------- */
export const matchApi = {
  mine: () => request.get<never, MatchVO[]>('/api/match/mine'),
  setStatus: (id: number, status: string) =>
    request.put(`/api/match/${id}/status`, null, { params: { status } }),
}

/* ---------------- 文件 ---------------- */
export const fileApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<never, { url: string }>('/api/file/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

/* ---------------- 管理端 ---------------- */
export const adminApi = {
  stats: () => request.get<never, AdminStats>('/api/admin/stats'),
  items: (params: {
    type: string
    reviewStatus?: string
    status?: string
    keyword?: string
    page?: number
    size?: number
  }) => request.get<never, PageResult<Record<string, unknown>>>('/api/admin/items', { params }),
  review: (type: string, id: number, approve: boolean) =>
    request.put(`/api/admin/item/${type}/${id}/review`, null, { params: { approve } }),
  users: (params: { keyword?: string; page?: number; size?: number }) =>
    request.get<never, PageResult<User>>('/api/admin/users', { params }),
  changeUserStatus: (id: number, enable: boolean) =>
    request.put(`/api/admin/user/${id}/status`, null, { params: { enable } }),
}
