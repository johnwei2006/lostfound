<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { DataAnalysis, Files, OfficeBuilding, User, Back } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const menus = [
  { path: '/admin/dashboard', label: '数据统计', icon: DataAnalysis },
  { path: '/admin/items', label: '物品审核', icon: Files },
  { path: '/admin/sites', label: '招领站管理', icon: OfficeBuilding },
  { path: '/admin/users', label: '用户管理', icon: User },
]
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <div class="admin-brand">
        <svg viewBox="0 0 48 48" width="28" height="28">
          <rect width="48" height="48" rx="12" fill="#2563EB" opacity="0.14"/>
          <path d="M24 10a10 10 0 0 1 10 10c0 7-10 18-10 18S14 27 14 20a10 10 0 0 1 10-10z" fill="#2563EB"/>
          <circle cx="24" cy="20" r="4.5" fill="#F97316"/>
        </svg>
        <span>百觅 · 管理后台</span>
      </div>
      <nav class="admin-nav">
        <RouterLink v-for="m in menus" :key="m.path" :to="m.path" class="admin-link" :class="{ active: route.path.startsWith(m.path) }">
          <el-icon :size="16"><component :is="m.icon" /></el-icon>
          {{ m.label }}
        </RouterLink>
      </nav>
      <div class="admin-user">
        <el-avatar :size="30">{{ store.user?.nickname?.charAt(0) }}</el-avatar>
        <span>{{ store.user?.nickname }}</span>
        <el-tooltip content="返回前台" placement="top">
          <el-button text circle :icon="Back" @click="router.push('/')" />
        </el-tooltip>
      </div>
    </aside>
    <main class="admin-main">
      <RouterView v-slot="{ Component }">
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
  width: 210px;
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
  padding: 18px 18px 14px;
  font-size: 14px;
  font-weight: 600;
  color: var(--lf-text);
}
.admin-nav {
  flex: 1;
  padding: 8px 12px;
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
  transition: all 0.2s;
}
.admin-link:hover {
  background: var(--lf-bg);
  color: var(--lf-primary);
}
.admin-link.active {
  background: var(--lf-primary-soft);
  color: var(--lf-primary);
  font-weight: 600;
}
.admin-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid var(--lf-border);
  font-size: 13px;
}
.admin-user span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-main {
  flex: 1;
  min-width: 0;
  padding: 24px;
}
@media (max-width: 860px) {
  .admin-side {
    width: 64px;
  }
  .admin-brand span,
  .admin-user span {
    display: none;
  }
  .admin-link {
    justify-content: center;
  }
}
</style>
