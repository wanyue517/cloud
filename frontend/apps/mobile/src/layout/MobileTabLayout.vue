<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tabbar as VanTabbar, TabbarItem as VanTabbarItem } from 'vant'
import { Folder, Home, Share2, User } from '@lucide/vue'
import { mobileUi } from '@/stores/mobile-ui'

const route = useRoute()
const router = useRouter()

const activeTab = computed({
  get: () => String(route.meta.tab || 'home'),
  set: (value: string) => {
    const target = `/${value}`
    if (route.path !== target) void router.push(target)
  },
})

const meBadge = computed(() => {
  const total = mobileUi.unreadMessageCount + mobileUi.activeUploadCount
  if (total <= 0) return undefined
  return total > 99 ? '99+' : total
})
</script>

<template>
  <div class="min-h-dvh flex flex-col bg-background text-foreground">
    <div class="flex-1 min-h-0 flex flex-col pb-14">
      <RouterView />
    </div>

    <VanTabbar v-show="!mobileUi.tabBarHidden" v-model="activeTab" fixed safe-area-inset-bottom :border="true">
      <VanTabbarItem name="home">
        <template #icon="{ active }">
          <Home :size="20" :stroke-width="active ? 2.2 : 1.8" />
        </template>
        首页
      </VanTabbarItem>
      <VanTabbarItem name="files">
        <template #icon="{ active }">
          <Folder :size="20" :stroke-width="active ? 2.2 : 1.8" />
        </template>
        文件
      </VanTabbarItem>
      <VanTabbarItem name="shared">
        <template #icon="{ active }">
          <Share2 :size="20" :stroke-width="active ? 2.2 : 1.8" />
        </template>
        共享
      </VanTabbarItem>
      <VanTabbarItem name="me" :badge="meBadge">
        <template #icon="{ active }">
          <User :size="20" :stroke-width="active ? 2.2 : 1.8" />
        </template>
        我的
      </VanTabbarItem>
    </VanTabbar>
  </div>
</template>
