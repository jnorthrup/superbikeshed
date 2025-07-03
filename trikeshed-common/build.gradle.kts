plugins { kotlin("multiplatform") }
group = "borg.trikeshed"
repositories { mavenCentral() }

kotlin {
    jvm(); wasmJs { browser(); nodejs() }
    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> { macosX64(); macosArm64() }
        hostOs == "Linux" -> { linuxX64(); linuxArm64() }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
            }
        }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
} 