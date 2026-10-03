#!/usr/bin/env python3
"""Fail on missing engines, duplicate ZIP entries, wrong ELF ABI or unsafe page alignment."""
import argparse, collections, json, os, re, subprocess, tempfile, zipfile
from pathlib import Path
REQUIRED=set('libsingbox.so libtor.so libgojni.so libzeptun.so libzeptun-jni.so libdnstt.so libvaydns.so libnoizdns.so libmasterdns.so libstormdns.so libcottendns.so libslipstream.so libghajarhelper.so liblyrebird.so libaether.so libjuicity.so libndpiclassify.so'.split())
SYSTEM=set('libc.so libm.so libdl.so liblog.so libandroid.so libz.so libjnigraphics.so libOpenSLES.so libEGL.so libGLESv2.so libvulkan.so libmediandk.so'.split())
def audit(apk, full=True):
    failures=[];libraries=[]
    with zipfile.ZipFile(apk) as z, tempfile.TemporaryDirectory() as temp:
        duplicates=[n for n,count in collections.Counter(z.namelist()).items() if count>1]
        if duplicates:failures.append('Duplicate ZIP entries: '+str(duplicates))
        native=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
        abis={n.split('/')[1] for n in native}
        if len(abis)!=1:failures.append('Expected one ABI: '+str(abis))
        for abi in abis:
            packaged={Path(n).name for n in native if n.split('/')[1]==abi}
            if full and abi in ('arm64-v8a','armeabi-v7a') and REQUIRED-packaged:failures.append('Missing engines: '+str(sorted(REQUIRED-packaged)))
        for n in native:
            f=Path(temp)/Path(n).name;f.write_bytes(z.read(n))
            elf=subprocess.check_output(['readelf','-h','-lW','-dW',str(f)],text=True)
            abi=n.split('/')[1];expected={'arm64-v8a':'AArch64','armeabi-v7a':'ARM','x86_64':'Advanced Micro Devices X86-64'}[abi]
            machine=re.search(r'Machine:\s*(.+)',elf).group(1).strip()
            alignment=[int(x,16) for x in re.findall(r'^\s*LOAD\s+.*?\s+(0x[0-9a-f]+)\s*$',elf,re.M)]
            needed=re.findall(r'Shared library: \[(.*?)\]',elf)
            packaged={Path(x).name for x in native if x.split('/')[1]==abi}
            unresolved=set(needed)-SYSTEM-packaged
            if machine!=expected:failures.append(n+': ELF architecture '+machine)
            if not alignment or min(alignment)<16384:failures.append(n+': insufficient 16KiB alignment')
            if unresolved:failures.append(n+': unresolved dependencies '+str(sorted(unresolved)))
            libraries.append(dict(path=n,machine=machine,alignment=alignment,needed=needed))
        if 'AndroidManifest.xml' not in z.namelist() or 'resources.arsc' not in z.namelist():failures.append('Missing packaged manifest/resources')
    return dict(apk=str(apk),libraries=libraries,failures=failures)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('apk',nargs='+');p.add_argument('--partial',action='store_true');p.add_argument('--report',default='apk-audit.json');a=p.parse_args()
    reports=[audit(Path(x),not a.partial) for x in a.apk];Path(a.report).write_text(json.dumps(reports,indent=2))
    for r in reports:print(r['apk'],len(r['libraries']),'ELFs',r['failures'])
    raise SystemExit(1 if any(r['failures'] for r in reports) else 0)
