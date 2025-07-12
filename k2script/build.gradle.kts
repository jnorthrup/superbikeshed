plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

// Read version from top-level VERSION file
val projectVersion = file("../VERSION").readText().trim()
version = projectVersion

group = "org.k2script"

repositories {
    mavenCentral()
    google()
}

val singleTarget: String? = rootProject.findProperty("singleTarget") as String?

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs += listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlin.ExperimentalStdlibApi"
                )
            }
        }
    }
    macosArm64("native") {
        binaries {
            executable {
                entryPoint = "k2script.main"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-reactor"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-services"))
                implementation(project(":trikeshed-json"))
                implementation(project(":trikeshed-rest"))
                implementation(project(":trikeshed-wave"))
                implementation(project(":trikeshed-sumo"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":trikeshed-couchdb"))
                // Add more as needed if referenced
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
                implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("commons-io:commons-io:2.15.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("junit:junit:4.13.2")
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val nativeTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

application {
    mainClass.set("k2script.K2scriptKt")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.clean {
    delete("build")
    delete("out")
} 