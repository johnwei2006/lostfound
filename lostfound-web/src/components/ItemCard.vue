<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Star, View, ChatDotRound, Location, Clock } from '@element-plus/icons-vue'
import { STATUS_MAP, type ItemVO } from '@/types'

const props = defineProps<{ item: ItemVO }>()
const router = useRouter()

const cover = computed(() => props.item.images?.[0] || '')
const status = computed(() => STATUS_MAP[props.item.status] || STATUS_MAP.WAITING)
const typeText = computed(() => (props.item.type === 'FOUND' ? '拾物' : '失物'))
const typeCls = computed(() => (props.item.type === 'FOUND' ? 'card-type--found' : 'card-type--lost'))
/** 后端仅在按距离排序时返回，单位公里；首页 latest 接口不返回该字段 */
const distanceText = computed(() => {
  const km = props.item.distance
  if (typeof km !== 'number' || !Number.isFinite(km) || km < 0) return ''
  return km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`
})
/** 发布时间；缺少发布时间时回退到拾获/丢失时间 */
const timeText = computed(() => {
  const raw = props.item.createTime || props.item.itemTime
  if (!raw) return ''
  const d = dayjs(raw)
  return d.isValid() ? d.format('YYYY MM-DD HH:mm') : ''
})

const goDetail = () => {
  router.push(`/${props.item.type.toLowerCase()}/${props.item.id}`)
}
</script>

<template>
  <article class="item-card lf-card lf-card--hover" @click="goDetail">
    <div class="card-cover">
      <img v-if="cover" :src="cover" :alt="item.title" loading="lazy" />
      <div v-else class="cover-placeholder">
        <svg viewBox="0 0 48 48" width="42" height="42">
          <path d="M24 8a11 11 0 0 1 11 11c0 7.5-11 21-11 21S13 26.5 13 19A11 11 0 0 1 24 8z" fill="currentColor" opacity="0.25"/>
          <circle cx="24" cy="19" r="5" fill="currentColor" opacity="0.5"/>
        </svg>
      </div>
      <span class="card-type" :class="typeCls">{{ typeText }}</span>
      <span class="lf-tag card-status" :class="status.cls">{{ status.text }}</span>
      <span v-if="distanceText" class="card-distance" :aria-label="`距您 ${distanceText}`">
        <el-icon :size="12"><Location /></el-icon>距您 {{ distanceText }}
      </span>
    </div>
    <div class="card-body">
      <h3 class="card-title">{{ item.title }}</h3>
      <p class="card-desc">{{ item.description || '暂无描述' }}</p>
      <div class="card-meta">
        <span class="meta-category">{{ item.category }}</span>
        <span v-if="item.city || item.address" class="meta-loc">{{ item.city }}{{ item.address ? ' · ' + item.address : '' }}</span>
        <span v-if="timeText" class="meta-time" :title="'发布于 ' + timeText">
          <el-icon :size="12"><Clock /></el-icon>{{ timeText }}
        </span>
      </div>
      <div class="card-footer">
        <div class="card-author">
          <el-avatar :size="22">{{ item.nickname?.charAt(0) || 'U' }}</el-avatar>
          <span>{{ item.nickname }}</span>
        </div>
        <div class="card-stats">
          <span><el-icon :size="13"><View /></el-icon>{{ item.viewCount }}</span>
          <span><el-icon :size="13"><Star /></el-icon>{{ item.focusCount }}</span>
          <span><el-icon :size="13"><ChatDotRound /></el-icon>{{ item.commentCount }}</span>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card-distance {
  position: absolute;
  bottom: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--lf-primary);
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 1px 6px rgba(31, 41, 55, 0.12);
}

.item-card {
  overflow: hidden;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}
.card-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: #eef2f8;
  overflow: hidden;
}
.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease-out);
}
.item-card:hover .card-cover img {
  transform: scale(1.06);
}
.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9db3d8;
}
.card-type {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  backdrop-filter: blur(4px);
}
.card-type--found {
  background: rgba(16, 185, 129, 0.85);
}
.card-type--lost {
  background: rgba(239, 68, 68, 0.85);
}
.card-status {
  position: absolute;
  top: 10px;
  right: 10px;
  background: #fff;
}
.card-body {
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  font-size: 12.5px;
  color: var(--lf-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 34px;
}
.card-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--lf-text-3);
}
.meta-category {
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  padding: 1px 8px;
  border-radius: 6px;
  flex-shrink: 0;
}
.meta-loc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-time {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
}
.card-footer {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px dashed var(--lf-border);
}
.card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--lf-text-2);
}
.card-stats {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--lf-text-3);
}
.card-stats span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
</style>
