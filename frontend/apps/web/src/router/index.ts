import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/lib/auth'
import { loginUrl } from '@repo/session/navigation'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/app' },
  {
    path: '/auth',
    redirect: '/auth/login',
    children: [
      { path: 'login', name: 'login', component: () => import('@/views/auth/login/index.vue') },
      { path: 'register', name: 'register', component: () => import('@/views/auth/register/index.vue') },
      { path: 'forgot-password', name: 'forgot-password', component: () => import('@/views/auth/forgot-password/index.vue') },
    ],
  },
  {
    path: '/app',
    component: () => import('@/layout/index.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'app', component: () => import('@/views/drive/index.vue') },
      {
        path: 'spaces/:spaceId(\\d+)',
        name: 'spaces',
        component: () => import('@/views/drive/index.vue'),
      },
      {
        path: 'spaces/:spaceId(\\d+)/folders/:folderId(\\d+)',
        name: 'folders',
        component: () => import('@/views/drive/index.vue'),
      },
    ],
  },
  { path: '/404', name: 'not-found', component: () => import('@/views/not-found.vue') },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/not-found.vue') },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(to => {
  if (to.meta.requiresAuth && !getAccessToken()) return loginUrl(to.fullPath)
})

export default router
