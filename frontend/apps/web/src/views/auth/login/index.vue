<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { CheckCircle2, Cloud, Eye, EyeOff, QrCode, RefreshCw } from '@lucide/vue'
import QRCode from 'qrcode'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@repo/ui/form'
import { saveAccessToken } from '@/lib/auth'
import { request } from '@repo/session/request'
import { loginSchema } from './schema'
import { useLogin } from './useLogin'
import { useSiteConfig } from '@/composables/useSiteConfig'

import { safeReturnPath, isAdminPath } from '@repo/session/navigation'
const route = useRoute()
const router = useRouter()
const { siteConfig, loadSiteConfig } = useSiteConfig()
const form = useForm({
  validationSchema: toTypedSchema(loginSchema),
  initialValues: { email: '', password: '' },
})
const showPassword = ref(false)
const loginTab = ref<'password' | 'qr'>('password')

// M034 QR Login state
const qrId = ref('')
const qrDataUrl = ref('')
const qrStatus = ref<'IDLE' | 'PENDING' | 'SCANNED' | 'CONFIRMED' | 'EXPIRED'>('IDLE')
const qrError = ref('')
let qrPollTimer: ReturnType<typeof setInterval> | null = null

function stopQrPolling() {
  if (qrPollTimer) {
    clearInterval(qrPollTimer)
    qrPollTimer = null
  }
}

async function initQrLogin() {
  stopQrPolling()
  qrError.value = ''
  qrStatus.value = 'PENDING'
  qrDataUrl.value = ''
  qrId.value = ''
  try {
    const res = await request.post<{ qrId: string; expiresInSeconds: number; expiresAt: string }>(
      '/admin/auth/qr/create',
      undefined,
      { skipAuth: true, skipRefresh: true },
    )
    qrId.value = res.qrId
    const mobileConfirmUrl = `${window.location.origin}/m/qr-confirm?qrId=${encodeURIComponent(res.qrId)}`
    qrDataUrl.value = await QRCode.toDataURL(mobileConfirmUrl, { width: 200, margin: 2 })
    qrPollTimer = setInterval(async () => {
      if (!qrId.value || loginTab.value !== 'qr') return
      try {
        const st = await request.get<{
          status: 'PENDING' | 'SCANNED' | 'CONFIRMED' | 'EXPIRED'
          accessToken?: string | null
          expiresInSeconds?: number | null
        }>(`/admin/auth/qr/${encodeURIComponent(qrId.value)}/status`, undefined, {
          skipAuth: true,
          skipRefresh: true,
          silent: true,
        })
        qrStatus.value = st.status
        if (st.status === 'EXPIRED') {
          stopQrPolling()
        } else if (st.status === 'CONFIRMED' && st.accessToken) {
          stopQrPolling()
          saveAccessToken(st.accessToken)
          const destination = safeReturnPath(route.query.returnTo)
          if (isAdminPath(destination)) window.location.assign(destination)
          else await router.replace(destination)
        }
      } catch {
        // transient poll error; keep polling until expired
      }
    }, 2000)
  } catch (err) {
    qrError.value = err instanceof Error ? err.message : '生成二维码失败'
    qrStatus.value = 'EXPIRED'
  }
}

function switchTab(tab: 'password' | 'qr') {
  loginTab.value = tab
  if (tab === 'qr') {
    void initQrLogin()
  } else {
    stopQrPolling()
  }
}

const { submitting, error, login } = useLogin()
const onSubmit = form.handleSubmit(async (values) => {
  if (await login(values)) {
    form.resetForm()
    const destination = safeReturnPath(route.query.returnTo)
    if (isAdminPath(destination)) window.location.assign(destination)
    else await router.replace(destination)
  }
})
onMounted(() => { void loadSiteConfig() })
onBeforeUnmount(() => { stopQrPolling() })
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-background px-5 py-12 text-foreground">
    <div class="w-full max-w-[400px]">
      <header class="mb-9">
        <RouterLink to="/" class="mb-8 inline-flex items-center gap-2.5 text-sm font-semibold tracking-tight">
          <span class="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground overflow-hidden">
            <img v-if="siteConfig.siteLogoUrl" :src="siteConfig.siteLogoUrl" :alt="siteConfig.siteName" class="size-full object-contain" />
            <Cloud v-else :size="19" aria-hidden="true" />
          </span>
          {{ siteConfig.siteName }}
        </RouterLink>
        <h1 class="text-2xl font-semibold tracking-tight">欢迎回来</h1>
        <p class="mt-2 text-sm text-muted-foreground">使用邮箱和密码登录 {{ siteConfig.siteName }}</p>
        <p v-if="siteConfig.siteAnnouncement" class="mt-3 rounded-lg border border-primary/25 bg-primary/5 px-3 py-2 text-xs text-foreground">
          {{ siteConfig.siteAnnouncement }}
        </p>
      </header>

      <!-- 登录方式切换 Tab -->
      <div class="mb-6 grid grid-cols-2 gap-1 rounded-lg bg-muted p-1 text-sm">
        <button
          type="button"
          class="rounded-md py-1.5 font-medium transition-colors"
          :class="loginTab === 'password' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground hover:text-foreground'"
          @click="switchTab('password')"
        >
          密码登录
        </button>
        <button
          type="button"
          class="inline-flex items-center justify-center gap-1.5 rounded-md py-1.5 font-medium transition-colors"
          :class="loginTab === 'qr' ? 'bg-background text-foreground shadow-xs' : 'text-muted-foreground hover:text-foreground'"
          @click="switchTab('qr')"
        >
          <QrCode :size="15" />
          <span>扫码登录</span>
        </button>
      </div>

      <!-- 扫码登录面板 -->
      <div v-if="loginTab === 'qr'" class="flex flex-col items-center rounded-xl border border-border bg-card p-6 text-center space-y-4">
        <div class="relative flex size-48 items-center justify-center rounded-xl bg-white p-2 border border-border">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="手机扫码登录二维码" class="size-full" />
          <span v-else class="text-xs text-muted-foreground">正在生成二维码…</span>

          <div
            v-if="qrStatus === 'EXPIRED'"
            class="absolute inset-0 flex flex-col items-center justify-center gap-2 rounded-xl bg-background/90 backdrop-blur-xs"
          >
            <p class="text-xs font-medium text-foreground">二维码已过期</p>
            <Button size="sm" @click="initQrLogin">
              <RefreshCw :size="14" class="mr-1" />
              刷新二维码
            </Button>
          </div>
          <div
            v-else-if="qrStatus === 'SCANNED'"
            class="absolute inset-0 flex flex-col items-center justify-center gap-1.5 rounded-xl bg-background/90 backdrop-blur-xs"
          >
            <CheckCircle2 :size="28" class="text-primary" />
            <p class="text-xs font-medium text-foreground">已扫码，请在手机端确认</p>
          </div>
        </div>

        <p class="text-xs text-muted-foreground">
          打开手机端 Cloudlane「我的 → 扫一扫」扫描上方二维码
        </p>

        <div v-if="qrId && qrStatus !== 'EXPIRED'" class="w-full rounded-lg bg-muted/60 px-3 py-2 text-xs">
          <span class="text-muted-foreground">确认码：</span>
          <code class="font-mono font-semibold text-foreground select-all">{{ qrId }}</code>
        </div>

        <p v-if="qrError" class="text-xs text-destructive">{{ qrError }}</p>
      </div>

      <form v-else class="space-y-5" novalidate @submit="onSubmit">
        <fieldset :disabled="submitting || form.isSubmitting.value" class="contents">
          <FormField v-slot="{ componentField }" name="email">
            <FormItem>
              <FormLabel class="text-sm">邮箱地址</FormLabel>
              <FormControl>
                <Input
                  v-bind="componentField"
                  type="email"
                  autocomplete="username"
                  placeholder="name@example.com"
                  class="h-11 rounded-lg"
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="password">
            <FormItem>
              <FormLabel class="text-sm">密码</FormLabel>
              <div class="relative">
                <FormControl>
                  <Input
                    v-bind="componentField"
                    :type="showPassword ? 'text' : 'password'"
                    autocomplete="current-password"
                    placeholder="输入登录密码"
                    class="h-11 rounded-lg pr-11"
                  />
                </FormControl>
                <button
                  type="button"
                  class="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  :aria-pressed="showPassword"
                  @click="showPassword = !showPassword"
                >
                  <EyeOff v-if="showPassword" :size="17" aria-hidden="true" />
                  <Eye v-else :size="17" aria-hidden="true" />
                </button>
              </div>
              <FormMessage />
            </FormItem>
          </FormField>
        </fieldset>

        <p v-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>

        <Button
          type="submit"
          class="mt-2 h-11 w-full rounded-lg text-sm font-medium"
          :disabled="submitting || form.isSubmitting.value"
          :aria-busy="submitting"
        >
          {{ submitting ? '登录中…' : '登录' }}
        </Button>
      </form>

      <div v-if="siteConfig.oauthProviders.length" class="mt-5 space-y-2.5">
        <div class="relative flex items-center justify-center text-xs text-muted-foreground">
          <span class="bg-background px-2">或使用第三方账号登录</span>
        </div>
        <div class="grid grid-cols-1 gap-2">
          <Button
            v-for="provider in siteConfig.oauthProviders"
            :key="provider.providerCode"
            as-child
            variant="outline"
            class="h-10 w-full rounded-lg text-sm"
          >
            <a :href="`/api/v1/auth/oauth/${encodeURIComponent(provider.providerCode)}/authorize`">
              使用 {{ provider.displayName }} 登录
            </a>
          </Button>
        </div>
      </div>

      <div class="mt-7 flex items-center justify-between text-sm text-muted-foreground">
        <RouterLink to="/auth/forgot-password" class="hover:text-foreground hover:underline underline-offset-4">
          忘记密码？
        </RouterLink>
        <span v-if="siteConfig.allowRegister">
          还没有账号？
          <RouterLink to="/auth/register" class="font-medium text-primary hover:underline underline-offset-4">
            立即注册
          </RouterLink>
        </span>
      </div>
    </div>
  </main>
</template>
