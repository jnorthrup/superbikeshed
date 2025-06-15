import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.1.21"
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/space-sdk/maven")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://www.jetbrains.com/intellij-repository/releases/")
        maven("https://central.sonatype.com/")
        maven("https://maven.pkg.jetbrains.space/public/p/maven/maven")
        maven("https://dl.google.com/dl/android/maven2/")
        maven("https://plugins.jetbrains.com/maven")
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://repository.jboss.org/nexus/content/repositories/releases")
        maven("https://oss.sonatype.org/content/repositories/releases")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    js(IR) {
        browser {
            webpackTask {
                mainOutputFileName = "trikeshed.js"
                outputDirectory = file("$buildDir/distributions")
            }
        }
        nodejs()
    }
    
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()
    
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
            implementation("com.ionspin.kotlin:bignum:0.3.10")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            implementation("org.lz4:lz4-java:1.8.0")
            implementation("com.github.luben:zstd-jni:1.5.5-5")
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.junit.jupiter:junit-jupiter:5.10.1")
            implementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
            implementation("org.mockito:mockito-core:5.8.0")
            implementation("org.mockito:mockito-junit-jupiter:5.8.0")
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.register("jsBrowserWebpack") {
    dependsOn("jsBrowserDevelopmentWebpack")
}