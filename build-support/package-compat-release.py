"""Package only the two requested, verified artifacts and their reproducible source."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1]).resolve()
OUT.mkdir(parents=True, exist_ok=True)
TARGETS = ('1.21.11', '1.21.4')
VERSION = '1.0.5'
records = []

for mc in TARGETS:
    filename = f'Tanghulu-mc{mc}-{VERSION}.jar'
    source = ROOT / 'build/libs' / filename
    with zipfile.ZipFile(source) as jar:
        assert jar.testzip() is None, f'Invalid archive: {mc}'
        assert jar.namelist().count('fabric.mod.json') == 1, 'Duplicate addon metadata'
        mod = json.loads(jar.read('fabric.mod.json'))
        assert mod['depends']['minecraft'] == mc
        assert mod['depends']['meteor-client'] == '*'
        assert mod['version'] == VERSION
        assert not any('compat/smoke/' in n or '/qa/' in n or n.endswith('.java') for n in jar.namelist())
        classes = [n for n in jar.namelist() if n.endswith('.class')]
        assert all(struct.unpack('>H', jar.read(n)[6:8])[0] <= 65 for n in classes), 'Main addon must run on Java 21'
        for config in mod['mixins']:
            data = json.loads(jar.read(config))
            for name in data.get('client', []) + data.get('mixins', []):
                assert (data['package'] + '.' + name).replace('.', '/') + '.class' in jar.namelist()
        assert 'tanghulu/prediction/prediction26.jar' in jar.namelist()
        assert 'tanghulu/mushroom/worker.jar' in jar.namelist()
    shutil.copy2(source, OUT / filename)
    records.append({'minecraft': mc, 'file': filename, 'bytes': source.stat().st_size,
                    'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'metadataValidated': True,
                    'mainClassesJava21': True, 'inGameTested': False})

source_zip = OUT / f'Tanghulu-{VERSION}-source.zip'
with zipfile.ZipFile(source_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(ROOT.rglob('*')):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        parts = relative.parts
        if any(p in {'build', '.gradle', '.git', '__pycache__', 'run', '.idea', 'dist', 'config', 'logs', 'log'} for p in parts):
            continue
        if path.suffix in {'.log', '.class', '.pyc', '.key', '.pem'} or path.name.startswith('.env'):
            continue
        if parts[0] == 'libs' and path.suffix == '.jar':
            continue
        archive.write(path, 'Tanghulu/' + relative.as_posix())
shutil.copy2(ROOT / 'COMPATIBILITY.md', OUT / '适配说明.md')
(OUT / 'artifacts.json').write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n', 'utf-8')
checksums = [(r['file'], r['sha256']) for r in records]
checksums.append((source_zip.name, hashlib.sha256(source_zip.read_bytes()).hexdigest()))
(OUT / 'SHA256SUMS.txt').write_text(''.join(f'{digest}  {name}\n' for name, digest in checksums), 'utf-8')
print(json.dumps({'output': str(OUT), 'artifacts': records, 'sourceBytes': source_zip.stat().st_size}, ensure_ascii=True, indent=2))
