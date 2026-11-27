const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M004: Vant 4 is installed and bridged to Cloudlane theme tokens', () => {
  const pkg = JSON.parse(fs.readFileSync(path.join(mobileRoot, 'package.json'), 'utf8'))
  assert.ok(pkg.dependencies.vant, 'Expected vant dependency in @cloud/mobile')

  const indexCss = fs.readFileSync(path.join(mobileRoot, 'src/styles/index.css'), 'utf8')
  assert.match(indexCss, /vant\/lib\/index\.css/)
  assert.match(indexCss, /\.\/vant-theme\.css/)

  const bridgeCss = fs.readFileSync(path.join(mobileRoot, 'src/styles/vant-theme.css'), 'utf8')
  assert.match(bridgeCss, /--van-primary-color:\s*var\(--primary\)/)
  assert.match(bridgeCss, /--van-background:\s*var\(--background\)/)
  assert.match(bridgeCss, /van-theme-dark/)

  const themeSync = fs.readFileSync(path.join(mobileRoot, 'src/lib/theme-sync.ts'), 'utf8')
  assert.match(themeSync, /van-theme-dark/)
})

test('M004: all required mobile foundation components exist and are exported', () => {
  const componentsDir = path.join(mobileRoot, 'src/components/common')
  const required = [
    'MobileShell.vue',
    'BottomSheet.vue',
    'ActionSheet.vue',
    'PullRefresh.vue',
    'InfiniteList.vue',
    'FloatingActionButton.vue',
    'UndoToast.vue',
    'EmptyState.vue',
    'ErrorState.vue',
    'SkeletonList.vue',
    'index.ts',
  ]
  for (const file of required) {
    assert.ok(fs.existsSync(path.join(componentsDir, file)), `Missing component: ${file}`)
  }
  const barrel = fs.readFileSync(path.join(componentsDir, 'index.ts'), 'utf8')
  assert.match(barrel, /MobileShell/)
  assert.match(barrel, /BottomSheet/)
  assert.match(barrel, /ActionSheet/)
  assert.match(barrel, /UndoToast/)
})

test('M004: mobile-ui store manages badges and 10-second undo toast lifecycle', async () => {
  const store = await import(pathToFileURL(path.join(mobileRoot, 'src/stores/mobile-ui.ts')).href)

  store.setUnreadMessageCount(5)
  store.setActiveUploadCount(2)
  assert.equal(store.mobileUi.unreadMessageCount, 5)
  assert.equal(store.mobileUi.activeUploadCount, 2)

  store.setUnreadMessageCount(-3)
  assert.equal(store.mobileUi.unreadMessageCount, 0)

  let undoneToken = ''
  store.showUndoToast({
    tokenId: 'undo-42',
    summary: '已删除 1 项',
    durationMs: 5000,
    onUndo: async tokenId => {
      undoneToken = tokenId
    },
  })
  assert.equal(store.mobileUi.undo.visible, true)
  assert.equal(store.mobileUi.undo.tokenId, 'undo-42')

  const ok = await store.triggerUndoToast()
  assert.equal(ok, true)
  assert.equal(undoneToken, 'undo-42')
  assert.equal(store.mobileUi.undo.visible, false)
})
