<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Smartphone, X } from '@lucide/vue'
import {
  isMobileUserAgent,
  loadClientModePreference,
  mapWebPathToMobile,
  saveClientModePreference,
} from '@repo/drive-core/client-mode'

const visible = ref(false)

onMounted(() => {
  const pref = loadClientModePreference()
  if (pref === 'desktop') return
  const narrow = typeof window !== 'undefined' && window.innerWidth <= 760
  if (isMobileUserAgent() || narrow) {
    visible.value = true
  }
})

function switchToMobile() {
  saveClientModePreference('mobile')
  const target = mapWebPathToMobile(window.location.pathname + window.location.search + window.location.hash)
  window.location.assign(target)
}

function stayDesktop() {
  saveClientModePreference('desktop')
  visible.value = false
}
</script>

<template>
  <div
    v-if="visible"
    role="region"
    aria-label="移动端切换提示"
    class="fixed bottom-4 left-4 right-4 z-50 mx-auto max-w-md flex items-center justify-between gap-3 rounded-xl border border-border bg-card/95 px-3.5 py-2.5 text-sm shadow-lg backdrop-blur"
  >
    <div class="flex items-center gap-2 min-w-0">
      <Smartphone :size="18" class="shrink-0 text-primary" />
      <span class="truncate text-foreground">检测到移动设备，切换到移动版体验更佳</span>
    </div>
    <div class="flex items-center gap-1.5 shrink-0">
      <button
        type="button"
        class="rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground"
        @click="switchToMobile"
      >
        前往移动版
      </button>
      <button
        type="button"
        class="p-1 rounded-lg text-muted-foreground hover:text-foreground"
        aria-label="留在桌面版"
        @click="stayDesktop"
      >
        <X :size="16" />
      </button>
    </div>
  </div>
</template>
