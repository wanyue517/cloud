<script setup lang="ts">
import { Download, FileQuestion } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import type { DriveNode, Version } from '@/api/drive'
import { getExtension } from '@/lib/preview-registry'

const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ download: [] }>()

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 items-center justify-center bg-muted/20 p-6">
    <div class="flex w-full max-w-md flex-col items-center gap-4 rounded-xl border border-border bg-card p-8 text-center shadow-sm">
      <div class="flex size-16 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
        <FileQuestion :size="32" />
      </div>
      <div class="space-y-1">
        <h3 class="break-all text-base font-semibold">{{ props.node.name }}</h3>
        <p class="text-xs text-muted-foreground">当前文件类型暂不支持在线预览，请下载到本地使用专业软件打开。</p>
      </div>
      <dl class="grid w-full grid-cols-2 gap-y-2 rounded-lg bg-muted/40 p-3 text-left text-xs">
        <dt class="text-muted-foreground">扩展名</dt>
        <dd class="font-mono uppercase">{{ getExtension(props.node.name) || '—' }}</dd>
        <dt class="text-muted-foreground">MIME 类型</dt>
        <dd class="truncate">{{ props.version?.mimeType || props.node.mimeType || 'application/octet-stream' }}</dd>
        <dt class="text-muted-foreground">文件大小</dt>
        <dd>{{ formatSize(props.version?.size ?? props.node.size) }}</dd>
        <dt class="text-muted-foreground">更新时间</dt>
        <dd>{{ new Date(props.version?.createdAt || props.node.updatedAt).toLocaleString('zh-CN') }}</dd>
      </dl>
      <Button class="w-full" @click="emit('download')">
        <Download :size="16" /> 下载原文件
      </Button>
    </div>
  </div>
</template>
