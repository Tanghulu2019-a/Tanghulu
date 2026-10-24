"""Compile/run the separate Java 25 oracle against only the official 26.1 server libraries."""
from pathlib import Path
import subprocess
import os
import argparse
import hashlib
import json

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--compile-only',action='store_true')
args=parser.parse_args()
bundled=root/'.downloads/vanilla-26.1/META-INF'
if not bundled.exists():
    bundled=root.parent/'ancient-apple-hunter/.downloads/vanilla-26.1/META-INF'
output=root/'build/qa26'
output.mkdir(exist_ok=True)
classpath=os.pathsep.join(str(p) for p in sorted(bundled.rglob('*.jar')))
source=root/'scripts/qa26/Vanilla26Oracle.java'
result=subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',str(output),str(source)],cwd=root)
if result.returncode: raise SystemExit(result.returncode)
if not args.compile_only:
    work=root/'build/oracle-26.1'
    work.mkdir(exist_ok=True)
    result=subprocess.run(['java','-Xmx3G','-cp',str(output)+os.pathsep+classpath,'Vanilla26Oracle',str(root)],cwd=work)
    if result.returncode: raise SystemExit(result.returncode)
    report=json.loads((root/'build/reports/loot26-oracle.json').read_text('utf-8'))
    assert report['passed'] and report['checked'] == 1024
    digest=hashlib.sha256()
    main=root/'src/main/java'
    paths=[p for p in main.rglob('*.java') if 'prediction' in p.parts or 'kludwisz' in p.parts]
    for p in sorted(paths,key=lambda p:p.relative_to(root).as_posix()):
        digest.update(p.relative_to(root).as_posix().encode('utf-8')); digest.update(p.read_bytes())
    report['sourceHash']=digest.hexdigest()
    report['fixturesHash']=hashlib.sha256((root/'build/reports/loot26-fixtures.json').read_bytes()).hexdigest()
    assert report['sourceHash'] == json.loads((root/'src/main/resources/prediction-validation.json').read_text('utf-8'))['sourceHash']
    (root/'build/reports/loot26-oracle.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    (root/'src/main/resources/loot26-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
