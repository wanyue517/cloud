<script setup lang="ts">
import { Input } from '@repo/ui/input'
import { Select } from '@repo/ui/select'
const settings = defineModel<{ mode: string; endpoint: string }>({ required: true })
defineProps<{ disabled?: boolean }>()
</script>

<template>
  <fieldset class="space-y-3 rounded-lg border p-3" :disabled="disabled">
    <legend class="px-1 text-sm font-medium">媒体播放方式</legend>
    <label class="block space-y-1 text-sm">
      <span>播放路径</span>
      <Select v-model="settings.mode" aria-label="媒体播放方式" :disabled="disabled" :options="[
        { value: 'AUTO', label: '自动（优先直连，失败后代理）' },
        { value: 'DIRECT', label: '对象存储直连' },
        { value: 'PROXY', label: '后端流式代理' },
      ]" />
    </label>
    <label v-if="settings.mode !== 'PROXY'" class="block space-y-1 text-sm">
      <span>浏览器访问端点（可选）</span>
      <Input v-model="settings.endpoint" aria-label="播放访问端点" placeholder="留空使用实例 Endpoint" :disabled="disabled" />
    </label>
    <p class="text-xs text-muted-foreground">直连使用短期签名地址，减少服务器带宽占用。端点须可被用户设备访问，并与同一存储桶匹配；为本站来源配置 GET、HEAD 的跨域访问。R2 请使用 S3 API 端点。代理适合仅服务器可达的存储，按需转发视频片段。此设置独立于上传方式。</p>
  </fieldset>
</template>
