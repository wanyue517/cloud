"""Run after CAPACITOR_BUILD=1 web build and cap sync ios on a Mac with Xcode >=26.

Produces a genuine iphoneos app in an unsigned IPA for local Sideloadly signing.
No Apple account, certificate, provisioning profile, or signing secret is used.
"""
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tempfile


def choose_xcode():
    candidates = []
    for app in Path('/Applications').glob('Xcode*.app'):
        developer = app / 'Contents/Developer'
        try:
            version = subprocess.check_output([str(developer / 'usr/bin/xcodebuild'), '-version'], text=True, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError:
            continue
        match = re.search(r'Xcode (\d+)(?:\.(\d+))?(?:\.(\d+))?', version)
        if match and int(match[1]) >= 26:
            candidates.append((tuple(int(part or 0) for part in match.groups()), developer))
    if not candidates:
        raise SystemExit('Capacitor 8 requires Xcode 26+. Choose a macOS runner with Xcode 26 or newer.')
    return max(candidates, key=lambda item: item[0])[1]


def main():
    if platform.system() != 'Darwin':
        raise SystemExit('iOS compilation requires macOS + Xcode. On Windows, run the Build iOS IPA for local signing workflow.')
    root = Path(__file__).resolve().parent.parent
    project = root / 'frontend/apps/mobile/ios/App/App.xcodeproj'
    output = root / 'release-dist/ios'
    output.mkdir(parents=True, exist_ok=True)
    environment = {**os.environ, 'DEVELOPER_DIR': str(choose_xcode())}
    print(f"Using {environment['DEVELOPER_DIR']}", flush=True)
    with tempfile.TemporaryDirectory(prefix='cloudlane-ios-') as temporary:
        build = Path(temporary) / 'DerivedData'
        command = [
            'xcodebuild', '-project', str(project), '-scheme', 'App',
            '-configuration', 'Release', '-sdk', 'iphoneos',
            '-destination', 'generic/platform=iOS', '-derivedDataPath', str(build),
            'CODE_SIGNING_ALLOWED=NO', 'CODE_SIGNING_REQUIRED=NO', 'CODE_SIGN_IDENTITY=',
            f"CURRENT_PROJECT_VERSION={os.environ.get('GITHUB_RUN_NUMBER', '1')}", 'build',
        ]
        with (output / 'xcodebuild.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=environment)
            for line in process.stdout:
                print(line, end='')
                log.write(line)
            if process.wait() != 0:
                raise SystemExit('Xcode build failed. See release-dist/ios/xcodebuild.log.')
        application = build / 'Build/Products/Release-iphoneos/App.app'
        if not (application / 'App').is_file():
            raise SystemExit('The iphoneos App.app executable was not produced.')
        payload = Path(temporary) / 'Payload'
        payload.mkdir()
        shutil.copytree(application, payload / 'App.app', symlinks=True)
        ipa = output / 'Cloudlane-unsigned.ipa'
        subprocess.run(['ditto', '-c', '-k', '--keepParent', str(payload), str(ipa)], check=True)
        print(f'Created {ipa}. Sign and install with Sideloadly on Windows; this IPA is not directly installable.')


if __name__ == '__main__':
    main()
