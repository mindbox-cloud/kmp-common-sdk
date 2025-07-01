import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("maven-publish")
    id("com.vanniktech.maven.publish") version "0.33.0"
}

group = "cloud.mindbox"
version = "1.0.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
        publishLibraryVariants("release")
    }

    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "mindbox-common"
            xcf.add(this)
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test:2.0.0")
        }
    }
}

android {
    namespace = "cloud.mindbox.common"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    repositories {
        maven {
            name = "Snapshots"
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    
    coordinates(group.toString(), "mindbox-common", version.toString())

    pom {
        name.set("Mindbox Common SDK")
        description = "Android Mindbox SDK"
        url = "https://github.com/mindbox-cloud/android-sdk"
        licenses {
            license {
                name = "The Mindbox License"
                url = "https://github.com/mindbox-cloud/android-sdk/blob/master/LICENSE.md"
            }
        }

        developers {
            developer {
                id = "Mindbox"
                name = "Mindbox"
                email = "android-sdk@mindbox.ru"
            }
        }

        scm {
            connection = "scm:https://github.com/mindbox-cloud/android-sdk.git"
            developerConnection = "scm:git://github.com/mindbox-cloud/android-sdk.git"
            url = "https://github.com/mindbox-cloud/android-sdk"
        }
    }

    signAllPublications()
}
