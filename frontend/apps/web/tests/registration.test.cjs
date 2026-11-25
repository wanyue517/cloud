const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const app = path.resolve(__dirname, '..')
const dependency = createRequire(path.join(app, 'package.json'))
const ts = dependency('typescript')
const vue = dependency('vue')

function load(relative, dependencies, globals = {}) {
  const source = fs.readFileSync(path.join(app, relative), 'utf8')
    .replace('import.meta.env.VITE_API_BASE_URL', 'undefined')
  const { outputText } = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true,
  } })
  const module = { exports: {} }
  vm.runInNewContext(outputText, {
    module, exports: module.exports, console, Error,
    require: name => dependencies[name] ?? dependency(name),
    ...globals,
  }, { filename: relative })
  return module.exports
}

function state(api = {}) {
  const clock = { now: 0, tick: undefined, disposed: false }
  const defaults = { sendEmailCode: async () => true, registerWithEmail: async () => true }
  const mod = load('src/views/auth/register/useRegistration.ts', {
    vue: { ...vue, onUnmounted: callback => { clock.dispose = callback } },
    '@/api/auth/register': { ...defaults, ...api },
  }, {
    Date: { now: () => clock.now },
    setInterval: callback => { clock.tick = callback; return 1 },
    clearInterval: () => { clock.tick = undefined; clock.disposed = true },
  })
  return { ...mod.useRegistration(), clock }
}

test('send awaits success, deduplicates in-flight requests, and starts cooldown', async () => {
  let resolve, calls = 0
  const s = state({ sendEmailCode: () => { calls++; return new Promise(r => { resolve = r }) } })
  const pending = s.sendCode('user@example.com')
  assert.equal(s.sending.value, true)
  await s.sendCode('user@example.com')
  assert.equal(calls, 1)
  assert.equal(s.countdown.value, 0)
  resolve(true)
  await pending
  assert.equal(s.countdown.value, 60)
  assert.match(s.notice.value, /user@example.com/)
  await s.sendCode('user@example.com')
  assert.equal(calls, 1)
})

test('failed send has error and permits retry without false success', async () => {
  const s = state({ sendEmailCode: async () => { throw new Error('SMTP unavailable') } })
  await s.sendCode('user@example.com')
  assert.equal(s.error.value, 'SMTP unavailable')
  assert.equal(s.notice.value, '')
  assert.equal(s.countdown.value, 0)
  assert.equal(s.sending.value, false)
})

test('cooldown uses elapsed wall time and clears timers on unmount', async () => {
  const s = state()
  await s.sendCode('user@example.com')
  s.clock.now = 59_500
  s.clock.tick()
  assert.equal(s.countdown.value, 1)
  s.clock.now = 61_000
  s.clock.tick()
  assert.equal(s.countdown.value, 0)
  assert.equal(s.clock.tick, undefined)
  await s.sendCode('user@example.com')
  s.clock.dispose()
  assert.equal(s.clock.tick, undefined)
})

test('registration blocks repeated submissions and only succeeds on true', async () => {
  let resolve, calls = 0
  const s = state({ registerWithEmail: () => { calls++; return new Promise(r => { resolve = r }) } })
  const pending = s.register({})
  await s.register({})
  await s.sendCode('user@example.com')
  assert.equal(calls, 1)
  assert.equal(s.submitting.value, true)
  assert.equal(s.registered.value, false)
  resolve(true)
  await pending
  assert.equal(s.registered.value, true)
  assert.equal(s.submitting.value, false)
  await s.register({})
  assert.equal(calls, 1)
})

test('false registration and API errors remain on the form', async () => {
  const s = state({ registerWithEmail: async () => false })
  await s.register({})
  assert.equal(s.registered.value, false)
  assert.notEqual(s.error.value, '')
  const failed = state({ registerWithEmail: async () => { throw new Error('Code expired') } })
  await failed.register({})
  assert.equal(failed.error.value, 'Code expired')
  assert.equal(failed.submitting.value, false)
})

function wire(adapter) {
  const axios = dependency('axios')
  const client = axios.create({ baseURL: '/api', adapter })
  const shim = new Proxy(axios, { get: (target, prop) => prop === 'create' ? () => client : target[prop] })
  const request = load('src/utils/request.ts', { axios: shim }, {
    window: { location: { origin: 'http://localhost:3000' } }, Blob, URL, setTimeout,
  })
  const api = load('src/api/auth/register/index.ts', { '@/utils/request': request })
  return { request, api, client, axios }
}

test('actual API and request layers match both backend contracts without auth tokens', async () => {
  const calls = []
  const { request, api, client } = wire(async config => {
    calls.push(config)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 0, data: true } }
  })
  request.setupRequest({ getAccessToken: () => 'must-not-be-sent' })
  assert.equal(await api.sendEmailCode(' User@Example.com '), true)
  assert.equal(client.getUri(calls[0]), '/api/admin/auth/register/send-email-code?email=user%40example.com')
  assert.equal(calls[0].data, undefined)
  assert.equal(calls[0].headers.get('Authorization'), undefined)
  await api.registerWithEmail({ displayName: ' User ', email: ' User@Example.com ', code: '012345', password: ' password123 ' })
  assert.equal(client.getUri(calls[1]), '/api/admin/auth/email')
  const body = JSON.parse(calls[1].data)
  assert.equal(Object.hasOwn(body, 'displayName'), false)
  assert.equal(body.email, 'user@example.com')
  assert.equal(body.password, ' password123 ')
  assert.equal(body.code, '012345')
})

test('public API failures preserve business message and never retry or refresh', async () => {
  let calls = 0, refreshes = 0
  const { request, api, axios } = wire(async config => {
    calls++
    throw new axios.AxiosError('failed', 'ERR_BAD_REQUEST', config, undefined,
      { config, status: 429, statusText: 'Too Many Requests', headers: {}, data: { code: 429, msg: 'Rate limited' } })
  })
  request.setupRequest({ refreshAccessToken: async () => { refreshes++; return 'token' } })
  await assert.rejects(api.sendEmailCode('user@example.com'), error =>
    error.message === 'Rate limited' && error.httpStatus === 429)
  assert.equal(calls, 1)
  assert.equal(refreshes, 0)
})

test('registration validation normalizes email and code while preserving password', () => {
  const { registrationSchema } = load('src/views/auth/register/schema.ts', {})
  const result = registrationSchema.parse({
    email: ' User@Example.com ', code: ' 012345 ', password: ' password123 ', displayName: 'spoofed',
  })
  assert.equal(result.email, 'user@example.com')
  assert.equal(result.code, '012345')
  assert.equal(result.password, ' password123 ')
  assert.equal(Object.hasOwn(result, 'displayName'), false)
})

test('registration validation rejects invalid fields and enforces password boundaries', () => {
  const { registrationSchema } = load('src/views/auth/register/schema.ts', {})
  const valid = { email: 'user@example.com', code: '012345', password: 'password123' }
  for (const password of ['x'.repeat(8), 'x'.repeat(32)]) {
    assert.equal(registrationSchema.safeParse({ ...valid, password }).success, true)
  }
  for (const invalid of [
    { email: '' }, { email: 'invalid' },
    { email: 'a'.repeat(64) + '@' + 'b'.repeat(63) + '.' + 'c'.repeat(63) + '.com' },
    { code: '' }, { code: '12345' }, { code: '1234567' }, { code: '12345a' },
    { password: 'x'.repeat(7) }, { password: 'x'.repeat(33) },
  ]) {
    assert.equal(registrationSchema.safeParse({ ...valid, ...invalid }).success, false)
  }
})
