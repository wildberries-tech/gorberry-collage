import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "ru.wildberries"
version = "0.1.0-SNAPSHOT"

kotlin {
    androidLibrary {
        namespace = "ru.wildberries.collage"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {

        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

//    coordinates(
//        group = "ru.wildberries",
//        artifactId = "gorberry-collage",
//        version = version.toString(),
//    )

    pom {
        name.set("Gorberry Collage")
        description.set(
            "Kotlin Multiplatform adaptive collage layout engine for image groups " +
                    "in chats, reviews, feeds, and other media group previews"
        )
        inceptionYear.set("2026")
        url.set("https://github.com/<wb-github-org>/gorberry-collage")


        // Надо уточнять
//        licenses {
//            license {
//                name.set("The Apache License, Version 2.0")
//                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
//                distribution.set("repo")
//            }
//        }

        developers {
            developer {
                id.set("ektomo")
                name.set("Ivan Gorbunov")
                url.set("https://github.com/Ektomo")
            }
        }

        scm {
            url.set("https://github.com/<wb-github-org>/gorberry-collage")
            connection.set("scm:git:https://github.com/<wb-github-org>/gorberry-collage.git")
            developerConnection.set("scm:git:ssh://git@github.com/<wb-github-org>/gorberry-collage.git")
        }
    }
}
