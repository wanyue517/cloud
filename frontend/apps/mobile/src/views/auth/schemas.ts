import { z } from 'zod'

export const emailSchema = z
  .string()
  .trim()
  .toLowerCase()
  .min(1, '邮箱不能为空')
  .email('请输入正确的邮箱地址')
  .max(191, '邮箱不能超过 191 个字符')

export const codeSchema = z
  .string()
  .trim()
  .min(1, '验证码不能为空')
  .regex(/^[0-9]{6}$/, '请输入 6 位数字验证码')

export const passwordSchema = z
  .string()
  .min(1, '密码不能为空')
  .min(8, '密码不能少于 8 位')
  .max(32, '密码不能超过 32 位')
  .refine(value => value.trim().length > 0, '密码不能为空')

export const loginSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
})

export const registerSchema = z.object({
  email: emailSchema,
  code: codeSchema,
  password: passwordSchema,
})

export const forgotPasswordSchema = z
  .object({
    email: emailSchema,
    code: codeSchema,
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, '请再次输入新密码'),
  })
  .refine(data => data.newPassword === data.confirmPassword, {
    message: '两次输入的新密码不一致',
    path: ['confirmPassword'],
  })
