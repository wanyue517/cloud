const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const dependency = createRequire(path.join(__dirname, '../package.json'))
const ts = dependency('typescript')
function load(file, dependencies, globals) {
  const target = file === 'src/lib/theme.ts' ? '../../packages/ui/src/theme.ts' : file
  const source = fs.readFileSync(path.join(__dirname, '..', target), 'utf8')
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(compiled, { module, exports: module.exports, require: name => dependencies[name] || dependency(name), ...globals })
  return module.exports
}
function storage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key), values }
}
test('resuming an upload skips received chunks and preserves string identifiers', async () => {
  const local = storage({ 'cloudlane.upload.2124534695294148609.0.sample.txt.6.10': '99' })
  const writes = []
  const request = {
    get: async url => { assert.equal(url, '/v1/uploads/99'); return { id: '99', chunkSize: 3, chunkCount: 2, received: [0], status: 'UPLOADING' } },
    put: async (url, form) => { writes.push(url); assert.equal(await form.get('file').text(), 'def') },
    post: async url => { writes.push(url); return '2124534695294148999' },
  }
  const api = load('src/api/drive.ts', { '@/utils/request': { request } }, { localStorage: local, FormData, File })
  const progress = []
  await api.uploadFile('2124534695294148609', '0', new File(['abcdef'], 'sample.txt', { lastModified: 10 }), value => progress.push(value))
  assert.deepEqual(writes, ['/v1/uploads/99/chunks/1', '/v1/uploads/99/complete'])
  assert.equal(local.values.size, 0)
  assert.equal(progress.at(-1), 100)
})
test('failed chunk keeps the upload session for a later retry and never commits incomplete data', async () => {
  const local = storage()
  let completes = 0
  const request = {
    post: async url => { if (url.endsWith('/complete')) completes++; return { id: '88', chunkSize: 3, chunkCount: 2, received: [], status: 'UPLOADING' } },
    put: async () => { throw new Error('offline') },
  }
  const api = load('src/api/drive.ts', { '@/utils/request': { request } }, { localStorage: local, FormData, File })
  await assert.rejects(api.uploadFile('1', '0', new File(['abcdef'], 'sample.txt', { lastModified: 10 }), () => {}), /offline/)
  assert.equal(completes, 0)
  assert.equal([...local.values.values()][0], '88')
})
function themed(saved) {
  const local = storage({ 'cloudlane.theme': saved })
  const properties = new Map(), classes = new Set(), media = { matches: false, addEventListener: (name, fn) => { media.changed = fn } }
  let changed
  const root = { style: { setProperty: (key, value) => properties.set(key, value) }, classList: { toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name) }, dataset: {} }
  const theme = load('src/lib/theme.ts', { vue: { reactive: value => value, watch: (value, fn) => { changed = fn } } }, {
    localStorage: local, window: { matchMedia: () => media }, document: { documentElement: root },
  })
  theme.initTheme()
  return { ...theme, local, properties, classes, root, media, change: () => changed() }
}
test('theme restores appearance and persists updates including system color mode changes', () => {
  const state = themed(JSON.stringify({ mode: 'dark', accent: 'violet', radius: 1, compact: true }))
  assert.ok(state.classes.has('dark'))
  assert.equal(state.properties.get('--primary'), '#7c3aed')
  assert.equal(state.properties.get('--radius'), '1rem')
  assert.equal(state.root.dataset.density, 'compact')
  state.theme.mode = 'system'; state.theme.accent = 'green'; state.change()
  assert.equal(state.classes.has('dark'), false)
  state.media.matches = true; state.media.changed()
  assert.ok(state.classes.has('dark'))
  const saved = JSON.parse(state.local.getItem('cloudlane.theme'))
  assert.equal(saved.mode, 'system'); assert.equal(saved.accent, 'green')
})
test('corrupt and unknown theme preferences fall back to supported settings', () => {
  for (const saved of ['null', 'not-json', '123', JSON.stringify({ mode: 'unknown', accent: '__proto__', radius: 999 })]) {
    const state = themed(saved)
    assert.equal(state.theme.mode, 'system'); assert.equal(state.theme.accent, 'blue'); assert.equal(state.theme.radius, 0.75)
  }
})
