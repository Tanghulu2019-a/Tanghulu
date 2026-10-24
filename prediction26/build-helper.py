"""Compile our bridge only. Mojang classes stay in a verified local cache, never in the release."""
from pathlib import Path
import hashlib, os, shutil, subprocess, urllib.request, zipfile

root = Path(__file__).resolve().parent.parent
build = root / 'build/prediction26'
build.mkdir(parents=True, exist_ok=True)
server = build / 'server.jar'
digest = '33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c'
if not server.exists() or hashlib.sha1(server.read_bytes()).hexdigest() != digest:
    urllib.request.urlretrieve(f'https://piston-data.mojang.com/v1/objects/{digest}/server.jar', server)
assert hashlib.sha1(server.read_bytes()).hexdigest() == digest, 'Official server checksum mismatch'
runtime = build / 'runtime'
runtime.mkdir(exist_ok=True)
with zipfile.ZipFile(server) as archive:
    for name in archive.namelist():
        if name.startswith(('META-INF/versions/', 'META-INF/libraries/')) and name.endswith('.jar'):
            (runtime / Path(name).name).write_bytes(archive.read(name))
javac = Path(os.environ.get('JAVA_HOME', '')) / 'bin/javac.exe'
if not javac.is_file():
    javac = shutil.which('javac')
classes = build / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run([str(javac), '--release', '25', '-encoding', 'UTF-8', '-cp', str(runtime / '*'),
                '-d', str(classes), *map(str, (root / 'prediction26/src').rglob('*.java'))], check=True)
output = build / 'prediction26.jar'
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in classes.rglob('*.class'):
        archive.write(path, path.relative_to(classes).as_posix())
print('Built isolated Java 25 prediction helper:', output)
