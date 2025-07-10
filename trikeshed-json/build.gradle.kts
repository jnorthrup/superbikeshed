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
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64()
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" && hostArch == "aarch64" -> linuxArm64()
        hostOs == "Linux" -> linuxX64()
        hostOs.contains("Windows", ignoreCase = true) -> mingwX64()
    }
    
    sourceSets {
        getByName("commonMain") {
            dependencies {
                
                implementation(project(":trikeshed-lib"))
                implementation(libs.serialization.json)
                implementation(libs.coroutines)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation("com.google.code.gson:gson:2.10.1")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
            }
        }
        
    }
}