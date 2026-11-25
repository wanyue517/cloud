import { ref } from 'vue'
import { loginWithEmail, type EmailLoginRequest } from '@/api/auth/login'
import { saveAccessToken } from '@/lib/auth'

export function useLogin() {
  const submitting = ref(false)
  const error = ref('')

  async function login(values: EmailLoginRequest) {
    if (submitting.value) return false
    submitting.value = true
    error.value = ''
    try {
      const result = await loginWithEmail(values)
      if (!result || typeof result.accessToken !== 'string' || !result.accessToken.trim()) {
        throw new Error('登录响应缺少凭证，请稍后重试')
      }
      saveAccessToken(result.accessToken)
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '登录失败，请稍后重试'
      return false
    } finally {
      submitting.value = false
    }
  }

  return { submitting, error, login }
}
