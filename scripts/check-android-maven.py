#!/usr/bin/env python3
"""Compile an isolated Android consumer against a Maven ZIP or directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import tomllib
import xml.etree.ElementTree as ET
import zipfile

from swift_package import read_version


def check(source: Path, group: str, artifact_id: str, version: str | None) -> None:
    root = Path(__file__).resolve().parent.parent
    version = version or read_version(root)
    versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']
    temporary_root = root / 'build/tmp'
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='android-maven-consumer-', dir=temporary_root) as directory:
        consumer = Path(directory)
        repository = consumer / 'repository'
        if source.is_dir():
            shutil.copytree(source, repository)
        else:
            with zipfile.ZipFile(source) as package:
                for entry in package.namelist():
                    if not (repository / entry).resolve().is_relative_to(repository.resolve()):
                        raise ValueError(f'Invalid archive path: {entry}')
                package.extractall(repository)

        coordinate = f'{group.replace(".", "/")}/{artifact_id}/{version}'
        artifact_dir = repository / coordinate
        stem = f'{artifact_id}-{version}'
        for suffix in ['.aar', '.pom', '.module', '-sources.jar']:
            artifact = artifact_dir / (stem + suffix)
            if not artifact.is_file() or artifact.stat().st_size == 0:
                raise ValueError(f'Missing Maven artifact: {artifact.name}')
            # publishToMavenLocal omits sidecar checksums; the release ZIP must include them.
            if not source.is_dir():
                expected = artifact.with_name(artifact.name + '.sha256').read_text().strip()
                if hashlib.sha256(artifact.read_bytes()).hexdigest() != expected:
                    raise ValueError(f'Checksum mismatch: {artifact.name}')
        pom = ET.parse(artifact_dir / (stem + '.pom'))
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for field, expected in [('groupId', group), ('artifactId', artifact_id),
                                ('version', version), ('packaging', 'aar')]:
            if pom.findtext(f'm:{field}', namespaces=ns) != expected:
                raise ValueError(f'Unexpected Maven {field}')

        metadata = json.loads((artifact_dir / (stem + '.module')).read_text())
        for variant in metadata['variants']:
            for entry in variant.get('files', []):
                file = artifact_dir / entry['url']
                if not file.resolve().is_relative_to(artifact_dir.resolve()):
                    raise ValueError(f'Invalid metadata URL: {entry["url"]}')
                if hashlib.sha256(file.read_bytes()).hexdigest() != entry['sha256']:
                    raise ValueError(f'Module metadata checksum mismatch: {entry["url"]}')

        (consumer / 'settings.gradle.kts').write_text('''
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    url = uri("repository")
                    if (providers.gradleProperty("pomOnly").isPresent) {
                        metadataSources {
                            mavenPom()
                            ignoreGradleMetadataRedirection()
                        }
                    }
                }
            }
            filter { includeGroup(PUBLISHED_GROUP) }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "android-maven-consumer"
'''.replace('PUBLISHED_GROUP', json.dumps(group)))
        (consumer / 'build.gradle.kts').write_text(f'''
plugins {{
    id("com.android.library") version {json.dumps(versions['agp'])}
    id("org.jetbrains.kotlin.android") version {json.dumps(versions['kotlin'])}
}}
android {{
    namespace = "test.collage.consumer"
    compileSdk = {versions['android-compileSdk']}
    defaultConfig {{ minSdk = {versions['android-minSdk']} }}
    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }}
}}
kotlin {{
    compilerOptions {{ jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }}
}}
dependencies {{ implementation({json.dumps(f'{group}:{artifact_id}:{version}')}) }}
''')
        # Disabling the plugin's automatic stdlib dependency proves the published
        # metadata supplies it, including when a Maven server serves only the POM.
        (consumer / 'gradle.properties').write_text(
            'kotlin.stdlib.default.dependency=false\norg.gradle.jvmargs=-Xmx2048M\n')
        local_properties = root / 'local.properties'
        if local_properties.exists():
            shutil.copyfile(local_properties, consumer / 'local.properties')
        source = consumer / 'src/main/kotlin/Consumer.kt'
        source.parent.mkdir(parents=True)
        source.write_text('''
package test.collage.consumer
import ru.wildberries.collage.CollageConfiguration
import ru.wildberries.collage.CollageEngine

fun engine(): CollageEngine = CollageEngine(CollageConfiguration().apply { spacing = 4f })
fun stdlibFromPom(): List<String> = listOf("resolved transitively")
''')
        (consumer / 'src/main/AndroidManifest.xml').write_text('<manifest />\n')
        wrapper = [str(root / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(root / 'gradlew')]
        command = wrapper + ['-p', str(consumer), 'compileDebugKotlin', 'compileReleaseKotlin', '--stacktrace']
        for flags in [[], ['-PpomOnly=true', '--rerun-tasks']]:
            print('Checking Android consumer: ' + ('POM only' if flags else 'Gradle module metadata'), flush=True)
            subprocess.run(command + flags, cwd=root, check=True)
    print(f'Android Maven package verified: {group}:{artifact_id}:{version}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--group', default='ru.wildberries')
    parser.add_argument('--artifact', default='gorberry-collage-android')
    parser.add_argument('--version')
    args = parser.parse_args()
    check(args.source.resolve(), args.group, args.artifact, args.version)
