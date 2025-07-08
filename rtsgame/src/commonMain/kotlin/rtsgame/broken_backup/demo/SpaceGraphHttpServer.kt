import kotlin.math.*
package rtsgame.demo
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import borg.trikeshed.lib.j as join
import borg.trikeshed.net.http.HttpServer
import borg.trikeshed.net.http.HttpServerConfig
import borg.trikeshed.net.http.HttpServerPort
import borg.trikeshed.net.http.HttpRequest
import borg.trikeshed.net.http.HttpResponse
import borg.trikeshed.net.http.HttpStatusCode
import borg.trikeshed.net.http.HttpReasonPhrase
import borg.trikeshed.net.http.HttpHeaderName
import borg.trikeshed.net.http.HttpHeaderValue
import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.game.GameState
import com.rtsgame.shared.map.Position
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.webgpu.*

/**
 * HTTP server for SpaceGraph RTS visualization
 */
class SpaceGraphHttpServer {
    internal val gameEngine = GameEngine()
    internal val spaceGraphRenderer = RTSSpaceGraphRenderer()
    internal val webgpuRenderer = CommonWebGPUSpaceGraph()
    
    internal var currentGameState: GameState? = null
    internal var currentCamera: CameraState? = null
    
    suspend fun startServer(port: Int = 8080) {
        // Initialize WebGPU renderer
        if (!webgpuRenderer.initialize()) {
            throw IllegalStateException("Failed to initialize WebGPU renderer")
        }
        
        // Create initial game state
        currentGameState = createInitialGameState()
        currentCamera = createDefaultCamera()
        
        // Create HTTP server
        val server = HttpServer(
            config = HttpServerConfig(
                port = HttpServerPort(port)
            ),
            handler = { request: HttpRequest ->
                when (request.path.value) {
                    "/" -> serveHtmlPage(request)
                    "/api/game-state" -> serveGameState(request)
                    "/api/render" -> serveRenderFrame(request)
                    "/api/update" -> updateGameState(request)
                    else -> HttpResponse(
                        status = HttpStatusCode(404),
                        reasonPhrase = HttpReasonPhrase("Not Found"),
                        headers = createEmptyHeaders(),
                        body = "Not Found".encodeToByteArray().toSeries()
                    )
                }
            }
        )
        
        server.start()
    }
    
    internal fun serveHtmlPage(request: HttpRequest): HttpResponse {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>SpaceGraph RTS Visualization</title>
                <style>
                    body { margin: 0; overflow: hidden; }
                    canvas { width: 100vw; height: 100vh; }
                </style>
            </head>
            <body>
                <canvas id="spacegraph"></canvas>
                <script>
                    const canvas = document.getElementById('spacegraph');
                    const ctx = canvas.getContext('webgpu');
                    
                    // Initialize WebGPU
                    async function initWebGPU() {
                        const adapter = await navigator.gpu.requestAdapter();
                        const device = await adapter.requestDevice();
                        
                        // Configure canvas
                        const format = navigator.gpu.getPreferredCanvasFormat();
                        ctx.configure({
                            device: device,
                            format: format,
                            alphaMode: 'premultiplied'
                        });
                        
                        return { device, format };
                    }
                    
                    // Main render loop
                    async function renderLoop() {
                        const { device, format } = await initWebGPU();
                        
                        while (true) {
                            // Get game state
                            const gameState = await fetch('/api/game-state').then(r => r.json());
                            
                            // Get render frame
                            const frame = await fetch('/api/render').then(r => r.arrayBuffer());
                            
                            // Render frame
                            const texture = ctx.getCurrentTexture();
                            const commandEncoder = device.createCommandEncoder();
                            const renderPass = commandEncoder.beginRenderPass({
                                colorAttachments: [{
                                    view: texture.createView(),
                                    clearValue: { r: 0.0, g: 0.0, b: 0.0, a: 1.0 },
                                    loadOp: 'clear',
                                    storeOp: 'store'
                                }]
                            });
                            
                            // TODO: Implement actual rendering using WebGPU
                            
                            renderPass.end();
                            device.queue.submit([commandEncoder.finish()]);
                            
                            // Request next frame
                            requestAnimationFrame(renderLoop);
                        }
                    }
                    
                    // Start render loop
                    renderLoop();
                </script>
            </body>
            </html>
        """.trimIndent()
        
        return HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = createHeaders(
                HttpHeaderName("Content-Type") to HttpHeaderValue("text/html")
            ),
            body = html.encodeToByteArray().toSeries()
        )
    }
    
    internal fun serveGameState(request: HttpRequest): HttpResponse {
        val gameState = currentGameState ?: throw IllegalStateException("Game state not initialized")
        
        val json = """
            {
                "tick": ${gameState.currentTime},
                "entities": [
                    ${gameState.entities.values.joinToString(",") { entity ->
                        """
                        {
                            "id": "${entity.id}",
                            "position": {
                                "x": ${entity.position.x},
                                "y": ${entity.position.y}
                            },
                            "health": ${entity.health},
                            "team": ${entity.team}
                        }
                        """.trimIndent()
                    }}
                ]
            }
        """.trimIndent()
        
        return HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = createHeaders(
                HttpHeaderName("Content-Type") to HttpHeaderValue("application/json")
            ),
            body = json.encodeToByteArray().toSeries()
        )
    }
    
    internal fun serveRenderFrame(request: HttpRequest): HttpResponse {
        val gameState = currentGameState ?: throw IllegalStateException("Game state not initialized")
        val camera = currentCamera ?: throw IllegalStateException("Camera not initialized")
        
        val renderResult = webgpuRenderer.renderGameState(gameState, camera)
        
        // TODO: Implement actual frame data serialization
        val frameData = ByteArray(0).toSeries()
        
        return HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = createHeaders(
                HttpHeaderName("Content-Type") to HttpHeaderValue("application/octet-stream")
            ),
            body = frameData
        )
    }
    
    internal fun updateGameState(request: HttpRequest): HttpResponse {
        val gameState = currentGameState ?: throw IllegalStateException("Game state not initialized")
        currentGameState = gameEngine.simulateTick(gameState)
        
        return HttpResponse(
            status = HttpStatusCode(200),
            reasonPhrase = HttpReasonPhrase("OK"),
            headers = createHeaders(
                HttpHeaderName("Content-Type") to HttpHeaderValue("application/json")
            ),
            body = "{\"success\": true}".encodeToByteArray().toSeries()
        )
    }
    
    internal fun createInitialGameState(): GameState {
        val entities = mutableMapOf<String, Entity>()
        
        // Player 1 forces
        val commander1 = CoreEntity(
            id = "commander_p1",
            position = Position(100f, 100f),
            health = 100f,
            maxHealth = 100f,
            speed = 5f,
            team = 1,
            name = "Commander"
        )
        entities[commander1.id] = commander1
        
        val tank1 = CoreEntity(
            id = "tank_p1_1",
            position = Position(120f, 80f),
            health = 75f,
            maxHealth = 100f,
            speed = 3f,
            team = 1,
            name = "Tank"
        )
        entities[tank1.id] = tank1
        
        val scout1 = CoreEntity(
            id = "scout_p1_1",
            position = Position(90f, 130f),
            health = 30f,
            maxHealth = 50f,
            speed = 8f,
            team = 1,
            name = "Scout"
        )
        entities[scout1.id] = scout1
        
        // Player 2 forces
        val commander2 = CoreEntity(
            id = "commander_p2",
            position = Position(300f, 300f),
            health = 100f,
            maxHealth = 100f,
            speed = 5f,
            team = 2,
            name = "Commander"
        )
        entities[commander2.id] = commander2
        
        val tank2 = CoreEntity(
            id = "tank_p2_1",
            position = Position(280f, 320f),
            health = 85f,
            maxHealth = 100f,
            speed = 3f,
            team = 2,
            name = "Tank"
        )
        entities[tank2.id] = tank2
        
        // Neutral building
        val resource = CoreEntity(
            id = "resource_1",
            position = Position(200f, 200f),
            health = 50f,
            maxHealth = 50f,
            speed = 0f,
            team = 0,
            name = "Resource"
        )
        entities[resource.id] = resource
        
        return GameState(
            entities = entities,
            resources = mapOf(),
            currentTime = 0
        )
    }
    
    internal fun createDefaultCamera(): CameraState {
        return CameraState(
            position = Vector3D(200.0, 200.0, 300.0),
            target = Vector3D(200.0, 200.0, 0.0),
            fov = 60f,
            aspect = 16f / 9f
        )
    }
    
    internal fun createHeaders(vararg headers: Pair<HttpHeaderName, HttpHeaderValue>): Series2<HttpHeaderName, HttpHeaderValue> {
        return headers.size join { i: Int ->
            headers[i].first join headers[i].second
        }
    }
    
    internal fun createEmptyHeaders(): Series2<HttpHeaderName, HttpHeaderValue> {
        return 0 join { _: Int -> HttpHeaderName("") join HttpHeaderValue("") }
    }
}

/**
 * Start the SpaceGraph HTTP server
 */
suspend fun startSpaceGraphHttpServer(port: Int = 8080) {
    val server = SpaceGraphHttpServer()
    server.startServer(port)
} 