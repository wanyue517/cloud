<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, ref } from 'vue'
import { ChevronDown, RotateCcw, Search } from '@lucide/vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { cn } from '../../lib/utils'
import { findLucideIconOption, ICON_OPTIONS } from './icons'

const props = withDefaults(defineProps<{
  modelValue?: string
  previewColor?: string
  disabled?: boolean
  ariaLabel?: string
  class?: HTMLAttributes['class']
}>(), {
  modelValue: 'folder',
  previewColor: '',
  disabled: false,
  ariaLabel: '选择图标',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const open = ref(false)
const keyword = ref('')

const currentOption = computed(() => findLucideIconOption(props.modelValue))

const filteredIcons = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return ICON_OPTIONS
  return ICON_OPTIONS.filter(item =>
    item.name.includes(q)
    || item.label.toLowerCase().includes(q)
    || item.keywords.toLowerCase().includes(q),
  )
})

function selectIcon(name: string) {
  emit('update:modelValue', name)
  open.value = false
}
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child :disabled="disabled">
      <button
        type="button"
        :disabled="disabled"
        :aria-label="ariaLabel"
        :class="cn(
          'flex h-9 w-full cursor-pointer items-center justify-between gap-2 rounded-md border border-input bg-background px-3 py-1.5 text-sm shadow-2xs transition-colors outline-none',
          'hover:bg-accent/40 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
          'disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30',
          props.class,
        )"
      >
        <span class="flex min-w-0 items-center gap-2">
          <span class="flex size-5 shrink-0 items-center justify-center rounded bg-muted/70">
            <component
              :is="currentOption.component"
              :size="15"
              :style="previewColor ? { color: previewColor } : undefined"
            />
          </span>
          <span class="truncate text-left">{{ currentOption.label }}</span>
        </span>
        <ChevronDown :size="14" class="shrink-0 text-muted-foreground" />
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="6"
        class="z-[95] w-72 rounded-lg border border-border bg-popover p-3 text-popover-foreground shadow-lg outline-none"
      >
        <div class="space-y-2.5">
          <div class="relative">
            <Search :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索图标，如 相册 / 代码 / folder…"
              class="h-8 w-full rounded-md border border-input bg-background pl-8 pr-2.5 text-xs outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            />
          </div>

          <div
            v-if="filteredIcons.length"
            class="grid max-h-52 grid-cols-6 gap-1.5 overflow-y-auto pr-0.5"
          >
            <button
              v-for="item in filteredIcons"
              :key="item.name"
              type="button"
              :title="`${item.label} (${item.name})`"
              :aria-label="item.label"
              class="flex size-9 cursor-pointer items-center justify-center rounded-md border transition-colors"
              :class="currentOption.name === item.name
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-transparent hover:border-border hover:bg-accent'"
              @click="selectIcon(item.name)"
            >
              <component
                :is="item.component"
                :size="17"
                :style="currentOption.name === item.name && previewColor ? { color: previewColor } : undefined"
              />
            </button>
          </div>
          <div v-else class="py-6 text-center text-xs text-muted-foreground">
            未找到匹配的 Lucide 图标
          </div>

          <div class="flex items-center justify-between border-t border-border pt-2 text-[11px] text-muted-foreground">
            <span>Lucide 图标库 · {{ filteredIcons.length }} 个</span>
            <button
              type="button"
              class="inline-flex cursor-pointer items-center gap-1 text-foreground/80 hover:text-primary"
              @click="selectIcon('folder')"
            >
              <RotateCcw :size="11" /> 恢复默认
            </button>
          </div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
