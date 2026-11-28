"""Exercise admin upload mode configuration using mocked HTTP responses only."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
saved = []
uploaded = {}
profile = dict(id='2', name='R2 test', provider='S3', rootPath='s3://test', endpoint='https://storage.example.com',
               bucket='test', region='auto', accessKey='access', hasSecret=True, isDefault=True,
               objectCount=0, totalBytes=0, uploadMode='PROXY')

def api(route):
    url = route.request.url
    data = {}
    if '/storage-profiles' in url:
        if route.request.method == 'PUT':
            saved.append(route.request.post_data_json)
            profile.update(saved[-1])
        else:
            data = [profile]
    elif '/storage-routing' in url:
        data = dict(userFilesProfileId='', siteAssetsProfileId='', userAvatarsProfileId='')
    elif '/me' in url:
        data = dict(admin=True, displayName='Test admin')
    route.fulfill(status=200, content_type='application/json', body=json.dumps(dict(code=0, msg='ok', data=data)))

def storage(route):
    headers = {'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'PUT,OPTIONS',
               'Access-Control-Allow-Headers': 'content-type'}
    if route.request.method == 'PUT':
        request_headers = route.request.headers
        assert 'authorization' not in request_headers
        assert 'cookie' not in request_headers
        assert not any('private' in key.lower() for key in request_headers)
        uploaded[int(route.request.url.rsplit('/', 1)[1])] = route.request.post_data_buffer
    route.fulfill(status=200, headers=headers, body='')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel='msedge')
    page = browser.new_page(viewport=dict(width=1280, height=900))
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.add_init_script("sessionStorage.setItem('cloudlane.accessToken', 'ui-test-only')")
    page.route('**/api/**', api)
    page.route('https://direct-storage.example.test/**', storage)
    page.goto('http://127.0.0.1:5194/admin/storage')
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('button', name='编辑', exact=True)).to_be_visible()
    page.get_by_role('button', name='编辑', exact=True).click()
    page.get_by_role('combobox', name='文件上传方式').click()
    page.get_by_role('option', name='自动选择（大文件直传）').click()
    page.get_by_label('直传大小阈值', exact=True).fill('200')
    page.get_by_label('直传分片大小', exact=True).fill('16')
    page.get_by_label('直传并发数', exact=True).fill('2')
    page.get_by_label('直传地址有效期', exact=True).fill('600')
    page.get_by_role('combobox', name='直传失败处理').click()
    page.get_by_role('option', name='切换中转并重新上传').click()
    page.get_by_role('button', name='保存修改', exact=True).click()
    expect(page.get_by_role('dialog')).not_to_be_visible()
    assert saved[-1]['uploadMode'] == 'AUTO'
    assert saved[-1]['directThresholdBytes'] == 200 * 1048576
    assert saved[-1]['directChunkSize'] == 16 * 1048576
    assert saved[-1]['directConcurrency'] == 2
    assert saved[-1]['directUrlTtl'] == 600
    assert saved[-1]['directFallback'] is True
    page.get_by_role('button', name='编辑', exact=True).click()
    expect(page.get_by_label('直传大小阈值', exact=True)).to_have_value('200')
    for width, height, name in [(1280, 900, 'desktop'), (390, 844, 'mobile')]:
        page.set_viewport_size(dict(width=width, height=height))
        folder = ROOT / 'frontend/test-results/direct-upload'
        folder.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(folder / (name + '.png')), full_page=True)
        assert page.locator('[role="dialog"]').evaluate('(el) => el.getBoundingClientRect().width <= innerWidth')
        assert page.locator('[role="dialog"]').evaluate('(el) => { const r = el.getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight + 1 }')
    assert not errors, errors
    module = '/admin/@fs/' + (ROOT / 'frontend/packages/drive-core/src/direct-upload.ts').as_posix()
    result = page.evaluate('''async modulePath => {
      const { executeDirectUpload } = await import(modulePath);
      const file = new File(['abcdefghijklmnopqrst'], 'test.bin');
      const result = await executeDirectUpload({
        info: async () => ({ received: [], concurrency: 2, fallbackAllowed: false }),
        presign: async (_, index) => ({ uploadUrl: 'https://direct-storage.example.test/parts/' + index }),
        fallback: async () => { throw new Error('unexpected fallback') },
      }, { id: 'browser', status: 'UPLOADING', uploadMode: 'DIRECT', chunkSize: 5, chunkCount: 4 }, file);
      return result.received.sort();
    }''', module)
    assert result == [0, 1, 2, 3]
    assert b''.join(uploaded[index] for index in range(4)) == b'abcdefghijklmnopqrst'
    browser.close()
print('Admin settings and browser cross-origin multipart PUT passed; no application credentials leaked (mock API/storage).')
