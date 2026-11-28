<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { showToast } from 'vant'
import { ArrowLeft, Download, Info, MoreHorizontal, Share2, Star } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { previewKindLabel, resolvePreviewKind } from '@repo/drive-core/preview-registry'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { triggerBlobDownload } from '@/lib/file-actions'
import MobileImagePreviewer from './MobileImagePreviewer.vue'
import MobileUnsupportedPreviewer from './MobileUnsupportedPreviewer.vue'

// 重型渲染依赖（markdown-it / KaTeX / highlight.js / mermaid / pdfjs / xgplayer）按需懒加载
const MobileMarkdownPreviewer = defineAsyncComponent(() => import('./MobileMarkdownPreviewer.vue'))
const MobileTextCodePreviewer = defineAsyncComponent(() => import('./MobileTextCodePreviewer.vue'))
const MobilePdfPreviewer = defineAsyncComponent(() => import('./MobilePdfPreviewer.vue'))
const MobileOfficePreviewer = defineAsyncComponent(() => import('./MobileOfficePreviewer.vue'))
const MobileExcelPreviewer = defineAsyncComponent(() => import('./MobileExcelPreviewer.vue'))
const MobileVideoPreviewer = defineAsyncComponent(() => import('./MobileVideoPreviewer.vue'))
const MobileAudioPreviewer = defineAsyncComponent(() => import('./MobileAudioPreviewer.vue'))
const MobileArchivePreviewer = defineAsyncComponent(() => import('./MobileArchivePreviewer.vue'))

const props = withDefaults(
  defineProps<{
    open: boolean
    node: DriveNode | null
    siblings?: DriveNode[]
    versionId?: string
    favorite?: boolean
  }>(),
  {
    siblings: () => [],
    versionId: undefined,
    favorite: false,
  },
)

const emit = defineEmits<{
  close: []
  select: [node: DriveNode]
  share: [node: DriveNode]
  'toggle-favorite': [node: DriveNode]
  detail: [node: DriveNode]
  more: [node: DriveNode]
}>()

const chromeVisible = ref(true)
const downloading = ref(false)

const previewKind = computed(() =>
  props.node ? resolvePreviewKind(props.node.name, props.node.mimeType) : 'unsupported',
)

const imageSiblings = computed(() =>
  props.siblings.filter(
    n => n.nodeType === 'FILE' && resolvePreviewKind(n.name, n.mimeType) === 'image',
  ),
)

const currentImageIndex = computed(() => {
  if (!props.node || previewKind.value !== 'image') return -1
  return imageSiblings.value.findIndex(n => n.id === props.node!.id)
})

const hasPrevImage = computed(() => currentImageIndex.value > 0)
const hasNextImage = computed(
  () => currentImageIndex.value >= 0 && currentImageIndex.value < imageSiblings.value.length - 1,
)

const counterText = computed(() => {
  if (previewKind.value === 'image' && currentImageIndex.value >= 0 && imageSiblings.value.length > 1) {
    return `${currentImageIndex.value + 1} / ${imageSiblings.value.length}`
  }
  return previewKindLabel(previewKind.value)
})

const isDarkBackdrop = computed(
  () => previewKind.value === 'image' || previewKind.value === 'video',
)

watch(
  () => [props.open, props.node?.spaceId, props.node?.id] as const,
  ([opened, spaceId, nodeId]) => {
    if (opened && spaceId && nodeId) {
      chromeVisible.value = true
      void mobileDriveApi.recordAccess(spaceId, nodeId).catch(() => undefined)
    }
  },
  { immediate: true },
)

function goPrevImage() {
  if (!hasPrevImage.value) return
  const target = imageSiblings.value[currentImageIndex.value - 1]
  if (target) emit('select', target)
}

function goNextImage() {
  if (!hasNextImage.value) return
  const target = imageSiblings.value[currentImageIndex.value + 1]
  if (target) emit('select', target)
}

async function handleDownload() {
  if (!props.node || downloading.value) return
  downloading.value = true
  try {
    showToast('正在准备下载…')
    const blob = await mobileDriveApi.downloadContent(
      props.node.spaceId,
      props.node.id,
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    triggerBlobDownload(blob, props.node.name)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '下载失败'))
  } finally {
    downloading.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="m-fade">
      <div
        v-if="props.open && props.node"
        class="fixed inset-0 z-50 flex flex-col"
        :class="isDarkBackdrop ? 'bg-black text-white' : 'bg-background text-foreground'"
      >
        <!-- 图片工具栏悬浮；视频工具栏参与布局，避免遮挡画面和播放器控件。 -->
        <Transition name="m-slide-down">
          <header
            v-show="chromeVisible"
            class="z-20 flex items-center gap-2 px-2.5 py-2 safe-top border-b shrink-0"
            :class="
              previewKind === 'image'
                ? 'absolute inset-x-0 top-0 bg-black/75 border-white/10 text-white backdrop-blur'
                : isDarkBackdrop ? 'bg-black border-white/10 text-white' : 'bg-background/95 border-border text-foreground backdrop-blur'
            "
          >
        <button
          type="button"
          class="inline-flex h-9 w-9 items-center justify-center rounded-full active:bg-white/10 shrink-0"
          aria-label="关闭预览"
          @click="emit('close')"
        >
          <ArrowLeft :size="20" />
        </button>

        <div class="min-w-0 flex-1">
          <p class="text-sm font-medium truncate">{{ props.node.name }}</p>
          <p class="text-[11px] opacity-70 truncate">{{ counterText }}</p>
        </div>

        <button
          type="button"
          class="inline-flex h-9 w-9 items-center justify-center rounded-full active:bg-white/10 shrink-0"
          aria-label="更多操作"
          @click="emit('more', props.node)"
        >
          <MoreHorizontal :size="20" />
        </button>
        </header>
        </Transition>

        <!-- 主预览体 -->
      <MobileImagePreviewer
        v-if="previewKind === 'image'"
        :node="props.node"
        :version-id="props.versionId"
        :has-prev="hasPrevImage"
        :has-next="hasNextImage"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @prev="goPrevImage"
        @next="goNextImage"
      />

      <MobileMarkdownPreviewer
        v-else-if="previewKind === 'markdown'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <MobileTextCodePreviewer
        v-else-if="previewKind === 'code' || previewKind === 'text'"
        :node="props.node"
        :version-id="props.versionId"
        :is-code="previewKind === 'code'"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <MobilePdfPreviewer
        v-else-if="previewKind === 'pdf'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
      />

      <MobileOfficePreviewer
        v-else-if="previewKind === 'word' || previewKind === 'ppt'"
        :node="props.node"
        :version-id="props.versionId"
        :slide-mode="previewKind === 'ppt'"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <MobileExcelPreviewer
        v-else-if="previewKind === 'excel'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <MobileVideoPreviewer
        v-else-if="previewKind === 'video'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <MobileAudioPreviewer
        v-else-if="previewKind === 'audio'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
      />

      <MobileArchivePreviewer
        v-else-if="previewKind === 'archive'"
        :node="props.node"
        :version-id="props.versionId"
        @toggle-chrome="chromeVisible = !chromeVisible"
      />

      <MobileUnsupportedPreviewer
        v-else
        :node="props.node"
        :downloading="downloading"
        @toggle-chrome="chromeVisible = !chromeVisible"
        @download="handleDownload"
      />

      <!-- 底部高频操作栏 -->
      <footer
        v-show="chromeVisible"
        class="z-20 border-t px-2 py-2 safe-bottom shrink-0 grid grid-cols-4 gap-1"
        :class="
          previewKind === 'image'
            ? 'absolute inset-x-0 bottom-0 bg-black/75 border-white/10 text-white backdrop-blur'
            : isDarkBackdrop ? 'bg-black border-white/10 text-white' : 'bg-card/95 border-border text-foreground backdrop-blur'
        "
      >
        <button
          type="button"
          class="flex flex-col items-center gap-0.5 rounded-xl py-1 active:opacity-70"
          :disabled="downloading"
          @click="handleDownload"
        >
          <Download :size="18" />
          <span class="text-[11px]">下载</span>
        </button>

        <button
          type="button"
          class="flex flex-col items-center gap-0.5 rounded-xl py-1 active:opacity-70"
          @click="emit('share', props.node)"
        >
          <Share2 :size="18" />
          <span class="text-[11px]">分享</span>
        </button>

        <button
          type="button"
          class="flex flex-col items-center gap-0.5 rounded-xl py-1 active:opacity-70"
          @click="emit('toggle-favorite', props.node)"
        >
          <Star :size="18" :class="props.favorite ? 'text-amber-500 fill-amber-500' : ''" />
          <span class="text-[11px]">{{ props.favorite ? '已收藏' : '收藏' }}</span>
        </button>

        <button
          type="button"
          class="tap-press flex flex-col items-center gap-0.5 rounded-xl py-1 active:opacity-70"
          @click="emit('detail', props.node)"
        >
          <Info :size="18" />
          <span class="text-[11px]">详情</span>
        </button>
      </footer>
    </div>
    </Transition>
  </Teleport>
</template>
