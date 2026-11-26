<script setup lang="ts">
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from '@lucide/vue'
import { dismissToast, useToastItems } from './toast'

const items = useToastItems()
</script>

<template>
  <Teleport to="body">
    <div
      class="pointer-events-none fixed top-4 right-4 z-[100] flex w-full max-w-sm flex-col gap-2 px-4 sm:px-0"
      aria-live="polite"
      aria-atomic="false"
    >
      <div
        v-for="item in items"
        :key="item.id"
        role="status"
        class="pointer-events-auto flex items-start gap-2.5 rounded-lg border bg-popover px-3.5 py-2.5 text-sm text-popover-foreground shadow-lg transition-all"
        :class="{
          'border-emerald-500/40': item.variant === 'success',
          'border-destructive/50': item.variant === 'error',
          'border-amber-500/40': item.variant === 'warning',
          'border-border': item.variant === 'info',
        }"
      >
        <CheckCircle2 v-if="item.variant === 'success'" :size="17" class="mt-0.5 shrink-0 text-emerald-500" />
        <AlertCircle v-else-if="item.variant === 'error'" :size="17" class="mt-0.5 shrink-0 text-destructive" />
        <AlertTriangle v-else-if="item.variant === 'warning'" :size="17" class="mt-0.5 shrink-0 text-amber-500" />
        <Info v-else :size="17" class="mt-0.5 shrink-0 text-primary" />
        <span class="min-w-0 flex-1 break-words leading-snug">{{ item.message }}</span>
        <button
          type="button"
          class="shrink-0 cursor-pointer rounded p-0.5 text-muted-foreground hover:bg-muted hover:text-foreground"
          aria-label="关闭提示"
          @click="dismissToast(item.id)"
        >
          <X :size="14" />
        </button>
      </div>
    </div>
  </Teleport>
</template>
