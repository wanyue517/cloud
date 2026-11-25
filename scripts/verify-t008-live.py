"""Create a disposable MySQL schema, run the real browser/API test, then drop it.
Reads local connection details without logging them. Never migrates the existing cloud schema.
Requires: mysql CLI, Maven, PyYAML, Playwright/Edge, Docker and the built T008 image.
"""
import argparse
import base64
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
from urllib.parse import urlsplit
import yaml

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=ROOT / 'backend/application-local.yaml')
    parser.add_argument('--local-dist', action='store_true', help='Use existing dist outputs with Nginx while the Node24 image builds')
    parser.add_argument('--dev', action='store_true', help='Run the same real session flow through the Vite development proxy')
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding='utf-8'))['spring']['datasource']
    connection = urlsplit(config['url'].removeprefix('jdbc:'))
    assert connection.scheme == 'mysql' and connection.hostname in ('localhost', '127.0.0.1'), 'Local MySQL required'
    schema = 'cloud_t008_e2e_' + secrets.token_hex(6)
    assert re.fullmatch(r'cloud_t008_e2e_[a-f0-9]{12}', schema)
    env = os.environ.copy()
    env['MYSQL_PWD'] = str(config.get('password', ''))
    mysql = [shutil.which('mysql') or 'mysql', '--protocol=TCP', '-h', connection.hostname,
             '-P', str(connection.port or 3306), '-u', config['username'], '--batch']
    def sql(statement):
        result = subprocess.run(mysql, input=statement, text=True, env=env, capture_output=True, timeout=20)
        if result.returncode:
            raise RuntimeError('Disposable-schema SQL failed: ' + result.stderr)
    env.update({
        'CLOUD_T008_E2E': 'true',
        'T008_DB_URL': f'jdbc:mysql://{connection.netloc}/{schema}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',
        'T008_DB_USER': str(config['username']), 'T008_DB_PASSWORD': str(config.get('password', '')),
        'T008_JWT_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(),
        'T008_VERIFICATION_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(),
        'T008_LOGIN_PASSWORD': secrets.token_urlsafe(18), 'T008_PYTHON': sys.executable,
        'PYTHONUTF8': '1', 'PYTHONUNBUFFERED': '1',
    })
    if args.dev:
        env['T008_USE_DEV'] = '1'
    if args.local_dist:
        env['T008_USE_LOCAL_DIST'] = '1'
    sql(f'CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    print('Created disposable schema:', schema, flush=True)
    try:
        return subprocess.run([shutil.which('mvn') or 'mvn', '-B', '-Dtest=FrontendSessionE2ETest', 'test'],
                              cwd=ROOT / 'backend', env=env).returncode
    finally:
        sql(f'DROP DATABASE {schema};')
        print('Removed disposable schema:', schema, flush=True)

if __name__ == '__main__':
    sys.exit(main())
