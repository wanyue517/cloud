import { reactive } from 'vue'

export type ToastVariant = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: number
  variant: ToastVariant
  message: string
  duration: number
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let seq = 0

export function dismissToast(id: number) {
  const idx = state.items.findIndex(i => i.id === id)
  if (idx >= 0) state.items.splice(idx, 1)
}

function push(variant: ToastVariant, message: string, duration?: number) {
  if (!message) return 0
  const id = ++seq
  const ms = duration ?? (variant === 'error' ? 5000 : 3500)
  state.items.push({ id, variant, message, duration: ms })
  if (state.items.length > 5) state.items.shift()
  if (ms > 0 && typeof setTimeout === 'function') {
    setTimeout(() => dismissToast(id), ms)
  }
  return id
}

export const toast = {
  success: (message: string, duration?: number) => push('success', message, duration),
  error: (message: string, duration?: number) => push('error', message, duration),
  info: (message: string, duration?: number) => push('info', message, duration),
  warning: (message: string, duration?: number) => push('warning', message, duration),
  dismiss: dismissToast,
}

export function useToastItems() {
  return state.items
}
