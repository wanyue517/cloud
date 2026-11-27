<script setup lang="ts">
import { Plus } from '@lucide/vue'

const props = withDefaults(
  defineProps<{
    label?: string
    aboveTabBar?: boolean
    badge?: number
    disabled?: boolean
  }>(),
  {
    label: '新建或上传',
    aboveTabBar: true,
    badge: 0,
    disabled: false,
  },
)

const emit = defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    type="button"
    :disabled="props.disabled"
    :aria-label="props.label"
    class="fixed right-4 z-30 inline-flex items-center justify-center w-13 h-13 rounded-full bg-primary text-primary-foreground shadow-lg active:scale-95 transition-transform disabled:opacity-50"
    :style="{
      bottom: props.aboveTabBar
        ? 'calc(4.5rem + var(--safe-bottom))'
        : 'calc(1.25rem + var(--safe-bottom))',
    }"
    @click="emit('click')"
  >
    <slot>
      <Plus :size="24" :stroke-width="2.2" />
    </slot>
    <span
      v-if="props.badge > 0"
      class="absolute -top-1 -right-1 min-w-5 h-5 px-1 rounded-full bg-destructive text-destructive-foreground text-[11px] font-semibold flex items-center justify-center"
    >
      {{ props.badge > 99 ? '99+' : props.badge }}
    </span>
  </button>
</template>
