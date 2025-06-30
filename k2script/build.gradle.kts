import java.util.Locale
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
    
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    js(IR) {
        browser()
        nodejs()
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
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("commons-io:commons-io:2.11.0")
                implementation("commons-codec:commons-codec:1.15")
                implementation("com.konghq:unirest-java:3.14.2")
                implementation("org.semver4j:semver4j:4.3.0")
                implementation(project(":Trikeshed"))
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }
        
        val jvmMain by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation("commons-cli:commons-cli:1.5.0")
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("commons-io:commons-io:2.11.0")
                implementation("commons-codec:commons-codec:1.15")
                implementation("com.konghq:unirest-java:3.14.2")
                implementation("org.semver4j:semver4j:4.3.0")
                implementation("org.slf4j:slf4j-nop:2.0.6")
                implementation("org.jetbrains.kotlin:kotlin-compiler:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven-all:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
                implementation("org.jetbrains.kotlin:kotlin-script-runtime:2.1.21")
                implementation("com.jcabi:jcabi-aether:0.10.1")
                implementation("org.sonatype.aether:aether-api:1.13.1")
                implementation("net.java.dev.jna:jna:5.13.0")
                implementation("com.zaxxer:nuprocess:2.0.6")
                implementation("com.sangupta:murmur:1.0.0")
                implementation("com.offbytwo:docopt:0.6.0.20150202")
                implementation("io.sdkman:sdkman-cli-publicapi:0.0.1")
                implementation("com.github.ajalt.mordant:mordant:2.2.0")
                implementation("org.apache.ivy:ivy:2.5.2")
                implementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
                implementation("org.apache.httpcomponents:httpclient:4.5.14")
                implementation("org.codehaus.plexus:plexus-utils:3.3.0")
                implementation("org.eclipse.aether:aether-api:1.1.0")
                implementation("org.eclipse.aether:aether-connector-basic:1.1.0")
                implementation("org.eclipse.aether:aether-transport-file:1.1.0")
                implementation("org.eclipse.aether:aether-transport-http:1.1.0")
                implementation("org.eclipse.aether:aether-impl:1.1.0")
                implementation("org.apache.maven:maven-aether-provider:3.3.9")
                implementation("commons-logging:commons-logging:1.2")
            }
        }
        
        val jvmTest by getting {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
                implementation("io.mockk:mockk:1.13.8")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.1.21")
            }
        }
        
        if (isMacOS) {
            if (isArm64) {
                val macosArm64Main by getting
                val macosArm64Test by getting
            } else {
                val macosX64Main by getting
                val macosX64Test by getting
            }
        }
        
        if (isLinux) {
            if (isArm64) {
                val linuxArm64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
                val linuxArm64Test by getting
            } else {
                val linuxX64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
                val linuxX64Test by getting
            }
        }
        
        if (isWindows) {
            val mingwX64Main by getting {
                dependencies {
                    implementation(libs.kotlinx.coroutines.core)
                }
            }
            val mingwX64Test by getting
        }
        
        val jsMain by getting {
            dependsOn(commonMain)
        }
        
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        
        val wasmJsMain by getting {
            dependsOn(commonMain)
        }
        
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }
    }
}

val hostOsName = System.getProperty("os.name").lowercase()
val isMac = hostOsName.contains("mac")
val isLinux = hostOsName.contains("linux")
val isWindows = hostOsName.contains("windows")

val generateEmbeddedResources by tasks.registering {
    outputs.dir("${layout.buildDirectory.get()}/generated/src/jvmMain/kotlin")
    doLast {
        val dir = file("${layout.buildDirectory.get()}/generated/src/jvmMain/kotlin/k2script/util")
        dir.mkdirs()
        val file = File(dir, "GeneratedBuildConfig.kt")
        file.writeText("""
            package k2script.util
            
            object GeneratedBuildConfig {
                const val KSCRIPT_VERSION = "${project.version}"
                const val KSCRIPT_KOTLINC_VERSION = "2.1.21"
                const val KSCRIPT_GRADLE_VERSION = "8.14.1"
            }
        """.trimIndent())
    }
}

val k2scriptKt by tasks.registering(Jar::class) {
    dependsOn(tasks.named("jvmJar"))
    archiveClassifier.set("bin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "io.github.kscripting.k2script.Kscript"
    }
    from(configurations["jvmRuntimeClasspath"].map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.named("jvmJar").get() as CopySpec)
}

val k2scriptDistribution by tasks.registering(Task::class) {
    dependsOn(k2scriptKt)
    doLast {
        println("K2Script distribution built: ${k2scriptKt.get().archiveFile.get().asFile}")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name.lowercase(Locale.getDefault())
            version = project.version.toString()
            
            from(components["kotlin"])
            
            artifact(tasks["k2scriptKt"]) {
                classifier = "bin"
            }
            
            pom {
                name.set("k2script")
                description.set("Kotlin scripting for the terminal")
                url.set("https://github.com/holgerbrandl/kscript")
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
                }
                scm {
                    connection.set("scm:git:git://github.com/holgerbrandl/kscript.git")
                    developerConnection.set("scm:git:ssh://github.com/holgerbrandl/kscript.git")
                    url.set("https://github.com/holgerbrandl/kscript")
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
    sign(publishing.publications["maven"])
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xskip-prerelease-check"
        )
    }
}

kotlin.sourceSets.getByName("jvmMain").kotlin.srcDir("${layout.buildDirectory.get()}/generated/src/jvmMain/kotlin")
tasks.named("compileKotlinJvm").configure {
    dependsOn(generateEmbeddedResources)
}

if (isMac || isLinux) {
    val createNativeDistributable by tasks.registering(Exec::class) {
        dependsOn(k2scriptKt)
        val workingDirPath = projectDir.absolutePath
        val jarFile = "${layout.buildDirectory.get()}/libs/${project.name}-${project.version}-bin.jar"
        workingDir = File(workingDirPath)
        
        commandLine("bash", "-c", """
            mkdir -p build/native
            export APP_NAME="${project.name}"
            export APP_VERSION="${project.version}"
            export MODULE_JAR="$jarFile"
            export BUILD_DIR="${layout.buildDirectory.get()}/native"
            export MAIN_CLASS="io.github.kscripting.k2script.Kscript"
            
            echo '#!/bin/bash' > "${'$'}BUILD_DIR/${'$'}APP_NAME"
            echo 'DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" && pwd )"' >> "${'$'}BUILD_DIR/${'$'}APP_NAME"
            echo 'java -jar "${'$'}DIR/${'$'}APP_NAME-${'$'}APP_VERSION-bin.jar" "${'$'}@"' >> "${'$'}BUILD_DIR/${'$'}APP_NAME"
            chmod +x "${'$'}BUILD_DIR/${'$'}APP_NAME"
            cp "${'$'}MODULE_JAR" "${'$'}BUILD_DIR/"
            
            echo "Created native distributable at ${'$'}BUILD_DIR/${'$'}APP_NAME"
        """.trimIndent())
    }
    
    val packageDistribution by tasks.registering(Zip::class) {
        dependsOn(createNativeDistributable)
        archiveBaseName.set("${project.name}-${project.version}-${currentPlatform()}")
        destinationDirectory.set(file("${layout.buildDirectory.get()}/distributions"))
        from("${layout.buildDirectory.get()}/native") {
            include("**/*")
        }
    }
}

fun currentPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch")
    return when {
        os.contains("mac") && arch == "aarch64" -> "macos-arm64"
        os.contains("mac") -> "macos-x64"
        os.contains("linux") && arch == "aarch64" -> "linux-arm64"
        os.contains("linux") -> "linux-x64"
        os.contains("windows") -> "windows-x64"
        else -> "unknown"
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
    useJUnitPlatform()
}