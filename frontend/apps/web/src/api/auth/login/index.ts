import { request } from '@/utils/request'

export interface EmailLoginRequest {
  email: string
  password: string
}

export interface EmailLoginResponse {
  accessToken: string
  expiresIn: number
  tokenType: string
}

export function loginWithEmail(values: EmailLoginRequest) {
  return request.post<EmailLoginResponse>('/admin/auth/login/email', {
    email: values.email.trim().toLowerCase(),
    password: values.password,
  }, { skipAuth: true, skipRefresh: true, silent: true, withCredentials: true })
}
