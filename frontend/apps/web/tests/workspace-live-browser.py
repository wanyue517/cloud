"""Real Nginx + Spring Boot + MySQL browser integration; invoked by the Java test.
No network interception or service mocks. Test accounts exist only in the disposable database.
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
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
            # The default /app child must render the real workbench after returning from admin.
            page.get_by_role('link', name='返回网盘', exact=True).click()
            page.wait_for_url('**/app')
            expect(page.locator('.drive-app')).to_be_visible()
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
