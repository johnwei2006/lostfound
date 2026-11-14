<template>
  <view class="mng-page">
    <site-context-bar />

    <view class="mng-head">
      <text class="mng-head__site">{{ siteName || '现场登记归还' }}</text>
      <text class="mng-head__desc">登记需选择本站已入库物品、填写领取人；登记后物品自动置为已完成。</text>
    </view>

    <view class="mng-filters">
      <text class="mng-chip" :class="{ 'mng-chip--on': tab === 'form' }" @tap="tab = 'form'">新增登记</text>
      <text class="mng-chip" :class="{ 'mng-chip--on': tab === 'records' }" @tap="switchRecords">归还记录</text>
    </view>

    <template v-if="tab === 'form'">
      <view class="mng-card">
        <text class="lf-group-title">选择归还物品</text>
        <picker :range="itemNames" :value="itemIndex" @change="onPickItem">
          <view class="lf-input mng-picker">{{ itemIndex >= 0 ? itemNames[itemIndex] : '点击选择本站物品' }}</view>
        </picker>
        <text class="mng-card__meta">仅列出在站待领物品；加载中请稍候</text>

        <text class="lf-group-title" style="display:block; margin-top:24rpx">领取人姓名 *</text>
        <input class="lf-input" v-model="form.receiverName" maxlength="50" placeholder="请填写领取人姓名" />

        <text class="lf-group-title" style="display:block; margin-top:20rpx">联系电话</text>
        <input class="lf-input" v-model="form.receiverPhone" maxlength="30" placeholder="选填，便于后续核对" />

        <text class="lf-group-title" style="display:block; margin-top:20rpx">归还凭证</text>
        <view class="mng-evidence">
          <view v-for="(img, i) in evidence" :key="i" class="mng-evidence__item">
            <image :src="img" mode="aspectFill" />
            <text class="mng-evidence__del" @tap="removeEvidence(i)">×</text>
          </view>
          <view v-if="evidence.length < 3" class="mng-evidence__add" @tap="chooseEvidence">＋</view>
        </view>

        <text class="lf-group-title" style="display:block; margin-top:20rpx">备注</text>
        <textarea class="lf-input" v-model="form.remark" maxlength="500" placeholder="选填，如证件核验情况" />
      </view>

      <button class="lf-primary-btn" :disabled="submitting" @tap="submit">
        {{ submitting ? '提交中…' : '确认登记归还' }}
      </button>
    </template>

    <template v-else>
      <view v-if="error" class="mng-card" @tap="reload">
        <text class="mng-card__meta">{{ error }}</text>
        <text class="mng-card__meta">点击重试</text>
      </view>
      <view v-for="r in items" :key="r.id" class="mng-card">
        <view class="mng-card__top">
          <view class="mng-card__main">
            <text class="mng-card__title">{{ r.itemTitle || '招领物品 #' + r.foundId }}</text>
            <text class="mng-card__meta">{{ r.way === 'ONSITE' ? '现场登记' : '认领审批' }} · {{ formatTime(r.returnTime || r.createTime) }}</text>
          </view>
          <text class="lf-tag lf-tag--finished">已归还</text>
        </view>
        <text class="mng-card__body">领取人：{{ maskName(r.receiverName) }}{{ r.receiverPhone ? ' · ' + maskPhone(r.receiverPhone) : '' }}</text>
        <text v-if="r.remark" class="mng-card__meta">备注：{{ r.remark }}</text>
        <text class="mng-card__meta">登记人 {{ r.operatorName || '—' }}</text>
      </view>
      <view v-if="loading" class="mng-foot">加载中…</view>
      <view v-else-if="!items.length && !error" class="lf-empty">暂无归还记录</view>
      <view v-else-if="noMore && items.length" class="mng-foot">共 {{ total }} 条，已全部加载</view>
    </template>
  </view>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { siteApi } from '@/utils/api'
import { uploadFile } from '@/utils/request'
import { useSitePagedList } from '@/utils/use-site-list'
import siteStore, { CONTEXT_CHANGE_EVENT } from '@/utils/site-store'

const tab = ref('form')
const submitting = ref(false)
const evidence = ref([])
const form = reactive({ receiverName: '', receiverPhone: '', remark: '' })
const candidates = ref([])
const itemIndex = ref(-1)
const itemNames = ref([])

const fetcher = (siteId, page, size) => siteApi.returns(siteId, { page, size })
const list = useSitePagedList(fetcher, { ttl: 1500 })
const { siteName, items, total, loading, noMore, error, reload } = list

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const maskName = (n) => (!n ? '—' : String(n).length <= 1 ? n : String(n)[0] + '*'.repeat(String(n).length - 1))
const maskPhone = (p) => (!p ? '' : String(p).replace(/^(.{3}).*(.{4})$/, '$1****$2'))

const loadCandidates = async () => {
  const sid = list.siteId.value || siteStore.currentSiteId
  if (!sid) return
  try {
    const data = await siteApi.items(sid, { page: 1, size: 50, status: 'WAITING' })
    candidates.value = (data && data.records) || []
    itemNames.value = candidates.value.map((i) => `${i.title}${i.status === 'OFFLINE' ? '（已下架）' : ''}`)
  } catch (e) {
    uni.showToast({ title: '物品列表加载失败', icon: 'none' })
  }
}

const switchRecords = () => {
  tab.value = 'records'
  reload()
}
const onPickItem = (e) => { itemIndex.value = Number(e.detail.value) }

const chooseEvidence = () => {
  uni.chooseImage({
    count: 3 - evidence.value.length,
    sizeType: ['compressed'],
    success: async (res) => {
      uni.showLoading({ title: '上传中' })
      try {
        for (const p of (res.tempFilePaths || [])) evidence.value.push(await uploadFile(p))
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
  })
}
const removeEvidence = (i) => { evidence.value.splice(i, 1) }

const submit = () => {
  const chosen = candidates.value[itemIndex.value]
  if (!chosen) return uni.showToast({ title: '请选择归还物品', icon: 'none' })
  if (!form.receiverName.trim()) return uni.showToast({ title: '请填写领取人', icon: 'none' })
  if (form.receiverPhone && !/^[\d\-+() ]{5,30}$/.test(form.receiverPhone.trim())) {
    return uni.showToast({ title: '联系电话格式有误', icon: 'none' })
  }
  uni.showModal({
    title: '确认登记归还',
    content: `将「${chosen.title}」登记归还给 ${form.receiverName.trim()}？登记后不可撤销。`,
    success: (r) => { if (r.confirm) doSubmit(chosen.id) },
  })
}

const doSubmit = async (foundId) => {
  const sid = list.siteId.value || siteStore.currentSiteId
  if (!sid) return uni.showToast({ title: '请先选择招领站', icon: 'none' })
  submitting.value = true
  try {
    await siteApi.registerReturn(sid, {
      foundId,
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      evidence: evidence.value.slice(),
      remark: form.remark.trim(),
    })
    uni.showToast({ title: '登记成功', icon: 'success' })
    form.receiverName = ''
    form.receiverPhone = ''
    form.remark = ''
    evidence.value = []
    itemIndex.value = -1
    loadCandidates()
    tab.value = 'records'
    reload()
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '登记失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

// 页面首屏与站点切换后都要刷新可选物品
const onContextChange = () => { itemIndex.value = -1; loadCandidates() }
onMounted(async () => {
  await siteStore.ensureLoaded()
  loadCandidates()
  if (typeof uni.$on === 'function') uni.$on(CONTEXT_CHANGE_EVENT, onContextChange)
})
onUnmounted(() => {
  if (typeof uni.$off === 'function') uni.$off(CONTEXT_CHANGE_EVENT, onContextChange)
})
</script>

<style scoped>
.mng-picker { display: flex; align-items: center; color: #1f2937; }
.mng-evidence { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 12rpx; }
.mng-evidence__item { position: relative; width: 150rpx; height: 150rpx; border-radius: 12rpx; overflow: hidden; }
.mng-evidence__item image { width: 100%; height: 100%; }
.mng-evidence__del { position: absolute; right: 6rpx; top: 2rpx; color: #fff; background: rgba(0,0,0,.5); border-radius: 50%; width: 36rpx; height: 36rpx; text-align: center; line-height: 34rpx; }
.mng-evidence__add { width: 150rpx; height: 150rpx; border: 2rpx dashed #cbd5e1; border-radius: 12rpx; color: #94a3b8; font-size: 48rpx; display: flex; align-items: center; justify-content: center; }
</style>
