<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { AlertTriangle, CheckCircle2, Wand2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import type { DriveNode } from '@/api/drive'
import { opsApi, type BatchRenamePreviewItem, type BatchRenameRequest, type UndoTokenReceipt } from '@/api/ops'

const props = defineProps<{ spaceId: string; nodes: DriveNode[] }>()
const emit = defineEmits<{ close: []; applied: [undo: UndoTokenReceipt | null] }>()

const mode = ref<'PREFIX' | 'SUFFIX' | 'REPLACE' | 'SEQUENCE'>('PREFIX')
const prefix = ref('')
const suffix = ref('')
const find = ref('')
const replace = ref('')
const template = ref('文件_{n}')
const startNumber = ref(1)
const keepExtension = ref(true)
const conflictPolicy = ref<'ABORT' | 'SKIP' | 'AUTO_NUMBER'>('AUTO_NUMBER')

const previewItems = ref<BatchRenamePreviewItem[]>([])
const previewing = ref(false)
const applying = ref(false)
const error = ref('')

const hasConflict = computed(() => previewItems.value.some(i => i.conflict))

function buildPayload(): BatchRenameRequest {
  return {
    nodeIds: props.nodes.map(n => n.id),
    mode: mode.value,
    prefix: prefix.value,
    suffix: suffix.value,
    find: find.value,
    replace: replace.value,
    template: template.value,
    startNumber: Number(startNumber.value) || 1,
    keepExtension: keepExtension.value,
    conflictPolicy: conflictPolicy.value,
  }
}

async function runPreview() {
  previewing.value = true
  error.value = ''
  try {
    previewItems.value = await opsApi.previewBatchRename(props.spaceId, buildPayload())
  } catch (e) {
    error.value = e instanceof Error ? e.message : '生成预览失败'
  } finally {
    previewing.value = false
  }
}

async function apply() {
  applying.value = true
  error.value = ''
  try {
    const res = await opsApi.applyBatchRename(props.spaceId, buildPayload())
    emit('applied', res.undo)
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '批量重命名失败'
  } finally {
    applying.value = false
  }
}

watch([mode, prefix, suffix, find, replace, template, startNumber, keepExtension, conflictPolicy], () => {
  void runPreview()
}, { immediate: true })
</script>

<template>
  <Dialog :open="true" @update:open="v => { if (!v && !applying) emit('close') }">
    <DialogContent class="!max-w-2xl">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2"><Wand2 :size="18" /> 批量重命名 ({{ nodes.length }} 项)</DialogTitle>
        <DialogDescription>提交前实时预览每一项的新名称与同名冲突处理结果，完成后支持 10 秒内一键撤销。</DialogDescription>
      </DialogHeader>

      <div class="grid gap-3 md:grid-cols-2">
        <label class="block space-y-1 text-xs">
          <span>重命名规则</span>
          <select v-model="mode" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm">
            <option value="PREFIX">添加前缀</option>
            <option value="SUFFIX">添加后缀</option>
            <option value="REPLACE">查找并替换</option>
            <option value="SEQUENCE">序号模板 ({n})</option>
          </select>
        </label>
        <label class="block space-y-1 text-xs">
          <span>同名冲突策略</span>
          <select v-model="conflictPolicy" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm">
            <option value="AUTO_NUMBER">自动追加序号</option>
            <option value="SKIP">跳过冲突项</option>
            <option value="ABORT">存在冲突时阻止提交</option>
          </select>
        </label>
      </div>

      <div v-if="mode === 'PREFIX'" class="space-y-1 text-xs">
        <span>前缀文本</span>
        <Input v-model="prefix" placeholder="例如 2026_" />
      </div>
      <div v-else-if="mode === 'SUFFIX'" class="space-y-1 text-xs">
        <span>后缀文本</span>
        <Input v-model="suffix" placeholder="例如 _final" />
      </div>
      <div v-else-if="mode === 'REPLACE'" class="grid gap-3 md:grid-cols-2 text-xs">
        <label class="space-y-1"><span>查找内容</span><Input v-model="find" placeholder="旧关键词" /></label>
        <label class="space-y-1"><span>替换为</span><Input v-model="replace" placeholder="新关键词" /></label>
      </div>
      <div v-else class="grid gap-3 md:grid-cols-2 text-xs">
        <label class="space-y-1"><span>名称模板（{n} 表示序号）</span><Input v-model="template" placeholder="项目资料_{n}" /></label>
        <label class="space-y-1"><span>起始编号</span><Input v-model="startNumber" type="number" min="0" /></label>
      </div>

      <label class="flex items-center gap-2 text-xs">
        <input v-model="keepExtension" type="checkbox" />
        <span>保留文件扩展名不变</span>
      </label>

      <div class="max-h-60 overflow-y-auto rounded-lg border border-border">
        <table class="w-full border-collapse text-xs">
          <thead class="sticky top-0 bg-muted text-muted-foreground">
            <tr>
              <th class="px-3 py-2 text-left font-medium">原名称</th>
              <th class="px-3 py-2 text-left font-medium">新名称预览</th>
              <th class="w-24 px-3 py-2 text-right font-medium">状态</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border">
            <tr v-for="item in previewItems" :key="item.nodeId" :class="item.conflict ? 'bg-destructive/10' : ''">
              <td class="max-w-[200px] truncate px-3 py-2 text-muted-foreground">{{ item.oldName }}</td>
              <td class="max-w-[220px] truncate px-3 py-2 font-medium">{{ item.newName }}</td>
              <td class="px-3 py-2 text-right">
                <span v-if="item.conflict" class="inline-flex items-center gap-1 text-destructive"><AlertTriangle :size="12" /> {{ item.reason || '冲突' }}</span>
                <span v-else class="inline-flex items-center gap-1 text-emerald-600"><CheckCircle2 :size="12" /> 就绪</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="error" role="alert" class="text-xs text-destructive">{{ error }}</p>

      <DialogFooter>
        <Button type="button" variant="outline" :disabled="applying" @click="emit('close')">取消</Button>
        <Button type="button" :disabled="applying || previewing || (conflictPolicy === 'ABORT' && hasConflict)" @click="apply">
          {{ applying ? '正在应用…' : '确认批量重命名' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
