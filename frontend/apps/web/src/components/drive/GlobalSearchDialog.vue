<script setup lang="ts">
import { ArrowLeft, Search } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import type { Space } from '@/api/drive'
import type { SearchHit } from '@/api/search'
import SearchPanel from './SearchPanel.vue'

defineProps<{
  spaces: Space[]
  currentSpaceId: string
  initialQuery?: string
  initialScope?: 'space' | 'global'
}>()
const emit = defineEmits<{
  close: []
  locate: [hit: SearchHit]
  preview: [hit: SearchHit]
}>()
const returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null

function restoreFocus(event: Event) {
  event.preventDefault()
  if (returnFocusTo?.isConnected) returnFocusTo.focus({ preventScroll: true })
}
</script>

<template>
  <Dialog :open="true" @update:open="open => { if (!open) emit('close') }">
    <DialogContent class="!max-w-6xl h-[min(88dvh,900px)] grid-rows-[auto_minmax(0,1fr)] !gap-0 !p-0 overflow-hidden" @close-auto-focus="restoreFocus">
      <DialogHeader class="border-b border-border px-5 py-4 pr-12">
        <div class="flex items-center gap-3">
          <Button variant="ghost" size="sm" aria-label="返回原页面" @click="emit('close')">
            <ArrowLeft :size="16" /> 返回
          </Button>
          <DialogTitle class="flex items-center gap-2 text-base"><Search :size="17" /> 搜索与检索</DialogTitle>
        </div>
        <DialogDescription class="text-xs">搜索当前空间或全部空间的文件名与正文，按 Esc 关闭并返回原页面。</DialogDescription>
      </DialogHeader>
      <div class="min-h-0 overflow-y-auto p-4 sm:p-5">
        <SearchPanel
          :spaces="spaces"
          :current-space-id="currentSpaceId"
          :initial-query="initialQuery"
          :initial-scope="initialScope"
          @locate="emit('locate', $event)"
          @preview="emit('preview', $event)"
        />
      </div>
    </DialogContent>
  </Dialog>
</template>
