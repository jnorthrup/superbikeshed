plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

kotlin {
    js {
        browser()
        nodejs()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Common dependencies
            }
        }
        
        val jsMain by getting {
            dependencies {
                // JS-specific dependencies
            }
        }
    }
}

tasks {
    register("buildAll") {
        dependsOn("build")
        dependsOn("jsBrowserProductionWebpack")
    }
    
    register("runJs") {
        dependsOn("jsBrowserDevelopmentRun")
    }
    
    // Clean task to remove all build artifacts
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${layout.buildDirectory.get()}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
            delete("${project.projectDir}/node_modules")
        }
    }
} 