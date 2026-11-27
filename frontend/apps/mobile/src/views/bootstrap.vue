<script setup lang="ts">
import { Cloud } from '@lucide/vue'
import { Button } from '@repo/ui/button'

function openDesktop() {
  window.location.assign('/app')
}
</script>

<template>
  <main class="min-h-dvh flex flex-col items-center justify-center px-6 text-center bg-background text-foreground safe-top safe-bottom">
    <div class="w-14 h-14 rounded-2xl bg-primary/10 text-primary flex items-center justify-center mb-4">
      <Cloud :size="28" />
    </div>
    <h1 class="text-xl font-semibold tracking-tight">Cloudlane 移动端</h1>
    <p class="mt-2 text-sm text-muted-foreground max-w-xs">
      独立移动应用工程已初始化（挂载路径 /m/），正在按阶段持续交付完整功能。
    </p>
    <div class="mt-6 flex items-center gap-3">
      <Button variant="outline" size="sm" @click="openDesktop">前往桌面版</Button>
    </div>
  </main>
</template>
