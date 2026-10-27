"""Resolve official, version-specific build dependencies and retain an auditable lock.

Uses curl so downloads also work with Windows' configured TLS/network setup.
Only compile dependencies are downloaded; none are bundled into the addon.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MC = sys.argv[1] if len(sys.argv) > 1 else "1.21.11"
if MC != "1.21.11":
    raise SystemExit("Only Minecraft 1.21.11 is supported")
DEST = ROOT / "libs" / MC
DEST.mkdir(parents=True, exist_ok=True)


def download(url, path):
    subprocess.run(["curl.exe", "-fsSL", "--retry", "2", "--connect-timeout", "20",
                    "--max-time", "180", url, "-o", str(path)], check=True)


def json_url(url, name):
    path = DEST / name
    download(url, path)
    return json.loads(path.read_text("utf-8"))


existing_lock = DEST / "dependencies.json"
if existing_lock.exists():
    lock = json.loads(existing_lock.read_text("utf-8"))
    for name, asset in lock.items():
        if not isinstance(asset, dict) or "file" not in asset:
            continue
        path = DEST / asset["file"]
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != asset["sha256"]:
            download(asset["url"], path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != asset["sha256"]:
            raise RuntimeError(f"Checksum mismatch: {name}")
    print(f"Locked dependencies verified for {MC}", flush=True)
    raise SystemExit(0)


def meteor():
    version = MC
    base = f"https://maven.meteordev.org/snapshots/meteordevelopment/meteor-client/{version}-SNAPSHOT"
    metadata = DEST / "meteor-metadata.xml"
    download(base + "/maven-metadata.xml", metadata)
    value = next(s.findtext("value") for s in ET.parse(metadata).findall("./versioning/snapshotVersions/snapshotVersion")
                 if s.findtext("extension") == "jar" and s.find("classifier") is None)
    url = f"{base}/meteor-client-{value}.jar"
    path = DEST / "meteor.jar"
    if not path.exists():
        download(url, path)
    return {"file": path.name, "version": value, "url": url, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def modrinth(project, preferred):
    query = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": json.dumps([MC])})
    versions = json_url(f"https://api.modrinth.com/v2/project/{project}/version?{query}", project + "-versions.json")
    candidates = [v for v in versions if preferred in v["version_number"]]
    if not candidates:
        raise RuntimeError(f"No {project} {preferred} release for {MC}; choose an API-compatible release explicitly")
    version = candidates[0]
    asset = next((f for f in version["files"] if f["primary"]), version["files"][0])
    path = DEST / (project + ".jar")
    expected = asset["hashes"]["sha512"]
    if not path.exists() or hashlib.sha512(path.read_bytes()).hexdigest() != expected:
        download(asset["url"], path)
    if hashlib.sha512(path.read_bytes()).hexdigest() != expected:
        raise RuntimeError(f"Checksum mismatch: {project}")
    return {"file": path.name, "version": version["version_number"], "url": asset["url"],
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}


lock = {"minecraft": MC, "meteor": meteor()}
for project, preferred in [("xaeros-minimap", "26.4.2"), ("xaeros-world-map", "1.44.2"), ("xaeroplus", "2.35.1")]:
    lock[project] = modrinth(project, preferred)
maps = json_url(f"https://meta.fabricmc.net/v2/versions/yarn/{MC}", "yarn-versions.json")
lock["yarn"] = "1.21.11+build.3"
assert any(m["version"] == lock["yarn"] for m in maps), "Pinned Yarn mappings unavailable"
(DEST / "dependencies.json").write_text(json.dumps(lock, ensure_ascii=False, indent=2) + "\n", "utf-8")
print(f"Dependency lock ready for {MC}", flush=True)
