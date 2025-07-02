plugins { kotlin("multiplatform") }
group = "borg.trikeshed"
version = "1.0-SNAPSHOT"
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
                implementation(project(":trikeshed-io"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
} 