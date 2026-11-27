const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mediaCaptureUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/media-capture.ts')).href

test('M019: buildCaptureFilename formats photo, video, and audio filenames with timestamp', async () => {
  const { buildCaptureFilename, formatRecordingDuration, pickPreferredAudioMimeType } =
    await import(mediaCaptureUrl)

  const fixed = new Date(2026, 10, 27, 9, 5, 3) // 2026-11-27 09:05:03
  assert.equal(buildCaptureFilename('photo', 'image/jpeg', 'raw.jpg', fixed), 'IMG_20261127_090503.jpg')
  assert.equal(buildCaptureFilename('photo', 'image/png', '', fixed), 'IMG_20261127_090503.png')
  assert.equal(buildCaptureFilename('video', 'video/mp4', '', fixed), 'VID_20261127_090503.mp4')
  assert.equal(buildCaptureFilename('audio', 'audio/webm;codecs=opus', '', fixed), 'REC_20261127_090503.webm')
  assert.equal(buildCaptureFilename('audio', 'audio/mp4', '', fixed), 'REC_20261127_090503.m4a')

  assert.equal(formatRecordingDuration(0), '00:00')
  assert.equal(formatRecordingDuration(65), '01:05')
  assert.equal(formatRecordingDuration(3599), '59:59')

  assert.equal(
    pickPreferredAudioMimeType(m => m === 'audio/webm;codecs=opus'),
    'audio/webm;codecs=opus',
  )
  assert.equal(pickPreferredAudioMimeType(() => false), '')
})

test('M020: router wires /tasks to real views/tasks/index.vue', () => {
  const routerSrc = fs.readFileSync(path.resolve(__dirname, '../src/router/index.ts'), 'utf8')
  assert.match(routerSrc, /path:\s*'\/tasks'[\s\S]*?@\/views\/tasks\/index\.vue/)
})
