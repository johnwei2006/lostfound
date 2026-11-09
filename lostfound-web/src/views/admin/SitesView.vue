<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { adminApi, siteApi } from '@/api'
import type { FoundSite, SiteVO, User } from '@/types'
import { errMsg } from '@/utils/format'

const list = ref<SiteVO[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)
const submitting = ref(false)

const dialog = ref(false)
const editing = ref<FoundSite | null>(null)
const form = ref<Partial<FoundSite>>({})
const users = ref<User[]>([])
const managerIds = ref<number[]>([])

const load = async () => {
  loading.value = true
  try {
    const data = await siteApi.page({ keyword: keyword.value, page: page.value, size: 10 })
    list.value = data.records
    total.value = data.total
  } catch (e) { ElMessage.error(errMsg(e, '站点列表加载失败')) }
  finally { loading.value = false }
}
onMounted(load)

const loadUsers = async () => {
  try {
    const data = await adminApi.users({ page: 1, size: 100 })
    users.value = data?.records || []
  } catch (e) { ElMessage.error(errMsg(e, '用户列表加载失败')) }
}

const openCreate = () => {
  editing.value = null
  form.value = { name: '', address: '', phone: '', openTime: '', description: '' }
  managerIds.value = []
  loadUsers()
  dialog.value = true
}
const openEdit = async (s: SiteVO) => {
  editing.value = s
  form.value = { ...s }
  managerIds.value = []
  loadUsers()
  try {
    managerIds.value = (await siteApi.managers(s.id)).map((m) => m.userId)
  } catch (e) { ElMessage.error(errMsg(e, '管理员名单加载失败')) }
  dialog.value = true
}

const save = async () => {
  if (!form.value.name?.trim() || !form.value.address?.trim()) return ElMessage.warning('请填写站点名称和地址')
  if (submitting.value) return
  submitting.value = true
  try {
    const siteId = editing.value ? editing.value.id : await siteApi.create(form.value)
    if (editing.value) await siteApi.update(siteId, form.value)
    // 全量覆盖式名单，后端会去重并校验用户有效性
    await siteApi.setManagers(siteId, managerIds.value)
    ElMessage.success(`已保存站点信息，当前 ${managerIds.value.length} 位管理员`)
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(errMsg(e, '保存失败')) }
  finally { submitting.value = false }
}

const remove = async (s: SiteVO) => {
  try {
    await ElMessageBox.confirm(`确定删除招领站「${s.name}」？站内物品将解除关联，待审批的转交申请会自动撤销。`, '删除确认', { type: 'warning' })
    await siteApi.remove(s.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e instanceof Error && e.message !== 'cancel') ElMessage.error(errMsg(e, '删除失败'))
  }
}
</script>

<template>
  <div>
    <div class="sites-head">
      <div>
        <h2>招领站管理</h2>
        <p>建立站点并配置多名管理员，管理员可在「招领站管理」后台处理本站物品与归还。</p>
      </div>
      <el-button type="primary" round :icon="Plus" @click="openCreate">新建站点</el-button>
    </div>

    <el-card shadow="never" class="sites-card">
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="站点名称 / 地址" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="() => { page = 1; load() }" />
        <el-button type="primary" plain @click="() => { page = 1; load() }">查询</el-button>
        <el-button text :icon="Refresh" @click="() => { keyword = ''; page = 1; load() }">重置</el-button>
      </div>

      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="站点名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="190" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column label="管理员" width="100">
          <template #default="{ row }"><span class="lf-tag lf-tag--waiting">{{ row.managerCount }} 人</span></template>
        </el-table-column>
        <el-table-column label="物品数" width="90">
          <template #default="{ row }"><span class="lf-tag lf-tag--muted">{{ row.itemCount }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无招领站" :image="'/empty.svg'" /></template>
      </el-table>
      <div class="pager">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="10" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </el-card>

    <el-dialog v-model="dialog" :title="editing ? '编辑站点' : '新建站点'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="站点名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="地址" required><el-input v-model="form.address" maxlength="200" /></el-form-item>
        <el-form-item label="经度"><el-input-number v-model="form.longitude" :precision="6" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="form.latitude" :precision="6" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="开放时间"><el-input v-model="form.openTime" placeholder="如：8:00-22:00" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="站点管理员">
          <el-select v-model="managerIds" multiple filterable collapse-tags collapse-tags-tooltip :max-collapse-tags="3"
            placeholder="搜索并添加用户，可多选" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="`${u.nickname || u.username}（#${u.id}）`" :value="u.id" />
          </el-select>
          <p class="form-hint">全量覆盖保存：移除标签即取消该用户的站点管理员权限，新增管理员会收到站内通知。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sites-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.sites-head h2 { font-size: 20px; font-weight: 600; }
.sites-head p { margin-top: 6px; font-size: 13px; color: var(--lf-text-3); }
.sites-card { border-radius: 14px; padding: 6px 4px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 10px 12px 16px; }
.pager { display: flex; justify-content: flex-end; margin: 16px 12px 4px; }
.form-hint { margin-top: 8px; font-size: 12px; line-height: 1.6; color: var(--lf-text-3); }
</style>
