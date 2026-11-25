<script setup lang="ts">
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@repo/ui/dialog'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Download, X } from '@lucide/vue'
import { drive, type DriveNode, type Version } from '@/api/drive'
import { saveBlob } from '@/utils/request'
const props = defineProps<{ node: DriveNode; version?: Version }>()
const emit = defineEmits<{ close: [] }>()
const loading = ref(true), error = ref(''), url = ref(''), text = ref('')
let disposed = false
const mime = props.version?.mimeType || props.node.mimeType
const size = props.version?.size ?? props.node.size
async function load() {
  try {
    if (size > 64 * 1024 * 1024) { error.value = '文件超过 64 MB，请下载后查看'; return }
    const blob = await drive.content(props.node, props.version?.id)
    if (disposed) return
    url.value = URL.createObjectURL(blob)
    if (mime.startsWith('text/')) text.value = (await blob.text()).slice(0, 1_000_000)
  } catch (e) { error.value = e instanceof Error ? e.message : '预览加载失败' }
  finally { loading.value = false }
}
async function download() {
  try { saveBlob(await drive.content(props.node, props.version?.id), props.node.name) }
  catch (e) { error.value = e instanceof Error ? e.message : '下载失败' }
}
function key(event: KeyboardEvent) { if (event.key === 'Escape') emit('close') }
onMounted(() => { void load(); document.addEventListener('keydown', key) })
onBeforeUnmount(() => { disposed = true; URL.revokeObjectURL(url.value); document.removeEventListener('keydown', key) })
</script>
<template>
  <Dialog :open="true" @update:open="value => { if (!value) emit('close') }">
    <DialogContent class="preview-dialog !max-w-[min(1100px,96vw)] !flex !gap-0 !p-0" :show-close-button="false">
      <DialogDescription class="sr-only">文件只读预览，可以下载原文件。</DialogDescription>
      <header><DialogTitle class="flex-1 truncate">{{ node.name }}<small v-if="version"> · 版本 {{ version.number }}</small></DialogTitle><Button variant="outline" @click="download"><Download :size="16" /> 下载</Button><Button variant="outline" autofocus aria-label="关闭预览" @click="emit('close')"><X :size="20" /></Button></header>
      <p v-if="loading" role="status">正在读取文件…</p>
      <p v-else-if="error" role="alert">{{ error }}</p>
      <img v-else-if="mime.startsWith('image/')" :src="url" :alt="node.name" />
      <iframe v-else-if="mime === 'application/pdf'" :src="url" title="PDF 预览" />
      <pre v-else-if="mime.startsWith('text/')">{{ text }}</pre>
      <audio v-else-if="mime.startsWith('audio/')" :src="url" controls />
      <video v-else-if="mime.startsWith('video/')" :src="url" controls />
      <p v-else>此文件暂不支持在线预览，请下载后查看。</p>
    </DialogContent>
  </Dialog>
</template>
