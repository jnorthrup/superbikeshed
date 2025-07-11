plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

// Read version from top-level VERSION file
val projectVersion = file("../VERSION").readText().trim()
version = projectVersion

group = "org.k2script"

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs += listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlin.ExperimentalStdlibApi"
                )
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlin:kotlin-scripting-common")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
                implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable")
                implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223")
                implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
                
                // HTTP client for AI integration
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-cio:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                
                // Process execution
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                
                // File operations
                implementation("commons-io:commons-io:2.15.1")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("junit:junit:4.13.2")
            }
        }
    }
}

application {
    mainClass.set("k2script.K2scriptKt")
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to "k2script.K2scriptKt",
            "Implementation-Title" to "K2Script",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "K2Script Team"
        )
    }
    
    // Create fat JAR with all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Create distribution
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Creates a fat JAR with all dependencies"
    
    archiveClassifier.set("all")
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    
    manifest {
        attributes(
            "Main-Class" to "k2script.K2scriptKt",
            "Implementation-Title" to "K2Script",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "K2Script Team"
        )
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Update version in package.json and setup.py
tasks.register("updateVersions") {
    group = "versioning"
    description = "Updates version in package.json and setup.py"
    
    doLast {
        // Update package.json
        val packageJson = file("package.json")
        val packageJsonContent = packageJson.readText()
        val updatedPackageJson = packageJsonContent.replace(
            "\"version\": \"[^\"]*\"",
            "\"version\": \"$projectVersion\""
        )
        packageJson.writeText(updatedPackageJson)
        
        // Update setup.py
        val setupPy = file("setup.py")
        val setupPyContent = setupPy.readText()
        val updatedSetupPy = setupPyContent.replace(
            "version='[^']*'",
            "version='$projectVersion'"
        )
        setupPy.writeText(updatedSetupPy)
        
        println("✅ Updated version to $projectVersion in package.json and setup.py")
    }
}

// Test task
tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Clean task
tasks.clean {
    delete("build")
    delete("out")
} 