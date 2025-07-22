import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("maven-publish")
    id("com.vanniktech.maven.publish") version "0.33.0"
}

group = "cloud.mindbox"
version = "1.0.1-SNAPSHOT"

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

    val xcframeworkName = "MindboxCommon"
    val xcf = XCFramework(xcframeworkName)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = xcframeworkName
            xcf.add(this)
            isStatic = true
            freeCompilerArgs += "-g"
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
    compileSdk = 35
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

    kotlin {
        explicitApi()
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    
    coordinates(group.toString(), "mindbox-common", version.toString())

    pom {
        name.set("Mindbox Common SDK")
        description = "Android Mindbox SDK"
        url = "https://github.com/mindbox-cloud/kmp-common-sdk"
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
            connection = "scm:https://github.com/mindbox-cloud/kmp-common-sdk.git"
            developerConnection = "scm:git://github.com/mindbox-cloud/kmp-common-sdk.git"
            url = "https://github.com/mindbox-cloud/kmp-common-sdk"
        }
    }

    signAllPublications()
}


abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    val outputDir = project.layout.buildDirectory.dir("generated/source/version")

    @TaskAction
    fun generate() {
        val pkgDir = outputDir.get().asFile.resolve("cloud/mindbox/common")
        pkgDir.mkdirs()
        val file = pkgDir.resolve("BuildConfig.kt")
        file.writeText(
            """
            package cloud.mindbox.common
            
            internal object BuildConfig {
                internal const val VERSION_NAME = "${versionName.get()}"
             }
            """.trimIndent()
        )
    }
}

tasks.register("generateBuildConfig", GenerateBuildConfigTask::class) {
    versionName.set(project.version.toString())
}

kotlin.sourceSets["commonMain"].kotlin.srcDir(
    tasks.named("generateBuildConfig").flatMap { (it as GenerateBuildConfigTask).outputDir }
)

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("generateBuildConfig")
}

tasks.matching { it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn("generateBuildConfig")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    dependsOn("generateBuildConfig")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon>().configureEach {
    dependsOn("generateBuildConfig")
}