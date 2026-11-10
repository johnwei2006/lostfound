<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { siteApplicationApi } from '@/api'
import { SITE_APPLICATION_STATUS_MAP } from '@/types'
import type { SiteApplication, SiteApplicationStatus } from '@/types'
import { errMsg, fmtTime } from '@/utils/format'

/** 表格插槽 row 为 any，统一经此函数收窄状态键类型 */
const statusMeta = (status: string) =>
  SITE_APPLICATION_STATUS_MAP[status as SiteApplicationStatus] ?? { text: status, cls: 'lf-tag--muted' }

const list = ref<SiteApplication[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const keyword = ref('')
const loading = ref(false)
const counts = ref({ PENDING: 0, APPROVED: 0, REJECTED: 0 })

const detailOpen = ref(false)
const active = ref<SiteApplication | null>(null)
const remark = ref('')
const reviewing = ref(false)
const statuses = ['PENDING', 'APPROVED', 'REJECTED'] as const

const load = async () => {
  loading.value = true
  try {
    const data = await siteApplicationApi.page({
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value, size,
    })
    list.value = data.records
    total.value = data.total
  } catch (e) { ElMessage.error(errMsg(e, '申请列表加载失败')) }
  finally { loading.value = false }
}

const loadCounts = async () => {
  try {
    const [p, a, r] = await Promise.all([
      siteApplicationApi.page({ status: 'PENDING', page: 1, size: 1 }),
      siteApplicationApi.page({ status: 'APPROVED', page: 1, size: 1 }),
      siteApplicationApi.page({ status: 'REJECTED', page: 1, size: 1 }),
    ])
    counts.value = { PENDING: p.total, APPROVED: a.total, REJECTED: r.total }
  } catch { /* 统计失败不阻塞主列表 */ }
}

const reload = () => { load(); loadCounts() }
onMounted(reload)

const search = () => { page.value = 1; load() }
const reset = () => { status.value = ''; keyword.value = ''; page.value = 1; load() }
const switchStatus = (s: string) => { status.value = s; page.value = 1; load() }

const openDetail = (row: SiteApplication) => {
  active.value = row
  remark.value = ''
  detailOpen.value = true
}

const review = async (approve: boolean) => {
  if (!active.value || reviewing.value) return
  const text = remark.value.trim()
  if (!approve && !text) return ElMessage.warning('请填写驳回意见，申请人会看到这段说明')
  reviewing.value = true
  try {
    const siteId = await siteApplicationApi.review(active.value.id, { approve, remark: text || undefined })
    ElMessage.success(approve
      ? `审核通过，已创建招领站 #${siteId} 并绑定申请人为管理员，站内消息已发送`
      : '已驳回申请并通知申请人')
    detailOpen.value = false
    reload()
  } catch (e) { ElMessage.error(errMsg(e, '审核失败')) }
  finally { reviewing.value = false }
}
</script>

<template>
  <div class="review-page">
    <header class="page-head">
      <div>
        <h2>建站申请审核</h2>
        <p>审核通过后系统自动创建招领站、绑定申请人为站点管理员并发送站内通知；驳回不会创建任何站点。</p>
      </div>
      <el-button text :icon="Refresh" @click="reload">刷新</el-button>
    </header>

    <div class="stat-row">
      <button v-for="s in statuses" :key="s" type="button"
        class="lf-card stat-card" :class="[`stat-card--${s.toLowerCase()}`, { 'stat-card--active': status === s }]"
        @click="switchStatus(s)">
        <span>{{ SITE_APPLICATION_STATUS_MAP[s].text }}</span>
        <b>{{ counts[s] }}</b>
        <small>点击筛选</small>
      </button>
      <button type="button" class="lf-card stat-card" :class="{ 'stat-card--active': status === '' }" @click="switchStatus('')">
        <span>全部申请</span><b>{{ counts.PENDING + counts.APPROVED + counts.REJECTED }}</b><small>点击清除筛选</small>
      </button>
    </div>

    <el-card shadow="never" class="table-card">
      <div class="filter-bar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="search">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="keyword" placeholder="站点名称 / 地址 / 理由 / 联系方式" clearable :prefix-icon="Search"
          style="width: 280px" @keyup.enter="search" />
        <el-button type="primary" plain @click="search">查询</el-button>
        <el-button text @click="reset">重置</el-button>
      </div>

      <el-table :data="list" v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="申请人" min-width="130">
          <template #default="{ row }">{{ row.applicantNickname || '用户#' + row.applicantUserId }} <small>#{{ row.applicantUserId }}</small></template>
        </el-table-column>
        <el-table-column prop="name" label="拟建站点" min-width="160" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contact" label="联系方式" min-width="130" show-overflow-tooltip />
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="lf-tag" :class="statusMeta(row.status).cls">
              {{ row.statusLabel || statusMeta(row.status).text }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">{{ row.status === 'PENDING' ? '审核' : '详情' }}</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无符合条件的建站申请" :image="'/empty.svg'" /></template>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </el-card>

    <el-drawer v-model="detailOpen" title="建站申请详情" size="520px">
      <template v-if="active">
        <div class="drawer-head">
          <div><h3>{{ active.name }}</h3><p>申请 #{{ active.id }} · {{ fmtTime(active.createTime) }}</p></div>
          <span class="lf-tag" :class="SITE_APPLICATION_STATUS_MAP[active.status]?.cls">{{ active.statusLabel }}</span>
        </div>
        <dl class="detail-grid">
          <dt>申请人</dt><dd>{{ active.applicantNickname || '用户#' + active.applicantUserId }}（#{{ active.applicantUserId }}）</dd>
          <dt>站点地址</dt><dd>{{ active.address }}</dd>
          <dt>坐标</dt><dd>{{ active.longitude && active.latitude ? `${active.longitude}, ${active.latitude}` : '未填写' }}</dd>
          <dt>联系电话</dt><dd>{{ active.phone || '未填写' }}</dd>
          <dt>开放时间</dt><dd>{{ active.openTime || '全天开放' }}</dd>
          <dt>补充联系方式</dt><dd>{{ active.contact || '未填写' }}</dd>
          <dt>站点简介</dt><dd>{{ active.description || '未填写' }}</dd>
          <dt>申请理由</dt><dd>{{ active.reason }}</dd>
        </dl>
        <div v-if="active.status !== 'PENDING'" class="review-record">
          <b>{{ active.status === 'APPROVED' ? '审核通过' : '审核驳回' }} · {{ fmtTime(active.reviewTime) }}</b>
          <p>{{ active.reviewRemark || '未填写审核意见' }}</p>
          <p v-if="active.createdSiteId">已创建招领站 #{{ active.createdSiteId }}</p>
        </div>
        <div v-else class="review-form">
          <h4>审核意见</h4>
          <p class="hint">通过后立即建站并绑定申请人为管理员；驳回时意见会随站内通知发送给申请人。</p>
          <el-input v-model="remark" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="通过可留空或写一句说明；驳回必须填写原因" />
          <div class="review-ops">
            <el-button type="danger" plain :loading="reviewing" @click="review(false)">驳回申请</el-button>
            <el-button type="primary" :loading="reviewing" @click="review(true)">通过并自动建站</el-button>
          </div>
          <p class="hint">如申请人账号已停用或仍有其它待审申请，服务端会拒绝本次通过并保持原状态。</p>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.review-page { max-width: 1200px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-head h2 { font-size: 20px; font-weight: 600; }
.page-head p { margin-top: 6px; font-size: 13px; line-height: 1.7; color: var(--lf-text-3); }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 18px; }
.stat-card { padding: 16px 18px; text-align: left; cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; }
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--lf-shadow-md); }
.stat-card:focus-visible { outline: 2px solid var(--lf-primary); outline-offset: 2px; }
.stat-card--active { border-color: #bcd4fb; background: linear-gradient(130deg, #fff, #f2f7ff); }
.stat-card span { font-size: 12.5px; color: var(--lf-text-3); }
.stat-card b { display: block; margin-top: 2px; font-size: 25px; line-height: 1.3; color: var(--lf-text); }
.stat-card--pending b { color: var(--lf-primary); }
.stat-card--approved b { color: var(--lf-success); }
.stat-card--rejected b { color: var(--lf-danger); }
.stat-card small { font-size: 11px; color: var(--lf-text-3); }
.table-card { border-radius: var(--lf-radius); }
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 8px 10px 16px; }
.pager { display: flex; justify-content: flex-end; margin: 16px 10px 4px; }
.drawer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid var(--lf-border); }
.drawer-head h3 { font-size: 17px; font-weight: 600; }
.drawer-head p { margin-top: 4px; font-size: 12px; color: var(--lf-text-3); }
.detail-grid { display: grid; grid-template-columns: 96px 1fr; gap: 10px 14px; padding: 18px 0; margin: 0; font-size: 13px; }
.detail-grid dt { color: var(--lf-text-3); }
.detail-grid dd { margin: 0; line-height: 1.7; word-break: break-word; white-space: pre-wrap; }
.review-record { padding: 14px 16px; border-radius: 10px; background: var(--lf-bg); font-size: 13px; line-height: 1.8; }
.review-record p { color: var(--lf-text-2); }
.review-form { padding-top: 18px; border-top: 1px dashed var(--lf-border); }
.review-form h4 { font-size: 14px; font-weight: 600; }
.hint { margin: 6px 0 12px; font-size: 12px; line-height: 1.7; color: var(--lf-text-3); }
.review-ops { display: flex; justify-content: flex-end; gap: 10px; margin: 16px 0; }
@media (max-width: 860px) { .stat-row { grid-template-columns: repeat(2, 1fr); } }
@media (prefers-reduced-motion: reduce) { .stat-card { transition: none; } .stat-card:hover { transform: none; } }
</style>
