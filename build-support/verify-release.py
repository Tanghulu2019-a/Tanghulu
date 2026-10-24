"""Check the public source tree and recursively inspect release ZIP/JAR contents."""
from pathlib import Path
import argparse
import io
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED = {'.git', '.gradle', 'build', 'dist', '__pycache__', 'run', 'config',
            'logs', 'log', '.idea', '.vscode', '.settings', '.cache', 'out', 'classes'}
# Hashes of removed identifiers; private identifiers are not embedded in public tools.
REMOVED_IDENTIFIER_HASHES = {
    '46ad28d84def332ab9efdf7c86edc6191bf563bc24b22ef1668fa7f1b1b1cffa',
    'ef1942dc8d1fdd11805bc28de1281a38771a7ab072ba6680b231af07d1cc96e1',
    'ff3f8000a82ab8b8f87323965c042046b23673a604f42136d1ecaf28976b5092',
    'd9065849363d52755c4fc707ec477d825e7c4747f19142716f8ff0ca02a2c664',
    '8cc55465d3bc62a2e6e90b47a20b893a43f77c4afc51e332154cf1a9741c94de',
    '964da4685622617f8f9faa6d3c67bb71f579b3af3535b45a00db5684fad0bed7',
    'e37f8c9bf694d63e895ecf953883b1f23c4d51e86e2580a984170a85b2bd3273',
    '461b29a99eac914507bd1b089bbaec98ec153a18bd3df9db0fd4e35240a6d84d',
    '9dacc0996d162a8eac5c5b635fedc2927a6c83d48b21bbee896682f79502d76a',
}
PATTERNS = [
    re.compile(rb'(?i)[a-z]:[\\/](?:Users|Documents)[\\/]'),
    re.compile(rb'/(?:home|Users)/[^\s/]+/'),
    re.compile(rb'github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{30,}'),
    re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
]
REMOVED_CLASSES = [
    'dev/tanghulu/addon/ConfigManager.class',
    'dev/tanghulu/addon/util/MeituanTimeFetcher.class',
]

def inspect_bytes(data, label):
    tokens = re.findall(rb'[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*', data)
    for token in tokens:
        assert hashlib.sha256(token.lower()).hexdigest() not in REMOVED_IDENTIFIER_HASHES, 'Removed identifier in '+label
    for pattern in PATTERNS:
        assert pattern.search(data) is None, 'Sensitive pattern in '+label

def inspect_archive(data, label, main_jar=False):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate entries: '+label
        assert archive.testzip() is None, 'Corrupt archive: '+label
        for name in names:
            assert not name.startswith(('/', '\\')) and '..' not in Path(name).parts, name
            if name.endswith('/'):
                continue
            content = archive.read(name)
            inspect_bytes(name.encode('utf-8'), label+' entry name')
            inspect_bytes(content, label+'!'+name)
            if name.endswith(('.jar', '.zip')):
                inspect_archive(content, label+'!'+name)
        if main_jar:
            assert names.count('fabric.mod.json') == 1
            meta = json.loads(archive.read('fabric.mod.json'))
            assert meta['id'] == 'tanghulu' and meta['version'] == '1.0.1'
            assert meta['authors'] == ['Tanghulu']
            assert meta['contributors'] == ['Cqb13', 'Mikumiku']
            assert meta['depends']['minecraft'] == '1.21.11'
            assert meta['depends']['meteor-client'] == '1.21.11-86'
            assert meta['license'] == 'GPL-3.0-only'
            assert not any(n in names for n in REMOVED_CLASSES)
            assert not any(n.startswith(('dev/tanghulu/addon/auth/security/', 'dev/tanghulu/stash/', 'com/stash/')) for n in names)
            assert 'tanghulu-stash.mixins.json' not in names
            assert 'dev/tanghulu/addon/util/WaypointUtils.class' not in names
            assert not any(n.startswith(('baritone/', 'meteordevelopment/meteorclient/')) for n in names)
            assert 'META-INF/licenses/tanghulu/LICENSE' in names

def source_paths():
    for path in sorted(ROOT.rglob('*')):
        rel = path.relative_to(ROOT)
        if not path.is_file() or any(part in EXCLUDED for part in rel.parts):
            continue
        if (rel.parts[0] == 'libs' and path.suffix == '.jar') or path.suffix in {'.log', '.pyc'}:
            continue
        assert not path.name.startswith('.env') and path.suffix not in {'.pem', '.key'}, str(rel)
        yield path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT/'build/libs/Tanghulu-1.0.1.jar')
    parser.add_argument('--source', type=Path)
    args = parser.parse_args()
    paths = list(source_paths())
    for path in paths:
        rel = path.relative_to(ROOT).as_posix()
        inspect_bytes(rel.encode('utf-8'), rel)
        inspect_bytes(path.read_bytes(), rel)
    inspect_archive(args.jar.read_bytes(), args.jar.name, main_jar=True)
    if args.source:
        inspect_archive(args.source.read_bytes(), args.source.name)
        with zipfile.ZipFile(args.source) as archive:
            expected = {'Tanghulu/'+p.relative_to(ROOT).as_posix(): p for p in paths}
            assert set(archive.namelist()) == set(expected), 'Source package inventory mismatch'
            for name, path in expected.items():
                assert archive.read(name) == path.read_bytes(), 'Source package differs: '+name
    print(json.dumps({'passed': True, 'sourceFiles': len(paths), 'inGameTested': False}))

if __name__ == '__main__':
    main()
