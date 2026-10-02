#!/usr/bin/env python3
"""Validate same-run platform artifacts and collect the files attached to a release."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
from swift_package import prepare_manifest, read_version

root = Path(__file__).resolve().parent.parent
artifacts, output = map(Path, sys.argv[1:])
version = read_version(root)
if os.environ['RELEASE_TAG'] != f'v{version}':
    raise SystemExit('Release tag must match collageVersion')
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
if os.environ['EXPECTED_COMMIT'] != commit:
    raise SystemExit('Checkout must match the triggering commit')
packages = {
    'android': [f'gorberry-collage-{version}.aar', f'gorberry-collage-{version}-maven.zip'],
    'ios': [f'GorberryCollage-{version}-release.xcframework.zip'],
    'web': [f'wildberries-gorberry-collage-{version}.tgz'],
}
validated = []
for platform, filenames in packages.items():
    folder = artifacts / f'gorberry-collage-{platform}-{commit}'
    info = dict(line.split('=', 1) for line in (folder / 'build-info.txt').read_text().splitlines())
    if info != {'version': version, 'commit': commit, 'working_tree': 'clean'}:
        raise SystemExit(f'{platform}: expected a clean build of {version} at {commit}')
    entries = [line.split() for line in (folder / 'SHA256SUMS').read_text().splitlines()]
    if any(len(entry) != 2 for entry in entries):
        raise SystemExit(f'{platform}: invalid checksum file')
    checksums = {name: digest for digest, name in entries}
    if len(entries) != len(checksums) or set(checksums) != set(filenames):
        raise SystemExit(f'{platform}: missing or unexpected package checksum')
    for filename in filenames:
        archive = folder / filename
        if not archive.is_file() or archive.stat().st_size == 0:
            raise SystemExit(f'{platform}: missing or empty package: {filename}')
        actual = hashlib.sha256(archive.read_bytes()).hexdigest()
        if checksums[filename] != actual:
            raise SystemExit(f'{platform}: checksum mismatch: {filename}')
        validated.append((archive, actual))

# Don't prepare a partial release when one platform failed validation.
if output.exists() and any(output.iterdir()):
    raise SystemExit('Release output directory must be empty')
output.mkdir(parents=True, exist_ok=True)
for archive, _ in validated:
    shutil.copy2(archive, output / archive.name)
prepare_manifest(output, os.environ['GITHUB_REPOSITORY'], root)
manifest = output / 'Package.swift'
validated.append((manifest, hashlib.sha256(manifest.read_bytes()).hexdigest()))
(output / 'SHA256SUMS').write_text(''.join(f'{digest}  {archive.name}\n' for archive, digest in validated))
(output / 'build-info.txt').write_text(f'version={version}\ncommit={commit}\nworking_tree=clean\n')
shutil.copy2(root / 'docs/artifacts.md', output / 'INTEGRATION.md')
shutil.copy2(root / 'LICENSE', output / 'LICENSE')
print(f'Validated packages from all three platforms for v{version} ({commit})')
