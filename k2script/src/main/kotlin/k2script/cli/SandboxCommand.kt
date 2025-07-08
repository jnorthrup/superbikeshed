@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.cli

import k2script.platform.CommandResult
import k2script.platform.File
import k2script.platform.fileSystemOperations
import k2script.platform.processExecutor
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
data class SandboxMetadata(
    val id: String,
    val path: String,
    val type: String,
    val gitRepoUrl: String? = null,
    val gitBranch: String? = null,
    val dockerImageId: String? = null
)

object SandboxCommand {

    private val SANDBOX_METADATA_FILE = File(System.getProperty("user.home") + "/.k2script_sandboxes.json")

    suspend fun handle(args: Array<String>) {
        if (args.isEmpty()) {
            System.err.println("Error: Sandbox command required")
            System.err.println("Usage: k2script --sandbox <command> [args...]")
            System.err.println("Available commands: create, list, run, destroy")
            exitProcess(1)
        }

        val command = args[0]
        val commandArgs = args.drop(1).toTypedArray()

        when (command) {
            "create" -> createSandbox(commandArgs)
            "list" -> listSandboxes()
            "run" -> runSandbox(commandArgs)
            "destroy" -> destroySandbox(commandArgs)
            else -> {
                System.err.println("Unknown sandbox command: $command")
                System.err.println("Available commands: create, list, run, destroy")
                exitProcess(1)
            }
        }
    }

    private suspend fun createSandbox(args: Array<String>) {
        val fileSystem = coroutineContext.fileSystemOperations
        val processExecutor = coroutineContext.processExecutor

        if (args.size < 2 || args[0] != "--git") {
            System.err.println("Error: Missing --git <repo_url> <branch_name> for sandbox creation.")
            System.err.println("Usage: k2script --sandbox create --git <repo_url> <branch_name> [--docker]")
            exitProcess(1)
        }

        val repoUrl = args[1]
        val branchName = args[2]
        val useDocker = args.contains("--docker")

        println("Creating Git sandbox for $repoUrl on branch $branchName (Docker: $useDocker)...")

        // Create a temporary directory for the clone
        val tempDir = fileSystem.createTempDir(prefix = "k2script-sandbox-")
        println("Cloning repository into: ${tempDir.absolutePath}")

        // Perform a solid clone
        val gitCloneCommand = "git clone --depth 1 --branch $branchName $repoUrl ${tempDir.absolutePath}"
        val result = processExecutor.runCommand(gitCloneCommand)

        if (result.exitCode != 0) {
            System.err.println("Error cloning repository: ${result.stderr}")
            fileSystem.deleteRecursively(tempDir) // Clean up on failure
            exitProcess(1)
        }

        println("Repository cloned successfully.")

        var dockerImageId: String? = null
        if (useDocker) {
            println("Building Docker image...")
            val dockerfileContent = """
                FROM openjdk:17-jdk-slim
                WORKDIR /app
                COPY . .
                RUN chmod +x gradlew
                CMD ["./gradlew", "run"]
            """.trimIndent()
            
            val dockerfile = File(tempDir.absolutePath + "/Dockerfile")
            fileSystem.writeText(dockerfile, dockerfileContent)

            val buildResult = processExecutor.runCommand("docker build -t k2script-sandbox-${tempDir.name} .", tempDir)
            if (buildResult.exitCode != 0) {
                System.err.println("Error building Docker image: ${buildResult.stderr}")
                fileSystem.deleteRecursively(tempDir)
                exitProcess(1)
            }
            dockerImageId = "k2script-sandbox-${tempDir.name}"
            println("Docker image built: $dockerImageId")
        }

        val sandboxId = tempDir.name
        val metadata = SandboxMetadata(
            id = sandboxId,
            path = tempDir.absolutePath,
            type = if (useDocker) "docker-git" else "git",
            gitRepoUrl = repoUrl,
            gitBranch = branchName,
            dockerImageId = dockerImageId
        )
        saveSandboxMetadata(metadata)

        println("Sandbox created at: ${tempDir.absolutePath}")
        println("To update hourly, consider setting up a cron job to run: k2script --sandbox update $sandboxId")
    }

    private suspend fun listSandboxes() {
        val sandboxes = loadAllSandboxMetadata()
        if (sandboxes.isEmpty()) {
            println("No sandboxes found.")
            return
        }
        println("Existing Sandboxes:")
        sandboxes.forEach { sandbox ->
            println("  ID: ${sandbox.id}")
            println("    Path: ${sandbox.path}")
            println("    Type: ${sandbox.type}")
            sandbox.gitRepoUrl?.let { println("    Git Repo: $it") }
            sandbox.gitBranch?.let { println("    Git Branch: $it") }
            sandbox.dockerImageId?.let { println("    Docker Image: $it") }
            println("--------------------")
        }
    }

    private suspend fun runSandbox(args: Array<String>) {
        val processExecutor = coroutineContext.processExecutor

        if (args.isEmpty()) {
            System.err.println("Error: Sandbox ID required to run.")
            System.err.println("Usage: k2script --sandbox run <sandbox_id>")
            exitProcess(1)
        }
        val sandboxId = args[0]
        val metadata = loadSandboxMetadata(sandboxId)

        if (metadata == null) {
            System.err.println("Error: Sandbox with ID '$sandboxId' not found.")
            exitProcess(1)
        }

        println("Running sandbox '$sandboxId'...")
        when (metadata.type) {
            "git" -> {
                println("Entering sandbox directory: ${metadata.path}")
                // For a simple git sandbox, we just change directory and inform the user.
                // A more advanced 'run' might execute a default script or open a shell.
                println("You can now navigate to ${metadata.path} to work within the sandbox.")
            }
            "docker-git" -> {
                metadata.dockerImageId?.let { imageId ->
                    println("Running Docker container from image: $imageId")
                    val runResult = processExecutor.runCommand("docker run --rm -it $imageId", File(metadata.path))
                    if (runResult.exitCode != 0) {
                        System.err.println("Error running Docker container: ${runResult.stderr}")
                    }
                } ?: run {
                    System.err.println("Error: Docker image ID not found for sandbox '$sandboxId'.")
                }
            }
            else -> {
                System.err.println("Unsupported sandbox type: ${metadata.type}")
            }
        }
    }

    private suspend fun destroySandbox(args: Array<String>) {
        val fileSystem = coroutineContext.fileSystemOperations
        val processExecutor = coroutineContext.processExecutor

        if (args.isEmpty()) {
            System.err.println("Error: Sandbox ID required to destroy.")
            System.err.println("Usage: k2script --sandbox destroy <sandbox_id>")
            exitProcess(1)
        }
        val sandboxId = args[0]
        val metadata = loadSandboxMetadata(sandboxId)

        if (metadata == null) {
            System.err.println("Error: Sandbox with ID '$sandboxId' not found.")
            exitProcess(1)
        }

        println("Destroying sandbox '$sandboxId'...")

        // Remove Docker image if it exists
        metadata.dockerImageId?.let { imageId ->
            println("Removing Docker image: $imageId")
            val rmImageResult = processExecutor.runCommand("docker rmi $imageId")
            if (rmImageResult.exitCode != 0) {
                System.err.println("Warning: Failed to remove Docker image $imageId: ${rmImageResult.stderr}")
            }
        }

        // Delete the temporary directory
        val sandboxDir = File(metadata.path)
        if (fileSystem.fileExists(sandboxDir)) {
            println("Deleting sandbox directory: ${sandboxDir.absolutePath}")
            fileSystem.deleteRecursively(sandboxDir)
        }

        removeSandboxMetadata(sandboxId)
        println("Sandbox '$sandboxId' destroyed successfully.")
    }

    private suspend fun saveSandboxMetadata(metadata: SandboxMetadata) {
        val fileSystem = coroutineContext.fileSystemOperations
        val allSandboxes = loadAllSandboxMetadata().toMutableList()
        allSandboxes.removeAll { it.id == metadata.id } // Remove old entry if exists
        allSandboxes.add(metadata)
        fileSystem.writeText(SANDBOX_METADATA_FILE, Json.encodeToString(allSandboxes))
    }

    private suspend fun loadSandboxMetadata(id: String): SandboxMetadata? {
        return loadAllSandboxMetadata().firstOrNull { it.id == id }
    }

    private suspend fun loadAllSandboxMetadata(): List<SandboxMetadata> {
        val fileSystem = coroutineContext.fileSystemOperations
        if (!fileSystem.fileExists(SANDBOX_METADATA_FILE)) {
            return emptyList()
        } else {
            try {
                return Json.decodeFromString(fileSystem.readText(SANDBOX_METADATA_FILE))
            } catch (e: Exception) {
                System.err.println("Warning: Could not read sandbox metadata file. Creating new one. Error: ${e.message}")
                fileSystem.deleteRecursively(SANDBOX_METADATA_FILE) // Corrupted file, delete it
                return emptyList()
            }
        }
    }

    private suspend fun removeSandboxMetadata(id: String) {
        val fileSystem = coroutineContext.fileSystemOperations
        val allSandboxes = loadAllSandboxMetadata().toMutableList()
        allSandboxes.removeAll { it.id == id }
        fileSystem.writeText(SANDBOX_METADATA_FILE, Json.encodeToString(allSandboxes))
    }
}
