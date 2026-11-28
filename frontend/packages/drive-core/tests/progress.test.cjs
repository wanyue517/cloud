const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const pkgRoot = path.resolve(__dirname, '..')

test('M035: formatResumePrompt and shouldPromptResume handle VIDEO, AUDIO, and PDF accurately', async () => {
  const { formatResumePrompt, shouldPromptResume } = await import(
    pathToFileURL(path.join(pkgRoot, 'src/progress.ts')).href
  )

  assert.equal(formatResumePrompt('PDF', 12), '继续上次位置（第 12 页）')
  assert.equal(formatResumePrompt('VIDEO', 125), '继续上次位置（02:05）')
  assert.equal(formatResumePrompt('AUDIO', 3661), '继续上次位置（01:01:01）')

  assert.equal(shouldPromptResume('PDF', 1, 20), false)
  assert.equal(shouldPromptResume('PDF', 2, 20), true)
  assert.equal(shouldPromptResume('PDF', 25, 20), false)

  assert.equal(shouldPromptResume('VIDEO', 3, 120), false)
  assert.equal(shouldPromptResume('VIDEO', 30, 120), true)
  assert.equal(shouldPromptResume('VIDEO', 117, 120), false)
})

test('short videos and unknown durations never show resume prompts; longer videos retain existing boundaries', async () => {
  const { shouldPromptResume } = await import(pathToFileURL(path.join(pkgRoot, 'src/progress.ts')).href)
  for (const duration of [undefined, 0, -1, NaN, Infinity, 6, 10, 19.9, 20]) {
    assert.equal(shouldPromptResume('VIDEO', 6, duration), false, `duration=${duration}`)
  }
  assert.equal(shouldPromptResume('VIDEO', 6, 20.1), true)
  assert.equal(shouldPromptResume('VIDEO', 4, 120), false)
  assert.equal(shouldPromptResume('VIDEO', 60, 120), true)
  assert.equal(shouldPromptResume('VIDEO', 115, 120), false)
  assert.equal(shouldPromptResume('AUDIO', 6, 20), true)
  assert.equal(shouldPromptResume('PDF', 2, 20), true)
})

test('M035: createProgressReporter throttles calls to 10s interval and flushes pending position on demand', async () => {
  const { createProgressReporter } = await import(
    pathToFileURL(path.join(pkgRoot, 'src/progress.ts')).href
  )

  let now = 1_000
  const calls = []
  const reporter = createProgressReporter(
    (v) => {
      calls.push(v)
    },
    { intervalMs: 10_000, now: () => now },
  )

  reporter.report(10)
  assert.deepEqual(calls, [10])

  now += 3_000
  reporter.report(13)
  now += 3_000
  reporter.report(16)
  assert.deepEqual(calls, [10])

  now += 4_500
  reporter.report(20.5)
  assert.deepEqual(calls, [10, 20.5])

  now += 2_000
  reporter.report(22.5)
  assert.deepEqual(calls, [10, 20.5])
  reporter.flush()
  assert.deepEqual(calls, [10, 20.5, 22.5])
})
