<script setup lang="ts">
import { ArrowDown, ArrowUp, Check } from '@lucide/vue'
import { BottomSheet } from '@/components/common'
import type { SortConfig, SortField, SortOrder } from '@/lib/file-actions'

const props = defineProps<{
  open: boolean
  modelValue: SortConfig
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'update:modelValue': [value: SortConfig]
}>()

const fieldOptions: { field: SortField; label: string; desc: string }[] = [
  { field: 'updatedAt', label: '修改时间', desc: '按最近修改时间排列' },
  { field: 'name', label: '名称', desc: '按文件与文件夹名称排列' },
  { field: 'size', label: '大小', desc: '按文件占用空间大小排列' },
]

function selectField(field: SortField) {
  const nextOrder: SortOrder =
    props.modelValue.field === field
      ? props.modelValue.order === 'asc'
        ? 'desc'
        : 'asc'
      : field === 'name'
        ? 'asc'
        : 'desc'
  emit('update:modelValue', { field, order: nextOrder })
  emit('update:open', false)
}

function selectOrder(order: SortOrder) {
  emit('update:modelValue', { field: props.modelValue.field, order })
  emit('update:open', false)
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="排序方式"
    subtitle="文件夹将优先显示在顶部"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4">
      <div class="space-y-1.5">
        <p class="text-xs font-medium text-muted-foreground px-1">排序字段</p>
        <button
          v-for="opt in fieldOptions"
          :key="opt.field"
          type="button"
          class="w-full flex items-center justify-between gap-3 rounded-xl px-3.5 py-3 text-left transition-colors"
          :class="
            props.modelValue.field === opt.field
              ? 'bg-primary/10 text-primary'
              : 'bg-background active:bg-accent'
          "
          @click="selectField(opt.field)"
        >
          <div>
            <p class="text-sm font-medium">{{ opt.label }}</p>
            <p class="text-xs text-muted-foreground mt-0.5">{{ opt.desc }}</p>
          </div>
          <Check v-if="props.modelValue.field === opt.field" :size="18" class="shrink-0" />
        </button>
      </div>

      <div class="space-y-1.5">
        <p class="text-xs font-medium text-muted-foreground px-1">排序方向</p>
        <div class="grid grid-cols-2 gap-2">
          <button
            type="button"
            class="flex items-center justify-center gap-1.5 rounded-xl border py-2.5 text-sm font-medium"
            :class="
              props.modelValue.order === 'asc'
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-foreground'
            "
            @click="selectOrder('asc')"
          >
            <ArrowUp :size="15" />
            <span>升序</span>
          </button>
          <button
            type="button"
            class="flex items-center justify-center gap-1.5 rounded-xl border py-2.5 text-sm font-medium"
            :class="
              props.modelValue.order === 'desc'
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-foreground'
            "
            @click="selectOrder('desc')"
          >
            <ArrowDown :size="15" />
            <span>降序</span>
          </button>
        </div>
      </div>
    </div>
  </BottomSheet>
</template>
