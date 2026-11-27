const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M009: quota computation flags normal, warning (>=85%), and danger (>=95%) states', async () => {
  const { computeQuotaState } = await import(pathToFileURL(path.join(mobileRoot, 'src/lib/quota.ts')).href)

  assert.deepEqual(computeQuotaState(0, 1000), { percent: 0, level: 'normal' })
  assert.deepEqual(computeQuotaState(500, 1000), { percent: 50, level: 'normal' })
  assert.deepEqual(computeQuotaState(850, 1000), { percent: 85, level: 'warning' })
  assert.deepEqual(computeQuotaState(950, 1000), { percent: 95, level: 'danger' })
  assert.deepEqual(computeQuotaState(1200, 1000), { percent: 100, level: 'danger' })
  assert.deepEqual(computeQuotaState(100, 0), { percent: 0, level: 'normal' })
})

test('M009/M010: router wires /me and all 5 /settings/* pages to real views', () => {
  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  const expected = [
    '@/views/me/index.vue',
    '@/views/settings/profile.vue',
    '@/views/settings/security.vue',
    '@/views/settings/appearance.vue',
    '@/views/settings/files.vue',
    '@/views/settings/network.vue',
  ]
  for (const target of expected) {
    assert.ok(routerSource.includes(target), `Expected router to reference ${target}`)
  }
})
