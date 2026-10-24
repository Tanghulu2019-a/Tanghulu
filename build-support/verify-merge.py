"""Audit the standalone release JAR after removing the unlicensed integration.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import struct
import zipfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
args = parser.parse_args()
JAR = ROOT / 'build/libs/Tanghulu-1.0.1.jar'
report = {'passed': False, 'minecraft': '1.21.11', 'inGameTested': False}
with zipfile.ZipFile(JAR) as jar:
    names = set(jar.namelist())
    assert len(names) == len(jar.namelist()), 'Duplicate ZIP entries'
    assert sum(n.endswith('fabric.mod.json') for n in names) == 1
    meta = json.loads(jar.read('fabric.mod.json'))
    assert meta['id'] == 'tanghulu' and meta['name'] == 'Tanghulu'
    assert meta['entrypoints']['meteor'] == ['dev.tanghulu.addon.ok.TanghuluAddon']
    assert meta['entrypoints']['preLaunch'] == ['dev.tanghulu.addon.ok.PreLaunchInitializer']
    for entrypoints in meta['entrypoints'].values():
        for entry in entrypoints:
            assert entry.replace('.', '/') + '.class' in names
    mixin_count = 0
    for config in meta['mixins']:
        data = json.loads(jar.read(config))
        for section in ('client', 'mixins', 'server'):
            for mixin in data.get(section, []):
                assert (data['package']+'.'+mixin).replace('.', '/')+'.class' in names
                mixin_count += 1
    assert mixin_count == 22, mixin_count
    for name in names:
        assert 'miku' not in name.lower(), name
        if name.endswith('.class'):
            assert struct.unpack_from('>H',jar.read(name),6)[0] <= 65, name
            if name.startswith('dev/tanghulu/addon/'):
                assert b'miku' not in jar.read(name).lower(), name
    assert not any(n.startswith(('dev/tanghulu/stash/', 'com/stash/')) for n in names)
    assert 'tanghulu-stash.mixins.json' not in names
    assert 'assets/tanghulu/icon.png' in names
    assert not any(n.startswith(('cubitect/', 'org/fusesource/hawtjni/', 'META-INF/native/')) for n in names)
    digest = hashlib.sha256()
    apple = ROOT / 'ancient'
    predictions = sorted(list((apple/'src/main/java/dev/tanghulu/ancientapple/prediction').rglob('*.java'))
                         + list((apple/'src/main/java/kludwisz').rglob('*.java')),
                         key=lambda p:p.relative_to(apple).as_posix())
    for p in predictions:
        digest.update(p.relative_to(apple).as_posix().encode()); digest.update(p.read_bytes())
    for name in ('prediction-validation.json','loot26-validation.json'):
        cert = json.loads(jar.read(name))
        assert cert['passed'] and cert['sourceHash'] == digest.hexdigest()
    profiles = [n for n in names if n.startswith('loot-profiles/') and n.endswith('.json')]
    assert len(profiles) == 16
    assert 'com/seedfinding/mcfeature/structure/EndCity.class' in names
    assert 'dev/tanghulu/ancientapple/vendor/seedfinding/mcfeature/structure/EndCity.class' in names
    assert not any(n.startswith(('org/junit/', 'dev/tanghulu/ancientapple/qa/')) for n in names)
    active = re.compile(r'^\s*(?:modules|Modules\.get\(\))\.add\(new (\w+)\(\)\);', re.M)
    actual = active.findall((ROOT/'src/main/java/dev/tanghulu/addon/TanghuluApp.java').read_text('utf-8'))
    assert len(actual) == len(set(actual)) == 28 and 'MushroomFinder' in actual and 'PlayerSkeleton' in actual
    assert {'Beyblade', 'Confetti', 'SignTextEsp', 'SafeFire', 'NewChunks'} <= set(actual)
    for module in actual:
        assert 'dev/tanghulu/addon/modules/' + module + '.class' in names
    report.update(mixins=mixin_count, lootProfiles=len(profiles), predictionSourceHash=digest.hexdigest(),
                  registeredModules=29)

report['unitChecks'] = json.loads((ROOT/'ancient/build/reports/unit-checks.json').read_text('utf-8'))
assert report['unitChecks']['passed'] and report['unitChecks']['succeeded']==100
report.update(passed=True, jarSha256=hashlib.sha256(JAR.read_bytes()).hexdigest())
output = ROOT/'build/reports/merge-validation.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
