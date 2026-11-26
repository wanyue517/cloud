<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, CheckCheck } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger } from '@repo/ui/dropdown-menu'
import { getAccessToken } from '@/lib/auth'
import { shareApi, type MessageItem } from '@/api/share'

const router = useRouter()
const open = ref(false)
const unreadCount = ref(0)
const items = ref<MessageItem[]>([])
const loading = ref(false)

let abortController: AbortController | null = null
let reconnectTimer: number | null = null
let disposed = false

async function loadInbox() {
  loading.value = true
  try {
    const res = await shareApi.inboxMessages()
    unreadCount.value = res.unreadCount
    items.value = res.items
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

async function openMessage(msg: MessageItem) {
  if (!msg.read) {
    try {
      await shareApi.markMessageRead(msg.id)
      msg.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch { /* ignore */ }
  }
  if (msg.actionUrl && msg.actionUrl.startsWith('/app')) {
    open.value = false
    await router.push(msg.actionUrl)
  }
}

async function markAllRead() {
  try {
    await shareApi.markAllMessagesRead()
    items.value.forEach(m => { m.read = true })
    unreadCount.value = 0
  } catch { /* ignore */ }
}

async function connectSse(attempt = 0) {
  if (disposed) return
  const token = getAccessToken()
  if (!token) return
  abortController?.abort()
  abortController = new AbortController()
  try {
    const resp = await fetch('/api/v1/messages/events', {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal: abortController.signal,
    })
    if (!resp.ok || !resp.body) throw new Error('SSE unavailable')
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    attempt = 0
    while (!disposed) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      if (buffer.includes('event:new-message') || buffer.includes('event: new-message')) {
        buffer = ''
        void loadInbox()
      }
      if (buffer.length > 8192) buffer = buffer.slice(-1024)
    }
  } catch {
    if (disposed) return
  }
  const delay = Math.min(30_000, 2000 * 2 ** Math.min(attempt, 4))
  reconnectTimer = window.setTimeout(() => { void connectSse(attempt + 1) }, delay)
}

onMounted(() => {
  void loadInbox()
  void connectSse()
})

onBeforeUnmount(() => {
  disposed = true
  abortController?.abort()
  if (reconnectTimer !== null) window.clearTimeout(reconnectTimer)
})
</script>

<template>
  <DropdownMenu v-model:open="open" @update:open="v => { if (v) void loadInbox() }">
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="icon-sm" class="relative" aria-label="站内消息" title="站内消息">
        <Bell :size="17" />
        <span
          v-if="unreadCount > 0"
          class="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white"
        >
          {{ unreadCount > 99 ? '99+' : unreadCount }}
        </span>
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end" class="w-80 p-0">
      <div class="flex items-center justify-between border-b border-border px-3 py-2">
        <strong class="text-xs font-semibold">消息通知 ({{ unreadCount }} 未读)</strong>
        <Button v-if="unreadCount > 0" variant="ghost" size="sm" class="h-7 px-2 text-xs" @click="markAllRead">
          <CheckCheck :size="13" /> 全部已读
        </Button>
      </div>
      <div class="max-h-80 overflow-y-auto divide-y divide-border">
        <div v-if="loading && !items.length" class="p-6 text-center text-xs text-muted-foreground">正在加载消息…</div>
        <div v-else-if="!items.length" class="p-6 text-center text-xs text-muted-foreground">暂无新消息</div>
        <button
          v-for="msg in items"
          :key="msg.id"
          type="button"
          class="flex w-full flex-col gap-1 px-3 py-2.5 text-left transition hover:bg-muted/50"
          :class="msg.read ? 'opacity-70' : 'bg-primary/5'"
          @click="openMessage(msg)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="truncate text-xs font-medium">{{ msg.title }}</span>
            <span v-if="!msg.read" class="size-2 shrink-0 rounded-full bg-primary" />
          </div>
          <p class="line-clamp-2 text-xs text-muted-foreground">{{ msg.content }}</p>
          <time class="text-[10px] text-muted-foreground">{{ new Date(msg.createdAt).toLocaleString('zh-CN') }}</time>
        </button>
      </div>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
