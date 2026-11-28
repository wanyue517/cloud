"""Run against the Web Vite server; private sessions expire deterministically."""
import os
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('PRIVATE_WEB_URL', 'http://127.0.0.1:3015')
SPACE = '2124534695294148609'
FOLDER = '2124534695294148610'
valid_token = 'initial-vault'
issued = 0
calls = []

def api(route):
    global valid_token, issued
    request = route.request
    path = urlparse(request.url).path
    params = parse_qs(urlparse(request.url).query)
    credential = request.headers.get('x-private-token')
    calls.append((path, credential))
    if path.endswith('/private/unlock'):
        issued += 1
        valid_token = f'fresh-{issued}'
        data = {'privateToken': valid_token, 'idleMinutes': 15, 'expiresAt': '2099-01-01T00:00:00Z'}
    elif path.endswith('/public/site-config'):
        data = {'siteName': 'Test'}
    elif path.endswith('/me/private/overview'):
        data = {'spaces': [{'id': SPACE, 'kind': 'PRIVATE', 'name': '私密空间', 'privateEnabled': True}], 'items': []}
    elif path.endswith('/me'):
        data = {'id': '1', 'displayName': 'Test', 'admin': False}
    elif path.endswith('/spaces'):
        data = [{'id': SPACE, 'kind': 'PRIVATE', 'name': '私密空间', 'role': 'OWNER'}]
    elif '/spaces/' in path:
        if credential != valid_token:
            route.fulfill(status=423, json={'code': 423, 'msg': '私密空间授权已过期或已锁定，请重新解锁'})
            return
        if path.endswith('/stats'):
            data = {'folders': 1, 'files': 0, 'categories': {}}
        elif path.endswith('/nodes'):
            items = [] if params.get('parentId') == [FOLDER] else [{
                'id': FOLDER, 'spaceId': SPACE, 'parentId': '0', 'name': '私密目录',
                'nodeType': 'FOLDER', 'size': 0, 'currentVersionId': None,
                'revision': 0, 'updatedAt': '2026-11-29T00:00:00Z', 'hidden': False, 'hasChildren': True,
            }]
            data = {'items': items, 'total': len(items), 'page': 1, 'pageSize': 100}
        else:
            data = []
    else:
        data = []
    route.fulfill(json={'code': 0, 'data': data})

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel='msedge')
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.add_init_script("sessionStorage.setItem('cloudlane.accessToken','fixture')")
    context.route('**/api/v1/**', api)
    page = context.new_page()
    page.goto(BASE + '/app/settings/private')
    page.wait_for_load_state('networkidle')
    page.get_by_label('当前账号密码', exact=True).fill('Vault#2026')
    page.get_by_role('button', name='验证密码并进入', exact=True).click()
    page.get_by_role('button', name='浏览文件', exact=True).click()
    expect(page.locator('.file-item')).to_have_count(1)
    valid_token = 'expired'
    page.get_by_role('button', name='展开私密目录', exact=True).click()
    expect(page.locator('.folder-tree-viewport')).to_contain_text('已锁定')
    page.get_by_role('button', name='刷新文件', exact=True).click()
    dialog = page.get_by_role('dialog', name='解锁私密空间')
    expect(dialog).to_be_visible()
    dialog.get_by_label('账号密码').fill('Vault#2026')
    dialog.get_by_role('button', name='确认解锁', exact=True).click()
    expect(dialog).not_to_be_visible()
    expect(page.locator('.file-item')).to_have_count(1)
    # Reloading the tree must send the fresh credential, including after expiry.
    expect(page.locator('.folder-tree-viewport')).not_to_contain_text('已锁定')
    assert any(path.endswith('/nodes') and token == 'fresh-2' for path, token in calls)
    print('Private space recovers after idle expiry; listing and tree use fresh credentials')
    browser.close()
