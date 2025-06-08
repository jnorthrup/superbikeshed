plugins {
    kotlin("multiplatform") version "2.2.0-RC2"
}


group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    targets.all {
        withSourcesJar()
    }
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation(project(":Review:trikeshed-core"))
            }
            kotlin {
                // Default srcDir is usually src/commonMain/kotlin
                // exclude("borg/trikeshed/core/**") // Now compiled by :trikeshed-core
                // exclude("borg/trikeshed/lib/**")   // Now compiled by :trikeshed-core
                exclude("borg/trikeshed/net/**")
                exclude("evolution/**")
                exclude("com/example/trikeshedcore/**")
                exclude("gk/kademlia/**")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        val linuxX64Main by getting {
            dependsOn(commonMain)
            kotlin.srcDirs("src/posixMain/kotlin", "src/linuxX64Main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-linuxx64:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-linuxx64:0.6.1")
            }
        }
    }
}
