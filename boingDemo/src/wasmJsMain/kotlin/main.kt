import com.example.boingdemo.BoingDemoSimulator
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.io.file.std.*
import korlibs.audio.sound.*
import korlibs.logger.Logger
import korlibs.time.seconds
import korlibs.math.geom.* // For Size

val logger = Logger("WASMMain")

suspend fun main() = Korge(
    windowSize = Size(640, 480), // Changed from SizeInt
    backgroundColor = Colors.BLACK,
    title = "Boing Demo WASM"
) {
    logger.info { "Starting Boing Demo on WASM." }
    val simulator = BoingDemoSimulator(stage!!.width.toFloat(), stage!!.height.toFloat())
    simulator.initialize()

    // Create a dummy bounce sound file if it doesn't exist for the subtask
    // In a real scenario, this would be a pre-existing resource.
    // For the subtask, we'll proceed as if it's loadable and handle potential errors.
    val bounceSound = try {
        resourcesVfs["bounce.wav"].readSound()
    } catch (e: Exception) {
        println("Warning: Could not load bounce.wav: ${e.message}")
        // Create a dummy sound or handle absence
        NativeSound.dummy // Or some other placeholder if available
    }

    // Walls (visual representation)
    // For simplicity, we'll just use the stage boundaries, physics handles actual collision
    // A visual representation could be added e.g. by adding thin rectangles at edges

    // Boing Ball (visual representation)
    val ballView = circle(simulator.boingBall.radius, Colors.RED) {
        position(simulator.boingBall.position.x, simulator.boingBall.position.y)
    }
    // TODO: Implement checkerboard pattern later if feasible with KorGE Graphics API

    addUpdater { dt ->
        simulator.update(dt.secondsf)
        ballView.position(simulator.boingBall.position.x, simulator.boingBall.position.y)

        if (simulator.ballBouncedThisFrame) {
            if (bounceSound != NativeSound.dummy) { // Check if sound loaded
                logger.info { "Playing bounce sound on WASM." }
                launchImmediately { // launch in a coroutine for sound playback
                    bounceSound.play()
                }
            }
        }
    }
}
