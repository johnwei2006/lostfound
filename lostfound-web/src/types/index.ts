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
  /** 仅 /api/auth/me 返回：可管理的招领站 ID */
  managedSiteIds?: number[]
  /** 仅 /api/auth/me 返回：是否可进入站点管理后台 */
  siteManager?: boolean
}

/** 当前登录用户完整信息 */
export interface MeVO extends User {
  managedSiteIds: number[]
  siteManager: boolean
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
  /** 站点来源：PERSONAL / SITE / TRANSFER */
  sourceType?: SourceType
  /** 站点转交状态 */
  transferStatus?: TransferStatus
  /** 源头登记人 */
  submitUserId?: number
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
  sourceType?: SourceType
  transferStatus?: TransferStatus
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
  type: 'MATCH' | 'CLAIM' | 'SYSTEM' | 'TRANSFER' | 'RETURN' | 'SITE'
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
  /** 自定义背书：背书单位、推荐语与更新时间，未设置时为空 */
  endorsementOrg?: string
  endorsementText?: string
  endorsementUpdatedAt?: string
  endorsementUpdatedBy?: number
  /** 主管理员用户ID；每站唯一，由建站审核通过或超管指定产生 */
  ownerUserId?: number
  createTime: string
}

/** 站点列表（含批量统计字段） */
export interface SiteVO extends FoundSite {
  managerCount: number
  itemCount: number
  hasEndorsement: boolean
}

/* ---------------- 建站申请与背书 ---------------- */

export type SiteApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export const SITE_APPLICATION_STATUS_MAP: Record<SiteApplicationStatus, { text: string; cls: string }> = {
  PENDING: { text: '待审核', cls: 'lf-tag--waiting' },
  APPROVED: { text: '已通过', cls: 'lf-tag--finished' },
  REJECTED: { text: '已驳回', cls: 'lf-tag--danger' },
}

/** 注册用户提交的建站申请资料 */
export interface SiteApplicationPayload {
  name: string
  address: string
  longitude?: number
  latitude?: number
  phone?: string
  openTime?: string
  description?: string
  reason: string
  contact?: string
}

export interface SiteApplicationReviewPayload {
  approve: boolean
  remark?: string
}

/** 背书文案：允许清空，清空后前台不再展示背书状态 */
export interface SiteEndorsementPayload {
  endorsementOrg: string
  endorsementText: string
}

/** 申请列表 / 详情（/mine 与 /page 共用） */
export interface SiteApplication extends SiteApplicationPayload {
  id: number
  applicantUserId: number
  applicantNickname?: string
  status: SiteApplicationStatus
  statusLabel?: string
  reviewerUserId?: number
  reviewTime?: string
  reviewRemark?: string
  createdSiteId?: number
  createTime: string
  updateTime?: string
}

export interface SiteManager {
  id: number
  userId: number
  nickname: string
  /** 后端 managers() 派生：是否为主管理员 */
  isOwner?: boolean
}

/* ---------------- 招领站管理 ---------------- */

export type TransferStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type SourceType = 'PERSONAL' | 'SITE' | 'TRANSFER'
export type ReturnWay = 'CLAIM' | 'ONSITE'

export interface TimelineNode {
  key: string
  title: string
  detail?: string
  time?: string
  state: 'done' | 'current' | 'reject' | 'pending'
}

export interface TransferVO {
  id: number
  foundId: number
  itemTitle: string
  itemCategory?: string
  itemImages: string[]
  itemStatus?: string
  siteId: number
  siteName?: string
  fromUserId: number
  fromNickname?: string
  status: TransferStatus
  remark?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  createTime: string
  updateTime?: string
  returned?: boolean
  returnTime?: string
  returnWay?: ReturnWay
  timeline: TimelineNode[]
}

export interface ReturnRecordVO {
  id: number
  foundId: number
  itemTitle: string
  siteId?: number
  siteName?: string
  claimId?: number
  way: ReturnWay
  receiverName?: string
  receiverPhone?: string
  evidence: string[]
  remark?: string
  operatorId: number
  operatorName?: string
  returnTime: string
  createTime?: string
}

export interface SourceInfoVO {
  foundId: number
  title: string
  category?: string
  description?: string
  address?: string
  contact?: string
  images: string[]
  foundTime?: string
  createTime: string
  status?: string
  sourceType?: SourceType
  transferStatus?: TransferStatus
  originalUserId: number
  originalNickname?: string
  originalPhone?: string
  siteId?: number
  siteName?: string
  transferId?: number
  fromUserId?: number
  fromNickname?: string
  submitTime?: string
  transferRemark?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  reviewRemark?: string
  returns: ReturnRecordVO[]
}

export interface SiteStatsVO {
  siteId: number
  pendingTransfers: number
  pendingClaims: number
  siteItems: number
  returnedItems: number
  todayHandled: number
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

// CATEGORY_DICT_BEGIN
export const CATEGORIES = ['证件证照', '卡券票据', '手机通讯', '数码电子', '服饰配饰', '钥匙锁具', '钱包卡包', '箱包行李', '书刊文件', '生活用品', '宠物活体', '其他'] as const
// CATEGORY_DICT_END

export const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  WAITING: { text: '待认领', cls: 'lf-tag--waiting' },
  CLAIMED: { text: '待确认', cls: 'lf-tag--claimed' },
  FINISHED: { text: '已完成', cls: 'lf-tag--finished' },
  OFFLINE: { text: '已下架', cls: 'lf-tag--offline' },
}

export const TRANSFER_STATUS_MAP: Record<string, { text: string; cls: string }> = {
  NONE: { text: '未转交', cls: 'lf-tag--muted' },
  PENDING: { text: '待审批', cls: 'lf-tag--claimed' },
  APPROVED: { text: '已入库', cls: 'lf-tag--finished' },
  REJECTED: { text: '已驳回', cls: 'lf-tag--danger' },
  CANCELLED: { text: '已撤回', cls: 'lf-tag--muted' },
}

export const SOURCE_TYPE_MAP: Record<string, string> = {
  PERSONAL: '个人登记',
  SITE: '站点发布',
  TRANSFER: '转交入库',
}
