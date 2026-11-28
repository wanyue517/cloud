<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Cloud, Database, HardDrive, KeyRound, Pencil, Plus, RefreshCw, Route, Save, Trash2, Wrench } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { confirm } from '@repo/ui/confirm-dialog'
import { Input } from '@repo/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@repo/ui/dialog'
import { Select, type SelectOption } from '@repo/ui/select'
import { toast } from '@repo/ui/toast'
import { request } from '@repo/session/request'
import UploadModeSettings from './components/UploadModeSettings.vue'
import PlaybackSettings from './components/PlaybackSettings.vue'

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
  uploadMode?: string
  playbackMode?: string
  playbackEndpoint?: string | null
  directThresholdBytes?: number
  directChunkSize?: number
  directConcurrency?: number
  directUrlTtl?: number
  directFallback?: boolean | number
  isDefault: boolean | number
  objectCount: number
  totalBytes: number
}

const profiles = ref<StorageProfileItem[]>([])
const loading = ref(false)
const busyId = ref('')
const error = ref('')
const notice = ref('')

// 按业务分离的存储路由策略
const routingUserFiles = ref('')
const routingSiteAssets = ref('')
const routingUserAvatars = ref('')
const routingSaving = ref(false)

const routingOptions = computed<SelectOption[]>(() => [
  { value: '', label: '跟随系统默认存储实例' },
  ...profiles.value.map(p => ({
    value: p.id,
    label: `${p.name} (${p.provider}${p.isDefault ? ' · 默认' : ''})`,
  })),
])

function profileUsageBadges(id: string): string[] {
  const tags: string[] = []
  if (routingUserFiles.value === id) tags.push('网盘文件')
  if (routingSiteAssets.value === id) tags.push('网站公共资源')
  if (routingUserAvatars.value === id) tags.push('用户头像')
  return tags
}

const modalOpen = ref(false)
const editingId = ref<string | null>(null)
const editingHasSecret = ref(false)
const saving = ref(false)
const formTesting = ref(false)
const formTestError = ref('')
const formTestNotice = ref('')

const formProvider = ref<'LOCAL' | 'S3' | 'DB'>('LOCAL')
const formName = ref('')
const formRootPath = ref('data/objects-extra')
const formEndpoint = ref('http://127.0.0.1:9000')
const formBucket = ref('cloudlane')
const formRegion = ref('us-east-1')
const formAccessKey = ref('')
const formSecretKey = ref('')
const formMaxFileMb = ref(4)
const defaultUploadSettings = () => ({ uploadMode: 'PROXY', thresholdMb: 100, partMb: 16, concurrency: 3, ttl: 900, fallback: 'no' })
const formUpload = ref(defaultUploadSettings())
const formPlayback = ref({ mode: 'AUTO', endpoint: '' })

const totalObjects = computed(() => profiles.value.reduce((acc, p) => acc + Number(p.objectCount || 0), 0))
const totalBytes = computed(() => profiles.value.reduce((acc, p) => acc + Number(p.totalBytes || 0), 0))
const defaultProfile = computed(() => profiles.value.find(p => Boolean(p.isDefault)))

const canTestForm = computed(() => {
  if (formProvider.value === 'LOCAL') return Boolean(formRootPath.value.trim())
  if (formProvider.value === 'S3') return Boolean(formEndpoint.value.trim() && formBucket.value.trim())
  return true
})

const canSubmit = computed(() => Boolean(formName.value.trim()) && canTestForm.value)

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
    const [list, routing] = await Promise.all([
      request.get<StorageProfileItem[]>('/v1/admin/storage-profiles'),
      request.get<{ userFilesProfileId: string; siteAssetsProfileId: string; userAvatarsProfileId: string }>('/v1/admin/storage-routing'),
    ])
    profiles.value = list
    routingUserFiles.value = routing.userFilesProfileId || ''
    routingSiteAssets.value = routing.siteAssetsProfileId || ''
    routingUserAvatars.value = routing.userAvatarsProfileId || ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载存储实例失败'
  } finally {
    loading.value = false
  }
}

async function saveStorageRouting() {
  if (routingSaving.value) return
  routingSaving.value = true
  error.value = ''
  try {
    const res = await request.put<{ userFilesProfileId: string; siteAssetsProfileId: string; userAvatarsProfileId: string }>('/v1/admin/storage-routing', {
      userFilesProfileId: routingUserFiles.value,
      siteAssetsProfileId: routingSiteAssets.value,
      userAvatarsProfileId: routingUserAvatars.value,
    })
    routingUserFiles.value = res.userFilesProfileId || ''
    routingSiteAssets.value = res.siteAssetsProfileId || ''
    routingUserAvatars.value = res.userAvatarsProfileId || ''
    toast.success('业务存储路由策略已保存并即时生效（历史对象按原记录正常读取）')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '保存业务存储路由失败'
    error.value = msg
    toast.error(msg)
  } finally {
    routingSaving.value = false
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
    toast.success(`「${item.name}」连通性正常（${target}，耗时 ${res.elapsedMs}ms）`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '连接测试失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

async function setDefault(item: StorageProfileItem) {
  const ok = await confirm({
    title: '切换全站默认存储',
    description: `确定将「${item.name} (${item.provider})」设为全站默认存储实例吗？切换后所有新上传文件与图片将立即写入该实例，请确保已通过连通性测试。`,
    confirmText: '确认切换',
  })
  if (!ok) return
  busyId.value = item.id
  error.value = ''
  notice.value = ''
  try {
    await request.put(`/v1/admin/storage-profiles/${item.id}/default`)
    await load()
    toast.success(`已将「${item.name}」设为默认存储实例`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '切换默认存储失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

async function deleteProfile(item: StorageProfileItem) {
  const ok = await confirm({
    title: '删除存储实例',
    description: `确定删除存储实例「${item.name}」吗？此操作不可恢复。`,
    confirmText: '确认删除',
    variant: 'destructive',
  })
  if (!ok) return
  busyId.value = item.id
  error.value = ''
  notice.value = ''
  try {
    await request.delete(`/v1/admin/storage-profiles/${item.id}`)
    toast.success(`已删除存储实例「${item.name}」`)
    await load()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '删除存储实例失败'
    error.value = msg
    toast.error(msg)
  } finally {
    busyId.value = ''
  }
}

function openCreateModal() {
  formPlayback.value = { mode: 'AUTO', endpoint: '' }
  formUpload.value = defaultUploadSettings()
  editingId.value = null
  editingHasSecret.value = false
  formName.value = ''
  formProvider.value = 'LOCAL'
  formRootPath.value = 'data/objects-extra'
  formEndpoint.value = 'http://127.0.0.1:9000'
  formBucket.value = 'cloudlane'
  formRegion.value = 'us-east-1'
  formAccessKey.value = ''
  formSecretKey.value = ''
  formMaxFileMb.value = 4
  formTestError.value = ''
  formTestNotice.value = ''
  modalOpen.value = true
}

function openEditModal(item: StorageProfileItem) {
  formPlayback.value = { mode: item.playbackMode || 'AUTO', endpoint: item.playbackEndpoint || '' }
  formUpload.value = {
    uploadMode: item.uploadMode || 'PROXY',
    thresholdMb: (item.directThresholdBytes ?? 104857600) / 1048576,
    partMb: (item.directChunkSize ?? 16777216) / 1048576,
    concurrency: item.directConcurrency ?? 3,
    ttl: item.directUrlTtl ?? 900,
    fallback: item.directFallback ? 'yes' : 'no',
  }
  editingId.value = item.id
  editingHasSecret.value = Boolean(item.hasSecret)
  formName.value = item.name || ''
  formProvider.value = (item.provider === 'S3' || item.provider === 'DB' ? item.provider : 'LOCAL')
  formRootPath.value = item.rootPath || ''
  formEndpoint.value = item.endpoint || ''
  formBucket.value = item.bucket || ''
  formRegion.value = item.region || 'us-east-1'
  formAccessKey.value = item.accessKey || ''
  formSecretKey.value = ''
  formMaxFileMb.value = item.maxFileBytes ? Math.max(1, Math.round(Number(item.maxFileBytes) / (1024 * 1024))) : 4
  formTestError.value = ''
  formTestNotice.value = ''
  modalOpen.value = true
}

async function testFormConfig() {
  if (formTesting.value || !canTestForm.value) return
  formTesting.value = true
  formTestError.value = ''
  formTestNotice.value = ''
  try {
    const res = await request.post<{
      ok: boolean
      path?: string
      endpoint?: string
      bucket?: string
      provider?: string
      statusCode?: number
      elapsedMs: number
    }>('/v1/admin/storage-profiles/test-config', {
      id: editingId.value ? Number(editingId.value) : null,
      provider: formProvider.value,
      rootPath: formProvider.value === 'LOCAL' ? formRootPath.value.trim() : null,
      endpoint: formProvider.value === 'S3' ? formEndpoint.value.trim() : null,
      bucket: formProvider.value === 'S3' ? formBucket.value.trim() : null,
      region: formProvider.value === 'S3' ? formRegion.value.trim() : null,
      accessKey: formProvider.value === 'S3' ? formAccessKey.value.trim() : null,
      secretKey: formProvider.value === 'S3' && formSecretKey.value.trim() ? formSecretKey.value.trim() : null,
    })
    const target = res.path || (res.endpoint ? `${res.endpoint}/${res.bucket || ''} (HTTP ${res.statusCode})` : res.provider || formProvider.value)
    formTestNotice.value = `连接测试通过（${target}，耗时 ${res.elapsedMs}ms）`
  } catch (e) {
    formTestError.value = e instanceof Error ? e.message : '连接测试失败'
  } finally {
    formTesting.value = false
  }
}

async function saveProfile() {
  if (saving.value || !canSubmit.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const payload = {
      name: formName.value.trim(),
      provider: formProvider.value,
      rootPath: formProvider.value === 'LOCAL' ? formRootPath.value.trim() : null,
      endpoint: formProvider.value === 'S3' ? formEndpoint.value.trim() : null,
      bucket: formProvider.value === 'S3' ? formBucket.value.trim() : null,
      region: formProvider.value === 'S3' ? formRegion.value.trim() : null,
      accessKey: formProvider.value === 'S3' ? formAccessKey.value.trim() : null,
      secretKey: formProvider.value === 'S3' && formSecretKey.value.trim() ? formSecretKey.value.trim() : null,
      maxFileBytes: formProvider.value === 'DB' ? Math.max(1, Number(formMaxFileMb.value) || 4) * 1024 * 1024 : null,
      uploadMode: formProvider.value === 'S3' ? formUpload.value.uploadMode : 'PROXY',
      directThresholdBytes: Math.round(Number(formUpload.value.thresholdMb) * 1048576),
      directChunkSize: Math.round(Number(formUpload.value.partMb) * 1048576),
      directConcurrency: Number(formUpload.value.concurrency),
      directUrlTtl: Number(formUpload.value.ttl),
      directFallback: formUpload.value.fallback === 'yes',
      playbackMode: formProvider.value === 'S3' ? formPlayback.value.mode : 'PROXY',
      playbackEndpoint: formProvider.value === 'S3' ? formPlayback.value.endpoint.trim() : null,
    }
    if (editingId.value) {
      await request.put(`/v1/admin/storage-profiles/${editingId.value}`, payload)
      notice.value = `已更新存储实例「${payload.name}」`
    } else {
      await request.post('/v1/admin/storage-profiles', payload)
      notice.value = `已新增存储实例「${payload.name}」`
    }
    modalOpen.value = false
    await load()
  } catch (e) {
    formTestError.value = e instanceof Error ? e.message : '保存存储实例失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">存储配置</h2>
        <p class="text-sm text-muted-foreground mt-1">统一管理本地目录（LOCAL）、S3 对象存储（S3）与数据库存储（DB），支持表单即时测试、在线编辑与热切换默认存储。</p>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
        </Button>
        <Button size="sm" @click="openCreateModal">
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

    <section class="rounded-xl border border-border bg-card p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-sm font-semibold flex items-center gap-2"><Route class="size-4 text-primary" /> 业务存储路由策略</h3>
          <p class="text-xs text-muted-foreground mt-0.5">为不同业务场景独立指定存储方案（例如网站公共资源用服务器本地存储、用户网盘文件用 S3 对象存储），未单独指定时跟随系统默认实例。</p>
        </div>
        <Button size="sm" :disabled="routingSaving || loading" @click="saveStorageRouting">
          <Save class="size-3.5" /> {{ routingSaving ? '保存中…' : '保存路由策略' }}
        </Button>
      </div>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="space-y-1.5">
          <label class="text-xs font-medium text-foreground">用户网盘文件</label>
          <Select v-model="routingUserFiles" :options="routingOptions" :disabled="routingSaving" aria-label="用户网盘文件存储方案" />
          <p class="text-[11px] text-muted-foreground">个人、共享、私密空间的所有文件上传</p>
        </div>
        <div class="space-y-1.5">
          <label class="text-xs font-medium text-foreground">网站公共资源</label>
          <Select v-model="routingSiteAssets" :options="routingOptions" :disabled="routingSaving" aria-label="网站公共资源存储方案" />
          <p class="text-[11px] text-muted-foreground">站点 Logo 等全站公开静态图片</p>
        </div>
        <div class="space-y-1.5">
          <label class="text-xs font-medium text-foreground">用户头像资源</label>
          <Select v-model="routingUserAvatars" :options="routingOptions" :disabled="routingSaving" aria-label="用户头像存储方案" />
          <p class="text-[11px] text-muted-foreground">用户个人中心上传的头像图片</p>
        </div>
      </div>
    </section>

    <section class="rounded-xl border border-border bg-card overflow-x-auto">
      <table class="w-full min-w-[760px] text-sm text-left">
        <thead class="text-muted-foreground bg-muted/50">
          <tr>
            <th class="px-4 py-3 font-medium whitespace-nowrap">实例名称</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">驱动类型</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">目标路径 / 端点</th>
            <th class="px-4 py-3 font-medium whitespace-nowrap">对象 / 占用</th>
            <th class="px-4 py-3 font-medium text-right whitespace-nowrap">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && profiles.length === 0" class="border-t border-border">
            <td colspan="5" class="px-4 py-10 text-center text-muted-foreground">暂无存储配置</td>
          </tr>
          <tr v-for="item in profiles" :key="item.id" class="border-t border-border hover:bg-muted/20">
            <td class="px-4 py-3 font-medium whitespace-nowrap">
              <div class="flex items-center gap-2">
                <Cloud v-if="item.provider === 'S3'" class="size-4 text-primary shrink-0" />
                <Database v-else-if="item.provider === 'DB'" class="size-4 text-primary shrink-0" />
                <HardDrive v-else class="size-4 text-primary shrink-0" />
                <span class="max-w-[160px] truncate" :title="item.name">{{ item.name }}</span>
                <span v-if="item.isDefault" class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-primary/15 text-primary whitespace-nowrap shrink-0">
                  <CheckCircle2 class="size-3 shrink-0" /> 默认
                </span>
                <span
                  v-for="badge in profileUsageBadges(item.id)"
                  :key="badge"
                  class="inline-flex items-center px-1.5 py-0.5 rounded bg-muted text-muted-foreground text-[11px] font-medium whitespace-nowrap shrink-0"
                >
                  {{ badge }}
                </span>
              </div>
            </td>
            <td class="px-4 py-3 whitespace-nowrap">
              <div class="inline-flex items-center gap-1.5 flex-nowrap">
                <span class="px-2 py-0.5 rounded bg-muted text-xs font-mono whitespace-nowrap shrink-0">{{ item.provider }}</span>
                <span
                  v-if="item.hasSecret"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 text-[11px] whitespace-nowrap shrink-0"
                >
                  <KeyRound class="size-3 shrink-0" /> 密文密钥
                </span>
              </div>
            </td>
            <td class="px-4 py-3 text-muted-foreground font-mono text-xs max-w-[260px]">
              <span class="block truncate" :title="describeLocation(item)">{{ describeLocation(item) }}</span>
            </td>
            <td class="px-4 py-3 text-muted-foreground whitespace-nowrap tabular-nums">
              {{ Number(item.objectCount || 0).toLocaleString() }} 个 · {{ formatBytes(Number(item.totalBytes || 0)) }}
            </td>
            <td class="px-4 py-3 text-right whitespace-nowrap">
              <div class="inline-flex items-center gap-1.5 flex-nowrap">
                <Button variant="outline" size="sm" :disabled="busyId === item.id" @click="openEditModal(item)">
                  <Pencil class="size-3.5" /> 编辑
                </Button>
                <Button variant="outline" size="sm" :disabled="busyId === item.id" @click="testConnection(item)">
                  <Wrench class="size-3.5" /> 测试
                </Button>
                <Button variant="outline" size="sm" :disabled="Boolean(item.isDefault) || busyId === item.id" @click="setDefault(item)">
                  {{ item.isDefault ? '默认' : '设为默认' }}
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  class="text-destructive"
                  :disabled="Boolean(item.isDefault) || Number(item.objectCount || 0) > 0 || busyId === item.id"
                  :title="item.isDefault ? '默认实例不可删除' : Number(item.objectCount || 0) > 0 ? '该实例仍有对象，不可删除' : '删除存储实例'"
                  @click="deleteProfile(item)"
                >
                  <Trash2 class="size-3.5" /> 删除
                </Button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <Dialog :open="modalOpen" @update:open="v => { if (!v && !saving && !formTesting) modalOpen = false }">
      <DialogContent class="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ editingId ? '编辑存储实例' : '新增存储实例' }}</DialogTitle>
          <DialogDescription>填写配置后可直接点击「测试连接」验证连通性；S3 SecretKey 经 AES-256-GCM 密文存储，留空则保持原密钥不变。</DialogDescription>
        </DialogHeader>
        <form class="space-y-4 mt-2" @submit.prevent="saveProfile">
          <div class="grid grid-cols-3 gap-2">
            <Button
              v-for="kind in (['LOCAL', 'S3', 'DB'] as const)"
              :key="kind"
              type="button"
              size="sm"
              :variant="formProvider === kind ? 'default' : 'outline'"
              :disabled="saving || formTesting || Boolean(editingId)"
              @click="formProvider = kind; formTestError = ''; formTestNotice = ''"
            >
              {{ kind === 'LOCAL' ? '本地目录' : kind === 'S3' ? 'S3 对象存储' : '数据库存储' }}
            </Button>
          </div>

          <label class="block space-y-1.5 text-sm">
            <span>实例名称</span>
            <Input v-model="formName" aria-label="实例名称" placeholder="例如：MinIO 归档桶 / 本地数据盘" :disabled="saving || formTesting" />
          </label>

          <label v-if="formProvider === 'LOCAL'" class="block space-y-1.5 text-sm">
            <span>目录路径</span>
            <Input v-model="formRootPath" aria-label="目录路径" placeholder="data/objects-extra" :disabled="saving || formTesting" />
          </label>

          <template v-else-if="formProvider === 'S3'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label class="block space-y-1 text-sm">
                <span>Endpoint</span>
                <Input v-model="formEndpoint" placeholder="https://s3.example.com 或 http://127.0.0.1:9000" :disabled="saving || formTesting" />
              </label>
              <label class="block space-y-1 text-sm">
                <span>Bucket</span>
                <Input v-model="formBucket" placeholder="cloudlane" :disabled="saving || formTesting" />
              </label>
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label class="block space-y-1 text-sm">
                <span>Region</span>
                <Input v-model="formRegion" placeholder="us-east-1" :disabled="saving || formTesting" />
              </label>
              <label class="block space-y-1 text-sm">
                <span>Access Key</span>
                <Input v-model="formAccessKey" placeholder="AKIA..." :disabled="saving || formTesting" />
              </label>
            </div>
            <label class="block space-y-1 text-sm">
              <span>Secret Key（加密落库）</span>
              <Input
                v-model="formSecretKey"
                type="password"
                autocomplete="new-password"
                :placeholder="editingId && editingHasSecret ? '已加密保存，留空保持不变' : '请输入 Secret Key'"
                :disabled="saving || formTesting"
              />
            </label>
          </template>

          <label v-else class="block space-y-1.5 text-sm">
            <span>单文件上限（MB）</span>
            <Input v-model.number="formMaxFileMb" type="number" min="1" max="32" :disabled="saving || formTesting" />
            <p class="text-xs text-muted-foreground">数据库存储适合轻量演示或小文件场景，超出上限将自动拒绝写入。</p>
          </label>

          <p v-if="formTestError" role="alert" class="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ formTestError }}</p>
          <p v-if="formTestNotice" role="status" class="rounded-md border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-700 dark:text-emerald-300">{{ formTestNotice }}</p>

          <UploadModeSettings v-if="formProvider === 'S3'" v-model="formUpload" :disabled="saving || formTesting" />
          <PlaybackSettings v-if="formProvider === 'S3'" v-model="formPlayback" :disabled="saving || formTesting" />

          <DialogFooter class="flex flex-wrap items-center justify-between gap-2 sm:justify-between">
            <Button variant="outline" type="button" :disabled="saving || formTesting || !canTestForm" @click="testFormConfig">
              <Wrench class="size-3.5" /> {{ formTesting ? '正在测试…' : '测试连接' }}
            </Button>
            <div class="flex items-center gap-2">
              <Button variant="outline" type="button" :disabled="saving || formTesting" @click="modalOpen = false">取消</Button>
              <Button type="submit" :disabled="saving || formTesting || !canSubmit">
                {{ saving ? '正在保存…' : editingId ? '保存修改' : '确认新增' }}
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
