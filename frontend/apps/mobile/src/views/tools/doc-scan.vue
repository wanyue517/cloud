<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button as VanButton, showToast } from 'vant'
import { Camera, FileText, FolderOpen, Plus, Trash2 } from '@lucide/vue'
import { MobileShell } from '@/components/common'
import FolderPickerSheet from '@/components/drive/FolderPickerSheet.vue'
import { buildPdfFromJpegPages, type ScannedPageImage } from '@/native/share-and-scan'
import { enqueueFilesWithGuard } from '@/stores/uploads'

interface LocalScanPage {
  id: string
  previewUrl: string
  jpegBytes: Uint8Array
  width: number
  height: number
}

const router = useRouter()
const fileInputRef = ref<HTMLInputElement | null>(null)
const pages = ref<LocalScanPage[]>([])
const pdfName = ref(`SCAN_${new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')}.pdf`)
const targetSpaceId = ref('')
const targetParentId = ref('0')
const targetLabel = ref('点击选择上传目录')
const folderPickerOpen = ref(false)
const generating = ref(false)

async function convertImageToEnhancedJpeg(file: File): Promise<ScannedPageImage> {
  const bitmap = await createImageBitmap(file)
  const maxW = 1600
  const scale = bitmap.width > maxW ? maxW / bitmap.width : 1
  const width = Math.max(1, Math.round(bitmap.width * scale))
  const height = Math.max(1, Math.round(bitmap.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('无法初始化图像处理上下文')
  // 轻量增强文档对比度与去阴影
  ctx.filter = 'contrast(1.12) brightness(1.04)'
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close()

  const jpegBlob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      b => (b ? resolve(b) : reject(new Error('JPEG 编码失败'))),
      'image/jpeg',
      0.86,
    )
  })
  const jpegBytes = new Uint8Array(await jpegBlob.arrayBuffer())
  return { jpegBytes, width, height }
}

async function handlePagesPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  for (const f of files) {
    try {
      const processed = await convertImageToEnhancedJpeg(f)
      const previewUrl = URL.createObjectURL(new Blob([processed.jpegBytes.slice()], { type: 'image/jpeg' }))
      pages.value.push({
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        previewUrl,
        ...processed,
      })
    } catch {
      showToast(`无法处理图片「${f.name}」`)
    }
  }
}

function removePage(id: string) {
  const target = pages.value.find(p => p.id === id)
  if (target) URL.revokeObjectURL(target.previewUrl)
  pages.value = pages.value.filter(p => p.id !== id)
}

function handleFolderPicked(payload: { targetSpaceId: string; targetParentId: string; targetLabel: string }) {
  targetSpaceId.value = payload.targetSpaceId
  targetParentId.value = payload.targetParentId
  targetLabel.value = payload.targetLabel
}

async function handleBuildAndUploadPdf() {
  if (pages.value.length === 0) {
    showToast('请先拍摄或选择至少一页文档')
    return
  }
  if (!targetSpaceId.value) {
    folderPickerOpen.value = true
    showToast('请先选择保存目录')
    return
  }
  generating.value = true
  try {
    const pdfBytes = buildPdfFromJpegPages(pages.value)
    const finalName = pdfName.value.trim().toLowerCase().endsWith('.pdf')
      ? pdfName.value.trim()
      : `${pdfName.value.trim() || 'scan'}.pdf`
    const pdfFile = new File([pdfBytes.slice()], finalName, { type: 'application/pdf' })
    const ok = await enqueueFilesWithGuard(targetSpaceId.value, targetParentId.value, [pdfFile])
    if (ok) {
      showToast(`已合成 ${pages.value.length} 页 PDF 并开始上传`)
      router.replace('/tasks')
    }
  } catch (err) {
    showToast(err instanceof Error ? err.message : 'PDF 合成失败')
  } finally {
    generating.value = false
  }
}

onBeforeUnmount(() => {
  for (const p of pages.value) {
    URL.revokeObjectURL(p.previewUrl)
  }
})
</script>

<template>
  <MobileShell title="文档扫描转 PDF" fallback-path="/me" padded>
    <div class="space-y-4 pb-8">
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <div class="flex items-center gap-2">
          <FileText :size="18" class="text-primary" />
          <h2 class="text-sm font-semibold text-foreground">多页纸质文档合成 PDF</h2>
        </div>
        <p class="text-xs text-muted-foreground">
          连续拍摄或选择多张文档照片，自动对比度增强并合成为单个 PDF 直接上传到网盘。
        </p>

        <div class="space-y-1.5">
          <label class="block text-xs font-medium text-foreground">输出 PDF 文件名</label>
          <input
            v-model="pdfName"
            type="text"
            class="w-full h-10 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
          />
        </div>

        <button
          type="button"
          class="w-full flex items-center justify-between rounded-xl border border-border bg-background px-3 py-2.5 text-xs"
          @click="folderPickerOpen = true"
        >
          <span class="inline-flex items-center gap-1.5 text-foreground truncate">
            <FolderOpen :size="15" class="text-primary shrink-0" />
            <span class="truncate">{{ targetLabel }}</span>
          </span>
          <span class="text-primary font-medium shrink-0">选择目录</span>
        </button>
      </section>

      <!-- 已扫描页面网格 -->
      <div class="grid grid-cols-3 gap-2">
        <div
          v-for="(p, idx) in pages"
          :key="p.id"
          class="relative aspect-[3/4] rounded-xl border border-border bg-card overflow-hidden"
        >
          <img :src="p.previewUrl" :alt="`第 ${idx + 1} 页`" class="w-full h-full object-cover" />
          <span class="absolute bottom-1 left-1 rounded bg-black/65 px-1.5 py-0.5 text-[10px] text-white">
            P{{ idx + 1 }}
          </span>
          <button
            type="button"
            class="absolute top-1 right-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/65 text-white"
            aria-label="删除此页"
            @click="removePage(p.id)"
          >
            <Trash2 :size="12" />
          </button>
        </div>

        <button
          type="button"
          class="aspect-[3/4] rounded-xl border-2 border-dashed border-border bg-card/50 flex flex-col items-center justify-center gap-1.5 text-xs text-muted-foreground active:bg-accent"
          @click="fileInputRef?.click()"
        >
          <Camera v-if="pages.length === 0" :size="22" class="text-primary" />
          <Plus v-else :size="22" class="text-primary" />
          <span>{{ pages.length === 0 ? '拍摄/添加页面' : '继续添加' }}</span>
        </button>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept="image/*"
        capture="environment"
        multiple
        class="hidden"
        @change="handlePagesPicked"
      />

      <VanButton
        block
        round
        type="primary"
        :loading="generating"
        :disabled="pages.length === 0"
        class="!h-11"
        @click="handleBuildAndUploadPdf"
      >
        合成 PDF（共 {{ pages.length }} 页）并上传
      </VanButton>
    </div>

    <FolderPickerSheet
      v-model:open="folderPickerOpen"
      mode="COPY"
      :source-space-id="targetSpaceId || '0'"
      title="选择 PDF 保存目录"
      @confirm="handleFolderPicked"
    />
  </MobileShell>
</template>
