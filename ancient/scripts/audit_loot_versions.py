"""Audit official loot rules and derive small version profiles for the 1.21.11 client."""
from pathlib import Path
from collections import OrderedDict
import hashlib
import json
import zipfile
from nbt_audit import decode

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / '.downloads/loot-versions'
OUT = ROOT / 'src/main/resources/loot-profiles'
OUT.mkdir(parents=True, exist_ok=True)
TABLE = 'data/minecraft/loot_table/chests/ancient_city.json'


def load(z, name):
    return json.loads(z.read(name))


def tag(z, kind, value):
    if isinstance(value, list):
        return list(dict.fromkeys(v for part in value for v in tag(z, kind, part)))
    if isinstance(value, dict):
        value = value['id']
    if not value.startswith('#'):
        return [value]
    namespace, name = value[1:].split(':')
    return tag(z, kind, load(z, f'data/{namespace}/tags/{kind}/{name}.json')['values'])


def normalize(value):
    if isinstance(value, list):
        return [normalize(v) for v in value]
    if not isinstance(value, dict):
        return value
    result = {k: normalize(v) for k, v in value.items() if not (k == 'bonus_rolls' and v == 0) and not (k == 'add' and v is False)}
    if 'modifier' in result:
        modifiers = result.pop('modifier')
        result['functions'] = modifiers if isinstance(modifiers, list) else [modifiers]
        for modifier in result['functions']:
            modifier['function'] = modifier.pop('type')
    return result


def selection(z, name):
    obj = load(z, 'data/minecraft/enchantment/' + name.split(':')[1] + '.json')
    out = {k: obj[k] for k in ('min_cost', 'max_cost', 'max_level', 'weight')}
    out['exclusive_set'] = sorted(tag(z, 'enchantment', obj.get('exclusive_set', [])))
    items = tag(z, 'item', obj.get('primary_items', obj['supported_items']))
    out['items'] = [item for item in ('minecraft:diamond_hoe', 'minecraft:diamond_leggings', 'minecraft:iron_leggings') if item in items]
    return out


def structure(data):
    data.pop('DataVersion', None)
    # 26.3 renamed the serialized block-state keys without changing block states.
    for palette in ([data['palette']] if 'palette' in data else data.get('palettes', [])):
        for state in palette:
            if 'Name' in state:
                state['id'] = state.pop('Name')
            if 'Properties' in state:
                state['properties'] = state.pop('Properties')
    for block in data.get('blocks', []):
        nbt = block.get('nbt', {})
        if nbt.get('id') == 'minecraft:furnace':
            for old, new in (('BurnTime', 'lit_time_remaining'), ('CookTime', 'cooking_time_spent'), ('CookTimeTotal', 'cooking_total_time')):
                if old in nbt:
                    nbt[new] = nbt.pop(old)
            nbt.setdefault('lit_total_time', 0)
    return data


baseline = zipfile.ZipFile(DATA / '1.21.11/game.jar')
report = {'passed': True, 'latestRelease': json.loads((DATA / 'manifest.json').read_text('utf-8'))['latest']['release'], 'versions': []}
for folder in sorted(DATA.iterdir()):
    if not folder.is_dir():
        continue
    meta = json.loads((folder / 'version.json').read_text('utf-8'))
    with zipfile.ZipFile(folder / 'game.jar') as archive:
        table = normalize(load(archive, TABLE))
        options = tag(archive, 'enchantment', '#minecraft:on_random_loot')
        differences = []
        for name in options + ['minecraft:swift_sneak']:
            actual, current = selection(archive, name), selection(baseline, name)
            # Only leggings and hoe can be enchanted with levels by this loot table.
            # Random books use max_level; effects and other supported equipment do not consume RNG.
            if actual['max_level'] != current['max_level'] or ((actual['items'] or current['items']) and actual != current):
                differences.append({'enchantment': name, 'version': actual, 'client': current})
        profile = {'version': folder.name, 'table': table, 'enchantments': options,
                   'selection': {name: selection(archive, name) for name in options + ['minecraft:swift_sneak']}}
        target = OUT / (folder.name + '.json')
        target.write_text(json.dumps(profile, ensure_ascii=False, separators=(',', ':')) + '\n', 'utf-8')
        row = {'version': folder.name, 'officialServer': meta['downloads']['server'],
               'tableSha256': hashlib.sha256(archive.read(TABLE)).hexdigest(),
               'profileSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
               'enchantmentOptions': len(options), 'selectionDifferences': differences}
        templates = pools = 0
        structure_differences = []
        for name in baseline.namelist():
            if name.startswith('data/minecraft/structure/ancient_city/') and name.endswith('.nbt'):
                before, after = structure(decode(baseline.read(name))), structure(decode(archive.read(name)))
                if before != after:
                    structure_differences.append(name)
                templates += 1
            if name.startswith('data/minecraft/worldgen/template_pool/ancient_city/') and name.endswith('.json'):
                if load(baseline, name) != load(archive, name):
                    structure_differences.append(name)
                pools += 1
        row.update(unchangedTemplates=templates, unchangedPools=pools, structureDifferences=structure_differences)
        assert not structure_differences, (folder.name, structure_differences)
        assert templates == 58 and pools == 7
        report['versions'].append(row)
        print(folder.name, len(options), 'selection differences:', differences)
        report['passed'] &= not differences
out = ROOT / 'build/reports/loot-version-data.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, indent=2), 'utf-8')
if not report['passed']:
    raise SystemExit('Version-specific enchantment selection needs adaptation')
