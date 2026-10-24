"""Recompress a JAR without changing entry names, order, metadata or contents."""
import argparse
import json
from pathlib import Path
import zipfile


def compress(path):
    temporary = path.with_suffix('.compressing.jar')
    original_size = path.stat().st_size
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary, 'w') as output:
            assert len(original.namelist()) == len(set(original.namelist())), 'Duplicate entries'
            output.comment = original.comment
            for entry in original.infolist():
                output.writestr(entry, original.read(entry), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary) as output:
            assert original.namelist() == output.namelist(), 'Entry names changed'
            assert output.testzip() is None, 'Invalid compressed JAR'
            for name in original.namelist():
                assert original.read(name) == output.read(name), name
        if temporary.stat().st_size < original_size:
            temporary.replace(path)
        print(json.dumps({'compressionPassed': True, 'beforeBytes': original_size,
                          'afterBytes': path.stat().st_size}))
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    compress(parser.parse_args().jar.resolve())
