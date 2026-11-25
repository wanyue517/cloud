<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Cloud, Eye, EyeOff } from '@lucide/vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { loginSchema } from './schema'
import { useLogin } from './useLogin'

const router = useRouter()
const form = useForm({
  validationSchema: toTypedSchema(loginSchema),
  initialValues: { email: '', password: '' },
})
const showPassword = ref(false)
const { submitting, error, login } = useLogin()
const onSubmit = form.handleSubmit(async (values) => {
  if (await login(values)) {
    form.resetForm()
    await router.replace('/app')
  }
})
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-background px-5 py-12 text-foreground">
    <div class="w-full max-w-[400px]">
      <header class="mb-9">
        <RouterLink to="/" class="mb-8 inline-flex items-center gap-2.5 text-sm font-semibold tracking-tight">
          <span class="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Cloud :size="19" aria-hidden="true" />
          </span>
          Cloudlane
        </RouterLink>
        <h1 class="text-2xl font-semibold tracking-tight">欢迎回来</h1>
        <p class="mt-2 text-sm text-muted-foreground">使用邮箱和密码登录 Cloudlane</p>
      </header>

      <form class="space-y-5" novalidate @submit="onSubmit">
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

      <p class="mt-7 text-center text-sm text-muted-foreground">
        还没有账号？
        <RouterLink to="/auth/register" class="font-medium text-primary hover:underline underline-offset-4">
          立即注册
        </RouterLink>
      </p>
    </div>
  </main>
</template>
