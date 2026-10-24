"""Download official Mojang 26.1 data for local verification; no game files are redistributed."""
from pathlib import Path
import urllib.request
import json
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / '.downloads/vanilla-26.1'
out.mkdir(exist_ok=True)
with urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', timeout=25) as r:
    manifest = json.load(r)
version = next(v for v in manifest['versions'] if v['id'] == '26.1')
with urllib.request.urlopen(version['url'], timeout=25) as r:
    meta = json.load(r)
(out / 'version.json').write_text(json.dumps(meta, indent=2), encoding='utf-8')
download = meta['downloads']['server']
jar = out / 'server.jar'
if not jar.exists():
    with urllib.request.urlopen(download['url'], timeout=60) as r:
        jar.write_bytes(r.read())
assert hashlib.sha1(jar.read_bytes()).hexdigest() == download['sha1']
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if name.endswith('.jar'):
            path = out / name
            path.parent.mkdir(exist_ok=True, parents=True)
            path.write_bytes(z.read(name))
print(json.dumps({'version': meta['id'], 'server': download, 'java': meta['javaVersion']}))
