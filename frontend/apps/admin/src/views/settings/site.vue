<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Globe, RefreshCw, Save } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { request } from '@repo/session/request'

interface SiteSettings {
  siteName: string
  siteLogoUrl: string
  siteAnnouncement: string
}

const siteForm = reactive<SiteSettings>({
  siteName: 'Cloudlane',
  siteLogoUrl: '',
  siteAnnouncement: '',
})

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await request.get<SiteSettings>('/v1/admin/settings/site')
    siteForm.siteName = res.siteName || 'Cloudlane'
    siteForm.siteLogoUrl = res.siteLogoUrl || ''
    siteForm.siteAnnouncement = res.siteAnnouncement || ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载站点配置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (saving.value || !siteForm.siteName.trim()) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.put<SiteSettings>('/v1/admin/settings/site', {
      siteName: siteForm.siteName.trim(),
      siteLogoUrl: siteForm.siteLogoUrl.trim(),
      siteAnnouncement: siteForm.siteAnnouncement.trim(),
    })
    siteForm.siteName = res.siteName
    siteForm.siteLogoUrl = res.siteLogoUrl
    siteForm.siteAnnouncement = res.siteAnnouncement
    notice.value = '站点设置已保存并立即生效（用户登录页与公开配置接口同步更新）。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存站点配置失败'
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
        <h2 class="text-xl font-semibold">站点设置</h2>
        <p class="text-sm text-muted-foreground mt-1">配置站点名称、品牌 Logo 与全局公告，公开信息通过 `/api/v1/public/site-config` 下发。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <form class="rounded-xl border border-border bg-card p-6 space-y-5 max-w-2xl" @submit.prevent="save">
      <div class="flex items-center gap-3 pb-4 border-b border-border">
        <div class="size-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center overflow-hidden shrink-0">
          <img v-if="siteForm.siteLogoUrl" :src="siteForm.siteLogoUrl" alt="站点 Logo 预览" class="size-full object-contain" />
          <Globe v-else class="size-5" />
        </div>
        <div class="min-w-0">
          <p class="font-medium truncate">{{ siteForm.siteName || 'Cloudlane' }}</p>
          <p class="text-xs text-muted-foreground">站点品牌实时预览</p>
        </div>
      </div>

      <label class="block space-y-1.5 text-sm">
        <span class="font-medium">站点名称</span>
        <Input v-model="siteForm.siteName" maxlength="80" placeholder="Cloudlane" :disabled="loading || saving" />
      </label>

      <label class="block space-y-1.5 text-sm">
        <span class="font-medium">品牌 Logo URL</span>
        <Input v-model="siteForm.siteLogoUrl" maxlength="512" placeholder="留空使用默认图标" :disabled="loading || saving" />
      </label>

      <label class="block space-y-1.5 text-sm">
        <span class="font-medium">站点公告</span>
        <textarea
          v-model="siteForm.siteAnnouncement"
          rows="4"
          maxlength="1000"
          :disabled="loading || saving"
          placeholder="可选，将在登录页或顶部通知条展示给所有用户"
          class="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        ></textarea>
      </label>

      <div class="flex items-center justify-end gap-2 pt-2">
        <Button type="submit" :disabled="loading || saving || !siteForm.siteName.trim()">
          <Save class="size-4" /> {{ saving ? '正在保存…' : '保存站点设置' }}
        </Button>
      </div>
    </form>
  </div>
</template>
