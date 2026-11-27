<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Button as VanButton } from 'vant'
import { Eye, EyeOff, Lock, Mail } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { saveAccessToken, saveRefreshToken } from '@repo/session/auth'
import { safeMobileReturnPath } from '@repo/session/navigation'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { loginWithEmail } from '@/api/auth'
import { siteConfig } from '@/composables/useSiteConfig'
import { mobileCapabilities } from '@/native'
import { stripMobileBase } from '@/router/guard'
import { loginSchema } from './schemas'
import AuthLayout from './AuthLayout.vue'

const route = useRoute()
const router = useRouter()

const form = reactive({
  email: '',
  password: '',
})
const fieldErrors = reactive({
  email: '',
  password: '',
})
const showPassword = ref(false)
const submitting = ref(false)
const submitError = ref('')

onMounted(() => {
  if (typeof route.query.email === 'string' && route.query.email.trim()) {
    form.email = route.query.email.trim()
  }
})

function validate(): boolean {
  fieldErrors.email = ''
  fieldErrors.password = ''
  const parsed = loginSchema.safeParse(form)
  if (parsed.success) return true
  for (const issue of parsed.error.issues) {
    const key = issue.path[0]
    if (key === 'email' && !fieldErrors.email) fieldErrors.email = issue.message
    if (key === 'password' && !fieldErrors.password) fieldErrors.password = issue.message
  }
  return false
}

async function handleSubmit() {
  if (submitting.value) return
  submitError.value = ''
  if (!validate()) return

  submitting.value = true
  try {
    const res = await loginWithEmail({ email: form.email, password: form.password })
    if (!res?.accessToken) {
      throw new Error('登录响应缺少访问令牌，请稍后重试')
    }
    saveAccessToken(res.accessToken)
    if (res.refreshToken) {
      saveRefreshToken(res.refreshToken)
    }
    toast.success('登录成功')
    const rawRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/m/home'
    await router.replace(stripMobileBase(safeMobileReturnPath(rawRedirect)))
  } catch (err) {
    submitError.value = normalizeErrorMessage(err, '登录失败，请检查邮箱和密码')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="欢迎回来" subtitle="登录你的个人云端网盘">
    <form class="space-y-4" novalidate @submit.prevent="handleSubmit">
      <div
        v-if="submitError"
        role="alert"
        class="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-xs text-destructive"
      >
        {{ submitError }}
      </div>

      <div class="space-y-1.5">
        <label for="login-email" class="block text-xs font-medium text-foreground">邮箱地址</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.email ? 'border-destructive' : 'border-input'"
        >
          <Mail :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="login-email"
            v-model="form.email"
            type="email"
            inputmode="email"
            autocomplete="email"
            autocapitalize="off"
            placeholder="name@example.com"
            class="flex-1 min-w-0 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            @input="fieldErrors.email = ''"
          />
        </div>
        <p v-if="fieldErrors.email" class="text-xs text-destructive">{{ fieldErrors.email }}</p>
      </div>

      <div class="space-y-1.5">
        <div class="flex items-center justify-between">
          <label for="login-password" class="block text-xs font-medium text-foreground">登录密码</label>
          <RouterLink to="/auth/forgot-password" class="text-xs text-primary">忘记密码？</RouterLink>
        </div>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.password ? 'border-destructive' : 'border-input'"
        >
          <Lock :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="login-password"
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入 8–32 位密码"
            class="flex-1 min-w-0 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            @input="fieldErrors.password = ''"
          />
          <button
            type="button"
            class="p-1 text-muted-foreground"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" :size="16" />
            <Eye v-else :size="16" />
          </button>
        </div>
        <p v-if="fieldErrors.password" class="text-xs text-destructive">{{ fieldErrors.password }}</p>
      </div>

      <VanButton
        type="primary"
        native-type="submit"
        block
        round
        :loading="submitting"
        loading-text="正在登录…"
        class="!h-11 !text-sm !font-medium !mt-5"
      >
        登录
      </VanButton>
    </form>

    <template #footer>
      <div class="space-y-2">
        <div>
          <template v-if="siteConfig.allowRegister">
            还没有账号？
            <RouterLink to="/auth/register" class="font-medium text-primary">立即注册</RouterLink>
          </template>
          <span v-else>当前站点仅限受邀用户登录</span>
        </div>
        <div v-if="mobileCapabilities.customServer">
          <RouterLink to="/settings/server" class="text-xs text-muted-foreground underline underline-offset-4">
            配置自托管服务器地址
          </RouterLink>
        </div>
      </div>
    </template>
  </AuthLayout>
</template>
