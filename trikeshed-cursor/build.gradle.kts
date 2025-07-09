plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"



kotlin {
    jvm()
    
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("jvmTest") {
            // JVM-specific test dependencies or configurations can go here
        }
    }
}