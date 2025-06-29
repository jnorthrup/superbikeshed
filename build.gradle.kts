plugins {
    id("com.github.ben-manes.versions") version "0.51.0" apply false
<<<<<<< HEAD
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
=======
>>>>>>> origin/feat/core-serialization-impl
    kotlin("multiplatform") version "2.1.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
<<<<<<< HEAD
}

// KtLint disabled due to rule dependency issues - core compilation works fine
// subprojects {
//     apply(plugin = "org.jlleitschuh.gradle.ktlint")
// 
//     configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
//         version.set("1.0.1")
//         android.set(false)
//         ignoreFailures.set(true)
//         filter {
//             exclude { element -> element.file.path.contains("build/") }
//             exclude { element -> element.file.path.contains(".gradle/") }
//             exclude { element -> element.file.name.endsWith(".gradle.kts") }
//             exclude { element -> element.file.name.endsWith("gradle.properties") }
//         }
//         reporters {
//             reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
//             reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
//         }
//         ruleSets {
//             ruleSet("io.nlopez.compose.rules:ktlint:0.3.7")
//         }
//         additionalEditorconfig {
//             put("ktlint_standard_multiline-expression-wrapping", "disabled")
//             put("ktlint_standard_string-template-indent", "disabled")
//         }
//     }
// }
=======
} 
>>>>>>> origin/feat/core-serialization-impl
