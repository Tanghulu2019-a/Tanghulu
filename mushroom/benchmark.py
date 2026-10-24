"""Alternate baseline/current workers in separate JVMs using identical official runtimes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import statistics
import subprocess
import time
import zipfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-jar', type=Path, required=True)
parser.add_argument('--cache', type=Path, required=True)
parser.add_argument('--rounds', type=int, default=3)
parser.add_argument('--version', default='26.1')
parser.add_argument('--radius', type=int, default=16000)
args = parser.parse_args()
assert args.rounds > 0
baseline = root / 'build/mushroom/baseline-worker.jar'
with zipfile.ZipFile(args.baseline_jar) as archive:
    baseline.write_bytes(archive.read('tanghulu/mushroom/worker.jar'))
current = root / 'build/mushroom/worker.jar'
variants = [('original', baseline), ('optimized', current)]
records, expected = [], None
for run in range(args.rounds):
    for label, worker in variants if run % 2 == 0 else reversed(variants):
        start = time.perf_counter()
        command = ['java', '-Xmx1G', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8',
                   '-cp', str(worker) + os.pathsep + str(args.cache / (args.version + '-runtime') / '*'),
                   'dev.tanghulu.mushroom.BiomeWorker', str(args.cache / (args.version + '-mappings.txt')),
                   '-7346913998703726680', '0', '0', str(args.radius), '32', 'all']
        result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8',
                                check=True, timeout=180)
        elapsed = time.perf_counter() - start
        targets = [line for line in result.stdout.splitlines() if line.startswith('TARGET ')]
        if expected is None:
            expected = targets
        assert targets == expected, (label, targets, expected)
        assert result.stdout.splitlines()[-1] == 'DONE ' + str(len(targets))
        row = dict(variant=label, run=run+1, seconds=round(elapsed, 3), islands=len(targets))
        records.append(row)
        print(row, flush=True)
old, new = (statistics.median(r['seconds'] for r in records if r['variant'] == v)
            for v in ('original', 'optimized'))
report = dict(passed=True, workerSha256=hashlib.sha256(current.read_bytes()).hexdigest(),
              version=args.version, seed=-7346913998703726680, center=[0, 0], radius=args.radius,
              spacing=32, islands=len(expected), identicalTargets=True, originalMedianSeconds=old,
              optimizedMedianSeconds=new, reductionPercent=round((old-new)/old*100, 1), runs=records)
output = root / ('validation/lean-benchmark-' + args.version + '.json')
output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report), flush=True)
