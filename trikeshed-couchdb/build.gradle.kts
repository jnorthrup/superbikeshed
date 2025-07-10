plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"



kotlin {
    jvm {        
    }
    
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> {
            macosArm64()
        }
        hostOs == "Mac OS X" -> {
            macosX64()
        }
        hostOs.contains("Windows", ignoreCase = true) -> {
            mingwX64()
        }
        hostOs == "Linux" && hostArch == "aarch64" -> {
            linuxArm64()
        }
        hostOs == "Linux" -> {
            linuxX64()
        }
    }
    
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":trikeshed-net"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")
            }
        }
        getByName("jvmMain") {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
}

// Add run task for the KMP main  
tasks.register<JavaExec>("run") {
    mainClass.set("borg.trikeshed.couchdb.MainKt")
    classpath = sourceSets["jvmMain"].runtimeClasspath + sourceSets["commonMain"].runtimeClasspath
    
    standardInput = System.`in`
}