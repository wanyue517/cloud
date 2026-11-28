<script setup lang="ts">
import { Input } from '@repo/ui/input'
import { Select } from '@repo/ui/select'

const settings = defineModel<{
  uploadMode: string
  thresholdMb: number
  partMb: number
  concurrency: number
  ttl: number
  fallback: string
}>({ required: true })
defineProps<{ disabled?: boolean }>()
</script>

<template>
  <fieldset class="space-y-3 rounded-lg border p-3" :disabled="disabled">
    <legend class="px-1 text-sm font-medium">文件上传方式</legend>
    <label class="block space-y-1 text-sm">
      <span>传输路径</span>
      <Select v-model="settings.uploadMode" aria-label="文件上传方式" :disabled="disabled" :options="[
        { value: 'PROXY', label: '后端中转' },
        { value: 'DIRECT', label: '对象存储直传' },
        { value: 'AUTO', label: '自动选择（大文件直传）' },
      ]" />
    </label>
    <p class="text-xs text-muted-foreground">中转兼容性好，占用服务器带宽与临时磁盘。直传将文件直接发往对象存储，用户网络必须能访问上传端点。配置仅影响新任务。</p>
    <template v-if="settings.uploadMode !== 'PROXY'">
      <label v-if="settings.uploadMode === 'AUTO'" class="block space-y-1 text-sm">
        <span>直传文件大小阈值（MiB）</span>
        <Input v-model.number="settings.thresholdMb" aria-label="直传大小阈值" type="number" min="0" max="10240" />
      </label>
      <div class="grid grid-cols-2 gap-3">
        <label class="block space-y-1 text-sm"><span>分片大小（MiB）</span><Input v-model.number="settings.partMb" aria-label="直传分片大小" type="number" min="5" max="64" /></label>
        <label class="block space-y-1 text-sm"><span>并发分片数</span><Input v-model.number="settings.concurrency" aria-label="直传并发数" type="number" min="1" max="6" /></label>
        <label class="block space-y-1 text-sm"><span>地址有效期（秒）</span><Input v-model.number="settings.ttl" aria-label="直传地址有效期" type="number" min="60" max="3600" /></label>
        <label class="block space-y-1 text-sm"><span>直传失败后</span><Select v-model="settings.fallback" aria-label="直传失败处理" :disabled="disabled" :options="[{ value: 'no', label: '保留进度，提示重试' }, { value: 'yes', label: '切换中转并重新上传' }]" /></label>
      </div>
      <p class="text-xs text-muted-foreground">启用前请为存储桶配置本站来源的 CORS，允许 PUT 和 Content-Type，并配置未完成分片的生命周期清理。测试连接仅验证服务器连通性，不代表浏览器可以直传。直传文件暂不参与基于整文件 SHA-256 的秒传复用。</p>
    </template>
  </fieldset>
</template>
