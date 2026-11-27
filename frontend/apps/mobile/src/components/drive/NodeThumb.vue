<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  File,
  FileArchive,
  FileAudio,
  FileCode2,
  FileImage,
  FileSpreadsheet,
  FileText,
  FileVideo,
  Folder,
  Play,
} from '@lucide/vue'
import { mobileDriveApi } from '@/api/drive'
import { getMobileThumbWidth, networkPrefs, networkStatus } from '@/lib/network'
import { mobileThumbCache, supportsFileThumbnail } from '@/lib/thumb-cache'

export interface ThumbNodeLike {
  id: string
  spaceId: string
  name: string
  nodeType: 'FILE' | 'FOLDER'
  mimeType?: string
  currentVersionId?: string | null
  revision?: number
}

const props = withDefaults(
  defineProps<{
    node: ThumbNodeLike
    variant?: 'list' | 'grid'
  }>(),
  {
    variant: 'list',
  },
)

const rootEl = ref<HTMLElement | null>(null)
const thumbUrl = ref<string | null>(null)
const visible = ref(false)
let observer: IntersectionObserver | null = null

const canThumb = computed(() => supportsFileThumbnail(props.node))

const isVideo = computed(() => {
  if (props.node.nodeType !== 'FILE') return false
  const mime = (props.node.mimeType || '').toLowerCase()
  const ext = props.node.name.includes('.') ? props.node.name.split('.').pop()?.toLowerCase() || '' : ''
  return mime.startsWith('video/') || ['mp4', 'webm', 'mov', 'mkv', 'avi', 'm4v', 'flv', 'wmv', 'ogv'].includes(ext)
})

const iconMeta = computed(() => {
  if (props.node.nodeType === 'FOLDER') {
    return { icon: Folder, cls: 'bg-primary/10 text-primary', badge: '' }
  }
  const mime = (props.node.mimeType || '').toLowerCase()
  const ext = props.node.name.includes('.') ? props.node.name.split('.').pop()?.toLowerCase() || '' : ''

  if (mime.startsWith('image/')) {
    return { icon: FileImage, cls: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400', badge: '图片' }
  }
  if (mime.startsWith('video/') || isVideo.value) {
    return { icon: FileVideo, cls: 'bg-purple-500/10 text-purple-600 dark:text-purple-400', badge: '视频' }
  }
  if (mime.startsWith('audio/')) {
    return { icon: FileAudio, cls: 'bg-pink-500/10 text-pink-600 dark:text-pink-400', badge: '音频' }
  }
  if (mime === 'application/pdf' || ext === 'pdf') {
    return { icon: FileText, cls: 'bg-red-500/10 text-red-600 dark:text-red-400', badge: 'PDF' }
  }
  if (['xls', 'xlsx', 'csv'].includes(ext) || mime.includes('spreadsheet')) {
    return { icon: FileSpreadsheet, cls: 'bg-green-500/10 text-green-600 dark:text-green-400', badge: '表格' }
  }
  if (['zip', 'tar', 'gz', 'tgz', 'rar', '7z'].includes(ext) || mime.includes('zip') || mime.includes('archive')) {
    return { icon: FileArchive, cls: 'bg-amber-500/10 text-amber-600 dark:text-amber-400', badge: '压缩' }
  }
  if (
    ['ts', 'tsx', 'js', 'jsx', 'vue', 'java', 'py', 'go', 'rs', 'json', 'yml', 'yaml', 'md', 'html', 'css', 'sql', 'sh'].includes(ext) ||
    mime.startsWith('text/')
  ) {
    return { icon: FileCode2, cls: 'bg-sky-500/10 text-sky-600 dark:text-sky-400', badge: ext ? ext.toUpperCase().slice(0, 4) : '文本' }
  }
  if (['doc', 'docx'].includes(ext)) {
    return { icon: FileText, cls: 'bg-blue-500/10 text-blue-600 dark:text-blue-400', badge: 'DOC' }
  }
  if (['ppt', 'pptx'].includes(ext)) {
    return { icon: FileText, cls: 'bg-orange-500/10 text-orange-600 dark:text-orange-400', badge: 'PPT' }
  }
  return { icon: File, cls: 'bg-muted text-muted-foreground', badge: ext ? ext.toUpperCase().slice(0, 4) : '文件' }
})

async function loadThumb() {
  if (!canThumb.value || !visible.value) return
  const width = getMobileThumbWidth(networkPrefs, networkStatus)
  const verKey = props.node.currentVersionId || String(props.node.revision ?? '')
  const key = `${props.node.spaceId}:${props.node.id}:${verKey}:${width}`
  const cached = mobileThumbCache.get(key)
  if (cached) {
    thumbUrl.value = cached
    return
  }
  const url = await mobileThumbCache.getOrFetch(key, async () => {
    try {
      return await mobileDriveApi.thumbnail(
        props.node.spaceId,
        props.node.id,
        width,
        props.node.currentVersionId ?? undefined,
      )
    } catch {
      return null
    }
  })
  if (url) thumbUrl.value = url
}

onMounted(() => {
  if (!canThumb.value) return
  if (typeof IntersectionObserver !== 'undefined' && rootEl.value) {
    observer = new IntersectionObserver(
      entries => {
        if (entries.some(e => e.isIntersecting)) {
          visible.value = true
          observer?.disconnect()
          observer = null
          void loadThumb()
        }
      },
      { rootMargin: '160px' },
    )
    observer.observe(rootEl.value)
  } else {
    visible.value = true
    void loadThumb()
  }
})

watch(
  () => [props.node.id, props.node.revision, props.node.currentVersionId],
  () => {
    thumbUrl.value = null
    if (visible.value && canThumb.value) {
      void loadThumb()
    }
  },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div
    ref="rootEl"
    class="relative overflow-hidden flex items-center justify-center shrink-0 select-none"
    :class="[
      props.variant === 'grid' ? 'w-full aspect-square rounded-xl' : 'h-10 w-10 rounded-xl',
      thumbUrl ? 'bg-muted' : iconMeta.cls,
    ]"
  >
    <img
      v-if="thumbUrl"
      :src="thumbUrl"
      :alt="props.node.name"
      class="h-full w-full object-cover"
      loading="lazy"
      @error="thumbUrl = null"
    />
    <component :is="iconMeta.icon" v-else :size="props.variant === 'grid' ? 30 : 20" />

    <!-- 视频缩略图播放角标 -->
    <span
      v-if="thumbUrl && isVideo"
      class="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/20"
    >
      <span
        class="flex items-center justify-center rounded-full bg-black/60 text-white"
        :class="props.variant === 'grid' ? 'h-8 w-8' : 'h-5 w-5'"
      >
        <Play :size="props.variant === 'grid' ? 14 : 10" class="translate-x-0.5 fill-white" />
      </span>
    </span>

    <span
      v-if="props.variant === 'grid' && iconMeta.badge && !thumbUrl"
      class="absolute top-1.5 left-1.5 rounded bg-background/85 px-1.5 py-0.5 text-[10px] font-medium text-foreground shadow-xs"
    >
      {{ iconMeta.badge }}
    </span>
  </div>
</template>
