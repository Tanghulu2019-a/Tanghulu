"""Fetch pinned build dependencies without republishing third-party mod JARs."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--destination', type=Path, default=ROOT/'libs')
    args = parser.parse_args()
    args.destination.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT/'build-support/dependencies/downloads.json').read_text('utf-8'))
    for dep in manifest:
        target = args.destination/dep['name']
        if target.is_file() and sha256(target) == dep['sha256']:
            print('Verified:', dep['name'])
            continue
        if args.verify_only:
            raise SystemExit('Missing or mismatched dependency: '+dep['name'])
        request = urllib.request.Request(dep['url'], headers={'User-Agent': 'Tanghulu-build/1.0.0'})
        temp = None
        try:
            with tempfile.NamedTemporaryFile(dir=args.destination, suffix='.download', delete=False) as out:
                temp = Path(out.name)
                with urllib.request.urlopen(request, timeout=120) as response:
                    while chunk := response.read(1024*1024):
                        out.write(chunk)
            if sha256(temp) != dep['sha256']:
                raise RuntimeError('Checksum mismatch: '+dep['name'])
            os.replace(temp, target)
            print('Downloaded and verified:', dep['name'])
        finally:
            if temp is not None:
                temp.unlink(missing_ok=True)

if __name__ == '__main__':
    main()
