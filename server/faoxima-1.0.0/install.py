#!/usr/bin/env python3
"""Apply only to the supplied Faoxima 1.0.0 base; preserve local settings and backups."""
import argparse,datetime,hashlib,json,pathlib,shutil,subprocess
p=argparse.ArgumentParser();p.add_argument('root',type=pathlib.Path);p.add_argument('--apply',action='store_true');args=p.parse_args()
bundle=pathlib.Path(__file__).resolve().parent
expected=json.loads((bundle/'expected-source-sha256.json').read_text())
changes=[]
for name,want in expected.items():
 dst=args.root/name;src=bundle/'overlay'/name
 if dst.exists() and dst.read_bytes()==src.read_bytes():continue
 if want is None:
  if dst.exists():raise SystemExit('Existing custom file differs; review manually: '+name)
 elif not dst.exists() or hashlib.sha256(dst.read_bytes()).hexdigest()!=want:
  raise SystemExit('Source differs from supplied Faoxima 1.0.0; review manually: '+name)
 changes.append(name)
if shutil.which('php'):
 for name in changes:
  if name.endswith('.php'):subprocess.run(['php','-l',str(bundle/'overlay'/name)],check=True)
print('Verified files:', ', '.join(changes) or 'already installed')
if not args.apply:raise SystemExit('Dry run only. Add --apply to install after reviewing.')
backup=args.root/('ghajar-backup-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
for name in changes:
 dst=args.root/name
 if dst.exists():
  saved=backup/name;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(dst,saved)
 dst.parent.mkdir(parents=True,exist_ok=True);tmp=dst.with_name(dst.name+'.ghajar-new')
 shutil.copy2(bundle/'overlay'/name,tmp);tmp.replace(dst)
print('Installed; backups:',backup)
