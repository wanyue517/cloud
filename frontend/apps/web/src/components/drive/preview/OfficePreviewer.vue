<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AlertCircle, Download, Loader2, RefreshCw } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type DriveNode, type PreviewAsset, type Version } from '@/api/drive'
import { getExtension } from '@/lib/preview-registry'
import PdfPreviewer from './PdfPreviewer.vue'

const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ download: [] }>()

const asset = ref<PreviewAsset | null>(null)
const error = ref('')
const retrying = ref(false)
let pollTimer: number | null = null
let seq = 0

const isPresentation = computed(() => {
  const ext = getExtension(props.node.name)
  return ext === 'ppt' || ext === 'pptx' || ext === 'odp'
})

function clearPoll() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function pollStatus(currentSeq: number) {
  try {
    const res = await drive.previewAsset(props.node, 'OFFICE_PDF', props.version?.id)
    if (currentSeq !== seq) return
    asset.value = res
    error.value = ''
    if (res.status === 'PENDING' || res.status === 'PROCESSING') {
      pollTimer = window.setTimeout(() => { void pollStatus(currentSeq) }, 2000)
    }
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '获取 Office 转换状态失败'
  }
}

async function retryConvert() {
  retrying.value = true
  error.value = ''
  clearPoll()
  const currentSeq = ++seq
  try {
    asset.value = await drive.retryPreviewAsset(props.node, 'OFFICE_PDF', props.version?.id)
    if (asset.value.status === 'PENDING' || asset.value.status === 'PROCESSING') {
      pollTimer = window.setTimeout(() => { void pollStatus(currentSeq) }, 1500)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重试转换失败'
  } finally {
    retrying.value = false
  }
}

function loadDerivedPdf() {
  return drive.previewAssetContent(props.node, 'OFFICE_PDF', props.version?.id)
}

function start() {
  clearPoll()
  const currentSeq = ++seq
  asset.value = null
  error.value = ''
  void pollStatus(currentSeq)
}

watch(() => [props.node.id, props.version?.id], () => start())
onMounted(() => start())
onBeforeUnmount(() => { seq++; clearPoll() })
</script>

<template>
  <PdfPreviewer
    v-if="asset?.status === 'READY'"
    :node="node"
    :version="version"
    :custom-loader="loadDerivedPdf"
    :presentation-mode="isPresentation"
  />
  <div v-else class="flex h-full min-h-0 flex-1 items-center justify-center bg-muted/20 p-6">
    <div class="flex w-full max-w-md flex-col items-center gap-4 rounded-xl border border-border bg-card p-8 text-center shadow-sm">
      <template v-if="error || asset?.status === 'FAILED'">
        <AlertCircle :size="36" class="text-destructive" />
        <div class="space-y-1">
          <h3 class="text-base font-semibold">文档预览转换失败</h3>
          <p class="text-xs text-muted-foreground">{{ error || asset?.errorMessage || '转换服务暂不可用，请重试或下载原件。' }}</p>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" :disabled="retrying" @click="retryConvert">
            <RefreshCw :size="15" /> {{ retrying ? '正在重试…' : '重新转换' }}
          </Button>
          <Button size="sm" @click="emit('download')">
            <Download :size="15" /> 下载原件
          </Button>
        </div>
      </template>
      <template v-else>
        <Loader2 :size="36" class="animate-spin text-primary" />
        <div class="space-y-1">
          <h3 class="text-base font-semibold">{{ isPresentation ? '正在生成演示文稿预览…' : '正在生成文档分页预览…' }}</h3>
          <p class="text-xs text-muted-foreground">
            当前状态：{{ asset?.status === 'PROCESSING' ? '正在转换中' : '排队等待转换' }}（第 {{ asset?.attemptCount ?? 1 }} 次尝试）
          </p>
        </div>
      </template>
    </div>
  </div>
</template>
