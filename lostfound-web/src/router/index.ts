import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/found', name: 'foundList', component: () => import('@/views/ListView.vue'), props: { type: 'found' } },
    { path: '/lost', name: 'lostList', component: () => import('@/views/ListView.vue'), props: { type: 'lost' } },
    { path: '/found/:id', name: 'foundDetail', component: () => import('@/views/DetailView.vue'), props: (r) => ({ type: 'found', id: Number(r.params.id) }) },
    { path: '/lost/:id', name: 'lostDetail', component: () => import('@/views/DetailView.vue'), props: (r) => ({ type: 'lost', id: Number(r.params.id) }) },
    { path: '/publish', name: 'publish', component: () => import('@/views/PublishView.vue') },
    { path: '/sites', name: 'sites', component: () => import('@/views/SiteListView.vue') },
    { path: '/sites/:id', name: 'siteDetail', component: () => import('@/views/SiteDetailView.vue') },
    { path: '/site/apply', name: 'siteApply', component: () => import('@/views/SiteApplyView.vue'), meta: { auth: true } },
    { path: '/site/applications', name: 'mySiteApplications', component: () => import('@/views/MySiteApplicationsView.vue'), meta: { auth: true } },
    { path: '/map', name: 'map', component: () => import('@/views/MapView.vue') },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
    { path: '/messages', name: 'messages', component: () => import('@/views/MessageView.vue'), meta: { auth: true } },
    { path: '/matches', name: 'matches', component: () => import('@/views/MatchView.vue'), meta: { auth: true } },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { auth: true, admin: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'adminDashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'items', name: 'adminItems', component: () => import('@/views/admin/ItemsView.vue') },
        { path: 'sites', name: 'adminSites', component: () => import('@/views/admin/SitesView.vue') },
        { path: 'site-applications', name: 'adminSiteApplications', component: () => import('@/views/admin/SiteApplicationsView.vue') },
        { path: 'users', name: 'adminUsers', component: () => import('@/views/admin/UsersView.vue') },
      ],
    },
    {
      path: '/manage',
      component: () => import('@/views/manage/ManageLayout.vue'),
      meta: { auth: true, siteManager: true },
      children: [
        { path: '', redirect: '/manage/items' },
        { path: 'items', name: 'manageItems', component: () => import('@/views/manage/SiteItemsView.vue') },
        { path: 'transfers', name: 'manageTransfers', component: () => import('@/views/manage/TransferReviewView.vue') },
        { path: 'returns', name: 'manageReturns', component: () => import('@/views/manage/ReturnManageView.vue') },
        { path: 'site', name: 'manageSiteSettings', component: () => import('@/views/manage/SiteSettingsView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  if (to.meta.auth && !store.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !store.isAdmin) {
    return { name: 'home' }
  }
  if (to.meta.siteManager) {
    // 登录响应里没有站点范围，进入 /manage 前拉一次 /api/auth/me
    if (!store.siteLoaded || !store.isSiteManager) await store.refresh()
    if (!store.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
    if (!store.isSiteManager) return { name: 'home' }
  }
})

export default router
