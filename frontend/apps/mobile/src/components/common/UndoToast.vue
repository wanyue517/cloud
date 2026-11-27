<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Undo2, X } from '@lucide/vue'
import { dismissUndoToast, mobileUi, triggerUndoToast } from '@/stores/mobile-ui'

const remainingSec = ref(10)
let tickTimer: ReturnType<typeof setInterval> | null = null

function syncRemaining() {
  const ms = Math.max(0, mobileUi.undo.expiresAt - Date.now())
  remainingSec.value = Math.ceil(ms / 1000)
}

watch(
  () => [mobileUi.undo.visible, mobileUi.undo.expiresAt] as const,
  ([visible]) => {
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
    if (visible) {
      syncRemaining()
      tickTimer = setInterval(syncRemaining, 250)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (tickTimer) clearInterval(tickTimer)
})

const progressPct = computed(() => {
  if (!mobileUi.undo.visible || mobileUi.undo.durationMs <= 0) return 0
  return Math.max(0, Math.min(100, (remainingSec.value * 1000 / mobileUi.undo.durationMs) * 100))
})

async function handleUndo() {
  try {
    await triggerUndoToast()
  } catch {
    // Error handled by caller / global request toast
  }
}
</script>

<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="translate-y-4 opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-4 opacity-0"
  >
    <div
      v-if="mobileUi.undo.visible"
      role="status"
      aria-live="polite"
      class="fixed left-3 right-3 z-40 mx-auto max-w-md overflow-hidden rounded-xl border border-border bg-foreground text-background shadow-xl"
      style="bottom: calc(4.5rem + var(--safe-bottom))"
    >
      <div class="flex items-center justify-between gap-2 px-3.5 py-2.5 text-sm">
        <span class="truncate flex-1">{{ mobileUi.undo.summary }}</span>
        <button
          type="button"
          :disabled="mobileUi.undo.busy"
          class="inline-flex items-center gap-1 rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground shrink-0 disabled:opacity-60"
          @click="handleUndo"
        >
          <Undo2 :size="14" />
          <span>{{ mobileUi.undo.busy ? '撤销中…' : `撤销 (${remainingSec}s)` }}</span>
        </button>
        <button
          type="button"
          class="p-1 rounded-lg opacity-70 hover:opacity-100 shrink-0"
          aria-label="关闭撤销提示"
          @click="dismissUndoToast"
        >
          <X :size="16" />
        </button>
      </div>
      <div class="h-0.5 w-full bg-background/15">
        <div class="h-full bg-primary transition-[width] duration-200" :style="{ width: `${progressPct}%` }" />
      </div>
    </div>
  </Transition>
</template>
