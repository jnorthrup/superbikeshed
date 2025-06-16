package borg.dgm.polyglot

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TestSpec(
    val instanceId: String,
    val repo: String,
    val repoScriptList: List<String>,
    val evalScriptList: List<String>,
    val envScriptList: List<String>,
    val arch: String
) {
    val setupEnvScript: String
        get() = """
            #!/bin/bash
            set -euxo pipefail
            ${envScriptList.joinToString("\n")}
        """.trimIndent()

    val evalScript: String
        get() = """
            #!/bin/bash
            set -uxo pipefail
            ${evalScriptList.joinToString("\n")}
        """.trimIndent()

    val installRepoScript: String
        get() = """
            #!/bin/bash
            set -euxo pipefail
            ${repoScriptList.joinToString("\n")}
        """.trimIndent()

    val baseImageKey: String
        get() = "pb.base.$arch:latest"

    val envImageKey: String
        get() = "pb.env.$instanceId:latest"

    val instanceImageKey: String
        get() = "pb.instance.$instanceId:latest"

    fun getInstanceContainerName(runId: String): String =
        "pb-container-${instanceId.replace("__", "-")}-$runId"

    companion object {
        fun fromEntry(entry: Map<String, Any>): TestSpec {
            val instanceId = entry["instance_id"] as String
            val repo = entry["repo"] as String
            val language = entry["language"] as String

            // Get language-specific test commands
            val testCommands = when (language) {
                "python" -> listOf("pytest -rA --tb=long")
                "rust" -> listOf("cargo test -- --include-ignored")
                "go" -> listOf("go test ./...")
                "javascript" -> listOf(
                    "set -e",
                    "[ ! -e node_modules ] && ln -s /npm-install/node_modules .",
                    "[ ! -e package-lock.json ] && ln -s /npm-install/package-lock.json .",
                    "sed -i 's/\\bxtest(/test(/g' *.spec.js",
                    "npm run test"
                )
                "cpp" -> listOf(
                    "set -e",
                    "[ ! -d \"build\" ] && mkdir build",
                    "cd build",
                    "cmake -DEXERCISM_RUN_ALL_TESTS=1 -G \"Unix Makefiles\" ..",
                    "make",
                    "cd ../"
                )
                "java" -> listOf("./gradlew test")
                else -> throw IllegalArgumentException("Unsupported language: $language")
            }

            // Get language-specific environment setup
            val envSetup = when (language) {
                "python" -> listOf(
                    "python -m pip install --upgrade pip",
                    "pip install -r requirements.txt"
                )
                "rust" -> listOf(
                    "rustup update",
                    "cargo build"
                )
                "go" -> listOf(
                    "go mod download",
                    "go build ./..."
                )
                "javascript" -> listOf(
                    "npm install"
                )
                "cpp" -> listOf(
                    "apt-get update",
                    "apt-get install -y build-essential cmake"
                )
                "java" -> listOf(
                    "./gradlew build"
                )
                else -> throw IllegalArgumentException("Unsupported language: $language")
            }

            // Get repository setup commands
            val repoSetup = listOf(
                "git clone $repo .",
                "git checkout ${entry["commit"]}"
            )

            return TestSpec(
                instanceId = instanceId,
                repo = repo,
                repoScriptList = repoSetup,
                evalScriptList = testCommands,
                envScriptList = envSetup,
                arch = "linux/amd64" // Default to amd64, can be overridden
            )
        }
    }
} 