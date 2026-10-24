"""Integration-test every pinned Mojang runtime through the actual packaged worker."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, time

parser=argparse.ArgumentParser()
parser.add_argument("--cache",type=Path)
parser.add_argument("--baseline-report",type=Path)
args=parser.parse_args()

root=Path(__file__).resolve().parent.parent
lock=json.loads((root/'src/main/resources/tanghulu/mushroom/releases.json').read_text())
cache=args.cache or root/'mushroom-qa'
baseline=json.loads(args.baseline_report.read_text())['results'] if args.baseline_report else None
records=[]
worker=root/'build/mushroom/worker.jar'
for version in lock:
    for seed,x,z,radius,step in [(0,0,0,2048,32),(-1,-10000,12000,2048,64),(-7346913998703726680,16000,-16000,4096,64),
                                  (0,0,0,64,4),(0,-640,-960,64,4),(0,29999984,-29999984,64,32)]:
        start=time.monotonic()
        args=['java','-Xmx1G','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-cp',str(worker)+os.pathsep+str(cache/f'{version}-runtime'/'*'),
              'dev.tanghulu.mushroom.BiomeWorker',str(cache/f'{version}-mappings.txt'),
              str(seed),str(x),str(z),str(radius),str(step),'all']
        result=subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
        assert result.returncode==0,(version,result.stdout,result.stderr)
        lines=result.stdout.splitlines()
        terminal=[line for line in lines if line.startswith('DONE ')]
        targets=[list(map(int,line.split()[1:])) for line in lines if line.startswith('TARGET ')]
        assert len(terminal)==1 and int(terminal[0].split()[1])==len(targets),(version,result.stdout)
        assert len(set(map(tuple,targets)))==len(targets),'duplicate target'
        for px,py,pz in targets:
            assert (px-x)**2+(pz-z)**2<=radius**2 and py==64
            assert abs(px)<=29999984 and abs(pz)<=29999984
        if x==0 and z==0: assert bool(targets)==(radius>64)
        if x==-640: assert len(targets)==1
        row=dict(version=version,seed=seed,center=[x,z],radius=radius,step=step,targets=targets,seconds=round(time.monotonic()-start,2))
        if baseline is not None:
            old=baseline[len(records)]
            assert all(row[k]==old[k] for k in row if k!='seconds'), ('Baseline mismatch',row,old)
        records.append(row)
    print('Passed',version,flush=True)
output=dict(passed=True,workerSha256=hashlib.sha256(worker.read_bytes()).hexdigest(),checks=len(records),baselineMatched=baseline is not None,results=records)
(root/'validation/mushroom-releases.json').write_text(json.dumps(output,indent=2)+'\n')
print('Passed all',len(records),'release integration checks')
