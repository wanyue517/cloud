<script setup lang="ts">
import { useRouter } from 'vue-router'
import { FileQuestion } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { mapMobilePathToWeb, saveClientModePreference } from '@repo/drive-core/client-mode'

const router = useRouter()

function goHome() {
  void router.replace('/home')
}

function goDesktop() {
  saveClientModePreference('desktop')
  window.location.assign(mapMobilePathToWeb('/m/home'))
}
</script>

<template>
  <main class="min-h-dvh flex flex-col items-center justify-center px-6 text-center bg-background text-foreground safe-top safe-bottom">
    <div class="w-14 h-14 rounded-2xl bg-muted text-muted-foreground flex items-center justify-center mb-4">
      <FileQuestion :size="28" />
    </div>
    <h1 class="text-xl font-semibold">页面不存在</h1>
    <p class="mt-2 text-sm text-muted-foreground max-w-xs">
      你访问的移动端页面不存在或无访问权限，请返回首页继续使用。
    </p>
    <div class="mt-6 flex items-center gap-3">
      <Button size="sm" @click="goHome">返回首页</Button>
      <Button variant="outline" size="sm" @click="goDesktop">切换桌面版</Button>
    </div>
  </main>
</template>
