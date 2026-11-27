<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, CheckCheck } from '@lucide/vue'
import { formatRelativeTime } from '@repo/drive-core/format'
import type { MessageItem } from '@/api/share'
import { mapMessageActionUrlToMobile } from '@/lib/private-vault'
import {
  markAllMessagesAsRead,
  markMessageAsRead,
  messagesState,
  refreshMessages,
} from '@/stores/messages'
import { EmptyState, ErrorState, MobileShell, PullRefresh, SkeletonList } from '@/components/common'

const router = useRouter()
const refreshing = ref(false)

onMounted(() => {
  void refreshMessages(false)
})

async function onRefresh() {
  refreshing.value = true
  await refreshMessages(true)
  refreshing.value = false
}

async function openMessage(msg: MessageItem) {
  await markMessageAsRead(msg.id)
  const target = mapMessageActionUrlToMobile(msg.actionUrl)
  if (target) {
    void router.push(target)
  }
}
</script>

<template>
  <MobileShell title="消息中心" back-to="/home">
    <template #right>
      <button
        v-if="messagesState.unreadCount > 0"
        type="button"
        class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-primary active:bg-accent"
        @click="markAllMessagesAsRead"
      >
        <CheckCheck :size="14" />
        <span>全部已读</span>
      </button>
    </template>

    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <SkeletonList v-if="messagesState.loading && messagesState.items.length === 0" :count="6" />
      <ErrorState
        v-else-if="messagesState.error"
        :message="messagesState.error"
        @retry="refreshMessages(false)"
      />
      <EmptyState
        v-else-if="messagesState.items.length === 0"
        title="暂无站内消息"
        description="共享邀请、系统公告与协作提醒会实时推送到这里"
      />

      <div v-else class="divide-y divide-border px-3.5 pb-16">
        <button
          v-for="msg in messagesState.items"
          :key="msg.id"
          type="button"
          class="w-full flex items-start gap-3 py-3.5 text-left active:bg-accent/40"
          @click="openMessage(msg)"
        >
          <span
            class="relative flex h-9 w-9 items-center justify-center rounded-xl shrink-0 mt-0.5"
            :class="msg.read ? 'bg-muted text-muted-foreground' : 'bg-primary/10 text-primary'"
          >
            <Bell :size="17" />
            <span
              v-if="!msg.read"
              class="absolute -top-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-destructive ring-2 ring-background"
            />
          </span>

          <div class="flex-1 min-w-0 space-y-1">
            <div class="flex items-center justify-between gap-2">
              <p
                class="text-sm truncate"
                :class="msg.read ? 'font-normal text-foreground' : 'font-semibold text-foreground'"
              >
                {{ msg.title }}
              </p>
              <span class="text-[11px] text-muted-foreground shrink-0">
                {{ formatRelativeTime(msg.createdAt) }}
              </span>
            </div>
            <p class="text-xs text-muted-foreground line-clamp-2 break-words">
              {{ msg.content }}
            </p>
          </div>
        </button>
      </div>
    </PullRefresh>
  </MobileShell>
</template>
