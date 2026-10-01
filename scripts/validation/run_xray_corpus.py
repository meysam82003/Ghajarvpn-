#!/usr/bin/env python3
"""Run official `xray run -test` against unchanged Ghajar-generated configs; never builds an APK."""
import argparse,concurrent.futures,json,os,pathlib,subprocess,hashlib
p=argparse.ArgumentParser();p.add_argument('--corpus',type=pathlib.Path,required=True);p.add_argument('--cores',type=pathlib.Path,required=True);p.add_argument('--assets',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--host-without-tun',action='store_true');p.add_argument('--candidate-schema',action='store_true');a=p.parse_args()
manifest=json.loads((a.corpus/'manifest.json').read_text())
versions=['26.3.27','26.7.28','26.9.30']
def validate(pair):
 version,case=pair
 if not case['generated']:return dict(version=version,id=case['id'],status='APP_REJECTED',detail=case['error'])
 f=a.corpus/(case['id']+'.json')
 if a.host_without_tun or a.candidate_schema:
  config=json.loads(f.read_text())
  if a.candidate_schema:
   from xray_candidate_schema import migrate
   config=migrate(config,version)
  if a.host_without_tun:config['inbounds']=[i for i in config['inbounds'] if i['protocol']!='tun']
  host=a.corpus/('candidate-schema' if a.candidate_schema else 'host-without-tun')/version;host.mkdir(parents=True,exist_ok=True);f=host/f.name;f.write_text(json.dumps(config))
 try:
  r=subprocess.run([str(a.cores/version/'xray'),'run','-test','-config',str(f)],env=dict(os.environ,XRAY_LOCATION_ASSET=str(a.assets)),capture_output=True,text=True,timeout=20)
  return dict(version=version,id=case['id'],status='PASS' if r.returncode==0 else 'FAIL',returncode=r.returncode,sha256=hashlib.sha256(f.read_bytes()).hexdigest(),detail=(r.stdout+r.stderr).strip())
 except subprocess.TimeoutExpired:return dict(version=version,id=case['id'],status='TIMEOUT')
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: rows=list(pool.map(validate,[(v,c) for v in versions for c in manifest['cases']]))
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(command='xray run -test -config',assets=str(a.assets),host_without_tun=a.host_without_tun,candidate_schema=a.candidate_schema,cases=len(manifest['cases']),results=rows),indent=2))
for v in versions:
 subset=[r for r in rows if r['version']==v];print(v,{s:sum(r['status']==s for r in subset) for s in ['PASS','FAIL','APP_REJECTED','TIMEOUT']})
 for r in subset:
  if r['status']=='FAIL':print(' ',r['id'])
