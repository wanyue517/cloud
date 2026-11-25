import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@repo/session/auth'
import { request } from '@repo/session/request'
import { adminAccess } from '@repo/session/navigation'
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', component: () => import('@/views/index.vue') },
    { path: '/users', component: () => import('@/views/index.vue') },
    { path: '/unavailable', component: () => import('@/views/unavailable.vue') },
    { path: '/404', component: () => import('@/views/not-found.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})
router.beforeEach(async to => {
  if (to.path === '/404' || to.path === '/unavailable') return
  try {
    const result = await adminAccess(to.fullPath, getAccessToken, () => request.get<{ admin: boolean }>('/v1/me'))
    if (result === 'not-found') return '/404'
    if (result !== 'allow') { window.location.replace(result); return false }
  } catch { return getAccessToken() ? '/unavailable' : false }
})
router.onError(error => { console.error('后台路由加载失败', error) })
export default router
