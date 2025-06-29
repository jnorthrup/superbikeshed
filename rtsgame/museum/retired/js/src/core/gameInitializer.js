"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.GameInitializer = void 0;
// js/core/gameInitializer.js
const threeRenderer_js_1 = require("../rendering/threeRenderer.js");
const supcomCamera_js_1 = require("../input/supcomCamera.js");
const uiManager_js_1 = require("../ui/uiManager.js");
const gameContextManager_js_1 = require("./gameContextManager.js");
const simulation_js_1 = require("./simulation.js");
class GameInitializer {
    constructor() {
        this.gameContextManager = new gameContextManager_js_1.GameContextManager();
        this.uiManager = null;
        this.simulation = null;
        this.supcomCamera = null;
    }
    initialize() {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                // Initialize game context
                this.gameContextManager.initialize();
                // Initialize UI
                this.uiManager = new uiManager_js_1.UIManager(this.gameContextManager.gameContext);
                this.uiManager.initialize();
                // Initialize renderer if not in headless mode
                if (!this.gameContextManager.gameContext.HEADLESS_MODE) {
                    console.log("Initializing Three.js renderer...");
                    this.gameContextManager.gameContext.renderer = yield (0, threeRenderer_js_1.initThreeRenderer)(this.gameContextManager.gameContext.canvas);
                    console.log("Three.js renderer initialized asynchronously.");
                    // Initialize SupCom camera
                    this.supcomCamera = new supcomCamera_js_1.SupComCamera(this.gameContextManager.gameContext);
                    console.log("SupCom camera system initialized");
                }
                // Initialize simulation
                this.simulation = new simulation_js_1.Simulation(this.gameContextManager.getSimulationCoreContext());
                // Start game loop
                this.startGameLoop();
                console.log("Game initialization completed successfully!");
            }
            catch (error) {
                console.error("Error during game initialization:", error);
                throw error;
            }
        });
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
exports.GameInitializer = GameInitializer;
