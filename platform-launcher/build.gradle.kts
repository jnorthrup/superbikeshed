plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

group = "fiduciary"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
            }
        }
    }
    macosArm64()
    linuxX64()
    
    sourceSets {
        commonMain {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                
                // TrikeShed Dependencies
                implementation(project(":trikeshed-dht"))
                implementation(project(":trikeshed-lib"))
            }
        }
        
        jvmMain {
            dependencies {
                // Kotlin
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
                
                // HTTP Client
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-cio:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        
        jvmTest {
            dependencies {
                // Additional JVM-specific test dependencies if needed
            }
        }
    }
}

application {
    mainClass.set("fiduciary.demo.NUIDConcentricDemoMainKt")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Custom task to run the daemon
tasks.register<JavaExec>("runDaemon") {
    group = "application"
    mainClass.set("fiduciary.FiduciaryDaemonKt")
    classpath = sourceSets["jvmMain"].runtimeClasspath
    args("start")
}

// Custom task to run the production server
tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass.set("fiduciary.FiduciaryProductionServerKt")
    classpath = sourceSets["jvmMain"].runtimeClasspath
} 

tasks.register<JavaExec>("runStandaloneCCEKDemo") {
    group = "demo"
    description = "Run the standalone CCEK demo"
    
    mainClass.set("borg.trikeshed.platformlauncher.StandaloneCCEKDemoKt")
    classpath = sourceSets["jvmMain"].runtimeClasspath
    
    // Exclude problematic dependencies
    classpath = classpath.filter { 
        !it.name.contains("trikeshed-lib") && 
        !it.name.contains("trikeshed-") 
    }
    
    // Add coroutines dependency
    classpath += files("${System.getProperty("user.home")}/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3/*/kotlinx-coroutines-core-jvm-1.7.3.jar")
} 