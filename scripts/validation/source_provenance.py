#!/usr/bin/env python3
"""Snapshot evidence of current source; hashes do NOT prove it produced the AAR."""
from pathlib import Path
import hashlib,json,subprocess,zipfile,tempfile,argparse
p=argparse.ArgumentParser();p.add_argument('--go',required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];out=r/'docs/remaining-1.1.1';out.mkdir(exist_ok=True)
h=lambda b:hashlib.sha256(b).hexdigest()
paths=list((r/'native/Psiphon').rglob('*'))+[r/'go.mod',r/'go.sum']
files=[{'path':str(f.relative_to(r)),'size':f.stat().st_size,'sha256':h(f.read_bytes())} for f in sorted(paths) if f.is_file()]
aars=list(r.rglob('ca.psiphon.aar'));assert len(aars)==1
f=aars[0];evidence={'warning':'Current tree snapshot, NOT an exact producer attestation','aar':{'path':str(f.relative_to(r)),'size':f.stat().st_size,'sha256':h(f.read_bytes())},'currentFiles':files,'embeddedBuildInfo':{}}
with tempfile.TemporaryDirectory() as d,zipfile.ZipFile(f) as z:
 for name in z.namelist():
  if name.endswith('.so'):
   lib=Path(d)/Path(name).name;lib.write_bytes(z.read(name))
   evidence['embeddedBuildInfo'][name]={'sha256':h(z.read(name)),'goVersionM':subprocess.check_output([a.go,'version','-m',str(lib)],text=True).replace(str(lib),'LIBRARY')}
(out/'CURRENT_SOURCE_AND_AAR.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
print('Recorded',len(files),'current source files and',len(evidence['embeddedBuildInfo']),'native build-info records; no binary replaced.')
