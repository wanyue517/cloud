const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M012: space filtering excludes PRIVATE spaces, sorts by sortOrder, and checks permissions', async () => {
  const sp = await import(pathToFileURL(path.join(mobileRoot, 'src/lib/spaces.ts')).href)

  const sample = [
    { id: '1', name: '私密保险箱', kind: 'PRIVATE', role: 'OWNER', sortOrder: 0 },
    { id: '2', name: '项目共享', kind: 'SHARED', role: 'EDITOR', sortOrder: 20 },
    { id: '3', name: '个人空间', kind: 'PERSONAL', role: 'OWNER', sortOrder: 10 },
    { id: '4', name: '团队库', kind: 'SHARED', role: 'MANAGER', sortOrder: 15 },
  ]

  const visible = sp.filterVisibleSpaces(sample)
  assert.deepEqual(
    visible.map(s => s.id),
    ['3', '4', '2'],
  )

  assert.equal(sp.resolveActiveSpace(visible, '2')?.id, '2')
  assert.equal(sp.resolveActiveSpace(visible, '999', '4')?.id, '4')
  assert.equal(sp.resolveActiveSpace(visible)?.id, '3')

  assert.equal(sp.canEditSpace(sample[2]), true)
  assert.equal(sp.canEditSpace(sample[3]), true)
  assert.equal(sp.canEditSpace(sample[1]), false)
  assert.equal(sp.canDeleteSpace(sample[2]), true)
  assert.equal(sp.canDeleteSpace(sample[3]), false)
})

test('M011/M012: router wires /home and /files routes to real views', () => {
  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  assert.match(routerSource, /@\/views\/home\/index\.vue/)
  assert.match(routerSource, /@\/views\/files\/index\.vue/)
})
