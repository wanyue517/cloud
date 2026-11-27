<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button as VanButton, showToast } from 'vant'
import { CheckCircle2, Globe, RotateCcw, Server } from '@lucide/vue'
import { setApiBaseUrl } from '@repo/session/request'
import { MobileShell } from '@/components/common'
import {
  buildApiBaseUrl,
  getDefaultDeviceName,
  getNativePlatform,
  getSavedServerOrigin,
  isNativeApp,
  normalizeServerOrigin,
  persistServerOriginLocal,
  syncServerOriginToNative,
} from '@/native'

const router = useRouter()

const serverInput = ref(getSavedServerOrigin())
const testing = ref(false)
const saving = ref(false)
const testResult = ref<{ ok: boolean; text: string } | null>(null)
const platformLabel = isNativeApp()
  ? `${getDefaultDeviceName()} (${getNativePlatform().toUpperCase()})`
  : 'Web H5 / PWA'

async function handleTestConnection() {
  testResult.value = null
  let normalized = ''
  try {
    normalized = normalizeServerOrigin(serverInput.value)
  } catch (err) {
    testResult.value = { ok: false, text: err instanceof Error ? err.message : '地址格式无效' }
    return
  }
  const baseUrl = buildApiBaseUrl(normalized)
  testing.value = true
  try {
    const resp = await fetch(`${baseUrl}/v1/public/site-config`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    })
    if (!resp.ok) {
      throw new Error(`服务器返回 HTTP ${resp.status}`)
    }
    const json = (await resp.json()) as { data?: { siteName?: string } }
    const siteName = json?.data?.siteName || 'Cloudlane'
    testResult.value = { ok: true, text: `连接成功！已识别服务：${siteName}` }
  } catch (err) {
    testResult.value = {
      ok: false,
      text: err instanceof Error ? `无法连接服务器：${err.message}` : '无法连接服务器，请检查域名/IP 与网络',
    }
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  saving.value = true
  testResult.value = null
  try {
    const normalized = persistServerOriginLocal(serverInput.value)
    await syncServerOriginToNative(normalized)
    setApiBaseUrl(buildApiBaseUrl(normalized))
    serverInput.value = normalized
    showToast(normalized ? `已切换到 ${normalized}` : '已恢复默认同源地址')
    router.back()
  } catch (err) {
    testResult.value = { ok: false, text: err instanceof Error ? err.message : '保存失败' }
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  serverInput.value = ''
  persistServerOriginLocal('')
  await syncServerOriginToNative('')
  setApiBaseUrl('/api')
  testResult.value = { ok: true, text: '已恢复默认相对地址（/api）' }
  showToast('已恢复默认服务器地址')
}
</script>

<template>
  <MobileShell title="服务器地址配置" fallback-path="/auth/login" padded>
    <div class="space-y-4 pb-6">
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <div class="flex items-center gap-2.5">
          <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
            <Server :size="20" />
          </span>
          <div class="min-w-0">
            <h2 class="text-sm font-semibold text-foreground">自托管 Cloudlane 服务器</h2>
            <p class="text-xs text-muted-foreground">当前运行环境：{{ platformLabel }}</p>
          </div>
        </div>

        <p class="text-xs text-muted-foreground leading-relaxed">
          输入您自托管部署的 Cloudlane 域名或局域网 IP（支持 HTTP 与 HTTPS）。Web H5 留空则默认连接当前同源服务。
        </p>

        <div class="space-y-1.5">
          <label class="block text-xs font-medium text-foreground">服务器地址（Origin）</label>
          <div class="relative">
            <Globe :size="15" class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              v-model="serverInput"
              type="url"
              inputmode="url"
              autocomplete="url"
              placeholder="例如：https://cloud.example.com 或 http://192.168.1.10:8000"
              class="w-full h-11 rounded-xl border border-input bg-background pl-9 pr-3 text-sm text-foreground outline-none focus:border-primary"
            />
          </div>
        </div>

        <div
          v-if="testResult"
          class="flex items-start gap-2 rounded-xl px-3 py-2 text-xs"
          :class="
            testResult.ok
              ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
              : 'bg-destructive/10 text-destructive'
          "
        >
          <CheckCircle2 v-if="testResult.ok" :size="15" class="shrink-0 mt-0.5" />
          <span>{{ testResult.text }}</span>
        </div>

        <div class="flex gap-2 pt-1">
          <VanButton
            block
            round
            plain
            type="primary"
            :loading="testing"
            class="!h-10 !text-sm"
            @click="handleTestConnection"
          >
            测试连接
          </VanButton>
          <VanButton
            block
            round
            type="primary"
            :loading="saving"
            class="!h-10 !text-sm"
            @click="handleSave"
          >
            保存配置
          </VanButton>
        </div>

        <button
          type="button"
          class="w-full inline-flex items-center justify-center gap-1.5 py-2 text-xs text-muted-foreground active:text-foreground"
          @click="handleReset"
        >
          <RotateCcw :size="13" />
          <span>恢复默认同源地址</span>
        </button>
      </section>
    </div>
  </MobileShell>
</template>
