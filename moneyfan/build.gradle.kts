plugins {
    kotlin("multiplatform") version "2.1.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
    wasmJs {
        browser()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("com.ionspin.kotlin:bignum:0.3.10")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.junit.jupiter:junit-jupiter:5.9.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-jvm"))
            }
        }
        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
        val wasmJsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val wasmJsTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

dependencies {
    // implementation(kotlin("stdlib")) // Moved to source sets
    // testImplementation("org.junit.jupiter:junit-jupiter:5.9.2") // Moved to commonTest
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2") // Moved to commonTest
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2") // Moved to commonTest
}