<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Cloud, Monitor } from '@lucide/vue'
import { loadSiteConfig, siteConfig } from '@/composables/useSiteConfig'
import { mapMobilePathToWeb, saveClientModePreference } from '@repo/drive-core/client-mode'

defineProps<{
  title: string
  subtitle?: string
}>()

const route = useRoute()

onMounted(() => {
  void loadSiteConfig()
})

function switchToDesktop() {
  saveClientModePreference('desktop')
  window.location.assign(mapMobilePathToWeb(`/m${route.fullPath}`))
}
</script>

<template>
  <main class="min-h-dvh flex flex-col justify-between bg-background text-foreground px-5 py-8 safe-top safe-bottom">
    <div class="w-full max-w-sm mx-auto flex-1 flex flex-col justify-center">
      <header class="mb-7 text-center">
        <div class="mx-auto mb-3.5 flex h-13 w-13 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-sm overflow-hidden">
          <img
            v-if="siteConfig.siteLogoUrl"
            :src="siteConfig.siteLogoUrl"
            :alt="siteConfig.siteName"
            class="h-full w-full object-contain"
          />
          <Cloud v-else :size="26" aria-hidden="true" />
        </div>
        <p class="text-xs font-medium tracking-wide text-muted-foreground">{{ siteConfig.siteName }}</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-foreground">{{ title }}</h1>
        <p v-if="subtitle" class="mt-1.5 text-xs text-muted-foreground leading-relaxed">{{ subtitle }}</p>
      </header>

      <section class="rounded-2xl border border-border bg-card p-5 shadow-xs">
        <slot />
      </section>

      <div v-if="$slots.footer" class="mt-5 text-center text-xs text-muted-foreground">
        <slot name="footer" />
      </div>
    </div>

    <footer class="mt-8 flex justify-center">
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-muted-foreground active:bg-accent"
        @click="switchToDesktop"
      >
        <Monitor :size="14" />
        <span>切换到桌面版</span>
      </button>
    </footer>
  </main>
</template>
