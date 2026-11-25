"""T008 browser regression using deterministic API fixtures against dev or Nginx."""
import os
from pathlib import Path
from urllib.request import urlopen
from urllib.error import HTTPError
from playwright.sync_api import sync_playwright, expect
BASE = os.environ.get('T008_BASE_URL', 'http://localhost:3010')
OUTPUT = Path('frontend/apps/web/test-results/t008')
OUTPUT.mkdir(parents=True, exist_ok=True)
signed = False
admin = True
calls = []
def api(route):
    global signed
    path = route.request.url.split('/api', 1)[1].split('?')[0]
    calls.append(path)
    if path == '/admin/auth/login/email':
        signed = True
        data = {'accessToken': 'fixture-admin', 'expiresIn': 900, 'tokenType': 'Bearer'}
    elif path == '/v1/auth/logout':
        signed = False
        data = True
    elif not signed:
        route.fulfill(status=401, json={'code': 401, 'msg': 'expired', 'data': None})
        return
    elif path == '/v1/me':
        data = {'admin': admin, 'displayName': 'Test'}
    elif path == '/v1/admin/users':
        data = [{'id': '1', 'displayName': 'Test', 'status': 1, 'registeredAt': '2026-11-25T08:00:00Z'}]
    elif path == '/v1/admin/stats':
        data = {'users': 1, 'spaces': 1, 'files': 2, 'bytes': 1024}
    elif path == '/v1/spaces':
        data = []
    else:
        data = True
    route.fulfill(json={'code': 0, 'data': data})
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel=os.environ.get('T008_BROWSER_CHANNEL', 'msedge'))
    context = browser.new_context()
    context.route(BASE + '/api/**', api)
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(BASE + '/admin/users?page=2')
    page.wait_for_load_state('networkidle')
    page.wait_for_url('**/auth/login?returnTo=**')
    expect(page.locator('input[type=email]')).to_be_visible()
    print('Login heading:', page.locator('h1').all_text_contents())
    assert '/auth/login?returnTo=' in page.url
    assert page.locator('input[type=email]').count() == 1
    page.locator('input[type=email]').fill('admin@example.com')
    page.locator('input[type=password]').fill('password123')
    page.locator('button[type=submit]').click()
    try:
        page.wait_for_url('**/admin/users?page=2', timeout=10000)
    except Exception:
        print('DEBUG', page.url, page.locator('body').inner_text(), calls, errors, flush=True)
        raise
    page.wait_for_load_state('networkidle')
    print('Admin heading:', page.locator('h1').all_text_contents())
    expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
    expect(page.get_by_role('cell', name='Test', exact=True)).to_be_visible()
    # Confirms shared Tailwind sources are included in both apps.
    assert page.locator('h1').evaluate('(el) => getComputedStyle(el).fontSize') == '20px'
    page.reload()
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
    page.get_by_role('button', name='外观设置').click()
    expect(page.get_by_role('dialog')).to_be_visible()
    page.get_by_role('button', name='深色', exact=True).click()
    assert page.locator('html').get_attribute('class') == 'dark'
    page.keyboard.press('Escape')
    expect(page.get_by_role('dialog')).to_be_hidden()
    page.screenshot(path=str(OUTPUT / ('prod.png' if ':3088' in BASE else 'dev.png')), full_page=True)
    page.get_by_role('button', name='退出登录', exact=True).click()
    page.wait_for_url('**/auth/login?returnTo=**')
    assert page.evaluate("sessionStorage.getItem('cloudlane.accessToken')") is None
    page.goto(BASE + '/admin/users')
    page.wait_for_url('**/auth/login?returnTo=**')
    # A stored but revoked token must not show admin content.
    page.evaluate("sessionStorage.setItem('cloudlane.accessToken', 'revoked')")
    page.goto(BASE + '/admin/users')
    page.wait_for_url('**/auth/login?returnTo=**')
    # An authenticated member sees the admin app's 404.
    admin_calls = calls.count('/v1/admin/users')
    signed = True
    admin = False
    page.evaluate("sessionStorage.setItem('cloudlane.accessToken', 'member')")
    page.goto(BASE + '/admin/users')
    page.wait_for_url('**/admin/404')
    expect(page.get_by_role('heading', name='404 · 页面不存在')).to_be_visible()
    assert not page.get_by_role('heading', name='Cloudlane 管理后台').count()
    assert calls.count('/v1/admin/users') == admin_calls
    assert not errors, errors
    print('PASS: login return, deep refresh, shared theme, logout, revoked session, member 404')
    browser.close()
