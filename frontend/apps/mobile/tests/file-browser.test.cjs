const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const fileActionsUrl = pathToFileURL(path.resolve(__dirname, '../src/lib/file-actions.ts')).href

function createMemoryStorage() {
  const map = new Map()
  return {
    getItem(k) {
      return map.has(k) ? map.get(k) : null
    },
    setItem(k, v) {
      map.set(k, String(v))
    },
  }
}

test('buildCategoryPills only includes non-zero categories in canonical order plus ALL', async () => {
  const { buildCategoryPills } = await import(fileActionsUrl)

  assert.deepEqual(buildCategoryPills(null), [])

  const pills = buildCategoryPills({
    folders: 2,
    files: 5,
    categories: {
      PDF: 2,
      IMAGE: 3,
      VIDEO: 0,
      OTHER: 0,
    },
  })

  assert.deepEqual(pills, [
    { key: 'ALL', label: '全部', count: 7 },
    { key: 'IMAGE', label: '图片', count: 3 },
    { key: 'PDF', label: 'PDF', count: 2 },
  ])
})

test('buildNodeActions enforces role matrix and file/folder differences', async () => {
  const { buildNodeActions } = await import(fileActionsUrl)

  const fileNode = {
    id: '1',
    name: 'report.pdf',
    nodeType: 'FILE',
    favorite: false,
    hidden: false,
    pinned: false,
  }
  const folderNode = {
    id: '2',
    name: 'Docs',
    nodeType: 'FOLDER',
    favorite: true,
    hidden: false,
    pinned: true,
  }

  // OWNER on normal FILE has all actions including write + file-only actions
  const ownerFileKeys = buildNodeActions(fileNode, 'OWNER').map(a => a.key)
  assert.deepEqual(ownerFileKeys, [
    'open',
    'download',
    'share',
    'favorite',
    'pin',
    'rename',
    'move',
    'copy',
    'versions',
    'replace',
    'hidden',
    'detail',
    'delete',
  ])

  // VIEWER on FILE hides write actions (rename/move/copy/replace/hidden/delete) but keeps preview/download/versions/detail
  const viewerFileKeys = buildNodeActions(fileNode, 'VIEWER').map(a => a.key)
  assert.deepEqual(viewerFileKeys, ['open', 'download', 'share', 'favorite', 'pin', 'versions', 'detail'])

  // EDITOR on FOLDER excludes file-only actions (download/versions/replace)
  const editorFolderActions = buildNodeActions(folderNode, 'EDITOR')
  const editorFolderKeys = editorFolderActions.map(a => a.key)
  assert.equal(editorFolderKeys.includes('download'), false)
  assert.equal(editorFolderKeys.includes('versions'), false)
  assert.equal(editorFolderKeys.includes('replace'), false)
  assert.equal(editorFolderKeys.includes('rename'), true)
  assert.equal(editorFolderKeys.includes('delete'), true)
  assert.equal(editorFolderActions.find(a => a.key === 'favorite')?.name, '取消收藏')
  assert.equal(editorFolderActions.find(a => a.key === 'pin')?.name, '取消快捷访问')
})

test('buildNodeActions disables share and pin on hidden nodes', async () => {
  const { buildNodeActions } = await import(fileActionsUrl)

  const hiddenFile = {
    id: '3',
    name: 'secret.txt',
    nodeType: 'FILE',
    favorite: false,
    hidden: true,
    pinned: false,
  }

  const actions = buildNodeActions(hiddenFile, 'OWNER')
  const share = actions.find(a => a.key === 'share')
  const pin = actions.find(a => a.key === 'pin')
  const hiddenToggle = actions.find(a => a.key === 'hidden')

  assert.equal(share?.disabled, true)
  assert.match(share?.subname || '', /隐藏/)
  assert.equal(pin?.disabled, true)
  assert.match(pin?.subname || '', /隐藏/)
  assert.equal(hiddenToggle?.name, '取消隐藏')
})

test('view mode and sort config persist cleanly with safe defaults', async () => {
  const { loadFileViewMode, saveFileViewMode, loadSortConfig, saveSortConfig, formatSortLabel } =
    await import(fileActionsUrl)

  const storage = createMemoryStorage()
  assert.equal(loadFileViewMode(storage), 'list')
  saveFileViewMode('grid', storage)
  assert.equal(loadFileViewMode(storage), 'grid')

  assert.deepEqual(loadSortConfig(storage), { field: 'updatedAt', order: 'desc' })
  saveSortConfig({ field: 'name', order: 'asc' }, storage)
  assert.deepEqual(loadSortConfig(storage), { field: 'name', order: 'asc' })
  assert.equal(formatSortLabel({ field: 'name', order: 'asc' }), '名称·升序')
  assert.equal(formatSortLabel({ field: 'size', order: 'desc' }), '大小·降序')
})
