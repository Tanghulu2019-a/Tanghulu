"""Copy the 1.21.11 source tree into Gradle's generated-source directory."""
from pathlib import Path
import sys

MC, PROJECT, OUTPUT = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
if MC != '1.21.11':
    raise SystemExit('Only Minecraft 1.21.11 is supported')
OUTPUT.mkdir(parents=True, exist_ok=True)
expected = set()
for path in (PROJECT / 'src/main/java').rglob('*.java'):
    relative = path.relative_to(PROJECT / 'src/main/java')
    content = path.read_text('utf-8')
    target = OUTPUT / relative
    expected.add(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or target.read_text('utf-8') != content:
        target.write_text(content, 'utf-8')
for target in OUTPUT.rglob('*.java'):
    if target not in expected:
        target.unlink()
print(f'Prepared {len(expected)} {MC} sources for {PROJECT.name}')
