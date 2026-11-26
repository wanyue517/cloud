<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { KeyRound, Mail, RefreshCw, Save, ShieldCheck } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { request } from '@repo/session/request'

interface OAuthProviderItem {
  providerCode: string
  displayName: string
  enabled: boolean
  clientId: string
  hasSecret: boolean
  authorizeUrl: string
  tokenUrl: string
  draftSecret: string
}

interface AuthSettingsPayload {
  allowRegister: boolean
  requireEmailVerify: boolean
  allowPasswordLogin: boolean
  smtpHost: string
  smtpPort: number
  smtpUsername: string
  smtpFrom: string
  hasSmtpPassword: boolean
  oauthProviders: OAuthProviderItem[]
}

const form = reactive({
  allowRegister: true,
  requireEmailVerify: true,
  allowPasswordLogin: true,
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpFrom: '',
  smtpPassword: '',
  hasSmtpPassword: false,
})

const providers = ref<OAuthProviderItem[]>([])
const loading = ref(false)
const savingAuth = ref(false)
const savingProvider = ref('')
const error = ref('')
const notice = ref('')

function applyResponse(res: AuthSettingsPayload) {
  form.allowRegister = Boolean(res.allowRegister)
  form.requireEmailVerify = Boolean(res.requireEmailVerify)
  form.allowPasswordLogin = Boolean(res.allowPasswordLogin)
  form.smtpHost = res.smtpHost || ''
  form.smtpPort = Number(res.smtpPort) || 587
  form.smtpUsername = res.smtpUsername || ''
  form.smtpFrom = res.smtpFrom || ''
  form.smtpPassword = ''
  form.hasSmtpPassword = Boolean(res.hasSmtpPassword)
  providers.value = (res.oauthProviders || []).map(item => ({
    ...item,
    enabled: Boolean(item.enabled),
    hasSecret: Boolean(item.hasSecret),
    draftSecret: '',
  }))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await request.get<AuthSettingsPayload>('/v1/admin/settings/auth')
    applyResponse(res)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载认证与邮件配置失败'
  } finally {
    loading.value = false
  }
}

async function saveAuthAndSmtp() {
  if (savingAuth.value) return
  savingAuth.value = true
  error.value = ''
  notice.value = ''
  try {
    const res = await request.put<AuthSettingsPayload>('/v1/admin/settings/auth', {
      allowRegister: form.allowRegister,
      requireEmailVerify: form.requireEmailVerify,
      allowPasswordLogin: form.allowPasswordLogin,
      smtpHost: form.smtpHost.trim(),
      smtpPort: Number(form.smtpPort) || 587,
      smtpUsername: form.smtpUsername.trim(),
      smtpFrom: form.smtpFrom.trim(),
      smtpPassword: form.smtpPassword.trim() || null,
    })
    applyResponse(res)
    notice.value = '注册策略与 SMTP 配置已加密保存（密钥字段不会明文回显）。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存认证与 SMTP 配置失败'
  } finally {
    savingAuth.value = false
  }
}

async function saveProvider(item: OAuthProviderItem) {
  if (savingProvider.value) return
  savingProvider.value = item.providerCode
  error.value = ''
  notice.value = ''
  try {
    await request.put(`/v1/admin/oauth-providers/${encodeURIComponent(item.providerCode)}`, {
      enabled: Boolean(item.enabled),
      clientId: (item.clientId || '').trim(),
      clientSecret: (item.draftSecret || '').trim() || null,
      authorizeUrl: (item.authorizeUrl || '').trim(),
      tokenUrl: (item.tokenUrl || '').trim(),
    })
    item.draftSecret = ''
    await load()
    notice.value = `身份提供商「${item.displayName}」配置已更新。`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存 OAuth 提供商失败'
  } finally {
    savingProvider.value = ''
  }
}

onMounted(() => { void load() })
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-semibold">认证与邮件设置</h2>
        <p class="text-sm text-muted-foreground mt-1">管理注册登录策略、SMTP 邮件发信凭据与第三方 OAuth2 身份提供商；所有敏感密钥均采用 AES-256-GCM 密文落库且不明文回显。</p>
      </div>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw class="size-4" :class="{ 'animate-spin': loading }" /> 刷新
      </Button>
    </div>

    <p v-if="error" role="alert" class="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{{ notice }}</p>

    <form class="grid grid-cols-1 lg:grid-cols-2 gap-6" @submit.prevent="saveAuthAndSmtp">
      <section class="rounded-xl border border-border bg-card p-6 space-y-4">
        <div class="flex items-center gap-2 font-medium">
          <ShieldCheck class="size-4 text-primary" />
          <span>注册与登录策略</span>
        </div>

        <label class="flex items-center justify-between gap-3 py-2 border-b border-border/60 text-sm cursor-pointer">
          <div>
            <p class="font-medium">允许公开注册</p>
            <p class="text-xs text-muted-foreground">关闭后仅管理员可创建新账号</p>
          </div>
          <input v-model="form.allowRegister" type="checkbox" class="size-4 accent-primary" :disabled="loading || savingAuth" />
        </label>

        <label class="flex items-center justify-between gap-3 py-2 border-b border-border/60 text-sm cursor-pointer">
          <div>
            <p class="font-medium">注册强制邮箱验证</p>
            <p class="text-xs text-muted-foreground">要求新用户通过邮箱验证码确认归属</p>
          </div>
          <input v-model="form.requireEmailVerify" type="checkbox" class="size-4 accent-primary" :disabled="loading || savingAuth" />
        </label>

        <label class="flex items-center justify-between gap-3 py-2 text-sm cursor-pointer">
          <div>
            <p class="font-medium">允许账号密码登录</p>
            <p class="text-xs text-muted-foreground">关闭后仅允许邮箱验证码或第三方 OAuth 登录</p>
          </div>
          <input v-model="form.allowPasswordLogin" type="checkbox" class="size-4 accent-primary" :disabled="loading || savingAuth" />
        </label>
      </section>

      <section class="rounded-xl border border-border bg-card p-6 space-y-4">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-2 font-medium">
            <Mail class="size-4 text-primary" />
            <span>SMTP 邮件服务</span>
          </div>
          <span
            v-if="form.hasSmtpPassword"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-emerald-500/15 text-emerald-700 dark:text-emerald-300"
          >
            <KeyRound class="size-3" /> 已配置加密密钥
          </span>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <label class="sm:col-span-2 block space-y-1 text-sm">
            <span>SMTP 主机</span>
            <Input v-model="form.smtpHost" placeholder="smtp.example.com" :disabled="loading || savingAuth" />
          </label>
          <label class="block space-y-1 text-sm">
            <span>端口</span>
            <Input v-model.number="form.smtpPort" type="number" min="1" max="65535" :disabled="loading || savingAuth" />
          </label>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label class="block space-y-1 text-sm">
            <span>发信账号</span>
            <Input v-model="form.smtpUsername" placeholder="noreply@example.com" :disabled="loading || savingAuth" />
          </label>
          <label class="block space-y-1 text-sm">
            <span>发件人地址 (From)</span>
            <Input v-model="form.smtpFrom" placeholder="Cloudlane <noreply@example.com>" :disabled="loading || savingAuth" />
          </label>
        </div>

        <label class="block space-y-1 text-sm">
          <span>SMTP 密码 / 授权码</span>
          <Input
            v-model="form.smtpPassword"
            type="password"
            autocomplete="new-password"
            :placeholder="form.hasSmtpPassword ? '已配置密文，留空则保持原密码不变' : '请输入 SMTP 密码或授权码'"
            :disabled="loading || savingAuth"
          />
        </label>

        <div class="flex justify-end pt-1">
          <Button type="submit" :disabled="loading || savingAuth">
            <Save class="size-4" /> {{ savingAuth ? '正在保存…' : '保存认证与邮件设置' }}
          </Button>
        </div>
      </section>
    </form>

    <section class="rounded-xl border border-border bg-card p-6 space-y-4">
      <div>
        <h3 class="font-medium">第三方 OAuth2 身份提供商</h3>
        <p class="text-xs text-muted-foreground mt-1">预留企业及个人单点登录扩展能力，Client Secret 加密存储且仅在重新填写时覆盖。</p>
      </div>

      <div v-if="providers.length === 0 && !loading" class="text-sm text-muted-foreground py-6 text-center">
        暂无预置 OAuth 提供商
      </div>

      <div class="space-y-4">
        <div
          v-for="item in providers"
          :key="item.providerCode"
          class="rounded-lg border border-border p-4 space-y-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ item.displayName }}</span>
              <span class="px-2 py-0.5 rounded bg-muted text-xs font-mono">{{ item.providerCode }}</span>
              <span
                v-if="item.hasSecret"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs bg-emerald-500/15 text-emerald-700 dark:text-emerald-300"
              >
                <KeyRound class="size-3" /> 已配置 Secret
              </span>
            </div>
            <label class="inline-flex items-center gap-2 text-sm cursor-pointer">
              <input v-model="item.enabled" type="checkbox" class="size-4 accent-primary" />
              <span>启用</span>
            </label>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <label class="block space-y-1 text-xs">
              <span class="text-muted-foreground">Client ID</span>
              <Input v-model="item.clientId" placeholder="请输入 Client ID" />
            </label>
            <label class="block space-y-1 text-xs">
              <span class="text-muted-foreground">Client Secret</span>
              <Input
                v-model="item.draftSecret"
                type="password"
                autocomplete="new-password"
                :placeholder="item.hasSecret ? '已加密保存，留空保持不变' : '请输入 Client Secret'"
              />
            </label>
            <label class="block space-y-1 text-xs">
              <span class="text-muted-foreground">Authorize URL</span>
              <Input v-model="item.authorizeUrl" placeholder="输入 OAuth Authorize URL" />
            </label>
            <label class="block space-y-1 text-xs">
              <span class="text-muted-foreground">Token URL</span>
              <Input v-model="item.tokenUrl" placeholder="输入 OAuth Token URL" />
            </label>
          </div>

          <div class="flex justify-end">
            <Button size="sm" variant="outline" :disabled="savingProvider === item.providerCode" @click="saveProvider(item)">
              <Save class="size-3.5" /> {{ savingProvider === item.providerCode ? '保存中…' : `保存 ${item.displayName}` }}
            </Button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
