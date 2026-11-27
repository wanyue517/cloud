<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { Check, ChevronDown } from '@lucide/vue'
import {
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'
import { cn } from '../../lib/utils'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

const EMPTY_SENTINEL = '__CLOUDLANE_SELECT_EMPTY__'

const props = defineProps<{
  modelValue?: string | number
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  class?: HTMLAttributes['class']
  ariaLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const internalValue = computed({
  get: () => {
    const raw = props.modelValue === undefined || props.modelValue === null ? '' : String(props.modelValue)
    return raw === '' ? EMPTY_SENTINEL : raw
  },
  set: (val: string) => {
    emit('update:modelValue', val === EMPTY_SENTINEL ? '' : val)
  },
})

const normalizedOptions = computed(() =>
  props.options.map(opt => ({
    ...opt,
    internalValue: opt.value === '' ? EMPTY_SENTINEL : opt.value,
  })),
)
</script>

<template>
  <SelectRoot v-model="internalValue" :disabled="disabled">
    <SelectTrigger
      data-slot="select-trigger"
      :aria-label="ariaLabel"
      :class="cn(
        'flex h-9 w-full cursor-pointer items-center justify-between gap-2 rounded-md border border-input bg-background px-3 py-1.5 text-sm shadow-2xs transition-colors outline-none',
        'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
        'disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30',
        props.class,
      )"
    >
      <SelectValue :placeholder="placeholder || '请选择'" class="truncate text-left" />
      <ChevronDown :size="14" class="shrink-0 text-muted-foreground" />
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="4"
        :avoid-collisions="true"
        :collision-padding="12"
        class="z-[90] flex max-h-[min(18rem,var(--reka-select-content-available-height))] min-w-[var(--reka-select-trigger-width)] max-w-[calc(100vw-24px)] flex-col overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md"
      >
        <SelectViewport class="overflow-y-auto p-1">
          <SelectItem
            v-for="opt in normalizedOptions"
            :key="opt.internalValue"
            :value="opt.internalValue"
            :disabled="opt.disabled"
            class="relative flex w-full cursor-pointer select-none items-center rounded-sm py-1.5 pl-7 pr-3 text-sm outline-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground"
          >
            <SelectItemIndicator class="absolute left-2 inline-flex items-center justify-center">
              <Check :size="14" />
            </SelectItemIndicator>
            <SelectItemText>{{ opt.label }}</SelectItemText>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>
