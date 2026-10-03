#!/usr/bin/env python3
"""Prepare an offline corresponding-source archive; no APK/build/sign/publish.
The caller supplies a clean exact upstream checkout plus complete dependency source
archives (cargo vendor or Go module ZIPs, including licenses). Fails if missing.
"""
import argparse,hashlib,io,json,subprocess,tarfile
from pathlib import Path
PINS={'mihomo':'3189346611caeba73aa87feaf708e4fd65115d16','shadowquic':'5540e3a32ca73c85af125723e4262e02cf28ebcd'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--id',choices=PINS,required=True)
 for k in ('upstream','dependency-sources','out'):p.add_argument('--'+k,type=Path,required=True)
 a=p.parse_args();r=Path(__file__).resolve().parents[1]
 git=lambda *args:subprocess.check_output(['git','-C',str(a.upstream),*args])
 if git('rev-parse','HEAD').decode().strip()!=PINS[a.id] or git('status','--porcelain').strip():raise ValueError('Clean pinned upstream checkout required')
 if any(line.startswith(b'160000') for line in git('ls-files','--stage').splitlines()):raise ValueError('Gitlink source closure must be supplied and reviewed explicitly')
 # A reviewer attests this is the complete dependency closure, never infer it from lockfile presence.
 manifest=a.dependency_sources/'SOURCES.json'
 deps=json.loads(manifest.read_text())
 if not deps or not isinstance(deps,list):raise ValueError('Dependency source inventory required')
 for d in deps:
  name=d['archive'];path=a.dependency_sources/name
  if Path(name).is_absolute() or '..' in Path(name).parts or not d.get('license') or not d.get('module') or not d.get('version'):raise ValueError('Dependency identity/license required')
  if hashlib.sha256(path.read_bytes()).hexdigest()!=d['sha256']:raise ValueError('Dependency archive digest mismatch')
 payload=git('archive','--format=tar','HEAD');files={}
 prefixes=['plugin-api',f'plugins/{a.id}',f'native/plugin-{a.id}','gradle','LICENSE','gradlew','gradlew.bat','settings.gradle.kts','build.gradle.kts','gradle.properties',f'scripts/prepare-{a.id}-plugin.sh','scripts/plugin-source-bundle.py','scripts/plugin-release-manifest.py']
 tracked=subprocess.check_output(['git','ls-files','--',*prefixes],cwd=r,text=True).splitlines()
 for name in tracked:
  f=r/name
  if f.is_file() and 'build' not in f.parts and 'jniLibs' not in f.parts:
   files['host/'+name]=f.read_bytes()
 files['dependency-sources/SOURCES.json']=manifest.read_bytes()
 for d in deps:files['dependency-sources/'+d['archive']]=(a.dependency_sources/d['archive']).read_bytes()
 files['PROVENANCE.json']=json.dumps({'plugin':a.id,'upstreamCommit':PINS[a.id],'hostParent':subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(),'hostFiles':{n:hashlib.sha256(b).hexdigest() for n,b in files.items() if n.startswith('host/')}},sort_keys=True,indent=2).encode()
 with tarfile.open(a.out,'w') as out:
  with tarfile.open(fileobj=io.BytesIO(payload)) as upstream:
   for info in upstream:
    if not(info.isfile() or info.isdir()):raise ValueError('Review non-regular upstream archive member')
    data=upstream.extractfile(info).read() if info.isfile() else b''
    if info.isfile():files['upstream/'+info.name]=data
  for name,data in sorted(files.items()):
   info=tarfile.TarInfo(name);info.size=len(data);info.mode=0o644;info.mtime=0;out.addfile(info,io.BytesIO(data))
 print('Source archive prepared; dependency completeness still requires reviewer comparison with the lockfiles.')
if __name__=='__main__':main()
