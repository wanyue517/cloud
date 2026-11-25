
<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Eye, EyeOff } from '@lucide/vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { useRegistration } from './useRegistration'
import { registrationSchema } from './schema'

const formSchema = toTypedSchema(registrationSchema)

const form = useForm({
  validationSchema: formSchema,
  initialValues: {
    email: '',
    code: '',
    password: '',
  },
})

const showPassword = ref(false)
const {
  sending, submitting, registered, countdown, notice, error,
  sendCode: dispatchCode, register, clearMessages,
} = useRegistration()

async function sendCode() {
  if (sending.value || submitting.value || countdown.value > 0) return
  const result = await form.validateField('email')
  if (!result.valid || !form.values.email) return
  await dispatchCode(form.values.email)
}

const onSubmit = form.handleSubmit(async (values) => {
  await register(values)
  if (registered.value) form.resetForm()
})

watch(() => form.values.email?.trim().toLowerCase(), (email, previous) => {
  if (email === previous || registered.value) return
  form.setFieldValue('code', '')
  form.setFieldError('code', undefined)
  clearMessages()
})
</script>

<template>
  <main
      class="flex min-h-screen items-center justify-center
           bg-background px-5 py-12 text-foreground"
  >
    <div class="w-full max-w-[400px]">
      <!-- 品牌与标题 -->
      <header class="mb-9">
        <RouterLink
            to="/"
            class="mb-8 inline-flex items-center gap-2.5
                 text-sm font-semibold tracking-tight"
        >
          <span
              class="flex size-8 items-center justify-center
                   rounded-lg bg-primary text-primary-foreground"
          >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                width="19"
                height="19"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
            >
              <path
                  d="M20 16.6A4.5 4.5 0 0 0 18 8h-1.2A7 7 0 0 0 3 10.5 4 4 0 0 0 4 18h15a3 3 0 0 0 1-1.4Z"
              />
            </svg>
          </span>
          Cloudlane
        </RouterLink>

        <h1 class="text-2xl font-semibold tracking-tight">
          创建账号
        </h1>

        <p class="mt-2 text-sm text-muted-foreground">
          使用邮箱注册，开始使用 Cloudlane
        </p>
        <p class="mt-2 text-xs text-muted-foreground">用户名将使用邮箱中 @ 前的内容。</p>
      </header>

      <section v-if="registered" role="status" aria-live="polite" class="space-y-4">
        <p class="text-sm">注册成功，请使用邮箱和密码登录。</p>
        <RouterLink to="/auth/login" class="font-medium text-primary hover:underline">
          前往登录
        </RouterLink>
      </section>

      <!-- 注册表单 -->
      <form
          v-else
          class="space-y-5"
          novalidate
          @submit="onSubmit"
      >
        <fieldset :disabled="sending || submitting" class="contents">
        <!-- 邮箱 -->
        <FormField
            v-slot="{ componentField }"
            name="email"
        >
          <FormItem>
            <FormLabel class="text-sm">邮箱地址</FormLabel>

            <FormControl>
              <Input
                  v-bind="componentField"
                  type="email"
                  autocomplete="email"
                  placeholder="name@example.com"
                  class="h-11 rounded-lg"
              />
            </FormControl>

            <FormMessage />
          </FormItem>
        </FormField>

        <!-- 验证码 -->
        <FormField
            v-slot="{ componentField }"
            name="code"
        >
          <FormItem>
            <FormLabel class="text-sm">邮箱验证码</FormLabel>

            <div class="flex gap-2.5">
              <FormControl>
                <Input
                    v-bind="componentField"
                    type="text"
                    inputmode="numeric"
                    autocomplete="one-time-code"
                    maxlength="6"
                    placeholder="输入6位验证码"
                    class="h-11 min-w-0 flex-1 rounded-lg"
                />
              </FormControl>

              <Button
                  type="button"
                  variant="outline"
                  class="h-11 w-[112px] shrink-0 rounded-lg text-sm"
                  :disabled="sending || submitting || countdown > 0"
                  :aria-busy="sending"
                  @click="sendCode"
              >
                {{ sending ? '发送中…' : countdown > 0
                  ? `${countdown}s 后重发`
                  : '获取验证码'
                }}
              </Button>
            </div>

            <FormMessage />
          </FormItem>
        </FormField>

        <!-- 密码 -->
        <FormField
            v-slot="{ componentField }"
            name="password"
        >
          <FormItem>
            <FormLabel class="text-sm">设置密码</FormLabel>

            <div class="relative">
              <FormControl>
                <Input
                    v-bind="componentField"
                    :type="showPassword ? 'text' : 'password'"
                    autocomplete="new-password"
                    placeholder="8–32位字符"
                    maxlength="32"
                    class="h-11 rounded-lg pr-11"
                />
              </FormControl>

              <button
                  type="button"
                  class="absolute inset-y-0 right-0 flex w-11
                       items-center justify-center rounded-r-lg
                       text-muted-foreground transition-colors
                       hover:text-foreground"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" :size="17" />
                <Eye v-else :size="17" />
              </button>
            </div>

            <FormMessage />
          </FormItem>
        </FormField>
        </fieldset>

        <!-- 状态提示 -->
        <p
            v-if="notice"
            role="status"
            class="text-xs text-muted-foreground"
        >
          {{ notice }}
        </p>
        <p v-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>

        <!-- 提交 -->
        <Button
            type="submit"
            class="mt-2 h-11 w-full rounded-lg text-sm font-medium"
            :disabled="sending || submitting || form.isSubmitting.value"
            :aria-busy="submitting"
        >
          {{ submitting ? '注册中…' : '创建账号' }}
        </Button>
      </form>

      <!-- 底部 -->
      <p v-if="!registered" class="mt-7 text-center text-sm text-muted-foreground">
        已有账号？
        <RouterLink
            to="/auth/login"
            class="font-medium text-primary hover:underline underline-offset-4"
        >
          立即登录
        </RouterLink>
      </p>
    </div>
  </main>
</template>
