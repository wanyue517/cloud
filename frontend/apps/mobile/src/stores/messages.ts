import { reactive } from 'vue'
import { showToast } from 'vant'
import { getAccessToken } from '@repo/session/auth'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileShareApi, subscribeSseWithBackoff, type MessageItem } from '@/api/share'
import { setUnreadMessageCount } from '@/stores/mobile-ui'

export const messagesState = reactive<{
  items: MessageItem[]
  unreadCount: number
  loading: boolean
  error: string
}>({
  items: [],
  unreadCount: 0,
  loading: false,
  error: '',
})

let stopSse: (() => void) | null = null

export async function refreshMessages(silent = false) {
  if (!getAccessToken()) return
  if (!silent) messagesState.loading = true
  messagesState.error = ''
  try {
    const res = await mobileShareApi.inboxMessages()
    messagesState.items = res.items
    messagesState.unreadCount = res.unreadCount
    setUnreadMessageCount(res.unreadCount)
  } catch (err) {
    if (!silent) {
      messagesState.error = normalizeErrorMessage(err, '加载消息失败')
    }
  } finally {
    messagesState.loading = false
  }
}

export async function markMessageAsRead(id: string) {
  const item = messagesState.items.find(m => m.id === id)
  if (!item || item.read) return
  try {
    await mobileShareApi.markMessageRead(id)
    item.read = true
    messagesState.unreadCount = Math.max(0, messagesState.unreadCount - 1)
    setUnreadMessageCount(messagesState.unreadCount)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '标记已读失败'))
  }
}

export async function markAllMessagesAsRead() {
  try {
    await mobileShareApi.markAllMessagesRead()
    for (const item of messagesState.items) item.read = true
    messagesState.unreadCount = 0
    setUnreadMessageCount(0)
    showToast('已全部标记为已读')
  } catch (err) {
    showToast(normalizeErrorMessage(err, '操作失败'))
  }
}

export function startMessagesBridge() {
  if (stopSse || !getAccessToken()) return
  void refreshMessages(true)
  stopSse = subscribeSseWithBackoff('/api/v1/messages/events', (_eventName, data) => {
    void refreshMessages(true)
    try {
      const parsed = JSON.parse(data) as { title?: string }
      if (parsed.title) {
        showToast(`新消息：${parsed.title}`)
      }
    } catch {
      // non-JSON keepalive/ping
    }
  })
}

export function stopMessagesBridge() {
  stopSse?.()
  stopSse = null
  messagesState.items = []
  messagesState.unreadCount = 0
  setUnreadMessageCount(0)
}
