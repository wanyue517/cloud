const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const mobileRoot = path.resolve(__dirname, '..')
const frontendRoot = path.resolve(mobileRoot, '../..')
const repoRoot = path.resolve(frontendRoot, '..')

test('M001: mobile package.json defines @cloud/mobile scripts and workspace deps', () => {
  const pkg = JSON.parse(fs.readFileSync(path.join(mobileRoot, 'package.json'), 'utf8'))
  assert.equal(pkg.name, '@cloud/mobile')
  assert.equal(pkg.scripts.dev, 'vite')
  assert.equal(pkg.scripts['check-types'], 'vue-tsc -b --noEmit')
  assert.equal(pkg.scripts.build, 'vue-tsc -b && vite build')
  assert.equal(pkg.dependencies['@repo/ui'], 'workspace:*')
  assert.equal(pkg.dependencies['@repo/session'], 'workspace:*')
})

test('M001: mobile vite.config.ts mounts at /m/ on port 3002', () => {
  const content = fs.readFileSync(path.join(mobileRoot, 'vite.config.ts'), 'utf8')
  assert.match(content, /base:\s*['"]\/m\/['"]/)
  assert.match(content, /port:\s*3002/)
  assert.match(content, /strictPort:\s*true/)
})

test('M001: mobile index.html includes viewport-fit=cover and favicon.svg', () => {
  const html = fs.readFileSync(path.join(mobileRoot, 'index.html'), 'utf8')
  assert.match(html, /viewport-fit=cover/)
  assert.match(html, /\/favicon\.svg/)
})

test('M001: web proxy, nginx, Dockerfile, and release script include mobile target', () => {
  const webVite = fs.readFileSync(path.join(frontendRoot, 'apps/web/vite.config.ts'), 'utf8')
  assert.match(webVite, /\^\/m\(\?:\/\|\$\)/)
  assert.match(webVite, /127\.0\.0\.1:3002/)

  const nginx = fs.readFileSync(path.join(frontendRoot, 'nginx.conf'), 'utf8')
  assert.match(nginx, /location = \/m \{ return 308 \/m\/; \}/)
  assert.match(nginx, /location \/m\/ \{ try_files \$uri \$uri\/ \/m\/index\.html; \}/)

  const dockerfile = fs.readFileSync(path.join(frontendRoot, 'Dockerfile'), 'utf8')
  assert.match(dockerfile, /apps\/mobile\/package\.json/)
  assert.match(dockerfile, /pnpm check:mobile/)
  assert.match(dockerfile, /pnpm build:mobile/)
  assert.match(dockerfile, /apps\/mobile\/dist \/usr\/share\/nginx\/html\/m/)

  const releasePs1 = fs.readFileSync(path.join(repoRoot, 'scripts/release.ps1'), 'utf8')
  assert.match(releasePs1, /"mobile"/)
  assert.match(releasePs1, /frontend\/apps\/mobile/)
})
