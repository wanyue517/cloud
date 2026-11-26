<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Cloud, Download, File, Folder, Lock } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { shareApi, type PublicNode, type PublicShareMeta } from '@/api/share'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))

const loading = ref(true)
const error = ref('')
const meta = ref<PublicShareMeta | null>(null)
const password = ref('')
const verifying = ref(false)
const nodes = ref<PublicNode[]>([])
const trail = ref<{ id: string; name: string }[]>([])

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

async function loadMeta() {
  loading.value = true
  error.value = ''
  try {
    meta.value = await shareApi.publicShareMeta(token.value, password.value || undefined)
    if (meta.value.unlocked && meta.value.nodeType === 'FOLDER') {
      trail.value = [{ id: meta.value.nodeId, name: meta.value.nodeName }]
      await loadFolder(meta.value.nodeId)
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
  const res = await shareApi.publicShareNodes(token.value, password.value || undefined, parentId)
  nodes.value = res.items
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
  const params = new URLSearchParams({ download: 'true' })
  if (password.value.trim()) params.set('password', password.value.trim())
  if (nodeId) params.set('nodeId', nodeId)
  window.open(`/api/v1/public/shares/${encodeURIComponent(token.value)}/content?${params.toString()}`, '_blank')
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
          <Input v-model="password" placeholder="输入提取码" :disabled="verifying" />
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
              <button type="button" class="rounded px-1.5 py-0.5 hover:bg-muted" @click="jumpTrail(idx)">{{ c.name }}</button>
            </template>
          </nav>
          <div v-if="!nodes.length" class="py-10 text-center text-sm text-muted-foreground">此目录为空</div>
          <div v-else class="divide-y divide-border rounded-lg border border-border">
            <div v-for="n in nodes" :key="n.id" class="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
              <button
                v-if="n.nodeType === 'FOLDER'"
                type="button"
                class="flex items-center gap-2 font-medium hover:text-primary"
                @click="openSubfolder(n)"
              >
                <Folder :size="16" class="text-amber-500" /> {{ n.name }}
              </button>
              <span v-else class="flex items-center gap-2">
                <File :size="16" class="text-muted-foreground" /> {{ n.name }}
              </span>
              <div class="flex items-center gap-3 text-xs text-muted-foreground">
                <span>{{ n.nodeType === 'FOLDER' ? '目录' : formatSize(n.size) }}</span>
                <Button v-if="n.nodeType === 'FILE' && meta.allowDownload" variant="outline" size="sm" @click="downloadFile(n.id)">
                  <Download :size="13" /> 下载
                </Button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="p-8 text-center text-sm text-muted-foreground">
          {{ meta.allowDownload ? '点击右上角按钮即可安全下载此文件。' : '分享者仅允许查看信息，已关闭下载权限。' }}
        </div>
      </div>
    </main>
  </div>
</template>
