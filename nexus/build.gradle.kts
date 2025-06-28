plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "borg.nexus"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    jvm {
        // JVM only for now
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(project(":Trikeshed"))
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
                implementation(project(":Trikeshed"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check"
        )
    }
}

tasks.register<JavaExec>("runStandaloneNexus") {
    dependsOn("jvmJar")
    group = "application"
    description = "Run Standalone Nexus - Main()'s Pursuit of Happiness"
    mainClass.set("borg.trikeshed.nexus.StandaloneNexusKt")
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