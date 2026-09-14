#!/usr/bin/env python3
"""Keep the reconstructed engine dependencies while applying the release metadata."""
import pathlib,re,sys
root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/build.gradle.kts').read_text()
p=pathlib.Path(sys.argv[1])/'app/build.gradle.kts';text=p.read_text()
for key in ('versionCode','versionName'):
 value=re.search(r'(?m)^\s*'+key+r'\s*=\s*(.+)$',source).group(1)
 text,count=re.subn(r'(?m)^(\s*'+key+r'\s*=).*$',lambda m:m.group(1)+' '+value,text)
 assert count==1,key
if 'org.json:json:' not in text:text=text.replace('    testImplementation(libs.junit)','    testImplementation("org.json:json:20240303")\n    testImplementation(libs.junit)')
p.write_text(text)
