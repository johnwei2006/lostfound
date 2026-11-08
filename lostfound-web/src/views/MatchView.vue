<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import { matchApi } from '@/api'
import type { MatchVO } from '@/types'
import dayjs from 'dayjs'

const router = useRouter()
const list = ref<MatchVO[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    list.value = await matchApi.mine()
  } finally {
    loading.value = false
  }
})

const act = async (m: MatchVO, status: string) => {
  await matchApi.setStatus(m.id, status)
  m.status = status as MatchVO['status']
  ElMessage.success(status === 'ACCEPTED' ? '已采纳，快去联系对方吧' : '已忽略')
}

const statusText: Record<string, string> = { UNREAD: '待处理', ACCEPTED: '已采纳', IGNORED: '已忽略' }
</script>

<template>
  <div class="lf-container match-page">
    <div class="match-head">
      <div class="match-banner lf-card">
        <el-icon :size="26" color="#2563eb"><Connection /></el-icon>
        <div>
          <h1 class="lf-page-title">智能匹配推荐</h1>
          <p class="lf-page-sub">系统按类别、时间、地点、描述相似度自动配对失物与拾物</p>
        </div>
      </div>
    </div>

    <div v-if="loading">加载中…</div>
    <div v-else-if="list.length" class="match-list">
      <div v-for="m in list" :key="m.id" class="match-item lf-card">
        <div class="score-ring" :class="{ high: m.score >= 80 }">
          <b>{{ Math.round(m.score) }}</b>
          <span>相似度</span>
        </div>
        <div class="match-pair">
          <div class="pair-card" v-if="m.found" @click="router.push('/found/' + m.found.id)">
            <span class="pair-tag pair-tag--found">拾物</span>
            <b>{{ m.found.title }}</b>
            <p>{{ m.found.address || m.found.category }} · {{ dayjs(m.found.createTime).format('MM-DD') }}</p>
          </div>
          <div class="pair-link">
            <svg width="30" height="16" viewBox="0 0 30 16"><path d="M0 8h24m0 0-5-5m5 5-5 5" stroke="#9ca3af" stroke-width="1.6" fill="none"/></svg>
          </div>
          <div class="pair-card" v-if="m.lost" @click="router.push('/lost/' + m.lost.id)">
            <span class="pair-tag pair-tag--lost">失物</span>
            <b>{{ m.lost.title }}</b>
            <p>{{ m.lost.address || m.lost.category }} · {{ dayjs(m.lost.createTime).format('MM-DD') }}</p>
          </div>
        </div>
        <div class="match-side">
          <p class="match-reason">{{ m.reason }}</p>
          <span class="lf-tag" :class="{ 'lf-tag--waiting': m.status === 'UNREAD', 'lf-tag--finished': m.status === 'ACCEPTED', 'lf-tag--offline': m.status === 'IGNORED' }">
            {{ statusText[m.status] }}
          </span>
          <div v-if="m.status === 'UNREAD'" class="match-ops">
            <el-button type="primary" size="small" round @click="act(m, 'ACCEPTED')">采纳</el-button>
            <el-button size="small" round @click="act(m, 'IGNORED')">忽略</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无匹配推荐，发布失物/拾物后系统将自动配对" :image="'/empty.svg'">
      <el-button type="primary" round @click="router.push('/publish')">去发布</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.match-page {
  padding: 30px 20px 50px;
}
.match-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 26px;
  background: linear-gradient(120deg, #eaf1fe, #fff);
}
.match-list {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.match-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  flex-wrap: wrap;
}
.score-ring {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.score-ring.high {
  background: var(--lf-accent-soft);
  color: var(--lf-accent);
}
.score-ring b {
  font-size: 20px;
  line-height: 1.1;
}
.score-ring span {
  font-size: 10.5px;
}
.match-pair {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.pair-card {
  flex: 1;
  border: 1px solid var(--lf-border);
  border-radius: var(--lf-radius-sm);
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 0;
}
.pair-card:hover {
  border-color: var(--lf-primary);
  background: var(--lf-primary-soft);
}
.pair-card b {
  display: block;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pair-card p {
  font-size: 12px;
  color: var(--lf-text-3);
  margin-top: 4px;
}
.pair-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  color: #fff;
  margin-bottom: 6px;
  display: inline-block;
}
.pair-tag--found {
  background: var(--lf-success);
}
.pair-tag--lost {
  background: var(--lf-danger);
}
.match-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.match-reason {
  font-size: 12px;
  color: var(--lf-text-3);
  max-width: 200px;
  text-align: right;
}
.match-ops {
  display: flex;
  gap: 6px;
}
</style>
