const test = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')
const load = () => import(pathToFileURL(path.resolve(__dirname, '../src/media-playback.ts')).href)

async function fixture(mode = 'PROXY', onStartupTimeout) {
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
    onStartupTimeout,
    now: () => 0,
    schedule: (callback, ms) => { timers.set(++next, { callback, ms }); return next },
    cancel: id => timers.delete(id),
  })
  const watchdog = () => [...timers.entries()].find(([, t]) => t.ms === 60_000)
  await session.start()
  return { session, requests, failures, watchdog, timers }
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

test('download progress cannot postpone the absolute startup deadline', async () => {
  const f = await fixture('DIRECT')
  const deadline = [...f.timers.entries()].find(([, t]) => t.ms === 20_000)
  for (let i = 0; i < 100; i++) f.session.progress()
  assert.equal(f.timers.get(deadline[0]), deadline[1])
  f.timers.delete(deadline[0])
  deadline[1].callback()
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(f.requests, [false, true])
  f.session.ready()
  assert.equal([...f.timers.values()].some(t => t.ms === 20_000), false)
  f.session.dispose()
})

test('video dimensions alone do not prove that a picture was decoded', async () => {
  const { hasDecodedVideoFrame } = await load()
  const media = { videoWidth: 1920, videoHeight: 1080,
    getVideoPlaybackQuality: () => ({ totalVideoFrames: 0 }) }
  assert.equal(hasDecodedVideoFrame(media), false)
  media.getVideoPlaybackQuality = () => ({ totalVideoFrames: 5 })
  assert.equal(hasDecodedVideoFrame(media), true)
})

test('successful native HEVC decoding stays on the original while fragmented MP4 is optimized', async () => {
  const { needsVideoCompatibility } = await load()
  const media = { videoWidth: 1920, videoHeight: 1080,
    getVideoPlaybackQuality: () => ({ totalVideoFrames: 5, droppedVideoFrames: 0 }) }
  assert.equal(needsVideoCompatibility({ strategy: 'TRANSCODE', videoCodec: 'hevc', audioCodec: 'aac' }, media), false)
  assert.equal(needsVideoCompatibility({ strategy: 'REMUX', videoCodec: '', audioCodec: '' }, media), true)
  assert.equal(needsVideoCompatibility({ strategy: 'TRANSCODE', videoCodec: 'h264', audioCodec: 'ac3' }, media), true)
})

test('a stalled original can enter progressive preparation without repeating full startup waits', async () => {
  let preparations = 0
  const f = await fixture('DIRECT', () => { preparations++ })
  const deadline = [...f.timers.values()].find(t => t.ms === 20_000)
  deadline.callback()
  assert.equal(preparations, 1)
  assert.deepEqual(f.requests, [false])
  f.session.dispose()
})
