"""Package the locator JAR, rebuildable source, and current verification evidence."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
VERSION=re.search(r"^version = '([^']+)'",(ROOT.parent/"build.gradle").read_text("utf-8"),re.M)[1]
NAME=f"ancient-apple-hunter-{VERSION}"
DIST=ROOT/"dist"
DIST.mkdir(exist_ok=True)
jar=ROOT/"build/libs"/f"{NAME}.jar"
release=json.loads((ROOT/"build/reports/release-checks.json").read_text("utf-8"))
units=json.loads((ROOT/"build/reports/unit-checks.json").read_text("utf-8"))
comparison=json.loads((ROOT/"build/reports/refactor-comparison.json").read_text("utf-8"))
assert release["passed"] and units["passed"] and units["failed"]==0
assert release["jarSha256"]==hashlib.sha256(jar.read_bytes()).hexdigest(),"Rerun check_release.py"
assert comparison["passed"] and comparison["jarSha256"]==release["jarSha256"],"Rerun check_refactor.py"
with zipfile.ZipFile(jar) as archive:
    assert archive.testzip() is None
    assert json.loads(archive.read("fabric.mod.json"))["version"]==VERSION
shutil.copy2(jar,DIST/jar.name)
for name in ("README.md","VALIDATION.md",f"CHANGELOG-{VERSION}.md","LICENSE"):
    shutil.copy2(ROOT/name,DIST/name)
validation=DIST/"validation"
validation.mkdir(exist_ok=True)
for name,path in {
    "release-checks.json":ROOT/"build/reports/release-checks.json",
    "unit-checks.json":ROOT/"build/reports/unit-checks.json",
    "baseline-unit-checks.json":ROOT/"build/reports/baseline-unit-checks.json",
    "refactor-comparison.json":ROOT/"build/reports/refactor-comparison.json",
    "baseline-build.log":ROOT/"build/baseline-build.log",
    "release-build.log":ROOT/"build/release-build.log",
    "prediction-certificate.json":ROOT/"src/main/resources/prediction-validation.json",
    "loot26-certificate.json":ROOT/"src/main/resources/loot26-validation.json",
    "loot-version-data.json":ROOT/"build/reports/loot-version-data.json",
    "loot-profile-oracle.json":ROOT/"build/reports/loot-profile-oracle.json",
    "native-loot-26.2.json":ROOT/"build/reports/native-loot-26.2.json",
    "native-loot-26.3.json":ROOT/"build/reports/native-loot-26.3.json",
}.items():
    shutil.copy2(path,validation/name)

files=[ROOT/name for name in (".gitignore","build.gradle","settings.gradle","gradle.properties","gradlew","gradlew.bat","libs/README.md","README.md","VALIDATION.md",f"CHANGELOG-{VERSION}.md","LICENSE","scripts/check_release.py","scripts/package_release.py","src/qa/java/dev/tanghulu/ancientapple/qa/UnitTestMain.java")]
for directory in ("src/main","src/test","src/qa","licenses","gradle"):
    files.extend(p for p in (ROOT/directory).rglob("*") if p.is_file())
for name in ("fetch_loot_versions.py","audit_loot_versions.py","nbt_audit.py","fetch_vanilla_26.py","validate_native_loot.py","validate_vanilla_26.py","qa26/Vanilla26Oracle.java"):
    files.append(ROOT/"scripts"/name)
files.append(ROOT/"scripts/check_refactor.py")
files=list(set(files))
source=DIST/f"{NAME}-source.zip"
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(files):
        archive.write(path,"ancient-apple-refactored/"+path.relative_to(ROOT).as_posix())
with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
    assert [n for n in archive.namelist() if n.endswith(".jar")]==["ancient-apple-refactored/gradle/wrapper/gradle-wrapper.jar"]
    assert not any("HunterController" in n or "HunterMovementInputMixin" in n for n in archive.namelist())

# Include only this run's validation artifacts; older dist files remain untouched.
evidence_names=("release-checks.json","unit-checks.json","release-build.log","prediction-certificate.json","loot26-certificate.json","loot-version-data.json","loot-profile-oracle.json","native-loot-26.2.json","native-loot-26.3.json")
evidence_names+=("baseline-unit-checks.json","refactor-comparison.json","baseline-build.log")
deliverables=[DIST/jar.name,source]+[DIST/name for name in ("README.md","VALIDATION.md",f"CHANGELOG-{VERSION}.md","LICENSE")]+[validation/name for name in evidence_names]
checksums=DIST/"SHA256SUMS.txt"
checksums.write_text("".join(hashlib.sha256(p.read_bytes()).hexdigest()+"  "+p.relative_to(DIST).as_posix()+"\n" for p in sorted(deliverables)),"ascii")
release_zip=DIST/f"{NAME}-release.zip"
with zipfile.ZipFile(release_zip,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(deliverables+[checksums]):
        archive.write(path,NAME+"/"+path.relative_to(DIST).as_posix())
with zipfile.ZipFile(release_zip) as archive:
    assert archive.testzip() is None
    for path in deliverables:
        assert archive.read(NAME+"/"+path.relative_to(DIST).as_posix())==path.read_bytes()
for path in (DIST/jar.name,source,release_zip):
    print(path.name,path.stat().st_size,hashlib.sha256(path.read_bytes()).hexdigest())
