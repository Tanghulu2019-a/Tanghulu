"""Compare an optimized build with its original source ZIP and release JAR.

Uses Python's standard library. All classes and non-image runtime data must
remain identical; certificates may change JSON formatting only.
"""
import argparse
from io import BytesIO
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parent.parent
CERTIFICATES = {'prediction-validation.json', 'loot26-validation.json'}
ICON = 'assets/tanghulu/icon.png'


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def png_content(data):
    """Decode non-interlaced 8-bit RGB/RGBA PNGs, retaining ancillary metadata."""
    require(data[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG signature')
    offset, encoded, metadata = 8, bytearray(), []
    header = None
    while offset < len(data):
        length, kind = struct.unpack_from('>I4s', data, offset)
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from('>I', data, offset + 8 + length)[0]
        require(zlib.crc32(kind + payload) & 0xffffffff == crc, 'Invalid PNG chunk CRC')
        offset += length + 12
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'IDAT':
            encoded.extend(payload)
        elif kind == b'iCCP':
            name, profile = payload.split(b'\0', 1)
            require(profile[0] == 0, 'Unsupported ICC compression')
            metadata.append((kind, name, zlib.decompress(profile[1:])))
        elif kind != b'IEND':
            metadata.append((kind, payload))
        if kind == b'IEND':
            break
    require(header is not None and offset == len(data), 'Invalid PNG structure')
    width, height, depth, color, compression, filtering, interlace = header
    require(depth == 8 and color in (2, 6) and (compression, filtering, interlace) == (0, 0, 0),
            'Unsupported PNG format')
    channels = 3 if color == 2 else 4
    stride = width * channels
    raw = zlib.decompress(encoded)
    require(len(raw) == height * (stride + 1), 'Invalid PNG scanline size')
    previous, pixels = bytearray(stride), bytearray()
    for row in range(height):
        start = row * (stride + 1)
        mode = raw[start]
        require(mode <= 4, 'Invalid PNG filter')
        current = bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            left = current[i - channels] if i >= channels else 0
            above = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if mode == 0:
                predictor = 0
            elif mode == 1:
                predictor = left
            elif mode == 2:
                predictor = above
            elif mode == 3:
                predictor = (left + above) // 2
            else:
                estimate = left + above - corner
                distances = [abs(estimate - value) for value in (left, above, corner)]
                predictor = (left, above, corner)[distances.index(min(distances))]
            current[i] = (current[i] + predictor) & 255
        pixels.extend(current)
        previous = current
    return header, metadata, bytes(pixels)


def compare_jars(before, after, report, prefix=''):
    require(before.testzip() is None and after.testzip() is None, 'Invalid JAR')
    require(len(before.namelist()) == len(set(before.namelist())), 'Duplicate original JAR entries')
    require(len(after.namelist()) == len(set(after.namelist())), 'Duplicate optimized JAR entries')
    require(set(before.namelist()) == set(after.namelist()), 'JAR entries changed: ' + prefix)
    for name in before.namelist():
        old, new = before.read(name), after.read(name)
        if name.endswith('.jar'):
            with zipfile.ZipFile(BytesIO(old)) as a, zipfile.ZipFile(BytesIO(new)) as b:
                compare_jars(a, b, report, prefix + name + '!/')
        elif name.endswith('.class'):
            require(old == new, 'Class bytecode changed: ' + prefix + name)
            report['identicalClassFiles'] += 1
        elif old != new:
            if not prefix and name in CERTIFICATES:
                require(json.loads(old) == json.loads(new), 'Certificate data changed: ' + name)
            elif not prefix and name == ICON:
                require(png_content(old) == png_content(new), 'Icon pixels or metadata changed')
            else:
                raise AssertionError('Runtime resource changed: ' + prefix + name)
            report['losslessResourceChanges'].append(prefix + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path, help='Original source ZIP')
    parser.add_argument('--baseline-jar', required=True, type=Path)
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/Tanghulu-mc1.21.11-1.0.6.jar')
    parser.add_argument('--report', type=Path, default=ROOT / 'build/reports/size-optimization.json')
    args = parser.parse_args()
    report = {'passed': False, 'identicalClassFiles': 0, 'identicalJavaSources': 0,
              'losslessResourceChanges': [], 'inGameTested': False}
    with zipfile.ZipFile(args.source) as source:
        for entry in source.infolist():
            if entry.is_dir():
                continue
            relative = Path(entry.filename).relative_to('Tanghulu')
            require('..' not in relative.parts, 'Invalid source ZIP path')
            local = ROOT / relative
            require(local.is_file(), 'Original source file missing: ' + str(relative))
            if local.suffix == '.java':
                require(source.read(entry) == local.read_bytes(), 'Java source changed: ' + str(relative))
                report['identicalJavaSources'] += 1
            elif relative.as_posix().startswith('ancient/src/main/resources/loot-profiles/'):
                require(source.read(entry) == local.read_bytes(), 'Loot profile hash changed: ' + str(relative))
    with zipfile.ZipFile(args.baseline_jar) as before, zipfile.ZipFile(args.jar) as after:
        compare_jars(before, after, report)
    old_size, new_size = args.baseline_jar.stat().st_size, args.jar.stat().st_size
    require(new_size < old_size, 'JAR size did not decrease')
    report.update(passed=True, beforeJarBytes=old_size, afterJarBytes=new_size,
                  jarReductionPercent=round((old_size - new_size) / old_size * 100, 2),
                  jarSha256=hashlib.sha256(args.jar.read_bytes()).hexdigest())
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
