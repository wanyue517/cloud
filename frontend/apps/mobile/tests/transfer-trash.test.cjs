const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const spacesLibUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/spaces.ts')).href

test('M015: filterWritableSpaces excludes PRIVATE and VIEWER spaces and sorts by sortOrder', async () => {
  const { filterWritableSpaces } = await import(spacesLibUrl)

  const spaces = [
    { id: '1', name: 'ReadOnly', kind: 'SHARED', role: 'VIEWER', sortOrder: 1 },
    { id: '2', name: 'Secret', kind: 'PRIVATE', role: 'OWNER', sortOrder: 0 },
    { id: '3', name: 'Team', kind: 'SHARED', role: 'EDITOR', sortOrder: 3 },
    { id: '4', name: 'Mine', kind: 'PERSONAL', role: 'OWNER', sortOrder: 2 },
    { id: '5', name: 'Managed', kind: 'SHARED', role: 'MANAGER', sortOrder: 4 },
  ]

  const result = filterWritableSpaces(spaces).map(s => s.id)
  assert.deepEqual(result, ['4', '3', '5'])
})

test('M015: isFolderPickBlocked prevents selecting excluded folder in same space only', async () => {
  const { isFolderPickBlocked } = await import(spacesLibUrl)

  const excluded = new Set(['100', '101'])
  assert.equal(isFolderPickBlocked('100', '10', '10', excluded), true)
  assert.equal(isFolderPickBlocked('102', '10', '10', excluded), false)
  // Cross-space move with same numeric id is NOT blocked
  assert.equal(isFolderPickBlocked('100', '20', '10', excluded), false)
})

test('M016: router wires /trash to real views/trash/index.vue', () => {
  const routerSrc = fs.readFileSync(path.resolve(__dirname, '../src/router/index.ts'), 'utf8')
  assert.match(routerSrc, /path:\s*'\/trash'[\s\S]*?@\/views\/trash\/index\.vue/)
})
