plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

val enabledTargets = (rootProject.findProperty("enabledTargets") as? String)?.split(",")?.map { it.trim() } ?: listOf("jvm")

kotlin {
    jvm()
    macosArm64()
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
    }
    
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
        jsMain {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.2.0-pre.385")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:18.2.0-pre.385")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:11.9.3-pre.385")
                implementation(npm("d3", "^7.8.5"))
                implementation(npm("dagre", "^0.8.5"))
                implementation(npm("dagre-d3", "^0.6.4"))
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