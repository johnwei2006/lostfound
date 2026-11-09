<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, View } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { transferApi } from '@/api'
import { SOURCE_TYPE_MAP, TRANSFER_STATUS_MAP } from '@/types'
import type { SourceInfoVO } from '@/types'

const props = defineProps<{ modelValue: boolean; foundId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const info = ref<SourceInfoVO | null>(null)
const loading = ref(false)
const error = ref('')
const previewOpen = ref(false)
const previewUrl = ref('')

const showPreview = (url: string) => {
  previewUrl.value = url
  previewOpen.value = true
}

const fmt = (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—')

const load = async () => {
  if (!props.foundId) return
  loading.value = true
  error.value = ''
  info.value = null
  try {
    info.value = await transferApi.source(props.foundId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '源头信息加载失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.foundId] as const,
  () => {
    if (props.modelValue && props.foundId) load()
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="物品源头信息"
    size="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="loading" v-loading="true" class="drawer-state">正在查询源头信息…</div>

    <div v-else-if="error" class="drawer-state" role="alert">
      <el-empty description="源头信息加载失败" :image="'/empty.svg'">
        <p class="drawer-hint">{{ error }}</p>
        <el-button round :icon="Refresh" @click="load">重试</el-button>
      </el-empty>
    </div>

    <div v-else-if="info" class="source-body">
      <div class="source-head">
        <el-image
          v-if="info.images.length"
          :src="info.images[0]"
          :preview-src-list="info.images"
          fit="cover"
          class="source-cover"
        />
        <div v-else class="source-cover source-cover--empty">暂无图片</div>
        <div class="source-head__text">
          <h3>{{ info.title }}</h3>
          <p>{{ info.category || '未分类' }} · {{ fmt(info.createTime) }} 登记</p>
          <div class="source-tags">
            <span class="lf-tag lf-tag--waiting">{{ SOURCE_TYPE_MAP[info.sourceType || 'PERSONAL'] }}</span>
            <span class="lf-tag" :class="TRANSFER_STATUS_MAP[info.transferStatus || 'NONE'].cls">
              {{ TRANSFER_STATUS_MAP[info.transferStatus || 'NONE'].text }}
            </span>
          </div>
        </div>
      </div>

      <section class="source-section">
        <h4>原始登记</h4>
        <dl class="source-grid">
          <dt>登记人</dt><dd>{{ info.originalNickname || '未知用户' }}（#{{ info.originalUserId }}）</dd>
          <dt>联系电话</dt><dd>{{ info.originalPhone || '未填写' }}</dd>
          <dt>登记联系方式</dt><dd>{{ info.contact || '未填写' }}</dd>
          <dt>拾获时间</dt><dd>{{ fmt(info.foundTime) }}</dd>
          <dt>拾获地点</dt><dd>{{ info.address || '未填写' }}</dd>
          <dt>原始描述</dt><dd class="source-desc">{{ info.description || '未填写' }}</dd>
        </dl>
      </section>

      <section class="source-section">
        <h4>转交链路</h4>
        <dl class="source-grid">
          <dt>目标招领站</dt><dd>{{ info.siteName || '—' }}<template v-if="info.siteId">（#{{ info.siteId }}）</template></dd>
          <dt>转交人</dt><dd>{{ info.fromNickname || info.originalNickname || '—' }}</dd>
          <dt>提交时间</dt><dd>{{ fmt(info.submitTime) }}</dd>
          <dt>转交备注</dt><dd>{{ info.transferRemark || '无' }}</dd>
          <dt>审批人</dt><dd>{{ info.reviewerName || '—' }}</dd>
          <dt>审批时间</dt><dd>{{ fmt(info.reviewTime) }}</dd>
          <dt>审批意见</dt><dd>{{ info.reviewRemark || '无' }}</dd>
        </dl>
      </section>

      <section class="source-section">
        <h4>归还记录（{{ info.returns.length }}）</h4>
        <div v-if="!info.returns.length" class="source-empty">尚未登记归还，认领审批通过或现场登记后会在此展示。</div>
        <div v-for="r in info.returns" :key="r.id" class="return-row">
          <div>
            <b>{{ r.way === 'CLAIM' ? '认领审批' : '现场登记' }} · {{ r.receiverName || '未填写领取人' }}</b>
            <p>{{ fmt(r.returnTime) }} · 操作人 {{ r.operatorName || '—' }}</p>
            <p v-if="r.remark">{{ r.remark }}</p>
          </div>
          <el-button
            v-if="r.evidence.length"
            text
            type="primary"
            :icon="View"
            @click="showPreview(r.evidence[0])"
          >
            凭证
          </el-button>
        </div>
      </section>
    </div>

    <el-dialog v-model="previewOpen" title="归还凭证" width="520px" append-to-body>
      <el-image :src="previewUrl" fit="contain" style="width: 100%" />
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.drawer-state {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lf-text-3);
}
.drawer-hint {
  font-size: 13px;
  color: var(--lf-text-3);
  margin-bottom: 12px;
}
.source-head {
  display: flex;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--lf-border);
}
.source-cover {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 12px;
  background: var(--lf-bg);
}
.source-cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--lf-text-3);
}
.source-head__text {
  min-width: 0;
}
.source-head__text h3 {
  font-size: 17px;
  font-weight: 600;
  word-break: break-all;
}
.source-head__text p {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--lf-text-3);
}
.source-tags {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.source-section {
  padding: 20px 0 4px;
  border-bottom: 1px solid var(--lf-border);
}
.source-section:last-child {
  border-bottom: none;
}
.source-section h4 {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 14px;
}
.source-grid {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 10px 12px;
  font-size: 13px;
  margin: 0;
}
.source-grid dt {
  color: var(--lf-text-3);
}
.source-grid dd {
  margin: 0;
  color: var(--lf-text);
  min-width: 0;
  word-break: break-all;
}
.source-desc {
  line-height: 1.7;
  white-space: pre-wrap;
}
.source-empty {
  font-size: 13px;
  color: var(--lf-text-3);
  padding: 8px 0 18px;
}
.return-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: var(--lf-bg);
}
.return-row b {
  font-size: 13px;
}
.return-row p {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lf-text-3);
  word-break: break-all;
}
</style>
