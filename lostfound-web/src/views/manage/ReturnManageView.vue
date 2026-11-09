<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import { claimApi, fileApi, siteApi } from '@/api'
import type { ClaimVO, ItemVO, ReturnRecordVO } from '@/types'
import { useSiteStore } from '@/stores/site'
import { errMsg, fmtTime } from '@/utils/format'

const route = useRoute()
const siteStore = useSiteStore()
const siteId = computed(() => siteStore.currentSiteId)

const items = ref<ItemVO[]>([])
const claims = ref<ClaimVO[]>([])
const records = ref<ReturnRecordVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const error = ref('')
const result = ref('')
const submitting = ref(false)

const form = ref({ foundId: undefined as number | undefined, receiverName: '', receiverPhone: '', remark: '' })
const evidence = ref<{ name: string; url: string }[]>([])
const previewOpen = ref(false)
const previewUrl = ref('')

const load = async () => {
  if (!siteId.value) return
  loading.value = true
  error.value = ''
  try {
    const [itemPage, claimPage, returnPage] = await Promise.all([
      siteApi.items(siteId.value, { manage: true, page: 1, size: 50 }),
      siteApi.claims(siteId.value, { status: 'PENDING', page: 1, size: 10 }),
      siteApi.returns(siteId.value, { page: page.value, size }),
    ])
    items.value = itemPage.records.filter((it) => it.status !== 'FINISHED')
    claims.value = claimPage.records
    records.value = returnPage.records
    total.value = returnPage.total
    if (route.query.foundId && items.value.some((it) => it.id === Number(route.query.foundId))) {
      form.value.foundId = Number(route.query.foundId)
    }
  } catch (e) {
    error.value = errMsg(e, '归还数据加载失败')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}
watch(siteId, () => { page.value = 1; load() }, { immediate: true })

const uploadEvidence = async (options: { file: File; onSuccess: () => void; onError: (e: Error) => void }) => {
  try {
    const { url } = await fileApi.upload(options.file)
    evidence.value.push({ name: options.file.name, url })
    options.onSuccess()
  } catch (e) { options.onError(e as Error); ElMessage.error(errMsg(e, '凭证上传失败')) }
}

const submitReturn = async () => {
  if (!form.value.foundId) return ElMessage.warning('请选择要归还的物品')
  if (!form.value.receiverName.trim()) return ElMessage.warning('请填写领取人')
  if (!siteId.value) return
  submitting.value = true
  result.value = ''
  try {
    await siteApi.registerReturn(siteId.value, {
      foundId: form.value.foundId,
      receiverName: form.value.receiverName.trim(),
      receiverPhone: form.value.receiverPhone,
      evidence: evidence.value.map((e) => e.url),
      remark: form.value.remark,
    })
    result.value = '归还登记成功，物品已标记为已完成，并已通知原始登记人。'
    ElMessage.success('归还登记成功')
    form.value = { foundId: undefined, receiverName: '', receiverPhone: '', remark: '' }
    evidence.value = []
    await load()
  } catch (e) { ElMessage.error(errMsg(e, '登记失败')) }
  finally { submitting.value = false }
}

const reviewClaim = async (row: ClaimVO, approve: boolean) => {
  try {
    await claimApi.review('found', row.id, approve)
    ElMessage.success(approve ? '认领已通过，已自动登记归还' : '认领已驳回')
    load()
  } catch (e) { ElMessage.error(errMsg(e, '审批失败')) }
}

const showPreview = (url: string) => { previewUrl.value = url; previewOpen.value = true }
</script>

<template>
  <div class="manage-page">
    <header class="page-head">
      <div>
        <h1>归还管理</h1>
        <p>{{ siteStore.currentSite?.name || '—' }} · 认领审批通过或现场登记都会生成归还记录</p>
      </div>
      <el-button text :icon="Refresh" @click="load">刷新</el-button>
    </header>

    <el-alert v-if="result" type="success" show-icon :closable="true" class="result-alert" :title="result" @close="result = ''" />
    <div v-if="error" class="error-strip" role="alert">{{ error }} <el-button text type="primary" @click="load">重新加载</el-button></div>

    <div class="two-col">
      <section class="lf-card panel">
        <h2 class="panel-title">现场归还登记</h2>
        <p class="panel-sub">适用于失主直接到站领取、无需线上认领审批的场景。</p>
        <el-form label-width="88px" v-loading="loading">
          <el-form-item label="归还物品" required>
            <el-select v-model="form.foundId" filterable placeholder="搜索本站物品" style="width: 100%">
              <el-option v-for="it in items" :key="it.id" :label="`#${it.id} ${it.title}`" :value="it.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="领取人" required><el-input v-model="form.receiverName" maxlength="50" placeholder="领取人姓名" /></el-form-item>
          <el-form-item label="联系方式"><el-input v-model="form.receiverPhone" maxlength="30" placeholder="手机号 / 证件后四位" /></el-form-item>
          <el-form-item label="归还凭证">
            <el-upload list-type="picture-card" :file-list="evidence" :http-request="uploadEvidence as any" :limit="6" accept="image/*"
              :on-remove="(_: any, list: any[]) => (evidence = list.map((f: any) => ({ name: f.name, url: f.url })))">
              <el-icon :size="20"><UploadFilled /></el-icon>
            </el-upload>
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="交接说明、证件核验情况等" /></el-form-item>
          <el-form-item>
            <el-button type="primary" round :loading="submitting" @click="submitReturn">确认归还</el-button>
          </el-form-item>
        </el-form>
      </section>

      <section class="lf-card panel">
        <h2 class="panel-title">待审认领队列 <span class="count-pill">{{ claims.length }}</span></h2>
        <p class="panel-sub">审批通过后自动写入归还记录并通知双方。</p>
        <div v-if="!claims.length" class="queue-empty">当前没有待审批的认领申请。</div>
        <div v-for="c in claims" :key="c.id" class="queue-item">
          <div class="queue-item__main">
            <b>{{ c.itemTitle || '#' + c.itemId }}</b>
            <p>申请人：{{ c.nickname || '用户' + c.userId }} · {{ fmtTime(c.createTime) }}</p>
            <p class="queue-desc">{{ c.description }}</p>
            <p v-if="c.contact" class="queue-contact">联系方式：{{ c.contact }}</p>
          </div>
          <div class="queue-item__ops">
            <el-button size="small" text type="success" @click="reviewClaim(c, true)">通过</el-button>
            <el-button size="small" text type="danger" @click="reviewClaim(c, false)">驳回</el-button>
          </div>
        </div>
      </section>
    </div>

    <section class="lf-card panel" style="margin-top: 18px">
      <h2 class="panel-title">归还记录</h2>
      <el-table :data="records" v-loading="loading" row-key="id">
        <el-table-column label="物品" min-width="180"><template #default="{ row }"><b>{{ row.itemTitle }}</b><span class="sub">#{{ row.foundId }}</span></template></el-table-column>
        <el-table-column label="归还方式" width="120">
          <template #default="{ row }">
            <span class="lf-tag" :class="row.way === 'CLAIM' ? 'lf-tag--waiting' : 'lf-tag--finished'">
              {{ row.way === 'CLAIM' ? '认领审批' : '现场登记' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="领取人" width="140"><template #default="{ row }">{{ row.receiverName || '—' }}</template></el-table-column>
        <el-table-column label="归还时间" width="160"><template #default="{ row }">{{ fmtTime(row.returnTime) }}</template></el-table-column>
        <el-table-column label="操作人" width="130"><template #default="{ row }">{{ row.operatorName || '—' }}</template></el-table-column>
        <el-table-column label="凭证" width="90">
          <template #default="{ row }">
            <el-button v-if="row.evidence?.length" size="small" text type="primary" :icon="View" @click="showPreview(row.evidence[0])">查看</el-button>
            <span v-else class="sub">无</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无归还记录" :image="'/empty.svg'" /></template>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </section>

    <el-dialog v-model="previewOpen" title="归还凭证" width="520px">
      <el-image :src="previewUrl" fit="contain" style="width: 100%" />
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-page { max-width: 1280px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-head h1 { font-size: 20px; font-weight: 600; }
.page-head p { margin-top: 6px; font-size: 13px; color: var(--lf-text-3); }
.result-alert { margin-bottom: 16px; border-radius: 10px; }
.error-strip { display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 14px; border-radius: 10px; background: #fef2f2; color: var(--lf-danger); font-size: 13px; }
.two-col { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: 18px; }
.panel { padding: 20px; }
.panel-title { display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 600; }
.panel-sub { margin: 6px 0 18px; font-size: 12.5px; color: var(--lf-text-3); }
.count-pill { display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 20px; padding: 0 6px; border-radius: 999px; background: var(--lf-primary-soft); color: var(--lf-primary); font-size: 12px; }
.queue-empty { padding: 32px 0; text-align: center; font-size: 13px; color: var(--lf-text-3); }
.queue-item { display: flex; gap: 12px; justify-content: space-between; padding: 14px; margin-bottom: 10px; border: 1px solid var(--lf-border); border-radius: 10px; transition: border-color 180ms ease, background 180ms ease; }
.queue-item:hover { border-color: var(--lf-primary-light); background: #fafcff; }
.queue-item__main { min-width: 0; }
.queue-item__main b { font-size: 13.5px; word-break: break-all; }
.queue-item__main p { margin-top: 4px; font-size: 12px; color: var(--lf-text-3); }
.queue-desc { line-height: 1.6; word-break: break-all; }
.queue-contact { color: var(--lf-primary) !important; }
.queue-item__ops { display: flex; flex-direction: column; flex-shrink: 0; }
.sub { margin-left: 8px; font-size: 12px; color: var(--lf-text-3); font-weight: 400; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 1100px) { .two-col { grid-template-columns: 1fr; } }
</style>
