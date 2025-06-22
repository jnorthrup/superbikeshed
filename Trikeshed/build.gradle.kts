plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
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
    
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }
    
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMac = hostOs.startsWith("Mac OS")
    val isLinux = hostOs.startsWith("Linux")

    if (isMac) {
        macosArm64()
        macosX64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    } else if (isMingwX64) {
        mingwX64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(kotlin("reflect"))
            }
        }
    }
}

// Disable linting to keep code terse
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi"
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
                name.set("TrikeShed")
                description.set("TrikeShed - Core multiplatform data structures and algorithms")
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