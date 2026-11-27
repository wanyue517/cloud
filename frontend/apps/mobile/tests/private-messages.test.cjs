const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const privateVaultUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/private-vault.ts')).href

test('M032: handleVaultLockedError intercepts 423 Locked and clears tokens', async () => {
  const { handleVaultLockedError } = await import(privateVaultUrl)

  let cleared = 0
  let locked = 0

  assert.equal(
    handleVaultLockedError(
      { httpStatus: 423, message: 'Locked' },
      () => { cleared += 1 },
      () => { locked += 1 },
    ),
    true,
  )
  assert.equal(cleared, 1)
  assert.equal(locked, 1)

  assert.equal(
    handleVaultLockedError(
      { httpStatus: 500, message: 'Server error' },
      () => { cleared += 1 },
      () => { locked += 1 },
    ),
    false,
  )
  assert.equal(cleared, 1)
  assert.equal(locked, 1)
})

test('M033: mapMessageActionUrlToMobile maps PC routes to mobile equivalents', async () => {
  const { mapMessageActionUrlToMobile } = await import(privateVaultUrl)

  assert.equal(mapMessageActionUrlToMobile('/app'), '/home')
  assert.equal(mapMessageActionUrlToMobile('/app/shared'), '/shared')
  assert.equal(mapMessageActionUrlToMobile('/app/shared/received/42'), '/shared/received/42')
  assert.equal(mapMessageActionUrlToMobile('/app/spaces/10'), '/files/10')
  assert.equal(mapMessageActionUrlToMobile('/app/spaces/10/folders/99'), '/files/10/folders/99')
  assert.equal(mapMessageActionUrlToMobile('/app/trash'), '/trash')
  assert.equal(mapMessageActionUrlToMobile('/m/messages'), '/messages')
  assert.equal(mapMessageActionUrlToMobile(''), '')
})

test('M032/M033: router wires /private, /messages, and /admin-overview to real views', () => {
  const routerSrc = fs.readFileSync(path.resolve(__dirname, '../src/router/index.ts'), 'utf8')
  assert.match(routerSrc, /path:\s*'\/private'[\s\S]*?@\/views\/private\/index\.vue/)
  assert.match(routerSrc, /path:\s*'\/messages'[\s\S]*?@\/views\/messages\/index\.vue/)
  assert.match(routerSrc, /path:\s*'\/admin-overview'[\s\S]*?@\/views\/admin\/overview\.vue/)
})
