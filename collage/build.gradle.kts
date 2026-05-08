import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

group = "ru.wildberries"
version = "0.1.0"

kotlin {
    androidLibrary {
        namespace = "ru.wildberries.collage"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    js(IR) {
        browser {
            testTask {
                enabled = false
            }
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
