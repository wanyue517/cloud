<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Button as VanButton } from 'vant'
import { CheckCircle2, Eye, EyeOff, KeyRound, Lock, Mail } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { resetPasswordWithEmail, sendPasswordResetCode } from '@/api/auth'
import { useCooldown } from '@/composables/useCooldown'
import { emailSchema, forgotPasswordSchema } from './schemas'
import AuthLayout from './AuthLayout.vue'

const cooldown = useCooldown(60)

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})
const fieldErrors = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})
const showPassword = ref(false)
const sendingCode = ref(false)
const submitting = ref(false)
const done = ref(false)
const submitError = ref('')
const notice = ref('')

async function handleSendCode() {
  if (sendingCode.value || cooldown.isCoolingDown.value) return
  fieldErrors.email = ''
  submitError.value = ''
  notice.value = ''

  const parsedEmail = emailSchema.safeParse(form.email)
  if (!parsedEmail.success) {
    fieldErrors.email = parsedEmail.error.issues[0]?.message || '请输入正确的邮箱地址'
    return
  }

  sendingCode.value = true
  try {
    await sendPasswordResetCode(parsedEmail.data)
    notice.value = '如果该邮箱已注册，6 位重置验证码已发送，请注意查收'
    cooldown.start(60)
  } catch (err) {
    submitError.value = normalizeErrorMessage(err, '发送验证码失败')
  } finally {
    sendingCode.value = false
  }
}

function validate(): boolean {
  fieldErrors.email = ''
  fieldErrors.code = ''
  fieldErrors.newPassword = ''
  fieldErrors.confirmPassword = ''
  const parsed = forgotPasswordSchema.safeParse(form)
  if (parsed.success) return true
  for (const issue of parsed.error.issues) {
    const key = issue.path[0]
    if (key === 'email' && !fieldErrors.email) fieldErrors.email = issue.message
    if (key === 'code' && !fieldErrors.code) fieldErrors.code = issue.message
    if (key === 'newPassword' && !fieldErrors.newPassword) fieldErrors.newPassword = issue.message
    if (key === 'confirmPassword' && !fieldErrors.confirmPassword) fieldErrors.confirmPassword = issue.message
  }
  return false
}

async function handleSubmit() {
  if (submitting.value) return
  submitError.value = ''
  if (!validate()) return

  submitting.value = true
  try {
    await resetPasswordWithEmail({
      email: form.email,
      code: form.code,
      newPassword: form.newPassword,
    })
    done.value = true
  } catch (err) {
    submitError.value = normalizeErrorMessage(err, '重置密码失败，请检查验证码')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="重置密码" subtitle="重置后所有旧设备会话将自动失效">
    <div v-if="done" role="status" class="space-y-4 py-3 text-center">
      <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <CheckCircle2 :size="26" />
      </div>
      <div>
        <p class="text-sm font-semibold text-foreground">密码已重置成功</p>
        <p class="mt-1 text-xs text-muted-foreground">请使用新密码重新登录你的账号</p>
      </div>
      <VanButton
        type="primary"
        block
        round
        :to="{ name: 'mobile-login', query: { email: form.email.trim().toLowerCase() } }"
        class="!h-11 !text-sm !font-medium"
      >
        前往登录
      </VanButton>
    </div>

    <form v-else class="space-y-4" novalidate @submit.prevent="handleSubmit">
      <div
        v-if="submitError"
        role="alert"
        class="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2.5 text-xs text-destructive"
      >
        {{ submitError }}
      </div>
      <div
        v-if="notice"
        role="status"
        class="rounded-xl border border-primary/30 bg-primary/10 px-3 py-2.5 text-xs text-primary"
      >
        {{ notice }}
      </div>

      <div class="space-y-1.5">
        <label for="reset-email" class="block text-xs font-medium text-foreground">邮箱地址</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.email ? 'border-destructive' : 'border-input'"
        >
          <Mail :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="reset-email"
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
        <label for="reset-code" class="block text-xs font-medium text-foreground">邮箱验证码</label>
        <div class="flex items-center gap-2">
          <div
            class="flex flex-1 items-center gap-2 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
            :class="fieldErrors.code ? 'border-destructive' : 'border-input'"
          >
            <KeyRound :size="16" class="text-muted-foreground shrink-0" />
            <input
              id="reset-code"
              v-model="form.code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              placeholder="6 位数字"
              class="flex-1 min-w-0 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
              @input="fieldErrors.code = ''"
            />
          </div>
          <VanButton
            type="primary"
            plain
            size="small"
            :loading="sendingCode"
            :disabled="sendingCode || cooldown.isCoolingDown.value"
            class="!h-11 !rounded-xl !px-3.5 shrink-0"
            @click="handleSendCode"
          >
            {{ cooldown.isCoolingDown.value ? `${cooldown.secondsLeft.value}s` : '获取验证码' }}
          </VanButton>
        </div>
        <p v-if="fieldErrors.code" class="text-xs text-destructive">{{ fieldErrors.code }}</p>
      </div>

      <div class="space-y-1.5">
        <label for="reset-password" class="block text-xs font-medium text-foreground">新密码</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.newPassword ? 'border-destructive' : 'border-input'"
        >
          <Lock :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="reset-password"
            v-model="form.newPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="8–32 位新密码"
            class="flex-1 min-w-0 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            @input="fieldErrors.newPassword = ''"
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
        <p v-if="fieldErrors.newPassword" class="text-xs text-destructive">{{ fieldErrors.newPassword }}</p>
      </div>

      <div class="space-y-1.5">
        <label for="reset-confirm" class="block text-xs font-medium text-foreground">确认新密码</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.confirmPassword ? 'border-destructive' : 'border-input'"
        >
          <Lock :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="reset-confirm"
            v-model="form.confirmPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="再次输入新密码"
            class="flex-1 min-w-0 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            @input="fieldErrors.confirmPassword = ''"
          />
        </div>
        <p v-if="fieldErrors.confirmPassword" class="text-xs text-destructive">{{ fieldErrors.confirmPassword }}</p>
      </div>

      <VanButton
        type="primary"
        native-type="submit"
        block
        round
        :loading="submitting"
        loading-text="正在提交…"
        class="!h-11 !text-sm !font-medium !mt-5"
      >
        确认重置密码
      </VanButton>
    </form>

    <template #footer>
      记起密码了？
      <RouterLink to="/auth/login" class="font-medium text-primary">返回登录</RouterLink>
    </template>
  </AuthLayout>
</template>
