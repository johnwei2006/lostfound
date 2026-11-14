<template>
  <view class="workbench">
    <site-switcher
      :site="site"
      :sites="sites"
      :current-id="site ? site.id : null"
      :owner="owner"
      @change="onSwitch"
    />

    <view v-if="error" class="lf-card error-card" @tap="load">
      <text class="lf-muted">{{ error }}</text>
      <text class="error-retry">点击重试</text>
    </view>
    <view v-else-if="loading && !site" class="lf-empty">正在加载站点…</view>

    <template v-else-if="site">
      <view class="banner lf-card" :class="owner ? 'banner--owner' : 'banner--co'">
        <text class="banner__title">{{ owner ? '您是本站主管理员' : '您是本站共同管理员' }}</text>
        <text class="banner__desc">
          {{ owner
            ? '可邀请共同管理员、移除共同管理员，或把主管理员身份转让给本站其它管理员。'
            : '可查看管理员与站点概览；邀请、移除与转让需由主管理员操作。物品、审批与归还管理请前往网页版后台。' }}
        </text>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">站点概览</text>
        <view class="stats">
          <view class="stat" v-for="s in statCards" :key="s.label">
            <text class="lf-stat-num">{{ s.value }}</text>
            <text class="stat__label">{{ s.label }}</text>
          </view>
        </view>
        <text class="stats-hint lf-muted">物品清单、转交与认领审批、归还登记、站点资料请在网页版后台操作</text>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">管理员与邀请</text>
        <view class="entry" @tap="go('/pages/manage/managers')">
          <view class="entry__icon entry__icon--blue">管</view>
          <view class="entry__body">
            <text class="entry__title">管理员管理</text>
            <text class="lf-muted">管理员列表、详情与身份查看</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
        <view v-if="owner" class="entry" @tap="go('/pages/manage/invite')">
          <view class="entry__icon entry__icon--green">邀</view>
          <view class="entry__body">
            <text class="entry__title">邀请共同管理员</text>
            <text class="lf-muted">生成邀请码或小程序码，对方确认后加入</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
        <view v-if="owner" class="entry" @tap="go('/pages/manage/transfer')">
          <view class="entry__icon entry__icon--orange">转</view>
          <view class="entry__body">
            <text class="entry__title">转让主管理员</text>
            <text class="lf-muted">转让后您将成为共同管理员</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
        <view v-if="!owner" class="entry entry--disabled">
          <view class="entry__icon entry__icon--grey">限</view>
          <view class="entry__body">
            <text class="entry__title">共同管理员权限</text>
            <text class="lf-muted">不可邀请其它管理员、不可移除管理员、不可转让主管理员</text>
          </view>
        </view>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">其它</text>
        <view class="entry" @tap="go('/pages/site-applications/site-applications')">
          <view class="entry__icon entry__icon--blue">申</view>
          <view class="entry__body">
            <text class="entry__title">我的建站申请</text>
            <text class="lf-muted">可继续申请下一个招领站</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
        <view class="entry" @tap="goHome">
          <view class="entry__icon entry__icon--grey">返</view>
          <view class="entry__body"><text class="entry__title">返回前台</text></view>
          <text class="entry__arrow">›</text>
        </view>
      </view>
    </template>

    <view v-else class="empty lf-card">
      <text class="empty__title">还没有可管理的招领站</text>
      <text class="empty__desc">提交建站申请并通过平台审核后，您会自动成为该站主管理员；也可以凭主管理员发来的邀请码加入站点。</text>
      <button class="lf-primary-btn empty__btn" @tap="go('/pages/site-apply/site-apply')">申请创建招领站</button>
      <button class="lf-btn lf-btn--ghost empty__btn" @tap="go('/pages/manage/invite-accept')">输入邀请码加入</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { siteApi } from '@/utils/api'
import siteStore from '@/utils/site-store'
import SiteSwitcher from '@/components/site-switcher/site-switcher.vue'

const sites = ref([])
const site = ref(null)
const owner = ref(false)
const stats = ref(null)
const loading = ref(false)
const error = ref('')
let pendingSelect = null

const statCards = computed(() => {
  const s = stats.value || {}
  return [
    { label: '在站物品', value: s.siteItems ?? '—' },
    { label: '待审转交', value: s.pendingTransfers ?? '—' },
    { label: '待审认领', value: s.pendingClaims ?? '—' },
    { label: '累计归还', value: s.returnedItems ?? '—' },
  ]
})

onLoad((options) => {
  if (options?.site) pendingSelect = Number(options.site) || null
})

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    await siteStore.load(true)
    if (pendingSelect && siteStore.sites.some((s) => Number(s.id) === pendingSelect)) {
      siteStore.select(pendingSelect)
    }
    pendingSelect = null
    sites.value = siteStore.sites
    site.value = siteStore.currentSite
    owner.value = siteStore.isOwner()
    // 切换站点时先清空上一站点的统计，避免短暂显示脏数据
    stats.value = null
    if (site.value) stats.value = await siteApi.stats(site.value.id)
  } catch (e) {
    error.value = e?.message || '站点数据加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const onSwitch = (id) => {
  siteStore.select(id)
  load()
}
const go = (url) => uni.navigateTo({ url })
const goHome = () => uni.switchTab({ url: '/pages/home/home' })

onShow(load)
onPullDownRefresh(load)
</script>

<style scoped>
.workbench { padding: 20rpx 20rpx 40rpx; }
.error-card { display: flex; justify-content: space-between; align-items: center; }
.error-retry { color: #2563eb; font-size: 25rpx; }
.banner { border-left: 8rpx solid #2563eb; }
.banner--owner { background: linear-gradient(135deg, #eff6ff, #ffffff); }
.banner--co { border-left-color: #94a3b8; background: #f8fafc; }
.banner__title { display: block; font-size: 30rpx; font-weight: 600; color: #1e3a8a; }
.banner__desc { display: block; margin-top: 10rpx; font-size: 25rpx; line-height: 1.7; color: #64748b; }
.stats { display: flex; flex-wrap: wrap; margin-top: 20rpx; }
.stat { width: 50%; box-sizing: border-box; padding: 16rpx 0 22rpx; display: flex; flex-direction: column; gap: 6rpx; }
.stat__label { color: #64748b; font-size: 24rpx; }
.stats-hint { display: block; margin-top: 8rpx; font-size: 21rpx; line-height: 1.6; }
.entry { display: flex; align-items: center; gap: 20rpx; padding: 24rpx 0; border-bottom: 2rpx solid #f1f5f9; }
.entry:last-child { border-bottom: none; }
.entry--disabled { opacity: .7; }
.entry__icon { width: 68rpx; height: 68rpx; border-radius: 20rpx; text-align: center; line-height: 68rpx; font-size: 28rpx; font-weight: 600; flex-shrink: 0; }
.entry__icon--blue { background: #eaf1fe; color: #2563eb; }
.entry__icon--green { background: #e7f8f1; color: #10b981; }
.entry__icon--orange { background: #fff3e8; color: #f97316; }
.entry__icon--grey { background: #f1f5f9; color: #94a3b8; }
.entry__body { flex: 1; display: flex; flex-direction: column; gap: 6rpx; min-width: 0; }
.entry__title { font-size: 29rpx; font-weight: 500; }
.entry__arrow { color: #c0c8d4; font-size: 36rpx; }
.empty { padding: 60rpx 40rpx; display: flex; align-items: center; flex-direction: column; }
.empty__title { font-size: 32rpx; font-weight: 600; }
.empty__desc { margin-top: 18rpx; font-size: 25rpx; line-height: 1.8; color: #64748b; text-align: center; }
.empty__btn { width: 100%; margin-top: 28rpx; }
.empty__btn::after { border: none; }
</style>
