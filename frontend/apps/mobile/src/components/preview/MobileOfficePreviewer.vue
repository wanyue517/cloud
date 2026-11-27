<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton, showToast } from 'vant'
import { Loader2, RefreshCw } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi, type DriveNode, type PreviewAsset } from '@/api/drive'
import MobilePdfPreviewer from './MobilePdfPreviewer.vue'

const props = withDefaults(
  defineProps<{
    node: DriveNode
    versionId?: string
    slideMode?: boolean
  }>(),
  {
    versionId: undefined,
    slideMode: false,
  },
)

const emit = defineEmits<{
  'toggle-chrome': []
  download: []
}>()

const asset = ref<PreviewAsset | null>(null)
const pdfBlob = ref<Blob | null>(null)
const loading = ref(true)
const retrying = ref(false)
const errorText = ref('')

let pollTimer: ReturnType<typeof setTimeout> | null = null
let runSeq = 0

function clearPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function pollStatus() {
  const seq = ++runSeq
  clearPoll()
  try {
    const res = await mobileDriveApi.previewAsset(
      props.node.spaceId,
      props.node.id,
      'OFFICE_PDF',
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    if (seq !== runSeq) return
    asset.value = res
    errorText.value = ''

    if (res.status === 'READY') {
      const blob = await mobileDriveApi.previewAssetContent(
        props.node.spaceId,
        props.node.id,
        'OFFICE_PDF',
        props.versionId ?? props.node.currentVersionId ?? undefined,
      )
      if (seq !== runSeq) return
      pdfBlob.value = blob
      loading.value = false
    } else if (res.status === 'PENDING' || res.status === 'PROCESSING') {
      loading.value = false
      pollTimer = setTimeout(() => {
        void pollStatus()
      }, 2000)
    } else {
      loading.value = false
    }
  } catch (err) {
    if (seq !== runSeq) return
    loading.value = false
    errorText.value = normalizeErrorMessage(err, '获取 Office 预览状态失败')
  }
}

async function handleRetry() {
  if (retrying.value) return
  retrying.value = true
  try {
    await mobileDriveApi.retryPreviewAsset(
      props.node.spaceId,
      props.node.id,
      'OFFICE_PDF',
      props.versionId ?? props.node.currentVersionId ?? undefined,
    )
    showToast('已重新提交转换任务')
    pdfBlob.value = null
    loading.value = true
    await pollStatus()
  } catch (err) {
    showToast(normalizeErrorMessage(err, '重试转换失败'))
  } finally {
    retrying.value = false
  }
}

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  () => {
    pdfBlob.value = null
    asset.value = null
    loading.value = true
    errorText.value = ''
    void pollStatus()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  runSeq += 1
  clearPoll()
})
</script>

<template>
  <MobilePdfPreviewer
    v-if="pdfBlob"
    :node="props.node"
    :version-id="props.versionId"
    :pdf-blob="pdfBlob"
    :slide-mode="props.slideMode"
    @toggle-chrome="emit('toggle-chrome')"
  />

  <div
    v-else
    class="flex-1 min-h-0 flex flex-col items-center justify-center px-6 text-center space-y-4 bg-background text-foreground"
    @click="emit('toggle-chrome')"
  >
    <div v-if="loading" class="flex flex-col items-center gap-2 text-muted-foreground">
      <Loader2 :size="28" class="animate-spin text-primary" />
      <p class="text-sm">正在检查文档转换状态…</p>
    </div>

    <div
      v-else-if="asset?.status === 'PENDING' || asset?.status === 'PROCESSING'"
      class="flex flex-col items-center gap-2.5 max-w-xs"
      @click.stop
    >
      <Loader2 :size="30" class="animate-spin text-primary" />
      <p class="text-sm font-medium text-foreground">
        {{ asset.status === 'PENDING' ? '文档排队转换中…' : '正在将文档渲染为 PDF…' }}
      </p>
      <p class="text-xs text-muted-foreground">转换完成后将自动打开，或先直接下载原文件</p>
      <VanButton size="small" round plain type="primary" @click="emit('download')">
        直接下载原文件
      </VanButton>
    </div>

    <div v-else class="rounded-2xl border border-border bg-card p-5 max-w-xs w-full space-y-3" @click.stop>
      <p class="text-sm font-semibold text-foreground">文档在线转换失败</p>
      <p class="text-xs text-muted-foreground break-words">
        {{ asset?.errorMessage || errorText || 'Office 转换服务（Gotenberg）未就绪或文档格式不受支持' }}
      </p>
      <div class="flex gap-2 pt-1">
        <VanButton
          block
          size="small"
          round
          plain
          type="primary"
          :loading="retrying"
          @click="handleRetry"
        >
          <span class="inline-flex items-center gap-1">
            <RefreshCw :size="13" />
            <span>重试转换</span>
          </span>
        </VanButton>
        <VanButton block size="small" round type="primary" @click="emit('download')">
          下载原文件
        </VanButton>
      </div>
    </div>
  </div>
</template>
