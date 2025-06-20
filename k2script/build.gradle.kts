import java.util.Locale // Added for toLowerCase
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.jetbrains.kotlin.gradle.dsl.JvmTarget // Moved import to top

plugins {
    kotlin("jvm")
    application
    `maven-publish`
    signing
}

group = "io.github.kscripting"
version = "2.1.21"

repositories {
    mavenCentral()
}

application {
    mainClass.set("k2script.K2scriptKt")
}

val kotlinVersion = "2.1.21"

val createKscriptLayout by tasks.register<Copy>("createKscriptLayout") {
    from("src/main/resources") {
        into(".")
    }
    from("src/main/kotlin") {
        into(".")
    }
    from("src/main/kotlin") {
        into("bin")
    }
    from("wrappers") {
        into("wrappers")
    }
    from("setup.py")
    from("package.json")
    destinationDir = layout.buildDirectory.dir("kscript").get().asFile
}

val createK2scriptLayout by tasks.register<Copy>("createK2scriptLayout") {
    from("src/main/resources") {
        into(".")
    }
    from("src/main/kotlin") {
        into(".")
    }
    from("src/main/kotlin") {
        into("bin")
    }
    from("wrappers") {
        into("wrappers")
    }
    from("setup.py")
    from("package.json")
    destinationDir = layout.buildDirectory.dir("k2script").get().asFile
}

val packageK2scriptDistribution by tasks.register<Zip>("packageK2scriptDistribution") {
    dependsOn(createK2scriptLayout)
    from(layout.buildDirectory.dir("k2script")) {
        into("k2script-${project.version}")
    }
    archiveFileName.set("k2script-${project.version}-bin.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// TODO: Shadow/fat jar logic removed for Maven-centric build. Restore if needed for standalone distribution.

fun adjustVersion(archiveVersion: String): String {
    var newVersion = archiveVersion.lowercase(Locale.ROOT)
    val temporaryVersion = newVersion.substringBeforeLast(".")
    
    if (temporaryVersion.endsWith("-RC", true) || temporaryVersion.endsWith("-BETA", true) || temporaryVersion.endsWith("-ALPHA", true) ||
        temporaryVersion.endsWith("-SNAPSHOT", true)
    ) {
        newVersion = temporaryVersion.substringBeforeLast("-") + "-SNAPSHOT"
    }
    
    return newVersion
}

val jar: Jar by tasks.getting(Jar::class) {
    archiveVersion.set(adjustVersion(archiveVersion.get()))
}

// Disable distribution tasks that are not needed for Maven-centric build
tasks.withType<Tar> {
    enabled = false
}

tasks.withType<Zip> {
    if (name != "packageK2scriptDistribution") {
        enabled = false
    }
}

val test: Task by tasks.getting {
    inputs.dir("${project.projectDir}/test/resources")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name
            version = adjustVersion(project.version.toString())
            
            artifact(jar)
            
            pom {
                name.set("kscript")
                description.set("KScript - easy scripting with Kotlin")
                url.set("https://github.com/kscripting/kscript")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("holgerbrandl")
                        name.set("Holger Brandl")
                        email.set("holgerbrandl@gmail.com")
                    }
                    developer {
                        id.set("aartiPl")
                        name.set("Marcin Kuszczak")
                        email.set("aarti@interia.pl")
                    }
                }
                scm {
                    connection.set("scm:git:git://https://github.com/kscripting/kscript.git")
                    developerConnection.set("scm:git:ssh:https://github.com/kscripting/kscript.git")
                    url.set("https://github.com/kscripting/kscript")
                }
            }
        }
    }
    
    repositories {
        maven {
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            val adjustedVersion = adjustVersion(project.version.toString())
            url = uri(if (adjustedVersion.endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            
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

dependencies {
    implementation("commons-cli:commons-cli:1.5.0")
    
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven-all:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("com.konghq:unirest-java:3.14.2")
    
    implementation("net.igsoft:tablevis:0.6.0")
    implementation("io.github.kscripting:shell:0.5.2")
    
    implementation("org.slf4j:slf4j-nop:2.0.7")
    
    implementation("org.semver4j:semver4j:4.3.0")
    
    // Maven Resolver (Aether) for dependency resolution
    implementation("org.apache.maven.resolver:maven-resolver-impl:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-api:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-spi:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-util:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")
    implementation("org.apache.maven:maven-core:3.9.6")
    implementation("org.apache.maven:maven-model:3.9.6")
    implementation("org.apache.maven:maven-artifact:3.9.6")
    
    implementation(project(":Trikeshed"))
    
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.25")
    testImplementation("io.mockk:mockk:1.13.2")
    
    testImplementation(kotlin("script-runtime"))
}
