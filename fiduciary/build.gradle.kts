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
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-couchdb"))
                implementation(project(":trikeshed-json"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.register<JavaExec>("runFetchIndexes") {
    group = "application"
    description = "Runs the Divine Index Fetcher"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("fiduciary.FetchIndexesKt")
}