#!/usr/bin/env python3
"""Compile an isolated Android consumer against the Maven ZIP, including POM-only resolution."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
import zipfile

from swift_package import read_version


def check(archive: Path) -> None:
    root = Path(__file__).resolve().parent.parent
    version = read_version(root)
    versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']
    temporary_root = root / 'build/tmp'
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='android-maven-consumer-', dir=temporary_root) as directory:
        consumer = Path(directory)
        repository = consumer / 'repository'
        with zipfile.ZipFile(archive) as package:
            for entry in package.namelist():
                if not (repository / entry).resolve().is_relative_to(repository.resolve()):
                    raise ValueError(f'Invalid archive path: {entry}')
            package.extractall(repository)

        coordinate = f'ru/wildberries/gorberry-collage-android/{version}'
        artifact_dir = repository / coordinate
        stem = f'gorberry-collage-android-{version}'
        for suffix in ['.aar', '.pom', '.module', '-sources.jar']:
            artifact = artifact_dir / (stem + suffix)
            if not artifact.is_file() or artifact.stat().st_size == 0:
                raise ValueError(f'Missing Maven artifact: {artifact.name}')
            expected = artifact.with_name(artifact.name + '.sha256').read_text().strip()
            if hashlib.sha256(artifact.read_bytes()).hexdigest() != expected:
                raise ValueError(f'Checksum mismatch: {artifact.name}')
        pom = ET.parse(artifact_dir / (stem + '.pom'))
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for field, expected in [('groupId', 'ru.wildberries'), ('artifactId', 'gorberry-collage-android'),
                                ('version', version), ('packaging', 'aar')]:
            if pom.findtext(f'm:{field}', namespaces=ns) != expected:
                raise ValueError(f'Unexpected Maven {field}')

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
            filter { includeGroup("ru.wildberries") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "android-maven-consumer"
''')
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
dependencies {{ implementation("ru.wildberries:gorberry-collage-android:{version}") }}
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
    print(f'Android Maven package verified: ru.wildberries:gorberry-collage-android:{version}')


if __name__ == '__main__':
    check(Path(sys.argv[1]).resolve())
