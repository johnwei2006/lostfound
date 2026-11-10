<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, CircleCheck, Goods, OfficeBuilding, Refresh, Tickets } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useSiteStore } from '@/stores/site'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const menus = [
  { path: '/manage/items', label: '站点物品', icon: Goods },
  { path: '/manage/transfers', label: '转交与认领审批', icon: Tickets },
  { path: '/manage/returns', label: '归还管理', icon: CircleCheck },
  { path: '/manage/site', label: '站点资料与背书', icon: OfficeBuilding },
]

const noSite = computed(() => !siteStore.loading && !siteStore.error && siteStore.sites.length === 0)

onMounted(() => siteStore.load())
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <div class="admin-brand">
        <svg viewBox="0 0 48 48" width="28" height="28" aria-hidden="true">
          <rect width="48" height="48" rx="12" fill="#2563EB" opacity="0.14" />
          <path d="M24 10a10 10 0 0 1 10 10c0 7-10 18-10 18S14 27 14 20a10 10 0 0 1 10-10z" fill="#2563EB" />
          <circle cx="24" cy="20" r="4.5" fill="#F97316" />
        </svg>
        <span>百觅 · 招领站管理</span>
      </div>

      <div class="site-switch">
        <label for="manage-site-select">当前站点</label>
        <el-select
          id="manage-site-select"
          :model-value="siteStore.currentSiteId"
          placeholder="选择招领站"
          :loading="siteStore.loading"
          :disabled="!siteStore.sites.length"
          style="width: 100%"
          @change="siteStore.select"
        >
          <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <p v-if="siteStore.currentSite" class="site-address">{{ siteStore.currentSite.address }}</p>
      </div>

      <nav class="admin-nav" aria-label="站点管理菜单">
        <RouterLink
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          class="admin-link"
          :class="{ active: route.path.startsWith(m.path) }"
        >
          <el-icon :size="16"><component :is="m.icon" /></el-icon>
          {{ m.label }}
        </RouterLink>
      </nav>

      <div class="admin-user">
        <el-avatar :size="30">{{ userStore.user?.nickname?.charAt(0) }}</el-avatar>
        <span>{{ userStore.user?.nickname }}</span>
        <el-tooltip content="返回前台" placement="top">
          <el-button text circle :icon="Back" aria-label="返回前台" @click="router.push('/')" />
        </el-tooltip>
      </div>
    </aside>

    <main class="admin-main">
      <div v-if="siteStore.loading" class="state-panel lf-card" v-loading="true" aria-live="polite">
        正在加载招领站…
      </div>
      <div v-else-if="siteStore.error" class="state-panel lf-card" role="alert">
        <el-empty description="招领站加载失败" :image="'/empty.svg'">
          <p class="state-detail">{{ siteStore.error }}</p>
          <el-button type="primary" round :icon="Refresh" @click="siteStore.load(true)">重试</el-button>
        </el-empty>
      </div>
      <div v-else-if="noSite" class="state-panel lf-card">
        <el-empty description="你还没有被指派为任何招领站的管理员" :image="'/empty.svg'">
          <p class="state-detail">可提交建站申请，平台审核通过后自动创建站点并开通管理权限；也可联系平台管理员加入已有站点。</p>
          <el-button type="primary" round @click="router.push('/site/apply')">申请创建招领站</el-button>
          <el-button round :icon="OfficeBuilding" @click="router.push('/sites')">浏览招领站</el-button>
        </el-empty>
      </div>
      <RouterView v-else v-slot="{ Component }">
        <Transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: calc(100vh - 62px - 110px);
}
.admin-side {
  width: 218px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--lf-border);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 62px;
  height: calc(100vh - 62px);
}
.admin-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 18px 16px 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lf-text);
  white-space: nowrap;
}
.site-switch {
  padding: 8px 16px 16px;
  border-bottom: 1px solid var(--lf-border);
}
.site-switch label {
  display: block;
  font-size: 12px;
  color: var(--lf-text-3);
  margin-bottom: 8px;
}
.site-address {
  margin-top: 8px;
  font-size: 12px;
  color: var(--lf-text-3);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-nav {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.admin-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13.5px;
  color: var(--lf-text-2);
  transition: background 180ms ease, color 180ms ease, transform 180ms ease;
}
.admin-link:hover {
  background: var(--lf-bg);
  color: var(--lf-primary);
  transform: translateX(2px);
}
.admin-link.active {
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  font-weight: 600;
}
.admin-link:focus-visible {
  outline: 2px solid var(--lf-primary);
  outline-offset: 2px;
}
.admin-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--lf-border);
  font-size: 13px;
}
.admin-user span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-main {
  flex: 1;
  min-width: 0;
  padding: 24px;
}
.state-panel {
  padding: 60px 24px;
  min-height: 260px;
}
.state-detail {
  font-size: 13px;
  color: var(--lf-text-3);
  margin-bottom: 14px;
}
@media (max-width: 860px) {
  .admin-side {
    width: 64px;
  }
  .admin-brand span,
  .site-switch,
  .admin-user span,
  .admin-link {
    font-size: 0;
  }
  .site-switch {
    display: none;
  }
  .admin-link {
    justify-content: center;
    font-size: 0;
  }
}
</style>
