#!/usr/bin/env python3
"""Sign validated, already-optimized APKs with the production release key."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def validate_badging(text, version, code):
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text)
    if not package or package.groups() != ("com.ghajarvpn.app", str(code), version):
        raise ValueError("APK package/version does not match the release sources")
    if "application-debuggable" in text:
        raise ValueError("A debuggable APK cannot be promoted to a release")


def main():
    version = os.environ["RELEASE_VERSION"]
    code = os.environ["RELEASE_VERSION_CODE"]
    expected_signer = os.environ["EXPECTED_SIGNER_SHA256"].lower()
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ["ANDROID_HOME"]
    tools = Path(sdk) / "build-tools" / "36.0.0"
    props = {}
    for line in Path("keystore.properties").read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            props[key] = value
    signing_env = os.environ.copy()
    signing_env["GHAJAR_SIGN_STORE_PASSWORD"] = props["storePassword"]
    signing_env["GHAJAR_SIGN_KEY_PASSWORD"] = props["keyPassword"]
    apks = {}
    for apk in Path("preview-apk").rglob("*.apk"):
        with zipfile.ZipFile(apk) as z:
            abis = {name.split("/")[1] for name in z.namelist()
                    if name.startswith("lib/") and name.endswith(".so")}
        if len(abis) != 1:
            raise ValueError("Expected one native ABI per optimized APK")
        abi = abis.pop()
        if abi not in ("arm64-v8a", "armeabi-v7a") or abi in apks:
            raise ValueError("Unexpected or duplicate release ABI")
        badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(apk)], text=True)
        validate_badging(badging, version, code)
        subprocess.run([str(tools / "apksigner"), "verify", str(apk)], check=True)
        apks[abi] = apk
    if set(apks) != {"arm64-v8a", "armeabi-v7a"}:
        raise ValueError("Both phone APK architectures are required")
    dest = Path("dist")
    dest.mkdir(exist_ok=True)
    sums = []
    for abi, apk in sorted(apks.items()):
        output = dest / f"Ghajarvpn-{version}-{abi}.apk"
        with tempfile.TemporaryDirectory() as folder:
            aligned = Path(folder) / "aligned.apk"
            subprocess.run([str(tools / "zipalign"), "-P", "16", "-f", "4", str(apk), str(aligned)], check=True)
            subprocess.run([str(tools / "apksigner"), "sign", "--ks", props["storeFile"],
                            "--ks-key-alias", props["keyAlias"],
                            "--ks-pass", "env:GHAJAR_SIGN_STORE_PASSWORD",
                            "--key-pass", "env:GHAJAR_SIGN_KEY_PASSWORD",
                            "--v4-signing-enabled", "false",
                            "--out", str(output), str(aligned)], env=signing_env, check=True)
        verified = subprocess.check_output([str(tools / "apksigner"), "verify", "--print-certs", str(output)], text=True)
        signer = re.search(r"Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)", verified)
        if not signer or signer.group(1).lower() != expected_signer:
            raise ValueError("Release signing certificate differs from the installed stable version")
        subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(output)], check=True)
        sums.append(hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name)
        print(f"Promoted {output.name}: version {version} ({code}), signer {signer.group(1)}")
    (dest / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n")


if __name__ == "__main__":
    main()
