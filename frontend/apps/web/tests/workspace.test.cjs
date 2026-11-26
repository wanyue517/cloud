const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const root = path.resolve(__dirname, '../../..')
const dependency = createRequire(path.join(root, 'apps/web/package.json'))
const ts = dependency('typescript')
function load(relative, dependencies = {}, globals = {}) {
  let source = fs.readFileSync(path.join(root, relative), 'utf8')
    .replace('import.meta.env.BASE_URL', "'/admin/'")
    .replace('import.meta.env.VITE_API_BASE_URL', 'undefined')
  source = source.replace(/import\('@\/[^']+'\)/g, "Promise.resolve({ default: { render: () => null } })")
  const module = { exports: {} }
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true,
  } }).outputText, { module, exports: module.exports, Error, console,
    require: name => dependencies[name] ?? dependency(name), ...globals })
  return module.exports
}
const nav = load('packages/session/navigation.ts')
test('return allowlist accepts implemented local routes and rejects unsafe input', () => {
  for (const value of ['/admin', '/admin/', '/admin/users?page=2#row', '/admin/storage', '/admin/tasks', '/admin/settings/site', '/admin/logs', '/app', '/app/favorites', '/app/trash', '/app/tasks', '/app/shared', '/app/recent', '/app/settings/profile', '/app/settings/security', '/app/settings/appearance', '/app/settings/files', '/app/spaces/2124534695294148609/folders/10?preview=20']) assert.equal(nav.safeReturnPath(value), value)
  for (const value of [null, ['/admin'], '//evil.test/admin', 'https://evil.test', '/adminx', '/admin/../auth/login', '/admin/%2e%2e', '/admin\\evil', '/admin/users\n', '/auth/login', '/admin/unknown', '/admin/settings/unknown', '/app/settings/unknown', '/app/unknown']) assert.equal(nav.safeReturnPath(value), '/app')
  assert.equal(nav.isAdminPath('/admin?x=1'), true)
  assert.equal(nav.isAdminPath('/administrator'), false)
  assert.equal(new URL(nav.loginUrl('/admin/users?page=2'), 'http://local').searchParams.get('returnTo'), '/admin/users?page=2')
})
test('admin router redirects guests, hides admin from members, allows deep links and refresh', async () => {
  const vueRouter = dependency('vue-router')
  let token = null, admin = false, lookups = 0, redirect
  const makeRouter = () => load('apps/admin/src/router/index.ts', {
    'vue-router': { ...vueRouter, createWebHistory: () => vueRouter.createMemoryHistory('/admin/') },
    '@repo/session/navigation': nav,
    '@repo/session/auth': { getAccessToken: () => token },
    '@repo/session/request': { request: { get: async () => { lookups++; return { admin } } } },
  }, { window: { location: { replace: url => { redirect = url } } } }).default
  let router = makeRouter()
  await router.push('/users?page=2')
  assert.equal(redirect, '/auth/login?returnTo=%2Fadmin%2Fusers%3Fpage%3D2')
  assert.equal(lookups, 0)
  token = 'member'; router = makeRouter(); await router.push('/users')
  assert.equal(router.currentRoute.value.path, '/404')
  assert.equal(router.currentRoute.value.matched.length, 1)
  await router.push('/storage')
  assert.equal(router.currentRoute.value.path, '/404')
  admin = true; token = 'admin'; router = makeRouter(); await router.push('/users?page=2')
  assert.equal(router.currentRoute.value.fullPath, '/users?page=2')
  assert.equal(router.currentRoute.value.name, 'admin-users')
  assert.equal(router.currentRoute.value.matched.length, 2)
  await router.push('/')
  assert.equal(router.currentRoute.value.name, 'admin-dashboard')
  assert.equal(router.currentRoute.value.matched.length, 2)
  for (const [path, name] of [
    ['/messages', 'admin-messages'],
    ['/storage', 'admin-storage'],
    ['/settings/site', 'admin-settings-site'],
    ['/settings/auth', 'admin-settings-auth'],
    ['/settings/files', 'admin-settings-files'],
    ['/tasks', 'admin-tasks'],
    ['/logs', 'admin-logs'],
  ]) {
    await router.push(path)
    assert.equal(router.currentRoute.value.name, name)
    assert.equal(router.currentRoute.value.matched.length, 2)
  }
  await router.push('/not-a-real-admin-route')
  assert.equal(router.currentRoute.value.path, '/404')
  assert.equal(router.currentRoute.value.matched.length, 1)
  router = makeRouter(); await router.push('/users?page=2')
  assert.equal(router.currentRoute.value.path, '/users')
  token = null; await router.push('/')
  assert.equal(redirect, '/auth/login?returnTo=%2Fadmin%2F')
})
function wired() {
  const entries = new Map()
  const auth = load('packages/session/auth.ts', {}, { sessionStorage: {
    getItem: k => entries.get(k) ?? null, setItem: (k,v) => entries.set(k,v), removeItem: k => entries.delete(k),
  } })
  const axios = dependency('axios')
  const client = axios.create({ baseURL: '/api' })
  const shim = new Proxy(axios, { get: (obj,key) => key === 'create' ? () => client : obj[key] })
  const request = load('packages/session/request.ts', { axios: shim }, {
    window: { location: { origin: 'http://local' } }, URL, Blob, setTimeout,
  })
  const session = load('packages/session/session.ts', { './auth': auth, './request': request })
  return { auth, request: request.request, session, client, entries }
}
test('shared session refreshes once for concurrent expired requests', async () => {
  const { auth, request, session, client } = wired()
  auth.saveAccessToken('expired')
  let refreshes = 0
  session.setupSession(() => assert.fail('should refresh'))
  client.defaults.adapter = async config => {
    if (config.url.endsWith('/refresh')) {
      refreshes++
      assert.equal(config.withCredentials, true)
      assert.equal(config.headers.get('X-Requested-With'), 'Cloudlane')
      assert.equal(config.headers.get('Authorization'), undefined)
      return { config, status: 200, headers: {}, data: { code: 0, data: { accessToken: 'fresh' } } }
    }
    return { config, status: 200, headers: {}, data: config.headers.get('Authorization') === 'Bearer fresh' ? { code: 0, data: { admin: true } } : { code: 401, msg: 'expired' } }
  }
  const result = await Promise.all([request.get('/v1/me'), request.get('/v1/me')])
  assert.equal(result.every(me => me.admin), true)
  assert.equal(refreshes, 1)
  assert.equal(auth.getAccessToken(), 'fresh')
})
test('logout clears tab credentials and rejected refresh invalidates session', async () => {
  const { auth, request, session, client } = wired()
  let redirects = 0
  session.setupSession(() => redirects++)
  auth.saveAccessToken('token')
  client.defaults.adapter = async config => ({ config, status: 200, headers: {}, data: { code: 0, data: true } })
  await session.logout()
  assert.equal(auth.getAccessToken(), null)
  auth.saveAccessToken('revoked')
  client.defaults.adapter = async config => ({ config, status: 200, headers: {}, data: { code: 401, msg: 'revoked' } })
  await assert.rejects(request.get('/v1/me'), /revoked/)
  assert.equal(auth.getAccessToken(), null)
  assert.equal(redirects, 1)
})
test('apps never import each other and shared UI has no application aliases', () => {
  function files(dir) { return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(path.join(dir,e.name)) : [path.join(dir,e.name)]) }
  for (const app of ['web','admin']) for (const file of files(path.join(root, 'apps/'+app+'/src'))) {
    assert.doesNotMatch(fs.readFileSync(file,'utf8'), /(?:from\s*|import\s*\()['"][^'"]*(?:apps\/(?:web|admin)|@cloud\/(?:web|admin)|\.\.\/(?:web|admin))\//)
  }
  for (const file of files(path.join(root,'packages/ui/src'))) assert.doesNotMatch(fs.readFileSync(file,'utf8'), /['"]@\//)
  assert.match(fs.readFileSync(path.join(root,'apps/web/src/lib/auth.ts'),'utf8'), /export \* from '@repo\/session\/auth'/)
  assert.match(fs.readFileSync(path.join(root,'apps/web/src/utils/request.ts'),'utf8'), /export \* from '@repo\/session\/request'/)
})

function webRouter(getToken) {
  const vueRouter = dependency('vue-router')
  return load('apps/web/src/router/index.ts', {
    'vue-router': { ...vueRouter, createWebHistory: () => vueRouter.createMemoryHistory('/') },
    '@/lib/auth': { getAccessToken: getToken },
    '@repo/session/navigation': nav,
  }).default
}
test('web named home selects the workbench child and auth has a default entry', async () => {
  const router = webRouter(() => 'token')
  await router.push({ name: 'app' })
  assert.equal(router.currentRoute.value.path, '/app')
  assert.equal(router.currentRoute.value.matched.at(-1).name, 'app')
  assert.equal(router.currentRoute.value.matched.length, 2)
  await router.push('/auth')
  assert.equal(router.currentRoute.value.name, 'login')
  await router.push('/')
  assert.equal(router.currentRoute.value.name, 'app')
})
test('web guards known resources, preserves string IDs and leaves unknown pages to 404', async () => {
  let token = null
  const router = webRouter(() => token)
  const target = '/app/spaces/2124534695294148609/folders/2124534695294148999?preview=20#details'
  await router.push(target)
  assert.equal(router.currentRoute.value.name, 'login')
  assert.equal(router.currentRoute.value.query.returnTo, target)
  token = 'token'
  await router.push(target)
  assert.equal(router.currentRoute.value.name, 'folders')
  assert.equal(router.currentRoute.value.params.spaceId, '2124534695294148609')
  assert.equal(router.currentRoute.value.params.folderId, '2124534695294148999')
  await router.push('/app/tasks')
  assert.equal(router.currentRoute.value.name, 'tasks')
  token = null
  for (const value of ['/app/missing', '/app/spaces/invalid', '/app/spaces/1/folders/1e3', '/missing', '/404']) {
    await router.push(value)
    assert.equal(router.currentRoute.value.path, value)
    assert.equal(Boolean(router.currentRoute.value.meta.requiresAuth), false)
    assert.equal(router.currentRoute.value.query.returnTo, undefined)
  }
})

test('shared UI exports resolve existing consumers and configured generated imports', () => {
  for (const app of ['web', 'admin']) {
    const resolve = createRequire(path.join(root, 'apps', app, 'package.json')).resolve
    const config = JSON.parse(fs.readFileSync(path.join(root, 'apps', app, 'components.json'), 'utf8'))
    for (const name of ['@repo/ui/button', config.aliases.ui + '/button', config.aliases.ui + '/sheet/SheetDescription.vue', config.aliases.utils]) {
      const target = resolve(name)
      assert.equal(fs.existsSync(target), true, name)
      const relative = path.relative(path.join(root, 'packages/ui/src'), fs.realpathSync(target))
      assert.equal(relative.startsWith('..') || path.isAbsolute(relative), false, name)
    }
  }
})

test('OpenAPI generated TypeScript types stay in sync with docs/api/openapi.json and keep IDs as strings', () => {
  const { execFileSync } = require('node:child_process')
  const generator = path.join(root, 'packages/api-client/scripts/generate-types.mjs')
  const output = execFileSync(process.execPath, [generator, '--check'], { encoding: 'utf8' })
  assert.match(output, /up to date/)
  const dts = fs.readFileSync(path.join(root, 'packages/api-client/src/generated/openapi.d.ts'), 'utf8')
  assert.match(dts, /export type ResourceId = string/)
  assert.match(dts, /DriveModels_Node:\s*\{[\s\S]*?id\?: string[\s\S]*?spaceId\?: string[\s\S]*?parentId\?: string/)
  assert.match(dts, /DriveModels_UploadInput:\s*\{[\s\S]*?spaceId: string[\s\S]*?parentId: string/)
})
