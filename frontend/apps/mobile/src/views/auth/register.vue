<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Button as VanButton } from 'vant'
import { Eye, EyeOff, KeyRound, Lock, Mail } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { registerWithEmail, sendRegisterEmailCode } from '@/api/auth'
import { useCooldown } from '@/composables/useCooldown'
import { siteConfig } from '@/composables/useSiteConfig'
import { emailSchema, registerSchema } from './schemas'
import AuthLayout from './AuthLayout.vue'

const router = useRouter()
const cooldown = useCooldown(60)

const form = reactive({
  email: '',
  code: '',
  password: '',
})
const fieldErrors = reactive({
  email: '',
  code: '',
  password: '',
})
const showPassword = ref(false)
const sendingCode = ref(false)
const submitting = ref(false)
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
    const ok = await sendRegisterEmailCode(parsedEmail.data)
    if (!ok) throw new Error('发送验证码失败，请稍后重试')
    notice.value = '6 位验证码已发送至邮箱，请注意查收'
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
  fieldErrors.password = ''
  const parsed = registerSchema.safeParse(form)
  if (parsed.success) return true
  for (const issue of parsed.error.issues) {
    const key = issue.path[0]
    if (key === 'email' && !fieldErrors.email) fieldErrors.email = issue.message
    if (key === 'code' && !fieldErrors.code) fieldErrors.code = issue.message
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
    const ok = await registerWithEmail({
      email: form.email,
      code: form.code,
      password: form.password,
    })
    if (!ok) throw new Error('注册未成功，请核对验证码后重试')
    toast.success('注册成功，请登录')
    await router.replace({
      name: 'mobile-login',
      query: { email: form.email.trim().toLowerCase() },
    })
  } catch (err) {
    submitError.value = normalizeErrorMessage(err, '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout title="创建账号" subtitle="通过邮箱验证注册并初始化你的个人空间">
    <div v-if="!siteConfig.allowRegister" class="space-y-4 text-center py-2">
      <p class="text-sm font-medium text-foreground">管理员已关闭公开注册</p>
      <p class="text-xs text-muted-foreground">如需账号请联系站点管理员开通。</p>
      <VanButton type="primary" block round to="/auth/login" class="!h-10 !text-sm">返回登录</VanButton>
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
        <label for="reg-email" class="block text-xs font-medium text-foreground">邮箱地址</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.email ? 'border-destructive' : 'border-input'"
        >
          <Mail :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="reg-email"
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
        <label for="reg-code" class="block text-xs font-medium text-foreground">邮箱验证码</label>
        <div class="flex items-center gap-2">
          <div
            class="flex flex-1 items-center gap-2 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
            :class="fieldErrors.code ? 'border-destructive' : 'border-input'"
          >
            <KeyRound :size="16" class="text-muted-foreground shrink-0" />
            <input
              id="reg-code"
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
        <label for="reg-password" class="block text-xs font-medium text-foreground">设置密码</label>
        <div
          class="flex items-center gap-2.5 h-11 rounded-xl border bg-background px-3 transition-colors focus-within:border-primary"
          :class="fieldErrors.password ? 'border-destructive' : 'border-input'"
        >
          <Lock :size="16" class="text-muted-foreground shrink-0" />
          <input
            id="reg-password"
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="8–32 位字符"
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
        loading-text="正在注册…"
        class="!h-11 !text-sm !font-medium !mt-5"
      >
        完成注册
      </VanButton>
    </form>

    <template #footer>
      已有账号？
      <RouterLink to="/auth/login" class="font-medium text-primary">返回登录</RouterLink>
    </template>
  </AuthLayout>
</template>
