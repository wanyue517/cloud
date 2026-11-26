<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Megaphone, RefreshCw, Send } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { confirm } from '@repo/ui/confirm-dialog'
import { Input } from '@repo/ui/input'
import { toast } from '@repo/ui/toast'
import { request } from '@repo/session/request'

interface AdminMessageRecord {
  id: string
  scope: string
  category: string
  title: string
  content: string
  actionUrl: string
  recipientCount: number
  readCount: number
  createdAt: string
}

const loading = ref(true)
const publishing = ref(false)
const error = ref('')
const notice = ref('')
const records = ref<AdminMessageRecord[]>([])

const scope = ref<'ALL' | 'DIRECT'>('ALL')
const target = ref('')
const title = ref('')
const content = ref('')
const actionUrl = ref('/app')

async function load() {
  loading.value = true
  error.value = ''
  try {
    records.value = await request.get<AdminMessageRecord[]>('/v1/admin/messages')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载消息列表失败'
  } finally {
    loading.value = false
  }
}

async function publish() {
  if (!title.value.trim()) return
  if (scope.value === 'DIRECT' && !target.value.trim()) {
    toast.error('请输入目标用户的邮箱或 ID')
    return
  }
  if (scope.value === 'ALL') {
    const ok = await confirm({
      title: '确认发布全员广播',
      description: `消息「${title.value.trim()}」将立即推送给全站所有用户且发布后不可撤回，确定发布吗？`,
      confirmText: '确认发布',
      variant: 'destructive',
    })
    if (!ok) return
  }
  publishing.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.post('/v1/admin/messages', {
      scope: scope.value,
      target: scope.value === 'DIRECT' ? target.value.trim() : undefined,
      title: title.value.trim(),
      content: content.value.trim(),
      actionUrl: actionUrl.value.trim(),
    })
    title.value = ''
    content.value = ''
    target.value = ''
    toast.success('系统消息已发布并推送给在线用户')
    await load()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '发布消息失败'
    error.value = msg
    toast.error(msg)
  } finally {
    publishing.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold flex items-center gap-2"><Megaphone :size="20" /> 系统消息管理</h1>
        <p class="text-xs text-muted-foreground">向全员或指定用户发布系统公告与通知，支持已读统计。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load"><RefreshCw :size="14" /> 刷新</Button>
    </header>

    <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-md bg-primary/10 px-3 py-2 text-xs text-primary">{{ notice }}</p>

    <form class="space-y-3 rounded-xl border border-border bg-card p-5" @submit.prevent="publish">
      <h2 class="text-sm font-semibold">发布新消息</h2>
      <div class="grid gap-3 md:grid-cols-2">
        <label class="block space-y-1 text-xs">
          <span>发送范围</span>
          <select v-model="scope" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm">
            <option value="ALL">全员广播 (ALL)</option>
            <option value="DIRECT">定向用户 (DIRECT)</option>
          </select>
        </label>
        <label v-if="scope === 'DIRECT'" class="block space-y-1 text-xs">
          <span>目标用户（邮箱或 ID）</span>
          <Input v-model="target" placeholder="user@example.com" />
        </label>
        <label class="block space-y-1 text-xs">
          <span>消息标题</span>
          <Input v-model="title" placeholder="例如：系统升级公告" />
        </label>
        <label class="block space-y-1 text-xs">
          <span>点击跳转路径（可选）</span>
          <Input v-model="actionUrl" placeholder="/app" />
        </label>
      </div>
      <label class="block space-y-1 text-xs">
        <span>消息正文</span>
        <textarea v-model="content" rows="3" class="w-full rounded-md border border-input bg-background p-2.5 text-sm" placeholder="输入通知详情…" />
      </label>
      <Button type="submit" size="sm" :disabled="publishing || !title.trim()">
        <Send :size="14" /> {{ publishing ? '正在发布…' : '立即发布' }}
      </Button>
    </form>

    <div class="rounded-xl border border-border bg-card">
      <div class="border-b border-border px-5 py-3 text-sm font-semibold">已发布消息记录 ({{ records.length }})</div>
      <div v-if="loading" class="p-8 text-center text-xs text-muted-foreground">正在加载…</div>
      <div v-else-if="!records.length" class="p-8 text-center text-xs text-muted-foreground">暂无已发布系统消息</div>
      <div v-else class="divide-y divide-border">
        <div v-for="r in records" :key="r.id" class="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm">
          <div class="min-w-0">
            <div class="font-medium">{{ r.title }}</div>
            <p class="text-xs text-muted-foreground">{{ r.content }}</p>
          </div>
          <div class="text-right text-xs text-muted-foreground">
            <div>{{ r.scope === 'ALL' ? '全员' : '定向' }} · 已读 {{ r.readCount }} / {{ r.recipientCount }}</div>
            <time>{{ new Date(r.createdAt).toLocaleString('zh-CN') }}</time>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
