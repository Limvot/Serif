plugins {
    id("com.android.application")
    //kotlin("android")
    id("org.jetbrains.kotlin.android") //version "1.5.10"
    id("org.jetbrains.compose") version "1.0.0-beta5"

    id("kotlin-android-extensions")
    id("kotlin-android")
}
group = "xyz.room409.serif"
version = "1.0-SNAPSHOT"

repositories {
    gradlePluginPortal()
    google()
    jcenter()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    mavenCentral()
}
dependencies {
    implementation(project(":serif_shared"))
    implementation(project(":serif_compose"))
    implementation("com.google.android.material:material:1.2.0")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")


    implementation("androidx.compose.ui:ui:1.0.0-rc01")
    // Tooling support (Previews, etc.)
    implementation("androidx.compose.ui:ui-tooling:1.0.0-rc01")
    // Foundation (Border, Background, Box, Image, Scroll, shapes, animations, etc.)
    implementation("androidx.compose.foundation:foundation:1.0.0-rc01")
    // Material Design
    implementation("androidx.compose.material:material:1.0.0-rc01")
    // Material design icons
    implementation("androidx.compose.material:material-icons-core:1.0.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.0.0-rc01")
    // Integration with observables
    implementation("androidx.compose.runtime:runtime:1.0.0-rc01")
    implementation("androidx.compose.runtime:runtime-livedata:1.0.0-rc01")
    implementation("androidx.compose.runtime:runtime-rxjava2:1.0.0-rc01")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("androidx.activity:activity-compose:1.3.0-alpha06")
    implementation("androidx.navigation:navigation-runtime-ktx:2.3.5")

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.0.0-rc01")

    // More from JetChat
    implementation("androidx.compose.ui:ui-viewbinding:1.0.0-rc01")
    implementation("androidx.compose.ui:ui-util:1.0.0-rc01")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.4")
    implementation("com.google.accompanist:accompanist-insets:0.13.0")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "xyz.room409.serif.serif_android"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildFeatures {
        // Enables Jetpack Compose for this module
        compose = true
        viewBinding = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        useIR = true
    }
    composeOptions {
        //kotlinCompilerVersion = "1.5.10"
        kotlinCompilerExtensionVersion = "1.0.0-rc01"
        kotlinCompilerVersion = "1.4.32"
    }
}
