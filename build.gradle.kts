plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "org.superbikeshed"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = libs.versions.jvm.get()
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("buildAll") {
    dependsOn(
        ":Trikeshed:build",
        ":Trikeshed:trikeshed-core:build", 
        ":ta4k:build",
        ":moneyfan:build",
        ":Bao-Cline:build"
    )
    description = "Build all subprojects"
}

tasks.register("testAll") {
    dependsOn(
        ":Trikeshed:test",
        ":Trikeshed:trikeshed-core:test",
        ":ta4k:test", 
        ":moneyfan:test",
        ":Bao-Cline:test"
    )
    description = "Test all subprojects"
}

tasks.register("cleanAll") {
    dependsOn(
        ":Trikeshed:clean",
        ":Trikeshed:trikeshed-core:clean",
        ":ta4k:clean",
        ":moneyfan:clean", 
        ":Bao-Cline:clean"
    )
    description = "Clean all subprojects"
}