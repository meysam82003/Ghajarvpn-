#!/usr/bin/env python3
"""Install the pinned Aether build matching Oblivion's current CLI, checking release digests."""
import hashlib,io,pathlib,sys,tarfile,urllib.request
root=pathlib.Path(sys.argv[1]);version='v1.9.0'
for arch,abi,digest in [
 ('arm64','arm64-v8a','a5a488b8cf05b3e83c28ca35cef78334130411c8df5314850e816b900e9d6cb9'),
 ('armv7','armeabi-v7a','d49ee19423a33d905fb4fef3f163d2e3c88e5223940e0f03dbe6324bb2c7dcdb')]:
 url=f'https://github.com/CluvexStudio/Aether/releases/download/{version}/aether-android-{arch}.tar.gz'
 with urllib.request.urlopen(url,timeout=90) as response: archive=response.read()
 assert hashlib.sha256(archive).hexdigest()==digest, f'Aether {arch} digest mismatch'
 with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
  files=[m for m in tar.getmembers() if m.isfile() and pathlib.PurePosixPath(m.name).name in ('aether','libaether.so')]
  assert len(files)==1, 'Unexpected Aether archive layout'
  data=tar.extractfile(files[0]).read()
 assert data.startswith(b'\x7fELF'), 'Not an ELF executable'
 for flag in (b'--wiw-outer',b'--wiw-inner',b'--ip',b'--http-proxy'):
  assert flag in data, f'Aether {arch} lacks {flag!r}'
 target=root/'app/src/main/jniLibs'/abi/'libaether.so';target.parent.mkdir(parents=True,exist_ok=True)
 target.write_bytes(data);target.chmod(0o755)
 print(f'Installed Aether {version} for {abi}: {hashlib.sha256(data).hexdigest()}')
