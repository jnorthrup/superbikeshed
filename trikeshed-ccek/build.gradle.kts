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
                implementation(project(":trikeshed-common"))
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-reactor"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-dht"))
                implementation(project(":trikeshed-torrent"))
                implementation(project(":trikeshed-ipfs"))
                implementation(project(":trikeshed-couchdb"))
                implementation(project(":trikeshed-ljson"))
                implementation(project(":trikeshed-strace"))
                implementation(project(":trikeshed-ipc"))
                implementation(project(":trikeshed-services"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
} 