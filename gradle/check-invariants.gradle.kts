import java.security.MessageDigest

val checksumFile = rootProject.file(".gradle_files.checksums")
val gradleFiles = fileTree(rootProject.projectDir) {
    include("**/build.gradle*")
    include("**/settings.gradle*")
    include("**/libs.versions.toml")
}.files.sortedBy { it.path }

fun checksum(files: List<java.io.File>): String {
    val md = MessageDigest.getInstance("SHA-256")
    return files.joinToString("\n") { file ->
        val bytes = file.readBytes()
        val digest = md.digest(bytes).joinToString("") { "%02x".format(it) }
        "${file.relativeTo(rootProject.projectDir)}:$digest"
    }
}

val updateBaseline = project.hasProperty("updateBaseline") ||
    gradle.startParameter.taskNames.any { it.contains("--update-baseline") }

tasks.register("checkGradleInvariants") {
    group = "verification"
    description = "Fails if Gradle build files or targets change, or if dependency versions are present. Use --update-baseline to update baseline."

    doLast {
        val checksums = checksum(gradleFiles)
        if (!checksumFile.exists() || updateBaseline) {
            checksumFile.writeText(checksums)
            println("[INFO] Saved/updated Gradle file checksums baseline.")
        } else {
            val old = checksumFile.readText()
            if (old != checksums) {
                throw GradleException("[ERROR] Gradle build files or targets have changed! If intentional, run with --update-baseline to update baseline.")
            }
        }

        // Check for dependency versions
        val versionPattern = Regex(":\\d")
        gradleFiles.forEach { file ->
            if (file.readText().contains(versionPattern)) {
                throw GradleException("[ERROR] Dependency version found in ${file.path}. Use version catalogs instead.")
            }
        }
    }
} 