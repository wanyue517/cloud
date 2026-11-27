<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { Download, ExternalLink, FileQuestion } from '@lucide/vue'
import { formatBytes, formatDateTime } from '@repo/drive-core/format'
import { getExtension } from '@repo/drive-core/preview-registry'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { mobileCapabilities, openBlobWithExternalApp } from '@/native'

const props = withDefaults(
  defineProps<{
    node: DriveNode
    downloading?: boolean
  }>(),
  {
    downloading: false,
  },
)

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const ext = computed(() => {
  const e = getExtension(props.node.name)
  return e ? `.${e.toUpperCase()}` : '未知格式'
})

const openingExternal = ref(false)
async function handleOpenExternal() {
  if (openingExternal.value) return
  openingExternal.value = true
  try {
    const blob = await mobileDriveApi.downloadContent(props.node.spaceId, props.node.id)
    const res = await openBlobWithExternalApp(blob, props.node.name, props.node.mimeType)
    if (!res.ok) {
      showToast(res.message || '无法唤起外部应用')
    }
  } catch {
    showToast('下载文件失败，无法使用外部应用打开')
  } finally {
    openingExternal.value = false
  }
}
</script>

<template>
  <div
    class="flex-1 min-h-0 flex flex-col items-center justify-center px-6 text-center bg-background text-foreground"
    @click="emit('toggle-chrome')"
  >
    <div
      class="rounded-2xl border border-border bg-card p-6 w-full max-w-xs space-y-3 shadow-xs"
      @click.stop
    >
      <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
        <FileQuestion :size="28" />
      </div>

      <p class="text-base font-semibold text-foreground break-all">{{ props.node.name }}</p>
      <p class="text-xs text-muted-foreground">暂不支持在线预览此格式，可下载后使用本地应用打开</p>

      <dl class="divide-y divide-border rounded-xl border border-border bg-background text-xs text-left">
        <div class="flex items-center justify-between px-3 py-2">
          <dt class="text-muted-foreground">扩展名</dt>
          <dd class="font-medium text-foreground">{{ ext }}</dd>
        </div>
        <div class="flex items-center justify-between px-3 py-2 gap-3">
          <dt class="text-muted-foreground shrink-0">MIME</dt>
          <dd class="text-foreground truncate">{{ props.node.mimeType || '未知' }}</dd>
        </div>
        <div class="flex items-center justify-between px-3 py-2">
          <dt class="text-muted-foreground">文件大小</dt>
          <dd class="text-foreground">{{ formatBytes(props.node.size) }}</dd>
        </div>
        <div class="flex items-center justify-between px-3 py-2">
          <dt class="text-muted-foreground">更新时间</dt>
          <dd class="text-foreground">{{ formatDateTime(props.node.updatedAt) }}</dd>
        </div>
      </dl>

      <VanButton block round type="primary" :loading="props.downloading" @click="emit('download')">
        <span class="inline-flex items-center gap-1.5">
          <Download :size="15" />
          <span>下载文件</span>
        </span>
      </VanButton>

      <VanButton
        v-if="mobileCapabilities.openWithExternalApp"
        block
        round
        plain
        type="primary"
        :loading="openingExternal"
        @click="handleOpenExternal"
      >
        <span class="inline-flex items-center gap-1.5">
          <ExternalLink :size="15" />
          <span>用其他应用打开</span>
        </span>
      </VanButton>
    </div>
  </div>
</template>
