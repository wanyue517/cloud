const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { createRequire } = require('node:module')
const dependency = createRequire(path.join(__dirname, '../package.json'))

function session() {
  const axios = dependency('axios')
  const client = axios.create({ baseURL: '/api' })
  const shim = new Proxy(axios, { get: (target, key) => key === 'create' ? () => client : target[key] })
  const source = fs.readFileSync(path.resolve(__dirname, '../../../packages/session/request.ts'), 'utf8')
  const output = dependency('typescript').transpileModule(source, { compilerOptions: {
    target: dependency('typescript').ScriptTarget.ES2022,
    module: dependency('typescript').ModuleKind.CommonJS,
    esModuleInterop: true,
  } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(output, { module, exports: module.exports,
    require: name => name === 'axios' ? shim : dependency(name),
    window: { location: { origin: 'http://local' } }, URL, Blob, console,
  })
  return { ...module.exports, client }
}

test('renewing a vault session replaces expired scoped credentials on every space request', async () => {
  const api = session()
  api.setVaultToken('expired-vault')
  api.setPrivateSpaceToken('123', 'expired-space')
  api.setVaultToken('fresh-vault')
  api.client.defaults.adapter = async config => ({ config, status: 200, headers: {},
    data: { code: 0, data: config.headers.get('X-Private-Token') },
  })
  assert.equal(await api.request.get('/v1/spaces/123/nodes'), 'fresh-vault')
  assert.equal(await api.request.get('/v1/me/private/overview'), 'fresh-vault')
  assert.equal(await api.request.get('/v1/spaces/456/nodes'), 'fresh-vault')
})

test('a locked response evicts the rejected credential and a new scoped unlock restores access', async () => {
  const api = session()
  api.setVaultToken('expired')
  api.client.defaults.adapter = async config => ({ config, status: 423, headers: {},
    data: { code: 423, msg: 'locked' },
  })
  await assert.rejects(api.request.get('/v1/spaces/123/nodes'), /locked/)
  assert.equal(api.hasVaultToken(), false)
  assert.equal(api.hasPrivateSpaceToken('123'), false)
  api.setPrivateSpaceToken('123', 'fresh-scoped')
  api.client.defaults.adapter = async config => ({ config, status: 200, headers: {},
    data: { code: 0, data: config.headers.get('X-Private-Token') },
  })
  assert.equal(await api.request.get('/v1/spaces/123/nodes'), 'fresh-scoped')
})

test('a delayed locked response does not erase a newly issued private session', async () => {
  const api = session()
  let finish, sent
  api.setPrivateSpaceToken('123', 'expired')
  api.client.defaults.adapter = config => {
    sent = config
    return new Promise(resolve => { finish = resolve })
  }
  const pending = api.request.get('/v1/spaces/123/nodes')
  await new Promise(resolve => setImmediate(resolve))
  api.setPrivateSpaceToken('123', 'fresh')
  finish({ config: sent, status: 423, headers: {}, data: { code: 423, msg: 'expired' } })
  await assert.rejects(pending, /expired/)
  assert.equal(api.hasPrivateSpaceToken('123'), true)
  api.client.defaults.adapter = async config => ({ config, status: 200, headers: {},
    data: { code: 0, data: config.headers.get('X-Private-Token') },
  })
  assert.equal(await api.request.get('/v1/spaces/123/nodes'), 'fresh')
})
