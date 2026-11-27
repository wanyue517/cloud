const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

const mobileRoot = path.resolve(__dirname, '..')

test('M037: Capacitor config, scripts, and native abstraction files are wired without breaking Web H5', () => {
  const capConfig = fs.readFileSync(path.join(mobileRoot, 'capacitor.config.ts'), 'utf8')
  assert.match(capConfig, /com\.wanyue\.cloudlane/)
  assert.match(capConfig, /webDir:\s*['"]dist['"]/)

  const pkg = JSON.parse(fs.readFileSync(path.join(mobileRoot, 'package.json'), 'utf8'))
  assert.ok(pkg.dependencies['@capacitor/core'])
  assert.ok(pkg.dependencies['@capacitor/preferences'])
  assert.ok(pkg.dependencies['@capacitor/app'])
  assert.ok(pkg.dependencies['@capacitor/status-bar'])
  assert.ok(pkg.dependencies['@capacitor/network'])
  assert.ok(pkg.devDependencies['@capacitor/cli'])
  assert.ok(pkg.devDependencies['@capacitor/android'])
  assert.ok(pkg.devDependencies['@capacitor/ios'])

  const routerSource = fs.readFileSync(path.join(mobileRoot, 'src/router/index.ts'), 'utf8')
  assert.match(routerSource, /\/settings\/server/)
  assert.match(routerSource, /@\/views\/settings\/server\.vue/)
})

test('M037: normalizeServerOrigin validates HTTP/HTTPS origins and builds API base URLs safely', async () => {
  const { normalizeServerOrigin, buildApiBaseUrl } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/native/server-config.ts')).href
  )

  assert.equal(normalizeServerOrigin(''), '')
  assert.equal(normalizeServerOrigin('cloud.example.com/api'), 'https://cloud.example.com')
  assert.equal(normalizeServerOrigin('http://192.168.1.10:8000/m/'), 'http://192.168.1.10:8000')
  assert.equal(buildApiBaseUrl('https://cloud.example.com'), 'https://cloud.example.com/api')
  assert.equal(buildApiBaseUrl(''), '/api')

  assert.throws(() => normalizeServerOrigin('ftp://invalid.example.com'), /仅支持|无效/)
})

test('M038: biometric availability hides switch on unsupported devices and degrades smoothly to password', async () => {
  const {
    checkBiometricAvailability,
    authenticateBiometric,
    loadSecurityLockPrefs,
    saveSecurityLockPrefs,
  } = await import(pathToFileURL(path.join(mobileRoot, 'src/native/biometric.ts')).href)

  const avail = await checkBiometricAvailability()
  assert.equal(avail.available, false)
  assert.equal(avail.biometryType, 'none')

  const authRes = await authenticateBiometric('test')
  assert.equal(authRes.ok, false)
  assert.match(authRes.message, /密码/)

  const store = new Map()
  const prevStorage = globalThis.localStorage
  globalThis.localStorage = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => { store.set(k, String(v)) },
    removeItem: (k) => { store.delete(k) },
  }
  try {
    assert.equal(loadSecurityLockPrefs().backgroundBlurEnabled, true)
    assert.equal(loadSecurityLockPrefs().appStartupLockEnabled, false)
    const updated = saveSecurityLockPrefs({ appStartupLockEnabled: true, biometricVaultEnabled: true })
    assert.equal(updated.appStartupLockEnabled, true)
    assert.equal(updated.biometricVaultEnabled, true)
    assert.equal(loadSecurityLockPrefs().appStartupLockEnabled, true)
  } finally {
    globalThis.localStorage = prevStorage
  }

  const appSource = fs.readFileSync(path.join(mobileRoot, 'src/App.vue'), 'utf8')
  assert.match(appSource, /AppSecurityOverlay/)
})

test('M037/M038: mobileCapabilities strictly hides native-only features on Web H5 and enables them on Capacitor App', async () => {
  const { getMobileCapabilities } = await import(
    pathToFileURL(path.join(mobileRoot, 'src/native/capabilities.ts')).href
  )

  const h5 = getMobileCapabilities(false)
  assert.equal(h5.isNative, false)
  assert.equal(h5.customServer, false)
  assert.equal(h5.biometricLock, false)
  assert.equal(h5.appSecurityShield, false)
  assert.equal(h5.documentScanner, false)
  assert.equal(h5.offlineCache, false)
  assert.equal(h5.photoAutoBackup, false)

  const app = getMobileCapabilities(true)
  assert.equal(app.isNative, true)
  assert.equal(app.customServer, true)
  assert.equal(app.biometricLock, true)
  assert.equal(app.appSecurityShield, true)
  assert.equal(app.documentScanner, true)
  assert.equal(app.offlineCache, true)
  assert.equal(app.photoAutoBackup, true)
})
