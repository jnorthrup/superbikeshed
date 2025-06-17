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

val logger = Logger("NativeMain")

// Entry point for Native might differ slightly if @JvmStatic is needed for a main in an object
// but for a top-level main, this should be fine.
// KorGE typically handles the main loop setup.
suspend fun main() = Korge(
    windowSize = Size(640, 480),
    backgroundColor = Colors.DARKBLUE, // Different color to distinguish from WASM
    title = "Boing Demo Native"
) {
    logger.info { "Starting Boing Demo on Native." }
    val simulator = BoingDemoSimulator(stage!!.width.toFloat(), stage!!.height.toFloat())
    simulator.initialize()

    val bounceSound = try {
        resourcesVfs["bounce.wav"].readSound() // Assumes bounce.wav from commonMain/resources
    } catch (e: Exception) {
        println("Warning: Could not load bounce.wav: ${e.message}")
        NativeSound.dummy
    }

    val ballView = circle(simulator.boingBall.radius, Colors.GREEN) { // Different ball color
        position(simulator.boingBall.position.x, simulator.boingBall.position.y)
    }

    addUpdater { dt ->
        simulator.update(dt.secondsf)
        ballView.position(simulator.boingBall.position.x, simulator.boingBall.position.y)

        if (simulator.ballBouncedThisFrame) {
            if (bounceSound != NativeSound.dummy) {
                logger.info { "Playing bounce sound on Native." }
                launchImmediately {
                    bounceSound.play()
                }
            }
        }
    }
}
