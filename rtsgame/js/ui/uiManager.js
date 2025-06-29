import { WindowManager } from './borderLayout.js';
import SelectionManager from './selectionManager.js';

export class UIManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.windowManager = new WindowManager();
        this.selectionManager = new SelectionManager(gameContext);
        this.allowWindowDrawing = true;
    }

    initialize() {
        // Clear existing windows on game launch
        if (this.windowManager && Array.isArray(this.windowManager.windows)) {
            this.windowManager.windows = [];
        } else if (this.windowManager) {
            console.warn("windowManager.windows is not a directly clearable array. Cannot auto-clear windows on startup.");
        }

        // Initialize canvas
        this.initializeCanvas();
    }

    initializeCanvas() {
        const canvas = document.getElementById('gameCanvas');
        if (!canvas) {
            console.error("Main game canvas element not found!");
            return;
        }

        // Set canvas to full screen
        const resizeCanvas = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            if (this.gameContext.camera) {
                this.gameContext.camera.canvasWidth = window.innerWidth;
                this.gameContext.camera.canvasHeight = window.innerHeight;
            }
        };

        resizeCanvas();
        window.addEventListener('resize', resizeCanvas);
        console.log("Main game canvas initialized successfully!");
    }

    update() {
        // Update UI elements
        if (this.selectionManager) {
            this.selectionManager.update();
        }
    }
} 