enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
        id("com.android.library") version "8.9.1"
        id("com.vanniktech.maven.publish") version "0.33.0"
        id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    }
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MindboxCommonLibrary"
include(":mindbox-common")
