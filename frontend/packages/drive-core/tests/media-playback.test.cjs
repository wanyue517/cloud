const test = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')
const load = () => import(pathToFileURL(path.resolve(__dirname, '../src/media-playback.ts')).href)

async function fixture(mode = 'PROXY') {
  const { createMediaPlaybackSession } = await load()
  const timers = new Map()
  let next = 0
  const requests = []
  const failures = []
  const session = createMediaPlaybackSession({
    requestAddress: async forceProxy => {
      requests.push(forceProxy)
      return { streamUrl: '/video', expiresInSeconds: 1800,
        mode: forceProxy ? 'PROXY' : mode, proxyFallbackAllowed: mode === 'DIRECT' }
    },
    applyAddress: async () => {},
    getPosition: () => 0,
    isPlaying: () => false,
    onFailure: msg => failures.push(msg),
    now: () => 0,
    schedule: (callback, ms) => { timers.set(++next, { callback, ms }); return next },
    cancel: id => timers.delete(id),
  })
  const watchdog = () => [...timers.entries()].find(([, t]) => t.ms === 60_000)
  await session.start()
  return { session, requests, failures, watchdog }
}

test('startup progress extends idle timeout and metadata clears it', async () => {
  const f = await fixture()
  const before = f.watchdog()[0]
  f.session.progress()
  assert.notEqual(f.watchdog()[0], before)
  f.session.ready()
  assert.equal(f.watchdog(), undefined)
  f.session.progress()
  assert.equal(f.watchdog(), undefined)
  f.session.dispose()
})

test('proxy transport error retries once without forcing transcoding', async () => {
  const f = await fixture()
  await f.session.recover('network')
  assert.deepEqual(f.requests, [false, false])
  assert.equal(f.failures.length, 0)
  await f.session.recover('network')
  assert.equal(f.failures.length, 1)
  f.session.dispose()
})

test('direct playback retains automatic proxy fallback', async () => {
  const f = await fixture('DIRECT')
  await f.session.recover('network')
  assert.deepEqual(f.requests, [false, true])
  assert.equal(f.failures.length, 0)
  f.session.dispose()
})
