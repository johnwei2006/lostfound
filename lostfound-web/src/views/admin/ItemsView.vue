<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'
import dayjs from 'dayjs'

const type = ref('FOUND')
const reviewStatus = ref('')
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const list = ref<Record<string, any>[]>([])
const loading = ref(false)

const reviewText: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
const reviewTag: Record<string, string> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const statusText: Record<string, string> = {
  WAITING: '待认领',
  CLAIMED: '待确认',
  FINISHED: '已完成',
  OFFLINE: '已下架',
}

const load = async () => {
  loading.value = true
  try {
    const data = await adminApi.items({
      type: type.value,
      reviewStatus: reviewStatus.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: 12,
    })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

const reload = () => {
  page.value = 1
  load()
}

const review = async (row: Record<string, any>, approve: boolean) => {
  await ElMessageBox.confirm(
    approve ? `通过「${row.title}」的审核？` : `驳回「${row.title}」？驳回后信息将下架。`,
    '审核确认',
    { type: 'warning' },
  )
  await adminApi.review(row.type, row.id, approve)
  ElMessage.success('已处理')
  load()
}
</script>

<template>
  <div>
    <h2 style="margin-bottom: 18px">物品审核</h2>
    <el-card shadow="never" style="border-radius: 14px">
      <div class="toolbar">
        <el-radio-group v-model="type" @change="reload">
          <el-radio-button value="FOUND">拾物</el-radio-button>
          <el-radio-button value="LOST">失物</el-radio-button>
        </el-radio-group>
        <el-select v-model="reviewStatus" placeholder="审核状态" clearable style="width: 130px" @change="reload">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索物品名称"
          clearable
          style="width: 220px"
          @keyup.enter="reload"
        />
      </div>

      <el-table :data="list" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusText[row.status] || row.status }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="reviewTag[row.reviewStatus] as any" size="small">
              {{ reviewText[row.reviewStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="review(row, true)">通过</el-button>
            <el-button size="small" type="danger" text @click="review(row, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: center; margin-top: 16px">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="12"
          :current-page="page"
          @current-change="(p: number) => { page = p; load() }"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
