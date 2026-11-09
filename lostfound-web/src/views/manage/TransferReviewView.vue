<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { claimApi, siteApi, transferApi } from '@/api'
import { TRANSFER_STATUS_MAP } from '@/types'
import type { ClaimVO, SiteStatsVO, TransferVO } from '@/types'
import { useSiteStore } from '@/stores/site'
import { errMsg, fmtTime } from '@/utils/format'
import SourceInfoDrawer from '@/components/SourceInfoDrawer.vue'

const route = useRoute()
const siteStore = useSiteStore()
const siteId = computed(() => siteStore.currentSiteId)

const tab = ref<'transfer' | 'claim'>(route.query.tab === 'claim' ? 'claim' : 'transfer')
const stats = ref<SiteStatsVO | null>(null)
const status = ref('PENDING')
const keyword = ref('')
const page = ref(1)
const size = 10
const loading = ref(false)
const error = ref('')
const transfers = ref<TransferVO[]>([])
const claims = ref<ClaimVO[]>([])
const total = ref(0)

const statCards = computed(() => [
  { label: '待转交审批', value: stats.value?.pendingTransfers ?? 0, tone: 'warn', tabName: 'transfer' },
  { label: '待认领审批', value: stats.value?.pendingClaims ?? 0, tone: 'primary', tabName: 'claim' },
  { label: '今日已处理', value: stats.value?.todayHandled ?? 0, tone: 'success', tabName: '' },
])

const load = async () => {
  if (!siteId.value) return
  loading.value = true
  error.value = ''
  try {
    const [s, data] = await Promise.all([
      siteApi.stats(siteId.value),
      tab.value === 'transfer'
        ? siteApi.transfers(siteId.value, { status: status.value, keyword: keyword.value, page: page.value, size })
        : siteApi.claims(siteId.value, { status: status.value, page: page.value, size }),
    ])
    stats.value = s
    total.value = data.total
    if (tab.value === 'transfer') transfers.value = data.records as TransferVO[]
    else claims.value = data.records as ClaimVO[]
  } catch (e) {
    error.value = errMsg(e, '审批列表加载失败')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}
watch(siteId, () => { page.value = 1; load() }, { immediate: true })

const search = () => { page.value = 1; load() }
const switchTab = (name: string | number = 'transfer') => {
  tab.value = name === 'claim' ? 'claim' : 'transfer'
  status.value = 'PENDING'
  page.value = 1
  load()
}
const reset = () => { keyword.value = ''; status.value = 'PENDING'; page.value = 1; load() }

/* ---------------- 转交审批 ---------------- */
const detailOpen = ref(false)
const active = ref<TransferVO | null>(null)
const reviewRemark = ref('')
const reviewing = ref(false)
const sourceOpen = ref(false)
const sourceFoundId = ref<number | null>(null)

const openDetail = (row: TransferVO) => {
  active.value = row
  reviewRemark.value = ''
  detailOpen.value = true
}
const reviewTransfer = async (approve: boolean) => {
  if (!active.value) return
  reviewing.value = true
  try {
    await transferApi.review(active.value.id, { approve, remark: reviewRemark.value })
    ElMessage.success(approve ? '已通过，物品已进入站点清单' : '已驳回并通知申请人')
    detailOpen.value = false
    load()
  } catch (e) { ElMessage.error(errMsg(e, '审批失败')) }
  finally { reviewing.value = false }
}
const showSource = () => {
  if (!active.value) return
  sourceFoundId.value = active.value.foundId
  sourceOpen.value = true
}
const cancelTransfer = async (row: TransferVO) => {
  try {
    await transferApi.cancel(row.id)
    ElMessage.success('已撤回申请')
    load()
  } catch (e) { ElMessage.error(errMsg(e)) }
}

/* ---------------- 认领审批 ---------------- */
const reviewClaim = async (row: ClaimVO, approve: boolean) => {
  try {
    await claimApi.review('found', row.id, approve)
    ElMessage.success(approve ? '认领已通过，物品标记完成并登记归还' : '已驳回认领申请')
    load()
  } catch (e) { ElMessage.error(errMsg(e, '审批失败')) }
}
</script>

<template>
  <div class="manage-page">
    <header class="page-head">
      <div>
        <h1>转交与认领审批</h1>
        <p>{{ siteStore.currentSite?.name || '—' }} · 审批结果会同步通知申请人或转交者</p>
      </div>
      <el-button text :icon="Refresh" @click="load">刷新</el-button>
    </header>

    <div class="stat-grid">
      <button v-for="c in statCards" :key="c.label" type="button" class="stat-card lf-card" :class="`stat-card--${c.tone}`"
        @click="c.tabName && switchTab(c.tabName)">
        <span class="stat-value">{{ c.value }}</span>
        <span class="stat-label">{{ c.label }}</span>
      </button>
    </div>

    <section class="lf-card panel">
      <el-tabs v-model="tab" @tab-change="switchTab">
        <el-tab-pane label="转交申请" name="transfer" />
        <el-tab-pane label="认领申请" name="claim" />
      </el-tabs>

      <div class="filter-bar">
        <el-select v-model="status" style="width: 140px" @change="search">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已撤回" value="CANCELLED" />
          <el-option label="全部" value="ALL" />
        </el-select>
        <el-input v-if="tab === 'transfer'" v-model="keyword" placeholder="物品名称" clearable style="width: 200px"
          :prefix-icon="Search" @keyup.enter="search" />
        <el-button type="primary" plain @click="search">查询</el-button>
        <el-button text :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <div v-if="error" class="error-strip" role="alert">{{ error }} <el-button text type="primary" @click="load">重新加载</el-button></div>

      <el-table v-if="tab === 'transfer'" :data="transfers" v-loading="loading" row-key="id">
        <el-table-column label="物品" min-width="220">
          <template #default="{ row }">
            <div class="item-cell">
              <el-image v-if="row.itemImages?.length" :src="row.itemImages[0]" fit="cover" class="thumb" />
              <div v-else class="thumb thumb--empty">无图</div>
              <div class="item-cell__text"><b :title="row.itemTitle">{{ row.itemTitle }}</b><span>#{{ row.foundId }} · {{ row.siteName }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="转交人" width="130"><template #default="{ row }">{{ row.fromNickname || '用户' + row.fromUserId }}</template></el-table-column>
        <el-table-column label="提交时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="lf-tag" :class="TRANSFER_STATUS_MAP[row.status]?.cls">{{ TRANSFER_STATUS_MAP[row.status]?.text || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" text type="primary" @click="openDetail(row)">审批</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" text @click="cancelTransfer(row)">代撤回</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无转交申请" :image="'/empty.svg'" /></template>
      </el-table>

      <el-table v-else :data="claims" v-loading="loading" row-key="id">
        <el-table-column label="物品" min-width="180"><template #default="{ row }"><b>{{ row.itemTitle || '#' + row.itemId }}</b></template></el-table-column>
        <el-table-column label="申请人" width="120"><template #default="{ row }">{{ row.nickname || '用户' + row.userId }}</template></el-table-column>
        <el-table-column label="认领说明" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.description }}</template></el-table-column>
        <el-table-column label="联系方式" width="140"><template #default="{ row }">{{ row.contact || '未填写' }}</template></el-table-column>
        <el-table-column label="提交时间" width="150"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="lf-tag" :class="row.status === 'PENDING' ? 'lf-tag--claimed' : row.status === 'APPROVED' ? 'lf-tag--finished' : 'lf-tag--danger'">
              {{ row.status === 'PENDING' ? '待处理' : row.status === 'APPROVED' ? '已通过' : '已驳回' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" text type="success" @click="reviewClaim(row, true)">通过</el-button>
              <el-button size="small" text type="danger" @click="reviewClaim(row, false)">驳回</el-button>
            </template>
            <span v-else class="done-text">已处理</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无认领申请" :image="'/empty.svg'" /></template>
      </el-table>

      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </section>

    <el-drawer v-model="detailOpen" title="转交申请详情" size="520px">
      <template v-if="active">
        <div class="detail-head">
          <el-image v-if="active.itemImages?.length" :src="active.itemImages[0]" fit="cover" class="detail-cover" />
          <div class="detail-head__text">
            <h3>{{ active.itemTitle }}</h3>
            <p>#{{ active.foundId }} · {{ active.itemCategory || '未分类' }}</p>
            <span class="lf-tag" :class="TRANSFER_STATUS_MAP[active.status]?.cls">{{ TRANSFER_STATUS_MAP[active.status]?.text }}</span>
          </div>
        </div>
        <dl class="detail-grid">
          <dt>目标站点</dt><dd>{{ active.siteName || '—' }}</dd>
          <dt>转交人</dt><dd>{{ active.fromNickname || '用户' + active.fromUserId }}</dd>
          <dt>提交时间</dt><dd>{{ fmtTime(active.createTime) }}</dd>
          <dt>转交备注</dt><dd>{{ active.remark || '无' }}</dd>
          <dt>审批人</dt><dd>{{ active.reviewerName || '—' }}</dd>
          <dt>审批时间</dt><dd>{{ fmtTime(active.reviewTime) }}</dd>
        </dl>
        <div class="timeline">
          <h4>状态时间线</h4>
          <el-timeline>
            <el-timeline-item v-for="n in active.timeline" :key="n.key"
              :type="n.state === 'reject' ? 'danger' : n.state === 'done' ? 'success' : 'primary'"
              :timestamp="fmtTime(n.time)" placement="top">
              <b>{{ n.title }}</b><p v-if="n.detail">{{ n.detail }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>
        <el-button text type="primary" @click="showSource">查看完整源头信息</el-button>
        <template v-if="active.status === 'PENDING'">
          <h4 class="review-title">审批意见</h4>
          <el-input v-model="reviewRemark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="驳回时建议说明原因，会通知转交人" />
          <div class="drawer-actions">
            <el-button type="danger" plain :loading="reviewing" @click="reviewTransfer(false)">驳回</el-button>
            <el-button type="primary" :loading="reviewing" @click="reviewTransfer(true)">通过并入库</el-button>
          </div>
        </template>
      </template>
    </el-drawer>

    <SourceInfoDrawer v-model="sourceOpen" :found-id="sourceFoundId" />
  </div>
</template>

<style scoped>
.manage-page { max-width: 1280px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-head h1 { font-size: 20px; font-weight: 600; }
.page-head p { margin-top: 6px; font-size: 13px; color: var(--lf-text-3); }
.stat-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat-card { display: flex; flex-direction: column; gap: 6px; padding: 18px 20px; text-align: left; border: 1px solid var(--lf-border); cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease; }
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--lf-shadow-md); }
.stat-card:focus-visible { outline: 2px solid var(--lf-primary); outline-offset: 2px; }
.stat-value { font-size: 26px; font-weight: 650; color: var(--lf-text); }
.stat-label { font-size: 12.5px; color: var(--lf-text-3); }
.stat-card--primary .stat-value { color: var(--lf-primary); }
.stat-card--warn .stat-value { color: var(--lf-accent); }
.stat-card--success .stat-value { color: var(--lf-success); }
.panel { padding: 8px 18px 18px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; margin: 8px 0 16px; }
.error-strip { display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 14px; border-radius: 10px; background: #fef2f2; color: var(--lf-danger); font-size: 13px; }
.item-cell { display: flex; align-items: center; gap: 12px; min-width: 0; }
.thumb { width: 44px; height: 44px; flex-shrink: 0; border-radius: 8px; background: var(--lf-bg); }
.thumb--empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--lf-text-3); }
.item-cell__text { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.item-cell__text b { font-size: 13.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-cell__text span { font-size: 12px; color: var(--lf-text-3); }
.done-text { font-size: 12px; color: var(--lf-text-3); }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-head { display: flex; gap: 14px; padding-bottom: 18px; border-bottom: 1px solid var(--lf-border); }
.detail-cover { width: 84px; height: 84px; border-radius: 10px; flex-shrink: 0; }
.detail-head__text { min-width: 0; }
.detail-head__text h3 { font-size: 16px; font-weight: 600; word-break: break-all; }
.detail-head__text p { margin: 6px 0 10px; font-size: 12px; color: var(--lf-text-3); }
.detail-grid { display: grid; grid-template-columns: 88px 1fr; gap: 10px 12px; font-size: 13px; padding: 18px 0; margin: 0; }
.detail-grid dt { color: var(--lf-text-3); }
.detail-grid dd { margin: 0; word-break: break-all; }
.timeline { padding: 8px 0 16px; }
.timeline h4, .review-title { font-size: 14px; font-weight: 600; margin: 12px 0; }
.timeline p { font-size: 12.5px; color: var(--lf-text-3); margin-top: 4px; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
