<script>
import { ensureLogin } from '@/utils/session'
import { ensureLocation } from '@/utils/location'

export default {
  onLaunch() {
    // 进入小程序即静默建号/登录 + 获取当前位置；两者互不阻塞且各只执行一次
    ensureLogin().catch((e) => console.warn('[app] 静默登录异常', e))
    ensureLocation().catch((e) => console.warn('[app] 定位异常', e))
  },
}
</script>

<style>
page {
  background: #f5f7fa;
  font-size: 28rpx;
  color: #1f2937;
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* 全局工具类 */
.lf-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.05);
}
.lf-tag {
  display: inline-block;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}
.lf-tag--waiting { background: #eaf1fe; color: #2563eb; }
.lf-tag--claimed { background: #fff3e8; color: #f97316; }
.lf-tag--finished { background: #e7f8f1; color: #10b981; }
.lf-tag--offline { background: #f3f4f6; color: #9ca3af; }
.lf-primary { color: #2563eb; }
.lf-accent { color: #f97316; }
.lf-muted { color: #9ca3af; font-size: 24rpx; }
.lf-btn {
  background: #2563eb;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
  text-align: center;
  padding: 22rpx 0;
}
.lf-btn--accent { background: #f97316; }
.lf-btn--plain {
  background: #fff;
  color: #2563eb;
  border: 2rpx solid #2563eb;
}
.lf-empty {
  text-align: center;
  color: #9ca3af;
  padding: 120rpx 0;
  font-size: 26rpx;
}

/* 招领站申请与站点管理页复用组件 */
.lf-group-title {
  padding-left: 16rpx;
  border-left: 6rpx solid #2563eb;
  color: #94a3b8;
  font-size: 23rpx;
  letter-spacing: 2rpx;
  line-height: 1.4;
}
.lf-tag--owner { background: #fff7e6; color: #b45309; }
.lf-tag--pending { background: #eaf1fe; color: #2563eb; }
.lf-tag--approved { background: #e7f8f1; color: #10b981; }
.lf-tag--rejected { background: #feecec; color: #dc2626; }
.lf-tag--muted { background: #f3f4f6; color: #94a3b8; }
.lf-btn--danger { background: #dc2626; }
.lf-btn--ghost { background: #fff; color: #475569; border: 2rpx solid #e2e8f0; }
.lf-btn--busy { opacity: .6; }
.lf-field {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 2rpx solid #f1f5f9;
}
.lf-field:last-child { border-bottom: none; }
.lf-field__label { width: 170rpx; flex-shrink: 0; color: #64748b; font-size: 26rpx; }
.lf-field__value { flex: 1; color: #1f2937; font-size: 27rpx; word-break: break-all; }
.lf-input {
  width: 100%;
  padding: 0 20rpx;
  border: 2rpx solid #e2e8f0;
  border-radius: 14rpx;
  background: #f8fafc;
  font-size: 27rpx;
  color: #1f2937;
  box-sizing: border-box;
}
/* 微信原生 input 的输入文字与 placeholder 是两层独立绘制：
   固定高度让文字居中，但不能给 input 设超大 line-height，
   否则 placeholder 层会被挤出可视区显示不全 */
input.lf-input {
  height: 88rpx;
  line-height: normal;
}
textarea.lf-input {
  padding: 18rpx 20rpx;
  line-height: 1.6;
}
/* placeholder 单独指定正常行高，避免继承异常 */
.lf-ph,
.input-placeholder,
.textarea-placeholder {
  line-height: normal;
  color: #94a3b8;
}
.lf-primary-btn {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
  text-align: center;
  padding: 24rpx 0;
  box-shadow: 0 8rpx 24rpx rgba(37, 99, 235, .22);
}
.lf-stat-num { font-size: 44rpx; font-weight: 700; color: #1d4ed8; }

/* 站点日常管理页通用 */
.mng-page { padding: 20rpx 20rpx 48rpx; }
.mng-head { background: linear-gradient(135deg, #eff6ff, #ffffff); border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.mng-head__site { color: #1e3a8a; font-size: 30rpx; font-weight: 700; }
.mng-head__desc { display: block; margin-top: 8rpx; color: #64748b; font-size: 23rpx; line-height: 1.6; }
.mng-filters { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 18rpx; }
.mng-search { flex: 1; min-width: 260rpx; }
.mng-chip { padding: 12rpx 22rpx; border-radius: 999rpx; background: #fff; color: #64748b; font-size: 24rpx; border: 2rpx solid #e2e8f0; }
.mng-chip--on { background: #2563eb; color: #fff; border-color: #2563eb; }
.mng-card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, .05); }
.mng-card__top { display: flex; align-items: flex-start; gap: 14rpx; }
.mng-card__main { flex: 1; min-width: 0; }
.mng-card__title { display: block; font-size: 29rpx; font-weight: 600; color: #1f2937; }
.mng-card__meta { display: block; margin-top: 8rpx; color: #94a3b8; font-size: 22rpx; line-height: 1.6; }
.mng-card__body { margin-top: 16rpx; color: #64748b; font-size: 25rpx; line-height: 1.7; }
.mng-card__ops { display: flex; justify-content: flex-end; gap: 14rpx; margin-top: 18rpx; padding-top: 18rpx; border-top: 2rpx solid #f1f5f9; }
.mng-btn { margin: 0; padding: 12rpx 30rpx; font-size: 25rpx; line-height: 1.5; border-radius: 999rpx; }
.mng-btn::after { border: none; }
.mng-btn--primary { background: #2563eb; color: #fff; }
.mng-btn--danger { background: #fff; color: #dc2626; border: 2rpx solid #fecaca; }
.mng-btn--ghost { background: #f1f5f9; color: #475569; }
.mng-btn--busy { opacity: .6; }
.mng-foot { text-align: center; color: #94a3b8; font-size: 22rpx; padding: 24rpx 0; }
.mng-fab { position: fixed; right: 32rpx; bottom: 48rpx; z-index: 12; padding: 20rpx 34rpx; border-radius: 999rpx; background: linear-gradient(135deg, #3b82f6, #1d4ed8); color: #fff; font-size: 27rpx; box-shadow: 0 10rpx 26rpx rgba(37, 99, 235, .3); }
.mng-fab:active { opacity: .85; }
</style>
