<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Download, File, Folder, FolderOpen } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { drive, type ArchiveEntry, type ArchiveListing, type DriveNode, type Version } from '@/api/drive'
import { saveBlob } from '@/utils/request'

const props = defineProps<{ node: DriveNode; version?: Version }>()

const loading = ref(true)
const error = ref('')
const listing = ref<ArchiveListing | null>(null)
const currentPrefix = ref('')
const extractingPath = ref('')
let seq = 0

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(3, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

const breadcrumbs = computed(() => {
  if (!currentPrefix.value) return []
  const parts = currentPrefix.value.split('/').filter(Boolean)
  return parts.map((name, idx) => ({
    name,
    prefix: parts.slice(0, idx + 1).join('/'),
  }))
})

interface DisplayRow {
  path: string
  name: string
  directory: boolean
  size: number
  compressedSize: number
}

const visibleItems = computed<DisplayRow[]>(() => {
  const entries = listing.value?.entries ?? []
  const prefix = currentPrefix.value ? `${currentPrefix.value}/` : ''
  const dirs = new Map<string, DisplayRow>()
  const files: DisplayRow[] = []

  for (const entry of entries) {
    if (prefix && !entry.path.startsWith(prefix)) continue
    const rel = prefix ? entry.path.slice(prefix.length) : entry.path
    if (!rel) continue
    const slashIdx = rel.indexOf('/')
    if (slashIdx >= 0 || entry.directory) {
      const dirName = slashIdx >= 0 ? rel.slice(0, slashIdx) : rel
      const dirPath = prefix ? `${prefix}${dirName}` : dirName
      if (!dirs.has(dirPath)) {
        dirs.set(dirPath, { path: dirPath, name: dirName, directory: true, size: 0, compressedSize: 0 })
      }
    } else {
      files.push({
        path: entry.path,
        name: rel,
        directory: false,
        size: entry.size,
        compressedSize: entry.compressedSize,
      })
    }
  }
  return [
    ...Array.from(dirs.values()).sort((a, b) => a.name.localeCompare(b.name)),
    ...files.sort((a, b) => a.name.localeCompare(b.name)),
  ]
})

async function loadArchive() {
  const currentSeq = ++seq
  loading.value = true
  error.value = ''
  currentPrefix.value = ''
  listing.value = null
  try {
    const res = await drive.archiveEntries(props.node, props.version?.id)
    if (currentSeq !== seq) return
    listing.value = res
  } catch (e) {
    if (currentSeq !== seq) return
    error.value = e instanceof Error ? e.message : '解析压缩包目录失败'
  } finally {
    if (currentSeq === seq) loading.value = false
  }
}

async function extractFile(item: ArchiveEntry | DisplayRow) {
  extractingPath.value = item.path
  error.value = ''
  try {
    const blob = await drive.archiveExtract(props.node, item.path, props.version?.id)
    saveBlob(blob, item.name)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '提取文件失败'
  } finally {
    extractingPath.value = ''
  }
}

watch(() => [props.node.id, props.version?.id], () => { void loadArchive() })
onMounted(() => { void loadArchive() })
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 flex-col bg-background">
    <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2 text-xs">
      <nav class="flex flex-wrap items-center gap-1" aria-label="压缩包路径">
        <button type="button" class="rounded px-1.5 py-0.5 font-medium hover:bg-muted" @click="currentPrefix = ''">
          根目录
        </button>
        <template v-for="crumb in breadcrumbs" :key="crumb.prefix">
          <span class="text-muted-foreground">/</span>
          <button type="button" class="rounded px-1.5 py-0.5 hover:bg-muted" @click="currentPrefix = crumb.prefix">
            {{ crumb.name }}
          </button>
        </template>
      </nav>
      <span v-if="listing" class="text-muted-foreground">
        {{ listing.format }} · 共 {{ listing.totalEntries }} 项{{ listing.truncated ? '（已按安全上限截断展示）' : '' }}
      </span>
    </div>

    <div v-if="loading" role="status" class="flex flex-1 items-center justify-center text-sm text-muted-foreground">正在解析归档目录…</div>
    <div v-else-if="error" role="alert" class="flex flex-1 items-center justify-center text-sm text-destructive">{{ error }}</div>
    <div v-else-if="!visibleItems.length" class="flex flex-1 flex-col items-center justify-center gap-2 text-sm text-muted-foreground">
      <FolderOpen :size="32" />
      <span>当前目录为空</span>
    </div>
    <div v-else class="min-h-0 flex-1 overflow-y-auto">
      <table class="w-full border-collapse text-sm">
        <thead class="sticky top-0 bg-muted/60 text-xs text-muted-foreground">
          <tr>
            <th class="px-4 py-2 text-left font-medium">名称</th>
            <th class="w-28 px-4 py-2 text-right font-medium">原始大小</th>
            <th class="w-28 px-4 py-2 text-right font-medium">压缩大小</th>
            <th class="w-28 px-4 py-2 text-right font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-border">
          <tr v-for="item in visibleItems" :key="item.path" class="hover:bg-muted/30">
            <td class="px-4 py-2">
              <button
                v-if="item.directory"
                type="button"
                class="flex items-center gap-2 font-medium text-foreground hover:text-primary"
                @click="currentPrefix = item.path"
              >
                <Folder :size="16" class="text-amber-500" />
                <span>{{ item.name }}</span>
              </button>
              <span v-else class="flex items-center gap-2">
                <File :size="16" class="text-muted-foreground" />
                <span>{{ item.name }}</span>
              </span>
            </td>
            <td class="px-4 py-2 text-right text-xs tabular-nums text-muted-foreground">
              {{ item.directory ? '—' : formatSize(item.size) }}
            </td>
            <td class="px-4 py-2 text-right text-xs tabular-nums text-muted-foreground">
              {{ item.directory ? '—' : formatSize(item.compressedSize) }}
            </td>
            <td class="px-4 py-2 text-right">
              <Button
                v-if="!item.directory"
                variant="outline"
                size="sm"
                :disabled="extractingPath === item.path"
                @click="extractFile(item)"
              >
                <Download :size="13" /> {{ extractingPath === item.path ? '提取中' : '提取' }}
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
