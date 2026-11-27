<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Download, X } from '@lucide/vue'

const PWA_DISMISS_KEY = 'cloudlane.mobile.pwaDismissed'

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)
const showIosHint = ref(false)
const visible = ref(false)

function isStandalone(): boolean {
  if (typeof window === 'undefined') return true
  const nav = navigator as Navigator & { standalone?: boolean }
  return window.matchMedia('(display-mode: standalone)').matches || nav.standalone === true
}

function isDismissed(): boolean {
  try {
    return localStorage.getItem(PWA_DISMISS_KEY) === '1'
  } catch {
    return false
  }
}

function onBeforeInstallPrompt(event: Event) {
  event.preventDefault()
  if (isDismissed() || isStandalone()) return
  deferredPrompt.value = event as BeforeInstallPromptEvent
  visible.value = true
}

onMounted(() => {
  if (typeof window === 'undefined' || isDismissed() || isStandalone()) return
  window.addEventListener('beforeinstallprompt', onBeforeInstallPrompt)
  const isIosSafari = /iPhone|iPad|iPod/i.test(navigator.userAgent) && /Safari/i.test(navigator.userAgent) && !/CriOS|FxiOS/i.test(navigator.userAgent)
  if (isIosSafari) {
    showIosHint.value = true
    visible.value = true
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('beforeinstallprompt', onBeforeInstallPrompt)
  }
})

async function installApp() {
  if (!deferredPrompt.value) return
  await deferredPrompt.value.prompt()
  const choice = await deferredPrompt.value.userChoice
  if (choice.outcome === 'accepted') {
    visible.value = false
  }
  deferredPrompt.value = null
}

function dismissPermanently() {
  visible.value = false
  try {
    localStorage.setItem(PWA_DISMISS_KEY, '1')
  } catch {
    // Ignore storage errors
  }
}
</script>

<template>
  <div
    v-if="visible"
    role="region"
    aria-label="添加到主屏幕提示"
    class="fixed left-3 right-3 z-40 mx-auto max-w-md flex items-center justify-between gap-2.5 rounded-xl border border-border bg-card/95 px-3.5 py-2.5 text-xs shadow-lg backdrop-blur"
    style="bottom: calc(4.5rem + var(--safe-bottom))"
  >
    <div class="flex items-center gap-2 min-w-0">
      <Download :size="16" class="shrink-0 text-primary" />
      <span v-if="showIosHint" class="text-foreground leading-snug">
        点击浏览器底部分享图标，选择「添加到主屏幕」获得独立应用体验
      </span>
      <span v-else class="truncate text-foreground">将 Cloudlane 添加到主屏幕，打开更快捷</span>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <button
        v-if="!showIosHint"
        type="button"
        class="rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground"
        @click="installApp"
      >
        安装
      </button>
      <button
        type="button"
        class="p-1 rounded-lg text-muted-foreground hover:text-foreground"
        aria-label="不再提示"
        @click="dismissPermanently"
      >
        <X :size="15" />
      </button>
    </div>
  </div>
</template>
