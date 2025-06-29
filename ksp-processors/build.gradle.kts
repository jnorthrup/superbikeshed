plugins {
    kotlin("jvm")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.2")
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.squareup:kotlinpoet-ksp:1.14.2")
    implementation(kotlin("stdlib"))
    implementation(project(":Trikeshed"))
}

// Configure META-INF/services for processor registration
tasks.register("generateProcessorService") {
    doLast {
        val servicesDir = file("src/main/resources/META-INF/services")
        servicesDir.mkdirs()

        val serviceFile = file("$servicesDir/com.google.devtools.ksp.processing.SymbolProcessorProvider")
        serviceFile.writeText("borg.trikeshed.ksp.TrikeShedDslProcessorProvider")
    }
}

tasks.named("processResources") {
    dependsOn("generateProcessorService")
}
