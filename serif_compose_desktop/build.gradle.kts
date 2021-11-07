
import org.jetbrains.compose.compose

plugins {
    id("org.jetbrains.kotlin.jvm")
    //id("org.jetbrains.compose") version "1.0.0-alpha1"
    id("org.jetbrains.compose") version "1.0.0-beta5"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(project(":serif_shared"))
    implementation(project(":serif_compose"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

