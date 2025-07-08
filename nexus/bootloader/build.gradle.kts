plugins {
    kotlin("jvm")
    application
}

group = "nexus"

repositories {
    mavenCentral()
}

dependencies {
    // No external dependencies - using JDK built-in HTTP client
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("nexus.BootloaderKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "nexus.BootloaderKt"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
} 