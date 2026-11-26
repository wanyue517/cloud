<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Cloud, Database, HardDrive, KeyRound, Plus, RefreshCw, Wrench } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { request } from '@repo/session/request'

interface StorageProfileItem {
  id: string
  name: string
  provider: 'LOCAL' | 'S3' | 'DB' | string
  rootPath: string
  endpoint?: string | null
  bucket?: string | null
  region?: string | null
  accessKey?: string | null
  hasSecret?: boolean | number
  maxFileBytes?: number | null
  isDefault: boolean | number
  objectCount: number
  totalBytes: number
}

const profiles = ref<StorageProfileItem[]>([])
const loading = ref(false)
const busyId = ref('')
const error = ref('')
const notice = ref('')

const createOpen = ref(false)
const createSaving = ref(false)
const newProvider = ref<'LOCAL' | 'S3' | 'DB'>('LOCAL')
const newName = ref('')
const newRootPath = ref('data/objects-extra')
const newEndpoint = ref('http://127.0.0.1:9000')
const newBucket = ref('cloudlane')
const newRegion = ref('us-east-1')
const newAccessKey = ref('')
const newSecretKey = ref('')
const newMaxFileMb = ref(4)

const totalObjects = computed(() => profiles.value.reduce((acc, p) => acc + Number(p.objectCount || 0), 0))
const totalBytes = computed(() => profiles.value.reduce((acc, p) => acc + Number(p.totalBytes || 0), 0))
const defaultProfile = computed(() => profiles.value.find(p => Boolean(p.isDefault)))

const canSubmit = computed(() => {
  if (!newName.value.trim()) return false
  if (newProvider.value === 'LOCAL') return Boolean(newRootPath.value.trim())
  if (newProvider.value === 'S3') return Boolean(newEndpoint.value.trim() && newBucket.value.trim())
  return true
})

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

function describeLocation(item: StorageProfileItem) {
  if (item.provider === 'S3') {
    const region = item.region ? ` (${item.region})` : ''
    return `${item.endpoint || ''} / ${item.bucket || ''}${region}`
  }
  if (item.provider === 'DB') {
    const limit = item.maxFileBytes ? ` · 上限 ${formatBytes(Number(item.maxFileBytes))}` : ''
    return `blob_db_content${limit}`
  }
  return item.rootPath
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    profiles.value = await request.get<StorageProfileItem[]>('/v1/admin/storage-profiles')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载存储实例失败'
  } finally {
    loading.value = false
  }
}

async function testConnection(item: StorageProfileItem) {
  busyId.value = item.id
  error.value = ''
  notice.value = ''
  try {
    const res = await request.post<{
      ok: boolean
      path?: string
      endpoint?: string
      bucket?: string
      provider?: string
      statusCode?: number
      elapsedMs: number
    }>(`/v1/admin/storage-profiles/${item.id}/test`)
    const target = res.path || (res.endpoint ? `${res.endpoint}/${res.bucket || ''} (HTTP ${res.statusCode})` : res.provider || item.provider)
    notice.value = `「${item.name}」连通性正常（${target}，耗时 ${res.elapsedMs}ms）`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '连接测试失败'
  } finally {
    busyId.value = ''
  }
}

async function setDefault(item: StorageProfileItem) {
  busyId.value = item.id
  error.value = ''
  notice.value = ''
  try {
    await request.put(`/v1/admin/storage-profiles/${item.id}/default`)
    await load()
    notice.value = `已将「${item.name}」设为默认存储实例（新写入对象将路由至此实例，历史对象按原记录正常读取）。`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '切换默认存储失败'
  } finally {
    busyId.value = ''
  }
}

function resetCreateForm() {
  newName.value = ''
  newProvider.value = 'LOCAL'
  newRootPath.value = 'data/objects-extra'
  newEndpoint.value = 'http://127.0.0.1:9000'
  newBucket.value = 'cloudlane'
  newRegion.value = 'us-east-1'
  newAccessKey.value = ''
  newSecretKey.value = ''
  newMaxFileMb.value = 4
}

async function createProfile() {
  if (createSaving.value || !canSubmit.value) return
  createSaving.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.post('/v1/admin/storage-profiles', {
      name: newName.value.trim(),
      provider: newProvider.value,
      rootPath: newProvider.value === 'LOCAL' ? newRootPath.value.trim() : null,
      endpoint: newProvider.value === 'S3' ? newEndpoint.value.trim() : null,
      bucket: newProvider.value === 'S3' ? newBucket.value.trim() : null,
      region: newProvider.value === 'S3' ? newRegion.value.trim() : null,
      accessKey: newProvider.value === 'S3' ? newAccessKey.value.trim() : null,
      secretKey: newProvider.value === 'S3' ? newSecretKey.value.trim() : null,
      maxFileBytes: newProvider.value === 'DB' ? Math.max(1, Number(newMaxFileMb.value) || 4) * 1024 * 1024 : null,
    })
    createOpen.value = false
    resetCreateForm()
    await load()
    notice.value = '已新增存储实例'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建存储实例失败'
  } finally {
    createSaving.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">存储配置</h2>
        <p class="text-sm text-muted-foreground mt-1">统一管理本地文件系统（LOCAL）、S3 兼容对象存储（S3）与数据库小文件存储（DB），支持在线探测与切换默认写入目标。</p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
        </Button>
        <Button size="sm" @click="createOpen = true">
          <Plus class="size-4" /> 新增存储实例
        </Button>
      </div>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">存储实例数</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ profiles.length }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">当前默认实例</p>
        <p class="text-xl font-semibold mt-3 truncate">{{ defaultProfile?.name || '—' }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">物理对象总数</p>
        <p class="text-3xl font-semibold mt-2 tabular-nums">{{ totalObjects.toLocaleString() }}</p>
      </section>
      <section class="rounded-xl border border-border bg-card p-5">
        <p class="text-sm text-muted-foreground">物理存储占用</p>
        <p class="text-2xl font-semibold mt-3 tabular-nums">{{ formatBytes(totalBytes) }}</p>
      </section>
    </div>

    <section class="rounded-xl border border-border bg-card overflow-x-auto">
      <table class="w-full text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-3 font-medium">实例名称</th>
            <th class="px-4 py-3 font-medium">驱动类型</th>
            <th class="px-4 py-3 font-medium">目标路径 / 端点</th>
            <th class="px-4 py-3 font-medium">对象 / 占用</th>
            <th class="px-4 py-3 font-medium text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && profiles.length === 0" class="border-t border-border">
            <td colspan="5" class="px-4 py-10 text-center text-muted-foreground">暂无存储配置</td>
          </tr>
          <tr v-for="item in profiles" :key="item.id" class="border-t border-border">
            <td class="px-4 py-3 font-medium">
              <div class="flex items-center gap-2">
                <Cloud v-if="item.provider === 'S3'" class="size-4 text-primary shrink-0" />
                <Database v-else-if="item.provider === 'DB'" class="size-4 text-primary shrink-0" />
                <HardDrive v-else class="size-4 text-primary shrink-0" />
                <span>{{ item.name }}</span>
                <span v-if="item.isDefault" class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-primary/15 text-primary">
                  <CheckCircle2 class="size-3" /> 默认
                </span>
              </div>
            </td>
            <td class="px-4 py-3">
              <div class="flex items-center gap-1.5">
                <span class="px-2 py-0.5 rounded bg-muted text-xs font-mono">{{ item.provider }}</span>
                <span
                  v-if="item.hasSecret"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 text-[11px]"
                >
                  <KeyRound class="size-3" /> 密文密钥
                </span>
              </div>
            </td>
            <td class="px-4 py-3 text-muted-foreground font-mono text-xs">{{ describeLocation(item) }}</td>
            <td class="px-4 py-3 text-muted-foreground">
              {{ Number(item.objectCount || 0).toLocaleString() }} 个 · {{ formatBytes(Number(item.totalBytes || 0)) }}
            </td>
            <td class="px-4 py-3 text-right">
              <div class="inline-flex items-center gap-2">
                <Button variant="outline" size="sm" :disabled="busyId === item.id" @click="testConnection(item)">
                  <Wrench class="size-3.5" /> 测试连接
                </Button>
                <Button variant="outline" size="sm" :disabled="Boolean(item.isDefault) || busyId === item.id" @click="setDefault(item)">
                  {{ item.isDefault ? '当前默认' : '设为默认' }}
                </Button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <Dialog :open="createOpen" @update:open="v => { if (!v && !createSaving) createOpen = false }">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新增存储实例</DialogTitle>
          <DialogDescription>选择驱动类型并填写目标配置；S3 SecretKey 将经 AES-256-GCM 加密后存储，不会明文回显。</DialogDescription>
        </DialogHeader>
        <form class="space-y-4 mt-2" @submit.prevent="createProfile">
          <div class="grid grid-cols-3 gap-2">
            <Button
              v-for="kind in (['LOCAL', 'S3', 'DB'] as const)"
              :key="kind"
              type="button"
              size="sm"
              :variant="newProvider === kind ? 'default' : 'outline'"
              @click="newProvider = kind"
            >
              {{ kind === 'LOCAL' ? '本地目录' : kind === 'S3' ? 'S3 对象存储' : '数据库存储' }}
            </Button>
          </div>

          <label class="block space-y-1.5 text-sm">
            <span>实例名称</span>
            <Input v-model="newName" aria-label="实例名称" placeholder="例如：MinIO 归档桶 / 本地数据盘" :disabled="createSaving" />
          </label>

          <label v-if="newProvider === 'LOCAL'" class="block space-y-1.5 text-sm">
            <span>目录路径</span>
            <Input v-model="newRootPath" aria-label="目录路径" placeholder="data/objects-extra" :disabled="createSaving" />
          </label>

          <template v-else-if="newProvider === 'S3'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label class="block space-y-1 text-sm">
                <span>Endpoint</span>
                <Input v-model="newEndpoint" placeholder="http://127.0.0.1:9000" :disabled="createSaving" />
              </label>
              <label class="block space-y-1 text-sm">
                <span>Bucket</span>
                <Input v-model="newBucket" placeholder="cloudlane" :disabled="createSaving" />
              </label>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label class="block space-y-1 text-sm">
                <span>Region</span>
                <Input v-model="newRegion" placeholder="us-east-1" :disabled="createSaving" />
              </label>
              <label class="block space-y-1 text-sm">
                <span>Access Key</span>
                <Input v-model="newAccessKey" placeholder="AKIA..." :disabled="createSaving" />
              </label>
            </div>
            <label class="block space-y-1 text-sm">
              <span>Secret Key（加密落库）</span>
              <Input v-model="newSecretKey" type="password" autocomplete="new-password" placeholder="请输入 Secret Key" :disabled="createSaving" />
            </label>
          </template>

          <label v-else class="block space-y-1.5 text-sm">
            <span>单文件上限（MB）</span>
            <Input v-model.number="newMaxFileMb" type="number" min="1" max="32" :disabled="createSaving" />
            <p class="text-xs text-muted-foreground">数据库存储适合轻量演示或小文件场景，超出上限将自动拒绝写入。</p>
          </label>

          <DialogFooter>
            <Button variant="outline" type="button" :disabled="createSaving" @click="createOpen = false">取消</Button>
            <Button type="submit" :disabled="createSaving || !canSubmit">
              {{ createSaving ? '正在创建…' : '确认新增' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
