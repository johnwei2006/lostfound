<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import type { User } from '@/types'
import dayjs from 'dayjs'

const list = ref<User[]>([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const data = await adminApi.users({ keyword: keyword.value || undefined, page: page.value, size: 12 })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

const toggle = async (u: User) => {
  const enable = u.status === 0
  await ElMessageBox.confirm(
    enable ? `恢复用户「${u.nickname || u.username}」？` : `禁用用户「${u.nickname || u.username}」？禁用后无法登录。`,
    '确认操作',
    { type: 'warning' },
  )
  await adminApi.changeUserStatus(u.id, enable)
  ElMessage.success('已处理')
  load()
}
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px">
      <h2>用户管理</h2>
      <el-input v-model="keyword" placeholder="搜索用户名/昵称/手机号" clearable style="width: 240px" @keyup.enter="page = 1; load()" />
    </div>

    <el-card shadow="never" style="border-radius: 14px">
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ dayjs(row.createTime).format('YYYY-MM-DD') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.role !== 'ADMIN'" size="small" text :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '禁用' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination layout="prev, pager, next, total" :total="total" :page-size="12" :current-page="page"
          @current-change="(p: number) => { page = p; load() }" />
      </div>
    </el-card>
  </div>
</template>
