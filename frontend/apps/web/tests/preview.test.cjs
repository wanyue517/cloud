const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')

const root = path.resolve(__dirname, '../../..')
const dependency = createRequire(path.join(root, 'apps/web/package.json'))
const ts = dependency('typescript')

function loadTs(relative, globals = {}) {
  const source = fs.readFileSync(path.join(root, relative), 'utf8')
  const module = { exports: {} }
  const localRequire = id => {
    if (id.startsWith('@repo/drive-core/')) {
      return loadTs(`packages/drive-core/src/${id.slice('@repo/drive-core/'.length)}.ts`)
    }
    return dependency(id)
  }
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      esModuleInterop: true,
    },
  }).outputText, { module, exports: module.exports, require: localRequire, Error, console, ...globals })
  return module.exports
}

const preview = loadTs('apps/web/src/lib/preview-registry.ts')

test('a thumbnail network failure is fetched once even when Cache Storage is available', async () => {
  const thumbnails = loadTs('apps/web/src/lib/thumb-cache.ts', {
    window: { caches: { open: async () => ({ match: async () => undefined }) } },
  })
  let attempts = 0
  await assert.rejects(thumbnails.getOrFetchThumbnail('video', async () => {
    attempts++
    throw new Error('network unavailable')
  }))
  assert.equal(attempts, 1)
})

test('preview registry dispatches common file extensions and MIME types accurately', () => {
  assert.equal(preview.resolvePreviewKind('photo.png', 'image/png'), 'image')
  assert.equal(preview.resolvePreviewKind('scan.PDF', 'application/octet-stream'), 'pdf')
  assert.equal(preview.resolvePreviewKind('spec.docx', 'application/octet-stream'), 'word')
  assert.equal(preview.resolvePreviewKind('slides.pptx', 'application/octet-stream'), 'ppt')
  assert.equal(preview.resolvePreviewKind('budget.xlsx', 'application/octet-stream'), 'excel')
  assert.equal(preview.resolvePreviewKind('data.csv', 'text/plain'), 'excel')
  assert.equal(preview.resolvePreviewKind('release.tar.gz', 'application/gzip'), 'archive')
  assert.equal(preview.resolvePreviewKind('bundle.zip', 'application/zip'), 'archive')
  assert.equal(preview.resolvePreviewKind('README.md', 'text/plain'), 'markdown')
  assert.equal(preview.resolvePreviewKind('App.vue', 'text/plain'), 'code')
  assert.equal(preview.resolvePreviewKind('Main.java', 'text/plain'), 'code')
  assert.equal(preview.resolvePreviewKind('server.log', 'text/plain'), 'text')
  assert.equal(preview.resolvePreviewKind('song.mp3', 'audio/mpeg'), 'audio')
  assert.equal(preview.resolvePreviewKind('demo.mp4', 'video/mp4'), 'video')
  assert.equal(preview.resolvePreviewKind('archive.bin', 'application/octet-stream'), 'unsupported')

  assert.equal(preview.detectCodeLanguage('index.ts'), 'typescript')
  assert.equal(preview.detectCodeLanguage('Dockerfile'), 'dockerfile')
  assert.equal(preview.detectCodeLanguage('notes.txt'), 'plaintext')
})

test('markdown TOC extraction ignores fenced code blocks and generates stable heading IDs', () => {
  const md = [
    '# 系统设计',
    '```markdown',
    '# 代码块内的假标题',
    '```',
    '## 架构概览',
    '### 1. 存储层',
  ].join('\n')
  const toc = preview.extractMarkdownToc(md).map(h => ({ ...h }))
  assert.equal(toc.length, 3)
  assert.equal(toc[0].level, 1)
  assert.equal(toc[0].text, '系统设计')
  assert.equal(toc[1].level, 2)
  assert.equal(toc[1].text, '架构概览')
  assert.equal(toc[2].level, 3)
  assert.equal(toc[2].text, '1. 存储层')
  assert.notEqual(toc[0].id, toc[1].id)
})
