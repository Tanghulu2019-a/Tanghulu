"""Audit the restored Stash package, optional hooks and injection descriptors."""
import argparse, hashlib, json, os, re, shutil, subprocess, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--gradle-home',type=Path,default=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle')))
args=parser.parse_args()
JAR=ROOT/'build/libs/Tanghulu-1.0.2.jar'
STASH='dev/tanghulu/stash/'
report={'passed':False,'inGameTested':False}
def sha(data):return hashlib.sha256(data).hexdigest()
def run(*command):
    result=subprocess.run(command,cwd=ROOT,capture_output=True,encoding='utf8',errors='replace')
    assert result.returncode==0,result.stdout+result.stderr
    return result.stdout
def javap(path,name,*options):
    return run(shutil.which('javap'),'-J-Dfile.encoding=UTF-8','-J-Dsun.stdout.encoding=UTF-8',*options,'-classpath',str(path),name.replace('/','.').removesuffix('.class'))
with zipfile.ZipFile(JAR) as jar:
    names=set(jar.namelist())
    assert len(names)==len(jar.namelist()) and jar.testzip() is None
    meta=json.loads(jar.read('fabric.mod.json'))
    assert meta['version']=='1.0.2'
    assert meta['entrypoints']['meteor']==['dev.tanghulu.addon.ok.TanghuluAddon']
    modules={'AutoLogPlus','BetterStashFinder','ElytraFlyPlusPlus','Pitch40Util','VanityESP'}
    for name in modules:assert STASH+'modules/'+name+'.class' in names,name
    for name in ('Weather','EntityList'):assert STASH+'hud/'+name+'.class' in names,name
    assert not any(n.startswith(('xaero/','xaeroplus/','baritone/','net/lenni0451/','com/stash/','meteordevelopment/meteorclient/')) for n in names)
    mixins=json.loads(jar.read('tanghulu-stash.mixins.json'))
    for helper in ['FlightHooks','mixin/EntityMixin','mixin/LivingEntityMixin','mixin/KeyBindingMixin']:
        data=jar.read(STASH+helper+'.class')
        assert b'baritone/' not in data and b'ElytraFlyPlusPlus' not in data,helper
    report['modules']=sorted(modules)

# Minecraft 1.21.11 intermediary classes, including the jumpingCooldown shadow.
mc_candidates = list((args.gradle_home / 'caches/fabric-loom/minecraftMaven').rglob('minecraft-merged-intermediary-1.21.11-*.jar'))
assert mc_candidates, 'Minecraft 1.21.11 intermediary cache not found.'
mc_jar = mc_candidates[0]
injections = []
for mixin in mixins['client']:
    if mixin == 'SettingTitleAccessor':
        continue
    details = javap(JAR, STASH + 'mixin/' + mixin + '.class', '-p', '-v')
    target = re.search(r'value=\[class L([^;]+);\]', details)[1]
    target_api = javap(mc_jar, target, '-p', '-s')
    methods = {}
    for method, descriptor in re.findall(r'(?m)^  (?:public|protected|private)[^\n]*? ([\w$]+)\([^\n]*\);\s*\n\s*descriptor: (\S+)', target_api):
        methods.setdefault(method, []).append(descriptor)
    for block in re.split(r'(?m)^  (?=(?:public|protected|private) )', details):
        selected = re.search(r'method=\["([^"\]]+)"\]', block)
        if not selected:
            continue
        method = selected[1].split('(', 1)[0]
        specified = '(' + selected[1].split('(', 1)[1] if '(' in selected[1] else None
        injected = re.search(r'descriptor: (\S+)', block)[1]
        prefix = injected[1:injected.index('Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo')]
        matching = [d for d in methods.get(method, []) if d[1:d.index(')')] == prefix and (specified is None or d == specified)]
        assert matching, (mixin, target, method, injected)
        assert ('CallbackInfoReturnable' in injected) == (not matching[0].endswith(')V'))
        injections.append({'mixin': mixin, 'target': target, 'method': method, 'descriptor': matching[0]})
    if mixin == 'LivingEntityMixin':
        shadow = re.search(r'private int ([\w$]+);\s*\n\s*descriptor: I', details)[1]
        assert re.search(r'\bint ' + re.escape(shadow) + r';\s*\n\s*descriptor: I', target_api)
        report['jumpCooldownShadow'] = shadow
assert len(injections) == 6, injections
report['flightInjections'] = injections

# Run the real dependency-free hook implementation, including state changes
# after registration; no Minecraft, Meteor or Baritone classes are on this path.
test_dir = ROOT / 'build/stash-hook-checks'
test_dir.mkdir(parents=True, exist_ok=True)
test_source = test_dir / 'FlightHooksCheck.java'
test_source.write_text('''package dev.tanghulu.stash;
public final class FlightHooksCheck {
    public static void main(String[] args) {
        if (FlightHooks.isEnabled()) throw new AssertionError("enabled before registration");
        boolean[] state = {false};
        int[] calls = {0};
        FlightHooks.bind(() -> { calls[0]++; return state[0]; });
        if (FlightHooks.isEnabled()) throw new AssertionError("inactive flight");
        state[0] = true;
        if (!FlightHooks.isEnabled()) throw new AssertionError("active flight");
        state[0] = false;
        if (FlightHooks.isEnabled()) throw new AssertionError("disabled flight");
        if (calls[0] != 3) throw new AssertionError("cached flight state");
        FlightHooks.bind(() -> false);
        if (FlightHooks.isEnabled()) throw new AssertionError("replacement binding");
        System.out.println("PASS: 6 flight-hook lifecycle checks without game or optional mods.");
    }
}
''', encoding='utf-8')
run(shutil.which('javac'), '--release', '21', '-encoding', 'UTF-8', '-d', str(test_dir),
    str(ROOT / ('src/main/java/' + STASH + 'FlightHooks.java')), str(test_source))
report['flightHookLifecycleChecks'] = run(shutil.which('java'), '-cp', str(test_dir), 'dev.tanghulu.stash.FlightHooksCheck').strip()
report.update(passed=True, jarSha256=sha(JAR.read_bytes()))
output = ROOT / 'build/reports/stash-merge-validation.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"PASS: 5 Stash modules, 2 HUDs, {len(injections)} flight injections; hook lifecycle checks passed.")
