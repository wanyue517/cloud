const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M007: auth Zod schemas validate login, register, and forgot-password forms', async () => {
  const schemas = await import(pathToFileURL(path.join(mobileRoot, 'src/views/auth/schemas.ts')).href)

  const okLogin = schemas.loginSchema.safeParse({ email: '  User@Example.COM ', password: 'Password123' })
  assert.equal(okLogin.success, true)
  assert.equal(okLogin.data.email, 'user@example.com')

  const badLogin = schemas.loginSchema.safeParse({ email: 'not-email', password: '123' })
  assert.equal(badLogin.success, false)

  const okReg = schemas.registerSchema.safeParse({ email: 'a@b.com', code: '123456', password: 'Password123' })
  assert.equal(okReg.success, true)

  const badCode = schemas.registerSchema.safeParse({ email: 'a@b.com', code: '12ab', password: 'Password123' })
  assert.equal(badCode.success, false)

  const mismatchReset = schemas.forgotPasswordSchema.safeParse({
    email: 'a@b.com',
    code: '654321',
    newPassword: 'NewPassword1',
    confirmPassword: 'DifferentPassword',
  })
  assert.equal(mismatchReset.success, false)
  assert.equal(mismatchReset.error.issues[0].path[0], 'confirmPassword')

  const okReset = schemas.forgotPasswordSchema.safeParse({
    email: 'a@b.com',
    code: '654321',
    newPassword: 'NewPassword1',
    confirmPassword: 'NewPassword1',
  })
  assert.equal(okReset.success, true)
})

test('M007: cooldown controller tracks elapsed wall-clock time and cleans up cleanly', async () => {
  const { createCooldownController } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/composables/useCooldown.ts')).href
  )

  let now = 1_000_000
  const ctrl = createCooldownController(() => now)
  assert.equal(ctrl.isCoolingDown.value, false)

  ctrl.start(60)
  assert.equal(ctrl.secondsLeft.value, 60)
  assert.equal(ctrl.isCoolingDown.value, true)

  now += 25_000
  ctrl.sync()
  assert.equal(ctrl.secondsLeft.value, 35)

  now += 40_000
  ctrl.sync()
  assert.equal(ctrl.secondsLeft.value, 0)
  assert.equal(ctrl.isCoolingDown.value, false)
  ctrl.dispose()
})

test('M007: router wires login, register, and forgot-password to real auth views', () => {
  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  assert.match(routerSource, /@\/views\/auth\/login\.vue/)
  assert.match(routerSource, /@\/views\/auth\/register\.vue/)
  assert.match(routerSource, /@\/views\/auth\/forgot-password\.vue/)
})

test('M034: QR login route, mobile confirm view, and PC login QR tab are wired', () => {
  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  assert.match(routerSource, /path:\s*'\/qr-confirm'/)
  assert.match(routerSource, /@\/views\/auth\/qr-confirm\.vue/)

  const qrConfirmSource = fs.readFileSync(path.join(mobileRoot, 'src/views/auth/qr-confirm.vue'), 'utf8')
  assert.match(qrConfirmSource, /\/v1\/auth\/qr\/.*\/scan/)
  assert.match(qrConfirmSource, /\/v1\/auth\/qr\/.*\/confirm/)

  const meSource = fs.readFileSync(path.join(mobileRoot, 'src/views/me/index.vue'), 'utf8')
  assert.match(meSource, /\/qr-confirm/)

  const pcLoginSource = fs.readFileSync(
    path.resolve(mobileRoot, '../web/src/views/auth/login/index.vue'),
    'utf8',
  )
  assert.match(pcLoginSource, /\/admin\/auth\/qr\/create/)
  assert.match(pcLoginSource, /\/admin\/auth\/qr\/.*\/status/)
})
