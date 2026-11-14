<template>
  <view class="apply">
    <!-- 提交结果态 -->
    <view v-if="done" class="done lf-card">
      <view class="done__icon">✓</view>
      <text class="done__title">申请已提交</text>
      <text class="done__desc">平台管理员会在后台审核您的站点资料，审核结果会通过「通知」推送。审核通过后您将自动成为该站主管理员。</text>
      <button class="lf-primary-btn done__btn" @tap="goApplications">查看审核进度</button>
      <button class="lf-btn lf-btn--ghost done__btn" @tap="goBack">返回</button>
    </view>

    <template v-else>
      <!-- 四步进度 -->
      <view class="steps lf-card">
        <view v-for="(s, i) in steps" :key="s" class="step" :class="{ active: i <= activeStep }">
          <view class="step__dot">{{ i < activeStep ? '✓' : i + 1 }}</view>
          <text class="step__text">{{ s }}</text>
        </view>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">基础资料</text>
        <view class="form-row">
          <text class="form-label">站点名称 <text class="req">*</text></text>
          <input v-model="form.name" class="lf-input" placeholder-class="lf-ph" placeholder="如： XX 社区招领站" maxlength="100" />
        </view>
        <view class="form-row">
          <text class="form-label">详细地址 <text class="req">*</text></text>
          <input v-model="form.address" class="lf-input" placeholder-class="lf-ph" placeholder="街道、门牌号等" maxlength="200" />
        </view>
        <view class="form-row">
          <text class="form-label">联系电话</text>
          <input v-model="form.phone" class="lf-input" placeholder-class="lf-ph" placeholder="选填，方便平台联系" maxlength="20" />
        </view>
        <view class="form-row">
          <text class="form-label">开放时间</text>
          <input v-model="form.openTime" class="lf-input" placeholder-class="lf-ph" placeholder="如： 周一至周五 9:00-18:00" maxlength="100" />
        </view>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">站点位置</text>
        <view class="loc-row">
          <button class="loc-btn" @tap="chooseLocation">地图选点</button>
          <button class="loc-btn loc-btn--plain" @tap="useCurrentLocation">当前位置</button>
        </view>
        <text v-if="hasLocation" class="loc-text">已选坐标：{{ form.longitude }}, {{ form.latitude }}</text>
        <text v-else class="loc-text loc-text--muted">定位不可用时不写入坐标，可稍后在地图选点补充</text>
      </view>

      <view class="lf-card">
        <text class="lf-group-title">补充说明</text>
        <view class="form-row">
          <text class="form-label">站点简介</text>
          <textarea v-model="form.description" class="lf-input area" placeholder-class="lf-ph" placeholder="场地、保管条件等（选填）" maxlength="1000" />
        </view>
        <view class="form-row">
          <text class="form-label">申请理由 <text class="req">*</text></text>
          <textarea v-model="form.reason" class="lf-input area" placeholder-class="lf-ph" placeholder="为什么希望创建这个招领站" maxlength="500" />
        </view>
        <view class="form-row">
          <text class="form-label">补充联系方式</text>
          <input v-model="form.contact" class="lf-input" placeholder-class="lf-ph" placeholder="微信号 / 邮箱（选填）" maxlength="100" />
        </view>
      </view>

      <view class="submit-bar">
        <view class="submit-info">
          <text class="lf-muted">资料完整度 {{ completeness }}%</text>
          <text class="lf-muted">同一时间仅可有一条待审申请</text>
        </view>
        <button class="lf-primary-btn submit-btn" :class="{ 'lf-btn--busy': submitting }" :disabled="submitting" @tap="submit">
          {{ submitting ? '提交中…' : '提交建站申请' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { siteApplicationApi } from '@/utils/api'
import { ensurePrivacyAuthorize, ensureLocation, getCurrentLocation } from '@/utils/location'

const steps = ['资料', '位置', '补充', '结果']
const form = reactive({
  name: '', address: '', longitude: null, latitude: null,
  phone: '', openTime: '', description: '', reason: '', contact: '',
})
const submitting = ref(false)
const done = ref(false)
const locationHint = ref('')

const hasLocation = computed(() => form.longitude != null && form.latitude != null)
const completeness = computed(() => {
  const keys = ['name', 'address', 'phone', 'openTime', 'description', 'reason', 'contact']
  const filled = keys.filter((k) => String(form[k] || '').trim()).length
  return Math.round((filled / keys.length) * 100)
})
const activeStep = computed(() => {
  if (done.value) return 3
  if (String(form.reason).trim()) return 2
  if (hasLocation.value) return 1
  return 0
})

const fillLocation = (l) => {
  if (!l || l.available === false || l.longitude == null) return
  if (hasLocation.value) return
  form.longitude = l.longitude
  form.latitude = l.latitude
}

onLoad(() => {
  // 驳回后「带资料重提」由申请列表页写入草稿
  const draft = uni.getStorageSync('lf_site_apply_draft')
  if (draft && typeof draft === 'object') {
    Object.keys(form).forEach((k) => { if (draft[k] !== undefined) form[k] = draft[k] })
    uni.removeStorageSync('lf_site_apply_draft')
    uni.showToast({ title: '已带入上次资料', icon: 'none' })
  }
})

onShow(() => {
  const u = uni.getStorageSync('lf_user') || {}
  if (!form.contact) form.contact = u.contact || ''
  fillLocation(getCurrentLocation())
  ensureLocation().then(fillLocation).catch(() => { locationHint.value = '定位不可用' })
})

const chooseLocation = async () => {
  try {
    await ensurePrivacyAuthorize()
    uni.chooseLocation({
      success: (res) => {
        form.address = res.address || res.name || form.address
        form.longitude = res.longitude
        form.latitude = res.latitude
      },
      fail: (e) => {
        if (!/cancel/i.test(String(e?.errMsg || ''))) {
          uni.showToast({ title: '选点失败，请检查定位授权', icon: 'none' })
        }
      },
    })
  } catch (e) {
    uni.showToast({ title: '选点失败，请检查隐私授权', icon: 'none' })
  }
}

const useCurrentLocation = () => {
  const l = getCurrentLocation()
  if (!l || l.available === false || l.longitude == null) {
    uni.showToast({ title: '定位不可用，请手动选点', icon: 'none' })
    return
  }
  form.longitude = l.longitude
  form.latitude = l.latitude
  uni.showToast({ title: '已带入当前坐标', icon: 'none' })
}

const submit = async () => {
  const name = String(form.name).trim()
  const address = String(form.address).trim()
  const reason = String(form.reason).trim()
  if (!name) return uni.showToast({ title: '请填写站点名称', icon: 'none' })
  if (!address) return uni.showToast({ title: '请填写详细地址', icon: 'none' })
  if (!reason) return uni.showToast({ title: '请填写申请理由', icon: 'none' })
  if (submitting.value) return
  submitting.value = true
  try {
    await siteApplicationApi.submit({
      ...form, name, address, reason,
      phone: String(form.phone).trim() || undefined,
      openTime: String(form.openTime).trim() || undefined,
      description: String(form.description).trim() || undefined,
      contact: String(form.contact).trim() || undefined,
    })
    done.value = true
  } catch (e) {
    uni.showToast({ title: e?.message || '提交失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

const goApplications = () => uni.redirectTo({ url: '/pages/site-applications/site-applications' })
const goBack = () => uni.navigateBack({ delta: 1 })
</script>

<style scoped>
.apply { padding: 20rpx 20rpx 200rpx; }
.steps { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 20rpx; }
.step { display: flex; flex-direction: column; align-items: center; gap: 8rpx; flex: 1; opacity: .45; }
.step.active { opacity: 1; }
.step__dot { width: 48rpx; height: 48rpx; border-radius: 50%; background: #e2e8f0; color: #64748b; text-align: center; line-height: 48rpx; font-size: 24rpx; font-weight: 600; }
.step.active .step__dot { background: #2563eb; color: #fff; }
.step__text { font-size: 22rpx; color: #64748b; }
.form-row { margin-top: 22rpx; }
.form-label { display: block; margin-bottom: 10rpx; font-size: 26rpx; color: #475569; }
.req { color: #dc2626; }
.area { height: 150rpx; padding-top: 16rpx; }
.loc-row { display: flex; gap: 16rpx; margin-top: 20rpx; }
.loc-btn { flex: 1; background: #eaf1fe; color: #2563eb; border-radius: 14rpx; font-size: 27rpx; padding: 16rpx 0; }
.loc-btn--plain { background: #fff; border: 2rpx solid #dbeafe; }
.loc-btn::after { border: none; }
.loc-text { display: block; margin-top: 16rpx; font-size: 23rpx; color: #16a34a; }
.loc-text--muted { color: #94a3b8; }
.submit-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 24rpx rgba(15, 23, 42, .06); }
.submit-info { display: flex; justify-content: space-between; margin-bottom: 12rpx; }
.submit-btn { width: 100%; }
.done { margin-top: 80rpx; padding: 60rpx 40rpx; display: flex; align-items: center; flex-direction: column; }
.done__icon { width: 108rpx; height: 108rpx; border-radius: 50%; background: #e7f8f1; color: #10b981; font-size: 56rpx; text-align: center; line-height: 108rpx; }
.done__title { margin-top: 28rpx; font-size: 36rpx; font-weight: 700; }
.done__desc { margin-top: 20rpx; font-size: 26rpx; line-height: 1.8; color: #64748b; text-align: center; }
.done__btn { width: 100%; margin-top: 30rpx; }
.done__btn::after { border: none; }
</style>
