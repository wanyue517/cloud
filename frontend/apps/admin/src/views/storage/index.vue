<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, Cloud, Database, HardDrive, KeyRound, Pencil, Plus, RefreshCw, Wrench } from '@lucide/vue'
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

function openCreateModal() {
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
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <Dialog :open="modalOpen" @update:open="v => { if (!v && !saving && !formTesting) modalOpen = false }">
      <DialogContent>
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
              :disabled="saving || formTesting"
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
                <Input v-model="formEndpoint" placeholder="输入 S3 端点地址" :disabled="saving || formTesting" />
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
