plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "com.rtsgame"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        withJava()
    }
    
    js(IR) {
        browser()
        nodejs()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
                implementation(project(":trikeshed-lib"))
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
            }
        }
        
        val jvmTest by getting
        
        val jsMain by getting
    }
}

// JVM target version
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Main application task
tasks.register<JavaExec>("runJvm") {
    group = "application"
    description = "Run the RTS game on JVM"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.MainKt")
}

// Dense RTS launcher
tasks.register<JavaExec>("runDense") {
    group = "application"
    description = "Run the Dense RTS implementation"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.core.DenseLauncherKt")
}

// Concentric Network RTS launcher
tasks.register<JavaExec>("runRTS") {
    group = "application"
    description = "Run the RTS game with concentric network"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.RTSLauncherKt")
    args = listOf("demo")
}

// Solo game task
tasks.register<JavaExec>("runSolo") {
    group = "application"
    description = "Run solo RTS game"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.RTSLauncherKt")
    args = listOf("solo")
}

// Host multiplayer task
tasks.register<JavaExec>("runHost") {
    group = "application"
    description = "Host multiplayer RTS game"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.RTSLauncherKt")
    args = listOf("host")
}

// Join multiplayer task
tasks.register<JavaExec>("runJoin") {
    group = "application"
    description = "Join multiplayer RTS game"
    classpath = kotlin.jvm().compilations.getByName("main").output.allOutputs +
                kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("rtsgame.RTSLauncherKt")
    args = listOf("join")
}