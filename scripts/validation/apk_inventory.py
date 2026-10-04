#!/usr/bin/env python3
"""Read existing APK/AAR ZIPs; no build. Compression bytes are measured, not attributed guesses."""
import argparse, collections, hashlib, json, pathlib, zipfile
p=argparse.ArgumentParser();p.add_argument('files',nargs='+',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
def category(name):
 if name.startswith(('lib/','jni/')): return 'native'
 if name.endswith('.dex'): return 'dex'
 if any(s in name.lower() for s in ['geoip','geosite']): return 'geodata'
 if name.endswith(('.ttf','.otf','.woff','.woff2')): return 'fonts'
 if name.endswith(('.png','.jpg','.webp','.jpeg')): return 'images'
 if name.startswith('assets/'): return 'other_assets'
 if name.startswith('res/') or name=='resources.arsc': return 'resources'
 return 'other'
def inspect(path):
 groups=collections.defaultdict(lambda:dict(raw=0,compressed=0,entries=0)); abis=set(); rows=[]; hashes=collections.defaultdict(list)
 with zipfile.ZipFile(path) as z:
  for entry in z.infolist():
   if entry.is_dir():continue
   group=groups[category(entry.filename)];group['raw']+=entry.file_size;group['compressed']+=entry.compress_size;group['entries']+=1
   if entry.filename.startswith(('lib/','jni/')):abis.add(entry.filename.split('/')[1])
   digest=hashlib.sha256(z.read(entry)).hexdigest()
   if entry.file_size>65536:hashes[digest].append(entry.filename)
   rows.append(dict(path=entry.filename,raw=entry.file_size,compressed=entry.compress_size,sha256=digest))
 return dict(file=path.name,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),abis=sorted(abis),groups=groups,zip_and_signing_overhead=path.stat().st_size-sum(r['compressed'] for r in rows),largest=sorted(rows,key=lambda r:r['compressed'],reverse=True)[:40],native=[r for r in rows if r['path'].startswith(('lib/','jni/'))],duplicate_contents=[v for v in hashes.values() if len(v)>1])
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps([inspect(p) for p in a.files],indent=2))
