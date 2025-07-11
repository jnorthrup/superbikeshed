plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "fiduciary"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // DateTime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    
    // HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // TrikeShed Dependencies
    implementation(project(":trikeshed-dht"))
    implementation(project(":trikeshed-lib"))
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

// Custom task to run the daemon
tasks.register<JavaExec>("runDaemon") {
    group = "application"
    mainClass.set("fiduciary.FiduciaryDaemonKt")
    classpath = sourceSets["main"].runtimeClasspath
    args("start")
}

// Custom task to run the production server
tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass.set("fiduciary.FiduciaryProductionServerKt")
    classpath = sourceSets["main"].runtimeClasspath
} 