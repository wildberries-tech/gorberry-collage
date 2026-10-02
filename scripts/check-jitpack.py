#!/usr/bin/env python3
"""Check JitPack publication locally, without contacting or publishing to JitPack."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

from swift_package import read_version


def main():
    root = Path(__file__).resolve().parent.parent
    tag = f'v{read_version(root)}'
    group, artifact = 'com.github.wildberries-tech', 'gorberry-collage'
    temporary_root = root / 'build/tmp'
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='jitpack-check-', dir=temporary_root) as directory:
        repository = Path(directory) / 'repository'
        env = dict(os.environ, JITPACK='true', GROUP=group, ARTIFACT=artifact, VERSION=tag)
        wrapper = [str(root / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(root / 'gradlew')]
        subprocess.run(wrapper + [':collage:publishAndroidPublicationToMavenLocal',
                                 f'-Dmaven.repo.local={repository}', '--stacktrace'],
                       cwd=root, env=env, check=True)
        # JitPack must discover exactly one Android publication, with no sample apps
        # or partially published KMP umbrella pointing to missing iOS/JS artifacts.
        expected = repository / group.replace('.', '/') / artifact / tag / f'{artifact}-{tag}.pom'
        if list(repository.rglob('*.pom')) != [expected]:
            raise ValueError('Expected exactly one Android publication for JitPack')
        subprocess.run([sys.executable, str(root / 'scripts/check-android-maven.py'),
                        str(repository), '--group', group, '--artifact', artifact,
                        '--version', tag], cwd=root, check=True)
    print(f'JitPack publication preflight passed: {group}:{artifact}:{tag}')


if __name__ == '__main__':
    main()
