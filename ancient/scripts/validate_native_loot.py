"""Check current loot predictions in unmodified native Mojang 26.2/26.3 runtimes."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('version', choices=('26.2', '26.3'))
args = parser.parse_args()
version = args.version
folder = ROOT / '.downloads/loot-versions' / version
with zipfile.ZipFile(folder / 'server.jar') as archive:
    for name in archive.namelist():
        if name.endswith('.jar'):
            target = folder / name
            if not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
classpath = os.pathsep.join(str(p) for p in sorted((folder / 'META-INF').rglob('*.jar')))
original = (ROOT / 'scripts/qa26/Vanilla26Oracle.java').read_text('utf-8')
source = original.replace('Vanilla26Oracle', 'NativeLootOracle').replace('oracle-26.1', 'native-loot-' + version)
start = source.index('            var world=server.overworld();')
end = source.index('            var ops=RegistryOps.create', start)
source = source[:start] + '''            var world=server.overworld();
            var table=server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace("chests/ancient_city")));
            var params=new LootParams.Builder(world).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).withLuck(0).create(LootContextParamSets.CHEST);
            var fixtures=JsonParser.parseString(Files.readString(project.resolve("build/reports/native-fixtures-VERSION.json"))).getAsJsonArray();
'''.replace('VERSION', version) + source[end:]
start = source.index('                    var encoded=ItemStack.CODEC')
end = source.index('                if(apples>0)appleChests++;', start)
source = source[:start] + '''                    signature.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
                }
                Map<String,Integer> predicted=new TreeMap<>(); expected.getAsJsonObject("counts").entrySet().forEach(e -> predicted.put(e.getKey(),e.getValue().getAsInt()));
                if(!signature.equals(predicted))errors.add("seed="+seed+" actual="+signature+" expected="+predicted);
''' + source[end:]
start = source.index('            var sample=new SimpleContainer')
end = source.index('        } finally', start)
source = source[:start] + '''            var report=new LinkedHashMap<String,Object>();
            report.put("passed",errors.isEmpty() && checked==1024); report.put("minecraft","VERSION");
            report.put("method","Unmodified official Mojang server LootTable.fill, all item counts and apple counts for 1024 seeds; no network listener; component encoding not compared across versions.");
            report.put("checked",checked); report.put("appleChests",appleChests); report.put("errors",errors);
            Files.writeString(project.resolve("build/reports/native-loot-VERSION.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("NATIVE_LOOT_RESULT "+report);
            if(!errors.isEmpty())throw new AssertionError("Native loot oracle failed: "+errors.size());
'''.replace('VERSION', version) + source[end:]
source = source.replace('},false);', '},false,new net.minecraft.server.notifications.NotificationManager());')
source = source.replace('        @Override public int getRateLimitPacketsPerSecond()',
                        '        @Override public int getCommandSpamThresholdSeconds() { return 20; }\n'
                        '        @Override public int getChatSpamThresholdSeconds() { return 20; }\n'
                        '        @Override public int getRateLimitPacketsPerSecond()')
if version == '26.3':
    source = source.replace('import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;',
                            'import com.mojang.authlib.services.MinecraftServicesDiscoveryService;')
    source = source.replace('new YggdrasilAuthenticationService(Proxy.NO_PROXY)',
                            'MinecraftServicesDiscoveryService.createOffline(Proxy.NO_PROXY)')
    source = source.replace('c.datapackWorldgen()', 'c.datapackWorldRegistries()')
out = ROOT / 'build/native-qa' / version
out.mkdir(parents=True, exist_ok=True)
java_source = out / 'NativeLootOracle.java'
java_source.write_text(source, 'utf-8')
subprocess.run(['javac', '-J-Duser.language=en', '-J-Dfile.encoding=UTF-8', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(out), str(java_source)], check=True)
work = ROOT / 'build' / ('native-loot-' + version)
work.mkdir(exist_ok=True)
subprocess.run(['java', '-Xmx2G', '-cp', str(out) + os.pathsep + classpath, 'NativeLootOracle', str(ROOT)], cwd=work, check=True)
