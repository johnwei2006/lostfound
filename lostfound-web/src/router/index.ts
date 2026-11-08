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
        { path: 'users', name: 'adminUsers', component: () => import('@/views/admin/UsersView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.auth && !store.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !store.isAdmin) {
    return { name: 'home' }
  }
})

export default router
