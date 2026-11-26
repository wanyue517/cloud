import { reactive } from 'vue'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
}

interface ConfirmState {
  open: boolean
  title: string
  description: string
  confirmText: string
  cancelText: string
  variant: 'default' | 'destructive'
  resolver: ((accepted: boolean) => void) | null
}

const state = reactive<ConfirmState>({
  open: false,
  title: '',
  description: '',
  confirmText: '确认',
  cancelText: '取消',
  variant: 'default',
  resolver: null,
})

export function confirm(options: ConfirmOptions): Promise<boolean> {
  if (state.resolver) {
    state.resolver(false)
    state.resolver = null
  }
  state.title = options.title
  state.description = options.description || ''
  state.confirmText = options.confirmText || '确认'
  state.cancelText = options.cancelText || '取消'
  state.variant = options.variant || 'default'
  state.open = true
  return new Promise<boolean>(resolve => {
    state.resolver = resolve
  })
}

export function settleConfirm(accepted: boolean) {
  state.open = false
  const fn = state.resolver
  state.resolver = null
  fn?.(accepted)
}

export function useConfirmState() {
  return state
}
