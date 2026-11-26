<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Cloud, Eye, EyeOff } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { request } from '@repo/session/request'
import { useSiteConfig } from '@/composables/useSiteConfig'

const { siteConfig, loadSiteConfig } = useSiteConfig()

const email = ref('')
const code = ref('')
const newPassword = ref('')
const showPassword = ref(false)
const sending = ref(false)
const submitting = ref(false)
const done = ref(false)
const countdown = ref(0)
const notice = ref('')
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null

function startCooldown() {
  countdown.value = 60
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function sendCode() {
  if (sending.value || countdown.value > 0) return
  const normalized = email.value.trim()
  if (!normalized || !normalized.includes('@')) {
    error.value = '请输入有效的邮箱地址'
    return
  }
  sending.value = true
  error.value = ''
  notice.value = ''
  try {
    await request.post('/admin/auth/password/send-code', null, { params: { email: normalized } })
    notice.value = '如果该邮箱已注册，我们已发送 6 位重置验证码，请注意查收。'
    startCooldown()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送验证码失败'
  } finally {
    sending.value = false
  }
}

async function submitReset() {
  if (submitting.value) return
  error.value = ''
  notice.value = ''
  if (!email.value.trim() || code.value.trim().length !== 6 || newPassword.value.length < 8) {
    error.value = '请填写完整邮箱、6 位验证码以及 8–32 位新密码'
    return
  }
  submitting.value = true
  try {
    await request.post('/admin/auth/password/reset', {
      email: email.value.trim(),
      code: code.value.trim(),
      newPassword: newPassword.value,
    })
    done.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重置密码失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => { void loadSiteConfig() })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-background px-5 py-12 text-foreground">
    <div class="w-full max-w-[400px]">
      <header class="mb-8">
        <RouterLink to="/" class="mb-7 inline-flex items-center gap-2.5 text-sm font-semibold tracking-tight">
          <span class="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground overflow-hidden">
            <img v-if="siteConfig.siteLogoUrl" :src="siteConfig.siteLogoUrl" :alt="siteConfig.siteName" class="size-full object-contain" />
            <Cloud v-else :size="19" aria-hidden="true" />
          </span>
          {{ siteConfig.siteName }}
        </RouterLink>
        <h1 class="text-2xl font-semibold tracking-tight">重置密码</h1>
        <p class="mt-2 text-sm text-muted-foreground">通过已验证邮箱获取 6 位验证码并设置新密码，重置后旧登录会话将自动失效。</p>
      </header>

      <section v-if="done" role="status" class="space-y-4 rounded-xl border border-border bg-card p-5">
        <p class="text-sm font-medium">密码已重置成功！</p>
        <p class="text-xs text-muted-foreground">请使用新密码重新登录。</p>
        <RouterLink to="/auth/login" class="inline-block text-sm font-medium text-primary hover:underline">
          返回登录
        </RouterLink>
      </section>

      <form v-else class="space-y-4" novalidate @submit.prevent="submitReset">
        <label class="block space-y-1.5 text-sm">
          <span>邮箱地址</span>
          <Input v-model="email" type="email" autocomplete="email" placeholder="name@example.com" class="h-11 rounded-lg" />
        </label>

        <label class="block space-y-1.5 text-sm">
          <span>邮箱验证码</span>
          <div class="flex gap-2.5">
            <Input v-model="code" type="text" inputmode="numeric" maxlength="6" placeholder="输入6位验证码" class="h-11 flex-1 rounded-lg" />
            <Button type="button" variant="outline" class="h-11 w-[112px] shrink-0 rounded-lg text-sm" :disabled="sending || countdown > 0" @click="sendCode">
              {{ sending ? '发送中…' : countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </Button>
          </div>
        </label>

        <label class="block space-y-1.5 text-sm">
          <span>新密码</span>
          <div class="relative">
            <Input
              v-model="newPassword"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="new-password"
              maxlength="32"
              placeholder="8–32位字符"
              class="h-11 rounded-lg pr-11"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-lg text-muted-foreground hover:text-foreground"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="17" />
              <Eye v-else :size="17" />
            </button>
          </div>
        </label>

        <p v-if="notice" role="status" class="text-xs text-muted-foreground">{{ notice }}</p>
        <p v-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>

        <Button type="submit" class="mt-2 h-11 w-full rounded-lg text-sm font-medium" :disabled="submitting">
          {{ submitting ? '正在重置…' : '确认重置密码' }}
        </Button>
      </form>

      <p class="mt-7 text-center text-sm text-muted-foreground">
        想起密码了？
        <RouterLink to="/auth/login" class="font-medium text-primary hover:underline underline-offset-4">返回登录</RouterLink>
      </p>
    </div>
  </main>
</template>
