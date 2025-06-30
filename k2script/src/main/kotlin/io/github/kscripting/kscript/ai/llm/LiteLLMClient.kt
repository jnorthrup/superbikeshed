package kscript.ai.llm

import k2script.env.EnvironmentManager
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val stream: Boolean = false
)

@Serializable
data class LLMMessage(
    val role: String,
    val content: String
)

@Serializable
data class LLMResponse(
    val id: String? = null,
    val choices: List<LLMChoice> = emptyList(),
    val usage: LLMUsage? = null,
    val status: String = "success",
    val content: String = "",
    val error_message: String? = null
)

@Serializable
data class LLMChoice(
    val message: LLMMessage,
    val finish_reason: String? = null
)

@Serializable
data class LLMUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

class LiteLLMClient {
    companion object {
        private const val PORT_FILE_NAME = "litellm_port.txt"
        private const val PYTHON_SERVICE_DIR_NAME = "python_services"
        private const val SERVICE_SCRIPT_NAME = "litellm_service.py"
        private const val DEFAULT_PORT = 4000
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<LLMResponse>>()
    private val executor = Executors.newCachedThreadPool()

    private val portFilePath = Paths.get(EnvironmentManager.Java.getTempDir(), PORT_FILE_NAME)

    private val kscriptBaseDir: File by lazy {
        var resolvedDir: File? = null

        val jarLocation = this::class.java.protectionDomain.codeSource.location
        if (jarLocation != null) {
            val jarFile = File(jarLocation.toURI())
            resolvedDir = if (jarFile.isFile) jarFile.parentFile else jarFile
        }

        if (resolvedDir == null || !resolvedDir.resolve(PYTHON_SERVICE_DIR_NAME).exists()) {
             val kscriptDirProp = EnvironmentManager.getProperty("kscript.dir")
             if (kscriptDirProp != null) {
                 resolvedDir = File(kscriptDirProp).absoluteFile
             }
        }

        if (resolvedDir == null || !resolvedDir.resolve(PYTHON_SERVICE_DIR_NAME).exists()) {
            resolvedDir = File(".").absoluteFile
        }

        resolvedDir ?: throw IllegalStateException("Could not determine kscript base directory")
    }

    private val pythonServiceDir: File by lazy {
        kscriptBaseDir.resolve(PYTHON_SERVICE_DIR_NAME)
    }

    private val pythonScriptPath: File by lazy {
        pythonServiceDir.resolve(SERVICE_SCRIPT_NAME)
    }

    fun isServiceRunning(): Boolean {
        return try {
            val port = getServicePort() ?: return false
            Socket("localhost", port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun getServicePort(): Int? {
        return try {
            if (Files.exists(portFilePath)) {
                Files.readString(portFilePath).trim().toIntOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun startService(pythonExecutable: String = EnvironmentManager.Python.getCommand()): Boolean {
        if (isServiceRunning()) {
            println("LiteLLM Service is already running.")
            return true
        }

        if (!pythonScriptPath.exists()) {
            System.err.println("Error: LiteLLM Python service script not found at $pythonScriptPath")
            System.err.println("Determined kscriptBaseDir: ${kscriptBaseDir.absolutePath}")
            System.err.println("Determined pythonServiceDir: ${pythonServiceDir.absolutePath}")
            System.err.println("Working directory: ${EnvironmentManager.Java.getWorkingDir()}")
            System.err.println("kscript.dir system property: ${EnvironmentManager.getProperty("kscript.dir")}")
            return false
        }
        println("Found Python service script: $pythonScriptPath")

        return try {
            val processBuilder = ProcessBuilder(pythonExecutable, pythonScriptPath.absolutePath)
            processBuilder.directory(pythonServiceDir)
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            
            Thread.sleep(2000)
            
            if (process.isAlive && isServiceRunning()) {
                println("LiteLLM Service started successfully.")
                true
            } else {
                System.err.println("Failed to start LiteLLM Service.")
                false
            }
        } catch (e: Exception) {
            System.err.println("Error starting LiteLLM Service: ${e.message}")
            false
        }
    }

    fun stopService(): Boolean {
        return try {
            val port = getServicePort() ?: return true
            
            val url = "http://localhost:$port/shutdown"
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            if (Files.exists(portFilePath)) {
                Files.delete(portFilePath)
            }
            
            responseCode == 200
        } catch (e: Exception) {
            System.err.println("Error stopping LiteLLM Service: ${e.message}")
            false
        }
    }

    suspend fun sendRequest(request: LLMRequest): LLMResponse {
        if (!isServiceRunning() && !startService()) {
            return LLMResponse(
                status = "error",
                error_message = "LiteLLM Service is not available"
            )
        }

        val port = getServicePort() ?: return LLMResponse(
            status = "error", 
            error_message = "Could not determine service port"
        )

        return try {
            val requestJson = json.encodeToString(request)
            val url = "http://localhost:$port/v1/chat/completions"
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            connection.outputStream.use { os ->
                os.write(requestJson.toByteArray())
            }
            
            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            }
            
            connection.disconnect()
            
            if (responseCode == 200) {
                json.decodeFromString<LLMResponse>(responseText)
            } else {
                LLMResponse(
                    status = "error",
                    error_message = "HTTP $responseCode: $responseText"
                )
            }
        } catch (e: Exception) {
            LLMResponse(
                status = "error",
                error_message = "Request failed: ${e.message}"
            )
        }
    }
}
