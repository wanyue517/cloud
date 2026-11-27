const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const shareHelpersUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/share-helpers.ts')).href

test('M027: truncateSharedBreadcrumbs strictly hides ancestors above authorized root', async () => {
  const { truncateSharedBreadcrumbs } = await import(shareHelpersUrl)

  const ancestors = [
    { id: '1', name: 'SecretParent' },
    { id: '2', name: 'TeamRoot' },
    { id: '3', name: 'SubA' },
    { id: '4', name: 'SubB' },
  ]

  const truncated = truncateSharedBreadcrumbs(ancestors, '2')
  assert.deepEqual(
    truncated.map(a => a.id),
    ['2', '3', '4'],
  )
  // If authorized root is missing, return empty (never leak full path)
  assert.deepEqual(truncateSharedBreadcrumbs(ancestors, '999'), [])
})

test('M028: share code generation, WeChat share text, and link status resolution', async () => {
  const {
    generateExtractCode,
    buildMobileShareUrl,
    buildWechatShareText,
    resolveShareLinkStatus,
  } = await import(shareHelpersUrl)

  const code = generateExtractCode(4)
  assert.equal(code.length, 4)
  assert.match(code, /^[A-Z0-9]{4}$/)

  const url = buildMobileShareUrl('https://cloud.example.com/', 'tok123')
  assert.equal(url, 'https://cloud.example.com/m/s/tok123')

  const text = buildWechatShareText({
    name: '季度报告.pdf',
    url,
    password: 'AB12',
    expiresAt: '2026-12-01 12:00',
  })
  assert.match(text, /季度报告\.pdf/)
  assert.match(text, /AB12/)
  assert.match(text, /https:\/\/cloud\.example\.com\/m\/s\/tok123/)

  const now = Date.parse('2026-11-28T00:00:00Z')
  assert.equal(resolveShareLinkStatus({ disabledAt: '2026-11-27T00:00:00Z' }, now), 'DISABLED')
  assert.equal(resolveShareLinkStatus({ expiresAt: '2026-11-27T00:00:00Z' }, now), 'EXPIRED')
  assert.equal(resolveShareLinkStatus({ maxDownloads: 5, downloadCount: 5 }, now), 'LIMIT_REACHED')
  assert.equal(
    resolveShareLinkStatus({ expiresAt: '2026-12-30T00:00:00Z', maxDownloads: 5, downloadCount: 2 }, now),
    'ACTIVE',
  )
})

test('M027/M029: router wires /shared, /shared/received/:shareId, and /s/:token to real views', () => {
  const routerSrc = fs.readFileSync(path.resolve(__dirname, '../src/router/index.ts'), 'utf8')
  assert.match(routerSrc, /path:\s*'shared'[\s\S]*?@\/views\/shared\/index\.vue/)
  assert.match(routerSrc, /path:\s*'shared\/received\/:shareId\(\\\\d\+\)'[\s\S]*?@\/views\/shared\/received\.vue/)
  assert.match(routerSrc, /path:\s*'\/s\/:token'[\s\S]*?@\/views\/share\/public\.vue/)
})
