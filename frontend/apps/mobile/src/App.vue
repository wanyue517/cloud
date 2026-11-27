<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { ConfigProvider as VanConfigProvider } from 'vant'
import { Toaster } from '@repo/ui/toast'
import { isDarkTheme } from '@/lib/theme-sync'
import { loadSiteConfig } from '@/composables/useSiteConfig'
import AppSecurityOverlay from '@/components/common/AppSecurityOverlay.vue'
import FloatingAudioBar from '@/components/common/FloatingAudioBar.vue'
import OfflineBanner from '@/components/common/OfflineBanner.vue'
import PwaInstallBanner from '@/components/common/PwaInstallBanner.vue'
import UndoToast from '@/components/common/UndoToast.vue'
import UploadProgressCapsule from '@/components/common/UploadProgressCapsule.vue'
import { startMessagesBridge, stopMessagesBridge } from '@/stores/messages'
import { initUploadNetworkBridge } from '@/stores/uploads'

onMounted(() => {
  initUploadNetworkBridge()
  startMessagesBridge()
  void loadSiteConfig()
})

onBeforeUnmount(() => {
  stopMessagesBridge()
})
</script>

<template>
  <VanConfigProvider :theme="isDarkTheme ? 'dark' : 'light'">
    <OfflineBanner />
    <RouterView />
    <FloatingAudioBar />
    <UploadProgressCapsule />
    <PwaInstallBanner />
    <UndoToast />
    <Toaster />
    <AppSecurityOverlay />
  </VanConfigProvider>
</template>
