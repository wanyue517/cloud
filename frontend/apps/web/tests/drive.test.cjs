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
function themed(saved, blocked = false) {
  const local = storage({ 'cloudlane.theme': saved })
  if (blocked) {
    local.getItem = () => { throw new Error('storage disabled') }
    local.setItem = () => { throw new Error('quota exceeded') }
  }
  const counts = { watches: 0, media: 0 }, events = {}
  const vue = dependency('vue')
  const properties = new Map(), classes = new Set(), media = { matches: false, addEventListener: (name, fn) => { counts.media++; media.changed = fn } }
  let changed
  const root = { style: { setProperty: (key, value) => properties.set(key, value) }, classList: { toggle: (name, enabled) => enabled ? classes.add(name) : classes.delete(name) }, dataset: {} }
  const theme = load('src/lib/theme.ts', { vue: { reactive: vue.reactive, watch: (value, fn, options) => { counts.watches++; changed = fn; return vue.watch(value, fn, options) } } }, {
    localStorage: local, window: { matchMedia: () => media, addEventListener: (name, fn) => { events[name] = fn } }, document: { documentElement: root },
  })
  theme.initTheme()
  return { ...theme, local, properties, classes, root, media, counts, events, change: () => changed() }
}
test('theme restores appearance and persists updates including system color mode changes', () => {
  const state = themed(JSON.stringify({ mode: 'dark', accent: 'violet', radius: 1, compact: true, layoutStyle: 'classic' }))
  assert.ok(state.classes.has('dark'))
  assert.equal(state.properties.get('--primary'), '#a78bfa')
  assert.equal(state.properties.get('--radius'), '1rem')
  assert.equal(state.root.dataset.density, 'compact')
  assert.equal(state.root.dataset.layout, 'classic')
  state.theme.mode = 'system'; state.theme.accent = 'green'; state.theme.layoutStyle = 'islands'; state.change()
  assert.equal(state.classes.has('dark'), false)
  assert.equal(state.root.dataset.layout, 'islands')
  state.media.matches = true; state.media.changed()
  assert.ok(state.classes.has('dark'))
  const saved = JSON.parse(state.local.getItem('cloudlane.theme'))
  assert.equal(saved.mode, 'system'); assert.equal(saved.accent, 'green'); assert.equal(saved.layoutStyle, 'islands')
})
test('corrupt and unknown theme preferences fall back to supported settings', () => {
  for (const saved of ['null', 'not-json', '123', JSON.stringify({ mode: 'unknown', accent: '__proto__', radius: 999, layoutStyle: 'floating' })]) {
    const state = themed(saved)
    assert.equal(state.theme.mode, 'system'); assert.equal(state.theme.accent, 'blue'); assert.equal(state.theme.radius, 0.75); assert.equal(state.theme.layoutStyle, 'islands')
  }
})

test('theme stays usable when browser storage is unavailable', () => {
  const state = themed(null, true)
  assert.equal(state.theme.mode, 'system')
  assert.doesNotThrow(() => { state.theme.mode = 'dark'; state.theme.accent = 'orange' })
  assert.ok(state.classes.has('dark'))
  assert.equal(state.properties.get('--primary'), '#fb923c')
  assert.equal(state.properties.get('--primary-foreground'), '#101114')
})

test('theme initializes once and validates cross-tab changes without writing them back', () => {
  const initial = JSON.stringify({ mode: 'light', accent: 'blue', radius: 0.75, compact: false })
  const state = themed(initial)
  state.initTheme()
  assert.equal(state.counts.watches, 1)
  assert.equal(state.counts.media, 1)
  state.events.storage({ key: 'cloudlane.theme', newValue: JSON.stringify({ mode: 'dark', accent: 'green', radius: 0, compact: true }) })
  assert.equal(state.properties.get('--primary'), '#4ade80')
  assert.equal(state.properties.get('--radius'), '0rem')
  assert.equal(state.root.dataset.density, 'compact')
  assert.equal(state.local.getItem('cloudlane.theme'), initial)
  state.events.storage({ key: 'other', newValue: '{}' })
  assert.equal(state.theme.mode, 'dark')
  state.events.storage({ key: 'cloudlane.theme', newValue: 'broken JSON' })
  assert.equal(state.theme.mode, 'system')
  assert.equal(state.theme.accent, 'blue')
  state.events.storage({ key: null, newValue: null })
  assert.equal(state.classes.has('dark'), false)
})

test('all theme accent button pairs meet normal text contrast in both modes', () => {
  const state = themed('{}')
  function luminance(hex) {
    const rgb = hex.match(/[a-f\d]{2}/gi).map(v => parseInt(v, 16) / 255).map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
  }
  for (const mode of ['light', 'dark']) for (const accent of Object.keys(state.accentColors)) {
    state.theme.mode = mode; state.theme.accent = accent
    const a = luminance(state.properties.get('--primary')), b = luminance(state.properties.get('--primary-foreground'))
    assert.ok((Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) >= 4.5, mode + ':' + accent)
  }
})

test('layout preferences persist, clamp tree width, and recover from corrupt or blocked storage', () => {
  const layout = load('src/lib/layout.ts', {}, {})
  const local = storage()
  assert.deepEqual({ ...layout.loadLayoutPreferences(local) }, { sidebarCollapsed: false, treeCollapsed: false, treeWidth: 260 })
  assert.equal(layout.clampTreeWidth(120), 200)
  assert.equal(layout.clampTreeWidth(999), 400)
  assert.equal(layout.clampTreeWidth(Number.NaN), 260)
  const saved = layout.saveLayoutPreferences({ sidebarCollapsed: true, treeCollapsed: false, treeWidth: 318.6 }, local)
  assert.deepEqual({ ...saved }, { sidebarCollapsed: true, treeCollapsed: false, treeWidth: 319 })
  assert.deepEqual({ ...layout.loadLayoutPreferences(local) }, { sidebarCollapsed: true, treeCollapsed: false, treeWidth: 319 })
  for (const bad of ['not-json', 'null', '[]', JSON.stringify({ sidebarCollapsed: 'yes', treeWidth: 'wide' })]) {
    assert.deepEqual({ ...layout.loadLayoutPreferences(storage({ 'cloudlane.layout': bad })) }, { sidebarCollapsed: false, treeCollapsed: false, treeWidth: 260 })
  }
  const blocked = { getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('quota') } }
  assert.deepEqual({ ...layout.loadLayoutPreferences(blocked) }, { sidebarCollapsed: false, treeCollapsed: false, treeWidth: 260 })
  assert.doesNotThrow(() => layout.saveLayoutPreferences({ sidebarCollapsed: true, treeCollapsed: true, treeWidth: 280 }, blocked))
})

test('viewport tiers classify desktop, tablet, and mobile breakpoints accurately', () => {
  const layout = load('src/lib/layout.ts', {}, {})
  assert.equal(layout.resolveViewportTier(1440), 'desktop')
  assert.equal(layout.resolveViewportTier(1025), 'desktop')
  assert.equal(layout.resolveViewportTier(1024), 'tablet')
  assert.equal(layout.resolveViewportTier(761), 'tablet')
  assert.equal(layout.resolveViewportTier(760), 'mobile')
  assert.equal(layout.resolveViewportTier(375), 'mobile')
})

test('tree flattens expanded nodes, virtualizes large lists, partitions breadcrumbs, and selects main filename', () => {
  const tree = load('src/lib/tree.ts', {}, {})
  const childrenByParent = new Map([
    ['0', [
      { id: '1', parentId: '0', name: '项目', hasChildren: true },
      { id: '2', parentId: '0', name: '归档', hasChildren: false },
    ]],
    ['1', [
      { id: '11', parentId: '1', name: '前端', hasChildren: false },
      { id: '12', parentId: '1', name: '后端', hasChildren: true },
    ]],
  ])
  const flat = tree.flattenVisibleTree({
    childrenByParent,
    expandedIds: new Set(['1', '12']),
    loadingParents: new Set(['12']),
    errorByParent: new Map(),
    hasMoreByParent: new Map([['1', true]]),
  })
  assert.deepEqual(
    [...flat].map(i => `${i.kind}:${i.depth}:${i.kind === 'node' ? i.node.id : i.parentId}`),
    ['node:0:1', 'node:1:11', 'node:1:12', 'loading:2:12', 'more:1:1', 'node:0:2'],
  )

  const win = tree.computeVirtualWindow(100000, 3200, 320, 32, 4)
  assert.equal(win.totalHeight, 3200000)
  assert.equal(win.startIndex, 96)
  assert.equal(win.endIndex, 114)
  assert.equal(win.offsetY, 96 * 32)

  const crumbs = tree.partitionBreadcrumbs(['A', 'B', 'C', 'D', 'E'], 2)
  assert.deepEqual([...crumbs.collapsed], ['A', 'B', 'C'])
  assert.deepEqual([...crumbs.tail], ['D', 'E'])
  const shortCrumbs = tree.partitionBreadcrumbs(['A', 'B', 'C'], 2)
  assert.deepEqual([...shortCrumbs.collapsed], [])
  assert.deepEqual([...shortCrumbs.tail], ['A', 'B', 'C'])

  assert.deepEqual([...tree.mainFilenameRange('report.final.pdf', false)], [0, 12])
  assert.deepEqual([...tree.mainFilenameRange('.env', false)], [0, 4])
  assert.deepEqual([...tree.mainFilenameRange('v1.0.folder', true)], [0, 11])
})

test('upload queue supports exponential backoff retry, pause, resume, and cancel transitions', async () => {
  const queueMod = load('src/lib/upload-queue.ts', {}, { AbortController, setTimeout, clearTimeout })
  const delays = []
  let tries = 0
  const result = await queueMod.withExponentialBackoff(
    async () => {
      tries++
      if (tries < 3) throw new Error('transient')
      return 'ok'
    },
    { maxRetries: 3, baseDelayMs: 100, sleep: async ms => { delays.push(ms) } },
  )
  assert.equal(result, 'ok')
  assert.deepEqual(delays, [100, 200])

  let failFirst = true
  const snapshots = []
  const queue = queueMod.createUploadQueue(
    async (item, onProgress) => {
      onProgress(50)
      if (failFirst) { failFirst = false; throw new Error('network drop') }
      onProgress(100)
    },
    items => snapshots.push(items.map(i => `${i.id}:${i.status}:${i.percent}`)),
  )
  queue.enqueue([{ file: { name: 'a.bin' }, name: 'a.bin', spaceId: '1', parentId: '0' }])
  await new Promise(r => setTimeout(r, 20))
  assert.equal(queue.snapshot()[0].status, 'error')
  queue.resume(queue.snapshot()[0].id)
  await new Promise(r => setTimeout(r, 20))
  assert.equal(queue.snapshot()[0].status, 'completed')
  assert.equal(queue.snapshot()[0].percent, 100)
  queue.clearFinished()
  assert.equal(queue.snapshot().length, 0)
})
