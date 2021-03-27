buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
        classpath("com.android.tools.build:gradle:4.1.3")
        classpath("com.squareup.sqldelight:gradle-plugin:1.4.3")
    }
}
group = "xyz.room409.serif"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

//KtLint
val ktlint by configurations.creating

dependencies {
    ktlint("com.pinterest:ktlint:0.40.0")
    // ktlint(project(":custom-ktlint-ruleset")) // in case of custom ruleset
}

val outputDir = "${project.buildDir}/reports/ktlint/"
val inputFiles = project.fileTree(mapOf("dir" to "src", "include" to "**/*.kt"))

val ktlintCheck by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Check Kotlin code style."
    classpath = ktlint
    main = "com.pinterest.ktlint.Main"
    args = listOf("serif_android/src/**/*.kt", "serif_shared/src/**/*.kt", "serif_cli/src/**/*.kt", "serif_swing/src/**/*.kt", "serif_ios/src/**/*.kt")
}

val ktlintFormat by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    outputs.dir(outputDir)

    description = "Fix Kotlin code style deviations."
    classpath = ktlint
    main = "com.pinterest.ktlint.Main"
    args = listOf("-F", "serif_android/src/**/*.kt", "serif_shared/src/**/*.kt", "serif_cli/src/**/*.kt", "serif_swing/src/**/*.kt", "serif_ios/src/**/*.kt")
}

