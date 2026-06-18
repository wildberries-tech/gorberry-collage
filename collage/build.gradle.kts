import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.npm.publish)
}

group = "ru.wildberries"
version = "0.1.0"

private val jsOutputModuleName = "gorberry-collage"

kotlin {
    androidLibrary {
        namespace = "ru.wildberries.collage"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    js(IR) {
        useEsModules()
        outputModuleName = jsOutputModuleName

        browser {
            testTask {
                enabled = false
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
        }

        binaries.library()
        generateTypeScriptDefinitions()

        compilerOptions {
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
    }


    val frameworkName = "GorberryCollage"
    val xcf = XCFramework(frameworkName)

    val iosTargets = listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    )

    iosTargets.forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = frameworkName
            isStatic = true

            // Unique bundle id for the generated Apple framework.
            binaryOption("bundleId", "ru.wildberries.collage.GorberryCollage")

            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // No runtime dependencies.
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

npmPublish {
    organization = "wildberries"

    packages {
        named("js") {
            packageName = "gorberry-collage"
            version = project.version.toString()
        }
    }
}

private val webNpmSourceDir =
    rootProject.layout.projectDirectory.dir("npm/gorberry-collage")

private val webNpmPackageDir =
    layout.buildDirectory.dir("npm/gorberry-collage")

tasks.register<Sync>("prepareWebNpmPackage") {
    dependsOn("assembleJsPackage")

    into(webNpmPackageDir)

    from(webNpmSourceDir) {
        include("index.js")
        include("index.d.ts")
    }

    from(webNpmSourceDir.file("package.template.json")) {
        rename { "package.json" }
        expand(
            mapOf(
                "version" to project.version.toString(),
            )
        )
    }

    from(rootProject.layout.projectDirectory.file("README.md"))
    from(rootProject.layout.projectDirectory.file("LICENSE"))

    from(layout.buildDirectory.dir("packages/js")) {
        include("*.mjs")
        include("*.mjs.map")
        include("*.d.mts")
        into("dist/kotlin")
    }
}

/**
 * Защита от случайной публикации raw Kotlin/JS package
 *
 * Публиковать нужно не collage/build/packages/js,
 * а collage/build/npm/gorberry-collage
 */
tasks.matching { task ->
    task.name == "packJsPackage" || task.name.startsWith("publishJsPackage")
}.configureEach {
    enabled = false
}

