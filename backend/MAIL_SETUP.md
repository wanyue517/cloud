# 邮件验证码接入

调用链：`AuthController → AuthService → VerificationCodeService → EmailVerificationCodeSender → MailService → JavaMailSender`。

Redis 是启动必需依赖。应用初始化时执行 PING 检查，连接、认证或命令执行失败都会报错并终止启动；需先启动 Redis，再启动后端。连接和命令超时沿用 `spring.data.redis` 配置。

- `modules/auth` 负责注册规则、验证码签发和消费、邮件内容。
- `common/mail` 负责 SMTP 文本邮件投递，不依赖认证模块。
- Lua 脚本位于 `src/main/resources/redis`。

启动前设置环境变量（不要将实际凭据提交到仓库）：

| 变量 | 用途 |
| --- | --- |
| `DB_USERNAME` | 数据库用户名，默认 root |
| `DB_PASSWORD` | 数据库密码 |
| `MAIL_USERNAME` | SMTP 账号 |
| `MAIL_PASSWORD` | SMTP 授权码 |
| `MAIL_FROM` | 发件地址，默认使用 MAIL_USERNAME，需与 SMTP 账号允许的地址一致 |
| `VERIFICATION_HMAC_SECRET` | 至少 32 字符的随机密钥，多实例必须一致 |
| `MAIL_HOST` / `MAIL_PORT` | 默认 smtp.qq.com / 587，要求 STARTTLS |

接口保持为 `POST /api/admin/auth/register/send-email-code?email=...`，成功返回 `data: true`。
注册使用 `POST /api/admin/auth/email`，JSON 只需传 `email`、`code`、`password`。
初始用户显示名称由后端取规范化邮箱的前缀，例如 `Alice@example.com` 对应 `alice`，无需传昵称。
验证码 300 秒有效，同场景同邮箱重发间隔 60 秒，累计错误 5 次失效。
请求尝试限额默认：直连 IP 每小时 20 次、目标邮箱每天 10 次、系统每小时 1000 次；可通过 `cloud.verification` 配置调整。窗口从第一次请求开始计时，失败尝试也计数。
发送失败只撤销本次签发，不返还限额和重发冷却，避免反复冲击 SMTP。
IP 限流使用直连地址；反向代理部署时需要在可信代理边界配置真实客户端地址，禁止直接信任任意客户端的转发头。

验证码必须在业务事务内校验占用。事务提交后消费，回滚恢复；事务结果未知时消费。进程崩溃或 Redis 回调失败时保持占用，直到原有效期结束，不恢复可重放的验证码。

验证命令：`mvn -B "-Dtest=AuthEmailTest,AuthEmailEndpointTest,VerificationCodeServiceTest,VerificationConfigurationTest" test`。不依赖真实 SMTP，不发送邮件。

Lua 实测（本地 Docker 容器名为 redis）：

```powershell
docker cp src/main/resources/redis redis:/tmp/cloud-verification-review-tests
docker cp scripts/test-verification-redis.sh redis:/tmp/cloud-verification-review-tests/test.sh
docker exec redis sh /tmp/cloud-verification-review-tests/test.sh
```

测试使用独立随机前缀，并在退出时删除本次测试键。

跨域由 Spring Security 统一处理，默认允许 `http://localhost:3000` 和 `http://127.0.0.1:3000`。
其他前端域名或端口可在 `cloud.cors.allowed-origins` 中配置，或设置逗号分隔的环境变量
`CORS_ALLOWED_ORIGINS`。填写完整来源（协议、域名、端口），不包含页面路径。
配置支持携带凭据，因此不能使用 `*`。修改配置后需重启后端。
