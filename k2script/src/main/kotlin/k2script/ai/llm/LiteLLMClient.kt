@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.ai.llm

import k2script.env.EnvironmentManager
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

// Simple data classes for request and response (manual JSON handling for PoC)
// In a real scenario, kotlinx.serialization would be used.
data class LLMRequest(
    val id: String,
    val model: String,
    val messages: List<Map<String, String>>,
    val api_key: String? = null,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val base_url: String? = null,
    val custom_llm_provider: String? = null,
    val stream: Boolean? = null,
    val timeout: Int? = null
)

data class LLMResponse(
    val id: String,
    val status: String,
    val content: String?,
    val raw_response: String?, // Keep as string for PoC, could be parsed further
    val error_message: String?
)

object LiteLLMClient {
    internal const val PYTHON_SERVICE_DIR_NAME = "python_litellm_service"
    internal const val PYTHON_SERVICE_SCRIPT_NAME = "litellm_service.py"
    internal const val PORT_FILE_NAME = "k2script_litellm_port.txt"

    internal var pythonProcess: Process? = null
    internal var clientSocket: Socket? = null
    internal var writer: BufferedWriter? = null
    internal var readerThread: Thread? = null
    internal var port: Int = -1

    internal val requestCounter = AtomicLong(0)
    internal val pendingRequests = ConcurrentHashMap<String, CompletableFuture<LLMResponse>>()
    internal val executor = Executors.newCachedThreadPool() // For CompletableFuture async operations

    internal val portFilePath = Paths.get(EnvironmentManager.Java.getTempDir(), PORT_FILE_NAME)

    // Determine the directory of the kscript.jar or execution context
    internal val kscriptBaseDir: File by lazy {
        var resolvedDir: File? = null
        try {
            // Attempt 1: Protection Domain (works when run from JAR)
            val protectionDomain = LiteLLMClient::class.java.protectionDomain
            val codeSource = protectionDomain?.codeSource
            if (codeSource != null) {
                val jarFile = File(codeSource.location.toURI())
                if (jarFile.isFile) { // Running from a JAR
                    resolvedDir = jarFile.parentFile // Directory containing the JAR
                } else { // Running from classes (e.g. IDE or gradle run)
                    resolvedDir = jarFile
                }
            }
        } catch (e: Exception) {
            // Fallback if protection domain fails
        }

        if (resolvedDir == null || !resolvedDir.resolve(PYTHON_SERVICE_DIR_NAME).exists()) {
             // Attempt 2: kscript.dir system property
             val kscriptDirProp = EnvironmentManager.getProperty("kscript.dir")
             if (kscriptDirProp != null) {
                 resolvedDir = File(kscriptDirProp).absoluteFile
             }
        }

        if (resolvedDir == null || !resolvedDir.resolve(PYTHON_SERVICE_DIR_NAME).exists()) {
             // Attempt 3: Current working directory
             resolvedDir = File(".").absoluteFile
        }

        // If resolvedDir is 'k2script/build/classes/kotlin/main', go up to 'k2script'
        if (resolvedDir?.endsWith("build/classes/kotlin/main") == true) {
            resolvedDir = resolvedDir?.parentFile?.parentFile?.parentFile?.parentFile
        } else if (resolvedDir?.endsWith("build/libs") == true) { // If kscript.jar is in build/libs
             resolvedDir = resolvedDir?.parentFile?.parentFile // Go up to k2script project root
        }


        // Check if PYTHON_SERVICE_DIR_NAME is a direct child of the current resolvedDir
        // This implies resolvedDir is the 'k2script' module root.
        if (resolvedDir?.resolve(PYTHON_SERVICE_DIR_NAME)?.exists() == true) {
             resolvedDir
        } else {
             // If not, assume resolvedDir might be the parent of 'k2script' (e.g. monorepo root)
             // and try to find 'k2script/python_litellm_service'
             val monoRepoPath = resolvedDir?.resolve("k2script")
             if (monoRepoPath?.resolve(PYTHON_SERVICE_DIR_NAME)?.exists() == true) {
                 monoRepoPath // This should be k2script directory
             } else {
                 // Final fallback to current dir, though error will likely occur in startService
                 File(".").absoluteFile
             }
        }!! // Ensure resolvedDir is not null by this point, though logic should handle it
    }

    internal val pythonServiceDir by lazy {
        // kscriptBaseDir should now point to the 'k2script' directory itself or where 'python_litellm_service' is a child
        kscriptBaseDir.resolve(PYTHON_SERVICE_DIR_NAME).absoluteFile
    }
    internal val pythonScriptPath by lazy { pythonServiceDir.resolve(PYTHON_SERVICE_SCRIPT_NAME).absolutePath }


    @Synchronized
    fun startService(pythonExecutable: String = EnvironmentManager.Python.getCommand()): Boolean {
        if (isServiceRunning()) {
            println("LiteLLM Service is already running.")
            return true
        }

        println("Attempting to find Python service script at: $pythonScriptPath")
        if (!File(pythonScriptPath).exists()) {
            System.err.println("Error: LiteLLM Python service script not found at $pythonScriptPath")
            System.err.println("Determined kscriptBaseDir: ${kscriptBaseDir.absolutePath}")
            System.err.println("Determined pythonServiceDir: ${pythonServiceDir.absolutePath}")
            System.err.println("Working directory: ${File(".").absolutePath}")
            System.err.println("kscript.dir system property: ${EnvironmentManager.getProperty("kscript.dir")}")
            return false
        }
        println("Found Python service script: $pythonScriptPath")


        try {
            println("Starting LiteLLM Python service using command: [$pythonExecutable, $pythonScriptPath] in dir ${pythonServiceDir.absolutePath}")
            val command = listOf(pythonExecutable, pythonScriptPath)
            val processBuilder = ProcessBuilder(command)
                .directory(pythonServiceDir)
                .redirectErrorStream(true)

            pythonProcess = processBuilder.start()

            thread(isDaemon = true, name = "PythonServiceOutputReader") {
                pythonProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                    println("[LiteLLM Service Log]: $line")
                }
            }

            val maxWaitMillis = 10000L
            val startTime = System.currentTimeMillis()
            var portFileFound = false
            while (System.currentTimeMillis() - startTime < maxWaitMillis) {
                if (Files.exists(portFilePath)) {
                    val portString = Files.readAllLines(portFilePath).firstOrNull()
                    if (!portString.isNullOrBlank()){
                        port = portString.toIntOrNull() ?: -1
                        if (port != -1) {
                            portFileFound = true
                            break
                        }
                    }
                }
                Thread.sleep(200)
            }

            if (!portFileFound || port == -1) {
                System.err.println("Error: LiteLLM Python service did not report port in time at $portFilePath.")
                stopService()
                return false
            }

            println("LiteLLM Python service reported port: $port. Connecting...")
            clientSocket = Socket("localhost", port)
            writer = clientSocket!!.getOutputStream().bufferedWriter()

            readerThread = thread(isDaemon = true, name = "LiteLLMClientReader") {
                try {
                    clientSocket!!.getInputStream().bufferedReader().forEachLine { line ->
                        handleResponse(line)
                    }
                } catch (e: IOException) {
                    if (isServiceRunning()) {
                        System.err.println("LiteLLMClient: Connection error in reader thread: ${e.message}")
                    }
                } finally {
                     if (isServiceRunning()) {
                         println("LiteLLMClient: Reader thread exited. Assuming connection lost.")
                         stopService()
                     }
                }
            }
            println("LiteLLMClient: Connected to Python service.")
            return true
        } catch (e: Exception) {
            System.err.println("Error starting LiteLLM Python service or connecting: ${e.message}")
            e.printStackTrace()
            stopService()
            return false
        }
    }

    @Synchronized
    fun stopService() {
        println("Stopping LiteLLM Service...")
        try {
            readerThread?.interrupt()
            writer?.close()
            clientSocket?.close()
        } catch (e: IOException) {
            // Ignore errors during close, as resources might already be closed
        } finally {
            clientSocket = null
            writer = null
            readerThread = null
        }

        pythonProcess?.let {
            if (it.isAlive) {
                println("Destroying LiteLLM Python process...")
                it.destroy()
                if (!it.waitFor(3, TimeUnit.SECONDS)) {
                     println("LiteLLM Python process did not terminate gracefully, forcing.")
                     it.destroyForcibly()
                     it.waitFor(2, TimeUnit.SECONDS)
                }
                if (it.isAlive){
                    System.err.println("Python process could not be terminated.")
                }
            }
        }
        pythonProcess = null

        try {
            Files.deleteIfExists(portFilePath)
        } catch (e: IOException) {
            System.err.println("Error deleting port file $portFilePath: ${e.message}")
        }
        port = -1

        pendingRequests.forEach { (_, future) ->
            if (!future.isDone) {
                future.completeExceptionally(IOException("LiteLLM Service stopped."))
            }
        }
        pendingRequests.clear()
        println("LiteLLM Service stopped.")
    }

    fun isServiceRunning(): Boolean = pythonProcess?.isAlive == true && clientSocket?.isConnected == true && clientSocket?.isClosed == false


    internal fun handleResponse(jsonResponse: String) {
        try {
            // Basic manual JSON parsing for PoC.
            val idMatch = """"id":\s*"([^"]+)"""".toRegex().find(jsonResponse)
            val statusMatch = """"status":\s*"([^"]+)"""".toRegex().find(jsonResponse)
            val contentMatch = """"content":\s*((?:null)|(?:"(?:[^"\\\\]|\\\\.)*"))""".toRegex().find(jsonResponse)
            val rawResponseJsonMatch = """"raw_response":\s*((?:null)|(?:"(?:[^"\\\\]|\\\\.)*"))""".toRegex().find(jsonResponse)
            val errorMatch = """"error_message":\s*((?:null)|(?:"(?:[^"\\\\]|\\\\.)*"))""".toRegex().find(jsonResponse)


            val id = idMatch?.groupValues?.get(1)
            val future = if (id != null) pendingRequests.remove(id) else null

            if (future == null) {
                if (id != null) System.err.println("LiteLLMClient: Received response for unknown or timed-out request ID: $id")
                return
            }

            fun unescapeJsonString(str: String?): String? {
                 return str?.removeSurrounding("\"")
                            ?.replace("\\\"", "\"")
                            ?.replace("\\\\", "\\")
                            ?.replace("\\n", "\n")
                            ?.replace("\\r", "\r")
                            ?.replace("\\t", "\t")
                            ?.takeIf { it != "null" }
            }

            val status = statusMatch?.groupValues?.get(1) ?: "error"
            val content = unescapeJsonString(contentMatch?.groupValues?.get(1))
            val rawResponse = unescapeJsonString(rawResponseJsonMatch?.groupValues?.get(1))
            val errorMessage = unescapeJsonString(errorMatch?.groupValues?.get(1))

            val response = LLMResponse(id!!, status, content, rawResponse, errorMessage)

            if (response.status == "success") {
                future.complete(response)
            } else {
                future.completeExceptionally(IOException("LiteLLM Error: ${response.error_message ?: "Unknown error from Python service."} (ID: $id)"))
            }
        } catch (e: Exception) {
            System.err.println("LiteLLMClient: Error parsing response '$jsonResponse': ${e.message}")
            e.printStackTrace()
        }
    }

    fun complete(
        model: String,
        messages: List<Map<String, String>>,
        apiKey: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        baseUrl: String? = null,
        customLlmProvider: String? = null,
        stream: Boolean? = null,
        requestTimeoutSeconds: Long = 60
    ): CompletableFuture<LLMResponse> {
        if (!isServiceRunning()) {
            println("LiteLLMClient: Service not running. Attempting to start...")
            if (!startService()) {
                return CompletableFuture.failedFuture(IOException("LiteLLM service could not be started."))
            }
            Thread.sleep(500)
            if (!isServiceRunning()) {
                 return CompletableFuture.failedFuture(IOException("LiteLLM service failed to start or connect properly after delay."))
            }
        }

        val requestId = "req-${requestCounter.incrementAndGet()}"

        fun escapeJsonValue(value: Any?): String {
             return when (value) {
                 is String -> "\"${value.replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                        .replace("\t", "\\t")}\""
                 is Number, is Boolean -> value.toString()
                 null -> "null"
                 else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
             }
        }

        val messagesJson = messages.joinToString(",") { msg ->
             val role = escapeJsonValue(msg["role"])
             val contentVal = escapeJsonValue(msg["content"])
             "{\"role\":$role,\"content\":$contentVal}"
        }

        val jsonRequest = buildString {
            append("{")
            append("\"id\":\"$requestId\",")
            append("\"model\":${escapeJsonValue(model)}," )
            append("\"messages\":[$messagesJson]," )
            apiKey?.let { append("\"api_key\":${escapeJsonValue(it)}," ) }
            temperature?.let { append("\"temperature\":$it," ) }
            maxTokens?.let { append("\"max_tokens\":$it," ) }
            baseUrl?.let { append("\"base_url\":${escapeJsonValue(it)}," ) }
            customLlmProvider?.let { append("\"custom_llm_provider\":${escapeJsonValue(it)}," ) }
            stream?.let { append("\"stream\":$it," ) }
            val pythonTimeout = (requestTimeoutSeconds * 0.9).toInt().takeIf { it > 0 }
            pythonTimeout?.let { append("\"timeout\":$it," ) }
            if (endsWith(',')) deleteCharAt(length -1)
            append("}")
        }

        val future = CompletableFuture<LLMResponse>()
        pendingRequests[requestId] = future

        try {
            // println("LiteLLMClient sending: $jsonRequest")
            writer?.write(jsonRequest + "\n")
            writer?.flush()
        } catch (e: IOException) {
            System.err.println("LiteLLMClient: Error sending request $requestId: ${e.message}")
            pendingRequests.remove(requestId)
            future.completeExceptionally(e)
            if (!isServiceRunning()) {
                 println("LiteLLMClient: Send error detected service is not running. Attempting to stop.")
                 stopService()
            }
        }

        executor.submit {
            try {
                if (!future.isDone) {
                    future.get(requestTimeoutSeconds, TimeUnit.SECONDS)
                }
            } catch (e: java.util.concurrent.TimeoutException) {
                if (!future.isDone) {
                    System.err.println("LiteLLMClient: Request $requestId timed out on Kotlin side after $requestTimeoutSeconds seconds.")
                    future.completeExceptionally(IOException("Request $requestId timed out", e))
                    pendingRequests.remove(requestId)
                }
            } catch (e: InterruptedException) {
                 if (!future.isDone) {
                     System.err.println("LiteLLMClient: Request $requestId future get interrupted.")
                     future.completeExceptionally(IOException("Request $requestId interrupted", e))
                     pendingRequests.remove(requestId)
                 }
                 Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (!future.isDone) {
                     future.completeExceptionally(e)
                     pendingRequests.remove(requestId)
                }
            }
        }
        return future
    }
}
