# 邮箱注册

在前端项目根目录运行 `pnpm --filter @cloud/web dev`，访问 `/auth/register`。
开发时，Vite 将 `/api` 请求代理至后端（默认 `http://127.0.0.1:8000`）；
如需修改地址，将 `.env.example` 中的配置放入本机 `.env.local`。
生产部署由 Nginx 将同源 `/api` 转发至后端。两个应用共享会话，API 基址固定为 `/api`；旧 `VITE_API_BASE_URL` 不再生效。

接口约定：

- `POST /api/admin/auth/register/send-email-code?email=...`：无请求体，返回 `R<boolean>`。
- `POST /api/admin/auth/email`：JSON 仅包含 `email`、`code`、`password`，返回 `R<boolean>`。
- `R` 的 `code=0` 且 `data=true` 才展示成功；错误由页面显示，不触发登录刷新。

用户名由后端自动取规范化邮箱中最后一个 `@` 前的部分，无需填写或提交昵称。
邮箱最长 191 字符、验证码为 6 位数字、密码 8–32 字符。
发送成功后才开始 60 秒倒计时；后台标签页通过截止时间校正。
修改邮箱会清空旧验证码，发送和注册请求互斥，失败保留表单以供重试。
注册成功后显示登录入口，不自动登录。
登录页为 `/auth/login`，调用 `POST /api/admin/auth/login/email`，JSON 仅含 `email`、`password`。
登录成功后将 Access Token 保存到当前标签页的 sessionStorage，后续 API 请求自动携带 Bearer 凭证，并跳转白名单内的 `returnTo` 地址（后台使用整页跳转），默认 `/app`。
密码保持原样，不去除空格。登录失败显示后端错误并保留输入；受保护请求返回 401 时清除凭证并返回登录页。

验证：

```powershell
pnpm --filter @cloud/web build
pnpm --filter @cloud/web test:registration
```

测试使用模拟网络响应，覆盖真实请求封装、参数序列化、发送失败、重复点击及倒计时，不会发送真实邮件。
