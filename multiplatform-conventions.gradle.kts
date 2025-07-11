val enabledTargets = (rootProject.findProperty("enabledTargets") as? String)?.split(",")?.map { it.trim() } ?: listOf("jvm")

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    kotlin {
        applyEnabledTargets(enabledTargets)
    }
}

fun org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.applyEnabledTargets(enabledTargets: List<String>) {
    if ("jvm" in enabledTargets) jvm()
    if ("macosArm64" in enabledTargets) macosArm64()
    if ("linuxX64" in enabledTargets) linuxX64()
    // Add more as needed
} 