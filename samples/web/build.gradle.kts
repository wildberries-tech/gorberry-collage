plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "gorberry-collage-web-sample.js"
            }
        }

        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":collage"))
        }
    }
}
