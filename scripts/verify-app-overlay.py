#!/usr/bin/env python3
import hashlib,pathlib,sys
root=pathlib.Path(__file__).resolve().parents[1]
target=pathlib.Path(sys.argv[1]);count=0
for p in (root/'app/src').rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(root);dest=target/rel
 if p.name=='AppColors.kt' and not dest.exists():continue
 if p.name=='Ikecontroller.kt' and not dest.exists():continue
 assert dest.is_file() and p.read_bytes()==dest.read_bytes(),str(rel)
 count+=1
print('Verified exact app overlay:',count,'files')
