"""Verify the remapped JAR, its vanilla injection signatures and packaged prediction gate."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.search(r"^version = '([^']+)'", (ROOT / "build.gradle").read_text("utf-8"), re.M)[1]
JAR = ROOT / "build/libs" / f"ancient-apple-hunter-{VERSION}.jar"


def run(*args):
    if Path(args[0]).stem.lower() == "javap":
        args = (args[0], "-J-Dfile.encoding=UTF-8", "-J-Dsun.stdout.encoding=UTF-8", *args[1:])
    result = subprocess.run(args, cwd=ROOT, text=True, encoding="utf-8", errors="replace", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


javap = shutil.which("javap")
java = shutil.which("java")
assert javap and java, "JDK tools are required"
cache_roots = [ROOT / ".gradle-user-home", ROOT.parent / "ancient-apple-hunter/.gradle-user-home", Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))]
intermediary = next(jar for cache in cache_roots for jar in (cache / "caches/fabric-loom/minecraftMaven").rglob("minecraft-merged-intermediary-*.jar"))
gson = next(jar for cache in cache_roots for jar in (cache / "caches/modules-2/files-2.1/com.google.code.gson/gson/2.13.2").rglob("gson-*.jar"))

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
xaero_jar=ROOT/'libs/xaerominimap-fabric-1.21.11-26.6.0.jar'
optional_injections=[]
def descriptor_args(value):
    return re.findall(r'\[*L[^;]+;|\[*[BCDFIJSZ]',value[1:value.index(')')])
for mixin in xaero_config['client']:
    details=run(javap,'-p','-v','-classpath',str(JAR),xaero_config['package']+'.'+mixin)
    assert 'org.spongepowered.asm.mixin.Pseudo' in details
    target=re.search(r'targets=\["([^"\]]+)"\]',details)[1].replace('/','.')
    api_jars=([ROOT/'libs/xaeroworldmap-fabric-1.21.11-1.44.2.jar'] if target.startswith('xaero.map.')
              else [xaero_jar,ROOT/'libs/xaerominimap-fabric-1.21.11-26.4.2.jar'])
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

# Prediction sources must still match the inherited, previously executed oracles.
with zipfile.ZipFile(JAR) as jar:
    certificate=json.loads(jar.read("prediction-validation.json"))
    loot26=json.loads(jar.read("loot26-validation.json"))
    digest=hashlib.sha256()
    paths=sorted(list((ROOT / "src/main/java/dev/tanghulu/ancientapple/prediction").rglob("*.java"))
        + list((ROOT / "src/main/java/kludwisz").rglob("*.java")), key=lambda p:p.relative_to(ROOT).as_posix())
    for path in paths:
        digest.update(path.relative_to(ROOT).as_posix().encode("utf-8")); digest.update(path.read_bytes())
    assert certificate["passed"] and loot26["passed"]
    assert digest.hexdigest()==certificate["sourceHash"]==loot26["sourceHash"]
    forbidden=("HunterController", "FlightController", "InventoryActions", "BaritoneBridge", "FreecamLease", "ModuleLease", "ThreatDetector", "FlightSupplies", "HunterMovementInputMixin", "FireworkStateAccessor", "HunterModule$Mode")
    assert not any(any(part in name for part in forbidden) for name in names), "Automation runtime leaked into locator"
    historical_models=("ChestReach", "FlightPlanner", "FlightPolicy", "GlidePilot", "NavigationProgress", "TakeoffSequence", "TargetQueue", "TargetSelector", "TransferReceipt")
    for model in historical_models:
        prefix="dev/tanghulu/ancientapple/core/"+model
        assert not any(n==prefix+".class" or n.startswith(prefix+"$") for n in names), model+" leaked into production"
        assert (ROOT/("src/test/java/"+prefix+".java")).is_file(), model+" regression fixture was lost"
    assert "dev/tanghulu/ancientapple/gui/BlacklistScreen.class" in names
    assert set(config["client"])=={"ContainerSyncMixin","ManualInteractionMixin","ManualCursorSyncMixin"}
    assert "baritone" not in json.loads(jar.read("fabric.mod.json")).get("suggests",{})
    data=json.loads((ROOT/"build/reports/loot-version-data.json").read_text("utf-8"))
    oracle=json.loads((ROOT/"build/reports/loot-profile-oracle.json").read_text("utf-8"))
    assert data["passed"] and oracle["passed"]
    expected_versions={"1.21."+str(i) for i in range(1,12)}|{"26.1","26.1.1","26.1.2","26.2","26.3"}
    assert {v["version"] for v in data["versions"]}==expected_versions
    assert {v["version"] for v in oracle["versions"]}==expected_versions
    audited={v["version"]:v for v in data["versions"]}
    for checked in oracle["versions"]:
        profile_hash=hashlib.sha256(jar.read("loot-profiles/"+checked["version"]+".json")).hexdigest()
        assert checked["checked"]==1024 and checked["mismatches"]==0
        assert checked["missingRegistryChecked"]==1024
        assert profile_hash==checked["profileSha256"]==audited[checked["version"]]["profileSha256"]
        assert audited[checked["version"]]["unchangedTemplates"]==58 and audited[checked["version"]]["unchangedPools"]==7
    for backend in ("26.2","26.3"):
        native=json.loads((ROOT/("build/reports/native-loot-"+backend+".json")).read_text("utf-8"))
        assert native["passed"] and native["checked"]==1024 and not native["errors"]
module_code=run(javap,"-p","-c","-classpath",str(JAR),"dev.tanghulu.ancientapple.HunterModule")
assert "HunterController" not in module_code
assert 'prediction/LootVersion.MC_26_1:' in module_code, 'Default loot version must be 26.1'
assert 'prediction/LootVersion.MC_1_21_11:' not in module_code, 'Old default loot version remains'
assert "screenToOpen" in module_code
command_code=run(javap,"-p","-c","-classpath",str(JAR),"dev.tanghulu.ancientapple.HunterCommand")
assert not re.search(r"// String (auto|go|resume|pause|stop|manual|status)$",command_code,re.M)
assert command_code.count('// String list') == 1, 'Only blacklist list should remain'
assert '列出目标' not in module_code
assert not re.search(r'// String 状态$',module_code,re.M)
marker_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.render.WorldMarkerRenderer')
selection_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.core.MarkerSelection')
assert '航标光束' in module_code and 'Render3DEvent' in module_code and 'depthRenderer' in marker_code
assert '光束最大距离' in module_code and 'beamDistance' in module_code
assert '多人游戏请选择服务器版本。' in module_code
assert 'gui/NoteSetting' in module_code
assert re.search(r'getfield[^\n]*beamDistance[\s\S]*?invokevirtual[^\n]*WorldMarkerRenderer.renderBeams:', module_code)
assert re.search(r'getfield[^\n]*renderDistance[\s\S]*?invokevirtual[^\n]*WorldMarkerRenderer.renderIcons:', module_code)
assert 'core/MarkerSelection.nearest:' in marker_code and 'sipush        128' in marker_code
assert 'NametagUtils.to2D:' in marker_code
assert 'net/minecraft/class_638.method_17742:(Lnet/minecraft/class_3959;)Lnet/minecraft/class_3965;' in marker_code # World.raycast
assert 'WorldMarkerRenderer.renderIcons:' in module_code
apple_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.integration.XaeroAppleRenderer')
assert 'getShaderLights' in apple_code and 'setShaderLights' in apple_code
assert 'net/minecraft/class_308$class_11274.field_60026' in apple_code # ITEMS_FLAT after remapping
assert 'float 16.0f' in apple_code and 'float 32.0f' in apple_code and 'float 1.2f' in apple_code
note_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.gui.NoteSetting')
assert 'registerCustomFactory' in note_code and 'GuiTheme.label' in note_code
assert 'openedWithApples' in module_code
assert '世界标识' not in module_code and 'worldSlot' not in module_code
assert '131072' in module_code
prediction_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.prediction.PredictionEngine')
assert re.search(r'iconst_0\s+\d+: invokeinterface[^\n]+Future.cancel',prediction_code), 'Cancellation must not interrupt JAR reads'
assert 'shutdownNow' not in prediction_code and 'ExecutorService.shutdown:' in prediction_code
assert 'dev/tanghulu/ancientapple/prediction/LootEnchantments.class' in names
screen_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.gui.BlacklistScreen')
assert all(s in screen_code for s in ('confirmedButton','textBox','restoreRecords','openedPositions'))
assert 'net/minecraft/class_309.method_1455:(Ljava/lang/String;)V' in screen_code # Keyboard.setClipboard after remapping
bridge_code=run(javap,'-p','-c','-classpath',str(JAR),'dev.tanghulu.ancientapple.integration.XaeroBridge')
assert '附魔金苹果' in bridge_code and '◆' in bridge_code
assert 'MarkerSelection.shouldMark:' in bridge_code and 'openedWithApples' in selection_code
assert 'ChestTarget.isBlacklisted:' in selection_code
assert not re.search(r'// String \?$',bridge_code,re.M)
assert 'ChestTarget.contentsLabel' not in module_code and 'ChestTarget.contentsLabel' not in bridge_code
# Check the string concatenation recipes in the packaged bytecode.
verbose=run(javap,"-p","-v","-classpath",str(JAR),"dev.tanghulu.ancientapple.HunterModule")
assert not re.search(r"\\u0001 \\u0001 \\u0001",verbose), "Coordinate label remains"

check = ROOT / "build/release-check/CheckRelease.java"
check.parent.mkdir(parents=True, exist_ok=True)
check.write_text('''import dev.tanghulu.ancientapple.core.ValidationGate;
class CheckRelease {
    public static void main(String[] args) {
        if (!ValidationGate.certified()) throw new AssertionError("Packaged prediction validation gate is closed");
        System.out.println("Packaged prediction validation gate: OPEN");
    }
}
''', encoding="utf-8")
gate = run(java, "-cp", str(JAR) + ";" + str(gson), str(check)).strip()
report = {
    "passed": True, "version": VERSION,
    "jarSha256": hashlib.sha256(JAR.read_bytes()).hexdigest(),
    "maxClassMajor": max(majors), "javaTarget": 21,
    "packagedGate": gate, "injections": injections, "automationRuntimeRemoved": True, "predictionSourceHash": digest.hexdigest(), "blacklistWindowPresent": True, "coordinateLabelRemoved": True,
    "markerName": "附魔金苹果", "mapItem": "minecraft:enchanted_golden_apple", "fallbackMapSymbol": "◆", "beamSettingPresent": True,
    "independentBeamDistance":True, "beamDistanceDefault":2048, "beamDistanceMaximum":131072,
    "visibleSeedVersionNote":True, "flatItemLightingRestored":True,
    "minimapAppleSize":16, "fullMapAppleSize":32, "fullMapHoverTextScale":1.2,
    "optionalXaeroInjections":optional_injections, "duplicateXaeroWorldMarkerSuppressed":True,
    "openedMarkerFilterPresent": True, "targetListAndStatusRemoved": True,
    "historicalNavigationModelsTestOnly":list(historical_models), "worldRendererSeparated":True,
    "worldIdentifierSettingRemoved": True, "maximumSearchRadius": 131072,
    "lootVersions":sorted(expected_versions), "profileOracleSamples":16384,
    "nativeLatestBackendSamples":2048, "blacklistSearchRestoreAndConfirmationPresent":True,
    "oracleEvidenceInheritedFrom017":True,
    "defaultLootVersion":"26.1",
    "missingServerRegistrySamples":16384, "cooperativeCancellation":True,
    "scope": "ZIP integrity, class target, bundled gate, injection target descriptors and marker bytecode audit; not a visual Minecraft client test.",
}
output = ROOT / "build/reports/release-checks.json"
output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: report[k] for k in ("passed", "version", "jarSha256", "packagedGate")}, indent=2))
print(f"Verified {len(injections)} remapped injection signatures; Java {report['javaTarget']} target")
