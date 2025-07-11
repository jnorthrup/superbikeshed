plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm()
    macosArm64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-lsmr"))
                implementation(project(":trikeshed-channel-api"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib"))
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

// Add run task for the fiduciary service (CCEK implementation)
tasks.register<JavaExec>("runCouchDB") {
    dependsOn("jvmMainClasses")
    classpath = configurations["jvmRuntimeClasspath"]
    mainClass.set("borg.trikeshed.couchdb.FiduciaryCCEKKt")
    standardInput = System.`in`
}

// Add run task for the full service (when dependencies are fixed)
tasks.register<JavaExec>("runCouchDBFull") {
    dependsOn("jvmMainClasses")
    classpath = configurations["jvmRuntimeClasspath"]
    mainClass.set("borg.trikeshed.couchdb.MainKt")
    standardInput = System.`in`
}