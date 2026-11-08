/** 通用类型定义 */

export interface User {
  id: number
  username: string
  nickname: string
  avatar?: string
  phone?: string
  email?: string
  role: 'USER' | 'ADMIN'
  status: number
  createTime: string
}

export interface ItemVO {
  id: number
  type: 'FOUND' | 'LOST'
  userId: number
  nickname?: string
  avatar?: string
  siteId?: number
  siteName?: string
  title: string
  category: string
  description?: string
  images: string[]
  province?: string
  city?: string
  district?: string
  address?: string
  longitude?: number
  latitude?: number
  itemTime?: string
  contact?: string
  status: 'WAITING' | 'CLAIMED' | 'FINISHED' | 'OFFLINE'
  reviewStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  viewCount: number
  focusCount: number
  commentCount: number
  focused?: boolean
  /** 距用户当前位置的公里数，仅按距离排序（sort=distance）时返回 */
  distance?: number
  createTime: string
  updateTime?: string
}

export interface PageResult<T> {
  total: number
  pages: number
  current: number
  size: number
  records: T[]
}

/** distance-距离近到远 / time、new-发布时间近到远 / hot-浏览最多 */
export type ItemSort = 'distance' | 'time' | 'new' | 'hot'

export interface ItemQuery {
  keyword?: string
  category?: string
  city?: string
  status?: string
  days?: number
  sort?: ItemSort
  /** 用户纬度（gcj02），仅 distance 排序使用 */
  userLat?: number
  /** 用户经度（gcj02），仅 distance 排序使用 */
  userLng?: number
  siteId?: number
  page?: number
  size?: number
}

export interface ClaimVO {
  id: number
  itemType: string
  itemId: number
  itemTitle?: string
  userId: number
  nickname?: string
  avatar?: string
  description: string
  contact?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createTime: string
}

export interface CommentVO {
  id: number
  itemId: number
  userId: number
  parentId?: number
  content: string
  nickname?: string
  avatar?: string
  createTime: string
  children: CommentVO[]
}

export interface Message {
  id: number
  title: string
  content: string
  type: 'MATCH' | 'CLAIM' | 'SYSTEM'
  relatedId?: number
  isRead: number
  createTime: string
}

export interface MatchVO {
  id: number
  score: number
  reason?: string
  status: 'UNREAD' | 'ACCEPTED' | 'IGNORED'
  createTime: string
  found?: ItemVO
  lost?: ItemVO
}

export interface FoundSite {
  id: number
  name: string
  address: string
  longitude?: number
  latitude?: number
  phone?: string
  openTime?: string
  description?: string
  createTime: string
}

export interface AdminStats {
  userCount: number
  foundCount: number
  lostCount: number
  waitingFound: number
  waitingLost: number
  pendingReview: number
  matchCount: number
  claimPending: number
  trend: { date: string; found: number; lost: number }[]
  categoryStats: { category: string; count: number }[]
}

/** Web 扫码登录票据：qrCode 为可直接用于 img src 的 data URL */
export interface QrTicketVO {
  ticket: string
  qrCode: string
  expiresIn: number
}

export type QrLoginStatus = 'pending' | 'confirmed' | 'expired'

/** Web 轮询扫码状态；confirmed 时携带登录会话 */
export interface QrStatusVO {
  status: QrLoginStatus
  token?: string
  user?: User
}

export const CATEGORIES = ['证件', '钥匙', '手机', '电子产品', '钱包', '书籍', '衣物', '其他'] as const

export const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  WAITING: { text: '待认领', cls: 'lf-tag--waiting' },
  CLAIMED: { text: '待确认', cls: 'lf-tag--claimed' },
  FINISHED: { text: '已完成', cls: 'lf-tag--finished' },
  OFFLINE: { text: '已下架', cls: 'lf-tag--offline' },
}
