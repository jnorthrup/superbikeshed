plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.github.ben-manes.versions")
    `maven-publish`
    signing
=======
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
>>>>>>> origin/feat/core-serialization-impl
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

<<<<<<< HEAD
group = "rtsgame"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    jvm {
        // JVM target for simulation/testing
    }
    wasmJs {
        browser()
        binaries.executable()
    }
=======
kotlin {
    jvm()
>>>>>>> origin/feat/core-serialization-impl
    // Platform detection for native targets
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"
<<<<<<< HEAD
=======

>>>>>>> origin/feat/core-serialization-impl
    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows -> mingwX64()
    }
<<<<<<< HEAD
=======
    
>>>>>>> origin/feat/core-serialization-impl
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":Trikeshed"))
<<<<<<< HEAD
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlin("reflect"))
                implementation(libs.kotlinx.serialization.json)
=======
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
>>>>>>> origin/feat/core-serialization-impl
            }
        }
    }
}

tasks {
    register("buildAll") {
        dependsOn("build")
        dependsOn("wasmJsBrowserProductionWebpack")
    }
<<<<<<< HEAD
=======
    
>>>>>>> origin/feat/core-serialization-impl
    register("runJvm", JavaExec::class) {
        classpath = configurations["jvmRuntimeClasspath"] + files("${layout.buildDirectory.get()}/classes/kotlin/jvm/main")
        mainClass.set("rtsgame.MainJvmKt")
        dependsOn("jvmMainClasses")
    }
<<<<<<< HEAD
    register("runWasm") {
        dependsOn("wasmJsBrowserDevelopmentRun")
    }
=======
    
    register("runWasm") {
        dependsOn("wasmJsBrowserDevelopmentRun")
    }
    
>>>>>>> origin/feat/core-serialization-impl
    // Clean task to remove all build artifacts
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${layout.buildDirectory.get()}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
            delete("${project.projectDir}/js")
            delete("${project.projectDir}/node_modules")
        }
    }
<<<<<<< HEAD
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            pom {
                name.set("RTSGame")
                description.set("RTSGame - Real-time strategy simulation core")
                url.set("https://github.com/superbikeshed/superbikeshed")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("superbikeshed")
                        name.set("SuperBikeShed Team")
                        email.set("team@superbikeshed.org")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/superbikeshed/superbikeshed.git")
                    developerConnection.set("scm:git:ssh://github.com/superbikeshed/superbikeshed.git")
                    url.set("https://github.com/superbikeshed/superbikeshed")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = project.findProperty("sonatype.user") as String? ?: System.getenv("SONATYPE_USER")
                password = project.findProperty("sonatype.password") as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
=======
} 
>>>>>>> origin/feat/core-serialization-impl
