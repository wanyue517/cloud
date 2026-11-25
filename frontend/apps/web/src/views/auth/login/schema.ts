import { z } from 'zod'

export const loginSchema = z.object({
  email: z.string().trim().toLowerCase()
    .min(1, '邮箱不能为空')
    .email('请输入正确的邮箱地址')
    .max(191, '邮箱不能超过191个字符'),
  password: z.string()
    .min(1, '密码不能为空')
    .min(8, '密码不能少于8位')
    .max(32, '密码不能超过32位')
    .refine(value => value.trim().length > 0, '密码不能为空'),
})
