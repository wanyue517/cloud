import { request } from '@/utils/request'

export interface EmailRegisterRequest {
  email: string
  code: string
  password: string
}

const publicRequest = { skipAuth: true, skipRefresh: true, silent: true }

export function sendEmailCode(email: string) {
  return request.post<boolean>('/admin/auth/register/send-email-code', undefined, {
    ...publicRequest,
    params: { email: email.trim().toLowerCase() },
  })
}

export function registerWithEmail(values: EmailRegisterRequest) {
  return request.post<boolean>('/admin/auth/email', {
    email: values.email.trim().toLowerCase(),
    code: values.code.trim(),
    password: values.password,
  }, publicRequest)
}
