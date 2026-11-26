<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Cloud, Download, Eye, File, Folder, Lock } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Input } from '@repo/ui/input'
import { shareApi, type PublicNode, type PublicShareMeta } from '@/api/share'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))

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

function detectPreviewType(name: string, mime: string): 'image' | 'pdf' | 'video' | 'audio' | 'text' | 'none' {
  const m = (mime || '').toLowerCase()
  const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : ''
  if (m.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(ext)) return 'image'
  if (m === 'application/pdf' || ext === 'pdf') return 'pdf'
  if (m.startsWith('video/') || ['mp4', 'webm', 'mov'].includes(ext)) return 'video'
  if (m.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'flac', 'm4a'].includes(ext)) return 'audio'
  if (m.startsWith('text/') || ['txt', 'md', 'json', 'xml', 'yml', 'yaml', 'csv', 'log', 'js', 'ts', 'vue', 'java', 'py', 'css', 'html', 'sql'].includes(ext)) return 'text'
  return 'none'
}

const singleFilePreviewType = computed(() =>
  meta.value && meta.value.nodeType === 'FILE'
    ? detectPreviewType(meta.value.nodeName, meta.value.mimeType)
    : 'none',
)
const singleFileText = ref('')
const singleFileTextLoading = ref(false)

async function loadTextContent(url: string, targetRef: typeof singleFileText, loadingRef: typeof singleFileTextLoading) {
  loadingRef.value = true
  try {
    const resp = await fetch(url)
    if (!resp.ok) throw new Error('读取内容失败')
    const raw = await resp.text()
    targetRef.value = raw.length > 200_000 ? `${raw.slice(0, 200_000)}\n\n…（文件较大，仅展示前 200,000 字符）` : raw
  } catch {
    targetRef.value = '暂时无法读取文本预览内容'
  } finally {
    loadingRef.value = false
  }
}

async function openPreview(node: PublicNode) {
  previewTarget.value = { nodeId: node.id, name: node.name, mimeType: node.mimeType, size: node.size }
  previewText.value = ''
  if (detectPreviewType(node.name, node.mimeType) === 'text') {
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
    } else if (meta.value.unlocked && meta.value.nodeType === 'FILE' && singleFilePreviewType.value === 'text') {
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

onMounted(() => { void loadMeta() })
</script>

<template>
  <div class="flex min-h-screen flex-col bg-muted/30">
    <header class="flex items-center justify-between border-b border-border bg-background px-6 py-3">
      <div class="flex items-center gap-2 font-semibold">
        <Cloud :size="20" class="text-primary" />
        <span>Cloudlane 公开分享</span>
      </div>
      <a href="/auth/login" class="text-xs text-muted-foreground hover:text-foreground">登录工作台</a>
    </header>

    <main class="mx-auto flex w-full max-w-4xl flex-1 flex-col justify-center p-6">
      <div v-if="loading" class="text-center text-sm text-muted-foreground">正在加载分享内容…</div>

      <div v-else-if="!meta" class="mx-auto max-w-md rounded-xl border border-border bg-card p-8 text-center shadow-sm">
        <h1 class="text-lg font-semibold">无法访问该分享</h1>
        <p class="mt-2 text-sm text-muted-foreground">{{ error || '分享链接不存在、已过期或已被分享者停用。' }}</p>
      </div>

      <div v-else-if="meta.requiresPassword && !meta.unlocked" class="mx-auto w-full max-w-sm rounded-xl border border-border bg-card p-6 shadow-sm">
        <div class="mb-4 flex items-center gap-2">
          <Lock :size="18" class="text-primary" />
          <h1 class="text-base font-semibold">请输入提取码</h1>
        </div>
        <p class="mb-4 text-xs text-muted-foreground">「{{ meta.nodeName }}」由 {{ meta.ownerName || '用户' }} 分享</p>
        <form class="space-y-3" @submit.prevent="unlock">
          <Input v-model="password" placeholder="输入提取码" :disabled="verifying" autofocus />
          <p v-if="error" role="alert" class="text-xs text-destructive">{{ error }}</p>
          <Button type="submit" class="w-full" :disabled="verifying || !password.trim()">
            {{ verifying ? '正在校验…' : '提取文件' }}
          </Button>
        </form>
      </div>

      <div v-else class="w-full rounded-xl border border-border bg-card shadow-sm">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-border px-6 py-4">
          <div class="min-w-0">
            <h1 class="truncate text-lg font-semibold">{{ meta.nodeName }}</h1>
            <p class="text-xs text-muted-foreground">
              分享者：{{ meta.ownerName || '匿名用户' }}
              <template v-if="meta.nodeType === 'FILE'"> · {{ formatSize(meta.size) }}</template>
              <template v-if="meta.expiresAt"> · 有效期至 {{ new Date(meta.expiresAt).toLocaleString('zh-CN') }}</template>
            </p>
          </div>
          <Button v-if="meta.nodeType === 'FILE' && meta.allowDownload" @click="downloadFile()">
            <Download :size="15" /> 下载文件
          </Button>
        </div>

        <div v-if="meta.nodeType === 'FOLDER'" class="p-4">
          <nav class="mb-3 flex flex-wrap items-center gap-1 text-xs">
            <template v-for="(c, idx) in trail" :key="c.id">
              <span v-if="idx > 0" class="text-muted-foreground">/</span>
              <button type="button" class="cursor-pointer rounded px-1.5 py-0.5 hover:bg-muted" @click="jumpTrail(idx)">{{ c.name }}</button>
            </template>
          </nav>
          <p v-if="error" role="alert" class="mb-3 rounded bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
          <div v-if="folderLoading" class="py-10 text-center text-sm text-muted-foreground">正在加载目录…</div>
          <div v-else-if="!nodes.length" class="py-10 text-center text-sm text-muted-foreground">此目录为空</div>
          <div v-else class="divide-y divide-border rounded-lg border border-border">
            <div v-for="n in nodes" :key="n.id" class="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
              <button
                v-if="n.nodeType === 'FOLDER'"
                type="button"
                class="flex cursor-pointer items-center gap-2 font-medium hover:text-primary"
                @click="openSubfolder(n)"
              >
                <Folder :size="16" class="text-amber-500" /> {{ n.name }}
              </button>
              <span v-else class="flex min-w-0 items-center gap-2">
                <File :size="16" class="shrink-0 text-muted-foreground" />
                <span class="truncate">{{ n.name }}</span>
              </span>
              <div class="flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
                <span>{{ n.nodeType === 'FOLDER' ? '目录' : formatSize(n.size) }}</span>
                <Button v-if="n.nodeType === 'FILE' && detectPreviewType(n.name, n.mimeType) !== 'none'" variant="outline" size="sm" @click="openPreview(n)">
                  <Eye :size="13" /> 预览
                </Button>
                <Button v-if="n.nodeType === 'FILE' && meta.allowDownload" variant="outline" size="sm" @click="downloadFile(n.id)">
                  <Download :size="13" /> 下载
                </Button>
              </div>
            </div>
          </div>
        </div>

        <!-- 单文件在线预览区 -->
        <div v-else class="p-6">
          <div v-if="singleFilePreviewType === 'image'" class="flex justify-center">
            <img :src="contentUrl(undefined, false)" :alt="meta.nodeName" class="max-h-[65vh] rounded-lg border border-border object-contain" />
          </div>
          <div v-else-if="singleFilePreviewType === 'pdf'">
            <iframe :src="contentUrl(undefined, false)" class="h-[68vh] w-full rounded-lg border border-border" title="PDF 预览" />
          </div>
          <div v-else-if="singleFilePreviewType === 'video'" class="flex justify-center">
            <video :src="contentUrl(undefined, false)" controls :controlsList="meta.allowDownload ? undefined : 'nodownload'" class="max-h-[65vh] w-full rounded-lg bg-black" />
          </div>
          <div v-else-if="singleFilePreviewType === 'audio'" class="py-8">
            <audio :src="contentUrl(undefined, false)" controls :controlsList="meta.allowDownload ? undefined : 'nodownload'" class="mx-auto w-full max-w-md" />
          </div>
          <div v-else-if="singleFilePreviewType === 'text'" class="max-h-[65vh] overflow-auto rounded-lg border border-border bg-muted/30 p-4">
            <p v-if="singleFileTextLoading" class="text-center text-xs text-muted-foreground">正在加载文本预览…</p>
            <pre v-else class="whitespace-pre-wrap break-words font-mono text-xs leading-relaxed">{{ singleFileText }}</pre>
          </div>
          <div v-else class="py-6 text-center text-sm text-muted-foreground">
            {{ meta.allowDownload ? '该文件格式暂不支持在线预览，点击右上角按钮即可安全下载。' : '该文件格式暂不支持在线预览，且分享者已关闭下载权限。' }}
          </div>
        </div>
      </div>
    </main>

    <!-- 文件夹内文件预览弹窗 -->
    <Dialog :open="!!previewTarget" @update:open="v => { if (!v) previewTarget = null }">
      <DialogContent class="!max-w-4xl">
        <template v-if="previewTarget">
          <DialogHeader>
            <DialogTitle class="truncate">{{ previewTarget.name }}</DialogTitle>
            <DialogDescription>{{ formatSize(previewTarget.size) }} · 只读预览</DialogDescription>
          </DialogHeader>
          <div class="max-h-[70vh] overflow-auto">
            <img
              v-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'image'"
              :src="contentUrl(previewTarget.nodeId, false)"
              :alt="previewTarget.name"
              class="mx-auto max-h-[66vh] rounded-lg object-contain"
            />
            <iframe
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'pdf'"
              :src="contentUrl(previewTarget.nodeId, false)"
              class="h-[66vh] w-full rounded-lg border border-border"
              title="PDF 预览"
            />
            <video
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'video'"
              :src="contentUrl(previewTarget.nodeId, false)"
              controls
              :controlsList="meta?.allowDownload ? undefined : 'nodownload'"
              class="max-h-[66vh] w-full rounded-lg bg-black"
            />
            <audio
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'audio'"
              :src="contentUrl(previewTarget.nodeId, false)"
              controls
              :controlsList="meta?.allowDownload ? undefined : 'nodownload'"
              class="mx-auto my-6 w-full max-w-md"
            />
            <pre
              v-else-if="detectPreviewType(previewTarget.name, previewTarget.mimeType) === 'text'"
              class="whitespace-pre-wrap break-words rounded-lg bg-muted/40 p-4 font-mono text-xs leading-relaxed"
            >{{ previewTextLoading ? '正在加载…' : previewText }}</pre>
          </div>
        </template>
      </DialogContent>
    </Dialog>
  </div>
</template>
