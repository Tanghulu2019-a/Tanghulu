"""Run a Java 21 fixture before/after removal of local-variable debug attributes."""
import importlib.util
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('class_debug', ROOT / 'build-support/class-debug.py')
debug = importlib.util.module_from_spec(spec)
spec.loader.exec_module(debug)
before = ROOT / 'build/class-debug/before'
after = ROOT / 'build/class-debug/after'
before.mkdir(parents=True, exist_ok=True)
after.mkdir(parents=True, exist_ok=True)
subprocess.run(['javac', '--release', '21', '-g', '-parameters', '-d', str(before),
                str(ROOT / 'build-support/fixtures/DebugTablesFixture.java')], check=True, capture_output=True)
removed = 0
for path in before.rglob('*.class'):
    original = path.read_bytes()
    compacted = debug.compact(original)
    assert debug.compact(compacted) == compacted
    removed += len(original) - len(compacted)
    target = after / path.relative_to(before)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(compacted)
assert removed > 0, 'Fixture must contain local-variable debug tables'
def run(directory):
    return subprocess.run(['java', '-Xverify:all', '-cp', str(directory), 'DebugTablesFixture'],
                          capture_output=True, text=True, check=True).stdout
assert run(before) == run(after), 'Program behavior or reflection data changed'
print(run(after).strip() + ' removedBytes=' + str(removed))
