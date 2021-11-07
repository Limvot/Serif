
import org.jetbrains.compose.compose

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    //id("org.jetbrains.compose") version "1.0.0-alpha1"
    id("org.jetbrains.compose") version "1.0.0-beta5"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvm()
    android()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                api(compose.materialIconsExtended)
                implementation(project(":serif_shared"))
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
        //val jvmMain by getting {
        //    dependencies {
        //    }
        //}
    }
}
android {
    compileSdkVersion(29)
    defaultConfig {
        minSdkVersion(24)
        targetSdkVersion(29)
        //versionCode = 1
        //versionName = "1.0"
    }
    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
        }
    }
}
