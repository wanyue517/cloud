"""Exercise mobile private browsing after server-side idle expiry."""
import os
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('PRIVATE_MOBILE_URL', 'http://127.0.0.1:3016/m')
SPACE = '2124534695294148609'
FOLDER = '2124534695294148610'
valid_token = None
issued = 0
node_tokens = []

def api(route):
    global valid_token, issued
    request = route.request
    path = urlparse(request.url).path
    token = request.headers.get('x-private-token')
    space = {'id': SPACE, 'kind': 'PRIVATE', 'name': '测试私密空间', 'privateEnabled': True, 'role': 'OWNER'}
    if path.endswith('/me/private/unlock'):
        issued += 1
        valid_token = f'mobile-fresh-{issued}'
        data = {'privateToken': valid_token, 'idleMinutes': 15}
    elif path.endswith('/me/private/overview') or path.endswith('/nodes'):
        if not token or token != valid_token:
            route.fulfill(status=423, json={'code': 423, 'msg': '私密授权已过期'})
            return
        if path.endswith('/overview'):
            data = {'spaces': [space], 'items': []}
        else:
            node_tokens.append(token)
            data = {'items': [{'id': FOLDER, 'spaceId': SPACE, 'parentId': '0', 'nodeType': 'FOLDER',
                'name': '私密子目录', 'size': 0, 'updatedAt': '2026-11-29T00:00:00Z', 'revision': 0,
                'currentVersionId': None, 'hidden': False}], 'total': 1, 'page': 1, 'pageSize': 100}
    elif path.endswith('/spaces'):
        data = [space]
    elif path.endswith('/me'):
        data = {'id': '1', 'displayName': 'Test', 'admin': False}
    elif path.endswith('/site-config'):
        data = {'siteName': 'Test'}
    else:
        data = []
    route.fulfill(json={'code': 0, 'data': data})

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel='msedge')
    context = browser.new_context(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
    context.add_init_script("sessionStorage.setItem('cloudlane.accessToken','fixture')")
    context.route('**/api/v1/**', api)
    page = context.new_page()
    page.goto(BASE + '/private')
    for attempt in range(2):
        page.get_by_placeholder('请输入账号登录密码', exact=True).fill('Vault#2026')
        page.get_by_role('button', name='验证并进入', exact=True).click()
        page.get_by_role('button', name='测试私密空间 点击进入浏览与上传').click()
        expect(page.get_by_role('button', name='私密子目录', exact=False)).to_be_visible()
        assert node_tokens[-1] == f'mobile-fresh-{attempt + 1}'
        if attempt == 0:
            valid_token = 'expired'
            page.get_by_role('button', name='私密子目录', exact=False).click()
            expect(page.get_by_role('heading', name='解锁私密保险箱')).to_be_visible()
    print('Mobile private space re-unlocks after idle expiry and requests carry the fresh credential')
    browser.close()
