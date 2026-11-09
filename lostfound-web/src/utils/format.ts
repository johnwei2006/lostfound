import dayjs from 'dayjs'

/** 后台列表统一时间格式 */
export const fmtTime = (v?: string | null): string => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—')

/** 业务异常统一提取可读提示（request.ts 对 code!=0 抛出 Error(message)） */
export const errMsg = (e: unknown, fallback = '操作失败'): string =>
  e instanceof Error && e.message ? e.message : fallback
