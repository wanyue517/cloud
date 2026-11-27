import { request } from '@repo/session/request'

export interface EmailLoginRequest {
  email: string
  password: string
}

export interface EmailLoginResponse {
  accessToken: string
  refreshToken?: string
  expiresIn: number
  tokenType: string
}

export interface EmailRegisterRequest {
  email: string
  code: string
  password: string
}

export interface PasswordResetRequest {
  email: string
  code: string
  newPassword: string
}

const PUBLIC_OPTIONS = {
  skipAuth: true,
  skipRefresh: true,
  silent: true,
} as const

export function loginWithEmail(values: EmailLoginRequest) {
  return request.post<EmailLoginResponse>(
    '/admin/auth/login/email',
    {
      email: values.email.trim().toLowerCase(),
      password: values.password,
    },
    { ...PUBLIC_OPTIONS, withCredentials: true },
  )
}

export function sendRegisterEmailCode(email: string) {
  return request.post<boolean>('/admin/auth/register/send-email-code', undefined, {
    ...PUBLIC_OPTIONS,
    params: { email: email.trim().toLowerCase() },
  })
}

export function registerWithEmail(values: EmailRegisterRequest) {
  return request.post<boolean>(
    '/admin/auth/email',
    {
      email: values.email.trim().toLowerCase(),
      code: values.code.trim(),
      password: values.password,
    },
    PUBLIC_OPTIONS,
  )
}

export function sendPasswordResetCode(email: string) {
  return request.post<boolean>('/admin/auth/password/send-code', undefined, {
    ...PUBLIC_OPTIONS,
    params: { email: email.trim().toLowerCase() },
  })
}

export function resetPasswordWithEmail(values: PasswordResetRequest) {
  return request.post<boolean>(
    '/admin/auth/password/reset',
    {
      email: values.email.trim().toLowerCase(),
      code: values.code.trim(),
      newPassword: values.newPassword,
    },
    PUBLIC_OPTIONS,
  )
}
