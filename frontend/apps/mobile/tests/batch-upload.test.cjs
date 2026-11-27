const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const uploadEngineUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/upload-engine.ts')).href

function createMemoryStorage() {
  const map = new Map()
  return {
    getItem(k) {
      return map.has(k) ? map.get(k) : null
    },
    setItem(k, v) {
      map.set(k, String(v))
    },
    removeItem(k) {
      map.delete(k)
    },
    size() {
      return map.size
    },
  }
}

function createFakeFile(name, totalBytes) {
  return {
    name,
    size: totalBytes,
    type: 'application/octet-stream',
    lastModified: 1700000000000,
    slice(start, end) {
      return new Blob([new Uint8Array(Math.max(0, end - start))])
    },
  }
}

test('M017: formatBatchOutcomeMessage reports full success and PARTIAL failure details', async () => {
  const { formatBatchOutcomeMessage } = await import(uploadEngineUrl)

  const okRes = formatBatchOutcomeMessage('批量删除', {
    status: 'COMPLETED',
    totalCount: 3,
    successCount: 3,
    failedCount: 0,
  })
  assert.equal(okRes.ok, true)
  assert.match(okRes.title, /3 项/)

  const partialRes = formatBatchOutcomeMessage('批量移动', {
    status: 'PARTIAL',
    totalCount: 3,
    successCount: 2,
    failedCount: 1,
    outcomes: [
      { nodeId: '1', ok: true, message: '' },
      { nodeId: '2', ok: true, message: '' },
      { nodeId: '3', ok: false, message: '不能移动到自身子目录' },
    ],
  })
  assert.equal(partialRes.ok, false)
  assert.match(partialRes.title, /成功 2 项，失败 1 项/)
  assert.match(partialRes.detail, /不能移动到自身子目录/)
})

test('M018: executeChunkedUpload retries transient chunk failures with exponential backoff', async () => {
  const { executeChunkedUpload } = await import(uploadEngineUrl)
  const storage = createMemoryStorage()
  const file = createFakeFile('video.mp4', 200)
  const delays = []
  let chunk0Attempts = 0
  const uploadedIndices = []

  const fileId = await executeChunkedUpload(
    {
      storage,
      sleep: async ms => {
        delays.push(ms)
      },
      baseDelayMs: 100,
      maxRetries: 3,
      getSession: async () => {
        throw new Error('no previous session')
      },
      createSession: async () => ({
        id: 'up-1',
        chunkSize: 100,
        chunkCount: 2,
        received: [],
        status: 'UPLOADING',
      }),
      uploadChunk: async (_id, idx, _blob, _name, onChunkProgress) => {
        if (idx === 0) {
          chunk0Attempts += 1
          if (chunk0Attempts < 3) throw new Error('network jitter')
        }
        uploadedIndices.push(idx)
        onChunkProgress(100, 100)
      },
      completeSession: async () => 'file-999',
    },
    { spaceId: '10', parentId: '0', file },
  )

  assert.equal(fileId, 'file-999')
  assert.equal(chunk0Attempts, 3)
  assert.deepEqual(delays, [100, 200])
  assert.deepEqual(uploadedIndices, [0, 1])
  assert.equal(storage.size(), 0) // cleaned up after completion
})

test('M018: executeChunkedUpload pauses on abort and resumes skipping already-received chunks', async () => {
  const { executeChunkedUpload } = await import(uploadEngineUrl)
  const storage = createMemoryStorage()
  const file = createFakeFile('archive.zip', 300)
  const ctrl = new AbortController()
  const serverReceived = new Set()
  const uploadedByRun = [[], []]

  // First run: upload chunk 0, then abort before chunk 1
  await assert.rejects(
    () =>
      executeChunkedUpload(
        {
          storage,
          sleep: async () => {},
          getSession: async () => {
            throw new Error('not found')
          },
          createSession: async () => ({
            id: 'up-resume',
            chunkSize: 100,
            chunkCount: 3,
            received: [],
            status: 'UPLOADING',
          }),
          uploadChunk: async (_id, idx) => {
            uploadedByRun[0].push(idx)
            serverReceived.add(idx)
            if (idx === 0) ctrl.abort()
          },
          completeSession: async () => 'should-not-complete',
        },
        { spaceId: '10', parentId: '0', file, signal: ctrl.signal },
      ),
    /暂停|aborted/,
  )

  assert.deepEqual(uploadedByRun[0], [0])
  assert.equal(storage.size(), 1) // resume key preserved

  // Second run: resumes from stored session and skips chunk 0
  let createCalled = false
  const fileId = await executeChunkedUpload(
    {
      storage,
      sleep: async () => {},
      getSession: async id => {
        assert.equal(id, 'up-resume')
        return {
          id,
          chunkSize: 100,
          chunkCount: 3,
          received: Array.from(serverReceived),
          status: 'UPLOADING',
        }
      },
      createSession: async () => {
        createCalled = true
        throw new Error('should not recreate')
      },
      uploadChunk: async (_id, idx) => {
        uploadedByRun[1].push(idx)
        serverReceived.add(idx)
      },
      completeSession: async () => 'file-resumed',
    },
    { spaceId: '10', parentId: '0', file },
  )

  assert.equal(createCalled, false)
  assert.deepEqual(uploadedByRun[1], [1, 2])
  assert.equal(fileId, 'file-resumed')
  assert.equal(storage.size(), 0)
})
