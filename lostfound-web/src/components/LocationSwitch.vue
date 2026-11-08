<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Location, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MapPicker from '@/components/MapPicker.vue'
import {
  currentLocation,
  DEFAULT_CITY,
  ensureLocation,
  getCurrentLocation,
  refreshLocation,
  setManualLocation,
} from '@/utils/location'

interface PickedLocation {
  longitude: number | null
  latitude: number | null
  address: string
}

const dialogVisible = ref(false)
const restoring = ref(false)
const pickerKey = ref(0)
const pending = ref<PickedLocation>({ longitude: null, latitude: null, address: '' })

const isManual = computed(() => currentLocation.value.source === 'manual')
const sourceKind = computed(() => {
  const l = currentLocation.value
  if (l.source === 'manual') return 'manual'
  return l.available ? 'auto' : 'default'
})
const sourceText = computed(() => {
  if (isManual.value) return '手动选点'
  return currentLocation.value.available ? '自动定位' : '默认城市'
})
const displayText = computed(() => {
  const l = currentLocation.value
  if (l.address) return l.address
  if (!l.available) return `未获取到定位，暂用默认城市（${DEFAULT_CITY}）`
  const coord = `${l.latitude.toFixed(4)}, ${l.longitude.toFixed(4)}`
  return isManual.value ? `已选位置 ${coord}` : `当前定位 ${coord}`
})

const openPicker = () => {
  const l = getCurrentLocation()
  pending.value = { longitude: l.longitude, latitude: l.latitude, address: l.address || '' }
  pickerKey.value += 1
  dialogVisible.value = true
}

const onMapUpdate = (p: PickedLocation) => {
  pending.value = { ...p }
}

const confirmPicker = () => {
  const p = pending.value
  if (p.longitude === null || p.latitude === null) {
    ElMessage.warning('请先在地图上点击选择位置，或填写经纬度')
    return
  }
  if (!setManualLocation({ latitude: p.latitude, longitude: p.longitude, address: p.address })) {
    ElMessage.warning('坐标不合法：纬度 -90~90、经度 -180~180')
    return
  }
  dialogVisible.value = false
  ElMessage.success('已切换当前位置，列表与距离已按新位置更新')
}

const restore = () => {
  if (restoring.value) return
  restoring.value = true
  refreshLocation()
    .then((l) => {
      ElMessage.success(l.available ? '已恢复当前位置' : '定位不可用，已回退默认城市')
    })
    .finally(() => {
      restoring.value = false
    })
}

onMounted(() => {
  // 与同一次会话内其它页面共用自动定位 Promise，不重复触发授权
  if (!getCurrentLocation().available) void ensureLocation()
})
</script>

<template>
  <div class="loc-switch" :class="`loc-switch--${sourceKind}`" role="status" aria-live="polite">
    <div class="loc-switch__main">
      <span class="loc-switch__pin" aria-hidden="true"><span /></span>
      <div class="loc-switch__copy">
        <span class="loc-switch__label">当前位置</span>
        <span class="loc-switch__value" :title="displayText">{{ displayText }}</span>
      </div>
      <span class="loc-switch__source">{{ sourceText }}</span>
    </div>
    <div class="loc-switch__actions">
      <el-button type="primary" size="small" round @click="openPicker">
        <el-icon><Location /></el-icon><span>地图选点</span>
      </el-button>
      <el-button size="small" text :loading="restoring" @click="restore">
        <el-icon v-if="!restoring"><RefreshRight /></el-icon>
        <span>{{ isManual ? '恢复当前位置' : '重新定位' }}</span>
      </el-button>
    </div>
  </div>

  <el-dialog
    v-model="dialogVisible"
    title="选择当前位置"
    width="min(680px, 92vw)"
    :destroy-on-close="true"
    append-to-body
  >
    <p class="loc-dialog__hint">
      选择位置后，失物与拾物列表将按该位置由近到远排序，卡片距离同步更新。所选位置仅在本次会话生效。
    </p>
    <MapPicker
      v-if="dialogVisible"
      :key="pickerKey"
      :longitude="pending.longitude"
      :latitude="pending.latitude"
      :address="pending.address"
      @update="onMapUpdate"
    />
    <p class="loc-dialog__coord">坐标使用 GCJ02（高德 / 微信地图）坐标系；未配置高德 Key 时可直接输入经纬度。</p>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmPicker">确定，使用该位置</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.loc-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 18px;
  border: 1px solid #d7e5fd;
  border-radius: var(--lf-radius-sm);
  background: var(--lf-primary-soft);
  font-size: 13px;
  line-height: 1.5;
}
.loc-switch--manual {
  border-color: var(--lf-primary);
}
.loc-switch--default {
  background: var(--lf-accent-soft);
  border-color: #fbd9b8;
}
.loc-switch__main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 220px;
}
.loc-switch__pin {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border: 2px solid var(--lf-primary);
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.loc-switch__pin span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lf-primary);
}
.loc-switch__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.loc-switch__label {
  color: #475569;
  font-size: 12px;
}
.loc-switch__value {
  color: var(--lf-text);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.loc-switch__source {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--lf-surface);
  color: var(--lf-primary);
  font-size: 12px;
}
.loc-switch--default .loc-switch__source {
  color: #7c3a12;
}
.loc-switch__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.loc-dialog__hint {
  color: #475569;
  font-size: 13px;
  margin-bottom: 12px;
}
.loc-dialog__coord {
  color: #475569;
  font-size: 12px;
  margin-top: 10px;
}
@media (max-width: 640px) {
  .loc-switch {
    align-items: flex-start;
    flex-direction: column;
  }
  .loc-switch__main {
    width: 100%;
  }
}
@media (prefers-reduced-motion: reduce) {
  .loc-switch,
  .loc-switch :deep(.el-button) {
    transition: none;
  }
}
</style>
