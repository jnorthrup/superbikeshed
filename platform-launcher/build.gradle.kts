plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "21"
        }
        withJava()
    }
    
    // Native targets - conditional fluent elvis
    if (System.getProperty("os.name").contains("Mac")) macosArm64() ?: macosX64()
    if (System.getProperty("os.name").contains("Linux")) linuxX64() ?: linuxArm64()  
    if (System.getProperty("os.name").contains("Windows")) mingwX64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":trikeshed-couchdb"))
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

tasks.register<JavaExec>("runFiduciary") {
    dependsOn("jvmMainClasses")
    classpath = configurations["jvmRuntimeClasspath"] + sourceSets["main"].output
    mainClass.set("borg.trikeshed.launcher.LaunchFiduciary")
    standardInput = System.`in`
}