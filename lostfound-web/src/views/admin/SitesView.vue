<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, siteApi } from '@/api'
import type { FoundSite, User } from '@/types'

const list = ref<FoundSite[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const dialog = ref(false)
const editing = ref<FoundSite | null>(null)
const form = ref<Partial<FoundSite>>({})
const users = ref<User[]>([])

const load = async () => {
  loading.value = true
  try {
    const data = await siteApi.page({ page: page.value, size: 10 })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

const openCreate = () => {
  editing.value = null
  form.value = { name: '', address: '', phone: '', openTime: '', description: '' }
  loadUsers()
  dialog.value = true
}
const openEdit = (s: FoundSite) => {
  editing.value = s
  form.value = { ...s }
  loadUsers()
  dialog.value = true
}
const loadUsers = async () => {
  const data = await adminApi.users({ page: 1, size: 100 })
  users.value = data?.records || []
}

const save = async () => {
  if (!form.value.name || !form.value.address) return ElMessage.warning('请填写站点名称和地址')
  if (editing.value) {
    await siteApi.update(editing.value.id, form.value)
  } else {
    await siteApi.create(form.value)
  }
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

const remove = async (s: FoundSite) => {
  await ElMessageBox.confirm(`确定删除招领站「${s.name}」？站内物品将解除关联。`, '删除确认', { type: 'warning' })
  await siteApi.remove(s.id)
  ElMessage.success('已删除')
  load()
}
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px">
      <h2>招领站管理</h2>
      <el-button type="primary" round @click="openCreate">新增站点</el-button>
    </div>

    <el-card shadow="never" style="border-radius: 14px">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="站点名称" min-width="160" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="openTime" label="开放时间" width="140" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination layout="prev, pager, next" :total="total" :page-size="10" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </el-card>

    <el-dialog v-model="dialog" :title="editing ? '编辑站点' : '新增站点'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="站点名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="经度"><el-input-number v-model="form.longitude as any" :precision="6" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="form.latitude as any" :precision="6" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="开放时间"><el-input v-model="form.openTime" placeholder="如：8:00-22:00" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
