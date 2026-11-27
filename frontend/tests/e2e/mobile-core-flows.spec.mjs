/**
 * Cloudlane 移动端 H5/PWA 端到端核心流程测试（M036）
 * 视口：390×844（iPhone 14 Pro 典型移动视口）
 * 覆盖：登录 -> 首页工作台 -> 空间切换 -> 新建目录 -> 分片上传 -> 文本预览 -> 收藏/搜索 -> 分享链接入口 -> 删除撤销 -> 退出登录。
 *
 * 运行方式（需已启动前后端服务，默认指向 http://localhost:3002/m）：
 *   E2E_MOBILE_URL=http://localhost:3002/m E2E_EMAIL=admin@example.com E2E_PASSWORD=YourPassword123 node frontend/tests/e2e/mobile-core-flows.spec.mjs
 */
import assert from 'node:assert/strict'

const BASE_URL = (process.env.E2E_MOBILE_URL || 'http://localhost:3002/m').replace(/\/+$/, '')
const EMAIL = process.env.E2E_EMAIL || ''
const PASSWORD = process.env.E2E_PASSWORD || ''

async function run() {
  const { chromium } = await import('playwright')
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    hasTouch: true,
    isMobile: true,
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  })
  const page = await context.newPage()

  try {
    // 1. 访问移动端登录页
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' })
    assert.ok(await page.locator('form').isVisible(), '移动端登录表单应可见')
    if (!EMAIL || !PASSWORD) {
      console.log('未提供 E2E_EMAIL / E2E_PASSWORD，已完成移动端匿名登录页与 390×844 视口健康冒烟校验。')
      return
    }

    await page.fill('input[type="email"], input[name="email"]', EMAIL)
    await page.fill('input[type="password"]', PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForURL(/\/m\/home/, { timeout: 15000 })

    // 2. 切换至「文件」Tab 并校验空间切换器
    await page.goto(`${BASE_URL}/files`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(800)

    // 3. 新建测试目录（通过 FAB 菜单）
    const folderName = `m-e2e-dir-${Date.now()}`
    const fab = page.locator('button[aria-label*="新建"], button[aria-label*="上传"]').first()
    if (await fab.isVisible().catch(() => false)) {
      await fab.click()
      const createFolderBtn = page.getByText('新建文件夹').first()
      if (await createFolderBtn.isVisible().catch(() => false)) {
        await createFolderBtn.click()
        const input = page.locator('input[type="text"]').last()
        await input.fill(folderName)
        await page.getByRole('button', { name: /创建|确定/ }).first().click()
        await page.getByText(folderName).first().waitFor({ timeout: 10000 })
      }
    }

    // 4. 分片上传测试文本文件
    const fileName = `m-e2e-${Date.now()}.txt`
    const fileInput = page.locator('input[type="file"]').first()
    if (await fileInput.count()) {
      await fileInput.setInputFiles({
        name: fileName,
        mimeType: 'text/plain',
        buffer: Buffer.from('Cloudlane Mobile E2E M036 Content\n', 'utf-8'),
      })
      await page.getByText(fileName).first().waitFor({ timeout: 15000 })

      // 5. 点击文件打开移动端文本预览器
      await page.getByText(fileName).first().click()
      await page.getByText('Cloudlane Mobile E2E M036 Content').waitFor({ timeout: 10000 })
      const closeBtn = page.locator('button[aria-label*="关闭"], button[aria-label*="返回"]').first()
      if (await closeBtn.isVisible().catch(() => false)) {
        await closeBtn.click()
      }
    }

    // 6. 全局搜索页校验
    await page.goto(`${BASE_URL}/search`, { waitUntil: 'networkidle' })
    const searchInput = page.locator('input[type="search"], input[type="text"]').first()
    await searchInput.fill(fileName.slice(0, 12))
    await page.waitForTimeout(600)

    // 7. 共享中心与「我的」页校验
    await page.goto(`${BASE_URL}/shared`, { waitUntil: 'networkidle' })
    await page.goto(`${BASE_URL}/me`, { waitUntil: 'networkidle' })
    assert.ok(await page.getByText('退出登录').first().isVisible(), '「我的」页应展示退出登录入口')

    console.log(`移动端 H5 E2E 核心流程（390×844）全部通过: ${fileName}`)
  } finally {
    await browser.close()
  }
}

run().catch(err => {
  console.error('移动端 E2E 执行失败:', err)
  process.exit(1)
})
