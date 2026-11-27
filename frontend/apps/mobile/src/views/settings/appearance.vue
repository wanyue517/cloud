<script setup lang="ts">
import { Switch as VanSwitch } from 'vant'
import { Check, Moon, Sun, SunMoon } from '@lucide/vue'
import { accentColors, theme, type ColorMode } from '@repo/ui/theme'
import { MobileShell } from '@/components/common'

const colorModes: { id: ColorMode; label: string; icon: typeof Sun }[] = [
  { id: 'system', label: '跟随系统', icon: SunMoon },
  { id: 'light', label: '浅色', icon: Sun },
  { id: 'dark', label: '深色', icon: Moon },
]

const accentOptions: { key: keyof typeof accentColors; name: string; color: string }[] = [
  { key: 'blue', name: '海蓝', color: accentColors.blue },
  { key: 'violet', name: '紫罗兰', color: accentColors.violet },
  { key: 'green', name: '森林', color: accentColors.green },
  { key: 'rose', name: '玫瑰', color: accentColors.rose },
  { key: 'orange', name: '暖橙', color: accentColors.orange },
]

function setMode(mode: ColorMode) {
  theme.mode = mode
}

function setAccent(key: keyof typeof accentColors) {
  theme.accent = key
}
</script>

<template>
  <MobileShell title="主题与外观" fallback-path="/me" padded>
    <div class="space-y-5">
      <!-- 明暗模式 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <h2 class="text-sm font-semibold text-foreground">颜色模式</h2>
        <div class="grid grid-cols-3 gap-2.5">
          <button
            v-for="item in colorModes"
            :key="item.id"
            type="button"
            class="flex flex-col items-center justify-center gap-1.5 rounded-xl border py-3 text-xs font-medium transition-colors"
            :class="
              theme.mode === item.id
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-muted-foreground'
            "
            @click="setMode(item.id)"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </button>
        </div>
      </section>

      <!-- 强调色 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <h2 class="text-sm font-semibold text-foreground">主题强调色</h2>
        <div class="grid grid-cols-5 gap-2">
          <button
            v-for="opt in accentOptions"
            :key="opt.key"
            type="button"
            class="flex flex-col items-center gap-1.5 py-1"
            @click="setAccent(opt.key)"
          >
            <span
              class="flex h-10 w-10 items-center justify-center rounded-full text-white transition-transform"
              :class="theme.accent === opt.key ? 'scale-110 ring-2 ring-offset-2 ring-primary ring-offset-card' : ''"
              :style="{ backgroundColor: opt.color }"
            >
              <Check v-if="theme.accent === opt.key" :size="16" :stroke-width="2.5" />
            </span>
            <span class="text-[11px]" :class="theme.accent === opt.key ? 'text-foreground font-medium' : 'text-muted-foreground'">
              {{ opt.name }}
            </span>
          </button>
        </div>
      </section>

      <!-- 界面密度 -->
      <section class="rounded-2xl border border-border bg-card px-4 py-3.5 flex items-center justify-between gap-3">
        <div>
          <p class="text-sm font-medium text-foreground">紧凑布局</p>
          <p class="text-xs text-muted-foreground mt-0.5">缩小列表间距以在单屏展示更多文件</p>
        </div>
        <VanSwitch v-model="theme.compact" size="22px" />
      </section>
    </div>
  </MobileShell>
</template>
