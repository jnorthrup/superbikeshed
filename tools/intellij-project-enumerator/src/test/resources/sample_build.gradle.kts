plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "com.example.gradlekts"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation(kotlin("test"))
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.17.1")

    // Map notation
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.13.0")

    // Platform/BOM
    implementation(platform("org.http4k:http4k-bom:4.25.0.0"))
    implementation("org.http4k:http4k-core") // Version from BOM
}

application {
    mainClass.set("com.example.MainKt")
}
