import { reactive } from 'vue'

export interface UndoToastPayload {
  tokenId: string
  summary: string
  durationMs?: number
  onUndo: (tokenId: string) => Promise<void> | void
}

export interface MobileUiState {
  unreadMessageCount: number
  activeUploadCount: number
  tabBarHidden: boolean
  undo: {
    visible: boolean
    tokenId: string
    summary: string
    expiresAt: number
    durationMs: number
    busy: boolean
    onUndo: ((tokenId: string) => Promise<void> | void) | null
  }
}

export const mobileUi = reactive<MobileUiState>({
  unreadMessageCount: 0,
  activeUploadCount: 0,
  tabBarHidden: false,
  undo: {
    visible: false,
    tokenId: '',
    summary: '',
    expiresAt: 0,
    durationMs: 10_000,
    busy: false,
    onUndo: null,
  },
})

let undoTimer: ReturnType<typeof setTimeout> | null = null

export function setUnreadMessageCount(count: number) {
  mobileUi.unreadMessageCount = Math.max(0, Math.floor(count))
}

export function setActiveUploadCount(count: number) {
  mobileUi.activeUploadCount = Math.max(0, Math.floor(count))
}

export function setTabBarHidden(hidden: boolean) {
  mobileUi.tabBarHidden = hidden
}

export function showUndoToast(payload: UndoToastPayload) {
  if (undoTimer) {
    clearTimeout(undoTimer)
    undoTimer = null
  }
  const durationMs = payload.durationMs ?? 10_000
  mobileUi.undo.visible = true
  mobileUi.undo.tokenId = payload.tokenId
  mobileUi.undo.summary = payload.summary
  mobileUi.undo.durationMs = durationMs
  mobileUi.undo.expiresAt = Date.now() + durationMs
  mobileUi.undo.busy = false
  mobileUi.undo.onUndo = payload.onUndo

  undoTimer = setTimeout(() => {
    dismissUndoToast()
  }, durationMs)
}

export function dismissUndoToast() {
  if (undoTimer) {
    clearTimeout(undoTimer)
    undoTimer = null
  }
  mobileUi.undo.visible = false
  mobileUi.undo.tokenId = ''
  mobileUi.undo.summary = ''
  mobileUi.undo.expiresAt = 0
  mobileUi.undo.busy = false
  mobileUi.undo.onUndo = null
}

export async function triggerUndoToast(): Promise<boolean> {
  if (!mobileUi.undo.visible || mobileUi.undo.busy || !mobileUi.undo.onUndo) return false
  const tokenId = mobileUi.undo.tokenId
  const handler = mobileUi.undo.onUndo
  mobileUi.undo.busy = true
  try {
    await handler(tokenId)
    dismissUndoToast()
    return true
  } catch (error) {
    mobileUi.undo.busy = false
    throw error
  }
}
