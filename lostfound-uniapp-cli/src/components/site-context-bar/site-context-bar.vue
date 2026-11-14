<template>
  <!-- 管理员身份下所有页面常驻；普通用户身份完全不渲染 -->
  <view v-if="isManager">
    <site-switcher
      variant="bar"
      :site="currentSite"
      :sites="sites"
      :current-id="currentSiteId"
      :owner="isOwner"
      @change="onSwitch"
    />
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import SiteSwitcher from '@/components/site-switcher/site-switcher.vue'
import siteStore, { CONTEXT_CHANGE_EVENT } from '@/utils/site-store'
import { viewMode, syncViewMode } from '@/utils/view-mode'

const isManager = computed(() => viewMode.value === 'manager')

// siteStore 是模块级普通对象，这里用本地响应式快照跟随上下文变化刷新
const revision = ref(0)
const currentSite = computed(() => { revision.value; return siteStore.currentSite })
const sites = computed(() => { revision.value; return siteStore.sites })
const currentSiteId = computed(() => { revision.value; return siteStore.currentSiteId })
const isOwner = computed(() => { revision.value; return siteStore.isOwner() })

const refresh = () => {
  syncViewMode()
  revision.value += 1
}

onMounted(() => {
  if (typeof uni.$on === 'function') uni.$on(CONTEXT_CHANGE_EVENT, refresh)
  // 冷启动直接进入非「我的」页时也要从 storage 恢复身份态
  refresh()
  // 仅管理员身份 + 已登录才发 /api/foundsite/mine；公开页匿名访客零请求
  if (isManager.value) siteStore.ensureLoaded()
})
onUnmounted(() => {
  if (typeof uni.$off === 'function') uni.$off(CONTEXT_CHANGE_EVENT, refresh)
})

const onSwitch = (id) => {
  siteStore.select(id)
  refresh()
}
</script>
