<template>
  <view class="list-page">
    <!-- 我的发布 / 我的关注 / 匹配推荐 -->
    <template v-if="mode === 'match'">
      <view v-for="m in matches" :key="m.id" class="lf-card">
        <view class="match-head">
          <view class="score" :class="{ high: m.score >= 80 }">{{ Math.round(m.score) }}%</view>
          <view style="flex: 1">
            <text style="font-weight: 600">匹配推荐</text>
            <text class="lf-muted" style="display: block; margin-top: 4rpx">{{ m.reason }}</text>
          </view>
          <text v-if="m.status === 'UNREAD'" class="lf-tag lf-tag--waiting">待处理</text>
          <text v-else-if="m.status === 'ACCEPTED'" class="lf-tag lf-tag--finished">已采纳</text>
          <text v-else class="lf-tag lf-tag--offline">已忽略</text>
        </view>
        <view class="match-pair">
          <view class="pair" v-if="m.found" @tap="goDetail('found', m.found.id)">
            <text class="lf-tag lf-tag--finished">拾物</text>
            <text style="font-weight: 600; margin-top: 8rpx; display: block">{{ m.found.title }}</text>
          </view>
          <view class="pair" v-if="m.lost" @tap="goDetail('lost', m.lost.id)">
            <text class="lf-tag lf-tag--claimed">失物</text>
            <text style="font-weight: 600; margin-top: 8rpx; display: block">{{ m.lost.title }}</text>
          </view>
        </view>
        <view v-if="m.status === 'UNREAD'" class="match-ops">
          <button class="lf-btn" style="flex: 1; padding: 14rpx 0; font-size: 26rpx" @tap="act(m, 'ACCEPTED')">采纳</button>
          <button class="lf-btn lf-btn--plain" style="flex: 1; padding: 14rpx 0; font-size: 26rpx" @tap="act(m, 'IGNORED')">忽略</button>
        </view>
      </view>
      <view v-if="!loading && matches.length === 0" class="lf-empty">
        暂无匹配推荐，发布失物/拾物后系统将自动配对
      </view>
    </template>

    <template v-else>
      <view class="tabs">
        <view class="tab" :class="{ active: curType === 'found' }" @tap="switchType('found')">我的拾物</view>
        <view class="tab" :class="{ active: curType === 'lost' }" @tap="switchType('lost')">我的失物</view>
      </view>
      <view v-for="it in list" :key="it.id">
        <item-card :item="it" />
        <view v-if="mode === 'published' && curType === 'found'" class="transfer-bar">
          <text class="lf-tag" :class="transferCls(it.transferStatus)">{{ transferText(it.transferStatus) }}</text>
          <button
            v-if="canTransfer(it)"
            class="lf-btn transfer-btn"
            @tap="openTransfer(it)"
          >转交给招领站</button>
          <button
            v-else-if="it.transferStatus === 'PENDING'"
            class="lf-btn lf-btn--plain transfer-btn"
            @tap="cancelTransfer(it)"
          >撤回申请</button>
        </view>
      </view>
      <view v-if="!loading && list.length === 0" class="lf-empty">
        {{ mode === 'published' ? '暂无发布记录' : '暂无关注内容' }}
      </view>
    </template>

    <!-- 转交弹层：选择招领站 + 备注 -->
    <view v-if="showTransfer" class="transfer-mask" @tap="closeTransfer">
      <view class="transfer-sheet" @tap.stop>
        <view class="sheet-title">转交给招领站</view>
        <text class="lf-muted sheet-sub">
          将拾物「{{ transferItem ? transferItem.title : '' }}」申请转入招领站，由站点管理员审批通过后入库
        </text>
        <picker :range="siteNames" @change="onSiteChange">
          <view class="picker" :class="{ 'picker--active': siteIndex >= 0 }">
            {{ siteIndex >= 0 ? siteNames[siteIndex] : '选择招领站' }}
          </view>
        </picker>
        <textarea
          v-model="transferRemark"
          class="sheet-remark"
          placeholder="转交备注（选填，可说明拾获经过与物品状况）"
          maxlength="200"
        />
        <button
          class="lf-btn sheet-submit"
          :class="{ 'lf-btn--disabled': siteIndex < 0 || submitting }"
          @tap="submitTransfer"
        >{{ submitting ? '提交中…' : '确认提交' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { itemApi, focusApi, matchApi, transferApi, siteApi } from '@/utils/api'
import { createDedupeGuard } from '@/utils/request-cache'

const guard = createDedupeGuard(1200)
const mode = ref('published')
const curType = ref('lost')
const list = ref([])
const matches = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    if (mode.value === 'match') {
      matches.value = await matchApi.mine()
    } else if (mode.value === 'published') {
      const data = await itemApi.mine(curType.value, 1, 50)
      list.value = data.records
    } else {
      const data = await focusApi.mine(curType.value.toUpperCase(), 1, 50)
      list.value = data.records
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const switchType = (t) => {
  curType.value = t
  load()
}
const goDetail = (type, id) => uni.navigateTo({ url: `/pages/detail/detail?type=${type}&id=${id}` })
const act = async (m, status) => {
  await matchApi.setStatus(m.id, status)
  uni.showToast({ title: '已处理', icon: 'none' })
  load()
}

/* ---------------- 转交给招领站 ---------------- */
const TRANSFER_STATUS = {
  NONE: { text: '未转交', cls: 'lf-tag--offline' },
  PENDING: { text: '转交审核中', cls: 'lf-tag--waiting' },
  APPROVED: { text: '已转入站点', cls: 'lf-tag--finished' },
  REJECTED: { text: '转交被驳回', cls: 'lf-tag--offline' },
  CANCELLED: { text: '已撤回转交', cls: 'lf-tag--offline' },
}
const transferText = (s) => (TRANSFER_STATUS[s] || TRANSFER_STATUS.NONE).text
const transferCls = (s) => (TRANSFER_STATUS[s] || TRANSFER_STATUS.NONE).cls
const canTransfer = (it) =>
  ['NONE', 'REJECTED', 'CANCELLED'].includes(it.transferStatus) && it.status !== 'FINISHED'

const showTransfer = ref(false)
const transferItem = ref(null)
const transferRemark = ref('')
const sites = ref([])
const siteIndex = ref(-1)
const submitting = ref(false)
const siteNames = computed(() =>
  sites.value.map((s) => (s.address ? `${s.name}（${s.address}）` : s.name))
)
const selectedSiteId = computed(() => (siteIndex.value >= 0 ? sites.value[siteIndex.value].id : null))

const openTransfer = async (it) => {
  transferItem.value = it
  transferRemark.value = ''
  siteIndex.value = -1
  showTransfer.value = true
  if (!sites.value.length) {
    try {
      const data = await siteApi.page({ page: 1, size: 50 })
      sites.value = data.records || []
    } catch (e) {
      console.error(e)
    }
  }
}
const onSiteChange = (e) => {
  siteIndex.value = Number(e.detail.value)
}
const closeTransfer = () => {
  if (!submitting.value) showTransfer.value = false
}
const submitTransfer = async () => {
  if (siteIndex.value < 0) {
    uni.showToast({ title: '请先选择招领站', icon: 'none' })
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await transferApi.create(transferItem.value.id, {
      siteId: selectedSiteId.value,
      remark: transferRemark.value.trim() || undefined,
    })
    uni.showToast({ title: '已提交，等待站点管理员审批', icon: 'none' })
    showTransfer.value = false
    load()
  } catch (e) {
    console.error(e)
  } finally {
    submitting.value = false
  }
}
const cancelTransfer = (it) => {
  uni.showModal({
    title: '撤回转交申请',
    content: '确定撤回该拾物的转交申请？撤回后可重新发起。',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await transferApi.cancelByItem(it.id)
        uni.showToast({ title: '已撤回', icon: 'none' })
        load()
      } catch (e) {
        console.error(e)
      }
    },
  })
}

onLoad((options) => {
  mode.value = options.mode || 'published'
})
onShow(() => guard.run('list:' + mode.value, load))
</script>

<style scoped>
.list-page {
  padding: 20rpx;
}
.tabs {
  display: flex;
  gap: 14rpx;
  margin-bottom: 20rpx;
}
.tab {
  background: #fff;
  border-radius: 999rpx;
  padding: 14rpx 36rpx;
  font-size: 27rpx;
  color: #6b7280;
  border: 2rpx solid #e5e9f0;
}
.tab.active {
  background: #eaf1fe;
  color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
}
.match-head {
  display: flex;
  align-items: center;
  gap: 18rpx;
}
.score {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #eaf1fe;
  color: #2563eb;
  text-align: center;
  line-height: 88rpx;
  font-weight: 700;
  font-size: 26rpx;
  flex-shrink: 0;
}
.score.high {
  background: #fff3e8;
  color: #f97316;
}
.match-pair {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
}
.pair {
  flex: 1;
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 16rpx;
}
.match-ops {
  display: flex;
  gap: 14rpx;
  margin-top: 18rpx;
}
.transfer-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: -6rpx 4rpx 20rpx;
}
.transfer-btn {
  margin-left: auto;
  padding: 10rpx 26rpx;
  font-size: 24rpx;
  line-height: 1.6;
}
.transfer-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 99;
  display: flex;
  align-items: flex-end;
}
.transfer-sheet {
  width: 100%;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 34rpx 32rpx calc(34rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 700;
}
.sheet-sub {
  display: block;
  margin-top: 10rpx;
  line-height: 1.6;
}
.picker {
  margin-top: 22rpx;
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 20rpx 22rpx;
  font-size: 27rpx;
  color: #6b7280;
}
.picker--active {
  color: #0f172a;
  border-color: #2563eb;
}
.sheet-remark {
  margin-top: 18rpx;
  width: 100%;
  box-sizing: border-box;
  min-height: 140rpx;
  border: 2rpx solid #e5e9f0;
  border-radius: 14rpx;
  padding: 18rpx 22rpx;
  font-size: 27rpx;
}
.sheet-submit {
  margin-top: 24rpx;
  padding: 20rpx 0;
}
.sheet-submit.lf-btn--disabled {
  opacity: 0.55;
}
</style>
