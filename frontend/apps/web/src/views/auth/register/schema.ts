import { z } from 'zod'

export const registrationSchema = z.object({
  email: z.string().trim().toLowerCase()
    .min(1, '邮箱不能为空')
    .email('请输入正确的邮箱地址')
    .max(191, '邮箱不能超过191个字符'),
  code: z.string().trim()
    .min(1, '验证码不能为空')
    .regex(/^[0-9]{6}$/, '请输入6位数字验证码'),
  password: z.string()
    .min(8, '密码不能少于8位')
    .max(32, '密码不能超过32位'),
})
