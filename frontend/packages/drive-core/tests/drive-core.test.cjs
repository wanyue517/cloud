const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const coreRoot = path.resolve(__dirname, '..')
const sessionRoot = path.resolve(coreRoot, '../session')

async function loadTs(filePath) {
  return import(pathToFileURL(filePath).href)
}

test('M002: format helpers format bytes, dates, relative time, and durations', async () => {
  const fmt = await loadTs(path.join(coreRoot, 'src/format.ts'))
  assert.equal(fmt.formatBytes(0), '0 B')
  assert.equal(fmt.formatBytes(1024), '1.0 KB')
  assert.equal(fmt.formatBytes(5 * 1024 * 1024), '5.0 MB')
  assert.equal(fmt.formatDuration(65), '01:05')
  assert.equal(fmt.formatDuration(3661), '1:01:01')
  const now = new Date('2026-11-27T12:00:00Z')
  assert.equal(fmt.formatRelativeTime(new Date('2026-11-27T11:59:30Z'), now), '刚刚')
  assert.equal(fmt.formatRelativeTime(new Date('2026-11-27T11:55:00Z'), now), '5 分钟前')
  assert.equal(fmt.formatRelativeTime(new Date('2026-11-27T09:00:00Z'), now), '3 小时前')
})

test('M002: categories and role helpers return labels and permission flags', async () => {
  const cat = await loadTs(path.join(coreRoot, 'src/categories.ts'))
  assert.equal(cat.categoryLabel('IMAGE'), '图片')
  assert.equal(cat.categoryLabel('UNKNOWN'), 'UNKNOWN')
  assert.equal(cat.roleLabel('OWNER'), '所有者')
  assert.equal(cat.canWriteInRole('EDITOR'), true)
  assert.equal(cat.canWriteInRole('VIEWER'), false)
  assert.equal(cat.canManageMembersInRole('MANAGER'), true)
  assert.equal(cat.canManageMembersInRole('EDITOR'), false)
})

test('M002: path helpers partition breadcrumbs and compute main filename selection', async () => {
  const p = await loadTs(path.join(coreRoot, 'src/path.ts'))
  assert.deepEqual(p.mainFilenameRange('report.final.pdf', false), [0, 12])
  assert.deepEqual(p.mainFilenameRange('.gitignore', false), [0, 10])
  assert.deepEqual(p.mainFilenameRange('v1.0', true), [0, 4])
  const part = p.partitionBreadcrumbs(['a', 'b', 'c', 'd'], 2)
  assert.deepEqual(part.collapsed, ['a', 'b'])
  assert.deepEqual(part.tail, ['c', 'd'])
})

test('M002: preview-registry resolves kinds and extracts markdown headings', async () => {
  const pr = await loadTs(path.join(coreRoot, 'src/preview-registry.ts'))
  assert.equal(pr.resolvePreviewKind('photo.webp'), 'image')
  assert.equal(pr.resolvePreviewKind('doc.pdf'), 'pdf')
  assert.equal(pr.resolvePreviewKind('deck.pptx'), 'ppt')
  assert.equal(pr.resolvePreviewKind('sheet.xlsx'), 'excel')
  assert.equal(pr.resolvePreviewKind('archive.tar.gz'), 'archive')
  assert.equal(pr.resolvePreviewKind('clip.mp4'), 'video')
  assert.equal(pr.resolvePreviewKind('README.md'), 'markdown')
  assert.equal(pr.resolvePreviewKind('Main.java'), 'code')
  const toc = pr.extractMarkdownToc('# Title\n```md\n## Ignored\n```\n## Section Two')
  assert.equal(toc.length, 2)
  assert.equal(toc[1].text, 'Section Two')
})

test('M002: upload-queue executes, pauses, resumes, and retries with backoff', async () => {
  const uq = await loadTs(path.join(coreRoot, 'src/upload-queue.ts'))
  let tries = 0
  const result = await uq.withExponentialBackoff(
    async () => {
      tries++
      if (tries < 3) throw new Error('transient')
      return 'ok'
    },
    { maxRetries: 3, baseDelayMs: 1, sleep: async () => {} },
  )
  assert.equal(result, 'ok')
  assert.equal(tries, 3)

  const snapshots = []
  const queue = uq.createUploadQueue(async (_item, onProgress) => {
    onProgress(50)
    onProgress(100)
  }, snap => snapshots.push(snap))
  queue.enqueue([{ file: new Blob(['abc']), name: 'a.txt', spaceId: '1', parentId: '0' }])
  await new Promise(resolve => setTimeout(resolve, 20))
  const finalSnap = queue.snapshot()
  assert.equal(finalSnap[0].status, 'completed')
  assert.equal(finalSnap[0].percent, 100)
})

test('M002: errors helper classifies 423/401/409 and normalizes messages', async () => {
  const errs = await loadTs(path.join(coreRoot, 'src/errors.ts'))
  assert.equal(errs.isLockedError({ httpStatus: 423 }), true)
  assert.equal(errs.isUnauthorizedError({ code: 401 }), true)
  assert.equal(errs.isConflictError({ httpStatus: 409 }), true)
  assert.equal(errs.normalizeErrorMessage(new Error('boom')), 'boom')
  assert.equal(errs.normalizeErrorMessage(null, 'fallback'), 'fallback')
})

test('M002: @repo/session supports custom TokenStorage and safeMobileReturnPath', async () => {
  const auth = await loadTs(path.join(sessionRoot, 'auth.ts'))
  const nav = await loadTs(path.join(sessionRoot, 'navigation.ts'))

  const backing = new Map()
  auth.configureTokenStorage({
    getItem: k => backing.get(k) ?? null,
    setItem: (k, v) => backing.set(k, v),
    removeItem: k => backing.delete(k),
  })
  auth.saveAccessToken('acc-123')
  auth.saveRefreshToken('ref-456')
  assert.equal(auth.getAccessToken(), 'acc-123')
  assert.equal(auth.getRefreshToken(), 'ref-456')
  auth.clearAccessToken()
  auth.clearRefreshToken()
  assert.equal(auth.getAccessToken(), null)
  assert.equal(auth.getRefreshToken(), null)
  auth.resetTokenStorage()

  assert.equal(nav.safeMobileReturnPath('/m/files/12/folders/34?sort=name'), '/m/files/12/folders/34?sort=name')
  assert.equal(nav.safeMobileReturnPath('/m/shared'), '/m/shared')
  assert.equal(nav.safeMobileReturnPath('https://evil.com'), '/m/home')
  assert.equal(nav.safeMobileReturnPath('/m/unknown'), '/m/home')
  assert.equal(nav.mobileLoginUrl('/m/tasks'), '/m/auth/login?redirect=%2Fm%2Ftasks')
})
