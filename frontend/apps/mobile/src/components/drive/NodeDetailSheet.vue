<script setup lang="ts">
import { computed } from 'vue'
import { roleLabel } from '@repo/drive-core/categories'
import { formatBytes, formatDateTime } from '@repo/drive-core/format'
import { BottomSheet } from '@/components/common'
import type { DriveNode, Space } from '@/api/drive'
import NodeThumb from './NodeThumb.vue'

const props = defineProps<{
  open: boolean
  node: DriveNode | null
  space: Space | null
  ancestors: DriveNode[]
  favorite?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const fullPath = computed(() => {
  const segments: string[] = []
  if (props.space?.name) segments.push(props.space.name)
  for (const a of props.ancestors) {
    segments.push(a.name)
  }
  if (props.node?.name) segments.push(props.node.name)
  return '/' + segments.join('/')
})

const rows = computed(() => {
  const n = props.node
  if (!n) return []
  return [
    { label: '完整路径', value: fullPath.value },
    { label: '类型', value: n.nodeType === 'FOLDER' ? '文件夹' : n.mimeType || '未知文件' },
    { label: '大小', value: n.nodeType === 'FOLDER' ? '—' : formatBytes(n.size) },
    { label: '修订版本', value: `v${n.revision}` },
    { label: '修改时间', value: formatDateTime(n.updatedAt) },
    { label: '所属空间', value: props.space ? `${props.space.name}（${roleLabel(props.space.role)}）` : '—' },
    {
      label: '状态标记',
      value: [props.favorite ? '已收藏' : '', n.hidden ? '已隐藏' : ''].filter(Boolean).join(' · ') || '普通',
    },
  ]
})
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="文件详情"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="props.node" class="space-y-4 pb-2">
      <div class="flex items-center gap-3 rounded-2xl bg-muted/50 p-3">
        <NodeThumb :node="props.node" variant="list" />
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold break-all text-foreground">{{ props.node.name }}</p>
          <p class="text-xs text-muted-foreground mt-0.5">
            {{ props.node.nodeType === 'FOLDER' ? '文件夹' : formatBytes(props.node.size) }}
          </p>
        </div>
      </div>

      <dl class="divide-y divide-border rounded-2xl border border-border bg-background overflow-hidden">
        <div
          v-for="row in rows"
          :key="row.label"
          class="flex items-start justify-between gap-4 px-3.5 py-2.5 text-sm"
        >
          <dt class="text-muted-foreground shrink-0">{{ row.label }}</dt>
          <dd class="text-right text-foreground break-all">{{ row.value }}</dd>
        </div>
      </dl>
    </div>
  </BottomSheet>
</template>
