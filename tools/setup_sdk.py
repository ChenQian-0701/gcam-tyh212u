#!/usr/bin/env python3
"""Download official, checksum-pinned SDK packages into a user cache.

No GCam APK, account credential, signing key or tool binary enters the repo.
The SDK's own licenses remain applicable; see docs/build.md.
"""
import argparse, hashlib, json, os, urllib.request, zipfile
from pathlib import Path

PACKAGES = [
    ('platform-30_r03.zip', 'e7c6280901dcfa511af098d67dd88c4dfcbc6ea2', 'platforms/android-30'),
    ('build-tools_r35_linux.zip', '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc', 'build-tools/35.0.0'),
]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=Path.home()/'.cache/gcam-unisoc-research/android-sdk')
    args = parser.parse_args(); args.sdk.mkdir(parents=True, exist_ok=True)
    archives = args.sdk/'downloads'; archives.mkdir(exist_ok=True)
    manifest = []
    for filename, expected, destination in PACKAGES:
        archive = archives/filename
        if not archive.exists():
            print('Downloading', filename, flush=True)
            tmp = archive.with_suffix('.part')
            urllib.request.urlretrieve('https://dl.google.com/android/repository/'+filename, tmp)
            tmp.replace(archive)
        content = archive.read_bytes()
        if hashlib.sha1(content).hexdigest() != expected:
            raise SystemExit('Official SDK archive checksum mismatch: '+filename)
        target = args.sdk/destination; target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as z:
            roots = {n.split('/')[0] for n in z.namelist() if '/' in n}
            if len(roots) != 1: raise SystemExit('Unexpected archive root')
            prefix = next(iter(roots))+'/'
            for info in z.infolist():
                if not info.filename.startswith(prefix): continue
                relative = Path(info.filename[len(prefix):])
                if not relative.parts or info.is_dir(): continue
                if relative.is_absolute() or '..' in relative.parts: raise SystemExit('Unsafe archive member')
                output = target/relative; output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(z.read(info))
                mode = info.external_attr >> 16
                if mode & 0o111: output.chmod(0o755)
        for executable in ['aapt2','d8','apksigner','zipalign']:
            if (target/executable).is_file(): (target/executable).chmod(0o755)
        manifest.append({'archive':filename,'official_sha1':expected,'sha256':hashlib.sha256(content).hexdigest()})
    (args.sdk/'download-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('SDK ready:', args.sdk)

if __name__ == '__main__': main()
