"""Exercise release publication against a local Git remote; no GitHub credentials needed."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

from swift_package import git, prepare_manifest, publish_tag, read_version

SCRIPTS = Path(__file__).resolve().parent


class SwiftPackageReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='gorberry-spm-test-')
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.root = self.base / 'source'
        self.root.mkdir()
        self.remote = self.base / 'remote.git'
        git(self.base, 'init', '--bare', str(self.remote))
        git(self.root, 'init', '-b', 'main')
        git(self.root, 'config', 'user.name', 'Release Test')
        git(self.root, 'config', 'user.email', 'test@example.com')
        git(self.root, 'config', 'core.autocrlf', 'false')
        git(self.root, 'config', 'commit.gpgsign', 'false')
        git(self.root, 'remote', 'add', 'origin', str(self.remote))
        shutil.copyfile(SCRIPTS.parent / 'Package.swift', self.root / 'Package.swift')
        (self.root / 'gradle.properties').write_text('collageVersion=0.1.1\n')
        (self.root / 'LICENSE').write_text('Fixture license\n')
        (self.root / 'docs').mkdir()
        (self.root / 'docs/artifacts.md').write_text('Fixture integration guide\n')
        git(self.root, 'add', '.')
        git(self.root, 'commit', '-m', 'Library source')
        self.source = git(self.root, 'rev-parse', 'HEAD')
        self.assets = self.base / 'assets'
        self.assets.mkdir()
        self.archive = self.assets / 'GorberryCollage-0.1.1-release.xcframework.zip'
        with zipfile.ZipFile(self.archive, 'w') as archive:
            archive.writestr('GorberryCollage.xcframework/Info.plist', 'fixture')
        self.repository = 'wildberries-tech/gorberry-collage'
        prepare_manifest(self.assets, self.repository, self.root)

    def publish(self):
        return publish_tag(self.assets, self.repository, self.source, self.root)

    def test_manifest_uses_exact_archive_and_checksum(self):
        manifest = (self.assets / 'Package.swift').read_text()
        self.assertIn('releases/download/v0.1.1/GorberryCollage-0.1.1-release.xcframework.zip', manifest)
        self.assertIn(hashlib.sha256(self.archive.read_bytes()).hexdigest(), manifest)
        self.assertNotIn('path: "collage/', manifest)
        self.assertTrue(manifest.startswith('// swift-tools-version:5.3\n'))

    def test_tag_contains_manifest_without_changing_branch_or_index(self):
        (self.root / 'unrelated.txt').write_text('Already staged by the caller\n')
        git(self.root, 'add', 'unrelated.txt')
        index_before = git(self.root, 'write-tree')
        commit = self.publish()
        self.assertEqual(git(self.root, 'rev-parse', 'HEAD'), self.source)
        self.assertEqual(git(self.root, 'write-tree'), index_before)
        self.assertEqual(git(self.root, 'rev-parse', f'{commit}^'), self.source)
        self.assertEqual(git(self.root, 'diff-tree', '--no-commit-id', '--name-only', '-r', commit), 'Package.swift')
        self.assertEqual(git(self.remote, 'show', 'v0.1.1:Package.swift'),
                         (self.assets / 'Package.swift').read_text().strip())
        self.assertEqual(git(self.remote, 'for-each-ref', '--format=%(refname)', 'refs/heads/'), '')

    def test_retry_reuses_exact_tag(self):
        first = self.publish()
        self.assertEqual(self.publish(), first)
        self.assertEqual(git(self.remote, 'rev-parse', 'v0.1.1'), first)

    def test_rebuilt_archive_cannot_replace_published_version(self):
        first = self.publish()
        with zipfile.ZipFile(self.archive, 'a') as archive:
            archive.writestr('GorberryCollage.xcframework/changed', 'different binary')
        prepare_manifest(self.assets, self.repository, self.root)
        with self.assertRaisesRegex(ValueError, 'already exists with different contents'):
            self.publish()
        self.assertEqual(git(self.remote, 'rev-parse', 'v0.1.1'), first)

    def test_existing_source_tag_is_never_moved(self):
        git(self.root, 'push', 'origin', f'{self.source}:refs/tags/v0.1.1')
        with self.assertRaisesRegex(ValueError, 'use a new collageVersion'):
            self.publish()
        self.assertEqual(git(self.remote, 'rev-parse', 'v0.1.1'), self.source)

    def test_wrong_source_and_tampered_manifest_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Checkout must match'):
            publish_tag(self.assets, self.repository, '0' * 40, self.root)
        (self.assets / 'Package.swift').write_text('// wrong archive\n')
        with self.assertRaisesRegex(ValueError, 'does not match the release archive'):
            self.publish()
        self.assertEqual(git(self.remote, 'for-each-ref', '--format=%(refname)', 'refs/tags/'), '')

    def test_repository_and_prerelease_validation(self):
        with self.assertRaisesRegex(ValueError, 'owner/repository'):
            prepare_manifest(self.assets, 'owner/repo/extra', self.root)
        for version in ['0.1.1-beta.1', '1.0.0-rc.2', '0.0.0']:
            (self.root / 'gradle.properties').write_text(f'collageVersion={version}\n')
            self.assertEqual(read_version(self.root), version)
        for version in ['01.1.0', '1.0.0-01', '1.0.0-', '1.0.0+build', '../bad']:
            (self.root / 'gradle.properties').write_text(f'collageVersion={version}\n')
            with self.assertRaises(ValueError):
                read_version(self.root)

    def test_release_assets_include_checked_manifest(self):
        script_dir = self.root / 'scripts'
        script_dir.mkdir()
        for filename in ['prepare-release-assets.py', 'swift_package.py']:
            shutil.copyfile(SCRIPTS / filename, script_dir / filename)
        incoming = self.base / 'incoming'
        packages = {'android': ['gorberry-collage-0.1.1.aar', 'gorberry-collage-0.1.1-maven.zip'],
                    'ios': [self.archive.name],
                    'web': ['wildberries-gorberry-collage-0.1.1.tgz']}
        for platform, filenames in packages.items():
            folder = incoming / f'gorberry-collage-{platform}-{self.source}'
            folder.mkdir(parents=True)
            checksums = []
            for filename in filenames:
                content = self.archive.read_bytes() if platform == 'ios' else b'package fixture'
                (folder / filename).write_bytes(content)
                digest = hashlib.sha256(content).hexdigest()
                checksums.append(f'{digest}  {filename}\n')
            (folder / 'SHA256SUMS').write_text(''.join(checksums))
            (folder / 'build-info.txt').write_text(
                f'version=0.1.1\ncommit={self.source}\nworking_tree=clean\n')
        output = self.base / 'release'
        env = dict(os.environ, RELEASE_TAG='v0.1.1', EXPECTED_COMMIT=self.source,
                   GITHUB_REPOSITORY=self.repository)
        command = [sys.executable, str(script_dir / 'prepare-release-assets.py'),
                   str(incoming), str(output)]
        subprocess.run(command, env=env, check=True, capture_output=True, text=True)
        checksums = (output / 'SHA256SUMS').read_text().splitlines()
        self.assertEqual(len(checksums), 5)
        for entry in checksums:
            digest, name = entry.split()
            self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
        self.assertEqual((output / 'Package.swift').read_bytes(), (self.assets / 'Package.swift').read_bytes())
        for platform, filename in [('ios', self.archive.name),
                                   ('android', 'gorberry-collage-0.1.1-maven.zip')]:
            archive = incoming / f'gorberry-collage-{platform}-{self.source}' / filename
            original = archive.read_bytes()
            archive.write_bytes(b'corrupt')
            result = subprocess.run(command[:-1] + [str(self.base / 'bad-release')], env=env,
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('checksum mismatch', result.stderr)
            self.assertFalse((self.base / 'bad-release').exists())
            archive.write_bytes(original)

        android = incoming / f'gorberry-collage-android-{self.source}'
        checksum_file = android / 'SHA256SUMS'
        original = checksum_file.read_text()
        for invalid in [original.splitlines()[0] + '\n', original + original,
                        original + '0  unexpected.zip\n']:
            checksum_file.write_text(invalid)
            result = subprocess.run(command[:-1] + [str(self.base / 'bad-release')], env=env,
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('missing or unexpected package checksum', result.stderr)
            self.assertFalse((self.base / 'bad-release').exists())
        checksum_file.write_text(original)
        (android / 'gorberry-collage-0.1.1-maven.zip').unlink()
        result = subprocess.run(command[:-1] + [str(self.base / 'bad-release')], env=env,
                                capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('missing or empty package', result.stderr)
        self.assertFalse((self.base / 'bad-release').exists())


if __name__ == '__main__':
    unittest.main()
