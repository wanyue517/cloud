<script setup lang="ts">
import { Settings2 } from '@lucide/vue'
import { Button } from './components/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from './components/dialog'
import { Switch } from './components/switch'
import { accentColors, theme } from './theme'

withDefaults(defineProps<{ showCompactOption?: boolean }>(), { showCompactOption: true })
const names = { blue: '海蓝', violet: '紫罗兰', green: '森林', rose: '玫瑰', orange: '暖橙' }
</script>

<template>
  <Dialog>
    <DialogTrigger as-child>
      <Button variant="ghost" size="sm" class="w-full justify-start">
        <Settings2 :size="16" /> 外观设置
      </Button>
    </DialogTrigger>
    <DialogContent>
      <DialogHeader>
        <DialogTitle>外观设置</DialogTitle>
        <DialogDescription>调整工作台主题与显示偏好，设置会自动保存在当前浏览器。</DialogDescription>
      </DialogHeader>
      <fieldset class="space-y-3">
        <legend class="mb-3 font-medium">颜色模式</legend>
        <div class="flex gap-2">
          <Button
            v-for="option in [{ id: 'system', name: '跟随系统' }, { id: 'light', name: '浅色' }, { id: 'dark', name: '深色' }]"
            :key="option.id"
            :variant="theme.mode === option.id ? 'default' : 'outline'"
            :aria-pressed="theme.mode === option.id"
            @click="theme.mode = option.id as typeof theme.mode"
          >
            {{ option.name }}
          </Button>
        </div>
      </fieldset>
      <fieldset class="space-y-3">
        <legend class="mb-3 font-medium">强调色</legend>
        <div class="flex flex-wrap gap-2">
          <Button
            v-for="(color, key) in accentColors"
            :key="key"
            variant="outline"
            :aria-pressed="theme.accent === key"
            :class="theme.accent === key ? 'ring-2 ring-ring' : ''"
            @click="theme.accent = key"
          >
            <span class="size-3 rounded-full" :style="{ background: color }" />{{ names[key] }}
          </Button>
        </div>
      </fieldset>
      <fieldset>
        <legend class="mb-3 font-medium">圆角</legend>
        <div class="flex gap-2">
          <Button
            v-for="radius in [0, 0.5, 0.75, 1]"
            :key="radius"
            :variant="theme.radius === radius ? 'default' : 'outline'"
            :aria-pressed="theme.radius === radius"
            @click="theme.radius = radius"
          >
            {{ radius === 0 ? '直角' : `${radius * 16}px` }}
          </Button>
        </div>
      </fieldset>
      <div v-if="showCompactOption" class="flex items-center justify-between rounded-lg border border-border px-3 py-2.5 text-sm">
        <span>紧凑文件列表</span>
        <Switch :model-value="theme.compact" @update:model-value="v => { theme.compact = v }" />
      </div>
    </DialogContent>
  </Dialog>
</template>
