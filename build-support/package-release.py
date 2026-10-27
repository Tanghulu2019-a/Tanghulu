"""Package the current release from a completed build."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / 'dist'
subprocess.run([sys.executable, str(ROOT / 'build-support/package-compat-release.py'), str(out)], check=True)
subprocess.run([sys.executable, str(ROOT / 'build-support/verify-release.py'), '--jar', str(out / 'Tanghulu-1.0.6.jar'), '--source', str(out / 'Tanghulu-1.0.6-source.zip')], check=True)
