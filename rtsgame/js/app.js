// Define gameContext as a global object for the game
let gameContext = window.gameContext || {};
if (!window.gameContext) {
    window.gameContext = gameContext;

    // Get the canvas element and its 2D rendering context, add them to gameContext
    window.gameContext.canvas = gameContext.canvas || document.getElementById('gameCanvas');
    if (window.gameContext.canvas) {
        // Set canvas to full screen
        const resizeCanvas = () => {
            window.gameContext.canvas.width = window.innerWidth;
            window.gameContext.canvas.height = window.innerHeight;
            if (gameContext.camera) {
                gameContext.camera.canvasWidth = window.innerWidth;
                gameContext.camera.canvasHeight = window.innerHeight;
            }
        };
        
        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);
        console.log("Main game canvas initialized successfully in main.js!");
    } else {
        console.error("Main game canvas element not found!");
    }

    // Minimap functionality removed

    // Initialize core game functions if they aren't already
    // window.gameContext.initGame = initGame; // OLD
    // window.gameContext.gameLoop = gameLoop; // OLD

    // Initialize input handling function
    if (!window.gameContext.initInputHandling) {
        window.gameContext.initInputHandling = initInputHandling;
    }
}

// Import necessary modules
import { InputManager } from './input/inputManager.js'; // NEW: Import InputManager
import { SupComCamera } from './input/supcomCamera.js'; // NEW: Import SupCom camera

import { initInputHandling } from './input/inputHandler.js';
// import { gameLoop, initGame } from './core/game.js'; // OLD: To be replaced
import { Simulation } from './core/simulation.js';
import { startRandomSeedRecording } from './core/recordingUtils.js';
import { SIMULATION_CONFIG } from './config/simulationConfig.js';
import battleJournal from './ai/battleJournal.js'; // Import battleJournal
import { Effect } from './core/entities/effect.js'; // Import Effect class
import { Caption } from './core/entities/caption.js'; // Import Caption class
import { initThreeRenderer } from './rendering/threeRenderer.js'; // Import Three.js renderer
// Minimap functionality removed
import { ModernUIManager } from './ui/modernUIManager.js'; // NEW: Import Modern UI Manager

// Initial game setup
// Initialize gameContext properties for the first time
gameContext.battleJournal = battleJournal; // Assign battleJournal to gameContext
gameContext.Effect = Effect; // Assign Effect class to gameContext
gameContext.Caption = Caption; // Assign Caption class to gameContext
gameContext.units = [];
gameContext.buildings = [];
gameContext.effects = [];
gameContext.captions = [];
gameContext.projectiles = [];
gameContext.terrain = []; // Initialize terrain
gameContext.resourceNodes = []; // Initialize resource nodes
gameContext.pathfindingGrid = []; // Initialize pathfinding grid
gameContext.resources = { blue: {}, red: {} }; // Initialize resources
// Camera constants (some might be duplicates from SIMULATION_CONFIG, consolidate if necessary)
const ZOOM_FACTOR = 1.2;
const MIN_ZOOM_APP = 0.01; // Renamed to avoid conflict if SIMULATION_CONFIG has MIN_ZOOM
const MAX_ZOOM_APP = 50.0; // Renamed
const CAMERA_SMOOTHING = 0.15;
const MOMENTUM_DECAY = 0.95;
const MAX_STRATEGIC_ZOOM_APP = 2.0; // Renamed
const MIN_TACTICAL_ZOOM_APP = 8.0;  // Renamed
const MAX_TACTICAL_ANGLE_APP = 45;  // Renamed
const KEYBOARD_MOVE_SPEED = 1500; // Adjusted for world units per second (approx)
const MODIFIER_SPEED_MULTIPLIER = 3;

gameContext.camera = {
    x: SIMULATION_CONFIG.CAMERA_START_X || 2500, // Center of 5000x5000 world
    y: SIMULATION_CONFIG.CAMERA_START_Y || 2500, // Center of 5000x5000 world
    zoom: SIMULATION_CONFIG.CAMERA_START_ZOOM || 0.5, // Zoom out to see more terrain
    targetX: SIMULATION_CONFIG.CAMERA_START_X || 2500, // From main_simulation.js
    targetY: SIMULATION_CONFIG.CAMERA_START_Y || 2500, // From main_simulation.js
    targetZoom: SIMULATION_CONFIG.CAMERA_START_ZOOM || 0.5, // From main_simulation.js
    velocityX: 0, // From main_simulation.js
    velocityY: 0, // From main_simulation.js
    velocityZoom: 0, // From main_simulation.js
    isDragging: false, // From main_simulation.js
    lastMouseX: 0, // From main_simulation.js
    lastMouseY: 0, // From main_simulation.js
    angle: 0, // Top-down view, no tilt
    targetAngle: 0, // Top-down view, no tilt
    rotation: 0, // Standard top-down view, no rotation
    targetRotation: 0, // Keep camera fixed without rotation
    canvasWidth: window.innerWidth,
    canvasHeight: window.innerHeight,
    minZoom: MIN_ZOOM_APP, // Use new constant
    maxZoom: MAX_ZOOM_APP, // Use new constant
    autoCamera: true, // This was from the old app.js camera, might be overridden by new controls
    cameraTarget: null, // This was from the old app.js camera
    cameraTimer: 0, // This was from the old app.js camera
};
gameContext.gameState = {
    paused: false,
    gameTime: 0,
    winner: null,
    events: [],
    fpvMode: false,
    aimingGrenade: false
};

// Initialize the Three.js renderer - this is now async
// gameContext.renderer = initThreeRenderer(gameContext.canvas); // Old synchronous call

// Initialize the enhanced journaling system
import { initializeRecordingSystem } from './core/recordingUtils.js';
  
// Initialize the random seed system using native Math.random()
// For production, you might want a more robust seed generation or a fixed seed for reproducibility.
gameContext.GAME_SEED = SIMULATION_CONFIG.GAME_SEED || battleJournal.seed;
// Replaced Math.seedrandom with a simple wrapper around Math.random for browser compatibility
gameContext.seedRandom = {
    random: function() { return Math.random(); },
    init: function(seed) { /* Not truly seeded without a library, but keeps the API */ }
};

// Initialize simulation parameters from config
gameContext.RECORD_AI_DECISIONS = SIMULATION_CONFIG.RECORD_AI_DECISIONS;
gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = 10; // Set to exactly 10 seconds for this simulation
gameContext.HEADLESS_MODE = SIMULATION_CONFIG.HEADLESS_MODE;
gameContext.JOURNALING_MODE = SIMULATION_CONFIG.JOURNALING_MODE;
gameContext.JOURNALING_TIMEOUT = SIMULATION_CONFIG.JOURNALING_TIMEOUT_SECONDS;

// Initialize the enhanced journaling system with current game context
initializeRecordingSystem();

// Start a random seed recording based on configured duration if not using full journaling
if (gameContext.JOURNALING_MODE !== 'FULL') {
    startRandomSeedRecording(gameContext, gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS);
}

// Initialize SelectionManager
import SelectionManager from './ui/selectionManager.js';
import { WindowManager } from './ui/borderLayout.js'; // Import WindowManager
gameContext.selectionManager = new SelectionManager(gameContext); // Pass gameContext to manager

// Initialize WindowManager and allow drawing
gameContext.windowManager = new WindowManager();
gameContext.allowWindowDrawing = true;

// Initialize Modern UI Manager
let modernUIManager = null;

// Initialize SupCom camera (will be done after renderer is ready)
let supcomCamera = null;

// Comprehensive HMR protection - prevent multiple initialization
if (window.gameInitialized) {
    console.log("Game already initialized, skipping duplicate initialization due to HMR");
    // Early exit to prevent duplicate initialization
    if (module.hot) {
        module.hot.decline(); // Disable HMR for this module to prevent reloading
    }
} else {
    window.gameInitialized = true;

    // Stop any existing animation loops
    if (window.gameAnimationId) {
        cancelAnimationFrame(window.gameAnimationId);
        window.gameAnimationId = null;
    }

// Initialize input handling and start the game - only run once
(async () => {
try {
if (!gameContext.HEADLESS_MODE) {
    console.log("Initializing Three.js renderer...");
    gameContext.renderer = await initThreeRenderer(gameContext.canvas); // New asynchronous call
    console.log("Three.js renderer initialized asynchronously.");

    // initInputHandling(gameContext); // Old input handling - review if it conflicts or can be merged/removed
                                   // For now, new handlers will be added.
    
    // Initialize SupCom camera system
    supcomCamera = new SupComCamera(gameContext);
    console.log("SupCom camera system initialized");
    
    // NEW: Clear all existing windows on game launch when not in headless mode
    if (gameContext.windowManager && Array.isArray(gameContext.windowManager.windows)) {
        gameContext.windowManager.windows = [];
    } else if (gameContext.windowManager) {
        console.warn("windowManager.windows is not a directly clearable array. Cannot auto-clear windows on startup.");
    }

    // Initialize the game state
    // initGame(gameContext); // OLD: This is now handled by Simulation constructor and init()

    // Prepare context for the new Simulation engine
    // This context should only contain what the simulation core truly needs.
    // Other properties on the global gameContext (like renderer, camera, UI managers)
    // will remain on the global gameContext for UI/rendering layers to use.
    const simulationCoreContext = {
        GAME_SEED: gameContext.GAME_SEED,
        seedRandom: gameContext.seedRandom, // The object with init/random methods
        HEADLESS_MODE: gameContext.HEADLESS_MODE,
        RECORD_AI_DECISIONS: gameContext.RECORD_AI_DECISIONS,
        RECORD_AI_DECISIONS_DURATION_SECONDS: gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS,
        battleJournal: gameContext.battleJournal,
        // resourceNodes are used by Engineer AI. If this AI logic moves into simulation, it might get this via context.
        // For now, passing it if unit logic (performSupportRole) still expects it on the passed context.
        resourceNodes: gameContext.resourceNodes, 
        // Effect and Caption classes are now imported directly by Unit/Building where needed.
        // No longer passing gameContext.Effect or gameContext.Caption to Simulation.
    };

    const simulation = new Simulation(simulationCoreContext);
    await simulation.init(); // Initialize simulation state, terrain, entities

    // After simulation initializes and terrain is ready, create the Three.js terrain mesh
    if (gameContext.renderer && simulation?.terrain) {
        gameContext.renderer.updateTerrain(simulation.terrain); // Explicitly create/update terrain after init
    }

    // Hide the loading overlay once simulation is initialized
    const loadingOverlay = document.getElementById('loading');
    if (loadingOverlay) {
        loadingOverlay.style.display = 'none';
    }

    // Make the simulation instance globally accessible for debugging or specific UI interactions.
    // This helps bridge the gap if some parts of UI still expect a global way to access sim state.
    window.simulation = simulation;

    // Create and store InputManager instance
    const inputManager = new InputManager(simulation);
    gameContext.inputManager = inputManager; // Make it available to initInputHandling via gameContext

    // Initialize Modern UI Manager
    modernUIManager = new ModernUIManager(gameContext);
    console.log("Modern RTS UI initialized.");

    // Start the game loop
    console.log("Starting game loop with new Simulation engine...");
    let lastFrameTime = 0;
    function animate(timestamp) {
        const deltaTime = lastFrameTime > 0 ? (timestamp - lastFrameTime) / 1000 : (1/60); // seconds
        lastFrameTime = timestamp;
        
        // Add battle detection and camera adjustment
        if (simulation.entityManager && simulation.entityManager.units && Array.isArray(simulation.entityManager.units)) {
            const battlingUnits = simulation.entityManager.units.filter(unit =>
                unit && (unit.state === 'attacking' || (unit.health !== undefined && unit.maxHealth !== undefined && unit.health < unit.maxHealth))
            );
            if (battlingUnits.length > 0) {
                const battleCenterX = battlingUnits.reduce((sum, unit) => sum + (unit.x || 0), 0) / battlingUnits.length;
                const battleCenterY = battlingUnits.reduce((sum, unit) => sum + (unit.y || 0), 0) / battlingUnits.length;
                gameContext.camera.targetX = battleCenterX;
                gameContext.camera.targetY = battleCenterY;
                gameContext.camera.targetZoom = Math.max(2.0, gameContext.camera.targetZoom);  // Zoom in slightly for focus
            }
        }

        // Update SupCom camera system
        if (supcomCamera) {
            supcomCamera.update(deltaTime);
        }


        // Update the simulation state
        const continueLoop = simulation.gameLoop(timestamp); // timestamp is still used by sim's internal deltaTime

        // Render the current state if not in headless mode
        if (!gameContext.HEADLESS_MODE && gameContext.renderer) {
            try {
                // Camera is already updated by updateCameraLogic which calls renderer.updateCamera
                // gameContext.renderer.updateCamera(gameContext.camera); // This is now done in updateCameraLogic
                
                // Renderer takes the simulation instance to get entities/state, 
                // and the global gameContext for other UI related info like camera.
                gameContext.renderer.render(simulation, gameContext); 
            } catch (e) {
                console.error("Error during WebGL rendering in main loop:", e);
            }

            // Update Modern UI Manager
            if (modernUIManager) {
                // Add extra safety checks to prevent filter errors
                const units = (simulation.entityManager && Array.isArray(simulation.entityManager.units)) ? simulation.entityManager.units : [];
                const buildings = (simulation.entityManager && Array.isArray(simulation.entityManager.buildings)) ? simulation.entityManager.buildings : [];
                
                const uiContext = {
                    ...gameContext,
                    units: units,
                    buildings: buildings,
                    resources: simulation.resources || { blue: {}, red: {} },
                    gameState: simulation.gameState || gameContext.gameState,
                    terrain: simulation.terrain || null,
                    resourceNodes: simulation.resourceNodes || []
                };
                modernUIManager.update(uiContext);
            }
        }
        
        // Check if the simulation or other logic determined the game should end
        if (!continueLoop || (simulation.gameState.winner && simulation.gameState.winner !== "RECORDING_COMPLETE")) {
            console.log("Game loop terminated.");
            if (simulation.gameState.winner === "RECORDING_COMPLETE" && gameContext.battleJournal && gameContext.battleJournal.isRecording) {
                 const recording = gameContext.battleJournal.stopRecording();
                 // TODO: Implement logic for sending/saving the recording data
                 console.log("Recording stopped in main.js due to RECORDING_COMPLETE state.");
                 // Example: sendRecordingToServer(recording); 
            }
            // Perform any other cleanup or end-game display logic here
            return; // Stop requesting new frames
        }

        window.gameAnimationId = requestAnimationFrame(animate);
    }
    window.gameAnimationId = requestAnimationFrame(animate);
}
} catch (error) {
    console.error("Error during initialization:", error);
    
    // Hide loading overlay even on error
    const loadingOverlay = document.getElementById('loading');
    if (loadingOverlay) {
        loadingOverlay.style.display = 'none';
        loadingOverlay.innerHTML = '<div style="color: red;">Error loading game. Please refresh the page.</div>';
        loadingOverlay.style.display = 'block';
    }
}
})(); // Close the async IIFE

} // Close the HMR protection if block

// import { GameState, TensorOps, DeterministicRNG } from '../src/trikeshed/core';
// import { GameEngine } from './core/gameEngine';
// Moved these imports to prevent issues - they're not currently used anyway
// import { ThreeRenderer } from './rendering/threeRenderer';
// import { UIManager } from './ui/uiManager';
// import { GameInitializer } from './core/gameInitializer.js';

// Commented out problematic Game class that uses non-existent imports
/*
class Game {
    constructor() {
        // Initialize core systems
        this.rng = new DeterministicRNG(Date.now());
        this.gameState = new GameState(
            TensorOps.create([1000, 1000], null) // 1000x1000 world grid
        );
        
        // Initialize subsystems
        this.engine = new GameEngine(this.gameState, this.rng);
        this.renderer = new ThreeRenderer();
        this.input = new InputManager();
        this.ui = new UIManager();
        
        // Bind update loop
        this.update = this.update.bind(this);
        this.lastTime = 0;
        
        // Start game loop
        requestAnimationFrame(this.update);
    }
    
    update(currentTime) {
        const deltaTime = (currentTime - this.lastTime) / 1000;
        this.lastTime = currentTime;
        
        // Update game state
        this.engine.update(deltaTime);
        
        // Render frame
        this.renderer.render(this.gameState);
        
        // Update UI
        this.ui.update(this.gameState);
        
        // Continue game loop
        requestAnimationFrame(this.update);
    }
}

// Initialize game when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.game = new Game();
});

// Initialize the game
const gameInitializer = new GameInitializer();
gameInitializer.initialize().catch(error => {
    console.error("Failed to initialize game:", error);
});
*/
