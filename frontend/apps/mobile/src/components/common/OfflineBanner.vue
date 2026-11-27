<script setup lang="ts">
import { WifiOff } from '@lucide/vue'
import { networkStatus } from '@/lib/network'
</script>

<template>
  <div
    v-if="!networkStatus.online"
    role="status"
    aria-live="polite"
    class="sticky top-0 z-50 flex items-center justify-center gap-2 bg-destructive px-4 py-1.5 text-xs font-medium text-destructive-foreground safe-top"
  >
    <WifiOff :size="14" />
    <span>当前网络已断开，恢复连接后将自动同步</span>
  </div>
</template>
