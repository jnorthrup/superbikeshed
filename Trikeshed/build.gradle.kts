plugins {
        
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    // WASM for modern web deployment (replaces JS)
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    // Platform detection for native target
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
                implementation(libs.kotlinx.serialization.core)
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
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                implementation(libs.kotlinx.coroutines.core)
                implementation("org.apache.commons:commons-csv:1.12.0")
                implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
                implementation("com.sun.jna:jna:3.0.9")
                implementation("com.google.guava:guava:33.3.1-jre")
                implementation("io.undertow:undertow-core:2.2.32.Final")
                implementation("org.apache.logging.log4j:log4j-core:2.24.3")
                implementation("com.dynatrace.hash4j:hash4j:0.18.0")
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
                implementation("com.github.luben:zstd-jni:1.5.6-8")
                implementation("org.tukaani:xz:1.10")
                implementation("org.lz4:lz4-java:1.8.0")
                implementation("com.github.haifengl:smile-core:3.1.1")
                implementation("com.github.haifengl:smile-kotlin:3.1.1")
                implementation("ai.djl:api:0.31.0")
                implementation("ai.djl.pytorch:pytorch-engine:0.31.0")
                implementation("ai.djl.pytorch:pytorch-model-zoo:0.31.0")
                runtimeOnly("ai.djl.pytorch:pytorch-native-cpu:2.5.1")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
            }
        }
        
        if (isMacOS) {
            if (isArm64) {
                val macosArm64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
            } else {
                val macosX64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
            }
        }
        
        if (isLinux) {
            if (isArm64) {
                val linuxArm64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
            } else {
                val linuxX64Main by getting {
                    dependencies {
                        implementation(libs.kotlinx.coroutines.core)
                    }
                }
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
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
            "-Xno-receiver-assertions"
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            pom {
                name.set("TrikeShed")
                description.set("TrikeShed - A multiplatform library for Kotlin")
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
    signing {
        sign(publishing.publications["mavenJava"])
    }
}