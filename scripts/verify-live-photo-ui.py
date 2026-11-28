"""Run with the web Vite server on 5173; verifies real Vue state and async cleanup."""
import os
from playwright.sync_api import sync_playwright, expect

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, channel=os.environ.get('PLAYWRIGHT_CHANNEL', 'msedge'))
    for viewport in [dict(width=1280, height=720), dict(width=390, height=844)]:
        page = browser.new_page(viewport=viewport)
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto('http://127.0.0.1:5173/tests/live-photo-harness.html')
        page.wait_for_load_state('networkidle')
        live = page.get_by_role('button', name='◉ LIVE · 播放', exact=True)
        expect(live).to_be_visible()
        expect(page.get_by_role('button', name='添加实况视频')).to_have_count(0)
        page.get_by_role('button', name='保存实况到相册', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('已保存到系统相册')
        assert page.evaluate('window.liveQA.saveCalls') == 1
        page.get_by_role('button', name='下载安卓动态照片', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('已开始下载动态照片')
        assert page.evaluate('window.liveQA.exportCalls') == 1
        page.evaluate('window.liveQA.failSaving()')
        page.get_by_role('button', name='保存实况到相册', exact=True).click()
        expect(page.get_by_role('status')).to_contain_text('相册权限被拒绝')
        live.click()
        expect(page.get_by_role('status')).to_contain_text('播放失败')
        expect(page.locator('video')).to_have_count(0)

        page.evaluate("window.liveQA.setMode('deferred')")
        live.click()
        page.wait_for_function('window.liveQA.addressCalls === 2')
        page.evaluate('window.liveQA.changePhoto(); window.liveQA.resolveAddress()')
        expect(live).to_be_enabled()
        expect(page.locator('video')).to_have_count(0)

        # Release a long press before the signed URL arrives: it must never start later.
        page.mouse.move(200, 300)
        page.mouse.down()
        page.wait_for_function('window.liveQA.addressCalls === 3')
        page.mouse.up()
        page.evaluate('window.liveQA.resolveAddress()')
        expect(live).to_be_enabled()
        expect(page.locator('video')).to_have_count(0)

        page.evaluate("window.liveQA.setMode('none')")
        expect(page.get_by_role('button', name='添加实况视频')).to_be_visible()
        page.locator('input[type=file]').set_input_files(dict(name='wrong.txt', mimeType='text/plain', buffer=b'bad'))
        expect(page.get_by_role('status')).to_contain_text('MOV 或 MP4')
        assert page.evaluate('window.liveQA.uploaded') is False
        page.locator('input[type=file]').set_input_files(dict(name='paired.mov', mimeType='video/quicktime', buffer=b'fixture'))
        expect(page.get_by_role('button', name='◉ LIVE · 处理中')).to_be_visible()
        assert page.evaluate('window.liveQA.uploaded') is True
        page.evaluate("window.liveQA.setMode('ready')")
        expect(live).to_be_visible()
        assert not errors, errors
        page.close()
    browser.close()
print('PASS: desktop/mobile LIVE state, native-save/export actions, permission failure, stale requests, long-press release, file validation and upload')
