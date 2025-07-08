import kotlin.math.*
package rtsgame.config
import kotlinx.datetime.*
import kotlin.time.*

import kotlin.js.Date

// Direct translation of js/config/simulationConfig.js
// NO IMPROVEMENTS - preserving exact JS behavior

object SIMULATION_CONFIG {
    // General simulation settings
    const val SIMULATION_DURATION_SECONDS = 10 // Default duration for recordings
    const val RECORD_AI_DECISIONS = true       // Enable/disable recording by default
    const val HEADLESS_MODE = false            // Run without rendering (true for server-side simulations)
    val GAME_SEED = Date().getTime().toLong() // Initial seed for reproducibility (Date.now() for random)
    const val JOURNALING_MODE = "FULL"         // 'FULL' for complete simulation logging, 'AI_ONLY' for decisions only
    const val JOURNALING_TIMEOUT_SECONDS = 0   // 0 for no timeout, otherwise simulation will be fully journaled for this duration
    const val AUTO_EXCEPTION_REPORTING = true  // Automatically log exceptions to the journal

    // Camera settings for playback/viewing
    const val CAMERA_START_X = 0
    const val CAMERA_START_Y = 0
    const val CAMERA_START_ZOOM = 1
    
    // Simulation logging settings
    const val LOG_SIMULATION_EVENTS = true    // Log all simulation events to console
    const val LOG_EXCEPTIONS = true           // Log all exceptions to console and journal
    const val LOG_WARNING_EVENTS = true       // Log all warning events to console and journal
    const val MAX_UNCAUGHT_EXCEPTIONS = 10    // Maximum number of uncaught exceptions before stopping

    // Add other simulation-specific parameters here as needed
    // e.g., initial unit counts, resource amounts, map generation parameters
    const val INITIAL_BLUE_MASS = 100
    const val INITIAL_BLUE_ENERGY = 150
    const val INITIAL_RED_MASS = 100
    const val INITIAL_RED_ENERGY = 150
    
    // Simulation error handling settings
    object ERROR_HANDLING {
        const val LOG_TO_FILE = true          // Log errors to error.log file
        const val CONTINUE_ON_WARNING = true  // Continue simulation on warning events
        const val STOP_ON_CRITICAL_ERROR = true // Stop simulation on critical errors
    }
    
    // AI decision recording
    const val AI_DECISION_RECORDING_PERIOD = 1000 // Record AI decisions every 1000ms

    // Terrain generation parameters (if not handled by gameConstants.js)
    // const val WORLD_SIZE = 2000
    // const val TILE_SIZE = 100
    // const val GRID_SIZE = 20
}