import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router";
import { getAccessToken } from '@/lib/auth'
import { loginUrl } from '@repo/session/navigation'

const routes: RouteRecordRaw[] = [
    {
        path: '/',
        redirect: '/app'
    },
    {
        path: '/auth',
        name: 'auth',
        children: [
            {
                path: 'login',
                name: 'login',
                component: () => import('@/views/auth/login/index.vue')
            },
            {
                path: 'register',
                name: 'register',
                component: () => import('@/views/auth/register/index.vue')
            },
            {
                path: 'forgot-password',
                name: 'forgot-password',
                component: () => import('@/views/auth/forgot-password/index.vue')
            }
        ]
    },
    {
        path: '/app',
        name: 'app',
        component: () => import('@/layout/index.vue'),
        children: [
            { path: '', component: () => import('@/views/drive/index.vue') },
            {
                path: 'spaces/:spaceId',
                name: 'spaces',
                component: () => import('@/views/drive/index.vue')
            },
            {
                path: 'spaces/:spaceId/folders/:folderId',
                name: 'folders',
                component: () => import('@/views/drive/index.vue')
            }
        ]
    },
    // {
    //
    //     path: '/share/:token',
    //     name: 'share',
    //     component: () => import('@/views/Share.vue')
    // },
    // {
    //     path: '/404',
    //     name: '404',
    //     component: () => import('@/views/NotFound.vue')
    // },
    {
        path: '/:pathMatch(.*)*',
        component: () => import('@/views/not-found.vue')
    }
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

router.beforeEach(to => {
    if ((to.path === '/app' || to.path.startsWith('/app/')) && !getAccessToken()) return loginUrl(to.fullPath)
})
export default router
