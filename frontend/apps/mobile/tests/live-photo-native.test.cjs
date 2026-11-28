const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function fixture({ failureAt = 0, approve = true, native = true } = {}) {
  const calls = []
  const staged = new Map()
  const originalVideo = Buffer.alloc(900_000, 37)
  const bridge = {
    pick: async () => ({ sessionId: '7dc68cf5-70f5-49c0-9eb8-5afcc2e572fe',
      photo: { path: 'file:///photo', name: 'photo.heic', mimeType: 'image/heic' },
      video: { path: 'file:///video', name: 'paired.mov', mimeType: 'video/quicktime' },
      poster: { path: 'file:///poster', name: 'poster.jpg', mimeType: 'image/jpeg' } }),
    cleanup: async input => { calls.push(['cleanup', input]) },
    save: async input => { calls.push(['save', input]) },
  }
  const Filesystem = {
    writeFile: async input => { staged.set(input.path, Buffer.from(input.data, 'base64')) },
    appendFile: async input => { staged.set(input.path, Buffer.concat([staged.get(input.path), Buffer.from(input.data, 'base64')])) },
  }
  let uploads = 0
  const api = {
    uploadFileDirect: async (space, parent, file) => {
      uploads++
      calls.push(['upload', space, parent, file.type, file.name])
      if (uploads === failureAt) throw new Error('network failure')
      return String(10 + uploads)
    },
    mobileDriveApi: {
      versions: async () => [{ id: '9007199254741009' }],
      pairLivePhoto: async (...args) => { calls.push(['pair', ...args]) },
      livePhotoPart: async (node, kind, version) => {
        calls.push(['download', node.id, kind, version])
        return kind === 'photo' ? new Blob(['photo-original'], { type: 'image/heic' }) : new Blob([originalVideo], { type: 'video/quicktime' })
      },
    },
  }
  const source = fs.readFileSync(path.resolve(__dirname, '../src/native/live-photo.ts'), 'utf8')
  const module = { exports: {} }
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports,
    require: name => {
      if (name === '@capacitor/core') return { Capacitor: { isNativePlatform: () => native, getPlatform: () => 'ios', isPluginAvailable: () => native, convertFileSrc: value => value }, registerPlugin: () => bridge }
      if (name === '@capacitor/filesystem') return { Directory: { Cache: 'CACHE' }, Filesystem }
      if (name === '@/api/drive') return api
      throw new Error(`Unexpected dependency ${name}`)
    },
    fetch: async value => ({ ok: true, blob: async () => new Blob([value]) }),
    File, Blob, Uint8Array, Error, Date, crypto, btoa,
  })
  return { api: module.exports, calls, staged, originalVideo,
    upload: () => module.exports.uploadNativeLivePhoto('10', '20', () => {}, async () => approve) }
}

test('native selection uploads untouched original tracks and a poster before pairing immutable versions', async () => {
  const f = fixture()
  assert.equal(await f.upload(), true)
  assert.deepEqual(f.calls.filter(call => call[0] === 'upload').map(call => call[3]), ['image/heic', 'video/quicktime', 'image/jpeg'])
  assert.deepEqual(f.calls.find(call => call[0] === 'pair'), ['pair', '10', '11', '9007199254741009', '12', '13'])
  assert.equal(f.calls.at(-1)[0], 'cleanup')
})

test('declining the network prompt releases native resources without uploading anything', async () => {
  const f = fixture({ approve: false })
  assert.equal(await f.upload(), false)
  assert.deepEqual(f.calls.map(call => call[0]), ['cleanup'])
})

test('partial upload failure preserves recoverable originals and never publishes an incomplete pair', async () => {
  const f = fixture({ failureAt: 2 })
  await assert.rejects(f.upload(), /1 个原件保留/)
  assert.equal(f.calls.some(call => call[0] === 'pair'), false)
  assert.equal(f.calls.at(-1)[0], 'cleanup')
})

test('iOS save downloads the pinned original pair and stages large videos without base64 chunk corruption', async () => {
  const f = fixture()
  await f.api.saveNativeLivePhoto({ id: '11', currentVersionId: '9007199254741009' })
  assert.deepEqual(f.calls.filter(call => call[0] === 'download'), [
    ['download', '11', 'photo', '9007199254741009'], ['download', '11', 'video', '9007199254741009'],
  ])
  const saved = f.calls.find(call => call[0] === 'save')[1]
  assert.equal(saved.photoName, 'photo.heic')
  assert.deepEqual(f.staged.get(`cloudlane-live/${saved.sessionId}/paired.mov`), f.originalVideo)
  assert.equal(f.calls.at(-1)[0], 'cleanup')
})

test('web callers cannot invoke native Photos operations', async () => {
  const f = fixture({ native: false })
  await assert.rejects(f.upload(), /iOS App/)
  await assert.rejects(f.api.saveNativeLivePhoto({ id: '11' }), /iOS App/)
  assert.equal(f.calls.length, 0)
})
