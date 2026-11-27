<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, ref, watch } from 'vue'
import { Check, ChevronDown } from '@lucide/vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { cn } from '../../lib/utils'

const DEFAULT_PRESETS = [
  '#6366f1',
  '#2563eb',
  '#0ea5e9',
  '#06b6d4',
  '#10b981',
  '#22c55e',
  '#eab308',
  '#f59e0b',
  '#f97316',
  '#ef4444',
  '#ec4899',
  '#8b5cf6',
]

const props = withDefaults(defineProps<{
  modelValue?: string
  presets?: string[]
  disabled?: boolean
  ariaLabel?: string
  class?: HTMLAttributes['class']
}>(), {
  modelValue: '#6366f1',
  disabled: false,
  ariaLabel: '选择颜色',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const open = ref(false)
const hue = ref(239)
const sat = ref(84)
const val = ref(95)
const hexInput = ref('#6366F1')
const svAreaRef = ref<HTMLElement | null>(null)
const hueBarRef = ref<HTMLElement | null>(null)

const swatches = computed(() => (props.presets && props.presets.length ? props.presets : DEFAULT_PRESETS))

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}

function normalizeHex(raw?: string): string | null {
  if (!raw) return null
  const clean = raw.trim().replace(/^#/, '')
  if (/^[0-9a-fA-F]{3}$/.test(clean)) {
    return `#${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}`.toLowerCase()
  }
  if (/^[0-9a-fA-F]{6}$/.test(clean)) {
    return `#${clean}`.toLowerCase()
  }
  return null
}

function hexToHsv(hex: string): { h: number; s: number; v: number } {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.slice(0, 2), 16) / 255
  const g = parseInt(clean.slice(2, 4), 16) / 255
  const b = parseInt(clean.slice(4, 6), 16) / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const d = max - min
  let h = 0
  if (d !== 0) {
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60
    else if (max === g) h = ((b - r) / d + 2) * 60
    else h = ((r - g) / d + 4) * 60
  }
  const s = max === 0 ? 0 : (d / max) * 100
  const v = max * 100
  return { h: Math.round(h), s: Math.round(s), v: Math.round(v) }
}

function hsvToHex(h: number, s: number, v: number): string {
  const sn = clamp(s, 0, 100) / 100
  const vn = clamp(v, 0, 100) / 100
  const c = vn * sn
  const hh = ((h % 360) + 360) % 360 / 60
  const x = c * (1 - Math.abs((hh % 2) - 1))
  let r = 0, g = 0, b = 0
  if (hh < 1) { r = c; g = x }
  else if (hh < 2) { r = x; g = c }
  else if (hh < 3) { g = c; b = x }
  else if (hh < 4) { g = x; b = c }
  else if (hh < 5) { r = x; b = c }
  else { r = c; b = x }
  const m = vn - c
  const toHex = (n: number) => Math.round((n + m) * 255).toString(16).padStart(2, '0')
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`
}

function syncFromProp(raw?: string) {
  const valid = normalizeHex(raw) || '#6366f1'
  hexInput.value = valid.toUpperCase()
  const hsv = hexToHsv(valid)
  if (hsv.s > 0 && hsv.v > 0) hue.value = hsv.h
  sat.value = hsv.s
  val.value = hsv.v
}

watch(() => props.modelValue, syncFromProp, { immediate: true })

const currentHex = computed(() => normalizeHex(props.modelValue) || '#6366f1')
const hueBg = computed(() => `hsl(${hue.value}, 100%, 50%)`)

function emitFromHsv() {
  const next = hsvToHex(hue.value, sat.value, val.value)
  hexInput.value = next.toUpperCase()
  emit('update:modelValue', next)
}

function updateSvFromPointer(e: PointerEvent) {
  const el = svAreaRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = clamp((e.clientX - rect.left) / rect.width, 0, 1)
  const y = clamp((e.clientY - rect.top) / rect.height, 0, 1)
  sat.value = Math.round(x * 100)
  val.value = Math.round((1 - y) * 100)
  emitFromHsv()
}

function onSvPointerDown(e: PointerEvent) {
  updateSvFromPointer(e)
  const onMove = (ev: PointerEvent) => updateSvFromPointer(ev)
  const onUp = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function updateHueFromPointer(e: PointerEvent) {
  const el = hueBarRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = clamp((e.clientX - rect.left) / rect.width, 0, 1)
  hue.value = Math.round(x * 360)
  emitFromHsv()
}

function onHuePointerDown(e: PointerEvent) {
  updateHueFromPointer(e)
  const onMove = (ev: PointerEvent) => updateHueFromPointer(ev)
  const onUp = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}

function pickPreset(hex: string) {
  syncFromProp(hex)
  emit('update:modelValue', hex.toLowerCase())
}

function commitHexInput() {
  const valid = normalizeHex(hexInput.value)
  if (valid) {
    syncFromProp(valid)
    emit('update:modelValue', valid)
  } else {
    hexInput.value = currentHex.value.toUpperCase()
  }
}
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child :disabled="disabled">
      <button
        type="button"
        :disabled="disabled"
        :aria-label="ariaLabel"
        :class="cn(
          'flex h-9 w-full cursor-pointer items-center justify-between gap-2 rounded-md border border-input bg-background px-3 py-1.5 text-sm shadow-2xs transition-colors outline-none',
          'hover:bg-accent/40 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
          'disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30',
          props.class,
        )"
      >
        <span class="flex min-w-0 items-center gap-2">
          <span
            class="size-4 shrink-0 rounded-sm border border-black/15 shadow-2xs dark:border-white/20"
            :style="{ backgroundColor: currentHex }"
          />
          <span class="truncate font-mono text-xs uppercase">{{ currentHex }}</span>
        </span>
        <ChevronDown :size="14" class="shrink-0 text-muted-foreground" />
      </button>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="6"
        class="z-[95] w-64 rounded-lg border border-border bg-popover p-3 text-popover-foreground shadow-lg outline-none"
      >
        <div class="space-y-3">
          <!-- 2D 饱和度 / 明度拾色面板 -->
          <div
            ref="svAreaRef"
            class="relative h-32 w-full cursor-crosshair select-none overflow-hidden rounded-md border border-border/80 touch-none"
            :style="{ backgroundColor: hueBg }"
            @pointerdown.prevent="onSvPointerDown"
          >
            <div class="absolute inset-0 bg-gradient-to-r from-white to-transparent" />
            <div class="absolute inset-0 bg-gradient-to-t from-black to-transparent" />
            <span
              class="pointer-events-none absolute size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,0.45)]"
              :style="{ left: `${sat}%`, top: `${100 - val}%`, backgroundColor: currentHex }"
            />
          </div>

          <!-- 色相滑块 -->
          <div
            ref="hueBarRef"
            class="relative h-3 w-full cursor-pointer select-none rounded-full border border-border/60 touch-none"
            style="background: linear-gradient(to right, #ff0000 0%, #ffff00 17%, #00ff00 33%, #00ffff 50%, #0000ff 67%, #ff00ff 83%, #ff0000 100%);"
            @pointerdown.prevent="onHuePointerDown"
          >
            <span
              class="pointer-events-none absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,0.35)]"
              :style="{ left: `${(hue / 360) * 100}%`, backgroundColor: hueBg }"
            />
          </div>

          <!-- 预设色板 -->
          <div class="grid grid-cols-6 gap-1.5">
            <button
              v-for="c in swatches"
              :key="c"
              type="button"
              :aria-label="`选择颜色 ${c}`"
              :title="c.toUpperCase()"
              class="flex size-7 cursor-pointer items-center justify-center rounded-md border border-black/10 transition-transform hover:scale-105 dark:border-white/15"
              :style="{ backgroundColor: c }"
              @click="pickPreset(c)"
            >
              <Check v-if="currentHex.toLowerCase() === c.toLowerCase()" :size="13" class="text-white drop-shadow-[0_1px_1px_rgba(0,0,0,0.6)]" />
            </button>
          </div>

          <!-- HEX 精确输入 -->
          <div class="flex items-center gap-2 pt-0.5">
            <span class="text-xs text-muted-foreground shrink-0">HEX</span>
            <input
              v-model="hexInput"
              type="text"
              maxlength="7"
              spellcheck="false"
              class="h-8 flex-1 rounded-md border border-input bg-background px-2.5 font-mono text-xs uppercase outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
              @blur="commitHexInput"
              @keydown.enter.prevent="commitHexInput"
            />
          </div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
