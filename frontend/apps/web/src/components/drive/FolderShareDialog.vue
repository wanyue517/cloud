<script setup lang="ts">
import { ref } from 'vue'
import { Share2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import type { DriveNode } from '@/api/drive'
import { shareApi } from '@/api/share'

const props = defineProps<{ node: DriveNode }>()
const emit = defineEmits<{ close: []; shared: [] }>()

const target = ref('')
const permission = ref<'VIEWER' | 'EDITOR'>('VIEWER')
const busy = ref(false)
const error = ref('')

async function submit() {
  if (!target.value.trim()) return
  busy.value = true
  error.value = ''
  try {
    await shareApi.shareFolder(props.node.spaceId, props.node.id, {
      target: target.value.trim(),
      permission: permission.value,
    })
    emit('shared')
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '共享失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog :open="true" @update:open="v => { if (!v && !busy) emit('close') }">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2"><Share2 :size="18" /> 共享「{{ node.name }}」</DialogTitle>
        <DialogDescription>将当前目录定向共享给其他用户，原路径保持不变，对方只能访问此目录及子项。</DialogDescription>
      </DialogHeader>
      <form class="space-y-3" @submit.prevent="submit">
        <label class="block space-y-1 text-sm">
          <span>目标用户（邮箱或 ID）</span>
          <Input v-model="target" placeholder="user@example.com" :disabled="busy" />
        </label>
        <label class="block space-y-1 text-sm">
          <span>访问权限</span>
          <select v-model="permission" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" :disabled="busy">
            <option value="VIEWER">仅查看与下载 (VIEWER)</option>
            <option value="EDITOR">可编辑与上传 (EDITOR)</option>
          </select>
        </label>
        <p v-if="error" role="alert" class="text-xs text-destructive">{{ error }}</p>
        <DialogFooter>
          <Button type="button" variant="outline" :disabled="busy" @click="emit('close')">取消</Button>
          <Button type="submit" :disabled="busy || !target.trim()">{{ busy ? '正在授权…' : '确认共享' }}</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
