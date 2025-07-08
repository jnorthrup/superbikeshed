// js/core/gameContextManager.js
import { SIMULATION_CONFIG } from '../config/simulationConfig.js';
import { initializeRecordingSystem, startRandomSeedRecording } from './recordingUtils.js';
import battleJournal from '../ai/battleJournal.js';
import { Effect } from './entities/effect.js';
import { Caption } from './entities/caption.js';

export class GameContextManager {
    constructor() {
        this.gameContext = window.gameContext || {};
        if (!window.gameContext) {
            window.gameContext = this.gameContext;
        }
    }

    initialize() {
        // Initialize core game state
        this.initializeGameState();
        
        // Initialize camera settings
        this.initializeCamera();
        
        // Initialize recording system
        this.initializeRecordingSystem();
        
        // Initialize simulation parameters
        this.initializeSimulationParameters();
    }

    initializeGameState() {
        this.gameContext.battleJournal = battleJournal;
        this.gameContext.Effect = Effect;
        this.gameContext.Caption = Caption;
        this.gameContext.units = [];
        this.gameContext.buildings = [];
        this.gameContext.effects = [];
        this.gameContext.captions = [];
        this.gameContext.projectiles = [];
        this.gameContext.terrain = [];
        this.gameContext.resourceNodes = [];
        this.gameContext.pathfindingGrid = [];
        this.gameContext.resources = { blue: {}, red: {} };
        
        this.gameContext.gameState = {
            paused: false,
            gameTime: 0,
            winner: null,
            events: [],
            fpvMode: false,
            aimingGrenade: false
        };
    }

    initializeCamera() {
        const ZOOM_FACTOR = 1.2;
        const MIN_ZOOM_APP = 0.01;
        const MAX_ZOOM_APP = 50.0;
        const CAMERA_SMOOTHING = 0.15;
        const MOMENTUM_DECAY = 0.95;
        const MAX_STRATEGIC_ZOOM_APP = 2.0;
        const MIN_TACTICAL_ZOOM_APP = 8.0;
        const MAX_TACTICAL_ANGLE_APP = 45;
        const KEYBOARD_MOVE_SPEED = 1500;
        const MODIFIER_SPEED_MULTIPLIER = 3;

        this.gameContext.camera = {
            x: SIMULATION_CONFIG.CAMERA_START_X || 2500,
            y: SIMULATION_CONFIG.CAMERA_START_Y || 2500,
            zoom: SIMULATION_CONFIG.CAMERA_START_ZOOM || 0.5,
            targetX: SIMULATION_CONFIG.CAMERA_START_X || 2500,
            targetY: SIMULATION_CONFIG.CAMERA_START_Y || 2500,
            targetZoom: SIMULATION_CONFIG.CAMERA_START_ZOOM || 0.5,
            velocityX: 0,
            velocityY: 0,
            velocityZoom: 0,
            isDragging: false,
            lastMouseX: 0,
            lastMouseY: 0,
            angle: 0,
            targetAngle: 0,
            rotation: 0,
            targetRotation: 0,
            canvasWidth: window.innerWidth,
            canvasHeight: window.innerHeight,
            minZoom: MIN_ZOOM_APP,
            maxZoom: MAX_ZOOM_APP,
            autoCamera: true,
            cameraTarget: null,
            cameraTimer: 0
        };
    }

    initializeRecordingSystem() {
        // Initialize the random seed system
        this.gameContext.GAME_SEED = SIMULATION_CONFIG.GAME_SEED || battleJournal.seed;
        this.gameContext.seedRandom = {
            random: function() { return Math.random(); },
            init: function(seed) { /* Not truly seeded without a library, but keeps the API */ }
        };

        // Initialize the enhanced journaling system
        initializeRecordingSystem();

        // Start a random seed recording if not using full journaling
        if (this.gameContext.JOURNALING_MODE !== 'FULL') {
            startRandomSeedRecording(this.gameContext, this.gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS);
        }
    }

    initializeSimulationParameters() {
        this.gameContext.RECORD_AI_DECISIONS = SIMULATION_CONFIG.RECORD_AI_DECISIONS;
        this.gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = 10;
        this.gameContext.HEADLESS_MODE = SIMULATION_CONFIG.HEADLESS_MODE;
        this.gameContext.JOURNALING_MODE = SIMULATION_CONFIG.JOURNALING_MODE;
        this.gameContext.JOURNALING_TIMEOUT = SIMULATION_CONFIG.JOURNALING_TIMEOUT_SECONDS;
    }

    getSimulationCoreContext() {
        return {
            GAME_SEED: this.gameContext.GAME_SEED,
            seedRandom: this.gameContext.seedRandom,
            HEADLESS_MODE: this.gameContext.HEADLESS_MODE,
            RECORD_AI_DECISIONS: this.gameContext.RECORD_AI_DECISIONS,
            RECORD_AI_DECISIONS_DURATION_SECONDS: this.gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS,
            battleJournal: this.gameContext.battleJournal,
            resourceNodes: this.gameContext.resourceNodes
        };
    }
} 