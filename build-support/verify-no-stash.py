"""Reject removed upstream code, compiled references, and integration resources."""
from pathlib import Path
import io
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
REMOVED = [b'dev/tanghulu/stash', b'com/stash/hunt', b'dev.tanghulu.stash',
           b'com.stash.hunt', b'StashSearchRecords', b'StashLocalization',
           b'StashChinese', b'FlightHooks', b'OneClickTravel', b'BaritoneTravel',
           b'tanghulu-stash.mixins.json', b'ModuleTitleMixin', b'WaypointUtils',
           b'BlockESPMixin']

def scan(data, label):
    for needle in REMOVED:
        assert needle not in data, 'Removed integration reference in '+label

def archive(data, label):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in z.namelist():
            scan(name.encode('utf-8'), label)
            if name.endswith('/'):
                continue
            content = z.read(name)
            scan(content, label+'!'+name)
            if name.endswith('.jar'):
                archive(content, label+'!'+name)

def main():
    count = 0
    for directory in ['src', 'ancient/src', 'mushroom', 'prediction26', 'threec', 'skeleton']:
        for path in (ROOT/directory).rglob('*'):
            if path.is_file() and path.suffix in {'.java', '.json', '.py'}:
                scan(path.read_bytes(), path.relative_to(ROOT).as_posix())
                count += 1
    assert not list((ROOT/'navigation').rglob('*.java'))
    manifest = json.loads((ROOT/'build-support/dependencies/downloads.json').read_text('utf-8'))
    assert len(manifest) == 4 and not any('xaeroplus' in d['name'].lower() for d in manifest)
    build = (ROOT/'build.gradle').read_text('utf-8')
    assert 'baritoneAssistantChecks' not in build and 'xaeroPlusCompileJar' not in build
    jar = ROOT/'build/libs/Tanghulu-1.0.0.jar'
    archive(jar.read_bytes(), jar.name)
    print(json.dumps({'passed': True, 'scannedSources': count, 'removedModuleCount': 7,
                      'removedHudCount': 2, 'inGameTested': False}))

if __name__ == '__main__':
    main()
