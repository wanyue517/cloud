<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, ref, shallowRef, watch } from 'vue'
import { ChevronDown, ChevronLeft, ChevronRight, RotateCcw, Search } from '@lucide/vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { cn } from '../../lib/utils'
import {
  findLucideIconLabelSync,
  getCachedLucideIcons,
  loadAllLucideIcons,
  normalizeIconName,
  type LucideIconOption,
} from './icons'
import LucideIconByName from './LucideIconByName.vue'

const PAGE_SIZE = 36

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
const loading = ref(false)
const allIcons = shallowRef<LucideIconOption[]>(getCachedLucideIcons() || [])
const currentPage = ref(1)

async function ensureLoaded() {
  if (allIcons.value.length > 0 || loading.value) return
  loading.value = true
  try {
    allIcons.value = await loadAllLucideIcons()
  } finally {
    loading.value = false
  }
}

watch(open, isOpen => {
  if (isOpen) {
    void ensureLoaded()
  }
})

watch(keyword, () => {
  currentPage.value = 1
})

const normalizedValue = computed(() => normalizeIconName(props.modelValue))

const currentLabel = computed(() => {
  void allIcons.value.length
  return findLucideIconLabelSync(normalizedValue.value)
})

const filteredIcons = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return allIcons.value
  return allIcons.value.filter(item => item.keywords.includes(q) || item.label.toLowerCase().includes(q))
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredIcons.value.length / PAGE_SIZE)))

watch(totalPages, max => {
  if (currentPage.value > max) currentPage.value = max
})

const pagedIcons = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredIcons.value.slice(start, start + PAGE_SIZE)
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
            <LucideIconByName
              :name="normalizedValue"
              :size="15"
              :style="previewColor ? { color: previewColor } : undefined"
            />
          </span>
          <span class="truncate text-left">{{ currentLabel }}</span>
        </span>
        <ChevronDown :size="14" class="shrink-0 text-muted-foreground" />
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="6"
        :avoid-collisions="true"
        :collision-padding="12"
        sticky="always"
        class="z-[95] flex w-80 max-w-[calc(100vw-24px)] max-h-[min(var(--reka-popover-content-available-height),calc(100vh-24px))] flex-col overflow-hidden rounded-lg border border-border bg-popover p-3 text-popover-foreground shadow-lg outline-none"
      >
        <div class="flex min-h-0 flex-1 flex-col gap-2.5">
          <div class="relative shrink-0">
            <Search :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索全部 Lucide 图标（中英文均可）…"
              class="h-8 w-full rounded-md border border-input bg-background pl-8 pr-2.5 text-xs outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            />
          </div>

          <div v-if="loading" class="py-10 text-center text-xs text-muted-foreground">
            正在加载全部 Lucide 图标…
          </div>
          <div v-else-if="pagedIcons.length" class="min-h-0 flex-1 overflow-y-auto pr-0.5">
            <div class="grid grid-cols-6 gap-1.5 content-start">
              <button
                v-for="item in pagedIcons"
                :key="item.name"
                type="button"
                :title="item.label !== item.name ? `${item.label} (${item.name})` : item.name"
                :aria-label="item.label"
                class="flex size-10 cursor-pointer items-center justify-center rounded-md border transition-colors"
                :class="normalizedValue === item.name
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-transparent hover:border-border hover:bg-accent'"
                @click="selectIcon(item.name)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  aria-hidden="true"
                  :style="normalizedValue === item.name && previewColor ? { color: previewColor } : undefined"
                >
                  <component :is="tag" v-for="([tag, attrs], idx) in item.nodes" :key="idx" v-bind="attrs" />
                </svg>
              </button>
            </div>
          </div>
          <div v-else class="py-10 text-center text-xs text-muted-foreground">
            未找到匹配的 Lucide 图标
          </div>

          <!-- 分页控制栏 -->
          <div v-if="filteredIcons.length > PAGE_SIZE" class="flex shrink-0 items-center justify-between border-t border-border pt-2 text-xs">
            <button
              type="button"
              class="inline-flex h-7 cursor-pointer items-center gap-0.5 rounded border border-input px-2 text-xs hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="currentPage <= 1"
              @click="currentPage--"
            >
              <ChevronLeft :size="13" /> 上一页
            </button>
            <span class="tabular-nums text-muted-foreground">{{ currentPage }} / {{ totalPages }} 页</span>
            <button
              type="button"
              class="inline-flex h-7 cursor-pointer items-center gap-0.5 rounded border border-input px-2 text-xs hover:bg-accent disabled:cursor-not-allowed disabled:opacity-40"
              :disabled="currentPage >= totalPages"
              @click="currentPage++"
            >
              下一页 <ChevronRight :size="13" />
            </button>
          </div>

          <div class="flex shrink-0 items-center justify-between border-t border-border pt-2 text-[11px] text-muted-foreground">
            <span>共 {{ filteredIcons.length }} / {{ allIcons.length }} 个图标</span>
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
