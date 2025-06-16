// Extracted app.js content from concatenated strategicAI.js file
// This contains the main application initialization code

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

// Define gameContext as a global object for the game
let gameContext = window.gameContext || {};
if (!window.gameContext) {
    window.gameContext = gameContext;

    // Get the canvas element and its 2D rendering context, add them to gameContext
    const canvas = document.getElementById('gameCanvas');
    if (canvas) {
        // Set canvas size to match window
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
        
        gameContext.canvas = canvas;
        gameContext.ctx = canvas.getContext('2d');

        // Handle window resize
        window.addEventListener('resize', () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            
            // Update camera canvas dimensions
            if (gameContext.camera) {
                gameContext.camera.canvasWidth = canvas.width;
                gameContext.camera.canvasHeight = canvas.height;
            }
        });
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
import { StrategicAI } from './ai/strategicAI.js'; // Import StrategicAI

// Initialize the random seed system using native Math.random()
// For production, you might want a more robust seed generation or a fixed seed for reproducibility.
let gameRandomSeed = Math.floor(Math.random() * 1000000);
console.log('Game random seed:', gameRandomSeed);

// Initialize the simulation
const simulation = new Simulation(gameContext);
gameContext.simulation = simulation;

// Initialize strategic AI for both players
gameContext.blueAI = new StrategicAI(gameContext, 'blue', 'blue', 'BALANCED');
gameContext.redAI = new StrategicAI(gameContext, 'red', 'red', 'AGGRESSIVE');

// Initialize modern UI manager
gameContext.modernUI = new ModernUIManager(gameContext);

// Initialize the enhanced recording system with journaling
initializeRecordingSystem(gameContext);

// Start recording with a random seed
startRandomSeedRecording(gameRandomSeed, gameContext);

// Initialize input handling
if (gameContext.initInputHandling) {
    gameContext.initInputHandling(gameContext);
}

// Game loop
function gameLoop(timestamp) {
    const deltaTime = timestamp - (gameContext.lastFrameTime || timestamp);
    gameContext.lastFrameTime = timestamp;

    if (!gameContext.gameState.paused) {
        // Update simulation
        simulation.update(deltaTime / 1000); // Convert to seconds
        
        // Update AI
        gameContext.blueAI.update(deltaTime / 1000, simulation);
        gameContext.redAI.update(deltaTime / 1000, simulation);
        
        // Update modern UI
        gameContext.modernUI.update(deltaTime / 1000);
    }

    // Render
    if (gameContext.ctx) {
        simulation.render(gameContext.ctx, gameContext.camera);
        
        // Render AI debug info if enabled
        if (gameContext.debugMode) {
            gameContext.blueAI.renderDebugInfo(gameContext.ctx, gameContext.camera);
            gameContext.redAI.renderDebugInfo(gameContext.ctx, gameContext.camera);
        }
        
        // Render modern UI
        gameContext.modernUI.render(gameContext.ctx);
    }

    requestAnimationFrame(gameLoop);
}

// Start the game loop
requestAnimationFrame(gameLoop);

// Initialize game when the page loads
document.addEventListener('DOMContentLoaded', () => {
    console.log('Game initializing...');
    
    // Additional initialization if needed
    if (gameContext.simulation) {
        gameContext.simulation.init();
    }
});

// Export gameContext for debugging
window.gameContext = gameContext;

/*
// OLD INITIALIZATION CODE - REPLACED BY NEW SIMULATION SYSTEM
const gameInitializer = new GameInitializer(gameContext);
gameInitializer.initialize().catch(error => {
    console.error("Failed to initialize game:", error);
});
*/