plugins {
    kotlin("multiplatform") version "2.2.0-RC2" apply false
    kotlin("jvm") version "2.2.0-RC2" apply false
    kotlin("android") version "2.2.0-RC2" apply false
    kotlin("js") version "2.2.0-RC2" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
}

allprojects {
    group = "org.superbikeshed"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/eap")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://jitpack.io")
    }
}

subprojects {
    // Apply different configurations based on project type
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        tasks.withType<JavaExec> {
            jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
        
        tasks.withType<Test> {
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            useJUnitPlatform()
        }
    }
    
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        tasks.withType<Test> {
            useJUnitPlatform()
        }
        
        // JVM-specific configurations for multiplatform projects
        tasks.matching { it.name.contains("jvm", ignoreCase = true) && it is JavaExec }.configureEach {
            (this as JavaExec).jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
        
        tasks.matching { it.name.contains("jvm", ignoreCase = true) && it is Test }.configureEach {
            (this as Test).jvmArgs("--enable-native-access=ALL-UNNAMED")
        }
    }
}