import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@repo/session/auth'
import { request } from '@repo/session/request'
import { adminAccess } from '@repo/session/navigation'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    children: [
      { path: '', name: 'admin-dashboard', component: () => import('@/views/dashboard/index.vue') },
      { path: 'users', name: 'admin-users', component: () => import('@/views/users/index.vue') },
      { path: 'messages', name: 'admin-messages', component: () => import('@/views/messages/index.vue') },
      { path: 'storage', name: 'admin-storage', component: () => import('@/views/storage/index.vue') },
      { path: 'settings/site', name: 'admin-settings-site', component: () => import('@/views/settings/site.vue') },
      { path: 'settings/auth', name: 'admin-settings-auth', component: () => import('@/views/settings/auth.vue') },
      { path: 'settings/files', name: 'admin-settings-files', component: () => import('@/views/settings/files.vue') },
      { path: 'tasks', name: 'admin-tasks', component: () => import('@/views/tasks/index.vue') },
      { path: 'logs', name: 'admin-logs', component: () => import('@/views/logs/index.vue') },
    ],
  },
  { path: '/unavailable', name: 'admin-unavailable', component: () => import('@/views/unavailable.vue') },
  { path: '/404', name: 'admin-not-found', component: () => import('@/views/not-found.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/404' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async to => {
  if (to.path === '/404' || to.path === '/unavailable') return
  try {
    const result = await adminAccess(to.fullPath, getAccessToken, () => request.get<{ admin: boolean }>('/v1/me'))
    if (result === 'not-found') return '/404'
    if (result !== 'allow') {
      window.location.replace(result)
      return false
    }
  } catch {
    return getAccessToken() ? '/unavailable' : false
  }
})

router.onError(error => {
  console.error('后台路由加载失败', error)
})

export default router
