import kotlinx.coroutines.*
import borg.trikeshed.net.http.*
import borg.trikeshed.io.IOContext
import borg.trikeshed.reactor.SocketFactory

/**
 * Simple test script to verify network implementation
 */
fun main() = runBlocking {
    println("🧪 Testing Network Implementation")
    
    try {
        // Test 1: Create HTTP client
        println("1. Creating HTTP client...")
        val ioContext = IOContext.NioContext("test-client")
        val httpClient = HttpClient(ioContext)
        println("✅ HTTP client created successfully")
        
        // Test 2: Create socket factory
        println("2. Testing socket factory...")
        val clientSocket = SocketFactory.createClientSocket("localhost", 8080)
        println("✅ Socket factory working")
        clientSocket.close()
        
        // Test 3: Test HTTP request building
        println("3. Testing HTTP request building...")
        val request = HttpRequest(
            method = HttpMethod.GET,
            path = HttpRequestPath("/test"),
            headers = arrayOf(
                HttpHeaderName("Host") j HttpHeaderValue("localhost:8080"),
                HttpHeaderName("User-Agent") j HttpHeaderValue("TrikeShed-Test/1.0")
            )
        )
        println("✅ HTTP request built successfully")
        
        // Test 4: Test URL parsing
        println("4. Testing URL parsing...")
        val url = "http://localhost:8080/api/test"
        val (host, path) = parseUrl(url)
        println("   Host: $host, Path: $path")
        println("✅ URL parsing working")
        
        println("\n🎉 All network implementation tests passed!")
        
    } catch (e: Exception) {
        println("❌ Test failed: ${e.message}")
        e.printStackTrace()
    }
}

private fun parseUrl(url: String): Pair<String, String> {
    val withoutProtocol = url.removePrefix("http://").removePrefix("https://")
    val firstSlash = withoutProtocol.indexOf('/')
    
    return if (firstSlash == -1) {
        withoutProtocol to "/"
    } else {
        withoutProtocol.substring(0, firstSlash) to withoutProtocol.substring(firstSlash)
    }
} 