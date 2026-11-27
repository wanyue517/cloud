const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(mobileRoot, '../../..')

test('M042: offline cache strictly forbids PRIVATE spaces and hidden files and reconciles revisions', async () => {
  const {
    assertOfflineCacheAllowed,
    reconcileOfflineEntries,
  } = await import(pathToFileURL(path.join(mobileRoot, 'src/native/offline-cache.ts')).href)

  assert.throws(() => assertOfflineCacheAllowed('PRIVATE', false), /私密空间/)
  assert.throws(() => assertOfflineCacheAllowed('PERSONAL', true), /隐藏文件/)
  assert.doesNotThrow(() => assertOfflineCacheAllowed('PERSONAL', false))
  assert.doesNotThrow(() => assertOfflineCacheAllowed('SHARED', false))

  const entries = [
    { nodeId: '1', spaceId: '10', name: 'same.pdf', size: 100, mimeType: 'application/pdf', revision: 2, currentVersionId: 'v2', localPath: 'p1', cachedAt: '2026-11-28T00:00:00Z' },
    { nodeId: '2', spaceId: '10', name: 'updated.pdf', size: 100, mimeType: 'application/pdf', revision: 1, currentVersionId: 'v1', localPath: 'p2', cachedAt: '2026-11-28T00:00:00Z' },
    { nodeId: '3', spaceId: '10', name: 'deleted.pdf', size: 100, mimeType: 'application/pdf', revision: 1, currentVersionId: 'v1', localPath: 'p3', cachedAt: '2026-11-28T00:00:00Z' },
  ]
  const { kept, removedIds, updatedIds } = reconcileOfflineEntries(entries, {
    '1': { revision: 2 },
    '2': { revision: 4 },
    '3': null,
  })
  assert.deepEqual(removedIds, ['3'])
  assert.deepEqual(updatedIds, ['2'])
  assert.equal(kept.length, 2)
  assert.equal(kept.find(e => e.nodeId === '2').hasUpdate, true)
  assert.equal(kept.find(e => e.nodeId === '1').hasUpdate, false)
})

test('M043: buildPdfFromJpegPages generates a valid multi-page PDF-1.4 document and AndroidManifest declares SEND intents', async () => {
  const { buildPdfFromJpegPages } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/native/share-and-scan.ts')).href
  )

  const fakeJpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0xff, 0xd9])
  const pdf = buildPdfFromJpegPages([
    { jpegBytes: fakeJpeg, width: 800, height: 1200 },
    { jpegBytes: fakeJpeg, width: 600, height: 900 },
  ])
  const text = new TextDecoder('latin1').decode(pdf)
  assert.ok(text.startsWith('%PDF-1.4\n'))
  assert.match(text, /\/Count 2/)
  assert.match(text, /\/Filter \/DCTDecode/)
  assert.ok(text.trimEnd().endsWith('%%EOF'))

  const manifest = fs.readFileSync(
    path.join(mobileRoot, 'android/app/src/main/AndroidManifest.xml'),
    'utf8',
  )
  assert.match(manifest, /android\.intent\.action\.SEND/)
  assert.match(manifest, /android\.intent\.action\.SEND_MULTIPLE/)
})

test('M044: release.ps1 supports android-apk target and mobile release guide exists', () => {
  const releasePs1 = fs.readFileSync(path.join(repoRoot, 'scripts/release.ps1'), 'utf8')
  assert.match(releasePs1, /android-apk/)
  assert.match(releasePs1, /cap sync android/)
  assert.match(releasePs1, /assembleRelease/)

  const guide = fs.readFileSync(path.join(repoRoot, 'docs/ops/移动端构建与发布指南.md'), 'utf8')
  assert.match(guide, /keystore\.properties/)
  assert.match(guide, /Share Extension/)
})
