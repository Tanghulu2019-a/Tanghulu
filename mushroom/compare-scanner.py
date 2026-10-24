"""Compare complete destinations/progress with the scanner in the original source ZIP."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('baseline', type=Path)
args = parser.parse_args()
output = root / 'build/mushroom/comparison'
output.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.baseline) as archive:
    source = archive.read('Tanghulu/mushroom/src/dev/tanghulu/mushroom/IslandScanner.java').decode('utf-8')
baseline = output / 'BaselineIslandScanner.java'
baseline.write_text(source.replace('IslandScanner', 'BaselineIslandScanner'), encoding='utf-8')
subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-d', str(output), str(baseline),
                str(root / 'mushroom/src/dev/tanghulu/mushroom/IslandScanner.java'),
                str(root / 'mushroom/qa/ScannerComparison.java')], check=True)
result = subprocess.run(['java', '-Xmx512m', '-cp', str(output), 'dev.tanghulu.mushroom.ScannerComparison'],
                        capture_output=True, text=True, check=True, timeout=120)
report = json.loads(result.stdout)
report['scannerSha256'] = hashlib.sha256((root / 'mushroom/src/dev/tanghulu/mushroom/IslandScanner.java').read_bytes()).hexdigest()
report['baselineSourceSha256'] = hashlib.sha256(args.baseline.read_bytes()).hexdigest()
(root / 'validation/scanner-comparison.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report))
