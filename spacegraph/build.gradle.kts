plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.github.ben-manes.versions")
    `maven-publish`
    signing
}

group = "spacegraph"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    jvm {
        // JVM target for graphics processing
    }
    wasmJs {
        browser()
        binaries.executable()
    }
=======
    kotlin("multiplatform")
    id("com.github.ben-manes.versions")
}

kotlin {
    jvmToolchain(21)
    jvm {
        // jvmToolchain(21) removed from here
    }
    
    js(IR) {
        browser()
        nodejs()
    }
    
>>>>>>> origin/feat/core-serialization-impl
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" -> {
            if (hostArch == "aarch64") {
                macosArm64()
            } else {
                macosX64()
            }
        }
        hostOs == "Linux" -> {
            if (hostArch == "aarch64") {
                linuxArm64()
            } else {
                linuxX64()
            }
        }
    }
<<<<<<< HEAD
=======
    
>>>>>>> origin/feat/core-serialization-impl
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
<<<<<<< HEAD
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlin("reflect"))
                implementation(libs.kotlinx.serialization.json)
=======
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
>>>>>>> origin/feat/core-serialization-impl
            }
        }
    }
}

tasks {
    register("buildAll") {
        dependsOn("build")
<<<<<<< HEAD
    }
=======
        dependsOn("jsBrowserProductionWebpack")
    }
    
    register("runJs") {
        dependsOn("jsBrowserDevelopmentRun")
    }
    
>>>>>>> origin/feat/core-serialization-impl
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${layout.buildDirectory.get()}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
            delete("${project.projectDir}/node_modules")
        }
    }
<<<<<<< HEAD
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            pom {
                name.set("SpaceGraph")
                description.set("SpaceGraph - Graphics and visualization library")
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
=======
} 
>>>>>>> origin/feat/core-serialization-impl
