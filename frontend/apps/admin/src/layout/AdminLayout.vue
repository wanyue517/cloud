<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import {
  Activity,
  Bell,
  FileSliders,
  Globe,
  HardDrive,
  LayoutDashboard,
  ListChecks,
  LogOut,
  PanelLeft,
  ShieldCheck,
  Users,
  X,
  type LucideIcon,
} from '@lucide/vue'
import { Button } from '@repo/ui/button'
import ThemeSettings from '@repo/ui/ThemeSettings.vue'
import { logout } from '@repo/session/session'
import { loginUrl } from '@repo/session/navigation'
import { ADMIN_NAV_GROUPS, ADMIN_NAV_ITEMS } from './navigation'

const route = useRoute()
const sidebarCollapsed = ref(false)
const mobileMenuOpen = ref(false)
const signingOut = ref(false)
const error = ref('')

const iconMap: Record<string, LucideIcon> = {
  'admin-dashboard': LayoutDashboard,
  'admin-users': Users,
  'admin-messages': Bell,
  'admin-storage': HardDrive,
  'admin-settings-site': Globe,
  'admin-settings-auth': ShieldCheck,
  'admin-settings-files': FileSliders,
  'admin-tasks': ListChecks,
  'admin-logs': Activity,
}

const currentItem = computed(() =>
  ADMIN_NAV_ITEMS.find(item => item.name === route.name) ?? ADMIN_NAV_ITEMS[0]!,
)

const groupedNav = computed(() =>
  ADMIN_NAV_GROUPS.map(group => ({
    group,
    items: ADMIN_NAV_ITEMS.filter(item => item.group === group),
  })),
)

function isActive(to: string) {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(`${to}/`)
}

function toggleNav() {
  if (typeof window !== 'undefined' && window.innerWidth <= 760) {
    mobileMenuOpen.value = !mobileMenuOpen.value
  } else {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && mobileMenuOpen.value) closeMobileMenu()
}

async function signOut() {
  if (signingOut.value) return
  signingOut.value = true
  error.value = ''
  try {
    await logout()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '退出失败'
  } finally {
    signingOut.value = false
    window.location.assign(loginUrl('/admin/'))
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="min-h-screen flex bg-muted/30 text-foreground overflow-x-clip">
    <div
      v-if="mobileMenuOpen"
      class="fixed inset-0 z-40 bg-black/50 md:hidden"
      aria-hidden="true"
      @click="closeMobileMenu"
    />

    <aside
      aria-label="后台导航"
      class="shrink-0 bg-sidebar text-sidebar-foreground border-r border-sidebar-border flex flex-col transition-all duration-200 max-md:fixed max-md:inset-y-0 max-md:left-0 max-md:z-50 max-md:w-64"
      :class="[
        sidebarCollapsed ? 'md:w-0 md:overflow-hidden md:border-r-0 md:opacity-0 md:pointer-events-none' : 'md:w-60',
        mobileMenuOpen ? 'max-md:translate-x-0 max-md:shadow-xl' : 'max-md:-translate-x-full',
      ]"
    >
      <div class="h-14 px-4 border-b border-sidebar-border flex items-center justify-between gap-2 shrink-0">
        <RouterLink to="/" class="flex items-center gap-2.5 min-w-0 font-semibold text-base" @click="closeMobileMenu">
          <span class="size-7 rounded-md bg-primary/10 text-primary flex items-center justify-center shrink-0">
            <ShieldCheck class="size-4" />
          </span>
          <span class="truncate">Cloudlane</span>
        </RouterLink>
        <Button variant="ghost" size="icon-xs" class="md:hidden" aria-label="关闭后台导航" @click="closeMobileMenu">
          <X class="size-4" />
        </Button>
      </div>

      <nav class="flex-1 overflow-y-auto p-3 space-y-5" aria-label="后台功能菜单">
        <section v-for="section in groupedNav" :key="section.group" class="space-y-1">
          <p class="px-2.5 text-xs font-medium text-muted-foreground tracking-wide">{{ section.group }}</p>
          <RouterLink
            v-for="item in section.items"
            :key="item.name"
            :to="item.to"
            :aria-current="isActive(item.to) ? 'page' : undefined"
            class="flex items-center gap-2.5 px-2.5 py-2 rounded-md text-sm transition-colors"
            :class="isActive(item.to)
              ? 'bg-sidebar-accent text-sidebar-accent-foreground font-medium'
              : 'text-sidebar-foreground/85 hover:bg-muted hover:text-foreground'"
            @click="closeMobileMenu"
          >
            <component :is="iconMap[item.name]" class="size-4 shrink-0" />
            <span class="truncate flex-1">{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>

      <div class="p-3 border-t border-sidebar-border text-xs text-muted-foreground shrink-0">
        独立管理后台 · 与用户工作台解耦
      </div>
    </aside>

    <div class="flex-1 min-w-0 flex flex-col">
      <header class="h-14 border-b border-border bg-background px-4 md:px-6 flex items-center justify-between gap-3 sticky top-0 z-30">
        <div class="flex items-center gap-2.5 min-w-0">
          <Button
            variant="ghost"
            size="icon-sm"
            :aria-label="sidebarCollapsed ? '展开后台导航' : '收起后台导航'"
            @click="toggleNav"
          >
            <PanelLeft class="size-4" />
          </Button>
          <h1 class="font-semibold text-base md:text-lg truncate">Cloudlane 管理后台</h1>
          <span class="hidden sm:inline text-muted-foreground">/</span>
          <span class="hidden sm:inline text-sm text-muted-foreground truncate">{{ currentItem.label }}</span>
        </div>

        <div class="flex items-center gap-2 md:gap-3 shrink-0">
          <div class="w-28 sm:w-32">
            <ThemeSettings />
          </div>
          <Button as-child variant="outline" size="sm">
            <a href="/app">返回网盘</a>
          </Button>
          <Button variant="outline" size="sm" :disabled="signingOut" @click="signOut">
            <LogOut class="size-4" />
            退出登录
          </Button>
        </div>
      </header>

      <main class="flex-1 min-w-0 p-4 md:p-6 lg:p-8 max-w-6xl w-full mx-auto space-y-6">
        <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {{ error }}
        </p>
        <RouterView />
      </main>
    </div>
  </div>
</template>
