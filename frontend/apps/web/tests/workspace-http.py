import re
from urllib.request import urlopen
from urllib.error import HTTPError
for base in ['http://localhost:3010','http://localhost:3088']:
    a=urlopen(base+'/admin/users', timeout=10).read().decode()
    w=urlopen(base+'/auth/login', timeout=10).read().decode()
    assert 'Cloudlane' in a and a != w
    if ':3088' in base:
        assets = re.findall(r'(?:src|href)="([^"]+\.(?:js|css))"',a)
        assert assets
        for asset in assets:
            assert asset.startswith('/admin/')
            assert urlopen(base+asset, timeout=10).status == 200
        assert urlopen(base+'/admin').url.endswith('/admin/')
        try: urlopen(base+'/admin/assets/missing.js')
        except HTTPError as e: assert e.code == 404
        else: raise AssertionError('missing asset must be 404')
    else:
        assert urlopen(base+'/admin/src/main.ts').status == 200
    print('PASS:',base,'admin deep route, isolated HTML and assets')
