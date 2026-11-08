<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { messageApi, matchApi } from '@/api'
import type { Message } from '@/types'
import dayjs from 'dayjs'

const router = useRouter()
const list = ref<Message[]>([])
const total = ref(0)
const page = ref(1)
const typeFilter = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const data = await messageApi.page({ type: typeFilter.value || undefined, page: page.value, size: 15 })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)

const read = async (m: Message) => {
  if (m.isRead) return
  await messageApi.read(m.id)
  m.isRead = 1
  if (m.type === 'MATCH') {
    router.push('/matches')
  }
}
const readAll = async () => {
  await messageApi.readAll()
  load()
}
const ignoreMatch = async (m: Message) => {
  if (m.relatedId) {
    await matchApi.setStatus(m.relatedId, 'IGNORED')
    m.isRead = 1
  }
}

const typeText: Record<string, string> = { MATCH: '匹配推荐', CLAIM: '认领动态', SYSTEM: '系统通知' }
const typeColor: Record<string, string> = { MATCH: '#2563eb', CLAIM: '#f97316', SYSTEM: '#6b7280' }
</script>

<template>
  <div class="lf-container msg-page">
    <div class="msg-head">
      <h1 class="lf-page-title">消息中心</h1>
      <el-button text type="primary" @click="readAll">全部已读</el-button>
    </div>
    <el-radio-group v-model="typeFilter" @change="page = 1; load()" style="margin-bottom: 16px">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="MATCH">匹配推荐</el-radio-button>
      <el-radio-button value="CLAIM">认领动态</el-radio-button>
      <el-radio-button value="SYSTEM">系统通知</el-radio-button>
    </el-radio-group>

    <div v-if="loading">加载中…</div>
    <div v-else-if="list.length" class="msg-list">
      <div
        v-for="m in list"
        :key="m.id"
        class="msg-item lf-card lf-card--hover"
        :class="{ unread: !m.isRead }"
        @click="read(m)"
      >
        <span class="msg-dot" v-if="!m.isRead"></span>
        <div class="msg-body">
          <div class="msg-top">
            <span class="msg-type" :style="{ background: typeColor[m.type] + '1a', color: typeColor[m.type] }">
              {{ typeText[m.type] }}
            </span>
            <b class="msg-title">{{ m.title }}</b>
            <span class="msg-time">{{ dayjs(m.createTime).format('MM-DD HH:mm') }}</span>
          </div>
          <p>{{ m.content }}</p>
        </div>
        <el-button v-if="m.type === 'MATCH' && !m.isRead" size="small" text type="info" @click.stop="ignoreMatch(m)">
          忽略
        </el-button>
      </div>
    </div>
    <el-empty v-else description="暂无消息" :image="'/empty.svg'" />

    <div v-if="total > 15" style="display: flex; justify-content: center; margin-top: 22px">
      <el-pagination layout="prev, pager, next" :total="total" :page-size="15" :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>
  </div>
</template>

<style scoped>
.msg-page {
  padding: 30px 20px 50px;
}
.msg-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 20px;
  cursor: pointer;
}
.msg-item.unread {
  border-left: 3px solid var(--lf-primary);
}
.msg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--lf-danger);
  margin-top: 8px;
  flex-shrink: 0;
}
.msg-body {
  flex: 1;
}
.msg-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.msg-type {
  font-size: 11.5px;
  padding: 1px 8px;
  border-radius: 999px;
  font-weight: 600;
}
.msg-title {
  font-size: 14.5px;
}
.msg-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--lf-text-3);
}
.msg-body p {
  margin-top: 5px;
  font-size: 13px;
  color: var(--lf-text-2);
}
</style>
