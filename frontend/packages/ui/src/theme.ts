import { reactive, watch } from 'vue'
export type ColorMode = 'system' | 'light' | 'dark'
export const accentColors = { blue: '#2563eb', violet: '#7c3aed', green: '#15803d', rose: '#be185d', orange: '#c2410c' } as const
const saved = (() => { try { const value = JSON.parse(localStorage.getItem('cloudlane.theme') || '{}'); return value && typeof value === 'object' ? value : {} } catch { return {} } })()
export const theme = reactive({
  mode: (['system', 'light', 'dark'].includes(saved.mode) ? saved.mode : 'system') as ColorMode,
  accent: (Object.prototype.hasOwnProperty.call(accentColors, saved.accent) ? saved.accent : 'blue') as keyof typeof accentColors,
  radius: [0, 0.5, 0.75, 1].includes(saved.radius) ? saved.radius as number : 0.75,
  compact: saved.compact === true,
})
const media = window.matchMedia('(prefers-color-scheme: dark)')
function apply() {
  const root = document.documentElement
  root.classList.toggle('dark', theme.mode === 'dark' || theme.mode === 'system' && media.matches)
  root.style.setProperty('--primary', accentColors[theme.accent])
  root.style.setProperty('--primary-foreground', '#ffffff')
  root.style.setProperty('--ring', accentColors[theme.accent])
  root.style.setProperty('--sidebar-primary', accentColors[theme.accent])
  root.style.setProperty('--app-selected', 'color-mix(in srgb, var(--primary) 12%, var(--background))')
  root.style.setProperty('--sidebar-accent', 'color-mix(in srgb, var(--primary) 12%, var(--background))')
  root.style.setProperty('--radius', `${theme.radius}rem`)
  root.dataset.density = theme.compact ? 'compact' : 'comfortable'
}
export function initTheme() {
  apply()
  media.addEventListener('change', apply)
  watch(theme, () => { apply(); localStorage.setItem('cloudlane.theme', JSON.stringify(theme)) })
}
