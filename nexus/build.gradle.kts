plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // id("com.google.devtools.ksp") version "2.1.21-2.0.2" // Temporarily disabled
    `maven-publish`
    signing
}

group = "borg.nexus"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    
    // Platform detection for native target
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                // implementation(project(":Trikeshed"))  // Temporarily disabled for pure functional system
                // implementation(project(":k2script"))   // Temporarily disabled for pure functional system
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("src/standalone/kotlin")
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlin("reflect"))
                implementation(libs.kotlinx.serialization.json)
                // implementation(project(":Trikeshed"))  // Temporarily disabled
                // implementation(project(":k2script"))   // Temporarily disabled
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

// KSP dependencies temporarily disabled during merge
// dependencies {
//     add("kspJvm", project(":ksp-processors"))
//     add("kspCommonMainMetadata", project(":ksp-processors"))
// }

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-source-roots-assertions"
        )
    }
}

tasks.register<JavaExec>("runStandaloneNexus") {
    dependsOn("jvmJar")
    group = "application"
    description = "Run Standalone Nexus - Main()'s Pursuit of Happiness"
    mainClass.set("nexus.standalone.MainStarter")
    classpath = files(
        tasks.named("jvmJar").get().outputs.files
    ) + (configurations["jvmRuntimeClasspath"] ?: files())
    jvmArgs = listOf(
        "-Xmx1g",
        "-XX:+UseG1GC"
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            pom {
                name.set("Nexus")
                description.set("Nexus - Standalone JVM tool")
                url.set("https://github.com/superbikeshed/superbikeshed")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("superbikeshed")
                        name.set("SuperBikeShed Team")
                        email.set("team@superbikeshed.org")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/superbikeshed/superbikeshed.git")
                    developerConnection.set("scm:git:ssh://github.com/superbikeshed/superbikeshed.git")
                    url.set("https://github.com/superbikeshed/superbikeshed")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = project.findProperty("sonatype.user") as String? ?: System.getenv("SONATYPE_USER")
                password = project.findProperty("sonatype.password") as String? ?: System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

// Configure JAR manifest for standalone execution
afterEvaluate {
    tasks.withType<Jar> {
        if (name == "jvmJar") {
            manifest {
                attributes["Main-Class"] = "nexus.standalone.MainStarter"
            }
        }
    }
}

gradle.buildFinished { buildResult ->
    if (buildResult.failure != null) {
        println("BUILD FAILED: Errors were detected during the build process.")
    } else {
        println("BUILD SUCCEEDED: No errors detected during the build process.")
    }
}

// IntelliJ Project Enumerator integration
// The code from tools/intellij-project-enumerator is now part of this build under src/main/kotlin/nexus/enumerator/intellij
// If additional dependencies are needed, add them here.
