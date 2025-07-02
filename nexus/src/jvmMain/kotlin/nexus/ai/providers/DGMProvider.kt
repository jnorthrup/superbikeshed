package nexus.ai.providers

import nexus.ai.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Darwin Gödel Machine Provider - JVM-hosted Python representation
 * 
 * Uses GraalPython or Jython to run a JVM-compatible subset of DGM
 * that represents Nexus capabilities without requiring full OS features.
 * 
 * This is NOT the full DGM but a Nexus-focused representation that:
 * - Runs within JVM constraints (no ProcessPoolExecutor, no Unix sockets)
 * - Provides Nexus AI capabilities through JVM Python
 * - Can still access langchain and other pure-Python AI libraries
 * - Focuses on what's possible within the JVM ecosystem
 */
class DGMProvider(
    private val pythonEngine: PythonEngine = PythonEngine.GRAALVM,
    private val scriptPath: String = "nexus/jvm_dgm_bridge.py",
    private val useEmbedded: Boolean = true
) : LLMProvider {
    
    enum class PythonEngine {
        GRAALVM,    // GraalPython - modern, fast, good interop
        JYTHON,     // Legacy but stable
        EMBEDDED    // Embedded Python script as resource
    }
    
    override suspend fun complete(prompt: String, systemPrompt: String?): String = withContext(Dispatchers.IO) {
        when (connectionType) {
            ConnectionType.HTTP -> completeViaHttp(prompt, systemPrompt)
            ConnectionType.UNIX_SOCKET -> completeViaSocket(prompt, systemPrompt)
            ConnectionType.SUBPROCESS -> completeViaSubprocess(prompt, systemPrompt)
        }
    }
    
    private fun completeViaHttp(prompt: String, systemPrompt: String?): String {
        val json = buildString {
            append("""{"type":"complete","prompt":"${prompt.replace("\"", "\\\"")}"""")
            systemPrompt?.let {
                append(""","system_prompt":"${it.replace("\"", "\\\"")}"""")
            }
            append("}")
        }
        
        return try {
            val url = URL("http://$host:$port/dgm/complete")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000 // 30 seconds for DGM processing
            
            conn.outputStream.use { 
                OutputStreamWriter(it).use { writer ->
                    writer.write(json)
                }
            }
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                // Extract result from DGM response
                val resultMatch = """"result"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                    .find(response)
                resultMatch?.groupValues?.get(1)?.replace("\\n", "\n") ?: response
            } else {
                "[DGM Error] HTTP ${conn.responseCode}: ${conn.responseMessage}"
            }
        } catch (e: Exception) {
            "[DGM Error] Failed to connect via HTTP: ${e.message}\nIs DGM running at http://$host:$port?"
        }
    }
    
    private fun completeViaSocket(prompt: String, systemPrompt: String?): String {
        // Unix domain sockets - JVM only, not available on all platforms
        return try {
            // Note: This is a simplified implementation
            // Real Unix domain socket support requires additional libraries
            "[DGM Error] Unix domain sockets require additional JVM libraries (junixsocket)"
        } catch (e: Exception) {
            "[DGM Error] Socket connection failed: ${e.message}"
        }
    }
    
    private fun completeViaSubprocess(prompt: String, systemPrompt: String?): String {
        return try {
            val pythonCmd = listOf("python3", dgmScript, "--mode", "complete", "--prompt", prompt)
            val processBuilder = ProcessBuilder(pythonCmd)
            
            systemPrompt?.let {
                processBuilder.environment()["DGM_SYSTEM_PROMPT"] = it
            }
            
            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            process.waitFor()
            
            if (process.exitValue() != 0) {
                "[DGM Error] Process failed: $error"
            } else {
                output.trim()
            }
        } catch (e: Exception) {
            "[DGM Error] Failed to launch DGM subprocess: ${e.message}"
        }
    }
    
    override suspend fun chat(messages: Indexed<Message>): String = withContext(Dispatchers.IO) {
        // Convert chat to DGM's expected format
        val dgmMessages = messages.toList().map { msg ->
            """{"role":"${msg.role}","content":"${msg.content.replace("\"", "\\\"")}"}"""
        }.joinToString(",", "[", "]")
        
        val json = """{"type":"chat","messages":$dgmMessages}"""
        
        // For chat, we'll use HTTP as it's most compatible
        completeViaHttp(json, null)
    }
    
    override fun isAvailable(): Boolean {
        return when (connectionType) {
            ConnectionType.HTTP -> {
                try {
                    val url = URL("http://$host:$port/dgm/health")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.connect()
                    conn.responseCode == 200
                } catch (e: Exception) {
                    false
                }
            }
            ConnectionType.UNIX_SOCKET -> {
                // Check if socket file exists
                java.io.File(socketPath).exists()
            }
            ConnectionType.SUBPROCESS -> {
                // Check if DGM script exists
                java.io.File(dgmScript).exists()
            }
        }
    }
    
    /**
     * DGM-specific features beyond standard LLM
     */
    suspend fun evolve(
        population: Indexed<String>,
        fitnessFunction: suspend (String) -> Double,
        generations: Int = 10
    ): Indexed<String> = withContext(Dispatchers.IO) {
        // Call DGM's evolutionary features
        val json = buildString {
            append("""{"type":"evolve",""")
            append(""""population":[${population.toList().joinToString(",") { "\"$it\"" }}],""")
            append(""""generations":$generations}""")
        }
        
        // This would call DGM's evolutionary algorithm
        // For now, return the original population
        population
    }
    
    /**
     * Access DGM's langchain integration
     */
    suspend fun langchainQuery(
        query: String,
        tools: Indexed<String> = Indexed.of("search", "calculate", "code")
    ): String = withContext(Dispatchers.IO) {
        val json = buildString {
            append("""{"type":"langchain",""")
            append(""""query":"${query.replace("\"", "\\\"")}",""")
            append(""""tools":[${tools.toList().joinToString(",") { "\"$it\"" }}]}""")
        }
        
        completeViaHttp(json, "You are a langchain-powered assistant with access to tools")
    }
}