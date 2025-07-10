plugins {
    kotlin("multiplatform")
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
                implementation(kotlin("test"))
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-couchdb"))
                implementation(project(":trikeshed-json"))
                implementation(libs.coroutines)
                implementation(libs.serialization.json)
                implementation(libs.datetime)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

