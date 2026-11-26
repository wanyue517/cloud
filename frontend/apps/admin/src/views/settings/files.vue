<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RefreshCw, Save, SlidersHorizontal } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { request } from '@repo/session/request'

interface FilePolicyPayload {
  maxFileVersions: number
  uploadChunkSizeMb: number
  uploadMaxFileMb: number
  uploadMaxConcurrentSessions: number
  uploadSessionTtlHours: number
  userDefaultQuotaGb: number
  maxDirectoryDepth: number
  trashRetentionDays: number
  blockedExtensions: string
}

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')

const form = reactive<FilePolicyPayload>({
  maxFileVersions: 20,
  uploadChunkSizeMb: 4,
  uploadMaxFileMb: 10240,
  uploadMaxConcurrentSessions: 20,
  uploadSessionTtlHours: 24,
  userDefaultQuotaGb: 100,
  maxDirectoryDepth: 256,
  trashRetentionDays: 30,
  blockedExtensions: '',
})

function apply(res: FilePolicyPayload) {
  form.maxFileVersions = Number(res.maxFileVersions) || 20
  form.uploadChunkSizeMb = Number(res.uploadChunkSizeMb) || 4
  form.uploadMaxFileMb = Number(res.uploadMaxFileMb) || 10240
  form.uploadMaxConcurrentSessions = Number(res.uploadMaxConcurrentSessions) || 20
  form.uploadSessionTtlHours = Number(res.uploadSessionTtlHours) || 24
  form.userDefaultQuotaGb = Number(res.userDefaultQuotaGb ?? 100)
  form.maxDirectoryDepth = Number(res.maxDirectoryDepth) || 256
  form.trashRetentionDays = Number(res.trashRetentionDays) || 30
  form.blockedExtensions = res.blockedExtensions || ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await request.get<FilePolicyPayload>('/v1/admin/settings/files')
    apply(res)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载文件策略失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.put<FilePolicyPayload>('/v1/admin/settings/files', {
      maxFileVersions: Number(form.maxFileVersions),
      uploadChunkSizeMb: Number(form.uploadChunkSizeMb),
      uploadMaxFileMb: Number(form.uploadMaxFileMb),
      uploadMaxConcurrentSessions: Number(form.uploadMaxConcurrentSessions),
      uploadSessionTtlHours: Number(form.uploadSessionTtlHours),
      userDefaultQuotaGb: Number(form.userDefaultQuotaGb),
      maxDirectoryDepth: Number(form.maxDirectoryDepth),
      trashRetentionDays: Number(form.trashRetentionDays),
      blockedExtensions: form.blockedExtensions.trim(),
    })
    apply(res)
    notice.value = '全站文件、上传与容量策略已保存并实时生效（无需重启服务）。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="flex items-center gap-2 text-xl font-semibold"><SlidersHorizontal :size="20" /> 文件、上传与容量策略</h1>
        <p class="text-xs text-muted-foreground mt-1">所有上传分片、单文件上限、用户配额、目录深度、回收站保留与危险后缀黑名单均可自由调整并实时生效。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
      </Button>
    </header>

    <p v-if="error" role="alert" class="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-md bg-emerald-500/10 px-3 py-2 text-xs text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <form v-if="!loading" class="grid grid-cols-1 lg:grid-cols-2 gap-6" @submit.prevent="save">
      <section class="space-y-4 rounded-xl border border-border bg-card p-5">
        <h2 class="font-medium text-sm border-b border-border pb-2">分片上传与并发控制</h2>
        <label class="block space-y-1 text-sm">
          <span>默认分片大小（MB，1–64）</span>
          <Input v-model.number="form.uploadChunkSizeMb" type="number" min="1" max="64" :disabled="saving" />
        </label>
        <label class="block space-y-1 text-sm">
          <span>单文件最大上传体积（MB，1–102400）</span>
          <Input v-model.number="form.uploadMaxFileMb" type="number" min="1" max="102400" :disabled="saving" />
        </label>
        <label class="block space-y-1 text-sm">
          <span>单用户最大同时上传任务数（1–100）</span>
          <Input v-model.number="form.uploadMaxConcurrentSessions" type="number" min="1" max="100" :disabled="saving" />
        </label>
        <label class="block space-y-1 text-sm">
          <span>断点续传会话有效期（小时，1–168）</span>
          <Input v-model.number="form.uploadSessionTtlHours" type="number" min="1" max="168" :disabled="saving" />
        </label>
      </section>

      <section class="space-y-4 rounded-xl border border-border bg-card p-5">
        <h2 class="font-medium text-sm border-b border-border pb-2">容量、版本与安全边界</h2>
        <label class="block space-y-1 text-sm">
          <span>单文件历史版本系统上限（1–200）</span>
          <Input v-model.number="form.maxFileVersions" type="number" min="1" max="200" :disabled="saving" />
        </label>
        <label class="block space-y-1 text-sm">
          <span>单用户默认存储配额（GB，0 表示不限）</span>
          <Input v-model.number="form.userDefaultQuotaGb" type="number" min="0" max="102400" :disabled="saving" />
        </label>
        <div class="grid grid-cols-2 gap-3">
          <label class="block space-y-1 text-sm">
            <span>目录树最大安全深度（16–512）</span>
            <Input v-model.number="form.maxDirectoryDepth" type="number" min="16" max="512" :disabled="saving" />
          </label>
          <label class="block space-y-1 text-sm">
            <span>回收站保留天数（1–365）</span>
            <Input v-model.number="form.trashRetentionDays" type="number" min="1" max="365" :disabled="saving" />
          </label>
        </div>
        <label class="block space-y-1 text-sm">
          <span>禁止上传的文件后缀黑名单（逗号分隔，留空不限制）</span>
          <Input v-model="form.blockedExtensions" placeholder="例如：exe,bat,cmd,sh" :disabled="saving" />
        </label>
      </section>

      <div class="lg:col-span-2 flex justify-end">
        <Button type="submit" :disabled="saving">
          <Save :size="14" /> {{ saving ? '正在保存…' : '保存全部策略' }}
        </Button>
      </div>
    </form>
  </div>
</template>
