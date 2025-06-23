package borg.trikeshed.k2script

import borg.trikeshed.lib.*
import borg.trikeshed.ccek.*
import borg.trikeshed.net.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.*

/**
 * K2Script Servlet Container
 * Executes Kotlin scripts as servlets with full TrikeShed integration
 */
class ServletContainer(
    private val scriptRoot: String,
    private val cacheScripts: Boolean = true,
    private val maxScriptSize: Long = 10_000_000 // 10MB
) {
    // Script cache
    private val scriptCache = mutableMapOf<String, CompiledScript>()
    private val scriptContexts = mutableMapOf<String, CoroutineContext>()
    
    // Servlet registry
    private val servlets = mutableMapOf<String, ServletInstance>()
    
    /**
     * Handle HTTP request by executing K2Script servlet
     */
    suspend fun handleRequest(
        path: String,
        request: HttpRequest,
        baseContext: CoroutineContext = EmptyCoroutineContext
    ): HttpResponse = coroutineScope {
        // Extract servlet path and script name
        val servletPath = path.removePrefix("/servlet/")
        val scriptName = servletPath.substringBefore('/')
        val pathInfo = servletPath.substringAfter('/', "")
        
        // Get or compile script
        val servlet = getOrCompileServlet(scriptName) ?: return@coroutineScope HttpResponse(
            404,
            "Servlet not found: $scriptName"
        )
        
        // Create servlet context
        val servletContext = baseContext +
            ServletRequestElement(request) +
            ServletResponseElement() +
            PathInfoElement(pathInfo)
        
        // Execute servlet
        try {
            servlet.execute(servletContext)
            
            // Get response from context
            val responseElement = servletContext[ServletResponseElement.Key]
            responseElement?.toHttpResponse() ?: HttpResponse(500, "No response generated")
        } catch (e: Exception) {
            HttpResponse(500, "Servlet execution error: ${e.message}")
        }
    }
    
    /**
     * Get or compile servlet
     */
    private suspend fun getOrCompileServlet(scriptName: String): ServletInstance? {
        // Check cache
        if (cacheScripts) {
            servlets[scriptName]?.let { return it }
        }
        
        // Load script
        val scriptPath = "$scriptRoot/$scriptName.kts"
        val scriptContent = loadScript(scriptPath) ?: return null
        
        // Compile script
        val compiled = compileScript(scriptName, scriptContent)
        val instance = ServletInstance(scriptName, compiled)
        
        if (cacheScripts) {
            servlets[scriptName] = instance
        }
        
        return instance
    }
    
    /**
     * Load script from disk
     */
    private suspend fun loadScript(path: String): String? {
        // In real implementation, would read from disk
        // For now, return sample scripts
        return when {
            path.endsWith("hello.kts") -> HELLO_SERVLET
            path.endsWith("json.kts") -> JSON_SERVLET
            path.endsWith("game.kts") -> GAME_SERVLET
            path.endsWith("ipfs.kts") -> IPFS_SERVLET
            else -> null
        }
    }
    
    /**
     * Compile K2Script to executable form
     */
    private suspend fun compileScript(name: String, content: String): CompiledScript {
        // In real implementation, would use Kotlin scripting API
        // For now, create a simple compiled script
        return CompiledScript(name, content)
    }
    
    /**
     * Register pre-compiled servlet
     */
    fun registerServlet(name: String, servlet: ServletInstance) {
        servlets[name] = servlet
    }
    
    /**
     * Clear script cache
     */
    fun clearCache() {
        scriptCache.clear()
        servlets.clear()
    }
}

// Servlet data structures

data class CompiledScript(
    val name: String,
    val content: String
)

class ServletInstance(
    val name: String,
    private val script: CompiledScript
) {
    suspend fun execute(context: CoroutineContext) = withContext(context) {
        // Get request and response from context
        val request = coroutineContext[ServletRequestElement.Key]?.request
            ?: throw IllegalStateException("No request in context")
        val response = coroutineContext[ServletResponseElement.Key]
            ?: throw IllegalStateException("No response in context")
        
        // Execute based on script content
        when (script.name) {
            "hello" -> executeHello(request, response)
            "json" -> executeJson(request, response)
            "game" -> executeGame(request, response)
            "ipfs" -> executeIpfs(request, response)
            else -> response.write("Unknown servlet: ${script.name}")
        }
    }
    
    private suspend fun executeHello(request: HttpRequest, response: ServletResponseElement) {
        response.contentType = "text/html"
        response.write("<h1>Hello from K2Script!</h1>")
        response.write("<p>Path: ${request.path}</p>")
        response.write("<p>Method: ${request.method}</p>")
    }
    
    private suspend fun executeJson(request: HttpRequest, response: ServletResponseElement) {
        response.contentType = "application/json"
        
        val json = buildJsonObject {
            put("servlet", "json")
            put("timestamp", getCurrentTimeMillis())
            put("method", request.method)
            put("path", request.path)
            
            // Parse request body as JSON if present
            if (request.body.isNotEmpty()) {
                try {
                    put("requestData", Json.parseToJsonElement(request.body))
                } catch (e: Exception) {
                    put("requestData", request.body)
                }
            }
        }
        
        response.write(json.toString())
    }
    
    private suspend fun executeGame(request: HttpRequest, response: ServletResponseElement) {
        response.contentType = "application/json"
        
        // Game state servlet
        val gameState = buildJsonObject {
            put("type", "gameState")
            put("tick", getCurrentTimeMillis() / 16) // ~60 FPS
            putJsonArray("entities") {
                add(buildJsonObject {
                    put("id", 1)
                    put("type", "unit")
                    put("x", 100)
                    put("y", 200)
                    put("health", 100)
                })
            }
        }
        
        response.write(gameState.toString())
    }
    
    private suspend fun executeIpfs(request: HttpRequest, response: ServletResponseElement) {
        response.contentType = "application/json"
        
        // IPFS integration servlet
        val result = buildJsonObject {
            put("servlet", "ipfs")
            put("status", "ready")
            put("capabilities", buildJsonArray {
                add("add")
                add("get")
                add("pin")
            })
        }
        
        response.write(result.toString())
    }
}

// Context elements

data class ServletRequestElement(
    val request: HttpRequest
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ServletRequestElement>
    override val key = Key
}

class ServletResponseElement : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<ServletResponseElement>
    override val key = Key
    
    private val buffer = StringBuilder()
    var contentType = "text/plain"
    var status = 200
    var statusText = "OK"
    private val headers = mutableMapOf<String, String>()
    
    fun write(content: String) {
        buffer.append(content)
    }
    
    fun setHeader(name: String, value: String) {
        headers[name] = value
    }
    
    fun toHttpResponse(): HttpResponse {
        return HttpResponse(
            status = status,
            statusText = statusText,
            body = buffer.toString(),
            contentType = contentType
        )
    }
}

data class PathInfoElement(
    val pathInfo: String
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<PathInfoElement>
    override val key = Key
}

// Sample servlet scripts

private val HELLO_SERVLET = """
// hello.kts - Simple hello world servlet
response.contentType = "text/html"
response.write("<h1>Hello from K2Script!</h1>")
response.write("<p>Your path: mock_path</p>")
"""

private const val JSON_SERVLET = """
// json.kts - JSON API servlet
import kotlinx.serialization.json.*

val request = servletRequest
val response = servletResponse

response.contentType = "application/json"

val result = buildJsonObject {
    put("message", "Hello from JSON servlet")
    put("timestamp", getCurrentTimeMillis())
}

response.write(result.toString())
"""

private const val GAME_SERVLET = """
// game.kts - Game state servlet
import kotlinx.serialization.json.*

val request = servletRequest
val response = servletResponse

response.contentType = "application/json"

val gameState = buildJsonObject {
    put("tick", getCurrentTimeMillis() / 16)
    putJsonArray("units") {
        for (i in 1..10) {
            add(buildJsonObject {
                put("id", i)
                put("x", (i * 50) % 800)
                put("y", (i * 30) % 600)
            })
        }
    }
}

response.write(gameState.toString())
"""

private const val IPFS_SERVLET = """
// ipfs.kts - IPFS integration servlet
import borg.trikeshed.ipfs.*
import kotlinx.serialization.json.*

val request = servletRequest
val response = servletResponse
val ipfs = context.ipfsClient

response.contentType = "application/json"

when (request.method) {
    "POST" -> {
        val data = request.body.toByteArray()
        val indexed = data.size j { data[it] }
        val cid = ipfs.add(indexed)
        
        response.write(buildJsonObject {
            put("cid", cid.encode())
        }.toString())
    }
    "GET" -> {
        val cid = request.pathInfo
        // Would retrieve from IPFS
        response.write(buildJsonObject {
            put("status", "ready")
        }.toString())
    }
}
"""