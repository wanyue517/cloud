"""Run upload completion integration tests in a disposable local MySQL schema.

Reads credentials without printing them; never migrates the existing application database.
Requires mysql CLI, Maven and PyYAML.
"""
import argparse
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
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding='utf-8'))['spring']['datasource']
    connection = urlsplit(config['url'].removeprefix('jdbc:'))
    if connection.hostname not in ('localhost', '127.0.0.1'):
        raise ValueError('Only a local test database is supported')
    schema = 'cloud_upload_test_' + secrets.token_hex(6)
    if not re.fullmatch(r'cloud_upload_test_[a-f0-9]{12}', schema):
        raise ValueError('Invalid disposable schema name')
    env = os.environ.copy()
    env.update({
        'MYSQL_PWD': str(config.get('password', '')),
        'UPLOAD_TEST_DB_URL': f'jdbc:mysql://{connection.netloc}/{schema}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',
        'UPLOAD_TEST_DB_USER': str(config['username']),
        'UPLOAD_TEST_DB_PASSWORD': str(config.get('password', '')),
        'MAVEN_OPTS': env.get('MAVEN_OPTS', '') + ' -Xmx512m',
    })
    mysql = [shutil.which('mysql') or 'mysql', '--protocol=TCP', '-h', connection.hostname,
             '-P', str(connection.port or 3306), '-u', str(config['username']), '--batch']

    def sql(statement):
        result = subprocess.run(mysql, input=statement, text=True, env=env, capture_output=True, timeout=20)
        if result.returncode:
            raise RuntimeError('Disposable-schema SQL failed: ' + result.stderr)

    sql(f'CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    print('Created disposable schema:', schema, flush=True)
    try:
        return subprocess.run([shutil.which('mvn') or 'mvn', '-B', '-pl', 'cloud-api', '-am',
                               '-Dtest=UploadCompletionTest', '-DargLine=-Xmx512m', 'test'],
                              cwd=ROOT / 'backend', env=env).returncode
    finally:
        sql(f'DROP DATABASE {schema};')
        print('Removed disposable schema:', schema, flush=True)


if __name__ == '__main__':
    sys.exit(main())
