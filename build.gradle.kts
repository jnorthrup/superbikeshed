plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
    kotlin("multiplatform") version "1.9.22"
}
repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}
// Detect if a single target (e.g., JVM only) is requested via project property
val singleTarget: String? = findProperty("singleTarget") as String?

subprojects {
    // Propagate the singleTarget property to all subprojects
    extensions.extraProperties["singleTarget"] = singleTarget
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
// Root build file

// Build Configuration - Architectural Decision Records Integration
//
// ADR-001: SIMD Strategy Pattern - C interop configuration for native SIMD
// ADR-002: String Performance War - No String allocations in build scripts
//
// This build file implements the foundational configuration that supports both ADRs.

// ADR-001 Compliance: Platform targets for SIMD optimization
kotlin {
    macosArm64()  // Apple Silicon with NEON/AMX SIMD
    linuxX64()    // Linux x86 with SSE/AVX/AVX2 SIMD
    
    // DO NOT add more platform targets without justification
    // DO NOT remove existing platform targets
}

// ADR-002 Compliance: Use structured version management
// DO NOT use String concatenation in build scripts
// DO NOT use String-based version strings in child projects