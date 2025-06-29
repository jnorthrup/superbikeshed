plugins {
    kotlin("jvm")
}

group = "borg.nexus"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    sourceSets {
        val main by getting {
            kotlin.srcDir("src/standalone/kotlin")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check"
        )
    }
}

tasks.register<JavaExec>("runStandaloneNexus") {
    dependsOn("jar")
    group = "application"
    description = "Run Standalone Nexus - Main()'s Pursuit of Happiness"
    mainClass.set("borg.trikeshed.nexus.StandaloneNexusKt")
    classpath = files(
        tasks.named("jar").get().outputs.files
    ) + (configurations.runtimeClasspath ?: files())
    
    jvmArgs = listOf(
        "-Xmx1g",
        "-XX:+UseG1GC"
    )
} 