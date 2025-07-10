plugins {
    kotlin("jvm")
    application
}

group = "borg.trikeshed"

application {
    mainClass.set("FiduciaryDemo")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

tasks.register<JavaExec>("runDemo") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("FiduciaryDemo")
    standardInput = System.`in`
}