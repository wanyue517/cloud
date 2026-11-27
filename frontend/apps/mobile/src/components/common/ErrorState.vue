<script setup lang="ts">
import { AlertCircle, RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'

withDefaults(
  defineProps<{
    title?: string
    message?: string
    retryText?: string
  }>(),
  {
    title: '加载失败',
    message: '请检查网络连接后重试',
    retryText: '重新加载',
  },
)

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div role="alert" class="flex flex-col items-center justify-center py-14 px-6 text-center">
    <div class="w-12 h-12 rounded-2xl bg-destructive/10 text-destructive flex items-center justify-center mb-3">
      <AlertCircle :size="24" />
    </div>
    <p class="text-sm font-medium text-foreground">{{ title }}</p>
    <p class="mt-1 text-xs text-muted-foreground max-w-xs">{{ message }}</p>
    <Button variant="outline" size="sm" class="mt-4" @click="emit('retry')">
      <RefreshCw :size="14" />
      <span>{{ retryText }}</span>
    </Button>
  </div>
</template>
