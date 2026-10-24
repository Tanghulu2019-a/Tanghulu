"""Fetch SHA-1 verified Mojang servers used by the cross-version loot audit."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.downloads/loot-versions'
OUT.mkdir(parents=True, exist_ok=True)


def fetch(url, path):
    subprocess.run(['curl.exe', '-L', '--fail', '--silent', '--show-error', '--retry', '2', url, '-o', str(path)], check=True)


manifest_path = OUT / 'manifest.json'
fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', manifest_path)
manifest = json.loads(manifest_path.read_text('utf-8'))
versions = [v for v in manifest['versions'] if v['type'] == 'release' and
            (v['id'].startswith('1.21.') or v['id'].startswith('26.'))]


def download(version):
    directory = OUT / version['id']
    directory.mkdir(exist_ok=True)
    meta_path = directory / 'version.json'
    fetch(version['url'], meta_path)
    meta = json.loads(meta_path.read_text('utf-8'))
    server = meta['downloads']['server']
    jar = directory / 'server.jar'
    existing = {
        '1.21.11': ROOT.parent / 'ancient-apple-hunter/.gradle-user-home/caches/fabric-loom/1.21.11/minecraft-server.jar',
        '26.1': ROOT.parent / 'ancient-apple-hunter/.downloads/vanilla-26.1/server.jar',
    }.get(version['id'])
    if existing and existing.exists() and hashlib.sha1(existing.read_bytes()).hexdigest() == server['sha1']:
        jar = existing
    elif not jar.exists() or hashlib.sha1(jar.read_bytes()).hexdigest() != server['sha1']:
        fetch(server['url'], jar)
    assert hashlib.sha1(jar.read_bytes()).hexdigest() == server['sha1'], version['id']
    with zipfile.ZipFile(jar) as archive:
        inner = next(n for n in archive.namelist() if n.startswith('META-INF/versions/') and n.endswith('.jar'))
        target = directory / 'game.jar'
        if not target.exists():
            target.write_bytes(archive.read(inner))
    print(version['id'] + ' verified', flush=True)


with ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(download, versions))
print('Latest release: ' + manifest['latest']['release'])
