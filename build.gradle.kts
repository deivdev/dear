buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships built-in Kotlin pinned to an older KGP; this lifts it to the catalog version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
