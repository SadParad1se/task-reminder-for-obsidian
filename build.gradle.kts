// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Keep AGP's built-in Kotlin aligned with the Compose compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}
