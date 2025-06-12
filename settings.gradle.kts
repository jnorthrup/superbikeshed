pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/maven/maven") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        maven { url = uri("https://www.jetbrains.com/intellij-repository/releases/") }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/space-sdk/maven") }
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "superbikeshed"

// Core TrikeShed modules (temporarily disabled due to compilation issues)
// include(":Trikeshed")
// include(":Trikeshed:trikeshed-core")

// K2Script (temporarily disabled due to build issues)
// include(":k2script")

// TA4K trading library
include(":ta4k")

// MoneyFan 
include(":moneyfan")

// RTS Game (temporarily disabled - no build.gradle.kts)
// include(":rtsgame")

// SpaceGraph JS (temporarily disabled - checking structure)
// include(":spacegraphjs")
// include(":spacegraphjs:kotlin-spacegraph")

// Nexus (temporarily disabled - checking structure)
// include(":nexus")

// DGM (Darwin Gödel Machine) (temporarily disabled - Python project)
// include(":dgm")

// Bao-Cline
include(":Bao-Cline")

// Demo projects (temporarily disabled due to dependencies)
// include(":ta4k-spacegraph-moneyfan-demo")
