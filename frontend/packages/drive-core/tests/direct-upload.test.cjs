const test = require('node:test')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const path = require('node:path')
const load = () => import(pathToFileURL(path.resolve(__dirname, '../src/direct-upload.ts')).href)
const session = { id: '1', uploadMode: 'DIRECT', status: 'UPLOADING', chunkSize: 5, chunkCount: 4 }
const file = new Blob(['abcdefghijklmnopqrst'])

test('direct multipart upload resumes remote parts and respects concurrency', async () => {
  const { executeDirectUpload } = await load()
  let running = 0, peak = 0
  const sent = []
  const api = {
    info: async () => ({ received: [0], concurrency: 2, fallbackAllowed: false }),
    presign: async (_, index) => ({ uploadUrl: String(index) }),
    fallback: async () => assert.fail('must not fallback'),
  }
  const result = await executeDirectUpload(api, session, file, undefined, undefined, async (url, blob, progress) => {
    peak = Math.max(peak, ++running)
    sent.push([url, await blob.text()])
    await new Promise(resolve => setTimeout(resolve, 5))
    progress(blob.size)
    running--
  })
  assert.equal(peak, 2)
  assert.deepEqual(sent.sort(), [['1', 'fghij'], ['2', 'klmno'], ['3', 'pqrst']])
  assert.deepEqual(result.received.sort(), [0, 1, 2, 3])
})

test('failed part retries refresh the signed URL', async () => {
  const { executeDirectUpload } = await load()
  let signed = 0
  let attempts = 0
  await executeDirectUpload({
    info: async () => ({ received: [0, 1, 2], concurrency: 1, fallbackAllowed: false }),
    presign: async () => ({ uploadUrl: String(++signed) }),
    fallback: async () => assert.fail(),
  }, session, file, undefined, undefined, async url => {
    if (++attempts === 1) throw new Error('expired')
    assert.equal(url, '2')
  })
  assert.equal(signed, 2)
})

test('fallback waits for workers to stop and requires administrator opt-in', async () => {
  const { executeDirectUpload } = await load()
  let fallback = 0
  const messages = []
  const api = {
    info: async () => ({ received: [], concurrency: 2, fallbackAllowed: true }),
    presign: async () => { throw new Error('storage inaccessible') },
    fallback: async () => { fallback++; return { ...session, uploadMode: 'PROXY', received: [] } },
  }
  const result = await executeDirectUpload(api, session, file, (_, message) => messages.push(message))
  assert.equal(result.uploadMode, 'PROXY')
  assert.equal(fallback, 1)
  assert.ok(messages.some(message => message.includes('重新上传')))
  api.info = async () => ({ received: [], concurrency: 1, fallbackAllowed: false })
  await assert.rejects(executeDirectUpload(api, session, file))
  assert.equal(fallback, 1)
})

test('paused or cancelled uploads do not fallback, queued uploads do not send again', async () => {
  const { executeDirectUpload } = await load()
  const controller = new AbortController()
  const api = {
    info: async () => { controller.abort(); throw new Error('aborted') },
    presign: async () => assert.fail(), fallback: async () => assert.fail(),
  }
  await assert.rejects(executeDirectUpload(api, session, file, undefined, controller.signal))
  assert.equal((await executeDirectUpload(api, { ...session, status: 'QUEUED' }, file)).status, 'QUEUED')
})
