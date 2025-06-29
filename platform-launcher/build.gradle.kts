import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // JNI for native integration
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    
    // GraalVM SDK for WASM support
    implementation("org.graalvm.sdk:graal-sdk:23.1.0")
    implementation("org.graalvm.truffle:truffle-api:23.1.0")
    implementation("org.graalvm.wasm:wasm:23.1.0")
    
    // For dynamic class loading
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.6")
    
    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlin.ExperimentalUnsignedTypes")
    }
}

application {
    mainClass.set("borg.trikeshed.launcher.PlatformLauncherKt")
}

// Native image configuration for GraalVM
tasks.register("nativeImageConfig") {
    doLast {
        val configDir = file("src/main/resources/META-INF/native-image")
        configDir.mkdirs()
        
        file("$configDir/native-image.properties").writeText("""
            Args = --no-fallback \
                   --initialize-at-build-time \
                   --enable-jni \
                   --enable-all-security-services \
                   -H:+JNI
        """.trimIndent())
    }
}

// Custom task for building native launcher
tasks.register<Exec>("buildNativeLauncher") {
    dependsOn("build")
    
    commandLine("native-image",
        "-cp", sourceSets["main"].runtimeClasspath.asPath,
        "-H:Name=platform-launcher",
        "-H:Class=borg.trikeshed.launcher.NativeLauncher",
        "-H:+JNI",
        "--no-fallback"
    )
}