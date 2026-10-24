"""Compare this build with the user's original JAR and source ZIP. Standard library only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev/tanghulu/ancientapple/"
VERSION = re.search(r"^version = '([^']+)'", (ROOT / "build.gradle").read_text("utf-8"), re.M)[1]
HISTORICAL = {
    "ChestReach", "FlightPlanner", "FlightPolicy", "GlidePilot", "NavigationProgress",
    "TakeoffSequence", "TargetQueue", "TargetSelector", "TransferReceipt",
}
MODIFIED = {
    PACKAGE + name for name in (
        "HunterModule", "integration/XaeroBridge", "integration/XaeroAppleRenderer",
        "navigation/ManualCollectionObserver",
    )
}
ADDED = {PACKAGE + name for name in ("core/MarkerSelection", "render/WorldMarkerRenderer")}
REMOVED = {PACKAGE + "core/" + name for name in HISTORICAL}


def owner(name):
    return name.removesuffix(".class").split("$", 1)[0]


def public_api(jar, name):
    output = subprocess.run(
        [shutil.which("javap"), "-public", "-s", "-classpath", str(jar), name],
        check=True, capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout
    return output.split("{", 1)[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline_jar", type=Path)
    parser.add_argument("baseline_source", type=Path)
    args = parser.parse_args()
    current = ROOT / "build/libs" / f"ancient-apple-hunter-{VERSION}.jar"
    with zipfile.ZipFile(args.baseline_jar) as old, zipfile.ZipFile(current) as new:
        before = {n for n in old.namelist() if not n.endswith("/")}
        after = {n for n in new.namelist() if not n.endswith("/")}
        removed = sorted(before - after)
        added = sorted(after - before)
        changed = sorted(n for n in before & after if old.read(n) != new.read(n))
        assert all(n.endswith(".class") and owner(n) in REMOVED for n in removed), removed
        assert {owner(n) for n in removed} == REMOVED
        assert all(n.endswith(".class") and owner(n) in ADDED for n in added), added
        assert {owner(n) for n in added} == ADDED
        assert all(n == "fabric.mod.json" or (n.endswith(".class") and owner(n) in MODIFIED) for n in changed), changed
        old_metadata = json.loads(old.read("fabric.mod.json"))
        new_metadata = json.loads(new.read("fabric.mod.json"))
        old_metadata["version"] = new_metadata["version"]
        assert old_metadata == new_metadata, "Mod identity, dependencies or mixin declarations changed"
        for name in after:
            assert not (name.endswith(".class") and owner(name) in REMOVED)
        unchanged = before & after - set(changed)
        prediction_entries = sorted(n for n in unchanged if n.startswith(PACKAGE + "prediction/"))
        assert prediction_entries
        # Verify the original implementation was retained as test-only source, byte for byte.
        with zipfile.ZipFile(args.baseline_source) as sources:
            source_names = {"/".join(n.split("/")[1:]): n for n in sources.namelist()}
            for name in HISTORICAL:
                path = PACKAGE + "core/" + name + ".java"
                assert sources.read(source_names["src/main/java/" + path]) == (ROOT / "src/test/java" / path).read_bytes()
            protected = []
            for relative, entry in source_names.items():
                if not relative.startswith("src/main/"):
                    continue
                if relative.endswith(".java") and owner(relative.removeprefix("src/main/java/").removesuffix(".java")) in MODIFIED | REMOVED:
                    continue
                assert sources.read(entry) == (ROOT / relative).read_bytes(), relative
                protected.append(relative)
        own_before = [n for n in before if n.startswith(PACKAGE) and not n.startswith(PACKAGE + "vendor/") and n.endswith(".class")]
        own_after = [n for n in after if n.startswith(PACKAGE) and not n.startswith(PACKAGE + "vendor/") and n.endswith(".class")]
        before_bytes = sum(len(old.read(n)) for n in own_before)
        after_bytes = sum(len(new.read(n)) for n in own_after)
    # Module is the command/mixin/GUI facade; its public surface stays compatible.
    facade = "dev.tanghulu.ancientapple.HunterModule"
    assert public_api(args.baseline_jar, facade) == public_api(current, facade)
    report = {
        "passed": True, "version": VERSION,
        "baselineJarSha256": hashlib.sha256(args.baseline_jar.read_bytes()).hexdigest(),
        "jarSha256": hashlib.sha256(current.read_bytes()).hexdigest(),
        "baselineBytes": args.baseline_jar.stat().st_size, "currentBytes": current.stat().st_size,
        "ownClassesBefore": len(own_before), "ownClassesAfter": len(own_after),
        "ownClassBytesBefore": before_bytes, "ownClassBytesAfter": after_bytes,
        "removedClasses": removed, "addedClasses": added, "changedEntries": changed,
        "unchangedEntries": len(unchanged), "unchangedPredictionClasses": len(prediction_entries),
        "unchangedMainSourcesAndResources": len(protected),
        "historicalModelsPreservedAsTestSource": sorted(HISTORICAL), "publicModuleApiUnchanged": True,
        "scope": "Binary/source preservation and API comparison; does not prove live client behavior.",
    }
    output = ROOT / "build/reports/refactor-comparison.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: report[k] for k in (
        "passed", "baselineBytes", "currentBytes", "ownClassesBefore", "ownClassesAfter",
        "ownClassBytesBefore", "ownClassBytesAfter", "unchangedEntries", "unchangedPredictionClasses",
    )}, indent=2))


if __name__ == "__main__":
    main()
