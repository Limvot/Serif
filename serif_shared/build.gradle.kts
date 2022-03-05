import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.4.10"
    id("com.android.library")
    id("kotlin-android-extensions")
    id("com.squareup.sqldelight")
}
val ktor_version = "1.5.0"
group = "xyz.room409.serif"
version = "1.0-SNAPSHOT"
val sql_delight_version = "1.4.3"

repositories {
    gradlePluginPortal()
    google()
    jcenter()
    mavenCentral()
}
kotlin {
    jvm()
    android()
    ios {
        binaries {
            framework {
                baseName = "serif_shared"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-serialization:$ktor_version")
                implementation("com.squareup.sqldelight:runtime:$sql_delight_version")
                implementation("io.github.brevilo:jolm:1.1.0")
                implementation(files("Olm.jar"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.material:material:1.2.0")
                //implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-android:$ktor_version")
                implementation("com.squareup.sqldelight:android-driver:$sql_delight_version")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.12")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-ios:$ktor_version")
                implementation("com.squareup.sqldelight:native-driver:$sql_delight_version")
            }
        }
        val iosTest by getting
        val jvmMain by getting {
            dependencies {
                //implementation("io.ktor:ktor-client-cio:$ktor_version")
                implementation("io.ktor:ktor-client-okhttp:$ktor_version")
                implementation("com.squareup.okhttp3:okhttp:4.9.0")
                implementation("com.squareup.sqldelight:sqlite-driver:$sql_delight_version")
            }
        }
    }
}
android {
    compileSdkVersion(29)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdkVersion(24)
        targetSdkVersion(29)
        //versionCode = 1
        //versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
    val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
}
tasks.getByName("build").dependsOn(packForXcode)

sqldelight {
  database("SessionDb") { // This will be the name of the generated database class.
      packageName = "xyz.room409.serif.serif_shared.db"
  }
}
