"""Prepare Minecraft ABI-specific Java sources from the shared, reviewed source tree.

Overrides live in compat/src/<version>/<root|ancient>/ and contain real implementations.
Generated files are build output; edit the shared sources or explicit overrides instead.
"""
from pathlib import Path
import sys
from legacy1214 import transform

MC, PROJECT, OUTPUT = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
ROOT = Path(__file__).resolve().parent.parent
PATCH = int(MC.split('.')[-1])
OUTPUT.mkdir(parents=True, exist_ok=True)
expected = set()
for path in (PROJECT / 'src/main/java').rglob('*.java'):
    relative = path.relative_to(PROJECT / 'src/main/java')
    content = path.read_text('utf-8')
    if MC == '1.21.4':
        content = transform(relative.as_posix(), content)
    if path.name == 'VersionConstants.java':
        content = content.replace('MINECRAFT_VERSION = "1.21.11"', f'MINECRAFT_VERSION = "{MC}"')
        content = content.replace('IS_1_21_4 = false', f'IS_1_21_4 = {str(PATCH == 4).lower()}')
    override = ROOT / 'compat/src' / MC / ('ancient' if PROJECT.name == 'ancient' else 'root') / relative
    if override.is_file():
        content = override.read_text('utf-8')
    target = OUTPUT / relative
    expected.add(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or target.read_text('utf-8') != content:
        target.write_text(content, 'utf-8')
for target in OUTPUT.rglob('*.java'):
    if target not in expected:
        target.unlink()
print(f'Prepared {len(expected)} {MC} sources for {PROJECT.name}')
