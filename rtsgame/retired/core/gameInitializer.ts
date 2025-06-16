// js/core/gameInitializer.js
import { initThreeRenderer } from '../rendering/threeRenderer.js';
import { SupComCamera } from '../input/supcomCamera.js';
import { UIManager } from '../ui/uiManager.js';
import { GameContextManager } from './gameContextManager.js';
import { Simulation } from './simulation.js';

export class GameInitializer {
    constructor() {
        this.gameContextManager = new GameContextManager();
        this.uiManager = null;
        this.simulation = null;
        this.supcomCamera = null;
    }

    async initialize() {
        try {
            // Initialize game context
            this.gameContextManager.initialize();
            
            // Initialize UI
            this.uiManager = new UIManager(this.gameContextManager.gameContext);
            this.uiManager.initialize();
            
            // Initialize renderer if not in headless mode
            if (!this.gameContextManager.gameContext.HEADLESS_MODE) {
                console.log("Initializing Three.js renderer...");
                this.gameContextManager.gameContext.renderer = await initThreeRenderer(this.gameContextManager.gameContext.canvas);
                console.log("Three.js renderer initialized asynchronously.");
                
                // Initialize SupCom camera
                this.supcomCamera = new SupComCamera(this.gameContextManager.gameContext);
                console.log("SupCom camera system initialized");
            }
            
            // Initialize simulation
            this.simulation = new Simulation(this.gameContextManager.getSimulationCoreContext());
            
            // Start game loop
            this.startGameLoop();
            
            console.log("Game initialization completed successfully!");
        } catch (error) {
            console.error("Error during game initialization:", error);
            throw error;
        }
    }

    startGameLoop() {
        let lastTime = 0;
        
        const gameLoop = (timestamp) => {
            // Calculate delta time
            const deltaTime = timestamp - lastTime;
            lastTime = timestamp;
            
            // Update simulation
            if (this.simulation) {
                this.simulation.update(deltaTime);
            }
            
            // Update UI
            if (this.uiManager) {
                this.uiManager.update();
            }
            
            // Request next frame
            requestAnimationFrame(gameLoop);
        };
        
        // Start the game loop
        requestAnimationFrame(gameLoop);
    }
} 