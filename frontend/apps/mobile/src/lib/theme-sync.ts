import { ref, watch } from 'vue'
import { initTheme, theme } from '@repo/ui/theme'

const LIGHT_THEME_COLOR = '#ffffff'
const DARK_THEME_COLOR = '#09090b'

export const isDarkTheme = ref(false)

function applyMobileThemeSync() {
  if (typeof document === 'undefined' || typeof window === 'undefined') return
  const dark =
    theme.mode === 'dark' ||
    (theme.mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  isDarkTheme.value = dark
  document.documentElement.classList.toggle('van-theme-dark', dark)
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'theme-color'
    document.head.appendChild(meta)
  }
  meta.content = dark ? DARK_THEME_COLOR : LIGHT_THEME_COLOR
}

export function initMobileTheme() {
  initTheme()
  applyMobileThemeSync()
  watch(() => theme.mode, applyMobileThemeSync)
  if (typeof window !== 'undefined') {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    media.addEventListener?.('change', applyMobileThemeSync)
  }
}
