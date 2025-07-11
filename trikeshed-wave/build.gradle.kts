plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

val enabledTargets = (rootProject.findProperty("enabledTargets") as? String)?.split(",")?.map { it.trim() } ?: listOf("jvm")

kotlin {
    jvm()
    macosArm64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-services"))
                implementation(project(":trikeshed-couchdb"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-io"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.graphstream:gs-core:2.0")
                implementation("org.graphstream:gs-ui-javafx:2.0")
                implementation("guru.nidi:graphviz-java:0.18.1")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
} 