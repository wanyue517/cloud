<script setup lang="ts">
import {
  Folder,
  House,
  Plus,
  Star,
  Clock3,
  Share2,
  LockKeyhole,
  Trash2,
  type LucideIcon
} from '@lucide/vue'

import { useRoute } from 'vue-router'

import {
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupAction,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton
} from '@/components/ui/sidebar'

interface MenuItem {
  label: string
  icon: LucideIcon
  path: string
}

interface MenuGroup {
  key: string
  name: string
  items: MenuItem[]
  control?: {
    icon: LucideIcon
    onClick: () => void
  }
}

const route = useRoute()

const menus: MenuGroup[] = [
  {
    key: 'spaces',
    name: '网盘空间',
    control: {
      icon: Plus,
      onClick: () => {
        // 打开新建空间弹窗
      }
    },
    items: [
      {
        label: '生活空间',
        icon: Folder,
        path: '/app/spaces/1'
      },
      {
        label: '工作空间',
        icon: Folder,
        path: '/app/spaces/2'
      }
    ]
  },
  {
    key: 'tools',
    name: '常用工具',
    items: [
      {
        label: '快捷访问',
        icon: House,
        path: '/app/quick'
      },
      {
        label: '我的收藏',
        icon: Star,
        path: '/app/star'
      },
      {
        label: '最近使用',
        icon: Clock3,
        path: '/app/recent'
      }
    ]
  },
  {
    key: 'collaboration',
    name: '协作与安全',
    items: [
      {
        label: '共享中心',
        icon: Share2,
        path: '/app/share'
      },
      {
        label: '私密空间',
        icon: LockKeyhole,
        path: '/app/private'
      },
      {
        label: '回收站',
        icon: Trash2,
        path: '/app/recycle-bin'
      }
    ]
  }
]

const isActive = (path: string) =>
    route.path === path ||
    route.path.startsWith(`${path}/`)
</script>

<template>
  <Sidebar collapsible="icon">
    <!-- Logo -->
    <SidebarHeader>
      <div class="flex h-10 items-center gap-2 px-2">
        <Folder class="size-5 shrink-0" />

        <span
            class="truncate font-semibold group-data-[collapsible=icon]:hidden"
        >
          Cloudlane
        </span>
      </div>
    </SidebarHeader>

    <!-- 菜单 -->
    <SidebarContent>
      <SidebarGroup
          v-for="group in menus"
          :key="group.key"
      >
        <SidebarGroupLabel>
          {{ group.name }}
        </SidebarGroupLabel>

        <!-- 分组操作按钮 -->
        <SidebarGroupAction
            v-if="group.control"
            :title="`新增${group.name}`"
            @click="group.control.onClick"
        >
          <component :is="group.control.icon" />
        </SidebarGroupAction>

        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem
                v-for="item in group.items"
                :key="item.path"
            >
              <SidebarMenuButton
                  as-child
                  :is-active="isActive(item.path)"
                  :tooltip="item.label"
              >
                <RouterLink :to="item.path">
                  <component :is="item.icon" />
                  <span>{{ item.label }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>
  </Sidebar>
</template>