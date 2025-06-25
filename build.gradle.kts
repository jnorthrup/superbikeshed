plugins {
    id("com.github.ben-manes.versions") version "0.51.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
    kotlin("multiplatform") version "2.1.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.0.1")
        android.set(false)
        ignoreFailures.set(false)
        filter {
            exclude { element -> element.file.path.contains("build/") }
            exclude { element -> element.file.path.contains(".gradle/") }
        }
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
    }
}
