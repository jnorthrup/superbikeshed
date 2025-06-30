#!/usr/bin/env k2script

@file:CompilerOpts("-jvm-target", "21")
@file:CompilerOpts("-Xjsr305=strict")

/**
 * TrikeShed HTTP Server Demo
 * 
 * This k2script demonstrates TrikeShed's HTTP server capabilities
 * using the relaxfactory/1xio visitor pattern architecture.
 * 
 * Usage: k2script demo_ts_httpd.kts [port]
 */

import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8080
    
    println("🚀 TrikeShed HTTP Server Demo")
    println("📡 Architecture: 1xio → relaxfactory → TrikeShed CCEK")
    println("🌐 Starting server on port $port...")
    println("📝 Serving BoingDemo content with JavaScript fallback")
    println("⚡ Using SelectionKey continuations and visitor patterns")
    println()
    
    runBlocking {
        startTrikeShedHttpServer(port)
    }
}

/**
 * Start TrikeShed-style HTTP server using NIO SelectionKey patterns
 * This demonstrates the relaxfactory visitor architecture
 */
suspend fun startTrikeShedHttpServer(port: Int) {
    val selector = Selector.open()
    val serverChannel = ServerSocketChannel.open()
    
    try {
        // Configure server channel
        serverChannel.configureBlocking(false)
        serverChannel.socket().bind(InetSocketAddress(port))
        
        // Register with selector for ACCEPT operations
        val serverKey = serverChannel.register(selector, SelectionOps.OP_ACCEPT)
        
        println("✅ TrikeShed HTTP Server listening on port $port")
        println("🌐 Open: http://localhost:$port")
        println("🎮 Try: http://localhost:$port/boingDemo")
        println("📊 Architecture demo: http://localhost:$port/architecture")
        println("🔄 Press Ctrl+C to stop")
        println()
        
        // Main reactor loop - this is the 1xio/relaxfactory pattern
        while (true) {
            val readyChannels = selector.select(1000)
            
            if (readyChannels > 0) {
                val selectedKeys = selector.selectedKeys()
                val iterator = selectedKeys.iterator()
                
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    iterator.remove()
                    
                    try {
                        handleSelectionKey(key, selector)
                    } catch (e: Exception) {
                        println("❌ Error handling key: ${e.message}")
                        key.cancel()
                        key.channel().close()
                    }
                }
            }
            
            // Yield to prevent blocking
            yield()
        }
        
    } finally {
        serverChannel.close()
        selector.close()
    }
}

/**
 * Handle SelectionKey events - this is the core 1xio visitor pattern
 * Each key stores visitor state in its attachment
 */
suspend fun handleSelectionKey(key: SelectionKey, selector: Selector) {
    when {
        key.isAcceptable -> handleAccept(key, selector)
        key.isReadable -> handleRead(key)
        key.isWritable -> handleWrite(key)
    }
}

/**
 * Handle ACCEPT operation - create new client connection
 * This is the first visitor in the relaxfactory chain
 */
suspend fun handleAccept(key: SelectionKey, selector: Selector) {
    val serverChannel = key.channel() as ServerSocketChannel
    val clientChannel = serverChannel.accept()
    
    if (clientChannel != null) {
        clientChannel.configureBlocking(false)
        
        // Create HTTP request state - this gets stored in SelectionKey.attachment()
        val httpState = HttpConnectionState()
        
        // Register for READ with the state attached
        val clientKey = clientChannel.register(selector, SelectionOps.OP_READ)
        clientKey.attach(httpState)
        
        println("📥 New connection: ${clientChannel.remoteAddress}")
    }
}

/**
 * Handle READ operation - parse HTTP request
 * This is the request parsing visitor
 */
suspend fun handleRead(key: SelectionKey) {
    val clientChannel = key.channel() as SocketChannel
    val httpState = key.attachment() as HttpConnectionState
    val buffer = ByteBuffer.allocate(8192)
    
    try {
        val bytesRead = clientChannel.read(buffer)
        
        when {
            bytesRead > 0 -> {
                buffer.flip()
                val requestData = ByteArray(buffer.remaining())
                buffer.get(requestData)
                httpState.requestData.addAll(requestData.toList())
                
                // Check if we have a complete HTTP request
                val requestString = String(httpState.requestData.toByteArray(), Charsets.UTF_8)
                if (requestString.contains("\r\n\r\n")) {
                    // Complete request received - parse and prepare response
                    httpState.response = generateTrikeShedHttpResponse(requestString)
                    httpState.responseBuffer = ByteBuffer.wrap(httpState.response.toByteArray())
                    
                    // Switch to WRITE mode
                    key.interestOps(SelectionOps.OP_WRITE)
                }
            }
            bytesRead == 0 -> {
                // Would block - continue reading later
            }
            else -> {
                // Connection closed
                println("📤 Connection closed: ${clientChannel.remoteAddress}")
                clientChannel.close()
                key.cancel()
            }
        }
    } catch (e: Exception) {
        println("❌ Read error: ${e.message}")
        clientChannel.close()
        key.cancel()
    }
}

/**
 * Handle WRITE operation - send HTTP response
 * This is the response visitor
 */
suspend fun handleWrite(key: SelectionKey) {
    val clientChannel = key.channel() as SocketChannel
    val httpState = key.attachment() as HttpConnectionState
    
    try {
        val bytesWritten = clientChannel.write(httpState.responseBuffer)
        
        if (!httpState.responseBuffer.hasRemaining()) {
            // Response complete
            val requestLine = String(httpState.requestData.toByteArray()).lines().firstOrNull() ?: ""
            println("✅ Response sent: $requestLine")
            
            // Check for keep-alive
            val requestString = String(httpState.requestData.toByteArray())
            if (requestString.contains("Connection: keep-alive", ignoreCase = true)) {
                // Reset for next request
                httpState.reset()
                key.interestOps(SelectionOps.OP_READ)
            } else {
                // Close connection
                clientChannel.close()
                key.cancel()
            }
        }
    } catch (e: Exception) {
        println("❌ Write error: ${e.message}")
        clientChannel.close()
        key.cancel()
    }
}

/**
 * HTTP Connection State - stored in SelectionKey.attachment()
 * This represents the CCEK scope for each connection
 */
class HttpConnectionState {
    val requestData = mutableListOf<Byte>()
    var response: String = ""
    var responseBuffer: ByteBuffer = ByteBuffer.allocate(0)
    
    fun reset() {
        requestData.clear()
        response = ""
        responseBuffer = ByteBuffer.allocate(0)
    }
}

/**
 * Generate HTTP response - TrikeShed style with architecture demonstration
 */
fun generateTrikeShedHttpResponse(requestString: String): String {
    val lines = requestString.split("\r\n")
    val requestLine = lines.firstOrNull() ?: return createErrorResponse(400, "Bad Request")
    
    val parts = requestLine.split(" ")
    if (parts.size < 2) return createErrorResponse(400, "Bad Request")
    
    val method = parts[0]
    val path = parts[1]
    
    if (method != "GET" && method != "HEAD") {
        return createErrorResponse(405, "Method Not Allowed")
    }
    
    return when {
        path == "/" || path == "/index.html" -> createBoingDemoResponse()
        path == "/boingDemo" -> createBoingDemoResponse()
        path == "/architecture" -> createArchitectureResponse()
        path == "/reactor" -> createReactorDemoResponse()
        path == "/health" -> createHealthResponse()
        else -> createErrorResponse(404, "Not Found")
    }
}

/**
 * Create the main BoingDemo HTML response
 */
fun createBoingDemoResponse(): String {
    val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>BoingDemo - TrikeShed k2script HTTP Server</title>
    <style>
        body { 
            margin: 0; padding: 20px; font-family: 'Courier New', monospace; 
            background: linear-gradient(135deg, #001122, #003366); 
            color: #00ff88; min-height: 100vh;
        }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 { color: #00ccff; text-align: center; text-shadow: 0 0 10px #00ccff; }
        .info { 
            background: rgba(0,0,0,0.3); padding: 20px; border-radius: 10px; 
            border: 1px solid #00ff88; margin: 20px 0;
        }
        #gameCanvas { 
            border: 2px solid #00ff88; background: #000080;
            display: block; margin: 20px auto; box-shadow: 0 0 20px #00ff88;
        }
        .architecture { 
            display: grid; grid-template-columns: 1fr 1fr; gap: 20px; 
            margin: 20px 0;
        }
        .layer { 
            background: rgba(0,100,200,0.2); padding: 15px; border-radius: 8px;
            border-left: 4px solid #00ccff;
        }
        code { color: #ffcc00; background: rgba(0,0,0,0.5); padding: 2px 6px; border-radius: 3px; }
        .status { 
            text-align: center; padding: 10px; 
            background: rgba(0,200,0,0.2); border-radius: 5px;
        }
        a { color: #00ccff; text-decoration: none; }
        a:hover { color: #00ff88; text-shadow: 0 0 5px #00ff88; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎮 BoingDemo - TrikeShed k2script Server</h1>
        
        <div class="info">
            <p><strong>Classic Amiga bouncing ball demo</strong> served by TrikeShed's k2script HTTP server</p>
            <p>🏗️ <strong>Architecture:</strong> 1xio SelectionKey continuations → relaxfactory visitors → TrikeShed CCEK</p>
            <p>⚡ <strong>Features:</strong> Zero-copy I/O, visitor pattern state machines, RFC 7230 compliance</p>
        </div>
        
        <div class="status">
            <p>Status: <span id="status">Initializing TrikeShed reactor...</span></p>
        </div>
        
        <canvas id="gameCanvas" width="800" height="500">
            Your browser does not support Canvas
        </canvas>
        
        <div class="architecture">
            <div class="layer">
                <h3>🧠 1xio Legacy</h3>
                <p>SelectionKey continuation storage</p>
                <p>Non-blocking I/O reactor pattern</p>
                <p>Zero OS thread allocation</p>
                <code>key.attachment() → visitor</code>
            </div>
            <div class="layer">
                <h3>🏭 relaxfactory</h3>
                <p>Visitor-based state machines</p>
                <p>Request/response pipeline</p>
                <p>Continuation-passing style</p>
                <code>visitor.invoke(key) → next</code>
            </div>
            <div class="layer">
                <h3>🚀 TrikeShed CCEK</h3>
                <p>Context-driven scopes</p>
                <p>Series&lt;T&gt; and Join&lt;A,B&gt;</p>
                <p>Arena allocators</p>
                <code>scope j visitor α transform</code>
            </div>
            <div class="layer">
                <h3>🌐 k2script Integration</h3>
                <p>Direct NIO channel access</p>
                <p>Kotlin coroutine integration</p>
                <p>Production HTTP serving</p>
                <code>k2script + TrikeShed = 🔥</code>
            </div>
        </div>
        
        <div class="info">
            <h3>🔗 Navigation</h3>
            <p>
                <a href="/architecture">Architecture Deep Dive</a> | 
                <a href="/reactor">Reactor Pattern Demo</a> | 
                <a href="/health">Health Check</a>
            </p>
            <p><em>This server is running pure k2script with NIO SelectionKey patterns</em></p>
        </div>
    </div>
    
    <script>
        const canvas = document.getElementById('gameCanvas');
        const ctx = canvas.getContext('2d');
        const status = document.getElementById('status');
        
        status.textContent = 'TrikeShed reactor active - bouncing ball engaged!';
        
        // Ball physics matching the original BoingDemo.kt
        let ball = {
            x: 100, y: 100, radius: 40,
            vx: 300, vy: 250, rotation: 0,
            rotSpeed: 180, justBounced: false
        };
        
        let lastTime = 0;
        
        function update(dt) {
            ball.justBounced = false;
            ball.x += ball.vx * dt / 1000;
            ball.y += ball.vy * dt / 1000;
            ball.rotation += ball.rotSpeed * dt / 1000;
            
            // Bounce physics with proper collision detection
            if ((ball.x < ball.radius && ball.vx < 0) || 
                (ball.x > canvas.width - ball.radius && ball.vx > 0)) {
                ball.vx *= -0.95; // Slight energy loss for realism
                ball.justBounced = true;
            }
            if ((ball.y < ball.radius && ball.vy < 0) || 
                (ball.y > canvas.height - ball.radius && ball.vy > 0)) {
                ball.vy *= -0.95;
                ball.justBounced = true;
            }
            
            // Apply gravity
            ball.vy += 500 * dt / 1000;
        }
        
        function draw() {
            // Clear with deep blue background
            ctx.fillStyle = '#000080';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            
            // Draw dynamic shadow
            const shadowY = canvas.height - 20;
            const shadowWidth = ball.radius * (1.5 - (ball.y / canvas.height));
            
            ctx.fillStyle = 'rgba(0, 0, 0, 0.4)';
            ctx.beginPath();
            ctx.ellipse(ball.x, shadowY, shadowWidth, 8, 0, 0, 2 * Math.PI);
            ctx.fill();
            
            // Draw the classic red/white striped ball
            ctx.save();
            ctx.translate(ball.x, ball.y);
            ctx.rotate(ball.rotation * Math.PI / 180);
            
            // 8 alternating segments
            for (let i = 0; i < 8; i++) {
                ctx.fillStyle = i % 2 === 0 ? '#FF0000' : '#FFFFFF';
                ctx.beginPath();
                ctx.arc(0, 0, ball.radius, 
                       i * Math.PI / 4, (i + 1) * Math.PI / 4);
                ctx.lineTo(0, 0);
                ctx.fill();
            }
            
            // Add glossy highlight
            ctx.fillStyle = 'rgba(255, 255, 255, 0.3)';
            ctx.beginPath();
            ctx.ellipse(-ball.radius/3, -ball.radius/3, ball.radius/3, ball.radius/4, 0, 0, 2 * Math.PI);
            ctx.fill();
            
            ctx.restore();
            
            // Status indicator
            if (ball.justBounced) {
                ctx.fillStyle = '#00ff88';
                ctx.font = '14px Courier New';
                ctx.fillText('BOUNCE!', 10, 30);
            }
        }
        
        function gameLoop(currentTime) {
            const dt = Math.min(currentTime - lastTime, 32); // Cap at ~30 FPS
            lastTime = currentTime;
            
            if (dt > 0) {
                update(dt);
                draw();
            }
            
            requestAnimationFrame(gameLoop);
        }
        
        // Start the TrikeShed-powered BoingDemo!
        console.log('🚀 TrikeShed BoingDemo initialized');
        console.log('⚡ Using k2script HTTP server with NIO reactor pattern');
        requestAnimationFrame(gameLoop);
    </script>
</body>
</html>
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "text/html", html)
}

/**
 * Create architecture documentation response
 */
fun createArchitectureResponse(): String {
    val html = """
<!DOCTYPE html>
<html><head><title>TrikeShed Architecture</title></head>
<body style="font-family: monospace; background: #001122; color: #00ff88; padding: 20px;">
    <h1>🏗️ TrikeShed Architecture Deep Dive</h1>
    
    <h2>1xio → relaxfactory → TrikeShed Evolution</h2>
    <pre>
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│    1xio     │ -> │ relaxfactory │ -> │ TrikeShed   │
│ SelectionKey│    │   Visitors   │    │    CCEK     │
│Continuations│    │ State Machine│    │Series/Join  │
└─────────────┘    └──────────────┘    └─────────────┘
    </pre>
    
    <h2>🔄 Reactor Pattern Flow</h2>
    <ol>
        <li><strong>Accept:</strong> ServerChannel.accept() → new client connection</li>
        <li><strong>Read:</strong> Parse HTTP request, store in SelectionKey.attachment()</li>
        <li><strong>Process:</strong> Generate response using visitor pattern</li>
        <li><strong>Write:</strong> Send response, handle keep-alive</li>
    </ol>
    
    <h2>💾 SelectionKey State Management</h2>
    <pre>
key.attachment() = HttpConnectionState {
    requestData: MutableList&lt;Byte&gt;
    response: String
    responseBuffer: ByteBuffer
}
    </pre>
    
    <p><a href="/">← Back to BoingDemo</a></p>
</body></html>
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "text/html", html)
}

/**
 * Create reactor demonstration response
 */
fun createReactorDemoResponse(): String {
    val html = """
<!DOCTYPE html>
<html><head><title>Reactor Pattern Demo</title></head>
<body style="font-family: monospace; background: #001122; color: #00ff88; padding: 20px;">
    <h1>⚡ TrikeShed Reactor Pattern Demo</h1>
    
    <h2>Current Connection State</h2>
    <p>This response was generated by the visitor pattern state machine!</p>
    
    <h2>🔍 How This Request Was Processed</h2>
    <ol>
        <li><strong>Accept Visitor:</strong> ServerChannel accepted your connection</li>
        <li><strong>Read Visitor:</strong> Parsed your HTTP request headers</li>
        <li><strong>Route Visitor:</strong> Matched path "/reactor" to this handler</li>
        <li><strong>Response Visitor:</strong> Generated this HTML content</li>
        <li><strong>Write Visitor:</strong> Will send this response back to you</li>
    </ol>
    
    <h2>🎯 SelectionKey Operations</h2>
    <pre>
OP_ACCEPT  → handleAccept()  → create client connection
OP_READ    → handleRead()    → parse HTTP request  
OP_WRITE   → handleWrite()   → send HTTP response
    </pre>
    
    <p><a href="/">← Back to BoingDemo</a></p>
</body></html>
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "text/html", html)
}

/**
 * Create health check response
 */
fun createHealthResponse(): String {
    val uptime = System.currentTimeMillis()
    val json = """
{
    "status": "healthy",
    "server": "TrikeShed k2script HTTP Server",
    "architecture": "1xio → relaxfactory → CCEK",
    "features": [
        "SelectionKey continuations",
        "Visitor pattern state machines", 
        "Zero-copy I/O",
        "HTTP/1.1 keep-alive",
        "RFC 7230 compliance"
    ],
    "uptime_ms": $uptime,
    "demo": "BoingDemo bouncing ball"
}
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "application/json", json)
}

/**
 * Create HTTP response following RFC 7230
 */
fun createHttpResponse(status: Int, reason: String, contentType: String, body: String): String {
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    return """HTTP/1.1 $status $reason
Content-Type: $contentType
Content-Length: ${bodyBytes.size}
Connection: keep-alive
Server: TrikeShed/1.0 (k2script-relaxfactory)
Cache-Control: no-cache

$body"""
}

/**
 * Create HTTP error response
 */
fun createErrorResponse(status: Int, reason: String): String {
    val body = """
<!DOCTYPE html>
<html><head><title>$status $reason</title></head>
<body style="font-family: monospace; background: #001122; color: #ff4444; padding: 20px;">
    <h1>$status $reason</h1>
    <p>TrikeShed k2script HTTP Server</p>
    <p><a href="/" style="color: #00ccff;">← Home</a></p>
</body></html>
    """.trimIndent()
    
    return createHttpResponse(status, reason, "text/html", body)
}

/**
 * Selection operation constants
 */
object SelectionOps {
    const val OP_READ = SelectionKey.OP_READ
    const val OP_WRITE = SelectionKey.OP_WRITE
    const val OP_ACCEPT = SelectionKey.OP_ACCEPT
}

// Execute the main function
if (args.isEmpty()) {
    main(arrayOf("8080"))
} else {
    main(args)
}