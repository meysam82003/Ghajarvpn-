#!/usr/bin/env python3
"""Archive the exact reconstructed Android tree, native engines and full backend."""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import zipfile

repo = Path(__file__).resolve().parents[1]
android = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
out.mkdir(parents=True, exist_ok=True)
psiphon = Path(sys.argv[3]).resolve()
aether = Path(sys.argv[4]).resolve()
assert (android / 'gradlew').is_file()
assert (android / 'app/libs/ca.psiphon.aar').is_file()
assert (psiphon / 'MobileLibrary/psi').is_dir()
assert (aether / 'Cargo.toml').is_file() or (aether / 'go.mod').is_file()

parts = repo / 'server/faoxima-1.0.0/full-source'
backend = base64.b64decode(''.join(p.read_text() for p in sorted(parts.glob('*.part-*'))), validate=True)
assert hashlib.sha256(backend).hexdigest() == (parts / 'SHA256').read_text().strip()
(out / 'Faoxima-1.0.0-complete-source.zip').write_bytes(backend)

prefix = 'Ghajarvpn-1.0.0'
manifest = []
excluded_dirs = {'.git', '.gradle', '.cxx', '.kotlin', '__pycache__', '.idea'}

def add_tree(tar, root, target, release_tools=False):
    # Module build outputs and machine-specific SDK settings are not source.
    builds = {root / 'build'} | {p.parent / 'build' for p in root.rglob('build.gradle.kts')}
    for current, dirs, files in os.walk(root):
        base = Path(current)
        dirs[:] = sorted(d for d in dirs if d not in excluded_dirs and base / d not in builds and base / d != out
                         and not (release_tools and (d == '.ghajarvpn-src' or base / d == parts)))
        for filename in sorted(files):
            p = base / filename
            if filename == 'local.properties' or p.suffix in {'.jks', '.keystore', '.p12'}:
                continue
            name = f'{prefix}/{target}/{p.relative_to(root).as_posix()}'
            tar.add(p, arcname=name, recursive=False)
            if p.is_file(): manifest.append({'path': name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()})

archive = out / 'Ghajarvpn-1.0.0-complete-source.tar.gz'
with tarfile.open(archive, 'w:gz', compresslevel=6) as tar:
    add_tree(tar, android, 'Android')
    add_tree(tar, repo, 'ReleaseTools', True)
    add_tree(tar, psiphon, 'NativeSources/Psiphon')
    add_tree(tar, aether, 'NativeSources/Aether')
    with zipfile.ZipFile(io.BytesIO(backend)) as z:
        for member in z.infolist():
            if member.is_dir(): continue
            path = Path(member.filename)
            assert not path.is_absolute() and '..' not in path.parts
            data = z.read(member)
            name = f'{prefix}/Backend/{member.filename}'
            info = tarfile.TarInfo(name); info.size = len(data); info.mode = 0o644
            tar.addfile(info, io.BytesIO(data))
            manifest.append({'path': name, 'sha256': hashlib.sha256(data).hexdigest()})
    metadata = json.dumps({'commit': subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip(),
        'files': manifest}, indent=2).encode()
    info = tarfile.TarInfo(f'{prefix}/SOURCE-MANIFEST.json'); info.size = len(metadata)
    tar.addfile(info, io.BytesIO(metadata))
    readme = (repo / 'docs/COMPLETE-SOURCE-1.0.0.md').read_bytes()
    info = tarfile.TarInfo(f'{prefix}/README.md'); info.size = len(readme)
    tar.addfile(info, io.BytesIO(readme))
with tarfile.open(archive) as tar:
    names = set(tar.getnames())
    for name in ['Android/gradlew', 'Android/app/src/main/AndroidManifest.xml', 'Android/app/libs/ca.psiphon.aar',
                 'Android/gozarcore.go', 'Backend/Faoxima-1.0.0/api/miniapp.php']:
        assert f'{prefix}/{name}' in names, name
print(f'Archived {len(manifest)} files: {archive.name} ({archive.stat().st_size} bytes)')
