const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const markdownLibUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/markdown.ts')).href
const pdfWindowUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/pdf-window.ts')).href
const archiveTreeUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/archive-tree.ts')).href
const thumbCacheUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/thumb-cache.ts')).href

test('M022: escapeHtml and highlightCodeSnippet safely encode and highlight code', async () => {
  const { escapeHtml, highlightCodeSnippet } = await import(markdownLibUrl)

  assert.equal(escapeHtml('<script>alert("x")</script>'), '&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;')
  const highlighted = highlightCodeSnippet('const x: number = 42', 'typescript')
  assert.match(highlighted, /hljs-keyword/)
  assert.match(highlighted, /42/)

  // Unknown language safely escapes HTML
  const fallback = highlightCodeSnippet('<img src=x onerror=alert(1)>', 'unknown-lang')
  assert.equal(fallback.includes('<img'), false)
  assert.match(fallback, /&lt;img/)
})

test('M023/M024: PDF visible page window, zoom clamping, and Excel column labels', async () => {
  const { computeVisiblePageWindow, clampZoomScale, excelColumnLabel } = await import(pdfWindowUrl)

  assert.deepEqual(computeVisiblePageWindow(1, 10, 2), [1, 2, 3])
  assert.deepEqual(computeVisiblePageWindow(5, 10, 2), [3, 4, 5, 6, 7])
  assert.deepEqual(computeVisiblePageWindow(10, 10, 2), [8, 9, 10])
  assert.deepEqual(computeVisiblePageWindow(1, 0, 2), [])

  assert.equal(clampZoomScale(0.2), 0.5)
  assert.equal(clampZoomScale(3.5), 2.5)
  assert.equal(clampZoomScale(1.25), 1.25)

  assert.equal(excelColumnLabel(0), 'A')
  assert.equal(excelColumnLabel(25), 'Z')
  assert.equal(excelColumnLabel(26), 'AA')
  assert.equal(excelColumnLabel(27), 'AB')
})

test('M025/M026: archive directory listing, breadcrumbs, and media time formatting', async () => {
  const { listArchiveDirectory, archiveBreadcrumbs, formatMediaTime } = await import(archiveTreeUrl)

  const entries = [
    { path: 'docs/readme.md', name: 'readme.md', directory: false, size: 120, compressedSize: 80, modifiedAt: '' },
    { path: 'docs/sub/spec.pdf', name: 'spec.pdf', directory: false, size: 500, compressedSize: 400, modifiedAt: '' },
    { path: 'index.html', name: 'index.html', directory: false, size: 64, compressedSize: 40, modifiedAt: '' },
  ]

  const root = listArchiveDirectory(entries, '')
  assert.deepEqual(
    root.map(i => ({ name: i.name, directory: i.directory })),
    [
      { name: 'docs', directory: true },
      { name: 'index.html', directory: false },
    ],
  )

  const docs = listArchiveDirectory(entries, 'docs')
  assert.deepEqual(
    docs.map(i => ({ name: i.name, directory: i.directory })),
    [
      { name: 'sub', directory: true },
      { name: 'readme.md', directory: false },
    ],
  )

  assert.deepEqual(archiveBreadcrumbs('docs/sub'), [
    { label: 'docs', path: 'docs' },
    { label: 'sub', path: 'docs/sub' },
  ])

  assert.equal(formatMediaTime(0), '00:00')
  assert.equal(formatMediaTime(125), '02:05')
  assert.equal(formatMediaTime(3661), '1:01:01')
})

test('Thumb parity: supportsFileThumbnail matches PC coverage for images, videos, PDF, Office, audio, and code', async () => {
  const { supportsFileThumbnail } = await import(thumbCacheUrl)

  assert.equal(supportsFileThumbnail({ nodeType: 'FOLDER', name: 'photos' }), false)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'a.jpg', mimeType: 'image/jpeg' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'clip.mp4', mimeType: 'video/mp4' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'spec.pdf', mimeType: 'application/pdf' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'deck.pptx' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'sheet.xlsx' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'song.mp3' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'app.ts' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'README.md' }), true)
  assert.equal(supportsFileThumbnail({ nodeType: 'FILE', name: 'binary.exe' }), false)
})
