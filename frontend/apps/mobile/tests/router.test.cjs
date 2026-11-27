const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')
const frontendRoot = path.resolve(mobileRoot, '../..')

async function loadTs(filePath) {
  return import(pathToFileURL(filePath).href)
}

test('M003: mobile route guard redirects guests to login with safe redirect and bounces signed-in guests', async () => {
  const guard = await loadTs(path.join(mobileRoot, 'src/router/guard.ts'))

  assert.deepEqual(
    guard.resolveMobileRouteGuard({ fullPath: '/files/12/folders/34?sort=name', meta: { requiresAuth: true } }, false),
    { name: 'mobile-login', query: { redirect: '/m/files/12/folders/34?sort=name' } },
  )

  assert.deepEqual(
    guard.resolveMobileRouteGuard({ fullPath: '/evil-path', meta: { requiresAuth: true } }, false),
    { name: 'mobile-login', query: { redirect: '/m/home' } },
  )

  assert.equal(
    guard.resolveMobileRouteGuard({ fullPath: '/files/12', meta: { requiresAuth: true } }, true),
    true,
  )

  assert.equal(
    guard.resolveMobileRouteGuard(
      { fullPath: '/auth/login', meta: { guestOnly: true }, query: { redirect: '/m/tasks' } },
      true,
    ),
    '/tasks',
  )

  assert.equal(
    guard.resolveMobileRouteGuard(
      { fullPath: '/auth/login', meta: { guestOnly: true }, query: { redirect: 'https://evil.example' } },
      true,
    ),
    '/home',
  )
})

test('M003: mobile router registers all required M003 routes and 404 catch-all', () => {
  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  const expectedSnippets = [
    "path: '/auth'",
    "path: 'login'",
    "path: 'register'",
    "path: 'forgot-password'",
    "path: '/s/:token'",
    "path: 'home'",
    "path: 'files'",
    "path: 'files/:spaceId(\\\\d+)'",
    "path: 'files/:spaceId(\\\\d+)/folders/:folderId(\\\\d+)'",
    "path: 'shared'",
    "path: 'shared/received/:shareId(\\\\d+)'",
    "path: 'me'",
    "path: '/search'",
    "path: '/tasks'",
    "path: '/messages'",
    "path: '/private'",
    "path: '/trash'",
    "path: '/settings'",
    "path: '/admin-overview'",
    "path: '/404'",
    "path: '/:pathMatch(.*)*'",
  ]
  for (const snippet of expectedSnippets) {
    assert.ok(routerSource.includes(snippet), `Expected router to contain: ${snippet}`)
  }
})

test('M003: client-mode detects mobile UA, persists preferences, and maps web <-> mobile paths', async () => {
  const cm = await loadTs(path.join(frontendRoot, 'packages/drive-core/src/client-mode.ts'))

  assert.equal(cm.isMobileUserAgent('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)'), true)
  assert.equal(cm.isMobileUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64)'), false)

  const backing = new Map()
  const storage = {
    getItem: k => backing.get(k) ?? null,
    setItem: (k, v) => backing.set(k, v),
  }
  assert.equal(cm.loadClientModePreference(storage), 'auto')
  cm.saveClientModePreference('desktop', storage)
  assert.equal(cm.loadClientModePreference(storage), 'desktop')

  assert.equal(cm.mapWebPathToMobile('/app/spaces/10/folders/22?view=grid'), '/m/files/10/folders/22?view=grid')
  assert.equal(cm.mapWebPathToMobile('/app/shared'), '/m/shared')
  assert.equal(cm.mapWebPathToMobile('/s/abc123'), '/m/s/abc123')
  assert.equal(cm.mapWebPathToMobile('/app/settings/security'), '/m/settings/security')

  assert.equal(cm.mapMobilePathToWeb('/m/files/10/folders/22?view=grid'), '/app/spaces/10/folders/22?view=grid')
  assert.equal(cm.mapMobilePathToWeb('/m/private'), '/app/settings/private')
  assert.equal(cm.mapMobilePathToWeb('/m/s/abc123'), '/s/abc123')
  assert.equal(cm.mapMobilePathToWeb('/m/admin-overview'), '/admin/')
})
