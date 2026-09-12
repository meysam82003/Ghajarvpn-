import pathlib,hashlib,json,difflib,sys
base=pathlib.Path(sys.argv[1]);bundle=pathlib.Path(__file__).resolve().parent;out=bundle/'overlay'
manifest={};patch=[]
for p in sorted(out.rglob('*')):
 if not p.is_file():continue
 name=str(p.relative_to(out));original=base/name
 manifest[name]=hashlib.sha256(original.read_bytes()).hexdigest() if original.exists() else None
 patch+=list(difflib.unified_diff(original.read_text().splitlines(True) if original.exists() else [],p.read_text().splitlines(True),fromfile='a/'+name if original.exists() else '/dev/null',tofile='b/'+name))
(bundle/'expected-source-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n');(bundle/'compatibility.patch').write_text(''.join(patch))
