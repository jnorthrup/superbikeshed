package org.v2superbikeshed.gradle

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.applyEnabledTargets(enabledTargets: List<String>) {
    if ("jvm" in enabledTargets) jvm()
    if ("macosArm64" in enabledTargets) macosArm64()
    if ("linuxX64" in enabledTargets) linuxX64()
    // Add more as needed
} 