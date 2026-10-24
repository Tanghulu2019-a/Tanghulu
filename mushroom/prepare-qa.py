"""Fetch official release metadata/runtimes for compatibility QA; never ship Mojang jars."""
from pathlib import Path
import json, subprocess, zipfile, hashlib, concurrent.futures

root = Path(__file__).resolve().parent.parent
cache = root / 'mushroom-qa'
cache.mkdir(parents=True,exist_ok=True)
lock_path=root/'src/main/resources/tanghulu/mushroom/releases.json'
releases=json.loads(lock_path.read_text())
versions=list(releases)
def fetch(url, path, sha=None):
    if path.exists() and (sha is None or hashlib.sha1(path.read_bytes()).hexdigest()==sha): return
    subprocess.run(['curl.exe','-fLsS','--retry','4','--retry-all-errors','--connect-timeout','30','--max-time','240',url,'-o',str(path)],check=True)
    if sha: assert hashlib.sha1(path.read_bytes()).hexdigest()==sha, path
def prepare(v):
    entries=releases[v]
    server=cache/(v+'-server.jar'); fetch(entries['server']['url'],server,entries['server']['sha1'])
    if 'server_mappings' in entries:
        m=entries['server_mappings']; fetch(m['url'],cache/(v+'-mappings.txt'),m['sha1'])
    runtime=cache/(v+'-runtime'); runtime.mkdir(exist_ok=True)
    with zipfile.ZipFile(server) as z:
        for n in z.namelist():
            if n.startswith(('META-INF/versions/','META-INF/libraries/')) and n.endswith('.jar'):
                p=runtime/Path(n).name
                if not p.exists() or p.stat().st_size!=z.getinfo(n).file_size: p.write_bytes(z.read(n))
    print('Prepared',v,flush=True)
    return v, entries
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    lock=dict(pool.map(prepare,versions))
out=root/'src/main/resources/tanghulu/mushroom/releases.json'
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(lock,indent=2)+'\n')
