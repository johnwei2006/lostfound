<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { fileApi, itemApi, siteApi } from '@/api'
import { CATEGORIES, SOURCE_TYPE_MAP, STATUS_MAP, TRANSFER_STATUS_MAP } from '@/types'
import type { ItemVO, SiteStatsVO } from '@/types'
import { useSiteStore } from '@/stores/site'
import { errMsg, fmtTime } from '@/utils/format'
import SourceInfoDrawer from '@/components/SourceInfoDrawer.vue'

const router = useRouter()
const siteStore = useSiteStore()
const siteId = computed(() => siteStore.currentSiteId)

const rows = ref<ItemVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)
const error = ref('')
const stats = ref<SiteStatsVO | null>(null)
const filters = ref({ keyword: '', category: '', status: '', sourceType: '' })

const statCards = computed(() => [
  { label: '待审批转交', value: stats.value?.pendingTransfers ?? 0, tone: 'warn', to: '/manage/transfers' },
  { label: '在站物品', value: stats.value?.siteItems ?? 0, tone: 'primary', to: '' },
  { label: '已归还', value: stats.value?.returnedItems ?? 0, tone: 'success', to: '/manage/returns' },
  { label: '今日已处理', value: stats.value?.todayHandled ?? 0, tone: 'muted', to: '' },
])

const load = async () => {
  if (!siteId.value) return
  loading.value = true
  error.value = ''
  try {
    const [p, s] = await Promise.all([
      siteApi.items(siteId.value, { ...filters.value, manage: true, page: page.value, size }),
      siteApi.stats(siteId.value),
    ])
    rows.value = p.records
    total.value = p.total
    stats.value = s
  } catch (e) {
    error.value = errMsg(e, '站点物品加载失败')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}
watch(siteId, () => {
  page.value = 1
  load()
}, { immediate: true })

const search = () => { page.value = 1; load() }
const reset = () => {
  filters.value = { keyword: '', category: '', status: '', sourceType: '' }
  search()
}

const toggleStatus = async (row: ItemVO) => {
  const next = row.status === 'OFFLINE' ? 'WAITING' : 'OFFLINE'
  try {
    await itemApi.changeStatus('found', row.id, next)
    ElMessage.success(next === 'OFFLINE' ? '已下架' : '已重新上架')
    load()
  } catch (e) { ElMessage.error(errMsg(e)) }
}

const sourceOpen = ref(false)
const sourceFoundId = ref<number | null>(null)
const openSource = (row: ItemVO) => { sourceFoundId.value = row.id; sourceOpen.value = true }

const goReturn = (row: ItemVO) => router.push({ path: '/manage/returns', query: { foundId: String(row.id) } })

/* ---------------- 站点代发布 ---------------- */
const publishOpen = ref(false)
const publishing = ref(false)
const publishForm = ref({ title: '', category: '', description: '', contact: '', itemTime: '', images: [] as string[] })
const openPublish = () => {
  publishForm.value = { title: '', category: '', description: '', contact: '', itemTime: '', images: [] }
  publishOpen.value = true
}
const uploadImage = async (options: { file: File; onSuccess: () => void; onError: (e: Error) => void }) => {
  try {
    const { url } = await fileApi.upload(options.file)
    publishForm.value.images.push(url)
    options.onSuccess()
  } catch (e) { options.onError(e as Error); ElMessage.error(errMsg(e, '上传失败')) }
}
const submitPublish = async () => {
  if (!publishForm.value.title.trim()) return ElMessage.warning('请填写物品名称')
  if (!publishForm.value.category) return ElMessage.warning('请选择物品类别')
  if (!siteId.value) return
  publishing.value = true
  try {
    await siteApi.publishItem(siteId.value, {
      ...publishForm.value,
      itemTime: publishForm.value.itemTime ? dayjs(publishForm.value.itemTime).format('YYYY-MM-DD HH:mm:ss') : null,
    })
    ElMessage.success('已发布到本站清单')
    publishOpen.value = false
    load()
  } catch (e) { ElMessage.error(errMsg(e, '发布失败')) }
  finally { publishing.value = false }
}
</script>

<template>
  <div class="manage-page">
    <header class="page-head">
      <div>
        <h1>站点物品</h1>
        <p>{{ siteStore.currentSite?.name || '—' }} · 仅展示站点发布与转交审批通过的物品</p>
      </div>
      <el-button type="primary" round :icon="Plus" @click="openPublish">发布物品</el-button>
    </header>

    <div class="stat-grid">
      <button
        v-for="c in statCards"
        :key="c.label"
        type="button"
        class="stat-card lf-card"
        :class="`stat-card--${c.tone}`"
        @click="c.to && router.push(c.to)"
      >
        <span class="stat-value">{{ c.value }}</span>
        <span class="stat-label">{{ c.label }}</span>
      </button>
    </div>

    <section class="lf-card panel">
      <div class="filter-bar">
        <el-input v-model="filters.keyword" placeholder="物品名称 / 描述" clearable style="width: 220px" :prefix-icon="Search" @keyup.enter="search" />
        <el-select v-model="filters.category" placeholder="类别" clearable style="width: 130px">
          <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="待认领" value="WAITING" /><el-option label="已完成" value="FINISHED" /><el-option label="已下架" value="OFFLINE" />
        </el-select>
        <el-select v-model="filters.sourceType" placeholder="来源" clearable style="width: 140px">
          <el-option v-for="(text, key) in SOURCE_TYPE_MAP" :key="key" :label="text" :value="key" />
        </el-select>
        <el-button type="primary" plain @click="search">查询</el-button>
        <el-button text :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <div v-if="error" class="error-strip" role="alert">
        {{ error }} <el-button text type="primary" @click="load">重新加载</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id" class="item-table">
        <el-table-column label="物品" min-width="230">
          <template #default="{ row }">
            <div class="item-cell">
              <el-image v-if="row.images?.length" :src="row.images[0]" fit="cover" class="thumb" />
              <div v-else class="thumb thumb--empty">无图</div>
              <div class="item-cell__text">
                <b :title="row.title">{{ row.title }}</b>
                <span>#{{ row.id }} · {{ row.category }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <span class="lf-tag" :class="row.sourceType === 'TRANSFER' ? 'lf-tag--claimed' : 'lf-tag--waiting'">
              {{ SOURCE_TYPE_MAP[row.sourceType] || '个人登记' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="lf-tag" :class="STATUS_MAP[row.status]?.cls">{{ STATUS_MAP[row.status]?.text || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="转交" width="100">
          <template #default="{ row }">
            <span class="lf-tag" :class="TRANSFER_STATUS_MAP[row.transferStatus || 'NONE'].cls">
              {{ TRANSFER_STATUS_MAP[row.transferStatus || 'NONE'].text }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" :icon="View" @click="openSource(row)">源头</el-button>
            <el-button size="small" text @click="toggleStatus(row)">{{ row.status === 'OFFLINE' ? '上架' : '下架' }}</el-button>
            <el-button size="small" text type="success" :disabled="row.status === 'FINISHED'" @click="goReturn(row)">归还登记</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无符合条件的物品" :image="'/empty.svg'" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </section>

    <el-dialog v-model="publishOpen" title="站点代发布拾物" width="560px">
      <el-form label-width="80px">
        <el-form-item label="物品名称" required><el-input v-model="publishForm.title" maxlength="100" /></el-form-item>
        <el-form-item label="物品类别" required>
          <el-select v-model="publishForm.category" placeholder="选择类别" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="拾获时间">
          <el-date-picker v-model="publishForm.itemTime" type="datetime" style="width: 100%" placeholder="选择时间" />
        </el-form-item>
        <el-form-item label="物品描述"><el-input v-model="publishForm.description" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        <el-form-item label="联系方式"><el-input v-model="publishForm.contact" placeholder="手机号 / 座机" /></el-form-item>
        <el-form-item label="物品图片">
          <el-upload list-type="picture-card" :file-list="publishForm.images.map((u, i) => ({ name: String(i), url: u }))"
            :http-request="uploadImage as any" :limit="6" accept="image/*"
            :on-remove="(_: any, list: any[]) => (publishForm.images = list.map((f: any) => f.url))">
            <el-icon :size="20"><UploadFilled /></el-icon>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishOpen = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">发布到本站</el-button>
      </template>
    </el-dialog>

    <SourceInfoDrawer v-model="sourceOpen" :found-id="sourceFoundId" />
  </div>
</template>

<style scoped>
.manage-page { max-width: 1280px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-head h1 { font-size: 20px; font-weight: 600; }
.page-head p { margin-top: 6px; font-size: 13px; color: var(--lf-text-3); }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat-card { display: flex; flex-direction: column; gap: 6px; padding: 18px 20px; text-align: left; border: 1px solid var(--lf-border); cursor: pointer; transition: transform 200ms ease, box-shadow 200ms ease; }
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--lf-shadow-md); }
.stat-card:focus-visible { outline: 2px solid var(--lf-primary); outline-offset: 2px; }
.stat-value { font-size: 26px; font-weight: 650; line-height: 1.1; color: var(--lf-text); }
.stat-label { font-size: 12.5px; color: var(--lf-text-3); }
.stat-card--primary .stat-value { color: var(--lf-primary); }
.stat-card--warn .stat-value { color: var(--lf-accent); }
.stat-card--success .stat-value { color: var(--lf-success); }
.panel { padding: 18px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.error-strip { display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-bottom: 14px; border-radius: 10px; background: #fef2f2; color: var(--lf-danger); font-size: 13px; }
.item-cell { display: flex; align-items: center; gap: 12px; min-width: 0; }
.thumb { width: 46px; height: 46px; flex-shrink: 0; border-radius: 8px; background: var(--lf-bg); }
.thumb--empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--lf-text-3); }
.item-cell__text { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.item-cell__text b { font-size: 13.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-cell__text span { font-size: 12px; color: var(--lf-text-3); }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
