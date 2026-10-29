"""Compact optional class debug tables, then recompress the JAR with full verification."""
import argparse
import json
from pathlib import Path
import zipfile
import importlib.util

spec = importlib.util.spec_from_file_location('class_debug', Path(__file__).with_name('class-debug.py'))
class_debug = importlib.util.module_from_spec(spec)
spec.loader.exec_module(class_debug)


def compress(path):
    temporary = path.with_suffix('.compressing.jar')
    original_size = path.stat().st_size
    changed_classes = removed_bytes = 0
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary, 'w') as output:
            assert len(original.namelist()) == len(set(original.namelist())), 'Duplicate entries'
            output.comment = original.comment
            for entry in original.infolist():
                data = original.read(entry)
                if entry.filename.endswith('.class'):
                    compacted = class_debug.compact(data)
                    changed_classes += compacted != data
                    removed_bytes += len(data) - len(compacted)
                    data = compacted
                output.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        with zipfile.ZipFile(path) as original, zipfile.ZipFile(temporary) as output:
            assert original.namelist() == output.namelist(), 'Entry names changed'
            assert output.testzip() is None, 'Invalid compressed JAR'
            for name in original.namelist():
                before, after = original.read(name), output.read(name)
                expected = class_debug.compact(before) if name.endswith('.class') else before
                assert expected == after, name
                if name.endswith('.class'):
                    assert class_debug.compact(after) == after, 'Compaction must be idempotent: ' + name
        if temporary.stat().st_size < original_size:
            temporary.replace(path)
        print(json.dumps({'compressionPassed': True, 'beforeBytes': original_size,
                          'afterBytes': path.stat().st_size, 'compactedClasses': changed_classes,
                          'removedLocalDebugBytes': removed_bytes}))
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    compress(parser.parse_args().jar.resolve())
