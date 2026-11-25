"""Frontend interaction regression with deterministic API fixtures; backend contracts have separate MySQL tests."""
import json
from pathlib import Path
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright, expect

OUTPUT = Path('frontend/apps/web/test-results')
OUTPUT.mkdir(parents=True, exist_ok=True)
SPACE = '2124534695294148609'
nodes = []
serial = 100

def node(name, folder=False, parent='0'):
    global serial
    serial += 1
    value = dict(id=f'2124534695294149{serial}', spaceId=SPACE, parentId=parent, name=name,
                 nodeType='FOLDER' if folder else 'FILE', currentVersionId=None if folder else '999',
                 revision=0, size=0 if folder else 14, mimeType='' if folder else 'text/plain',
                 hidden=False, updatedAt='2026-11-25T08:00:00Z', hasChildren=False)
    nodes.append(value)
    return value

node('项目资料', True)
node('使用说明.md')
uploads = {}

def api(route):
    request = route.request
    parsed = urlparse(request.url)
    path, params = parsed.path, parse_qs(parsed.query)
    method = request.method
    if not request.headers.get('authorization', '').startswith('Bearer '):
        route.fulfill(status=401, json={'code': 401, 'msg': '请先登录', 'data': None})
        return
    data = None
    if path.endswith('/me'):
        data = dict(id='1', displayName='测试用户', admin=False)
    elif path.endswith('/spaces'):
        data = [dict(id=SPACE, name='我的空间', kind='PERSONAL', color='#6366f1', sortOrder=0)]
    elif path.endswith('/content'):
        route.fulfill(body='安全的文本预览', content_type='text/plain;charset=utf-8'); return
    elif path.endswith('/ancestors'):
        target = path.split('/')[-2]
        data = [n for n in nodes if n['id'] == target]
    elif path.endswith('/nodes'):
        parent = params.get('parentId', ['0'])[0]
        search = params.get('query', [''])[0]
        values = [n for n in nodes if n['parentId'] == parent and search in n['name']]
        if params.get('folders') == ['true']: values = [n for n in values if n['nodeType'] == 'FOLDER']
        data = dict(items=values, total=len(values), page=1, pageSize=100)
    elif path.endswith('/folders') and method == 'POST':
        payload = request.post_data_json
        data = node(payload['name'], True, payload['parentId'])
    elif '/nodes/' in path and method == 'PATCH':
        payload = request.post_data_json
        data = next(n for n in nodes if n['id'] == path.split('/')[-1])
        data.update(name=payload['name'], parentId=payload['parentId'], revision=data['revision'] + 1)
    elif path.endswith('/uploads') and method == 'POST':
        payload = request.post_data_json
        data = dict(id='55', chunkSize=4194304, chunkCount=1, received=[], status='UPLOADING', fileId=None)
        uploads['55'] = payload
    elif '/uploads/55/chunks/' in path: pass
    elif path.endswith('/uploads/55/complete'):
        payload = uploads['55']; data = node(payload['name'], False, payload['parentId'])['id']
    else:
        route.fulfill(status=404, json={'code': 404, 'msg': '未配置测试接口 ' + path, 'data': None}); return
    route.fulfill(json={'code': 0, 'msg': '操作成功', 'data': data})

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    context = browser.new_context(viewport={'width': 1440, 'height': 1000})
    context.add_init_script("sessionStorage.setItem('cloudlane.accessToken','browser-test-token')")
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.route('**/api/**', api)
    page.goto('http://127.0.0.1:3000/app')
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='我的空间', exact=True)).to_be_visible()
    expect(page.get_by_role('listitem')).to_have_count(2)
    page.screenshot(path=str(OUTPUT / 'drive-light.png'), full_page=True)
    page.get_by_role('button', name='新建文件夹', exact=True).click()
    form = page.get_by_role('dialog', name='新建文件夹', exact=True)
    form.get_by_role('textbox').fill('验收目录')
    form.get_by_role('button', name='确认', exact=True).click()
    expect(page.get_by_role('listitem').filter(has_text='验收目录')).to_be_visible()
    page.get_by_role('listitem').filter(has_text='验收目录').dblclick()
    expect(page.get_by_role('navigation', name='目录路径')).to_contain_text('验收目录')
    page.locator('input[type=file]').first.set_input_files({'name': '上传验收.txt', 'mimeType': 'text/plain', 'buffer': b'upload fixture'})
    expect(page.get_by_role('listitem').filter(has_text='上传验收.txt')).to_be_visible()
    page.get_by_role('listitem').filter(has_text='上传验收.txt').dblclick()
    expect(page.get_by_role('dialog').filter(has_text='上传验收.txt')).to_be_visible()
    expect(page.locator('pre')).to_have_text('安全的文本预览')
    page.get_by_role('button', name='关闭预览').click()
    page.get_by_role('button', name='外观设置').click()
    theme = page.get_by_role('dialog', name='外观设置', exact=True)
    theme.get_by_role('button', name='深色', exact=True).click()
    theme.get_by_role('button', name='紫罗兰', exact=True).click()
    theme.get_by_role('button', name='16px', exact=True).click()
    theme.get_by_label('紧凑文件列表').check()
    expect(page.locator('html')).to_have_class('dark')
    page.keyboard.press('Escape')
    page.reload(); page.wait_for_load_state('networkidle')
    expect(page.locator('html')).to_have_class('dark')
    assert page.evaluate("getComputedStyle(document.documentElement).getPropertyValue('--primary').trim()") == '#7c3aed'
    assert page.evaluate("getComputedStyle(document.documentElement).getPropertyValue('--radius').trim()") == '1rem'
    page.screenshot(path=str(OUTPUT / 'drive-dark.png'), full_page=True)
    page.set_viewport_size({'width': 760, 'height': 900})
    page.screenshot(path=str(OUTPUT / 'drive-small.png'), full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Horizontal page overflow'
    assert not errors, errors
    browser.close()
print('PASS: folder navigation, upload, preview, shadcn dialogs, persisted theme, small layout; no browser exceptions')
