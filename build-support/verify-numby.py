"""Audit localized Numby integration and the remapped totem constructor injection."""
from pathlib import Path
import argparse
import json
import os
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle-home', type=Path, default=Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle')))
args = parser.parse_args()
jar_path = ROOT/'build/libs/Tanghulu-1.0.1.jar'
modules = ['Beyblade', 'Confetti', 'SignTextEsp', 'SafeFire', 'NewChunks']
labels = {}
app = (ROOT/'src/main/java/dev/tanghulu/addon/TanghuluApp.java').read_text('utf-8')
for module in modules:
    source = (ROOT/f'src/main/java/dev/tanghulu/addon/modules/{module}.java').read_text('utf-8')
    names = re.findall(r'\.name\("([^"\n]+)"\)', source)
    descriptions = re.findall(r'\.description\("([^"\n]+)"\)', source)
    groups = re.findall(r'settings.createGroup\("([^"\n]+)"\)', source)
    title, description = re.search(r'super\(BaseModule.CATEGORY, "([^"]+)", "([^"]+)"', source).groups()
    assert len(names) == len(set(names)), f'Duplicate persisted setting names: {module}'
    assert names and len(names) == len(descriptions), module
    assert all(re.search(r'[\u4e00-\u9fff]', text) for text in [title, description, *names, *descriptions, *groups]), module
    assert app.count(f'modules.add(new {module}());') == 1, module
    labels[module] = {'title': title, 'settings': names, 'groups': groups}

with zipfile.ZipFile(jar_path) as archive:
    config = json.loads(archive.read('tanghulu.mixins.json'))
    assert config['client'].count('TotemParticleMixin') == 1
    for module in modules:
        assert f'dev/tanghulu/addon/modules/{module}.class' in archive.namelist()
    assert 'META-INF/licenses/tanghulu/Numby-Hack-GPL-3.0.txt' in archive.namelist()

def javap(path, name):
    return subprocess.check_output(['javap', '-J-Dfile.encoding=UTF-8', '-J-Dsun.stdout.encoding=UTF-8',
                                   '-p', '-v', '-classpath', str(path), name], encoding='utf-8', errors='strict')

intermediary = next((args.gradle_home/'caches/fabric-loom/minecraftMaven').rglob('minecraft-merged-intermediary-*.jar'))
details = javap(jar_path, 'dev.tanghulu.addon.mixin.TotemParticleMixin')
target = re.search(r'value=\[class L([^;]+);\]', details)[1].replace('/', '.')
callback = re.search(r'private void onConfettiConstructor[^\n]+\n\s*descriptor: (\S+)', details)[1]
expected = callback.replace('Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;', '')
target_api = javap(intermediary, target)
constructors = re.findall(re.escape(target) + r'\([^\n]*\);\s*\n\s*descriptor: (\S+)', target_api)
assert expected in constructors, f'Totem constructor signature mismatch: {expected}, {constructors}'
assert 'method=["<init>"]' in details and 'value="TAIL"' in details

report = {'passed': True, 'inGameTested': False, 'modules': labels,
          'totemTarget': target, 'constructorDescriptor': expected}
output = ROOT/'build/reports/numby-validation.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', 'utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
