<script setup lang="ts">
withDefaults(
  defineProps<{
    count?: number
    variant?: 'list' | 'grid'
  }>(),
  {
    count: 6,
    variant: 'list',
  },
)
</script>

<template>
  <div v-if="variant === 'grid'" class="grid grid-cols-3 gap-2.5 p-3">
    <div v-for="i in count" :key="i" class="rounded-xl border border-border bg-card p-2 animate-pulse">
      <div class="aspect-square rounded-lg bg-muted mb-2" />
      <div class="h-3 rounded bg-muted w-4/5" />
    </div>
  </div>

  <div v-else class="divide-y divide-border px-4">
    <div v-for="i in count" :key="i" class="flex items-center gap-3 py-3 animate-pulse">
      <div class="w-10 h-10 rounded-xl bg-muted shrink-0" />
      <div class="flex-1 min-w-0 space-y-2">
        <div class="h-3.5 rounded bg-muted w-3/5" />
        <div class="h-2.5 rounded bg-muted w-2/5" />
      </div>
    </div>
  </div>
</template>
