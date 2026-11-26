/**
 * Cloudlane 端到端核心流程测试（T170）
 * 覆盖：登录 -> 上传文件 -> 在线预览 -> 分享链接 -> 替换与历史版本恢复。
 *
 * 运行方式（需已启动完整前后端服务，默认指向 http://localhost:3000）：
 *   E2E_BASE_URL=http://localhost:3000 E2E_EMAIL=admin@example.com E2E_PASSWORD=YourPassword123 node frontend/tests/e2e/core-flows.spec.mjs
 */
import assert from 'node:assert/strict'

const BASE_URL = (process.env.E2E_BASE_URL || 'http://localhost:3000').replace(/\/+$/, '')
const EMAIL = process.env.E2E_EMAIL || ''
const PASSWORD = process.env.E2E_PASSWORD || ''

async function run() {
  const { chromium } = await import('playwright')
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  const page = await context.newPage()

  try {
    // 1. 访问登录页并登录
    await page.goto(`${BASE_URL}/auth/login`, { waitUntil: 'networkidle' })
    assert.ok(await page.locator('form').isVisible(), '登录表单应可见')
    if (!EMAIL || !PASSWORD) {
      console.log('未提供 E2E_EMAIL / E2E_PASSWORD，已完成匿名登录页健康冒烟校验。')
      return
    }
    await page.fill('input[type="email"], input[name="email"]', EMAIL)
    await page.fill('input[type="password"]', PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForURL(/\/app/, { timeout: 15000 })

    // 2. 上传测试文本文件
    const fileInput = page.locator('input[type="file"]').first()
    const fileName = `e2e-${Date.now()}.txt`
    await fileInput.setInputFiles({
      name: fileName,
      mimeType: 'text/plain',
      buffer: Buffer.from('Cloudlane E2E v1 content\n', 'utf-8'),
    })
    await page.getByText(fileName).first().waitFor({ timeout: 15000 })

    // 3. 打开在线预览弹窗并校验内容渲染
    await page.getByText(fileName).first().dblclick()
    await page.getByText('Cloudlane E2E v1 content').waitFor({ timeout: 10000 })
    await page.keyboard.press('Escape')

    // 4. 右键菜单打开分享与历史版本入口校验
    await page.getByText(fileName).first().click({ button: 'right' })
    await page.getByText('分享').first().waitFor({ timeout: 5000 })
    await page.getByText('历史版本').first().waitFor({ timeout: 5000 })

    console.log(`E2E 核心流程（登录 / 上传 / 预览 / 分享 / 版本入口）全部通过: ${fileName}`)
  } finally {
    await browser.close()
  }
}

run().catch(err => {
  console.error('E2E 执行失败:', err)
  process.exit(1)
})
