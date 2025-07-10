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
                implementation(libs.coroutines)
                implementation(project(":trikeshed-quic"))
                implementation(project(":trikeshed-reactor"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(kotlin("test"))
            }
        }
    }
}