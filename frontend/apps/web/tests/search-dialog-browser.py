"""Search opens independently of routing and preserves the background directory."""
import os
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

BASE = os.environ.get('SEARCH_WEB_URL', 'http://127.0.0.1:3015')
SPACE = '2124534695294148609'
FOLDER = '2124534695294148610'
searches = []

def node(index):
    return {'id': str(2124534695294150000 + index), 'spaceId': SPACE, 'parentId': FOLDER,
        'name': f'报告{index}.txt', 'nodeType': 'FILE', 'size': 10, 'mimeType': 'text/plain',
        'currentVersionId': '9', 'revision': 0, 'hidden': False, 'updatedAt': '2026-11-29T00:00:00Z'}

def api(route):
    path = urlparse(route.request.url).path
    params = parse_qs(urlparse(route.request.url).query)
    if path.endswith('/site-config'):
        data = {'siteName': 'Test'}
    elif path.endswith('/me'):
        data = {'id': '1', 'displayName': 'Test', 'admin': False}
    elif path.endswith('/spaces'):
        data = [{'id': SPACE, 'kind': 'PERSONAL', 'name': '我的空间', 'role': 'OWNER'}]
    elif path.endswith('/stats'):
        data = {'folders': 0, 'files': 60, 'categories': {}}
    elif path.endswith('/nodes'):
        items = [] if params.get('folders') == ['true'] else [node(i) for i in range(60)]
        data = {'items': items, 'total': len(items), 'page': 1, 'pageSize': 100}
    elif path.endswith('/ancestors'):
        data = []
    elif path.endswith('/search'):
        searches.append(params)
        hit = {**node(99), 'parentId': '0', 'name': 'needle.txt', 'spaceName': '我的空间', 'matchSource': 'NAME', 'snippet': 'needle', 'category': 'CODE'}
        data = {'items': [hit], 'total': 1, 'page': 1, 'pageSize': 50, 'hasMore': False}
    elif path.endswith('/text'):
        data = {'content': 'needle preview', 'offset': 0, 'bytesRead': 14, 'totalBytes': 14, 'hasMore': False, 'binaryDetected': False}
    else:
        data = []
    route.fulfill(json={'code': 0, 'data': data})

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel='msedge')
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.add_init_script("sessionStorage.setItem('cloudlane.accessToken','fixture')")
    context.route('**/api/v1/**', api)
    page = context.new_page()
    initial_url = BASE + f'/app/spaces/{SPACE}/folders/{FOLDER}?keep=1#position'
    page.goto(initial_url)
    expect(page.locator('.file-item')).to_have_count(60)
    page.get_by_label('选择 报告0.txt', exact=True).check()
    page.locator('.drive-content').evaluate('el => el.scrollTop = 300')
    scroll_top = page.locator('.drive-content').evaluate('el => el.scrollTop')
    history_length = page.evaluate('history.length')
    trigger = page.get_by_role('button', name='搜索当前空间或全局检索', exact=True)
    trigger.click()
    dialog = page.get_by_role('dialog', name='搜索与检索', exact=True)
    expect(dialog).to_be_visible()
    expect(dialog.get_by_label('搜索关键词', exact=True)).to_be_focused()
    assert page.url == initial_url and page.evaluate('history.length') == history_length
    dialog.get_by_label('搜索关键词', exact=True).fill('needle')
    expect(dialog.get_by_text('needle.txt', exact=True)).to_be_visible()
    assert searches[-1]['spaceId'] == [SPACE]
    dialog.get_by_role('tab', name='全局检索', exact=True).click()
    page.wait_for_timeout(350)
    assert 'spaceId' not in searches[-1]
    dialog.get_by_role('button', name='预览', exact=True).click()
    expect(page.get_by_role('button', name='关闭预览', exact=True)).to_be_visible()
    assert page.url == initial_url
    page.get_by_role('button', name='关闭预览', exact=True).click()
    expect(dialog).to_be_visible()
    expect(dialog.get_by_text('needle.txt', exact=True)).to_be_visible()
    dialog.get_by_role('button', name='返回原页面', exact=True).click()
    expect(dialog).to_have_count(0)
    expect(trigger).to_be_focused()
    assert page.url == initial_url and page.evaluate('history.length') == history_length
    assert page.locator('.drive-content').evaluate('el => el.scrollTop') == scroll_top
    expect(page.get_by_label('选择 报告0.txt', exact=True)).to_be_checked()
    for close in ['escape', 'button', 'outside']:
        trigger.click()
        expect(dialog).to_be_visible()
        if close == 'escape': page.keyboard.press('Escape')
        elif close == 'button': dialog.get_by_role('button', name='关闭', exact=True).click()
        else: page.mouse.click(5, 5)
        expect(dialog).to_have_count(0)
        assert page.url == initial_url
    page.keyboard.press('Control+k')
    expect(dialog).to_be_visible()
    dialog.get_by_label('搜索关键词', exact=True).fill('needle')
    dialog.get_by_role('button', name='定位目录', exact=True).click()
    expect(dialog).to_have_count(0)
    page.wait_for_url(BASE + f'/app/spaces/{SPACE}')
    print('Search closure, focus, selection, scroll, scope, preview, and explicit locate passed')
    browser.close()
