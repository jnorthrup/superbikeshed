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

val logger = Logger("JvmMain")

// For JVM, Korge might be initialized within a runBlocking or similar,
// but a simple suspend fun main() should also work with Korge's setup.
// Alternatively, an object with @JvmStatic main can be used.
// Let's stick to the suspend fun main() for consistency with other targets.
suspend fun main() = Korge(
    windowSize = Size(640, 480),
    backgroundColor = Colors.DARKGREY, // Different background color
    title = "Boing Demo JVM"
) {
    logger.info { "Starting Boing Demo on JVM." }
    val simulator = BoingDemoSimulator(stage!!.width.toFloat(), stage!!.height.toFloat())
    simulator.initialize()

    val bounceSound = try {
        resourcesVfs["bounce.wav"].readSound() // Assumes bounce.wav from commonMain/resources
    } catch (e: Exception) {
        println("Warning: Could not load bounce.wav: ${e.message}")
        NativeSound.dummy
    }

    val ballView = circle(simulator.boingBall.radius, Colors.ORANGE) { // Different ball color
        position(simulator.boingBall.position.x, simulator.boingBall.position.y)
    }

    addUpdater { dt ->
        simulator.update(dt.secondsf)
        ballView.position(simulator.boingBall.position.x, simulator.boingBall.position.y)

        if (simulator.ballBouncedThisFrame) {
            if (bounceSound != NativeSound.dummy) {
                logger.info { "Playing bounce sound on JVM." }
                launchImmediately {
                    bounceSound.play()
                }
            }
        }
    }
}
