import { onUnmounted, ref } from 'vue'
import { registerWithEmail, sendEmailCode, type EmailRegisterRequest } from '@/api/auth/register'

/** 页面状态独立于表单校验，所有网络入口共享防重复提交状态。 */
export function useRegistration() {
  const sending = ref(false)
  const submitting = ref(false)
  const registered = ref(false)
  const countdown = ref(0)
  const notice = ref('')
  const error = ref('')
  let timer: ReturnType<typeof setInterval> | undefined

  function stopTimer() {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
  }

  function startCountdown() {
    stopTimer()
    const deadline = Date.now() + 60_000
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      if (countdown.value === 0) stopTimer()
    }, 1000)
  }

  function clearMessages() {
    notice.value = ''
    error.value = ''
  }

  async function sendCode(email: string) {
    if (sending.value || submitting.value || registered.value || countdown.value > 0) return
    sending.value = true
    clearMessages()
    try {
      const sent = await sendEmailCode(email)
      if (sent !== true) throw new Error('验证码未发送成功，请稍后重试')
      notice.value = `验证码已发送至 ${email.trim().toLowerCase()}，请检查收件箱或垃圾邮件。`
      startCountdown()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '验证码发送失败，请稍后重试'
    } finally {
      sending.value = false
    }
  }

  async function register(values: EmailRegisterRequest) {
    if (sending.value || submitting.value || registered.value) return
    submitting.value = true
    clearMessages()
    try {
      const created = await registerWithEmail(values)
      if (created !== true) throw new Error('注册未完成，请稍后重试')
      registered.value = true
      stopTimer()
      countdown.value = 0
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '注册失败，请稍后重试'
    } finally {
      submitting.value = false
    }
  }

  onUnmounted(stopTimer)
  return { sending, submitting, registered, countdown, notice, error, sendCode, register, clearMessages }
}
