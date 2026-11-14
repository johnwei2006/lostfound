<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, User as UserIcon, View } from '@element-plus/icons-vue'
import { itemApi, focusApi, claimApi, authApi, transferApi, siteApplicationApi } from '@/api'
import { SITE_APPLICATION_STATUS_MAP, TRANSFER_STATUS_MAP } from '@/types'
import type { ClaimVO, ItemVO, SiteApplication, TransferVO } from '@/types'
import { useUserStore } from '@/stores/user'
import { openQrLogin } from '@/composables/useQrLogin'
import { errMsg, fmtTime } from '@/utils/format'
import ItemCard from '@/components/ItemCard.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const initialTab = typeof route.query.tab === 'string' ? route.query.tab : 'published'
const tab = ref(['published', 'focused', 'claims', 'transfers', 'settings'].includes(initialTab) ? initialTab : 'published')

const published = ref<ItemVO[]>([])
const focused = ref<ItemVO[]>([])
const claims = ref<ClaimVO[]>([])
const transfers = ref<TransferVO[]>([])
const transferTotal = ref(0)
const transferPage = ref(1)
const transferSize = 20
const loading = ref(false)
const transferError = ref('')

const profileForm = ref({ nickname: '', phone: '', email: '', avatar: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirm: '' })

/** 个人中心只展示最近一条建站申请摘要，完整列表在 /site/applications */
const latestApplication = ref<SiteApplication | null>(null)
const loadLatestApplication = async () => {
  if (!store.isLoggedIn || store.isAdmin) { latestApplication.value = null; return }
  try {
    const apps = await siteApplicationApi.mine()
    latestApplication.value = apps[0] || null
  } catch { latestApplication.value = null }
}
watch([() => store.isLoggedIn, () => store.isAdmin], loadLatestApplication, { immediate: true })

const pendingTransfers = computed(() => transfers.value.filter((t) => t.status === 'PENDING').length)

const loadTransfers = async () => {
  transferError.value = ''
  try {
    const data = await transferApi.mine({ page: transferPage.value, size: transferSize })
    transfers.value = data.records
    transferTotal.value = data.total
  } catch (e) {
    transferError.value = errMsg(e, '转交记录加载失败')
  }
}

const loadTab = async (name: string) => {
  // 游客不发任何需登录的请求：个人资料与所有列表都保持为空
  if (!store.isLoggedIn) {
    published.value = []
    focused.value = []
    claims.value = []
    transfers.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    if (name === 'published') {
      const [f, l] = await Promise.all([itemApi.mine('found', 1, 50), itemApi.mine('lost', 1, 50)])
      published.value = [...l.records, ...f.records]
    } else if (name === 'focused') {
      const [f, l] = await Promise.all([
        focusApi.mine('FOUND', 1, 50),
        focusApi.mine('LOST', 1, 50),
      ])
      focused.value = [...l.records, ...f.records]
    } else if (name === 'claims') {
      claims.value = await claimApi.mine()
    } else if (name === 'transfers') {
      await loadTransfers()
    } else if (name === 'settings') {
      profileForm.value = {
        nickname: store.user?.nickname || '',
        phone: store.user?.phone || '',
        email: store.user?.email || '',
        avatar: store.user?.avatar || '',
      }
    }
  } finally {
    loading.value = false
  }
}
watch(tab, (name) => loadTab(name), { immediate: true })
// 扫码登录成功后立即补齐数据，无需刷新页面
watch(() => store.isLoggedIn, (logged) => {
  if (logged) loadTab(tab.value)
})

const removeItem = async (it: ItemVO) => {
  await ElMessageBox.confirm('确定删除该信息？', '删除确认', { type: 'warning' })
  await itemApi.remove(it.type.toLowerCase() as 'found' | 'lost', it.id)
  ElMessage.success('已删除')
  loadTab('published')
}
const offline = async (it: ItemVO) => {
  await itemApi.changeStatus(it.type.toLowerCase() as 'found' | 'lost', it.id, 'OFFLINE')
  ElMessage.success('已下架')
  loadTab('published')
}
const online = async (it: ItemVO) => {
  await itemApi.changeStatus(it.type.toLowerCase() as 'found' | 'lost', it.id, 'WAITING')
  ElMessage.success('已重新上架')
  loadTab('published')
}

const saveProfile = async () => {
  await authApi.updateProfile(profileForm.value)
  await store.refresh()
  ElMessage.success('资料已更新')
}
const changePwd = async () => {
  if (!pwdForm.value.newPassword) return ElMessage.warning('请输入新密码')
  if (pwdForm.value.newPassword !== pwdForm.value.confirm) return ElMessage.warning('两次密码不一致')
  await authApi.changePassword({ oldPassword: pwdForm.value.oldPassword, newPassword: pwdForm.value.newPassword })
  ElMessage.success('密码已修改')
  pwdForm.value = { oldPassword: '', newPassword: '', confirm: '' }
}

const claimStatusText = computed(() => ({
  PENDING: '待处理',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}))

/* ---------------- 我的转交 ---------------- */
const transferDetail = ref(false)
const activeTransfer = ref<TransferVO | null>(null)
const openTransfer = (t: TransferVO) => { activeTransfer.value = t; transferDetail.value = true }
const cancelTransfer = async (t: TransferVO) => {
  try {
    await ElMessageBox.confirm('撤回后需重新提交申请，确定撤回该转交申请？', '撤回确认', { type: 'warning' })
    await transferApi.cancel(t.id)
    ElMessage.success('已撤回，可重新申请转交')
    transferDetail.value = false
    loadTransfers()
  } catch (e) {
    if (e instanceof Error && e.message !== 'cancel') ElMessage.error(errMsg(e, '撤回失败'))
  }
}
</script>

<template>
  <div class="lf-container profile-page">
    <div class="profile-head lf-card">
      <el-avatar :size="64" :class="{ 'profile-avatar--empty': !store.isLoggedIn }">
        {{ store.isLoggedIn ? (store.user?.nickname?.charAt(0) || 'U') : '' }}
      </el-avatar>
      <div>
        <h2 v-if="store.user?.nickname">{{ store.user.nickname }}</h2>
        <p v-if="store.isLoggedIn">{{ store.user?.username }} · {{ store.isAdmin ? '管理员' : '普通用户' }}</p>
        <p v-else class="profile-hint">登录后查看你的发布、关注与认领记录</p>
      </div>
      <el-button v-if="!store.isLoggedIn" type="primary" round @click="openQrLogin()">微信扫码登录</el-button>
      <el-button v-else-if="store.isAdmin" type="warning" round plain @click="router.push('/admin')">
        进入管理后台
      </el-button>
      <el-button v-else-if="store.isSiteManager" round plain @click="router.push('/manage')">
        招领站管理
      </el-button>
    </div>

    <div v-if="store.isLoggedIn && !store.isAdmin" class="lf-card apply-summary">
      <div class="apply-summary__body">
        <b>招领站共建</b>
        <p v-if="latestApplication">
          最近申请「{{ latestApplication.name }}」
          <span class="lf-tag" :class="SITE_APPLICATION_STATUS_MAP[latestApplication.status]?.cls">
            {{ latestApplication.statusLabel || SITE_APPLICATION_STATUS_MAP[latestApplication.status]?.text }}
          </span>
          · {{ fmtTime(latestApplication.createTime) }}
        </p>
        <p v-else>想为社区、园区或单位建立招领站？提交资料并通过平台审核后，系统会自动建站并开通你的站点管理权限。</p>
      </div>
      <div class="apply-summary__ops">
        <el-button size="small" round @click="router.push('/site/applications')">我的建站申请</el-button>
        <el-button size="small" type="primary" round @click="router.push('/site/apply')">申请创建招领站</el-button>
      </div>
    </div>

    <div v-if="!store.isLoggedIn" class="guest-panel lf-card">
      <div class="guest-panel__icon"><el-icon :size="28"><UserIcon /></el-icon></div>
      <h3>登录后查看个人信息</h3>
      <p>扫码后与小程序使用同一个账号，发布记录、关注与认领会自动同步。</p>
      <div class="guest-panel__tabs" aria-label="登录后可用的栏目">
        <span>我的发布</span>
        <span>我的关注</span>
        <span>我的申请</span>
        <span>我的转交</span>
      </div>
      <el-button type="primary" round @click="openQrLogin()">微信扫码登录</el-button>
      <button type="button" class="guest-panel__alt" @click="router.push('/login')">使用账号密码登录</button>
    </div>

    <el-tabs v-else v-model="tab" class="profile-tabs">
      <el-tab-pane label="我的发布" name="published">
        <div v-if="loading">加载中…</div>
        <div v-else-if="published.length" class="grid">
          <div v-for="it in published" :key="it.id" class="mine-item lf-card">
            <ItemCard :item="it" />
            <div class="mine-ops">
              <el-button v-if="it.status === 'WAITING'" size="small" text type="warning" @click="offline(it)">下架</el-button>
              <el-button v-else-if="it.status === 'OFFLINE'" size="small" text type="success" @click="online(it)">上架</el-button>
              <el-button size="small" text type="danger" @click="removeItem(it)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有发布过信息" :image="'/empty.svg'">
          <el-button type="primary" round @click="router.push('/publish')">去发布</el-button>
        </el-empty>
      </el-tab-pane>

      <el-tab-pane label="我的关注" name="focused">
        <div v-if="loading">加载中…</div>
        <div v-else-if="focused.length" class="grid">
          <ItemCard v-for="it in focused" :key="it.id" :item="it" />
        </div>
        <el-empty v-else description="暂无关注的物品" :image="'/empty.svg'" />
      </el-tab-pane>

      <el-tab-pane label="我的申请" name="claims">
        <div v-if="claims.length" class="claim-list">
          <div v-for="c in claims" :key="c.id" class="claim-item lf-card">
            <div class="claim-head">
              <b>{{ c.itemTitle || (c.itemType === 'FOUND' ? '拾物 #' + c.itemId : '失物 #' + c.itemId) }}</b>
              <span class="lf-tag" :class="{ 'lf-tag--waiting': c.status === 'PENDING', 'lf-tag--finished': c.status === 'APPROVED', 'lf-tag--danger': c.status === 'REJECTED' }">
                {{ claimStatusText[c.status as keyof typeof claimStatusText] }}
              </span>
            </div>
            <p>{{ c.description }}</p>
          </div>
        </div>
        <el-empty v-else description="暂无认领申请记录" :image="'/empty.svg'" />
      </el-tab-pane>

      <el-tab-pane name="transfers">
        <template #label>
          我的转交 <el-badge v-if="pendingTransfers" :value="pendingTransfers" class="tab-badge" />
        </template>
        <div v-if="loading" v-loading="true" class="transfer-loading">正在加载转交记录…</div>
        <div v-else-if="transferError" class="transfer-error" role="alert">
          <el-empty :description="transferError" :image="'/empty.svg'">
            <el-button round :icon="Refresh" @click="loadTransfers">重试</el-button>
          </el-empty>
        </div>
        <template v-else-if="transfers.length">
          <p class="transfer-hint">审批通过后才会在招领站清单展示；归还完成后的进度也会在这里同步。</p>
          <div class="claim-list">
            <button v-for="t in transfers" :key="t.id" type="button" class="transfer-card lf-card" @click="openTransfer(t)">
              <el-image v-if="t.itemImages?.length" :src="t.itemImages[0]" fit="cover" class="transfer-cover" />
              <div v-else class="transfer-cover transfer-cover--empty">无图</div>
              <div class="transfer-main">
                <div class="claim-head">
                  <b>{{ t.itemTitle }}</b>
                  <span class="lf-tag" :class="TRANSFER_STATUS_MAP[t.status]?.cls">{{ TRANSFER_STATUS_MAP[t.status]?.text || t.status }}</span>
                </div>
                <p>目标站点：{{ t.siteName || '#' + t.siteId }} · 提交于 {{ fmtTime(t.createTime) }}</p>
                <p v-if="t.returned" class="transfer-return">已归还（{{ t.returnWay === 'CLAIM' ? '认领审批' : '现场登记' }}） · {{ fmtTime(t.returnTime) }}</p>
                <p v-else-if="t.status === 'APPROVED'" class="transfer-return">已入库，等待认领或现场归还</p>
              </div>
              <el-icon :size="16" class="transfer-arrow"><View /></el-icon>
            </button>
          </div>
          <div v-if="transferTotal > transferSize" class="pager">
            <el-pagination layout="prev, pager, next" :total="transferTotal" :page-size="transferSize" :current-page="transferPage"
              @current-change="(p: number) => { transferPage = p; loadTransfers() }" />
          </div>
        </template>
        <el-empty v-else description="还没有转交过物品" :image="'/empty.svg'">
          <p class="empty-hint">发布拾物时选择「申请转交到招领站」，审批通过后即可由站点统一管理。</p>
          <el-button type="primary" round @click="router.push('/publish?type=found')">去发布拾物</el-button>
        </el-empty>
      </el-tab-pane>

      <el-tab-pane label="账号设置" name="settings">
        <div class="settings-form lf-card">
          <h4>基本资料</h4>
          <el-form label-width="70px" style="max-width: 420px">
            <el-form-item label="昵称"><el-input v-model="profileForm.nickname" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
            <el-form-item label="头像URL"><el-input v-model="profileForm.avatar" placeholder="粘贴图片链接" /></el-form-item>
            <el-button type="primary" round @click="saveProfile">保存资料</el-button>
          </el-form>
          <h4 style="margin-top: 34px">修改密码</h4>
          <el-form label-width="70px" style="max-width: 420px">
            <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="pwdForm.confirm" type="password" show-password /></el-form-item>
            <el-button type="primary" round @click="changePwd">修改密码</el-button>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="transferDetail" title="转交进度" size="480px">
      <template v-if="activeTransfer">
        <div class="detail-summary">
          <b>{{ activeTransfer.itemTitle }}</b>
          <p>{{ activeTransfer.siteName || '#' + activeTransfer.siteId }} · 提交于 {{ fmtTime(activeTransfer.createTime) }}</p>
          <span class="lf-tag" :class="TRANSFER_STATUS_MAP[activeTransfer.status]?.cls">{{ TRANSFER_STATUS_MAP[activeTransfer.status]?.text }}</span>
        </div>
        <dl class="detail-grid">
          <dt>转交备注</dt><dd>{{ activeTransfer.remark || '无' }}</dd>
          <dt>审批人</dt><dd>{{ activeTransfer.reviewerName || '—' }}</dd>
          <dt>审批时间</dt><dd>{{ fmtTime(activeTransfer.reviewTime) }}</dd>
          <dt>审批意见</dt><dd>{{ activeTransfer.status !== 'PENDING' ? activeTransfer.remark || '无' : '待审批' }}</dd>
          <dt>归还进度</dt><dd>{{ activeTransfer.returned ? `已归还（${fmtTime(activeTransfer.returnTime)}）` : '尚未归还' }}</dd>
        </dl>
        <h4 class="timeline-title">状态时间线</h4>
        <el-timeline>
          <el-timeline-item v-for="n in activeTransfer.timeline" :key="n.key"
            :type="n.state === 'reject' ? 'danger' : n.state === 'done' ? 'success' : 'primary'"
            :timestamp="fmtTime(n.time)" placement="top">
            <b>{{ n.title }}</b><p v-if="n.detail">{{ n.detail }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-button v-if="activeTransfer.status === 'PENDING'" type="danger" plain @click="cancelTransfer(activeTransfer)">撤回申请</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.profile-page { padding: 30px 20px 50px; }
.profile-head { display: flex; align-items: center; gap: 18px; padding: 24px; }
.profile-head h2 { font-size: 20px; }
.profile-head p { font-size: 13px; color: var(--lf-text-3); margin-top: 3px; }
.profile-head .profile-hint { color: var(--lf-text-2); }
.profile-head .el-button { margin-left: auto; }
.profile-avatar--empty { background: #eef2f8; color: #94a3b8; }
.profile-tabs { margin-top: 18px; }
.apply-summary { display: flex; align-items: center; gap: 16px; margin-top: 18px; padding: 16px 22px; background: linear-gradient(110deg, #fff, #f2f7ff); }
.apply-summary__body { flex: 1; min-width: 0; }
.apply-summary__body b { font-size: 14px; font-weight: 600; }
.apply-summary__body p { margin-top: 4px; font-size: 12.5px; line-height: 1.7; color: var(--lf-text-2); }
.apply-summary__body .lf-tag { margin: 0 4px; }
.apply-summary__ops { display: flex; gap: 8px; flex-shrink: 0; }
@media (max-width: 620px) { .apply-summary { flex-direction: column; align-items: flex-start; } }
.tab-badge { margin-left: 6px; }
.guest-panel { display: flex; align-items: center; flex-direction: column; gap: 12px; margin-top: 18px; padding: 46px 24px 44px; text-align: center; background: linear-gradient(180deg, #fff 0%, #f8fbff 100%); }
.guest-panel__icon { display: flex; align-items: center; justify-content: center; width: 62px; height: 62px; border-radius: 50%; background: var(--lf-primary-soft); color: var(--lf-primary); }
.guest-panel h3 { font-size: 17px; font-weight: 600; color: var(--lf-text); }
.guest-panel p { max-width: 420px; font-size: 13px; line-height: 1.7; color: var(--lf-text-2); }
.guest-panel__tabs { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin: 6px 0 10px; }
.guest-panel__tabs span { padding: 5px 14px; border: 1px solid var(--lf-border); border-radius: 999px; background: #fff; font-size: 12px; color: var(--lf-text-3); }
.guest-panel__alt { padding: 4px 8px; border: none; background: transparent; color: var(--lf-primary); font-size: 13px; cursor: pointer; transition: color 180ms ease; }
.guest-panel__alt:hover { color: #1d4ed8; }
.guest-panel__alt:focus-visible { outline: 2px solid var(--lf-primary); outline-offset: 2px; border-radius: 4px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 18px; }
.mine-ops { display: flex; justify-content: flex-end; gap: 4px; padding: 6px 10px; background: #fff; border: 1px solid var(--lf-border); border-top: none; border-radius: 0 0 var(--lf-radius) var(--lf-radius); }
.claim-list { display: flex; flex-direction: column; gap: 14px; }
.claim-item { padding: 16px 20px; }
.claim-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; }
.claim-item p { font-size: 13.5px; color: var(--lf-text-2); }
.settings-form { padding: 26px; max-width: 560px; }
.settings-form h4 { font-size: 15px; margin-bottom: 16px; }
.transfer-loading { min-height: 180px; }
.transfer-error { min-height: 200px; }
.transfer-hint { margin-bottom: 16px; font-size: 13px; line-height: 1.7; color: var(--lf-text-3); }
.empty-hint { margin-bottom: 14px; font-size: 13px; line-height: 1.7; color: var(--lf-text-3); }
.transfer-card { display: flex; align-items: center; gap: 14px; width: 100%; padding: 14px 18px; text-align: left; border: 1px solid var(--lf-border); background: #fff; cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease; }
.transfer-card:hover { transform: translateY(-2px); border-color: var(--lf-primary-light); box-shadow: var(--lf-shadow-sm); }
.transfer-card:focus-visible { outline: 2px solid var(--lf-primary); outline-offset: 2px; }
.transfer-cover { width: 58px; height: 58px; flex-shrink: 0; border-radius: 10px; background: var(--lf-bg); }
.transfer-cover--empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--lf-text-3); }
.transfer-main { flex: 1; min-width: 0; }
.transfer-main p { margin-top: 5px; font-size: 12.5px; color: var(--lf-text-3); }
.transfer-return { color: var(--lf-success) !important; }
.transfer-arrow { color: var(--lf-text-3); flex-shrink: 0; }
.pager { display: flex; justify-content: center; margin-top: 20px; }
.detail-summary { padding-bottom: 16px; border-bottom: 1px solid var(--lf-border); }
.detail-summary b { font-size: 16px; word-break: break-all; }
.detail-summary p { margin: 6px 0 12px; font-size: 12.5px; color: var(--lf-text-3); }
.detail-grid { display: grid; grid-template-columns: 84px 1fr; gap: 10px 12px; padding: 18px 0; margin: 0; font-size: 13px; }
.detail-grid dt { color: var(--lf-text-3); }
.detail-grid dd { margin: 0; word-break: break-all; }
.timeline-title { font-size: 14px; font-weight: 600; margin: 8px 0 14px; }
.timeline-title + :deep(.el-timeline) p { font-size: 12.5px; color: var(--lf-text-3); margin-top: 4px; }
@media (max-width: 520px) { .profile-head { flex-wrap: wrap; } .guest-panel { padding: 34px 16px; } }
@media (prefers-reduced-motion: reduce) { .guest-panel__alt, .transfer-card { transition: none; } }
</style>
