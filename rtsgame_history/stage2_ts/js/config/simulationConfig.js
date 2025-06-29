// js/config/simulationConfig.js

export const SIMULATION_CONFIG = {
    // General simulation settings
    SIMULATION_DURATION_SECONDS: 10, // Default duration for recordings
    RECORD_AI_DECISIONS: true,       // Enable/disable recording by default
    HEADLESS_MODE: false,            // Run without rendering (true for server-side simulations)
    GAME_SEED: Date.now(),           // Initial seed for reproducibility (Date.now() for random)
    JOURNALING_MODE: 'FULL',       // 'FULL' for complete simulation logging, 'AI_ONLY' for decisions only
    JOURNALING_TIMEOUT_SECONDS: 0,  // 0 for no timeout, otherwise simulation will be fully journaled for this duration
    AUTO_EXCEPTION_REPORTING: true, // Automatically log exceptions to the journal

    // Camera settings for playback/viewing
    CAMERA_START_X: 0,
    CAMERA_START_Y: 0,
    CAMERA_START_ZOOM: 1,
    
    // Simulation logging settings
    LOG_SIMULATION_EVENTS: true,    // Log all simulation events to console
    LOG_EXCEPTIONS: true,           // Log all exceptions to console and journal
    LOG_WARNING_EVENTS: true,      // Log all warning events to console and journal
    MAX_UNCAUGHT_EXCEPTIONS: 10,    // Maximum number of uncaught exceptions before stopping

    // Add other simulation-specific parameters here as needed
    // e.g., initial unit counts, resource amounts, map generation parameters
    INITIAL_BLUE_MASS: 100,
    INITIAL_BLUE_ENERGY: 150,
    INITIAL_RED_MASS: 100,
    INITIAL_RED_ENERGY: 150,
    
    // Simulation error handling settings
    ERROR_HANDLING: {
        LOG_TO_FILE: true,          // Log errors to error.log file
        CONTINUE_ON_WARNING: true, // Continue simulation on warning events
        STOP_ON_CRITICAL_ERROR: true // Stop simulation on critical errors
    },
    
    // AI decision recording
    AI_DECISION_RECORDING_PERIOD: 1000, // Record AI decisions every 1000ms

    // Terrain generation parameters (if not handled by gameConstants.js)
    // WORLD_SIZE: 2000,
    // TILE_SIZE: 100,
    // GRID_SIZE: 20,
};