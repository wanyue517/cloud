<script setup lang="ts">
import { Popup as VanPopup } from 'vant'
import { X } from '@lucide/vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    subtitle?: string
    showClose?: boolean
    maxHeight?: string
    closeOnOverlay?: boolean
  }>(),
  {
    title: '',
    subtitle: '',
    showClose: true,
    maxHeight: '82vh',
    closeOnOverlay: true,
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  close: []
}>()

function onUpdateShow(value: boolean) {
  emit('update:open', value)
  if (!value) emit('close')
}

function close() {
  onUpdateShow(false)
}
</script>

<template>
  <VanPopup
    :show="props.open"
    position="bottom"
    round
    safe-area-inset-bottom
    teleport="body"
    :close-on-click-overlay="props.closeOnOverlay"
    :style="{ maxHeight: props.maxHeight }"
    class="flex flex-col overflow-hidden bg-card text-foreground"
    @update:show="onUpdateShow"
  >
    <div class="flex justify-center pt-2.5 pb-1 shrink-0">
      <span class="w-10 h-1 rounded-full bg-border" aria-hidden="true" />
    </div>

    <div
      v-if="props.title || $slots.header"
      class="flex items-start justify-between gap-3 px-4 pt-1 pb-3 border-b border-border shrink-0"
    >
      <slot name="header">
        <div class="min-w-0">
          <h2 class="text-base font-semibold truncate text-foreground">{{ props.title }}</h2>
          <p v-if="props.subtitle" class="text-xs text-muted-foreground mt-0.5 truncate">{{ props.subtitle }}</p>
        </div>
      </slot>
      <button
        v-if="props.showClose"
        type="button"
        class="inline-flex items-center justify-center w-8 h-8 -mr-1 rounded-full text-muted-foreground active:bg-accent"
        aria-label="关闭"
        @click="close"
      >
        <X :size="18" />
      </button>
    </div>

    <div class="flex-1 min-h-0 overflow-y-auto px-4 py-3">
      <slot />
    </div>

    <div v-if="$slots.footer" class="px-4 py-3 border-t border-border shrink-0 bg-card">
      <slot name="footer" />
    </div>
  </VanPopup>
</template>
