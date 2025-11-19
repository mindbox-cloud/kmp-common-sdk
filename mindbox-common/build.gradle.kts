
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("maven-publish")
    id("com.vanniktech.maven.publish")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "cloud.mindbox"
version = providers.gradleProperty("SDK_VERSION_NAME").get()
println("mindbox-common version: $version")

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

    val xcFrameworkName = "MindboxCommon"
    val xcf = XCFramework(xcFrameworkName)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = xcFrameworkName
            xcf.add(this)
            isStatic = false
            freeCompilerArgs += "-g"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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

ktlint {
    version = "1.3.1"
    debug = true
    verbose = true
    android = true
    outputToConsole = true
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

mavenPublishing {
    publishToMavenCentral("CENTRAL_PORTAL")

    if (System.getenv("CI") == "true") {
        signAllPublications()
    }

    coordinates(group.toString(), "mindbox-common", version.toString())

    pom {
        name.set("Mindbox Common SDK")
        description = "Mindbox Common SDK"
        url = "https://github.com/mindbox-cloud/kmp-common-sdk"
        licenses {
            license {
                name = "The Mindbox License"
                url = "https://github.com/mindbox-cloud/kmp-common-sdk/blob/master/LICENSE.md"
            }
        }

        developers {
            developer {
                id = "Mindbox"
                name = "Mindbox"
                email = "mobile-sdk@mindbox.cloud"
            }
        }

        scm {
            connection = "scm:https://github.com/mindbox-cloud/kmp-common-sdk.git"
            developerConnection = "scm:git://github.com/mindbox-cloud/kmp-common-sdk.git"
            url = "https://github.com/mindbox-cloud/kmp-common-sdk"
        }
    }
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

tasks.withType<KtLintCheckTask>().configureEach {
    dependsOn("generateBuildConfig")
}
tasks.withType<KtLintFormatTask>().configureEach {
    dependsOn("generateBuildConfig")
}

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

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("generateBuildConfig"))
}
