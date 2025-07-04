package nexus.agent

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse as JavaHttpResponse
import java.time.Duration

/**
 * JVM-specific implementations for NvidiaTasker
 */

private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

internal actual suspend fun NvidiaTasker.callApi(
    apiKey: String,
    requestBody: ChatCompletionRequest
): HttpResponse = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("${config.baseUrl}/chat/completions"))
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(ChatCompletionRequest.serializer(), requestBody)))
        .build()
    
    val response = httpClient.send(request, JavaHttpResponse.BodyHandlers.ofString())
    
    HttpResponse(
        statusCode = response.statusCode(),
        body = response.body()
    )
}

internal actual fun readFileContent(basePath: String, path: String): String {
    return try {
        File(basePath, path).readText()
    } catch (e: Exception) {
        "Error reading file: ${e.message}"
    }
}

internal actual fun writeFileContent(basePath: String, path: String, content: String): String {
    return try {
        File(basePath, path).writeText(content)
        "Successfully wrote to $path"
    } catch (e: Exception) {
        "Error writing file: ${e.message}"
    }
}

internal actual fun listFilesInDirectory(basePath: String, path: String): String {
    return try {
        val dir = File(basePath, path)
        if (!dir.exists()) return "Directory does not exist"
        if (!dir.isDirectory) return "Path is not a directory"
        dir.listFiles()?.joinToString("\n") { it.name } ?: "Empty directory"
    } catch (e: Exception) {
        "Error listing files: ${e.message}"
    }
}

// Extension to access config
internal val NvidiaTasker.config: NvidiaTasker.TaskerConfig
    get() = this::class.java.getDeclaredField("config").apply {
        isAccessible = true
    }.get(this) as NvidiaTasker.TaskerConfig

// Extension to access json
internal val NvidiaTasker.json: Json
    get() = this::class.java.getDeclaredField("json").apply {
        isAccessible = true
    }.get(this) as Json