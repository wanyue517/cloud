const test = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')
const core = () => import(pathToFileURL(path.resolve(__dirname, '../src/upload-queue.ts')).href)

test('completion can take longer than 120 seconds without resubmitting or timing out', async () => {
  const { waitForUploadCompletion } = await core()
  let elapsed = 0
  let submitted = 0
  const fileId = await waitForUploadCompletion({
    initial: { status: 'UPLOADING' },
    requestCompletion: async () => { submitted++; return { status: 'QUEUED' } },
    getSession: async () => elapsed > 180000 ? { status: 'COMPLETED', fileId: 'file-1' } : { status: 'PROCESSING' },
    sleep: async ms => { elapsed += ms },
  })
  assert.equal(fileId, 'file-1')
  assert.equal(submitted, 1)
  assert.ok(elapsed > 120000)
})

test('a lost submission response is retried idempotently and transient poll failures recover', async () => {
  const { waitForUploadCompletion } = await core()
  let submitted = 0
  let polled = 0
  assert.equal(await waitForUploadCompletion({
    initial: { status: 'UPLOADING' },
    requestCompletion: async () => {
      if (++submitted === 1) throw new Error('response lost')
      return { status: 'PROCESSING' }
    },
    getSession: async () => {
      if (++polled === 1) throw new Error('offline briefly')
      return { status: 'COMPLETED', fileId: 'file-2' }
    },
    sleep: async () => {},
  }), 'file-2')
  assert.equal(submitted, 2)
})

test('resuming a processing session only polls; completed sessions return immediately', async () => {
  const { waitForUploadCompletion } = await core()
  const unexpected = async () => assert.fail('must not submit again')
  assert.equal(await waitForUploadCompletion({
    initial: { status: 'PROCESSING' }, requestCompletion: unexpected,
    getSession: async () => ({ status: 'COMPLETED', fileId: 'resumed' }), sleep: async () => {},
  }), 'resumed')
  assert.equal(await waitForUploadCompletion({
    initial: { status: 'COMPLETED', fileId: 'existing' }, requestCompletion: unexpected, getSession: unexpected,
  }), 'existing')
})

test('failure messages are preserved and auth errors are not retried', async () => {
  const { waitForUploadCompletion } = await core()
  await assert.rejects(waitForUploadCompletion({
    initial: { status: 'PROCESSING' },
    requestCompletion: async () => assert.fail(),
    getSession: async () => ({ status: 'FAILED', errorMessage: '校验失败' }), sleep: async () => {},
  }), /校验失败/)
  let attempts = 0
  await assert.rejects(waitForUploadCompletion({
    requestCompletion: async () => assert.fail(),
    getSession: async () => { attempts++; throw Object.assign(new Error('forbidden'), { httpStatus: 403 }) },
    sleep: async () => assert.fail('must not retry'),
  }), /forbidden/)
  assert.equal(attempts, 1)
})

test('aborting polling leaves the server task alone and stops further requests', async () => {
  const { waitForUploadCompletion } = await core()
  const controller = new AbortController()
  await assert.rejects(waitForUploadCompletion({
    initial: { status: 'PROCESSING' }, signal: controller.signal,
    requestCompletion: async () => assert.fail(),
    getSession: async () => assert.fail('must not poll after abort'),
    sleep: async () => { controller.abort() },
  }))
})
