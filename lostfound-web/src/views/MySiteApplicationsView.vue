<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { siteApplicationApi } from '@/api'
import { SITE_APPLICATION_STATUS_MAP } from '@/types'
import type { SiteApplication } from '@/types'
import { errMsg, fmtTime } from '@/utils/format'

const router = useRouter()
const list = ref<SiteApplication[]>([])
const loading = ref(false)
const error = ref('')

const counts = computed(() => ({
  PENDING: list.value.filter((a) => a.status === 'PENDING').length,
  APPROVED: list.value.filter((a) => a.status === 'APPROVED').length,
  REJECTED: list.value.filter((a) => a.status === 'REJECTED').length,
}))
const hasPending = computed(() => counts.value.PENDING > 0)
const statuses = ['PENDING', 'APPROVED', 'REJECTED'] as const

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    list.value = await siteApplicationApi.mine()
  } catch (e) {
    error.value = errMsg(e, '建站申请加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)

const withdraw = async (app: SiteApplication) => {
  try {
    await ElMessageBox.confirm(`撤回后需要重新提交资料，确定撤回「${app.name}」的建站申请？`, '撤回确认', { type: 'warning' })
    await siteApplicationApi.withdraw(app.id)
    ElMessage.success('已撤回，可重新提交申请')
    load()
  } catch (e) {
    if (e instanceof Error && e.message !== 'cancel') ElMessage.error(errMsg(e, '撤回失败'))
  }
}

/** 驳回后带原资料跳回申请页，减少重复填写 */
const reapply = (app: SiteApplication) => {
  router.push({
    path: '/site/apply',
    query: {
      name: app.name, address: app.address, phone: app.phone || '', openTime: app.openTime || '',
      description: app.description || '', reason: app.reason, contact: app.contact || '',
      longitude: app.longitude ?? '', latitude: app.latitude ?? '',
    },
  })
}
</script>

<template>
  <div class="lf-container my-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">招领站共建计划</p>
        <h1 class="lf-page-title">我的建站申请</h1>
        <p class="lf-page-sub">查看审核状态、审核意见，以及审核通过后自动创建的招领站。</p>
      </div>
      <div class="head-ops">
        <el-button round :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" round :disabled="hasPending" @click="router.push('/site/apply')">
          {{ hasPending ? '有待审申请' : '申请创建招领站' }}
        </el-button>
      </div>
    </header>

    <div class="stat-row">
      <div v-for="s in statuses" :key="s" class="lf-card stat-card" :class="`stat-card--${s.toLowerCase()}`">
        <span>{{ SITE_APPLICATION_STATUS_MAP[s].text }}</span>
        <b>{{ counts[s] }}</b>
      </div>
      <div class="lf-card stat-note">
        <b>审核通过后会怎样？</b>
        <p>系统自动创建招领站、把你绑定为该站管理员，并通过站内消息通知；无需再次注册或改角色。</p>
      </div>
    </div>

    <div v-if="loading" class="lf-card state-panel" v-loading="true">正在加载申请…</div>
    <div v-else-if="error" class="lf-card state-panel" role="alert">
      <el-empty :description="error" :image="'/empty.svg'"><el-button round @click="load">重试</el-button></el-empty>
    </div>
    <div v-else-if="!list.length" class="lf-card state-panel">
      <el-empty description="还没有提交过建站申请" :image="'/empty.svg'">
        <p class="empty-hint">注册用户均可申请；审核通过前不会创建站点，也不会改变你的账号权限。</p>
        <el-button type="primary" round @click="router.push('/site/apply')">填写建站申请</el-button>
      </el-empty>
    </div>

    <div v-else class="app-list">
      <article v-for="app in list" :key="app.id" class="lf-card app-card">
        <div class="app-head">
          <div>
            <h2>{{ app.name }}</h2>
            <p>申请编号 #{{ app.id }} · 提交于 {{ fmtTime(app.createTime) }}</p>
          </div>
          <span class="lf-tag" :class="SITE_APPLICATION_STATUS_MAP[app.status]?.cls || 'lf-tag--muted'">
            {{ app.statusLabel || SITE_APPLICATION_STATUS_MAP[app.status]?.text || app.status }}
          </span>
        </div>
        <dl class="app-grid">
          <div><dt>站点地址</dt><dd>{{ app.address }}</dd></div>
          <div><dt>联系电话</dt><dd>{{ app.phone || '未填写' }}</dd></div>
          <div><dt>开放时间</dt><dd>{{ app.openTime || '全天开放' }}</dd></div>
          <div><dt>补充联系方式</dt><dd>{{ app.contact || '未填写' }}</dd></div>
          <div class="app-grid--wide"><dt>申请理由</dt><dd>{{ app.reason }}</dd></div>
        </dl>
        <div v-if="app.status !== 'PENDING'" class="review-box" :class="{ 'review-box--reject': app.status === 'REJECTED' }">
          <div><b>{{ app.status === 'APPROVED' ? '审核通过' : '审核未通过' }}</b><span>{{ fmtTime(app.reviewTime) }}</span></div>
          <p>{{ app.reviewRemark || '管理员未填写审核意见' }}</p>
          <p v-if="app.createdSiteId" class="site-link">已创建招领站 #{{ app.createdSiteId }}</p>
        </div>
        <footer class="app-foot">
          <el-button v-if="app.createdSiteId" size="small" text type="primary" @click="router.push(`/sites/${app.createdSiteId}`)">进入已建站点</el-button>
          <el-button v-if="app.status === 'REJECTED'" size="small" text type="primary" @click="reapply(app)">修改后重新申请</el-button>
          <el-button v-if="app.status === 'PENDING'" size="small" text type="danger" @click="withdraw(app)">撤回申请</el-button>
          <span v-if="app.status === 'PENDING'" class="foot-hint">待审核中，审核结果会通过站内消息通知</span>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.my-page { padding: 34px 20px 56px; }
.eyebrow { font-size: 12px; letter-spacing: 2px; font-weight: 600; color: var(--lf-primary); }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.page-head .lf-page-title { margin-top: 6px; }
.head-ops { display: flex; gap: 10px; }
.stat-row { display: grid; grid-template-columns: repeat(3, minmax(120px, 1fr)) 2fr; gap: 14px; margin: 24px 0 20px; }
.stat-card { padding: 16px 20px; }
.stat-card span { font-size: 12.5px; color: var(--lf-text-3); }
.stat-card b { display: block; margin-top: 4px; font-size: 26px; line-height: 1.2; }
.stat-card--pending b { color: var(--lf-primary); }
.stat-card--approved b { color: var(--lf-success); }
.stat-card--rejected b { color: var(--lf-danger); }
.stat-note { padding: 14px 18px; background: linear-gradient(120deg, #fff, #f2f7ff); }
.stat-note b { font-size: 13px; }
.stat-note p { margin-top: 4px; font-size: 12px; line-height: 1.7; color: var(--lf-text-3); }
.state-panel { padding: 46px 20px; min-height: 220px; }
.empty-hint { margin-bottom: 14px; font-size: 13px; line-height: 1.7; color: var(--lf-text-3); }
.app-list { display: flex; flex-direction: column; gap: 16px; }
.app-card { padding: 22px 24px; }
.app-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.app-head h2 { font-size: 17px; font-weight: 600; }
.app-head p { margin-top: 3px; font-size: 12px; color: var(--lf-text-3); }
.app-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 24px; margin: 18px 0 0; }
.app-grid dt { font-size: 12px; color: var(--lf-text-3); }
.app-grid dd { margin: 3px 0 0; font-size: 13.5px; line-height: 1.7; word-break: break-word; }
.app-grid--wide { grid-column: 1 / -1; }
.review-box { margin-top: 18px; padding: 14px 16px; border-radius: 10px; background: #eaf1fe; border-left: 3px solid var(--lf-primary); }
.review-box--reject { background: #fef2f2; border-left-color: var(--lf-danger); }
.review-box > div { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.review-box > div span { font-size: 12px; color: var(--lf-text-3); }
.review-box p { margin-top: 6px; font-size: 13px; line-height: 1.7; color: var(--lf-text-2); }
.site-link { color: var(--lf-primary) !important; }
.app-foot { display: flex; align-items: center; gap: 8px; margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--lf-border); }
.foot-hint { margin-left: auto; font-size: 12px; color: var(--lf-text-3); }
@media (max-width: 860px) {
  .stat-row { grid-template-columns: repeat(3, 1fr); }
  .stat-note { grid-column: 1 / -1; }
  .app-grid { grid-template-columns: 1fr; }
}
@media (max-width: 520px) {
  .page-head { flex-direction: column; align-items: flex-start; }
  .app-card { padding: 18px 16px; }
  .foot-hint { display: none; }
}
</style>
