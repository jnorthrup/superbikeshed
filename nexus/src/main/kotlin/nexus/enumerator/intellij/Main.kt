package nexus.enumerator.intellij

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nexus.enumerator.intellij.project_model.IntelliJProjectDetails // Assuming duplicated model
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("intellij-project-enumerator")
    val projectPath by parser.option(
        ArgType.String,
        shortName = "p",
        fullName = "project-path",
        description = "Absolute path to the root directory of the IntelliJ project."
    ).required()

    // Note: kotlinx-cli automatically handles --help
    try {
        parser.parse(args)
    } catch (e: Exception) { // Catches help being printed, invalid args, etc.
        // kotlinx-cli prints help or error messages to stderr by default.
        // If help was requested, it exits with 0. For errors, it exits with non-zero.
        // We don't need to do much more here if relying on its default behavior.
        // However, to ensure a specific exit code for parsing *our* args:
        if (!args.contains("--help")) { // kotlinx-cli exits with 0 for --help
             System.err.println("Error parsing arguments: ${e.message}")
             exitProcess(1) // Generic argument error
        }
        return // Exit if help was shown or error occurred
    }


    val projectDir = File(projectPath)
    if (!projectDir.exists() || !projectDir.isDirectory) {
        System.err.println("Error: Project path '$projectPath' does not exist or is not a directory.")
        exitProcess(2) // Invalid project path
    }

    val ideaDir = File(projectDir, ".idea")
    if (!ideaDir.exists() || !ideaDir.isDirectory) {
        System.err.println("Error: '.idea' directory not found in project path '$projectPath'.")
        exitProcess(2) // Invalid project (missing .idea)
    }

    val projectParser = IntelliJProjectParser()
    val jsonOutput = Json {
        prettyPrint = true
        encodeDefaults = true // Ensure all fields, even with default values, are present
    }

    try {
        val projectDetails: IntelliJProjectDetails = projectParser.parseProject(projectPath)
        val jsonString = jsonOutput.encodeToString(projectDetails)
        println(jsonString) // Output to stdout
        exitProcess(0) // Success
    } catch (e: IllegalArgumentException) {
        System.err.println("Configuration error: ${e.message}")
        exitProcess(2) // Invalid path or critical config missing, as thrown by parser
    } catch (e: Exception) { // Catch other parsing or unexpected errors
        System.err.println("Error during project parsing: ${e.message}")
        e.printStackTrace(System.err) // For more detailed diagnostics
        exitProcess(3) // Parsing error
    }
}
