"""Real Nginx + Spring Boot + MySQL browser integration; invoked by the Java test.
No network interception or service mocks. Test accounts exist only in the disposable database.
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import tempfile
import time
from urllib.request import urlopen
import uuid
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[4]
BASE = 'http://localhost:3010' if os.environ.get('T008_USE_DEV') == '1' else 'http://127.0.0.1:3088'
OUTPUT = ROOT / 'frontend/apps/web/test-results/t008-live'
OUTPUT.mkdir(parents=True, exist_ok=True)

def command(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=30)

def expired_token():
    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=')
    body = encode({'alg': 'HS256', 'typ': 'JWT'}) + b'.' + encode({
        'iss': 'cloudlane', 'sub': os.environ['T008_ADMIN_ID'], 'iat': int(time.time()) - 300,
        'exp': int(time.time()) - 120, 'tokenType': 'access', 'sessionVersion': 0,
    })
    signature = hmac.new(base64.b64decode(os.environ['T008_JWT_SECRET']), body, hashlib.sha256).digest()
    return (body + b'.' + base64.urlsafe_b64encode(signature).rstrip(b'=')).decode()

def verify_login_components(page):
    email = page.get_by_label('邮箱地址', exact=True)
    password = page.get_by_label('密码', exact=True)
    submit = page.get_by_role('button', name='登录', exact=True)
    expect(email).to_be_visible()
    expect(password).to_be_visible()
    # The app's h-11 override must merge with the shared h-9 defaults.
    expect(email).to_have_css('height', '44px')
    expect(submit).to_have_css('height', '44px')
    border = email.evaluate('(el) => getComputedStyle(el).borderColor')
    shadow = email.evaluate('(el) => getComputedStyle(el).boxShadow')
    email.focus()
    page.keyboard.press('Shift+Tab')
    page.keyboard.press('Tab')
    expect(email).to_be_focused()
    expect(email).not_to_have_css('box-shadow', shadow)
    email.fill('invalid-address')
    submit.click()
    expect(email).to_have_attribute('aria-invalid', 'true')
    expect(email).not_to_have_css('border-color', border)
    print('PASS: shared Input/Button styles, label association, keyboard focus and invalid state', flush=True)

def verify_shared_dialog(page, app):
    trigger = page.get_by_role('button', name='外观设置', exact=True)
    trigger.focus()
    trigger.press('Enter')
    dialog = page.get_by_role('dialog', name='外观设置', exact=True)
    expect(dialog).to_be_visible()
    expect(dialog).to_have_css('position', 'fixed')
    expect(dialog).to_have_css('opacity', '1')
    expect(dialog).not_to_have_css('background-color', 'rgba(0, 0, 0, 0)')
    expect(page.locator('[data-slot=dialog-overlay]')).to_be_visible()
    expect(page.locator('[data-slot=dialog-overlay]')).to_have_css('opacity', '1')
    close = dialog.get_by_role('button', name='关闭', exact=True)
    expect(close).to_be_visible()
    close.focus()
    page.keyboard.press('Tab')
    assert dialog.evaluate('(el) => el.contains(document.activeElement)'), 'Focus escaped the dialog'
    page.keyboard.press('Shift+Tab')
    assert dialog.evaluate('(el) => el.contains(document.activeElement)')
    box = dialog.bounding_box()
    assert box and box['width'] <= page.viewport_size['width'] - 16
    mode = 'dev' if os.environ.get('T008_USE_DEV') == '1' else 'prod'
    page.screenshot(path=str(OUTPUT / ('ui-' + app + '-' + mode + '.png')), full_page=True, animations='disabled')
    page.keyboard.press('Escape')
    expect(dialog).to_be_hidden()
    expect(trigger).to_be_focused()
    trigger.click()
    expect(dialog).to_be_visible()
    dialog.get_by_role('button', name='关闭', exact=True).click()
    expect(dialog).to_be_hidden()
    expect(trigger).to_be_focused()
    print('PASS: ' + app + ' shared Dialog styling, Chinese label, focus trap, Escape/click close and focus restore', flush=True)

def verify_theme_tokens(page, context):
    token = page.evaluate("sessionStorage.getItem('cloudlane.accessToken')")
    headers = {'Authorization': 'Bearer ' + token}
    created = context.request.post(BASE + '/api/v1/spaces', data={'name': 'Theme tokens'}, headers=headers)
    assert created.status == 200
    space = created.json()['data']
    folder = context.request.post(BASE + '/api/v1/spaces/' + space + '/folders',
                                  data={'parentId': '0', 'name': 'Theme row'}, headers=headers)
    assert folder.status == 200
    workspace = BASE + '/app/spaces/' + space
    page.goto(workspace)
    row = page.locator('.list .file-item').first
    expect(row).to_be_visible()
    expect(row).to_have_css('height', '48px')
    expect(page.locator('.drive-app')).to_have_css('font-size', '14px')
    expect(page.locator('.drive-topbar')).to_have_css('height', '56px')
    expect(page.locator('.drive-sidebar')).to_have_css('width', '240px')
    upload = page.locator('.upload-main')
    expect(upload).to_be_enabled()
    expect(upload).to_have_css('height', '36px')
    assert page.locator('body').evaluate('(el) => getComputedStyle(el).fontFamily') == page.locator('.drive-app').evaluate('(el) => getComputedStyle(el).fontFamily')
    mode = 'dev' if os.environ.get('T008_USE_DEV') == '1' else 'prod'
    def appearance():
        page.get_by_role('button', name='外观设置', exact=True).click()
        dialog = page.get_by_role('dialog', name='外观设置', exact=True)
        expect(dialog).to_be_visible()
        expect(dialog).to_have_css('opacity', '1')
        return dialog
    def primary(value):
        expect(page.locator('html')).to_have_css('--primary', value)
    dialog = appearance()
    light = [('海蓝', '#2563eb'), ('紫罗兰', '#7c3aed'), ('森林', '#15803d'), ('玫瑰', '#be185d'), ('暖橙', '#c2410c')]
    dark = [('海蓝', '#60a5fa'), ('紫罗兰', '#a78bfa'), ('森林', '#4ade80'), ('玫瑰', '#fb7185'), ('暖橙', '#fb923c')]
    for label, pairs in [('浅色', light), ('深色', dark)]:
        dialog.get_by_role('button', name=label, exact=True).click()
        for name, color in pairs:
            dialog.get_by_role('button', name=name, exact=True).click()
            primary(color)
    dialog.get_by_role('button', name='浅色', exact=True).click()
    dialog.get_by_role('button', name='紫罗兰', exact=True).click()
    dialog.get_by_role('button', name='直角', exact=True).click()
    dialog.get_by_label('紧凑文件列表', exact=True).check()
    expect(dialog).to_have_css('border-top-left-radius', '0px')
    expect(dialog.get_by_role('button', name='浅色', exact=True)).to_have_css('border-top-left-radius', '0px')
    expect(row).to_have_css('height', '36px')
    expect(upload).to_have_css('height', '36px')
    page.screenshot(path=str(OUTPUT / ('theme-light-' + mode + '.png')), full_page=True, animations='disabled')
    page.keyboard.press('Escape')
    page.reload()
    expect(row).to_have_css('height', '36px')
    primary('#7c3aed')
    expect(upload).to_have_css('border-top-left-radius', '0px')
    dialog = appearance()
    expect(dialog.get_by_role('button', name='直角', exact=True)).to_have_attribute('aria-pressed', 'true')
    dialog.get_by_role('button', name='深色', exact=True).click()
    dialog.get_by_role('button', name='森林', exact=True).click()
    dialog.get_by_role('button', name='16px', exact=True).click()
    dialog.get_by_label('紧凑文件列表', exact=True).uncheck()
    expect(row).to_have_css('height', '48px')
    expect(dialog).to_have_css('border-top-left-radius', '16px')
    expect(dialog).to_have_css('background-color', 'rgb(16, 17, 20)')
    primary('#4ade80')
    page.screenshot(path=str(OUTPUT / ('theme-dark-' + mode + '.png')), full_page=True, animations='disabled')
    page.keyboard.press('Escape')
    expect(upload).to_have_css('background-color', 'rgb(74, 222, 128)')
    expect(upload).to_have_css('color', 'rgb(16, 17, 20)')
    upload.hover()
    expect(upload).not_to_have_css('background-color', 'rgb(74, 222, 128)')
    page.mouse.move(0, 0)
    expect(upload).to_have_css('background-color', 'rgb(74, 222, 128)')
    expect(upload).to_have_css('height', '36px')
    # Full-page admin navigation and a second tab both restore the same preferences.
    page.goto(BASE + '/admin/')
    expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
    primary('#4ade80')
    expect(page.locator('body')).to_have_css('background-color', 'rgb(16, 17, 20)')
    dialog = appearance()
    expect(dialog).to_have_css('border-top-left-radius', '16px')
    page.screenshot(path=str(OUTPUT / ('theme-admin-dark-' + mode + '.png')), full_page=True, animations='disabled')
    page.keyboard.press('Escape')
    page.goto(workspace)
    expect(row).to_have_css('height', '48px')
    other = context.new_page()
    try:
        other.goto(BASE + '/auth/login')
        expect(other.locator('html')).to_have_class('dark')
        expect(other.locator('html')).to_have_css('--primary', '#4ade80')
        dialog = appearance()
        dialog.get_by_role('button', name='暖橙', exact=True).click()
        expect(other.locator('html')).to_have_css('--primary', '#fb923c')
        dialog.get_by_role('button', name='跟随系统', exact=True).click()
        page.emulate_media(color_scheme='light')
        expect(page.locator('html')).not_to_have_class('dark')
        primary('#c2410c')
        page.emulate_media(color_scheme='dark')
        expect(page.locator('html')).to_have_class('dark')
        primary('#fb923c')
        assert page.evaluate("JSON.parse(localStorage.getItem('cloudlane.theme')).mode") == 'system'
        dialog.get_by_role('button', name='海蓝', exact=True).click()
        dialog.get_by_role('button', name='12px', exact=True).click()
        page.keyboard.press('Escape')
        page.emulate_media(color_scheme='light')
        page.evaluate("localStorage.setItem('cloudlane.theme', 'broken JSON')")
        page.reload()
        primary('#2563eb')
        expect(row).to_have_css('height', '48px')
        expect(page.locator('html')).to_have_css('--radius', '0.75rem')
    finally:
        other.close()
    print('PASS: theme color pairs, zero radius, row density, persistence, admin consistency, cross-tab sync, OS changes and corrupt-preference recovery', flush=True)


def verify_three_pane_layout(page):
    mode = 'dev' if os.environ.get('T008_USE_DEV') == '1' else 'prod'
    sidebar = page.locator('.drive-sidebar')
    tree = page.locator('.drive-tree-pane')
    main = page.locator('.drive-main')
    resizer = page.get_by_role('separator', name='调整目录树宽度')

    page.set_viewport_size({'width': 1280, 'height': 720})
    expect(sidebar).to_have_css('width', '240px')
    expect(tree).to_have_css('width', '260px')
    assert page.evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), 'Desktop overflow'
    initial_main = main.bounding_box()['width']

    # Keyboard resize + persistence + double-click reset
    resizer.focus()
    page.keyboard.press('ArrowRight')
    expect(tree).to_have_css('width', '276px')
    page.reload()
    expect(tree).to_have_css('width', '276px')
    resizer.dblclick()
    expect(tree).to_have_css('width', '260px')

    # Collapse tree & sidebar and verify main area expands
    page.get_by_role('button', name='收起目录树', exact=True).click()
    expect(tree).to_have_css('width', '0px')
    after_tree_collapse = main.bounding_box()['width']
    assert after_tree_collapse > initial_main + 200
    page.get_by_role('button', name='收起导航栏', exact=True).click()
    expect(sidebar).to_have_css('width', '0px')
    after_both_collapse = main.bounding_box()['width']
    assert after_both_collapse > after_tree_collapse + 200
    page.get_by_role('button', name='展开导航栏', exact=True).click()
    page.get_by_role('button', name='展开目录树', exact=True).click()
    expect(sidebar).to_have_css('width', '240px')
    expect(tree).to_have_css('width', '260px')
    page.screenshot(path=str(OUTPUT / ('layout-desktop-' + mode + '.png')), full_page=True, animations='disabled')

    # Tablet breakpoint: sidebar visible, tree as overlay drawer
    page.set_viewport_size({'width': 900, 'height': 720})
    expect(sidebar).to_have_css('width', '216px')
    assert page.evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), 'Tablet overflow'
    page.get_by_role('button', name='展开目录树', exact=True).click()
    expect(tree).to_have_class(re.compile(r'is-open'))
    page.keyboard.press('Escape')
    expect(tree).not_to_have_class(re.compile(r'is-open'))

    # Mobile breakpoint: both panes as drawers, main takes full width
    page.set_viewport_size({'width': 390, 'height': 844})
    assert main.bounding_box()['width'] >= 380
    assert page.evaluate('document.documentElement.scrollWidth <= document.documentElement.clientWidth'), 'Mobile overflow'
    page.get_by_role('button', name='展开导航栏', exact=True).click()
    expect(sidebar).to_have_class(re.compile(r'is-open'))
    page.screenshot(path=str(OUTPUT / ('layout-mobile-drawer-' + mode + '.png')), full_page=False, animations='disabled')
    page.locator('.drive-drawer-backdrop').click(position={'x': 380, 'y': 700})
    expect(sidebar).not_to_have_class(re.compile(r'is-open'))
    page.get_by_role('button', name='展开目录树', exact=True).click()
    expect(tree).to_have_class(re.compile(r'is-open'))
    page.keyboard.press('Escape')
    expect(tree).not_to_have_class(re.compile(r'is-open'))

    # Restore desktop viewport for remaining checks
    page.set_viewport_size({'width': 1280, 'height': 720})
    expect(sidebar).to_have_css('width', '240px')
    expect(tree).to_have_css('width', '260px')
    print('PASS: three-pane desktop layout, tree resize persistence, pane collapse, tablet/mobile drawers without overflow', flush=True)


def run_browser():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, channel=os.environ.get('T008_BROWSER_CHANNEL', 'msedge'))
        try:
            context = browser.new_context()
            page = context.new_page()
            errors = []
            page.on('pageerror', lambda e: errors.append(str(e)))
            # T009 startup: use the real app entry and lazy route components.
            page.goto(BASE + '/')
            page.wait_for_url('**/auth/login?returnTo=**')
            expect(page.locator('input[type=email]')).to_be_visible()
            assert page.locator('html').get_attribute('lang') == 'zh-CN'
            assert page.title() == 'Cloudlane 云端网盘'
            page.goto(BASE + '/auth')
            page.wait_for_url('**/auth/login')
            expect(page.locator('input[type=email]')).to_be_visible()
            page.goto(BASE + '/auth/register')
            expect(page.locator('form')).to_be_visible()
            for missing in ['/missing', '/app/missing', '/app/spaces/not-an-id']:
                page.goto(BASE + missing)
                expect(page.get_by_role('heading', name='404 · 页面不存在')).to_be_visible()
                assert page.url == BASE + missing
            for asset in ['/favicon.svg', '/admin/favicon.svg']:
                response = context.request.get(BASE + asset)
                assert response.status == 200 and 'image/svg+xml' in response.headers['content-type']
            print('PASS: startup, auth default route, registration page, unknown routes, Chinese language and icons', flush=True)
            page.goto(BASE + '/admin/users?page=2')
            page.wait_for_url('**/auth/login?returnTo=**')
            expect(page.locator('input[type=email]')).to_be_visible()
            print('Observed login heading:', page.locator('h1').inner_text(), flush=True)
            verify_login_components(page)
            def login(email):
                page.locator('input[type=email]').fill(email)
                page.locator('input[type=password]').fill(os.environ['T008_LOGIN_PASSWORD'])
                with page.expect_response(lambda r: r.url.endswith('/api/admin/auth/login/email')) as pending:
                    page.locator('button[type=submit]').click()
                response = pending.value
                assert response.status == 200, 'Login HTTP status: ' + str(response.status)
            def refresh_cookie():
                return next(c for c in context.cookies() if c['name'] == 'cloudlane.refresh')
            login('admin-t008@example.test')
            page.wait_for_url('**/admin/users?page=2')
            expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
            page.wait_for_load_state('networkidle')
            print('Observed admin heading:', page.locator('h1').inner_text(), flush=True)
            next_page = page.get_by_role('button', name='下一页', exact=True)
            expect(next_page).to_be_disabled()
            expect(next_page).to_have_css('opacity', '0.5')
            # T013: verify independent admin layout navigation across dashboard, users, and planned routes.
            page.get_by_role('link', name='仪表盘', exact=True).click()
            page.wait_for_url('**/admin/')
            expect(page.get_by_role('heading', name='系统概览', exact=True)).to_be_visible()
            page.locator('nav a[href="/admin/storage"]').click()
            page.wait_for_url('**/admin/storage')
            expect(page.get_by_role('heading', name='存储配置', exact=True)).to_be_visible()
            page.get_by_role('link', name='用户管理', exact=True).click()
            page.wait_for_url('**/admin/users')
            expect(page.get_by_role('heading', name='用户管理', exact=True)).to_be_visible()
            verify_shared_dialog(page, 'admin')
            # The default /app child must render the real workbench after returning from admin.
            page.get_by_role('link', name='返回网盘', exact=True).click()
            page.wait_for_url('**/app')
            expect(page.locator('.drive-app')).to_be_visible()
            verify_shared_dialog(page, 'web')
            verify_theme_tokens(page, context)
            verify_three_pane_layout(page)
            expect(page.get_by_role('link', name='管理后台', exact=True)).to_be_visible()
            page.get_by_role('link', name='管理后台', exact=True).click()
            expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
            page.goto(BASE + '/admin/users?page=2')
            expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
            print('PASS: /app renders workbench and same-origin app navigation works', flush=True)
            access = page.evaluate("sessionStorage.getItem('cloudlane.accessToken')")
            identity = context.request.get(BASE + '/api/v1/me', headers={'Authorization': 'Bearer ' + access})
            assert identity.status == 200 and identity.json()['data']['id'] == os.environ['T008_ADMIN_ID']
            first = refresh_cookie()
            assert first['httpOnly'] and first['sameSite'] == 'Strict' and first['path'] == '/api/v1/auth'
            assert 'cloudlane.refresh' not in page.evaluate('document.cookie')
            page.reload()
            expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
            page.wait_for_load_state('networkidle')
            # A signed expired JWT must trigger the shared refresh flow using the real HttpOnly cookie.
            page.evaluate("(value) => sessionStorage.setItem('cloudlane.accessToken', value)", expired_token())
            page.reload()
            expect(page.get_by_role('heading', name='Cloudlane 管理后台')).to_be_visible()
            page.wait_for_load_state('networkidle')
            rotated = refresh_cookie()
            assert rotated['value'] != first['value']
            new_access = page.evaluate("sessionStorage.getItem('cloudlane.accessToken')")
            assert new_access and new_access != access
            isolated = p.request.new_context()
            try:
                reused = isolated.post(BASE + '/api/v1/auth/refresh', headers={
                    'Cookie': 'cloudlane.refresh=' + first['value'], 'X-Requested-With': 'Cloudlane',
                })
                assert reused.status == 401, 'Old rotated refresh cookie must be rejected'
                page.screenshot(path=str(OUTPUT / ('admin-dev.png' if os.environ.get('T008_USE_DEV') == '1' else 'admin.png')), full_page=True)
                page.get_by_role('button', name='退出登录', exact=True).click()
                page.wait_for_url('**/auth/login?returnTo=**')
                expect(page.locator('input[type=email]')).to_be_visible()
                assert page.evaluate("sessionStorage.getItem('cloudlane.accessToken')") is None
                assert not [c for c in context.cookies() if c['name'] == 'cloudlane.refresh']
                revoked = isolated.get(BASE + '/api/v1/me', headers={'Authorization': 'Bearer ' + new_access})
                assert revoked.status == 401, 'Logout must revoke the old access token on the server'
                revoked_refresh = isolated.post(BASE + '/api/v1/auth/refresh', headers={
                    'Cookie': 'cloudlane.refresh=' + rotated['value'], 'X-Requested-With': 'Cloudlane',
                })
                assert revoked_refresh.status == 401
                page.goto(BASE + '/admin/users')
                page.wait_for_url('**/auth/login?returnTo=**')
                expect(page.locator('input[type=email]')).to_be_visible()
                login('member-t008@example.test')
                page.wait_for_url('**/admin/404')
                expect(page.get_by_role('heading', name='404 · 页面不存在')).to_be_visible()
                member_access = page.evaluate("sessionStorage.getItem('cloudlane.accessToken')")
                member = isolated.get(BASE + '/api/v1/me', headers={'Authorization': 'Bearer ' + member_access})
                assert member.status == 200 and member.json()['data']['id'] == os.environ['T008_MEMBER_ID']
                forbidden = isolated.get(BASE + '/api/v1/admin/stats', headers={'Authorization': 'Bearer ' + member_access})
                assert forbidden.status == 403, 'Admin API must enforce the role on the server'
                assert not errors, errors
                print('PASS: real login return, deep refresh, HttpOnly cookie rotation, replay rejection, server logout revocation, member404/API403', flush=True)
            finally:
                isolated.dispose()
        except Exception:
            page.screenshot(path=str(OUTPUT / 'latest-failure.png'), full_page=True)
            print('Failed browser URL:', page.url, flush=True)
            print('Visible page:', page.locator('body').inner_text()[:800], flush=True)
            raise
        finally:
            browser.close()

def run_dev():
    frontend = ROOT / 'frontend'
    env = os.environ.copy()
    env['API_PROXY_TARGET'] = 'http://127.0.0.1:' + os.environ['T008_BACKEND_PORT']
    node = shutil.which('node') or 'node'
    processes = []
    with tempfile.TemporaryDirectory(prefix='cloud-t008-vite-') as directory:
        try:
            for app, arguments in [('admin', []), ('web', ['--port', '3010'])]:
                log = open(Path(directory) / (app + '.log'), 'w', encoding='utf-8')
                try:
                    proc = subprocess.Popen([node, str(frontend / 'apps' / app / 'node_modules/vite/bin/vite.js'), *arguments],
                                            cwd=frontend / 'apps' / app, env=env, stdout=log, stderr=subprocess.STDOUT,
                                            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0)
                    processes.append(proc)
                finally:
                    log.close()
            for attempt in range(60):
                if any(proc.poll() is not None for proc in processes):
                    raise RuntimeError('Temporary Vite process exited')
                try:
                    with urlopen(BASE + '/admin/users', timeout=2) as response:
                        if response.status == 200 and 'Cloudlane' in response.read().decode():
                            break
                except OSError:
                    time.sleep(0.3)
            else:
                raise RuntimeError('Development proxy did not start')
            run_browser()
        finally:
            for proc in reversed(processes):
                if proc.poll() is None:
                    if os.name == 'nt':
                        subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True, timeout=10)
                    else:
                        proc.terminate()
                    proc.wait(timeout=10)

def main():
    if os.environ.get('T008_USE_DEV') == '1':
        run_dev()
        return
    name = 'cloud-t008-live-' + uuid.uuid4().hex[:10]
    with tempfile.TemporaryDirectory(prefix='cloud-t008-nginx-') as directory:
        config = Path(directory) / 'nginx.conf'
        config.write_text((ROOT / 'frontend/nginx.conf').read_text(encoding='utf-8').replace(
            'http://api:8000', 'http://host.docker.internal:' + os.environ['T008_BACKEND_PORT']), encoding='utf-8')
        try:
            image = os.environ.get('T008_FRONTEND_IMAGE', 'cloudlane-t008-frontend:verify')
            mounts = []
            if os.environ.get('T008_USE_LOCAL_DIST') == '1':
                image = 'nginx:1.28-alpine'
                html = Path(directory) / 'html'
                shutil.copytree(ROOT / 'frontend/apps/web/dist', html)
                shutil.copytree(ROOT / 'frontend/apps/admin/dist', html / 'admin', dirs_exist_ok=True)
                mounts = ['--mount', f'type=bind,source={html},target=/usr/share/nginx/html,readonly']
            command('docker', 'run', '--rm', '-d', '--name', name, '-p', '127.0.0.1:3088:80',
                    '--mount', f'type=bind,source={config},target=/etc/nginx/conf.d/default.conf,readonly',
                    *mounts, image)
            for attempt in range(30):
                try:
                    with urlopen(BASE + '/auth/login', timeout=2) as response:
                        if response.status == 200:
                            break
                except OSError:
                    time.sleep(0.3)
            else:
                raise RuntimeError('Temporary Nginx did not start')
            command('docker', 'exec', name, 'nginx', '-t')
            run_browser()
        finally:
            subprocess.run(['docker', 'stop', name], capture_output=True, timeout=20)

if __name__ == '__main__':
    main()
