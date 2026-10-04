#!/usr/bin/env python3
"""Offline release preparation AFTER authorized APK signing. No download, build, publish or trust enrollment.
Reads private key only from a caller-owned file; never prints private material.
"""
import argparse, base64, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path
from urllib.parse import urlparse
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import rsa, padding

PINS = {
 'shadowquic': ('https://github.com/spongebob888/shadowquic','5540e3a32ca73c85af125723e4262e02cf28ebcd','MIT',{'supportsTcp':True,'supportsUdp':True,'supportsSocks':True,'supportsConnectionTest':True}),
 'mihomo': ('https://github.com/appshubcc/Bettbox','3189346611caeba73aa87feaf708e4fd65115d16','GPL-3.0',{'supportsTcp':True,'supportsUdp':True,'supportsIPv6':True,'supportsTun':True,'supportsSocks':True,'supportsDns':True,'supportsFullConfig':True,'supportsSubscription':True}),
}
def https(value):
 u=urlparse(value)
 if u.scheme!='https' or not u.hostname or u.username or u.password or u.fragment or u.port not in (None,443): raise ValueError('HTTPS origin required')
 return value

def envelope(payload, key):
 if not isinstance(key,rsa.RSAPrivateKey) or key.key_size<3072: raise ValueError('Manifest key must be RSA >=3072')
 data=json.dumps(payload,ensure_ascii=False,separators=(',',':'),sort_keys=True).encode()
 if len(data)>65536: raise ValueError('Manifest payload limit')
 signature=key.sign(data,padding.PKCS1v15(),hashes.SHA256())
 key.public_key().verify(signature,data,padding.PKCS1v15(),hashes.SHA256())
 return {'publisher':payload['publisher'],'payload':base64.b64encode(data).decode(),'signature':base64.b64encode(signature).decode()}

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('apk','manifest-key','publisher','download-url','source-bundle','source-url','min-host-version','min-host-code','out'): p.add_argument('--'+name,required=True)
 p.add_argument('--id',choices=PINS,required=True);p.add_argument('--apksigner',default='apksigner');p.add_argument('--aapt',default='aapt')
 a=p.parse_args();apk=Path(a.apk);source=Path(a.source_bundle)
 if not apk.is_file() or not source.is_file(): raise ValueError('Actual APK and complete source archive required')
 signer=subprocess.run([a.apksigner,'verify','--verbose','--print-certs',str(apk)],check=True,capture_output=True,text=True).stdout
 certificates=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})',signer)
 if len(certificates)!=1: raise ValueError('Exactly one reviewed current signing certificate required')
 badging=subprocess.run([a.aapt,'dump','badging',str(apk)],check=True,capture_output=True,text=True).stdout
 meta=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging)
 if not meta: raise ValueError('APK version metadata unavailable')
 package,code,version=meta.groups();code=int(code)
 if code<1 or package!=f'net.ghajar.plugin.{a.id}.v{code}' or not version: raise ValueError('Versioned APK identity mismatch')
 with zipfile.ZipFile(apk) as z:
  name='libshadowquic.so' if a.id=='shadowquic' else 'libmihomoghajar.so'
  abis=sorted({n.split('/')[1] for n in z.namelist() if n.startswith('lib/') and n.endswith('/'+name)})
  if set(abis)!={'arm64-v8a','armeabi-v7a'}: raise ValueError('Both reviewed ARM native binaries required')
  if not any(n.startswith('assets/licenses/') for n in z.namelist()): raise ValueError('License assets missing')
 repo,pin,license,caps=PINS[a.id]
 if int(a.min_host_code)<1 or not re.fullmatch(r'[A-Za-z0-9_.-]{1,64}',a.publisher): raise ValueError('Host/publisher identity required')
 payload=dict(id=a.id,name=a.id,version=version,versionCode=code,apiVersion=1,minGhajarVersion=a.min_host_version,minGhajarVersionCode=int(a.min_host_code),abis=abis,capabilities=caps,sourceRepository=repo,sourceCommit=pin,sourceTag='',downloadUrl=https(a.download_url),size=apk.stat().st_size,sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),publisher=a.publisher,certificateSha256=certificates[0].lower(),license=license,packageName=package,serviceClass=package+'.EngineService',dependencies=[],sourceBundle={'url':https(a.source_url),'size':source.stat().st_size,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
 key=serialization.load_pem_private_key(Path(a.manifest_key).read_bytes(),password=os.environ.get('GHAJAR_MANIFEST_KEY_PASSWORD','').encode() or None)
 output=envelope(payload,key);Path(a.out).write_text(json.dumps(output,indent=2)+'\n')
 print('Signed manifest prepared. Review public key, APK certificate, download origin and source bundle before host trust enrollment. Nothing was published.')
if __name__=='__main__':main()
