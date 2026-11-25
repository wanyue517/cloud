const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const app = path.resolve(__dirname, '..')
const dependency = createRequire(path.join(app, 'package.json'))
const ts = dependency('typescript')

function load(relative, dependencies = {}, globals = {}) {
  const source = fs.readFileSync(path.join(app, relative), 'utf8')
    .replace('import.meta.env.VITE_API_BASE_URL', 'undefined')
  const { outputText } = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true,
  } })
  const module = { exports: {} }
  vm.runInNewContext(outputText, {
    module, exports: module.exports, Error,
    require: name => dependencies[name] ?? dependency(name), ...globals,
  }, { filename: relative })
  return module.exports
}

const values = { email: ' User@Example.com ', password: ' password123 ' }

test('login validation normalizes email, preserves password, and rejects invalid values', () => {
  const { loginSchema } = load('src/views/auth/login/schema.ts')
  const parsed = loginSchema.parse(values)
  assert.equal(parsed.email, 'user@example.com')
  assert.equal(parsed.password, values.password)
  for (const invalid of [
    { email: '' }, { email: 'invalid' }, { email: 'a'.repeat(192) + '@example.com' },
    { password: '' }, { password: '1234567' }, { password: ' '.repeat(8) },
    { password: 'a'.repeat(33) },
  ]) assert.equal(loginSchema.safeParse({ ...values, ...invalid }).success, false)
})

test('login uses the backend contract without stale auth or automatic refresh', async () => {
  const axios = dependency('axios')
  const calls = []
  const response = { accessToken: 'new-token', expiresIn: 900, tokenType: 'Bearer' }
  const client = axios.create({ baseURL: '/api', adapter: async config => {
    calls.push(config)
    return { config, status: 200, headers: {}, data: { code: 0, data: response } }
  } })
  const shim = new Proxy(axios, { get: (target, prop) => prop === 'create' ? () => client : target[prop] })
  const request = load('src/utils/request.ts', { axios: shim }, {
    window: { location: { origin: 'http://localhost:3000' } }, Blob, URL, setTimeout,
  })
  let refreshes = 0
  request.setupRequest({
    getAccessToken: () => 'old-token',
    refreshAccessToken: async () => { refreshes++; return 'refreshed' },
  })
  const api = load('src/api/auth/login/index.ts', { '@/utils/request': request })
  assert.equal(await api.loginWithEmail(values), response)
  assert.equal(client.getUri(calls[0]), '/api/admin/auth/login/email')
  assert.deepEqual(JSON.parse(calls[0].data), { email: 'user@example.com', password: values.password })
  assert.equal(calls[0].headers.get('Authorization'), undefined)
  assert.equal(calls[0].withCredentials, true)
  client.defaults.adapter = async config => ({
    config, status: 200, headers: {}, data: { code: 401, msg: '邮箱或密码错误' },
  })
  await assert.rejects(api.loginWithEmail(values), /邮箱或密码错误/)
  assert.equal(refreshes, 0)
})

function state(loginWithEmail, saveAccessToken = () => {}) {
  return load('src/views/auth/login/useLogin.ts', {
    '@/api/auth/login': { loginWithEmail }, '@/lib/auth': { saveAccessToken },
  }).useLogin()
}

test('login blocks duplicate submissions and saves successful credentials', async () => {
  let resolve, calls = 0, saved
  const s = state(() => { calls++; return new Promise(r => { resolve = r }) }, token => { saved = token })
  const pending = s.login(values)
  assert.equal(s.submitting.value, true)
  assert.equal(await s.login(values), false)
  assert.equal(calls, 1)
  resolve({ accessToken: 'new-token' })
  assert.equal(await pending, true)
  assert.equal(saved, 'new-token')
  assert.equal(s.submitting.value, false)
})

test('failed login and malformed responses do not save credentials and allow retry', async () => {
  let attempts = 0, saves = 0
  const s = state(async () => {
    if (++attempts === 1) throw new Error('邮箱或密码错误')
    return { accessToken: 'valid-token' }
  }, () => { saves++ })
  assert.equal(await s.login(values), false)
  assert.equal(s.error.value, '邮箱或密码错误')
  assert.equal(s.submitting.value, false)
  assert.equal(await s.login(values), true)
  assert.equal(s.error.value, '')
  assert.equal(saves, 1)
  for (const response of [null, true, {}, { accessToken: '' }, { accessToken: ' ' }]) {
    const invalid = state(async () => response, () => assert.fail('must not save'))
    assert.equal(await invalid.login(values), false)
    assert.match(invalid.error.value, /缺少凭证/)
  }
})

test('session storage saves only the access token and supports clearing', () => {
  const entries = new Map()
  const auth = load('src/lib/auth.ts', {}, { sessionStorage: {
    getItem: key => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: key => entries.delete(key),
  } })
  assert.equal(auth.getAccessToken(), null)
  auth.saveAccessToken('token')
  assert.equal(auth.getAccessToken(), 'token')
  assert.equal(entries.size, 1)
  auth.clearAccessToken()
  assert.equal(auth.getAccessToken(), null)
})
