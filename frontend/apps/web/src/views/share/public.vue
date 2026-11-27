<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChevronRight,
  Clock,
  Cloud,
  Code2,
  Download,
  Eye,
  File,
  FileArchive,
  FileCode2,
  FileImage,
  FileMusic,
  FileText,
  FileVideo,
  Folder,
  Lock,
  ShieldAlert,
  User,
} from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import { shareApi, type PublicNode, type PublicShareMeta } from '@/api/share'
import { request } from '@/utils/request'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))

const siteName = ref('Cloudlane')
const siteLogoUrl = ref('')

const loading = ref(true)
const folderLoading = ref(false)
const error = ref('')
const meta = ref<PublicShareMeta | null>(null)
const password = ref('')
const verifying = ref(false)
const nodes = ref<PublicNode[]>([])
const trail = ref<{ id: string; name: string }[]>([])

interface PreviewTarget {
  nodeId?: string
  name: string
  mimeType: string
  size: number
}
const previewTarget = ref<PreviewTarget | null>(null)
const previewText = ref('')
const previewTextLoading = ref(false)

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

function contentUrl(nodeId?: string, download = false) {
  const params = new URLSearchParams({ download: String(download) })
  if (password.value.trim()) params.set('password', password.value.trim())
  if (nodeId) params.set('nodeId', nodeId)
  return `/api/v1/public/shares/${encodeURIComponent(token.value)}/content?${params.toString()}`
}

function detectPreviewType(name: string, mime: string): 'image' | 'pdf' | 'video' | 'audio' | 'html' | 'text' | 'none' {
  const m = (mime || '').toLowerCase()
  const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : ''
  if (m.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return 'image'
  if (m === 'application/pdf' || ext === 'pdf') return 'pdf'
  if (m.startsWith('video/') || ['mp4', 'webm', 'mov'].includes(ext)) return 'video'
  if (m.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'flac', 'm4a'].includes(ext)) return 'audio'
  if (m === 'text/html' || ext === 'html' || ext === 'htm' || ext === 'xhtml') return 'html'
  if (m.startsWith('text/') || ['txt', 'md', 'json', 'xml', 'yml', 'yaml', 'csv', 'log', 'js', 'ts', 'vue', 'java', 'py', 'css', 'sql'].includes(ext)) return 'text'
  return 'none'
}

const singleFilePreviewType = computed(() =>
  meta.value && meta.value.nodeType === 'FILE'
    ? detectPreviewType(meta.value.nodeName, meta.value.mimeType)
    : 'none',
)
const singleFileText = ref('')
const singleFileTextLoading = ref(false)
const singleHtmlMode = ref<'render' | 'source'>('render')
const modalHtmlMode = ref<'render' | 'source'>('render')

async function loadTextContent(url: string, targetRef: typeof singleFileText, loadingRef: typeof singleFileTextLoading) {
  loadingRef.value = true
  try {
    const resp = await fetch(url)
    if (!resp.ok) {
      let msg = `读取内容失败 (HTTP ${resp.status})`
      try {
        const body = await resp.json() as { msg?: string }
        if (body?.msg) msg = body.msg
      } catch { /* non-json error */ }
      throw new Error(msg)
    }
    const raw = await resp.text()
    targetRef.value = raw.length > 200_000 ? `${raw.slice(0, 200_000)}\n\n…（文件较大，仅展示前 200,000 字符）` : raw
  } catch (e) {
    targetRef.value = e instanceof Error ? e.message : '暂时无法读取文本预览内容'
  } finally {
    loadingRef.value = false
  }
}

async function openPreview(node: PublicNode) {
  previewTarget.value = { nodeId: node.id, name: node.name, mimeType: node.mimeType, size: node.size }
  previewText.value = ''
  modalHtmlMode.value = 'render'
  const ptype = detectPreviewType(node.name, node.mimeType)
  if (ptype === 'text' || ptype === 'html') {
    await loadTextContent(contentUrl(node.id, false), previewText, previewTextLoading)
  }
}

async function loadMeta() {
  loading.value = true
  error.value = ''
  try {
    meta.value = await shareApi.publicShareMeta(token.value, password.value || undefined)
    if (meta.value.unlocked && meta.value.nodeType === 'FOLDER') {
      trail.value = [{ id: meta.value.nodeId, name: meta.value.nodeName }]
      await loadFolder(meta.value.nodeId)
    } else if (meta.value.unlocked && meta.value.nodeType === 'FILE' && (singleFilePreviewType.value === 'text' || singleFilePreviewType.value === 'html')) {
      singleHtmlMode.value = 'render'
      void loadTextContent(contentUrl(undefined, false), singleFileText, singleFileTextLoading)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '分享链接无效或已失效'
    meta.value = null
  } finally {
    loading.value = false
  }
}

async function unlock() {
  if (!password.value.trim()) return
  verifying.value = true
  error.value = ''
  try {
    await shareApi.verifySharePassword(token.value, password.value.trim())
    await loadMeta()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '提取码不正确'
  } finally {
    verifying.value = false
  }
}

async function loadFolder(parentId: string) {
  folderLoading.value = true
  error.value = ''
  try {
    const res = await shareApi.publicShareNodes(token.value, password.value || undefined, parentId)
    nodes.value = res.items
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载目录失败'
  } finally {
    folderLoading.value = false
  }
}

async function openSubfolder(node: PublicNode) {
  trail.value = [...trail.value, { id: node.id, name: node.name }]
  await loadFolder(node.id)
}

async function jumpTrail(index: number) {
  const target = trail.value[index]
  if (!target) return
  trail.value = trail.value.slice(0, index + 1)
  await loadFolder(target.id)
}

function downloadFile(nodeId?: string) {
  window.open(contentUrl(nodeId, true), '_blank')
}

function fileIconComponent(name: string, mime: string) {
  const t = detectPreviewType(name, mime)
  if (t === 'image') return FileImage
  if (t === 'video') return FileVideo
  if (t === 'audio') return FileMusic
  if (t === 'html') return FileCode2
  const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : ''
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return FileArchive
  if (t === 'text' || t === 'pdf') return FileText
  return File
}

onMounted(() => {
  void request.get<{ siteName?: string; siteLogoUrl?: string }>('/v1/public/site-config', undefined, { silent: true })
    .then(res => {
      if (res?.siteName) siteName.value = res.siteName
      if (res?.siteLogoUrl) siteLogoUrl.value = res.siteLogoUrl
    })
    .catch(() => {})
  void loadMeta()
})
</script>

<template>
  <div class="flex min-h-screen flex-col bg-[var(--app-canvas)] text-foreground">
    <header class="flex h-14 shrink-0 items-center justify-between gap-3 border-b border-border bg-card px-4 sm:px-6">
      <div class="flex min-w-0 items-center gap-2.5 font-semibold">
        <span class="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-primary/10 text-primary">
          <img v-if="siteLogoUrl" :src="siteLogoUrl" :alt="siteName" class="size-full object-contain" />
          <Cloud v-else :size="18" />
        </span>
        <span class="truncate text-sm sm:text-base">{{ siteName }}</span>
        <span class="hidden sm:inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">公开分享</span>
      </div>
    </header>

    <main class="mx-auto flex w-full max-w-5xl flex-1 flex-col px-4 py-6 sm:px-6">
      <!-- 加载中 -->
      <div
        v-if="loading"
        class="my-auto flex flex-col items-center justify-center gap-3 py-20 text-sm text-muted-foreground"
      >
        <span class="size-8 animate-spin rounded-full border-2 border-primary/30 border-t-primary" />
        <span>正在加载分享内容…</span>
      </div>

      <!-- 失效或无权访问 -->
      <div
        v-else-if="!meta"
        class="my-auto mx-auto w-full max-w-md rounded-xl border border-border bg-card p-8 text-center shadow-xs"
      >
        <div class="mx-auto mb-4 flex size-12 items-center justify-center rounded-xl bg-destructive/10 text-destructive">
          <ShieldAlert :size="24" />
        </div>
        <h1 class="text-lg font-semibold">无法访问该分享</h1>
        <p class="mt-2 text-sm text-muted-foreground">{{ error || '分享链接不存在、已过期或已被分享者停用。' }}</p>
      </div>

      <!-- 提取码验证 -->
      <div
        v-else-if="meta.requiresPassword && !meta.unlocked"
        class="my-auto mx-auto w-full max-w-sm rounded-xl border border-border bg-card p-6 shadow-xs sm:p-7"
      >
        <div class="mb-5 flex items-center gap-3">
          <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Lock :size="19" />
          </div>
          <div class="min-w-0">
            <h1 class="text-base font-semibold">需要提取码</h1>
            <p class="truncate text-xs text-muted-foreground">「{{ meta.nodeName }}」· {{ meta.ownerName || '匿名用户' }}</p>
          </div>
        </div>
        <form class="space-y-3.5" @submit.prevent="unlock">
          <Input v-model="password" placeholder="请输入提取码" :disabled="verifying" autofocus />
          <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
          <Button type="submit" class="w-full" :disabled="verifying || !password.trim()">
            {{ verifying ? '正在校验…' : '提取并查看内容' }}
          </Button>
        </form>
      </div>

      <!-- 分享内容主卡片 -->
      <div v-else class="flex flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card shadow-xs">
        <!-- 头部元信息 -->
        <div class="flex flex-wrap items-center justify-between gap-4 border-b border-border/80 px-5 py-4 sm:px-6">
          <div class="flex min-w-0 items-center gap-3.5">
            <div
              class="flex size-11 shrink-0 items-center justify-center rounded-xl"
              :class="meta.nodeType === 'FOLDER'
                ? 'bg-amber-500/12 text-amber-500 ring-1 ring-inset ring-amber-500/20'
                : 'bg-primary/10 text-primary ring-1 ring-inset ring-primary/20'"
            >
              <Folder v-if="meta.nodeType === 'FOLDER'" :size="22" />
              <component :is="fileIconComponent(meta.nodeName, meta.mimeType)" v-else :size="22" />
            </div>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h1 class="truncate text-base font-semibold sm:text-lg">{{ meta.nodeName }}</h1>
                <span
                  class="inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
                  :class="meta.allowDownload ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'"
                >
                  {{ meta.allowDownload ? '可预览 / 下载' : '仅在线预览' }}
                </span>
              </div>
              <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                <span class="inline-flex items-center gap-1"><User :size="12" /> {{ meta.ownerName || '匿名用户' }}</span>
                <span v-if="meta.nodeType === 'FILE'" class="tabular-nums">{{ formatSize(meta.size) }}</span>
                <span v-if="meta.expiresAt" class="inline-flex items-center gap-1 tabular-nums">
                  <Clock :size="12" /> 有效期至 {{ new Date(meta.expiresAt).toLocaleString('zh-CN') }}
                </span>
              </div>
            </div>
          </div>

          <Button v-if="meta.nodeType === 'FILE' && meta.allowDownload" size="sm" @click="downloadFile()">
            <Download :size="15" /> 下载文件
          </Button>
        </div>

        <!-- 文件夹分享列表 -->
        <div v-if="meta.nodeType === 'FOLDER'" class="flex-1 p-4 sm:p-6">
          <nav class="mb-4 flex flex-wrap items-center gap-1 text-xs" aria-label="目录路径">
            <template v-for="(c, idx) in trail" :key="c.id">
              <ChevronRight v-if="idx > 0" :size="13" class="text-muted-foreground" />
              <button
                type="button"
                class="cursor-pointer rounded-md px-2 py-1 font-medium transition-colors hover:bg-muted"
                :class="idx === trail.length - 1 ? 'text-foreground' : 'text-muted-foreground'"
                @click="jumpTrail(idx)"
              >
                {{ c.name }}
              </button>
            </template>
          </nav>

          <p v-if="error" role="alert" class="mb-3 rounded-lg border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-xs text-destructive">{{ error }}</p>

          <div v-if="folderLoading" class="py-16 text-center text-sm text-muted-foreground">正在加载目录…</div>
          <div v-else-if="!nodes.length" class="rounded-xl border border-dashed border-border/80 bg-muted/25 py-16 text-center text-sm text-muted-foreground">
            此目录为空
          </div>
          <div v-else class="space-y-1">
            <div class="grid grid-cols-[minmax(0,1fr)_88px_auto] items-center gap-3 border-b border-border/75 px-3 pb-2.5 text-xs font-medium text-muted-foreground">
              <span>名称</span>
              <span class="text-right">大小</span>
              <span class="w-28 text-right">操作</span>
            </div>
            <div
              v-for="n in nodes"
              :key="n.id"
              class="group grid grid-cols-[minmax(0,1fr)_88px_auto] items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-muted/65"
            >
              <button
                v-if="n.nodeType === 'FOLDER'"
                type="button"
                class="flex min-w-0 cursor-pointer items-center gap-2.5 text-left font-medium hover:text-primary"
                @click="openSubfolder(n)"
              >
                <span class="flex size-8 shrink-0 items-center justify-center rounded-md bg-amber-500/12 text-amber-500 ring-1 ring-inset ring-amber-500/20">
                  <Folder :size="16" />
                </span>
                <span class="truncate">{{ n.name }}</span>
              </button>
              <div v-else class="flex min-w-0 items-center gap-2.5">
                <span class="flex size-8 shrink-0 items-center justify-center rounded-md bg-muted/75 text-muted-foreground ring-1 ring-inset ring-border/60">
                  <component :is="fileIconComponent(n.name, n.mimeType)" :size="16" />
                </span>
                <span class="truncate font-medium">{{ n.name }}</span>
              </div>

              <span class="text-right text-xs tabular-nums text-muted-foreground">
                {{ n.nodeType === 'FOLDER' ? '目录' : formatSize(n.size) }}
              </span>

              <div class="flex w-28 shrink-0 items-center justify-end gap-1.5">
                <Button
                  v-if="n.nodeType === 'FILE' && detectPreviewType(n.name, n.mimeType) !== 'none'"
                  variant="outline"
                  size="sm"
                  class="h-7 px-2 text-xs"
                  @click="openPreview(n)"
                >
                  <Eye :size="13" /> 预览
                </Button>
                <Button
                  v-if="n.nodeType === 'FILE' && meta.allowDownload"
                  variant="ghost"
                  size="icon-sm"
                  class="h-7 w-7"
                  title="下载文件"
                  aria-label="下载文件"
                  @click="downloadFile(n.id)"
                >
                  <Download :size="14" />
                </Button>
              </div>
            </div>
          </div>
        </div>

        <!-- 单文件在线预览区 -->
        <div v-else class="flex flex-1 flex-col p-4 sm:p-6">
          <div
            v-if="singleFilePreviewType === 'image'"
            class="flex flex-1 items-center justify-center rounded-xl border border-border/75 bg-muted/30 p-4"
          >
            <img :src="contentUrl(undefined, false)" :alt="meta.nodeName" class="max-h-[68vh] rounded-lg object-contain shadow-xs" />
          </div>

          <div v-else-if="singleFilePreviewType === 'pdf'" class="flex-1">
            <iframe :src="contentUrl(undefined, false)" class="h-[72vh] w-full rounded-xl border border-border/80 bg-white" title="PDF 预览" />
          </div>

          <div v-else-if="singleFilePreviewType === 'video'" class="flex flex-1 items-center justify-center rounded-xl bg-black/95 p-2">
            <video :src="contentUrl(undefined, false)" controls :controlsList="meta.allowDownload ? undefined : 'nodownload'" class="max-h-[70vh] w-full rounded-lg" />
          </div>

          <div v-else-if="singleFilePreviewType === 'audio'" class="my-auto flex flex-col items-center gap-4 rounded-xl border border-border/75 bg-muted/25 px-6 py-12">
            <div class="flex size-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <FileMusic :size="28" />
            </div>
            <p class="text-sm font-medium">{{ meta.nodeName }}</p>
            <audio :src="contentUrl(undefined, false)" controls :controlsList="meta.allowDownload ? undefined : 'nodownload'" class="w-full max-w-md" />
          </div>

          <div v-else-if="singleFilePreviewType === 'html'" class="flex flex-1 flex-col gap-3">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs text-muted-foreground">安全沙箱渲染 · 支持切换源码查看</span>
              <div class="inline-flex rounded-lg border border-border bg-muted/40 p-0.5" role="tablist" aria-label="HTML 预览模式">
                <Button type="button" size="sm" class="h-7 px-2.5 text-xs" :variant="singleHtmlMode === 'render' ? 'default' : 'ghost'" @click="singleHtmlMode = 'render'">
                  <Eye :size="13" /> 效果预览
                </Button>
                <Button type="button" size="sm" class="h-7 px-2.5 text-xs" :variant="singleHtmlMode === 'source' ? 'default' : 'ghost'" @click="singleHtmlMode = 'source'">
                  <Code2 :size="13" /> 源码模式
                </Button>
              </div>
            </div>
            <p v-if="singleFileTextLoading" class="py-12 text-center text-xs text-muted-foreground">正在加载 HTML 预览…</p>
            <iframe
              v-else-if="singleHtmlMode === 'render'"
              :srcdoc="singleFileText"
              sandbox="allow-scripts allow-forms allow-popups"
              referrerpolicy="no-referrer"
              title="HTML 渲染预览"
              class="h-[70vh] w-full rounded-xl border border-border/80 bg-white"
            />
            <pre v-else class="max-h-[68vh] overflow-auto rounded-xl border border-border/80 bg-muted/30 p-4 whitespace-pre-wrap break-words font-mono text-xs leading-6">{{ singleFileText }}</pre>
          </div>

          <div v-else-if="singleFilePreviewType === 'text'" class="max-h-[70vh] overflow-auto rounded-xl border border-border/80 bg-muted/30 p-4">
            <p v-if="singleFileTextLoading" class="py-8 text-center text-xs text-muted-foreground">正在加载文本预览…</p>
            <pre v-else class="whitespace-pre-wrap break-words font-mono text-xs leading-6">{{ singleFileText }}</pre>
          </div>

          <div v-else class="my-auto flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border/80 bg-muted/25 py-16 text-center">
            <div class="flex size-12 items-center justify-center rounded-xl bg-muted text-muted-foreground">
              <File :size="24" />
            </div>
            <p class="text-sm text-muted-foreground">
              {{ meta.allowDownload ? '该文件格式暂不支持在线预览，点击右上角按钮即可安全下载。' : '该文件格式暂不支持在线预览，且分享者已关闭下载权限。' }}
            </p>
          </div>
        </div>
      </div>
    </main>

    <!-- 文件夹内文件预览弹窗 -->
    <Dialog :open="!!previewTarget" @update:open="v => { if (!v) previewTarget = null }">
      <DialogContent class="!max-w-4xl">
        <template v-if="previewTarget">
          <DialogHeader>
            <DialogTitle class="flex items-center gap-2 truncate">
              <component :is="fileIconComponent(previewTarget.name, previewTarget.mimeType)" :size="18" class="shrink-0 text-primary" />
              <span class="truncate">{{ previewTarget.name }}</span>
            </DialogTitle>
            <DialogDescription>{{ formatSize(previewTarget.size) }} · 只读预览</DialogDescription>
          </DialogHeader>
          <div class="max-h-[72vh] overflow-auto">
            <div
              v-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'image'"
              class="flex justify-center rounded-xl border border-border/75 bg-muted/30 p-3"
            >
              <img
                :src="contentUrl(previewTarget.nodeId, false)"
                :alt="previewTarget.name"
                class="max-h-[66vh] rounded-lg object-contain"
              />
            </div>
            <iframe
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'pdf'"
              :src="contentUrl(previewTarget.nodeId, false)"
              class="h-[68vh] w-full rounded-xl border border-border/80 bg-white"
              title="PDF 预览"
            />
            <video
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'video'"
              :src="contentUrl(previewTarget.nodeId, false)"
              controls
              :controlsList="meta?.allowDownload ? undefined : 'nodownload'"
              class="max-h-[66vh] w-full rounded-xl bg-black"
            />
            <audio
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'audio'"
              :src="contentUrl(previewTarget.nodeId, false)"
              controls
              :controlsList="meta?.allowDownload ? undefined : 'nodownload'"
              class="mx-auto my-6 w-full max-w-md"
            />
            <div v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'html'" class="space-y-3">
              <div class="flex justify-end">
                <div class="inline-flex rounded-lg border border-border bg-muted/40 p-0.5" role="tablist" aria-label="HTML 预览模式">
                  <Button type="button" size="sm" class="h-7 px-2.5 text-xs" :variant="modalHtmlMode === 'render' ? 'default' : 'ghost'" @click="modalHtmlMode = 'render'">
                    <Eye :size="13" /> 效果预览
                  </Button>
                  <Button type="button" size="sm" class="h-7 px-2.5 text-xs" :variant="modalHtmlMode === 'source' ? 'default' : 'ghost'" @click="modalHtmlMode = 'source'">
                    <Code2 :size="13" /> 源码模式
                  </Button>
                </div>
              </div>
              <p v-if="previewTextLoading" class="py-8 text-center text-xs text-muted-foreground">正在加载…</p>
              <iframe
                v-else-if="modalHtmlMode === 'render'"
                :srcdoc="previewText"
                sandbox="allow-scripts allow-forms allow-popups"
                referrerpolicy="no-referrer"
                title="HTML 渲染预览"
                class="h-[64vh] w-full rounded-xl border border-border/80 bg-white"
              />
              <pre v-else class="whitespace-pre-wrap break-words rounded-xl border border-border/80 bg-muted/30 p-4 font-mono text-xs leading-6">{{ previewText }}</pre>
            </div>
            <pre
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'text'"
              class="whitespace-pre-wrap break-words rounded-xl border border-border/80 bg-muted/30 p-4 font-mono text-xs leading-6"
            >{{ previewTextLoading ? '正在加载…' : previewText }}</pre>
          </div>
        </template>
      </DialogContent>
    </Dialog>
  </div>
</template>
