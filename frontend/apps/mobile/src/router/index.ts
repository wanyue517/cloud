import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@repo/session/auth'
import { resolveMobileRouteGuard } from './guard'

export const mobileRoutes: RouteRecordRaw[] = [
  { path: '/', redirect: '/home' },
  {
    path: '/auth',
    redirect: '/auth/login',
    children: [
      {
        path: 'login',
        name: 'mobile-login',
        meta: { guestOnly: true, title: '登录' },
        component: () => import('@/views/auth/login.vue'),
      },
      {
        path: 'register',
        name: 'mobile-register',
        meta: { guestOnly: true, title: '注册账号' },
        component: () => import('@/views/auth/register.vue'),
      },
      {
        path: 'forgot-password',
        name: 'mobile-forgot-password',
        meta: { guestOnly: true, title: '找回密码' },
        component: () => import('@/views/auth/forgot-password.vue'),
      },
    ],
  },
  {
    path: '/s/:token',
    name: 'mobile-public-share',
    meta: { title: '公开分享' },
    component: () => import('@/views/share/public.vue'),
  },
  {
    path: '/settings/server',
    name: 'mobile-settings-server',
    meta: { title: '服务器地址配置' },
    component: () => import('@/views/settings/server.vue'),
  },
  {
    path: '/',
    component: () => import('@/layout/MobileTabLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'mobile-home',
        meta: { requiresAuth: true, tab: 'home', title: '首页' },
        component: () => import('@/views/home/index.vue'),
      },
      {
        path: 'files',
        name: 'mobile-files',
        meta: { requiresAuth: true, tab: 'files', title: '文件' },
        component: () => import('@/views/files/index.vue'),
      },
      {
        path: 'files/:spaceId(\\d+)',
        name: 'mobile-space',
        meta: { requiresAuth: true, tab: 'files', title: '空间文件' },
        component: () => import('@/views/files/index.vue'),
      },
      {
        path: 'files/:spaceId(\\d+)/folders/:folderId(\\d+)',
        name: 'mobile-folder',
        meta: { requiresAuth: true, tab: 'files', title: '文件夹' },
        component: () => import('@/views/files/index.vue'),
      },
      {
        path: 'shared',
        name: 'mobile-shared',
        meta: { requiresAuth: true, tab: 'shared', title: '共享中心' },
        component: () => import('@/views/shared/index.vue'),
      },
      {
        path: 'shared/received/:shareId(\\d+)',
        name: 'mobile-shared-folder',
        meta: { requiresAuth: true, tab: 'shared', title: '与我共享' },
        component: () => import('@/views/shared/received.vue'),
      },
      {
        path: 'me',
        name: 'mobile-me',
        meta: { requiresAuth: true, tab: 'me', title: '我的' },
        component: () => import('@/views/me/index.vue'),
      },
    ],
  },
  {
    path: '/search',
    name: 'mobile-search',
    meta: { requiresAuth: true, title: '全局搜索' },
    component: () => import('@/views/search/index.vue'),
  },
  {
    path: '/tasks',
    name: 'mobile-tasks',
    meta: { requiresAuth: true, title: '传输任务' },
    component: () => import('@/views/tasks/index.vue'),
  },
  {
    path: '/messages',
    name: 'mobile-messages',
    meta: { requiresAuth: true, title: '消息中心' },
    component: () => import('@/views/messages/index.vue'),
  },
  {
    path: '/private',
    name: 'mobile-private',
    meta: { requiresAuth: true, title: '私密空间' },
    component: () => import('@/views/private/index.vue'),
  },
  {
    path: '/trash',
    name: 'mobile-trash',
    meta: { requiresAuth: true, title: '回收站' },
    component: () => import('@/views/trash/index.vue'),
  },
  {
    path: '/photos',
    name: 'mobile-photos',
    meta: { requiresAuth: true, title: '云端相册' },
    component: () => import('@/views/photos/index.vue'),
  },
  {
    path: '/offline',
    name: 'mobile-offline',
    meta: { requiresAuth: true, title: '离线文件' },
    component: () => import('@/views/offline/index.vue'),
  },
  {
    path: '/tools/doc-scan',
    name: 'mobile-doc-scan',
    meta: { requiresAuth: true, title: '文档扫描转 PDF' },
    component: () => import('@/views/tools/doc-scan.vue'),
  },
  {
    path: '/settings',
    redirect: '/settings/profile',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'profile',
        name: 'mobile-settings-profile',
        meta: { requiresAuth: true, title: '个人资料' },
        component: () => import('@/views/settings/profile.vue'),
      },
      {
        path: 'security',
        name: 'mobile-settings-security',
        meta: { requiresAuth: true, title: '账户与安全' },
        component: () => import('@/views/settings/security.vue'),
      },
      {
        path: 'appearance',
        name: 'mobile-settings-appearance',
        meta: { requiresAuth: true, title: '主题与外观' },
        component: () => import('@/views/settings/appearance.vue'),
      },
      {
        path: 'files',
        name: 'mobile-settings-files',
        meta: { requiresAuth: true, title: '文件偏好' },
        component: () => import('@/views/settings/files.vue'),
      },
      {
        path: 'network',
        name: 'mobile-settings-network',
        meta: { requiresAuth: true, title: '网络与流量' },
        component: () => import('@/views/settings/network.vue'),
      },
      {
        path: 'photo-backup',
        name: 'mobile-settings-photo-backup',
        meta: { requiresAuth: true, title: '相册自动备份' },
        component: () => import('@/views/settings/photo-backup.vue'),
      },
    ],
  },
  {
    path: '/admin-overview',
    name: 'mobile-admin-overview',
    meta: { requiresAuth: true, title: '系统概览' },
    component: () => import('@/views/admin/overview.vue'),
  },
  {
    path: '/qr-confirm',
    name: 'mobile-qr-confirm',
    meta: { requiresAuth: true, title: '扫码登录 PC' },
    component: () => import('@/views/auth/qr-confirm.vue'),
  },
  {
    path: '/404',
    name: 'mobile-not-found',
    meta: { title: '页面不存在' },
    component: () => import('@/views/not-found.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'mobile-catch-all',
    meta: { title: '页面不存在' },
    component: () => import('@/views/not-found.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: mobileRoutes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

router.beforeEach(to => {
  return resolveMobileRouteGuard(
    {
      fullPath: to.fullPath,
      meta: {
        requiresAuth: Boolean(to.meta.requiresAuth),
        guestOnly: Boolean(to.meta.guestOnly),
      },
      query: to.query as Record<string, unknown>,
    },
    Boolean(getAccessToken()),
  )
})

router.afterEach(to => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : 'Cloudlane'
  if (typeof document !== 'undefined') {
    document.title = `${title} · Cloudlane`
  }
})

export default router
