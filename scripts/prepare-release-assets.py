#!/usr/bin/env python3
"""Validate same-run platform artifacts and collect the files attached to a release."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
artifacts, output = map(Path, sys.argv[1:])
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['collageVersion']
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', version):
    raise SystemExit('Invalid collageVersion')
if os.environ['RELEASE_TAG'] != f'v{version}':
    raise SystemExit('Release tag must match collageVersion')
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
if os.environ['EXPECTED_COMMIT'] != commit:
    raise SystemExit('Checkout must match the triggering commit')
packages = {
    'android': f'gorberry-collage-{version}.aar',
    'ios': f'GorberryCollage-{version}-release.xcframework.zip',
    'web': f'wildberries-gorberry-collage-{version}.tgz',
}
validated = []
for platform, filename in packages.items():
    folder = artifacts / f'gorberry-collage-{platform}-{commit}'
    info = dict(line.split('=', 1) for line in (folder / 'build-info.txt').read_text().splitlines())
    if info != {'version': version, 'commit': commit, 'working_tree': 'clean'}:
        raise SystemExit(f'{platform}: expected a clean build of {version} at {commit}')
    digest, checksum_name = (folder / 'SHA256SUMS').read_text().strip().split(maxsplit=1)
    archive = folder / filename
    if checksum_name != filename or not archive.is_file() or archive.stat().st_size == 0:
        raise SystemExit(f'{platform}: missing or unexpected package')
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != actual:
        raise SystemExit(f'{platform}: checksum mismatch')
    validated.append((archive, actual))

# Don't prepare a partial release when one platform failed validation.
if output.exists() and any(output.iterdir()):
    raise SystemExit('Release output directory must be empty')
output.mkdir(parents=True, exist_ok=True)
for archive, _ in validated:
    shutil.copy2(archive, output / archive.name)
(output / 'SHA256SUMS').write_text(''.join(f'{digest}  {archive.name}\n' for archive, digest in validated))
(output / 'build-info.txt').write_text(f'version={version}\ncommit={commit}\nworking_tree=clean\n')
shutil.copy2(root / 'docs/artifacts.md', output / 'INTEGRATION.md')
shutil.copy2(root / 'LICENSE', output / 'LICENSE')
print(f'Validated all three packages for v{version} ({commit})')
