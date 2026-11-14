<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, User as UserIcon, ArrowDown, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { ViewMode } from '@/stores/user'
import { useSiteStore } from '@/stores/site'
import { messageApi } from '@/api'
import { openQrLogin } from '@/composables/useQrLogin'
import QrLoginDialog from '@/components/QrLoginDialog.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const siteStore = useSiteStore()

const isDarkHeader = computed(() => route.path === '/')
const keyword = ref('')

const unread = ref(0)
const loadUnread = async () => {
  if (!store.isLoggedIn) return
  try {
    unread.value = await messageApi.unreadCount()
  } catch {
    /* ignore */
  }
}
watch(() => store.isLoggedIn, loadUnread, { immediate: true })
setInterval(loadUnread, 60_000)

// 站点管理员登录（账号密码或微信扫码共用 setSession）后选择视图身份；
// needsIdentityChoice 要求 siteLoaded，确保已拿到 /me 的站点管理范围
const identityDialogVisible = ref(false)
watch(
  () => store.needsIdentityChoice,
  (need) => { identityDialogVisible.value = need },
  { immediate: true }
)
const chooseIdentity = (mode: ViewMode) => {
  store.setViewMode(mode)
  identityDialogVisible.value = false
  if (mode === 'manager') router.push('/manage')
}
const switchIdentity = () => {
  const next: ViewMode = store.isManagerView ? 'personal' : 'manager'
  store.setViewMode(next)
  siteStore.reset()
  router.push(next === 'manager' ? '/manage' : '/')
}

// 401 时 request.ts 已清 localStorage，这里同步 Pinia，且不再整页跳登录
const onAuthExpired = () => {
  store.logout()
  siteStore.reset()
}
onMounted(() => {
  window.addEventListener('lf:auth-expired', onAuthExpired)
  // 登录响应只含基础 User，启动时刷新一次以拿到站点管理范围与最新角色
  if (store.isLoggedIn) store.refresh()
})
onUnmounted(() => window.removeEventListener('lf:auth-expired', onAuthExpired))

const onSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  router.push({ name: 'foundList', query: { keyword: kw } })
}

const onCommand = (cmd: string) => {
  if (cmd === 'logout') {
    store.logout()
    siteStore.reset()
    ElMessage.success('已退出登录')
    router.push('/')
  } else if (cmd === 'switchIdentity') {
    switchIdentity()
  } else {
    router.push(cmd)
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header" :class="{ 'app-header--dark': isDarkHeader }">
      <div class="lf-container header-inner">
        <RouterLink to="/" class="logo">
          <svg viewBox="0 0 48 48" class="logo-icon">
            <rect width="48" height="48" rx="12" fill="currentColor" opacity="0.12" />
            <path
              d="M24 10a10 10 0 0 1 10 10c0 7-10 18-10 18S14 27 14 20a10 10 0 0 1 10-10z"
              fill="currentColor"
            />
            <circle cx="24" cy="20" r="4.5" class="logo-dot" />
          </svg>
          <span class="logo-text">百觅</span>
        </RouterLink>

        <nav class="nav">
          <RouterLink to="/" class="nav-link" :class="{ active: route.path === '/' }">首页</RouterLink>
          <RouterLink to="/lost" class="nav-link" :class="{ active: route.path.startsWith('/lost') }">寻失物</RouterLink>
          <RouterLink to="/found" class="nav-link" :class="{ active: route.path.startsWith('/found') }">找失主</RouterLink>
          <RouterLink to="/sites" class="nav-link" :class="{ active: route.path.startsWith('/sites') }">招领站</RouterLink>
          <RouterLink to="/map" class="nav-link" :class="{ active: route.path === '/map' }">地图</RouterLink>
        </nav>

        <div class="header-search">
          <el-input
            v-model="keyword"
            placeholder="搜索物品名称、描述…"
            :prefix-icon="Search"
            clearable
            @keyup.enter="onSearch"
          />
        </div>

        <div class="header-actions">
          <template v-if="store.isLoggedIn">
            <el-badge :value="unread" :hidden="unread === 0" class="bell-badge">
              <button class="icon-btn" @click="router.push('/messages')" aria-label="消息">
                <el-icon :size="18"><Bell /></el-icon>
              </button>
            </el-badge>
            <el-dropdown trigger="click" @command="onCommand">
              <span class="user-entry">
                <el-avatar :size="30" :src="store.user?.avatar">
                  {{ store.user?.nickname?.charAt(0) || 'U' }}
                </el-avatar>
                <span class="user-name">{{ store.user?.nickname }}</span>
                <el-icon :size="12"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <!-- 超级管理员：个人中心 + 管理后台（招领站/用户/招领信息分组在后台侧栏） -->
                  <template v-if="store.isAdmin">
                    <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                    <el-dropdown-item command="/admin" divided>管理后台</el-dropdown-item>
                  </template>
                  <!-- 站点管理员·管理员身份 -->
                  <template v-else-if="store.isManagerView">
                    <el-dropdown-item command="/profile">个人信息</el-dropdown-item>
                    <el-dropdown-item command="/manage" divided>招领站管理</el-dropdown-item>
                    <el-dropdown-item command="/site/applications">招领站申请</el-dropdown-item>
                    <el-dropdown-item command="switchIdentity" divided>切换为个人身份</el-dropdown-item>
                  </template>
                  <!-- 普通用户，或站点管理员·个人身份 -->
                  <template v-else>
                    <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                    <el-dropdown-item command="/matches">匹配推荐</el-dropdown-item>
                    <el-dropdown-item command="/site/apply" divided>申请创建招领站</el-dropdown-item>
                    <el-dropdown-item command="/site/applications">我的建站申请</el-dropdown-item>
                    <el-dropdown-item v-if="store.isSiteManager" command="switchIdentity" divided>切换为管理员身份</el-dropdown-item>
                  </template>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button text class="login-btn" @click="openQrLogin()">微信扫码</el-button>
            <el-button text class="login-btn" @click="router.push('/login')">账号登录</el-button>
            <el-button text class="login-btn" @click="router.push('/site/apply')">申请建站</el-button>
            <el-button type="primary" round @click="router.push('/login?tab=register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="app-main">
      <RouterView v-slot="{ Component }">
        <Transition name="fade-slide" mode="out-in">
          <component :is="Component" :key="route.fullPath" />
        </Transition>
      </RouterView>
    </main>

    <footer class="app-footer">
      <div class="lf-container">
        <p>百觅 · 让每一份遗失都有归途</p>
        <p class="footer-sub">失物招领平台</p>
      </div>
    </footer>

    <!-- 全局唯一扫码登录弹层，避免多页面重复轮询 -->
    <QrLoginDialog />

    <!-- 站点管理员登录后的视图身份选择：仅影响菜单显示，不改真实权限 -->
    <el-dialog
      v-model="identityDialogVisible"
      title="选择登录身份"
      width="420px"
      align-center
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <p style="margin: 0; line-height: 1.7; color: #475569">
        你同时是招领站管理员，请选择本次使用系统的身份。身份只影响菜单显示，之后可在顶栏随时切换。
      </p>
      <template #footer>
        <el-button round @click="chooseIdentity('personal')">以个人身份使用</el-button>
        <el-button type="primary" round @click="chooseIdentity('manager')">以管理员身份使用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--lf-border);
  transition: background 0.3s;
}
.app-header--dark {
  background: linear-gradient(135deg, #1e40af, #2563eb 55%, #3b82f6);
  border-bottom-color: transparent;
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 22px;
  height: 62px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lf-primary);
  flex-shrink: 0;
}
.logo-icon {
  width: 32px;
  height: 32px;
}
.logo-dot {
  fill: #f97316;
}
.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
  color: inherit;
}
.app-header--dark .logo {
  color: #fff;
}
.app-header--dark .logo-dot {
  fill: #fdba74;
}
.nav {
  display: flex;
  gap: 4px;
}
.nav-link {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--lf-text-2);
  transition: all 0.25s var(--ease-out);
  white-space: nowrap;
}
.nav-link:hover {
  color: var(--lf-primary);
  background: var(--lf-primary-soft);
}
.nav-link.active {
  color: var(--lf-primary);
  background: var(--lf-primary-soft);
  font-weight: 600;
}
.app-header--dark .nav-link {
  color: rgba(255, 255, 255, 0.82);
}
.app-header--dark .nav-link:hover,
.app-header--dark .nav-link.active {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
.header-search {
  flex: 1;
  max-width: 300px;
  margin-left: auto;
}
.app-header--dark :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.16);
  box-shadow: none;
}
.app-header--dark :deep(.el-input__inner) {
  color: #fff;
}
.app-header--dark :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.6);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--lf-text-2);
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s;
}
.icon-btn:hover {
  color: var(--lf-primary);
  background: var(--lf-primary-soft);
}
.app-header--dark .icon-btn {
  color: #fff;
}
.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 999px;
  transition: background 0.2s;
}
.user-entry:hover {
  background: var(--lf-primary-soft);
}
.user-name {
  font-size: 13px;
  color: var(--lf-text);
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-header--dark .user-name {
  color: #fff;
}
.app-header--dark .login-btn {
  color: #fff;
}
.app-main {
  min-height: calc(100vh - 62px - 120px);
}
.app-footer {
  background: #1f2937;
  color: rgba(255, 255, 255, 0.9);
  padding: 34px 0;
  text-align: center;
  font-size: 14px;
  letter-spacing: 0.5px;
}
.footer-sub {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
@media (max-width: 900px) {
  .header-search,
  .nav {
    display: none;
  }
}
</style>
