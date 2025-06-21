package io.github.kscripting.kscript

import io.github.kscripting.kscript.code.Templates
import io.github.kscripting.kscript.creator.DebugInfoCreator
import io.github.kscripting.kscript.model.ConfigBuilder
import io.github.kscripting.kscript.resolver.CommandResolver
import io.github.kscripting.kscript.util.Executor
import io.github.kscripting.kscript.util.Logger
import io.github.kscripting.kscript.util.Logger.errorMsg
import io.github.kscripting.kscript.util.Logger.info
import io.github.kscripting.kscript.util.OptionsUtils
import io.github.kscripting.kscript.util.VersionChecker
import io.github.kscripting.shell.model.OsType
import java.nio.file.Paths
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.ParseException
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking


/**
 * A kscript - Scripting enhancements for Kotlin
 *
 * For details and license see https://github.com/kscripting/kscript
 *
 * @author Holger Brandl
 * @author Marcin Kuszczak
 */

fun main(args: Array<String>) {
    try {
        val config = ConfigBuilder(
            OsType.findOrThrow(args[0]), System.getProperties(), System.getenv()
        ).build()

        // note: first argument has to be OSTYPE
        val remainingArgs = args.drop(1)

        // note: with current implementation we still don't support `kscript -1` where "-1" is a valid kotlin expression
        val userArgs = remainingArgs.dropWhile { it.startsWith("-") && it != "-" }.drop(1)
        val kscriptArgs = remainingArgs.take(remainingArgs.size - userArgs.size)

        val parser: CommandLineParser = DefaultParser()
        val options = OptionsUtils.createOptions()

        val parsedLine = try {
            parser.parse(options, kscriptArgs.toTypedArray())
        } catch (e: ParseException) {
            info(OptionsUtils.createHelpText(config.osConfig.selfName, options))
            throw IllegalArgumentException(e.message)
        }

        val parsedOptions = parsedLine.options.associate { it.longOpt to it.value }.toMutableMap()

        if (parsedLine.argList.isNotEmpty()) {
            parsedOptions["script"] = parsedLine.argList[0]
        }

        // Constraints
        if (parsedOptions.isEmpty() || (parsedOptions.containsKey("interactive") && !parsedOptions.containsKey("script"))) {
            info(OptionsUtils.createHelpText(config.osConfig.selfName, options))
            throw IllegalArgumentException("KScript requires 'script' as an argument")
        }

        Logger.silentMode = parsedOptions.containsKey("silent")
        Logger.devMode = parsedOptions.containsKey("development")

        if (Logger.devMode) {
            info(DebugInfoCreator().create(config, kscriptArgs, userArgs))
        }

        val executor = Executor(CommandResolver(config.osConfig))

        if (parsedOptions.containsKey("help") || parsedOptions.containsKey("version")) {
            val versionChecker = VersionChecker(executor)

            val newVersion = if (versionChecker.isThereANewKscriptVersion()) versionChecker.remoteKscriptVersion else ""

            if (parsedOptions.containsKey("help")) {
                info(OptionsUtils.createHelpText(config.osConfig.selfName, options).trimEnd())
            }

            info(
                Templates.createVersionInfo(
                    BuildConfig.APP_BUILD_TIME,
                    BuildConfig.APP_VERSION,
                    newVersion,
                    versionChecker.localKotlinVersion,
                    versionChecker.localJreVersion
                )
            )
            info()

            return
        }

        // Handle --ai flag for AI-powered features
        if (parsedOptions.containsKey("ai")) {
            val aiPrompt = parsedOptions["ai"]
            val scriptContent = parsedOptions["script"]
            
            if (aiPrompt == null || aiPrompt.isBlank()) {
                errorMsg("The --ai flag requires a prompt argument.")
                exitProcess(1)
            }
            
            try {
                // Import LiteLLMClient for AI functionality
                val liteLLMClient = kscript.ai.llm.LiteLLMClient()
                
                val aiResponse = if (scriptContent != null && scriptContent.isNotBlank()) {
                    // Script explanation mode
                    val scriptFile = java.io.File(scriptContent)
                    val scriptText = if (scriptFile.exists()) {
                        scriptFile.readText()
                    } else {
                        scriptContent // Treat as direct script content
                    }
                    
                    val request = kscript.ai.llm.LLMRequest(
                        model = "gpt-3.5-turbo",
                        messages = listOf(
                            kscript.ai.llm.LLMMessage("system", "You are a Kotlin script expert. Explain the provided script clearly and concisely."),
                            kscript.ai.llm.LLMMessage("user", "Explain this Kotlin script:\n\n$scriptText")
                        ),
                        max_tokens = 1000
                    )
                    
                    // This needs to be async, but we'll handle it synchronously for CLI
                    runBlocking {
                        liteLLMClient.sendRequest(request)
                    }
                } else {
                    // Script generation mode
                    val request = kscript.ai.llm.LLMRequest(
                        model = "gpt-3.5-turbo",
                        messages = listOf(
                            kscript.ai.llm.LLMMessage("system", "You are a Kotlin script generator. Create clean, well-documented Kotlin scripts based on user requirements."),
                            kscript.ai.llm.LLMMessage("user", aiPrompt)
                        ),
                        max_tokens = 2000
                    )
                    
                    runBlocking {
                        liteLLMClient.sendRequest(request)
                    }
                }
                
                if (aiResponse.status == "success" && aiResponse.choices.isNotEmpty()) {
                    info(aiResponse.choices[0].message.content)
                } else {
                    errorMsg("AI request failed: ${aiResponse.error_message ?: "Unknown error"}")
                    exitProcess(1)
                }
                
                return
            } catch (e: Exception) {
                errorMsg("Error with AI functionality: ${e.message}")
                exitProcess(1)
            }
        }

        // Handle --export-to-gradle-project
        if (parsedOptions.containsKey("export-to-gradle-project")) {
            val outputDirPathValue = parsedOptions["export-to-gradle-project"]
            val scriptPathValue = parsedOptions["script"] // Script path is taken from remaining args

            // Basic validation
            if (scriptPathValue == null || scriptPathValue.isBlank()) {
                errorMsg("The <script> argument is required for --export-to-gradle-project.")
                exitProcess(1)
            }
            if (outputDirPathValue == null || outputDirPathValue.isBlank()) {
                errorMsg("The <output_dir> argument for --export-to-gradle-project is required.")
                exitProcess(1)
            }

            val scriptFile = java.nio.file.Paths.get(scriptPathValue).toFile()
            if (!scriptFile.exists()) {
                errorMsg("Script file not found: $scriptPathValue")
                exitProcess(1)
            }

            // scriptPathValue is already validated to be non-null/blank
            val outputDir = Paths.get(outputDirPathValue!!)

            try {
                io.github.kscripting.kscript.generator.exportToGradleProject(
                    scriptFilePathString = scriptPathValue,
                    outputDir = outputDir,
                    cliOptions = parsedOptions.toMap(),
                    config = config
                )
                info("Gradle project export process finished for '$scriptPathValue' to '$outputDirPathValue'.")
                exitProcess(0)
            } catch (e: Exception) {
                errorMsg("Error during --export-to-gradle-project for script '$scriptPathValue': ${e.message}")
                e.printStackTrace() // For detailed debugging during development
                exitProcess(1)
            }
        }

        KscriptHandler(executor, config, parsedOptions).handle(userArgs)
    } catch (e: Exception) {
        errorMsg(e)
        info()
        exitProcess(1)
    }
}
