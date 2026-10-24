"""Compare returned destinations to independently generated 26.3 chunk biomes."""
import argparse
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache', type=Path, default=root / 'mushroom-qa')
args = parser.parse_args()
output = root / 'build/mushroom/oracle-classes'
output.mkdir(parents=True, exist_ok=True)
runtime = str(args.cache / '26.3-runtime/*')
subprocess.run(['javac', '--release', '25', '-encoding', 'UTF-8', '-cp', runtime, '-d', str(output),
                str(root / 'prediction26/src/dev/tanghulu/prediction26/Vanilla26.java'),
                str(root / 'mushroom/qa/MushroomOracle26.java')], check=True)
subprocess.run(['java', '-Xmx2G', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8',
                '-cp', str(output) + os.pathsep + runtime, 'dev.tanghulu.prediction26.MushroomOracle26',
                str(root), str(args.cache.resolve())], check=True, timeout=180)
