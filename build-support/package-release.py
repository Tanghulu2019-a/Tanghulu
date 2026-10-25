"""Package this checkout after a successful build; no historical archive is required."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile

from importlib.util import spec_from_file_location, module_from_spec

ROOT = Path(__file__).resolve().parent.parent
spec = spec_from_file_location('verify_release', ROOT/'build-support/verify-release.py')
verify = module_from_spec(spec)
spec.loader.exec_module(verify)

def main():
    jar = ROOT/'build/libs/Tanghulu-1.0.4.jar'
    subprocess.run([sys.executable, str(ROOT/'build-support/verify-stash.py')], check=True)
    subprocess.run([sys.executable, str(ROOT/'build-support/verify-release.py')], check=True)
    # Merge validation also checks the complete 107-test Ancient suite report.
    report_path = ROOT/'build/reports/merge-validation.json'
    report = json.loads(report_path.read_text('utf-8'))
    assert report['passed'] and report['jarSha256'] == hashlib.sha256(jar.read_bytes()).hexdigest()
    output = ROOT/'dist'
    output.mkdir(exist_ok=True)
    source = output/'Tanghulu-1.0.4-source.zip'
    with zipfile.ZipFile(source, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in verify.source_paths():
            name = 'Tanghulu/'+path.relative_to(ROOT).as_posix()
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 25, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if path.name == 'gradlew' else 0o100644) << 16
            archive.writestr(info, path.read_bytes())
    target = output/jar.name
    shutil.copyfile(jar, target)
    subprocess.run([sys.executable, str(ROOT/'build-support/verify-release.py'), '--jar', str(target), '--source', str(source)], check=True)
    lines = [hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in [target, source]]
    (output/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n', encoding='utf-8')
    print('Release candidates: '+str(output))

if __name__ == '__main__':
    main()
