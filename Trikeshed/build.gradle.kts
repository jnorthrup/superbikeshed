plugins {
    kotlin("multiplatform") version "2.1.21"
    `maven-publish`
}


group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }
    
    // Support all common targets that dependents might need
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // implementation(project(":brokeshed"))  // Temporarily disabled
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            }
        }
        
        val posixMain by creating {
            dependsOn(commonMain)
        }

        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().all {
            compilations["main"].defaultSourceSet.dependsOn(posixMain)
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlin:kotlin-test-common")
            implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        }
        
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
        }

        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
                implementation(kotlin("reflect"))
            }
        }
    }
}

// Disable linting to keep code terse
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-source-roots-assertions",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
}