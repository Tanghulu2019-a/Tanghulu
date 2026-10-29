"""Package the current release from a completed build."""
from pathlib import Path
import subprocess
import sys
import re

ROOT = Path(__file__).resolve().parent.parent
out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / 'dist'
version = re.search(r"(?m)^version\s*=\s*'([^']+)'", (ROOT / 'build.gradle').read_text('utf-8')).group(1)
subprocess.run([sys.executable, str(ROOT / 'build-support/package-compat-release.py'), str(out)], check=True)
subprocess.run([sys.executable, str(ROOT / 'build-support/verify-release.py'), '--jar', str(out / f'Tanghulu-{version}.jar'), '--source', str(out / f'Tanghulu-{version}-source.zip')], check=True)
