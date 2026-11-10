<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, InfoFilled } from '@element-plus/icons-vue'
import { fmtTime } from '@/utils/format'

/** 招领站自定义背书展示：列表用紧凑模式，详情页用完整卡片 */
const props = withDefaults(defineProps<{
  org?: string
  text?: string
  updatedAt?: string
  compact?: boolean
}>(), { org: '', text: '', updatedAt: '', compact: false })

const has = computed(() => !!(props.org?.trim() || props.text?.trim()))
</script>

<template>
  <div v-if="compact" class="endorse-compact" :class="{ 'endorse-compact--set': has }">
    <el-icon :size="14"><CircleCheck v-if="has" /><InfoFilled v-else /></el-icon>
    <span>{{ has ? (org?.trim() || '已设置背书') : '暂未设置背书' }}</span>
  </div>

  <section v-else class="endorse-card" :class="{ 'endorse-card--empty': !has }" aria-label="站点背书信息">
    <div class="endorse-head">
      <span class="endorse-icon"><el-icon :size="18"><CircleCheck /></el-icon></span>
      <div class="endorse-title">
        <h3>站点背书</h3>
        <p>由站点自行维护的说明信息，不代表平台认证</p>
      </div>
      <span class="lf-tag" :class="has ? 'lf-tag--waiting' : 'lf-tag--muted'">
        {{ has ? '已设置背书' : '暂未设置' }}
      </span>
    </div>
    <template v-if="has">
      <p v-if="org?.trim()" class="endorse-org">{{ org.trim() }}</p>
      <blockquote v-if="text?.trim()" class="endorse-text">{{ text.trim() }}</blockquote>
      <p v-if="updatedAt" class="endorse-time">更新于 {{ fmtTime(updatedAt) }}</p>
    </template>
    <p v-else class="endorse-empty">
      该站点尚未填写背书单位或推荐语。站点管理员可在「招领站管理 → 站点资料与背书」中维护这段说明。
    </p>
  </section>
</template>

<style scoped>
.endorse-compact {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.6;
  background: #f3f4f6;
  color: var(--lf-text-3);
}
.endorse-compact span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.endorse-compact--set {
  background: linear-gradient(100deg, #eaf1fe, #eef8ff);
  color: var(--lf-primary);
  border: 1px solid #d7e5fd;
}
.endorse-card {
  margin-top: 20px;
  padding: 22px 24px;
  border-radius: var(--lf-radius);
  border: 1px solid #dbe8fe;
  background: linear-gradient(135deg, #ffffff 0%, #f5f9ff 55%, #eef4ff 100%);
  box-shadow: var(--lf-shadow-sm);
}
.endorse-card--empty {
  border-color: var(--lf-border);
  background: #fff;
}
.endorse-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.endorse-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 12px;
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
}
.endorse-card--empty .endorse-icon {
  background: #f3f4f6;
  color: var(--lf-text-3);
}
.endorse-title { flex: 1; min-width: 0; }
.endorse-title h3 { font-size: 16px; font-weight: 600; }
.endorse-title p { margin-top: 2px; font-size: 12px; color: var(--lf-text-3); }
.endorse-org {
  margin-top: 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--lf-primary);
}
.endorse-text {
  margin-top: 10px;
  padding: 12px 16px;
  border-left: 3px solid var(--lf-primary-light);
  border-radius: 0 10px 10px 0;
  background: rgba(255, 255, 255, 0.78);
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--lf-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
.endorse-time { margin-top: 12px; font-size: 12px; color: var(--lf-text-3); }
.endorse-empty { margin-top: 14px; font-size: 13px; line-height: 1.8; color: var(--lf-text-3); }
@media (max-width: 520px) {
  .endorse-card { padding: 18px 16px; }
  .endorse-head { flex-wrap: wrap; }
}
</style>
