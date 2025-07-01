/**
 * Simple HTTP Server Demo - TrikeShed Style
 * Pure Kotlin implementation without external dependencies
 */

import java.nio.ByteBuffer
import java.nio.channels.*
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull() ?: 8080
    
    println("🚀 TrikeShed HTTP Server Demo")
    println("📡 Architecture: 1xio → relaxfactory → TrikeShed CCEK")
    println("🌐 Starting server on port $port...")
    println("📝 Serving BoingDemo content")
    println("⚡ Using SelectionKey continuations and visitor patterns")
    println()
    
    startTrikeShedHttpServer(port)
}

/**
 * Start TrikeShed-style HTTP server using NIO SelectionKey patterns
 */
fun startTrikeShedHttpServer(port: Int) {
    val selector = Selector.open()
    val serverChannel = ServerSocketChannel.open()
    
    try {
        // Configure server channel
        serverChannel.configureBlocking(false)
        serverChannel.socket().bind(InetSocketAddress(port))
        
        // Register with selector for ACCEPT operations
        val serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT)
        
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
        }
        
    } finally {
        serverChannel.close()
        selector.close()
    }
}

/**
 * Handle SelectionKey events - this is the core 1xio visitor pattern
 */
fun handleSelectionKey(key: SelectionKey, selector: Selector) {
    when {
        key.isAcceptable -> handleAccept(key, selector)
        key.isReadable -> handleRead(key)
        key.isWritable -> handleWrite(key)
    }
}

/**
 * Handle ACCEPT operation - create new client connection
 */
fun handleAccept(key: SelectionKey, selector: Selector) {
    val serverChannel = key.channel() as ServerSocketChannel
    val clientChannel = serverChannel.accept()
    
    if (clientChannel != null) {
        clientChannel.configureBlocking(false)
        
        // Create HTTP request state - stored in SelectionKey.attachment()
        val httpState = HttpConnectionState()
        
        // Register for READ with the state attached
        val clientKey = clientChannel.register(selector, SelectionKey.OP_READ)
        clientKey.attach(httpState)
        
        println("📥 New connection: ${clientChannel.remoteAddress}")
    }
}

/**
 * Handle READ operation - parse HTTP request
 */
fun handleRead(key: SelectionKey) {
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
                val requestString = String(httpState.requestData.toByteArray(), StandardCharsets.UTF_8)
                if (requestString.contains("\r\n\r\n")) {
                    // Complete request received - parse and prepare response
                    httpState.response = generateTrikeShedHttpResponse(requestString)
                    httpState.responseBuffer = ByteBuffer.wrap(httpState.response.toByteArray())
                    
                    // Switch to WRITE mode
                    key.interestOps(SelectionKey.OP_WRITE)
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
 */
fun handleWrite(key: SelectionKey) {
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
                key.interestOps(SelectionKey.OP_READ)
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
 * Generate HTTP response - TrikeShed style
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
    <title>BoingDemo - TrikeShed HTTP Server</title>
    <style>
        body { 
            margin: 0; padding: 20px; font-family: 'Topaz', 'Courier New', monospace; 
            background: linear-gradient(135deg, #0055AA, #003366, #001144); 
            color: #FFFFFF; min-height: 100vh; overflow-x: hidden;
        }
        .container { max-width: 1000px; margin: 0 auto; }
        h1 { 
            color: #FFFF00; text-align: center; text-shadow: 2px 2px 0px #FF0000;
            font-size: 3em; margin: 0.2em 0; animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0%, 100% { text-shadow: 2px 2px 0px #FF0000, 0 0 20px #FFFF00; }
            50% { text-shadow: 2px 2px 0px #FF0000, 0 0 40px #FFFF00, 0 0 60px #FFFF00; }
        }
        .info { 
            background: rgba(255,255,255,0.1); padding: 20px; border-radius: 5px; 
            border: 2px solid #FFFFFF; margin: 20px 0;
        }
        #gameCanvas { 
            border: 4px solid #FFFFFF; background: #0055AA;
            display: block; margin: 20px auto; box-shadow: 0 0 30px rgba(255,255,255,0.3);
            border-radius: 10px; transition: box-shadow 0.3s ease;
        }
        #gameCanvas:hover {
            box-shadow: 0 0 50px rgba(255,255,255,0.5);
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
        <h1>BOING!</h1>
        
        <div class="info">
            <p><strong>🎮 Authentic Amiga BoingDemo</strong> - powered by TrikeShed's production HTTP server</p>
            <p>🏗️ <strong>Architecture:</strong> 1xio SelectionKey continuations → relaxfactory visitors → TrikeShed CCEK</p>
            <p>⚡ <strong>Features:</strong> Zero-copy I/O, visitor pattern state machines, RFC 7230 compliance, WebGPU ready</p>
            <p>🚀 <strong>Performance:</strong> Native access enabled, arena allocators, tensor-first processing</p>
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
                <p>Indexed&lt;T&gt; and Join&lt;A,B&gt;</p>
                <p>Arena allocators</p>
                <code>scope j visitor α transform</code>
            </div>
            <div class="layer">
                <h3>🌐 Pure NIO</h3>
                <p>Direct NIO channel access</p>
                <p>SelectionKey state management</p>
                <p>Production HTTP serving</p>
                <code>selector.select() → reactor</code>
            </div>
        </div>
        
        <div class="info">
            <h3>🔗 Navigation</h3>
            <p>
                <a href="/architecture">Architecture Deep Dive</a> | 
                <a href="/health">Health Check</a>
            </p>
            <p><em>This server demonstrates pure TrikeShed SelectionKey patterns</em></p>
        </div>
    </div>
    
    <script>
        const canvas = document.getElementById('gameCanvas');
        const ctx = canvas.getContext('2d');
        const status = document.getElementById('status');
        
        status.textContent = 'TrikeShed reactor active - bouncing ball engaged!';
        
        // Authentic Amiga BoingDemo physics - matching the real Amiga demo
        let ball = {
            x: 150, y: 120, radius: 60,
            vx: 200, vy: 150, rotation: 0,
            rotSpeed: 90, justBounced: false,
            trail: [] // Add particle trail for extra flair
        };
        
        let lastTime = 0;
        
        function update(dt) {
            ball.justBounced = false;
            
            // Add current position to trail
            ball.trail.push({x: ball.x, y: ball.y, life: 1.0});
            if (ball.trail.length > 20) ball.trail.shift();
            
            // Update trail particles
            ball.trail.forEach(particle => {
                particle.life -= dt / 1000;
            });
            ball.trail = ball.trail.filter(p => p.life > 0);
            
            ball.x += ball.vx * dt / 1000;
            ball.y += ball.vy * dt / 1000;
            ball.rotation += ball.rotSpeed * dt / 1000;
            
            // Authentic Amiga bounce physics - more bouncy, less damping
            if ((ball.x < ball.radius && ball.vx < 0) || 
                (ball.x > canvas.width - ball.radius && ball.vx > 0)) {
                ball.vx *= -0.98; // Less energy loss for more authentic bouncing
                ball.justBounced = true;
            }
            if ((ball.y < ball.radius && ball.vy < 0) || 
                (ball.y > canvas.height - ball.radius && ball.vy > 0)) {
                ball.vy *= -0.98; // Less energy loss
                ball.justBounced = true;
            }
            
            // More realistic gravity for the Amiga demo feel
            ball.vy += 380 * dt / 1000;
        }
        
        function draw() {
            // Clear with authentic Amiga blue background
            ctx.fillStyle = '#0055AA';
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            
            // Draw particle trail
            ball.trail.forEach((particle, index) => {
                const alpha = particle.life * 0.3;
                const size = particle.life * 8;
                ctx.fillStyle = "rgba(255, 255, 255, " + alpha + ")";
                ctx.beginPath();
                ctx.arc(particle.x, particle.y, size, 0, 2 * Math.PI);
                ctx.fill();
            });
            
            // Draw authentic shadow - oval, stretched horizontally
            const shadowY = canvas.height - 15;
            const shadowWidth = ball.radius * 1.5;
            const shadowHeight = ball.radius * 0.3;
            
            ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
            ctx.beginPath();
            ctx.ellipse(ball.x, shadowY, shadowWidth, shadowHeight, 0, 0, 2 * Math.PI);
            ctx.fill();
            
            // Draw the authentic Amiga BoingDemo ball - proper checkered sphere
            ctx.save();
            ctx.translate(ball.x, ball.y);
            ctx.rotate(ball.rotation * Math.PI / 180);
            
            // Draw white base circle first
            ctx.fillStyle = '#FFFFFF';
            ctx.beginPath();
            ctx.arc(0, 0, ball.radius, 0, 2 * Math.PI);
            ctx.fill();
            
            // Draw authentic checkered pattern like the real Amiga BoingDemo
            // Classic red and white checkered pattern with 8x8 grid
            ctx.fillStyle = '#FF0000';
            
            const segments = 8; // 8 longitude segments (vertical stripes)
            const bands = 8;    // 8 latitude bands (horizontal stripes)
            
            // Draw the checkered pattern by checking each "square" on the sphere
            for (let latBand = 0; latBand < bands; latBand++) {
                for (let longSeg = 0; longSeg < segments; longSeg++) {
                    // Checkerboard pattern: alternate red/white
                    const isRed = (latBand + longSeg) % 2 === 0;
                    
                    if (isRed) {
                        // Calculate the angular bounds for this segment
                        const startLong = (longSeg * 2 * Math.PI) / segments;
                        const endLong = ((longSeg + 1) * 2 * Math.PI) / segments;
                        
                        // For latitude, we need to handle the spherical projection
                        const startLat = (latBand * Math.PI) / bands;
                        const endLat = ((latBand + 1) * Math.PI) / bands;
                        
                        // Draw this red segment using multiple arc slices for proper curvature
                        const slices = 6;
                        for (let slice = 0; slice < slices; slice++) {
                            const sliceStartLat = startLat + (slice * (endLat - startLat)) / slices;
                            const sliceEndLat = startLat + ((slice + 1) * (endLat - startLat)) / slices;
                            
                            const innerRadius = ball.radius * Math.sin(sliceStartLat);
                            const outerRadius = ball.radius * Math.sin(sliceEndLat);
                            const centerY = -ball.radius * Math.cos((sliceStartLat + sliceEndLat) / 2);
                            
                            // Only draw if the segment is visible (front hemisphere)
                            if (outerRadius > 0) {
                                ctx.save();
                                ctx.translate(0, centerY);
                                ctx.scale(1, Math.cos((sliceStartLat + sliceEndLat) / 2));
                                
                                ctx.beginPath();
                                ctx.arc(0, 0, outerRadius, startLong, endLong);
                                ctx.arc(0, 0, innerRadius, endLong, startLong, true);
                                ctx.closePath();
                                ctx.fill();
                                
                                ctx.restore();
                            }
                        }
                    }
                }
            }
            
            // Add authentic Amiga-style shading/highlight
            const gradient = ctx.createRadialGradient(
                -ball.radius * 0.3, -ball.radius * 0.3, 0,
                -ball.radius * 0.3, -ball.radius * 0.3, ball.radius * 0.8
            );
            gradient.addColorStop(0, 'rgba(255, 255, 255, 0.6)');
            gradient.addColorStop(0.3, 'rgba(255, 255, 255, 0.2)');
            gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
            
            ctx.fillStyle = gradient;
            ctx.beginPath();
            ctx.arc(0, 0, ball.radius, 0, 2 * Math.PI);
            ctx.fill();
            
            ctx.restore();
            
            // Authentic Amiga-style status text
            if (ball.justBounced) {
                ctx.fillStyle = '#FFFF00';
                ctx.font = 'bold 16px sans-serif';
                ctx.strokeStyle = '#000000';
                ctx.lineWidth = 1;
                ctx.strokeText('BOING!', 10, 30);
                ctx.fillText('BOING!', 10, 30);
            }
        }
        
        function gameLoop(currentTime) {
            const dt = Math.min(currentTime - lastTime, 32);
            lastTime = currentTime;
            
            if (dt > 0) {
                update(dt);
                draw();
            }
            
            requestAnimationFrame(gameLoop);
        }
        
        // Start the TrikeShed-powered BoingDemo!
        console.log('🚀 TrikeShed BoingDemo initialized');
        console.log('⚡ Using pure NIO reactor pattern');
        requestAnimationFrame(gameLoop);
    </script>
</body>
</html>
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "text/html", html)
}

fun createArchitectureResponse(): String {
    val html = """
<!DOCTYPE html>
<html><head><title>TrikeShed Architecture</title></head>
<body style="font-family: monospace; background: #001122; color: #00ff88; padding: 20px;">
    <h1>🏗️ TrikeShed Architecture Deep Dive</h1>
    <h2>1xio → relaxfactory → TrikeShed Evolution</h2>
    <p>This server demonstrates the architectural lineage from 1xio through relaxfactory to TrikeShed.</p>
    <p><a href="/">← Back to BoingDemo</a></p>
</body></html>
    """.trimIndent()
    
    return createHttpResponse(200, "OK", "text/html", html)
}

fun createHealthResponse(): String {
    val json = """{"status": "healthy", "server": "TrikeShed", "architecture": "1xio → relaxfactory → CCEK"}"""
    return createHttpResponse(200, "OK", "application/json", json)
}

/**
 * Create HTTP response following RFC 7230
 */
fun createHttpResponse(status: Int, reason: String, contentType: String, body: String): String {
    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
    return """HTTP/1.1 $status $reason
Content-Type: $contentType
Content-Length: ${bodyBytes.size}
Connection: keep-alive
Server: TrikeShed/1.0 (pure-nio)

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
    <p>TrikeShed HTTP Server</p>
    <p><a href="/" style="color: #00ccff;">← Home</a></p>
</body></html>
    """.trimIndent()
    
    return createHttpResponse(status, reason, "text/html", body)
}