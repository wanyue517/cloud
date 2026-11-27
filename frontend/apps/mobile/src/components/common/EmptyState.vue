<script setup lang="ts">
import { FolderOpen } from '@lucide/vue'
import { Button } from '@repo/ui/button'

withDefaults(
  defineProps<{
    title?: string
    description?: string
    actionText?: string
  }>(),
  {
    title: '暂无内容',
    description: '',
    actionText: '',
  },
)

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-14 px-6 text-center">
    <div class="w-12 h-12 rounded-2xl bg-muted text-muted-foreground flex items-center justify-center mb-3">
      <slot name="icon">
        <FolderOpen :size="24" />
      </slot>
    </div>
    <p class="text-sm font-medium text-foreground">{{ title }}</p>
    <p v-if="description" class="mt-1 text-xs text-muted-foreground max-w-xs">{{ description }}</p>
    <div v-if="actionText || $slots.action" class="mt-4">
      <slot name="action">
        <Button size="sm" @click="emit('action')">{{ actionText }}</Button>
      </slot>
    </div>
  </div>
</template>
