const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M005: PWA manifest is valid JSON and referenced in index.html', () => {
  const manifestPath = path.join(mobileRoot, 'public/manifest.webmanifest')
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  assert.equal(manifest.start_url, '/m/home')
  assert.equal(manifest.scope, '/m/')
  assert.equal(manifest.display, 'standalone')
  assert.ok(Array.isArray(manifest.icons) && manifest.icons.length > 0)

  const html = fs.readFileSync(path.join(mobileRoot, 'index.html'), 'utf8')
  assert.match(html, /rel="manifest"\s+href="\/manifest\.webmanifest"/)
  assert.match(html, /rel="apple-touch-icon"/)
})

test('M006: network guard detects status, persists prefs, enforces cellular policies, and sizes thumbs', async () => {
  const net = await import(pathToFileURL(path.join(mobileRoot, 'src/lib/network.ts')).href)

  assert.equal(net.detectNetworkStatus(false).kind, 'offline')
  assert.equal(net.detectNetworkStatus(true, { type: 'wifi' }).kind, 'wifi')
  assert.equal(net.detectNetworkStatus(true, { type: 'cellular', effectiveType: '4g' }).kind, 'cellular')
  assert.equal(net.detectNetworkStatus(true, { effectiveType: '3g' }).kind, 'cellular')

  const backing = new Map()
  const storage = {
    getItem: k => backing.get(k) ?? null,
    setItem: (k, v) => backing.set(k, v),
  }
  const defaults = net.loadNetworkPreferences(storage)
  assert.equal(defaults.wifiOnlyUpload, false)
  assert.equal(defaults.warnCellularVideo, true)
  assert.equal(defaults.lowResThumbOnCellular, true)

  const saved = net.saveNetworkPreferences({ wifiOnlyUpload: true }, storage)
  assert.equal(saved.wifiOnlyUpload, true)
  assert.equal(net.loadNetworkPreferences(storage).wifiOnlyUpload, true)

  const cellular = net.detectNetworkStatus(true, { type: 'cellular' })
  const wifi = net.detectNetworkStatus(true, { type: 'wifi' })

  assert.equal(net.shouldConfirmUploadOnNetwork(saved, cellular), true)
  assert.equal(net.shouldConfirmUploadOnNetwork(saved, wifi), false)
  assert.equal(net.shouldWarnVideoOnNetwork(saved, cellular), true)
  assert.equal(net.shouldWarnVideoOnNetwork(saved, wifi), false)

  assert.equal(net.getMobileThumbWidth(saved, cellular, 3), 240)
  assert.equal(net.getMobileThumbWidth(saved, wifi, 2), 360)
  assert.equal(net.getMobileThumbWidth(saved, wifi, 3), 480)
})

test('M006: LRU thumb cache evicts oldest entries and revokes object URLs', async () => {
  const { createThumbLruCache } = await import(pathToFileURL(path.join(mobileRoot, 'src/lib/thumb-cache.ts')).href)
  const revoked = []
  const cache = createThumbLruCache(2, url => revoked.push(url))

  cache.set('k1', 'blob:1', 100)
  cache.set('k2', 'blob:2', 200)
  // Touch k1 so k2 becomes least recently used
  assert.equal(cache.get('k1'), 'blob:1')

  cache.set('k3', 'blob:3', 300)
  assert.deepEqual(revoked, ['blob:2'])
  assert.equal(cache.get('k2'), null)
  assert.equal(cache.get('k1'), 'blob:1')
  assert.equal(cache.get('k3'), 'blob:3')
  assert.deepEqual(cache.stats(), { count: 2, bytes: 400, maxEntries: 2 })

  cache.clear()
  assert.equal(cache.stats().count, 0)
  assert.equal(cache.stats().bytes, 0)
  assert.equal(revoked.length, 3)
})
