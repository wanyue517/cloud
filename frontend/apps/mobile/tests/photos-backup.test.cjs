const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M040: photo backup incremental ledger skips backed-up items and enforces Wi-Fi / charging guards', async () => {
  const {
    buildMediaFingerprint,
    filterUnbackedMedia,
    shouldRunAutoBackup,
  } = await import(pathToFileURL(path.join(mobileRoot, 'src/native/photo-backup.ts')).href)

  const media = [
    { name: 'IMG_001.jpg', size: 1024, lastModified: 100 },
    { name: 'IMG_002.jpg', size: 2048, lastModified: 200 },
  ]
  const ledger = new Set([buildMediaFingerprint(media[0])])
  const unbacked = filterUnbackedMedia(media, ledger)
  assert.equal(unbacked.length, 1)
  assert.equal(unbacked[0].name, 'IMG_002.jpg')

  const baseCfg = {
    enabled: true,
    targetSpaceId: '10',
    targetParentId: '0',
    targetLabel: '个人空间 / 根目录',
    wifiOnly: true,
    chargingOnly: true,
    selectedAlbums: ['Camera'],
    lastRunAt: null,
  }
  assert.equal(shouldRunAutoBackup(baseCfg, { online: true, isWifi: false, isCharging: true }).allowed, false)
  assert.equal(shouldRunAutoBackup(baseCfg, { online: true, isWifi: true, isCharging: false }).allowed, false)
  assert.equal(shouldRunAutoBackup(baseCfg, { online: true, isWifi: true, isCharging: true }).allowed, true)
})

test('M040/M041: upload engine supports instantHit COMPLETED short-circuit without chunk uploads', async () => {
  const { executeChunkedUpload } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/lib/upload-engine.ts')).href
  )

  let chunkCalls = 0
  let lastProgress = 0
  const fileId = await executeChunkedUpload(
    {
      getSession: async () => { throw new Error('not called') },
      createSession: async (payload) => {
        assert.equal(payload.expectedSha256, 'a'.repeat(64))
        return {
          id: 'u1',
          chunkSize: 1024,
          chunkCount: 1,
          received: [0],
          status: 'COMPLETED',
          fileId: 'node-instant-99',
          instantHit: true,
        }
      },
      uploadChunk: async () => { chunkCalls += 1 },
      completeSession: async () => { throw new Error('not called') },
    },
    {
      spaceId: '1',
      parentId: '0',
      file: { name: 'dup.jpg', size: 100, type: 'image/jpeg', lastModified: 1, slice: () => new Blob() },
      expectedSha256: 'a'.repeat(64),
      onProgress: (p) => { lastProgress = p },
    },
  )

  assert.equal(fileId, 'node-instant-99')
  assert.equal(chunkCalls, 0)
  assert.equal(lastProgress, 100)
})

test('M041: groupTimelineByDate groups items by YYYY-MM-DD and formats Today/Yesterday labels', async () => {
  const { groupTimelineByDate } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/lib/timeline.ts')).href
  )

  const refNow = new Date('2026-11-28T10:00:00Z')
  const groups = groupTimelineByDate(
    [
      { id: '1', spaceId: '1', spaceName: 'S', parentId: '0', name: 'a.jpg', size: 1, mimeType: 'image/jpeg', mediaType: 'IMAGE', takenAt: '2026-11-28T09:00:00Z', dateGroup: '2026-11-28', revision: 1 },
      { id: '2', spaceId: '1', spaceName: 'S', parentId: '0', name: 'b.jpg', size: 1, mimeType: 'image/jpeg', mediaType: 'IMAGE', takenAt: '2026-11-28T08:00:00Z', dateGroup: '2026-11-28', revision: 1 },
      { id: '3', spaceId: '1', spaceName: 'S', parentId: '0', name: 'c.mp4', size: 2, mimeType: 'video/mp4', mediaType: 'VIDEO', takenAt: '2026-11-27T12:00:00Z', dateGroup: '2026-11-27', revision: 1 },
      { id: '4', spaceId: '1', spaceName: 'S', parentId: '0', name: 'd.jpg', size: 1, mimeType: 'image/jpeg', mediaType: 'IMAGE', takenAt: '2025-05-01T12:00:00Z', dateGroup: '2025-05-01', revision: 1 },
    ],
    refNow,
  )

  assert.equal(groups.length, 3)
  assert.equal(groups[0].label, '今天')
  assert.equal(groups[0].items.length, 2)
  assert.equal(groups[1].label, '昨天')
  assert.equal(groups[2].label, '2025年5月1日')

  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  assert.match(routerSource, /path:\s*'\/photos'/)
  assert.match(routerSource, /path:\s*'photo-backup'/)
})
