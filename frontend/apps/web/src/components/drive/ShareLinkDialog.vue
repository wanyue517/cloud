<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Copy, Dices, Link2 } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import { Switch } from '@repo/ui/switch'
import { toast } from '@repo/ui/toast'
import type { DriveNode } from '@/api/drive'
import { shareApi, type CreatedShareLink } from '@/api/share'

const props = defineProps<{ node: DriveNode }>()
const emit = defineEmits<{ close: [] }>()

const expireDays = ref('7')
const password = ref('')
const allowDownload = ref(true)
const maxDownloads = ref('')
const busy = ref(false)
const error = ref('')
const created = ref<CreatedShareLink | null>(null)
const copied = ref(false)

const fullUrl = computed(() => {
  if (!created.value) return ''
  return `${window.location.origin}${created.value.urlPath}`
})

async function createLink() {
  busy.value = true
  error.value = ''
  try {
    const days = Number.parseInt(expireDays.value, 10)
    const maxDl = maxDownloads.value.trim() ? Number.parseInt(maxDownloads.value, 10) : undefined
    created.value = await shareApi.createShareLink(props.node.spaceId, props.node.id, {
      password: password.value.trim() || undefined,
      expireDays: days > 0 ? days : undefined,
      allowDownload: allowDownload.value,
      maxDownloads: maxDl && maxDl > 0 ? maxDl : undefined,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建分享链接失败'
  } finally {
    busy.value = false
  }
}

function generateRandomCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let out = ''
  for (let i = 0; i < 4; i++) out += chars[Math.floor(Math.random() * chars.length)]
  password.value = out
}

async function copyLink() {
  if (!fullUrl.value) return
  const text = created.value?.hasPassword && password.value.trim()
    ? `${fullUrl.value} 提取码：${password.value.trim()}`
    : fullUrl.value
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    copied.value = true
    toast.success('分享链接已复制到剪贴板')
    window.setTimeout(() => { copied.value = false }, 2000)
  } catch {
    toast.error('自动复制失败，请手动选中上方链接复制')
  }
}
</script>

<template>
  <Dialog :open="true" @update:open="v => { if (!v && !busy) emit('close') }">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2"><Link2 :size="18" /> 创建公开分享链接</DialogTitle>
        <DialogDescription>为「{{ node.name }}」生成受控公开访问链接，令牌仅展示一次且服务端仅存摘要。</DialogDescription>
      </DialogHeader>

      <div v-if="created" class="space-y-4">
        <div class="rounded-lg border border-border bg-muted/30 p-3 text-xs space-y-2">
          <div class="font-medium text-foreground">分享链接已生成</div>
          <div class="break-all rounded bg-background p-2 font-mono">{{ fullUrl }}</div>
          <div v-if="created.hasPassword" class="text-muted-foreground">提取码：<strong>{{ password.trim() }}</strong></div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="emit('close')">完成</Button>
          <Button @click="copyLink">
            <Check v-if="copied" :size="15" />
            <Copy v-else :size="15" />
            {{ copied ? '已复制' : '复制链接' }}
          </Button>
        </DialogFooter>
      </div>

      <form v-else class="space-y-3" @submit.prevent="createLink">
        <label class="block space-y-1 text-sm">
          <span>有效期</span>
          <select v-model="expireDays" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" :disabled="busy">
            <option value="1">1 天</option>
            <option value="7">7 天</option>
            <option value="30">30 天</option>
            <option value="0">永久有效</option>
          </select>
        </label>
        <label class="block space-y-1 text-sm">
          <span>提取码（可选，留空表示免密访问）</span>
          <div class="flex items-center gap-2">
            <Input v-model="password" maxlength="16" placeholder="例如 8899" :disabled="busy" />
            <Button type="button" variant="outline" size="sm" class="shrink-0" :disabled="busy" @click="generateRandomCode">
              <Dices :size="14" /> 随机生成
            </Button>
          </div>
        </label>
        <label class="block space-y-1 text-sm">
          <span>最大下载次数（可选，留空不限）</span>
          <Input v-model="maxDownloads" type="number" min="1" placeholder="不限" :disabled="busy" />
        </label>
        <div class="flex items-center justify-between rounded-lg border border-border px-3 py-2 text-sm">
          <span>允许访问者下载原文件</span>
          <Switch :model-value="allowDownload" :disabled="busy" @update:model-value="v => { allowDownload = v }" />
        </div>
        <p v-if="error" role="alert" class="text-xs text-destructive">{{ error }}</p>
        <DialogFooter>
          <Button type="button" variant="outline" :disabled="busy" @click="emit('close')">取消</Button>
          <Button type="submit" :disabled="busy">{{ busy ? '正在生成…' : '生成链接' }}</Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
