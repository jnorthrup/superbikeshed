<<<<<<< HEAD
import java.util.Locale

plugins {
    alias(libs.plugins.kotlin.multiplatform)
=======
import java.util.Locale // Added for toLowerCase
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.jetbrains.kotlin.gradle.dsl.JvmTarget // Moved import to top

plugins {
    kotlin("multiplatform") version "2.1.21"
>>>>>>> origin/feat/core-serialization-impl
    `maven-publish`
    signing
}

group = "io.github.kscripting"
version = "2.1.21"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
<<<<<<< HEAD
    wasmJs {
        browser()
=======
    js(IR) {
        browser()
        nodejs()
>>>>>>> origin/feat/core-serialization-impl
        binaries.executable()
    }
    // Platform detection for native target
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"
    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows -> mingwX64()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("commons-cli:commons-cli:1.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("commons-io:commons-io:2.11.0")
                implementation("commons-codec:commons-codec:1.15")
                implementation("com.konghq:unirest-java:3.14.2")
                implementation("org.semver4j:semver4j:4.3.0")
                // implementation(project(":Trikeshed")) // Temporarily disabled due to compilation issues
<<<<<<< HEAD
                // implementation(project(":kotlin-entity-scanner")) // Only in JVM
            }
        }

=======
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }
<<<<<<< HEAD

=======
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmMain by getting {
            dependencies {
                implementation("commons-cli:commons-cli:1.5.0")
                implementation("com.konghq:unirest-java:3.14.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("commons-io:commons-io:2.11.0")
                implementation("commons-codec:commons-codec:1.15")
                implementation("org.semver4j:semver4j:4.3.0")
                implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven-all:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
                implementation("org.slf4j:slf4j-nop:2.0.7")
                implementation("org.apache.maven.resolver:maven-resolver-impl:1.9.18")
                implementation("org.apache.maven.resolver:maven-resolver-api:1.9.18")
                implementation("org.apache.maven.resolver:maven-resolver-spi:1.9.18")
                implementation("org.apache.maven.resolver:maven-resolver-util:1.9.18")
                implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
                implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
                implementation("org.apache.maven:maven-core:3.9.6")
                implementation("org.apache.maven:maven-model:3.9.6")
                implementation("org.apache.maven:maven-artifact:3.9.6")
<<<<<<< HEAD
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-script-runtime:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-main-kts:2.1.21")
                // implementation(project(":kotlin-entity-scanner")) // Temporarily disabled
            }
        }

=======
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
                implementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
                implementation("io.mockk:mockk:1.13.2")
                implementation(kotlin("script-runtime"))
            }
        }
<<<<<<< HEAD

        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test"))
=======
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
>>>>>>> origin/feat/core-serialization-impl
            }
        }
    }
}

// Disable linting to keep code terse
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-source-roots-assertions",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlin.RequiresOptIn",
<<<<<<< HEAD
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
=======
            "-Xopt-in=kotlin.ExperimentalStdlibApi"
>>>>>>> origin/feat/core-serialization-impl
        )
    }
}

val createKscriptLayout by tasks.register<Copy>("createKscriptLayout") {
    from("src/main/resources") {
        into(".")
    }
    from("src/commonMain/kotlin") {
        into(".")
    }
    from("src/commonMain/kotlin") {
        into("bin")
    }
    from("src/jvmMain/kotlin") {
        into(".")
    }
    from("wrappers") {
        into("wrappers")
    }
    from("setup.py")
    from("package.json")
<<<<<<< HEAD
    destinationDir =
        layout.buildDirectory
            .dir("kscript")
            .get()
            .asFile
=======
    destinationDir = layout.buildDirectory.dir("kscript").get().asFile
>>>>>>> origin/feat/core-serialization-impl
}

val createK2scriptLayout by tasks.register<Copy>("createK2scriptLayout") {
    from("src/main/resources") {
        into(".")
    }
    from("src/commonMain/kotlin") {
        into(".")
    }
    from("src/commonMain/kotlin") {
        into("bin")
    }
    from("src/jvmMain/kotlin") {
        into(".")
    }
    from("wrappers") {
        into("wrappers")
    }
    from("setup.py")
    from("package.json")
<<<<<<< HEAD
    destinationDir =
        layout.buildDirectory
            .dir("k2script")
            .get()
            .asFile
=======
    destinationDir = layout.buildDirectory.dir("k2script").get().asFile
>>>>>>> origin/feat/core-serialization-impl
}

val packageK2scriptDistribution by tasks.register<Zip>("packageK2scriptDistribution") {
    dependsOn(createK2scriptLayout)
    from(layout.buildDirectory.dir("k2script")) {
        into("k2script-${project.version}")
    }
    archiveFileName.set("k2script-${project.version}-bin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// Disable distribution tasks that are not needed for Maven-centric build
tasks.withType<Tar> {
    enabled = false
}

tasks.withType<Zip> {
    if (name != "packageK2scriptDistribution") {
        enabled = false
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = adjustVersion(project.version.toString())
<<<<<<< HEAD

            artifact(tasks.named("jvmJar"))

=======
            
            artifact(tasks.named("jvmJar"))
            
>>>>>>> origin/feat/core-serialization-impl
            pom {
                name.set("kscript")
                description.set("KScript - easy scripting with Kotlin")
                url.set("https://github.com/kscripting/kscript")
<<<<<<< HEAD

=======
                
>>>>>>> origin/feat/core-serialization-impl
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("holgerbrandl")
                        name.set("Holger Brandl")
                        email.set("holgerbrandl@gmail.com")
                    }
                    developer {
                        id.set("aartiPl")
                        name.set("Marcin Kuszczak")
                        email.set("aarti@interia.pl")
                    }
                }
                scm {
                    connection.set("scm:git:git://https://github.com/kscripting/kscript.git")
                    developerConnection.set("scm:git:ssh:https://github.com/kscripting/kscript.git")
                    url.set("https://github.com/kscripting/kscript")
                }
            }
        }
    }
<<<<<<< HEAD

=======
    
>>>>>>> origin/feat/core-serialization-impl
    repositories {
        maven {
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            val adjustedVersion = adjustVersion(project.version.toString())
            url = uri(if (adjustedVersion.endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
<<<<<<< HEAD

=======
            
>>>>>>> origin/feat/core-serialization-impl
            credentials {
                username = project.findProperty("sonatype.user") as String? ?: System.getenv("SONATYPE_USER")
                password = project.findProperty("sonatype.password") as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

<<<<<<< HEAD
// signing {
//     sign(publishing.publications["mavenJava"])
// }
=======
signing {
    sign(publishing.publications["mavenJava"])
}
>>>>>>> origin/feat/core-serialization-impl

fun adjustVersion(archiveVersion: String): String {
    var newVersion = archiveVersion.lowercase(Locale.ROOT)
    val temporaryVersion = newVersion.substringBeforeLast(".")
<<<<<<< HEAD

    if (temporaryVersion.endsWith("-RC", true) ||
        temporaryVersion.endsWith("-BETA", true) ||
        temporaryVersion.endsWith("-ALPHA", true) ||
=======
    
    if (temporaryVersion.endsWith("-RC", true) || temporaryVersion.endsWith("-BETA", true) || temporaryVersion.endsWith("-ALPHA", true) ||
>>>>>>> origin/feat/core-serialization-impl
        temporaryVersion.endsWith("-SNAPSHOT", true)
    ) {
        newVersion = temporaryVersion.substringBeforeLast("-") + "-SNAPSHOT"
    }
<<<<<<< HEAD

    return newVersion
}

// Create executable JAR
val k2scriptJar by tasks.registering(Jar::class) {
    dependsOn("compileKotlinJvm")
    
    archiveBaseName.set("k2script")
    archiveClassifier.set("standalone")
    
    manifest {
        attributes["Main-Class"] = "k2script.jvm.K2ScriptStandaloneKt"
    }
    
    // Get the compiled classes
    val kotlinClasses = tasks.getByName("compileKotlinJvm").outputs.files
    from(kotlinClasses)
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Create k2script executable script
tasks.register("createK2ScriptExecutable") {
    dependsOn(k2scriptJar)
    doLast {
        val jarFile = k2scriptJar.get().archiveFile.get().asFile
        
        val scriptContent = """#!/bin/bash
DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" && pwd )"
java -jar "${'$'}DIR/${jarFile.name}" "$@"
"""
        val scriptFile = file("${layout.buildDirectory.get().asFile}/bin/k2script")
        scriptFile.parentFile.mkdirs()
        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)
        
        println("Created executable: ${scriptFile.absolutePath}")
        println("JAR location: ${jarFile.absolutePath}")
        
        // Copy JAR to bin directory
        jarFile.copyTo(file("${layout.buildDirectory.get().asFile}/bin/${jarFile.name}"), overwrite = true)
    }
}
=======
    
    return newVersion
}
>>>>>>> origin/feat/core-serialization-impl
