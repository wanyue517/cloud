const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const searchHistoryUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/search-history.ts')).href

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
  }
}

test('M030: search history deduplicates, caps length, removes item, and clears cleanly', async () => {
  const {
    loadSearchHistory,
    pushSearchHistory,
    removeSearchHistoryItem,
    clearSearchHistory,
    tokenizeHighlight,
  } = await import(searchHistoryUrl)

  const storage = createMemoryStorage()
  assert.deepEqual(loadSearchHistory(storage), [])

  pushSearchHistory('年度报告', storage, 3)
  pushSearchHistory('预算表', storage, 3)
  pushSearchHistory('年度报告', storage, 3)
  assert.deepEqual(loadSearchHistory(storage), ['年度报告', '预算表'])

  pushSearchHistory('设计稿', storage, 3)
  pushSearchHistory('会议纪要', storage, 3)
  assert.deepEqual(loadSearchHistory(storage), ['会议纪要', '设计稿', '年度报告'])

  removeSearchHistoryItem('设计稿', storage)
  assert.deepEqual(loadSearchHistory(storage), ['会议纪要', '年度报告'])

  clearSearchHistory(storage)
  assert.deepEqual(loadSearchHistory(storage), [])

  const segs = tokenizeHighlight('Hello Cloudlane cloud', 'cloud')
  assert.deepEqual(segs, [
    { text: 'Hello ', hit: false },
    { text: 'Cloud', hit: true },
    { text: 'lane ', hit: false },
    { text: 'cloud', hit: true },
  ])
})

test('M030: router wires /search to real views/search/index.vue', () => {
  const routerSrc = fs.readFileSync(path.resolve(__dirname, '../src/router/index.ts'), 'utf8')
  assert.match(routerSrc, /path:\s*'\/search'[\s\S]*?@\/views\/search\/index\.vue/)
})
