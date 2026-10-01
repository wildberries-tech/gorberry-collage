#!/usr/bin/env python3
"""Prepare a binary Swift package and publish its immutable version tag."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
LOCAL_TARGET = 'path: "collage/build/XCFrameworks/release/GorberryCollage.xcframework"'


def read_version(root=ROOT):
    properties = dict(line.split('=', 1) for line in
                      (root / 'gradle.properties').read_text().splitlines() if '=' in line)
    version = properties['collageVersion']
    number = r'(?:0|[1-9][0-9]*)'
    identifier = rf'(?:{number}|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)'
    if not re.fullmatch(rf'{number}\.{number}\.{number}(?:-{identifier}(?:\.{identifier})*)?', version):
        raise ValueError('collageVersion must be a semantic version without build metadata')
    return version


def render_manifest(template, repository, version, digest):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Expected a GitHub owner/repository')
    if not re.fullmatch(r'[a-f0-9]{64}', digest):
        raise ValueError('Expected a SHA-256 checksum')
    if template.count(LOCAL_TARGET) != 1:
        raise ValueError('Package.swift must contain exactly one local binary target')
    filename = f'GorberryCollage-{version}-release.xcframework.zip'
    url = f'https://github.com/{repository}/releases/download/v{version}/{filename}'
    return template.replace(LOCAL_TARGET, f'url: "{url}",\n            checksum: "{digest}"')


def prepare_manifest(assets, repository, root=ROOT):
    version = read_version(root)
    archive = assets / f'GorberryCollage-{version}-release.xcframework.zip'
    if not archive.is_file() or archive.stat().st_size == 0:
        raise ValueError(f'Missing release XCFramework: {archive}')
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    manifest = render_manifest((root / 'Package.swift').read_text(), repository, version, digest)
    (assets / 'Package.swift').write_text(manifest, encoding='utf-8', newline='\n')
    return manifest


def git(root, *args, env=None):
    return subprocess.check_output(['git', *args], cwd=root, env=env, text=True).strip()


def publish_tag(assets, repository, source_commit, root=ROOT):
    """Add only the generated manifest to a child commit; never move an existing tag."""
    version = read_version(root)
    tag = f'v{version}'
    if git(root, 'rev-parse', 'HEAD') != source_commit:
        raise ValueError('Checkout must match the source commit used to build the packages')
    manifest_path = assets / 'Package.swift'
    manifest = manifest_path.read_text()
    archive = assets / f'GorberryCollage-{version}-release.xcframework.zip'
    expected = render_manifest((root / 'Package.swift').read_text(), repository, version,
                               hashlib.sha256(archive.read_bytes()).hexdigest())
    if manifest != expected:
        raise ValueError('Swift manifest does not match the release archive')

    # A separate index keeps the checkout, branch and caller's staging area untouched.
    with tempfile.TemporaryDirectory(prefix='gorberry-spm-index-') as temporary:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(temporary) / 'index'))
        git(root, 'read-tree', source_commit, env=env)
        blob = git(root, 'hash-object', '-w', '--no-filters', str(manifest_path.resolve()))
        git(root, 'update-index', '--add', '--cacheinfo', f'100644,{blob},Package.swift', env=env)
        tree = git(root, 'write-tree', env=env)
        # Stable identity/date make a retry produce exactly the same release commit.
        date = git(root, 'show', '-s', '--format=%cI', source_commit)
        env.update(GIT_AUTHOR_NAME='github-actions[bot]',
                   GIT_AUTHOR_EMAIL='41898282+github-actions[bot]@users.noreply.github.com',
                   GIT_COMMITTER_NAME='github-actions[bot]',
                   GIT_COMMITTER_EMAIL='41898282+github-actions[bot]@users.noreply.github.com',
                   GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
        commit = git(root, 'commit-tree', tree, '-p', source_commit, '-m',
                     f'Prepare Swift package {tag}', env=env)

    ref = f'refs/tags/{tag}'
    remote = git(root, 'ls-remote', '--tags', 'origin', ref, f'{ref}^{{}}')
    if remote:
        refs = dict((name, sha) for sha, name in (line.split() for line in remote.splitlines()))
        if refs.get(f'{ref}^{{}}', refs.get(ref)) != commit:
            raise ValueError(f'{tag} already exists with different contents; use a new collageVersion')
        print(f'{tag} already contains this Swift package; reusing it')
    else:
        subprocess.run(['git', 'push', 'origin', f'{commit}:{ref}'], cwd=root, check=True)
        print(f'Published {tag}: {commit} (library source: {source_commit})')
    return commit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'tag'])
    parser.add_argument('assets', type=Path)
    parser.add_argument('--repository', default=os.environ.get('GITHUB_REPOSITORY'))
    parser.add_argument('--source-commit', default=os.environ.get('EXPECTED_COMMIT'))
    args = parser.parse_args()
    if not args.repository:
        parser.error('--repository or GITHUB_REPOSITORY is required')
    if args.command == 'prepare':
        prepare_manifest(args.assets, args.repository)
        print(f'Prepared {args.assets / "Package.swift"}')
    else:
        if not args.source_commit:
            parser.error('--source-commit or EXPECTED_COMMIT is required')
        publish_tag(args.assets, args.repository, args.source_commit)


if __name__ == '__main__':
    main()
