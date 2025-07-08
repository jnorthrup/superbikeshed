package rtsgame

import borg.trikeshed.lib.*
import rtsgame.demo.*
import rtsgame.core.*
import kotlinx.browser.*
import org.w3c.dom.*

actual fun platformMain() {
    println("Interactive RTS WebGPU Demo - WASM Version")
    
    window.onload = {
        setupGame()
    }
}

fun setupGame() {
    // Get canvas
    val canvas = document.getElementById("gameCanvas") as? HTMLCanvasElement
    if (canvas == null) {
        console.error("Canvas not found!")
        return
    }
    
    console.log("Initializing RTS game...")
    
    // Create game engine
    val launcher = RTSGameLauncher()
    
    // Hide loading screen
    document.getElementById("loading")?.setAttribute("style", "display: none")
    canvas.style.display = "block"
    
    // Set up game controls
    js("""
        window.rtsGame = {
            spawnArmy: function(team) {
                console.log('Spawning army for team ' + team);
                // Call Kotlin spawn function
            },
            
            togglePause: function() {
                console.log('Toggle pause');
            },
            
            benchmarkMode: function() {
                console.log('Starting benchmark...');
            }
        };
    """)
    
    // Start game loop
    var lastTime = 0.0
    
    fun gameLoop(currentTime: Double) {
        val deltaTime = if (lastTime > 0) (currentTime - lastTime) / 1000.0 else 0.016
        lastTime = currentTime
        
        // Update stats
        document.getElementById("fps")?.textContent = (1.0 / deltaTime).toInt().toString()
        
        window.requestAnimationFrame { gameLoop(it) }
    }
    
    window.requestAnimationFrame { gameLoop(it) }
    
    console.log("Game started!")
}

fun main() {
    platformMain()
}