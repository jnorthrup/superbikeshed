plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    js {
        browser {
            webpackTask {
                mainOutputFileName = "trikeshed-core.js"
                outputDirectory = file("$buildDir/distributions")
            }
        }
        nodejs()
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