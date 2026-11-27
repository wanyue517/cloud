<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Monitor } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { mapMobilePathToWeb, saveClientModePreference } from '@repo/drive-core/client-mode'

const route = useRoute()
const router = useRouter()

const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : 'Cloudlane'))
const isTabRoute = computed(() => Boolean(route.meta.tab))

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.replace('/home')
  }
}

function switchToDesktop() {
  saveClientModePreference('desktop')
  window.location.assign(mapMobilePathToWeb(`/m${route.fullPath}`))
}
</script>

<template>
  <div class="flex-1 flex flex-col bg-background text-foreground">
    <header class="sticky top-0 z-20 flex items-center justify-between gap-2 h-13 px-4 border-b border-border bg-background/95 backdrop-blur safe-top">
      <div class="flex items-center gap-2 min-w-0">
        <button
          v-if="!isTabRoute"
          type="button"
          class="p-1.5 -ml-1.5 rounded-lg text-muted-foreground hover:text-foreground"
          aria-label="返回"
          @click="goBack"
        >
          <ArrowLeft :size="20" />
        </button>
        <h1 class="text-base font-semibold truncate">{{ title }}</h1>
      </div>
      <Button variant="ghost" size="sm" class="h-8 px-2 text-xs text-muted-foreground" @click="switchToDesktop">
        <Monitor :size="15" />
        <span>桌面版</span>
      </Button>
    </header>

    <main class="flex-1 px-4 py-6">
      <div class="rounded-xl border border-border bg-card p-4 text-sm text-muted-foreground">
        <p class="font-medium text-foreground">{{ title }}</p>
        <p class="mt-1 text-xs">当前路由：{{ route.fullPath }}</p>
      </div>
    </main>
  </div>
</template>
