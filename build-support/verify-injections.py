"""Check merged ancient-city injection targets and the packaged prediction gate.
Adapted from the inherited ancient/scripts/check_release.py.
Run after build; pass --gradle-home for a nondefault Gradle cache.
"""
from pathlib import Path
import argparse, json, os, re, shutil, subprocess, zipfile
ROOT = Path(__file__).resolve().parent.parent
VERSION = '1.0.0'
JAR = ROOT / 'build/libs/Tanghulu-1.0.0.jar'
parser=argparse.ArgumentParser()
parser.add_argument('--gradle-home',type=Path,default=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle')))
args=parser.parse_args()
def run(*args):
    if Path(args[0]).stem.lower() == "javap":
        args = (args[0], "-J-Dfile.encoding=UTF-8", "-J-Dsun.stdout.encoding=UTF-8", *args[1:])
    result = subprocess.run(args, cwd=ROOT, text=True, encoding="utf-8", errors="replace", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


javap=shutil.which('javap'); java=shutil.which('java')
assert javap and java, 'JDK tools required'
modules=args.gradle_home/'caches/modules-2/files-2.1'
intermediary=next((args.gradle_home/'caches/fabric-loom/minecraftMaven').rglob('minecraft-merged-intermediary-*.jar'))
gson=next((modules/'com.google.code.gson/gson/2.13.2').rglob('gson-*.jar'))
with zipfile.ZipFile(JAR) as jar:
    assert jar.testzip() is None
    names = jar.namelist()
    assert len(names) == len(set(names)), "Duplicate ZIP entries"
    assert json.loads(jar.read("fabric.mod.json"))["version"] == VERSION
    assert not any(n.startswith(("net/minecraft/", "meteordevelopment/", "baritone/", "xaero/")) for n in names)
    assert not any("/qa/" in n for n in names)
    assert not any("LoadedChestSurvey" in n for n in names)
    majors = {int.from_bytes(jar.read(n)[6:8], "big") for n in names if n.endswith(".class")}
    assert max(majors) <= 65, "JAR requires a newer Java than 21"
    config = json.loads(jar.read("ancient-apple-hunter.mixins.json"))

injections = []
for mixin in config["client"]:
    if mixin == "FireworkStateAccessor":
        continue
    class_name = config["package"] + "." + mixin
    details = run(javap, "-p", "-v", "-classpath", str(JAR), class_name)
    target = re.search(r"value=\[class L([^;]+);\]", details)[1].replace("/", ".")
    target_api = run(javap, "-p", "-s", "-classpath", str(intermediary), target)
    methods = {}
    for name, descriptor in re.findall(r"(?m)^  (?:public|protected|private)[^\n]*? ([\w$]+)\([^\n]*\);\s*\n\s*descriptor: (\S+)", target_api):
        methods.setdefault(name, []).append(descriptor)
    blocks = re.split(r"(?m)^  (?=(?:public|protected|private) )", details)
    for block in blocks:
        selected = re.search(r'method=\["([^"\]]+)"\]', block)
        if not selected:
            continue
        injected = re.search(r"descriptor: (\S+)", block)[1]
        assert "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo" in injected
        parameters = injected[1:injected.index("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")]
        matching = [desc for desc in methods.get(selected[1], []) if desc[1:desc.index(")")] == parameters]
        assert matching, f"Missing injection target or incompatible arguments: {class_name} -> {selected[1]} {injected}"
        for desc in matching:
            assert ("CallbackInfoReturnable" in injected) == (not desc.endswith(")V"))
        injections.append({"mixin": mixin, "target": target, "method": selected[1], "descriptor": matching[0]})
assert len(injections) == 9, f"Unexpected injection count: {len(injections)}"

# Optional Xaero targets must match the actual shipped public API and runtime names.
with zipfile.ZipFile(JAR) as jar:
    xaero_config=json.loads(jar.read('ancient-apple-hunter.xaero.mixins.json'))
    assert not xaero_config['required']
    assert 'ancient-apple-hunter.xaero.mixins.json' in json.loads(jar.read('fabric.mod.json'))['mixins']
xaero_jar=next((modules/'maven.modrinth/xaeros-minimap/fabric-1.21.11-26.4.2').rglob('*.jar'))
optional_injections=[]
def descriptor_args(value):
    return re.findall(r'\[*L[^;]+;|\[*[BCDFIJSZ]',value[1:value.index(')')])
for mixin in xaero_config['client']:
    details=run(javap,'-p','-v','-classpath',str(JAR),xaero_config['package']+'.'+mixin)
    assert 'org.spongepowered.asm.mixin.Pseudo' in details
    target=re.search(r'targets=\["([^"\]]+)"\]',details)[1].replace('/','.')
    api_jars=([next((modules/'maven.modrinth/xaeros-world-map/fabric-1.21.11-1.44.2').rglob('*.jar'))] if target.startswith('xaero.map.')
              else [xaero_jar])
    target_apis=[run(javap,'-p','-s','-classpath',str(api_jar),target) for api_jar in api_jars]
    for block in re.split(r'(?m)^  (?=(?:public|protected|private) )',details):
        selected=re.search(r'method=\["([^"\]]+)"\]',block)
        if not selected:
            continue
        method,descriptor=selected[1].split('(',1); descriptor='('+descriptor
        assert all(re.search(r'\b'+re.escape(method)+r'\([^\n]*\);\s*\n\s*descriptor: '+re.escape(descriptor),api) for api in target_apis)
        injected=re.search(r'descriptor: (\S+)',block)[1]
        actual=descriptor_args(injected)[:-1]; expected=descriptor_args(descriptor)
        assert len(actual)==len(expected)
        for index,(a,b) in enumerate(zip(actual,expected)):
            assert a==b or (a=='Ljava/lang/Object;' and b.startswith('L') and re.search(r'parameter '+str(index)+r':\s*\n\s*\d+: #[^\n]*\n\s*org.spongepowered.asm.mixin.injection.Coerce',block))
        assert 'remap=false' in block and 'require=0' in block and 'cancellable=true' in block
        optional_injections.append({'mixin':mixin,'target':target,'method':method,'descriptor':descriptor,'checkedApis':[p.name for p in api_jars]})
assert len(optional_injections)==5

check=ROOT/'build/release-check/CheckRelease.java'
check.parent.mkdir(parents=True,exist_ok=True)
check.write_text('import dev.tanghulu.ancientapple.core.ValidationGate; class CheckRelease { public static void main(String[] args) { if(!ValidationGate.certified()) throw new AssertionError("Prediction gate is closed"); System.out.println("Prediction gate: OPEN"); } }',encoding='utf-8')
gate=run(java,'-cp',str(JAR)+os.pathsep+str(gson),str(check)).strip()
report={'passed':True,'vanillaInjections':injections,'xaeroInjections':optional_injections,'packagedGate':gate}
out=ROOT/'build/reports/injection-validation.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'PASS: {len(injections)} vanilla injections, {len(optional_injections)} Xaero injections, {gate}')
