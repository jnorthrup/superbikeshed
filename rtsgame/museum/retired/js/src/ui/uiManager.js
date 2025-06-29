"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.UIManager = void 0;
const borderLayout_js_1 = require("./borderLayout.js");
const selectionManager_js_1 = __importDefault(require("./selectionManager.js"));
class UIManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.windowManager = new borderLayout_js_1.WindowManager();
        this.selectionManager = new selectionManager_js_1.default(gameContext);
        this.allowWindowDrawing = true;
        this.isDesignatingNewThreatArea = false;
        this.activePredictionForContextMenu = null; // Store data of the prediction context menu is for
        // Define and create context menu HTML
        const menuHTML = `
            <div id="predictionContextMenu" style="position: absolute; display: none; background-color: #2c3e50; border: 1px solid #7f8c8d; padding: 8px; z-index: 1000; color: #ecf0f1; font-family: Arial, sans-serif; box-shadow: 0px 2px 5px rgba(0,0,0,0.5);">
                <ul style="list-style: none; margin: 0; padding: 0;">
                    <li data-action="acknowledge" style="padding: 5px 10px; cursor: pointer;">Acknowledge/Reinforce</li>
                    <li data-action="dispute" style="padding: 5px 10px; cursor: pointer;">Dispute & Monitor</li>
                    <li data-action="designate_threat" style="padding: 5px 10px; cursor: pointer;">Designate New Threat Area</li>
                </ul>
            </div>
        `;
        const gameContainer = document.getElementById('gameContainer') || document.body; // Prefer gameContainer if exists
        gameContainer.insertAdjacentHTML('beforeend', menuHTML);
        this.predictionContextMenuElement = document.getElementById('predictionContextMenu');
        this._initContextMenuListeners();
    }
    _initContextMenuListeners() {
        if (!this.predictionContextMenuElement)
            return;
        this.predictionContextMenuElement.querySelectorAll('li').forEach(item => {
            item.addEventListener('click', (e) => {
                const action = e.target.getAttribute('data-action');
                const predictionData = this.activePredictionForContextMenu; // Use stored prediction data
                if (action && this.gameContext.gameState && typeof this.gameContext.gameState.addEvent === 'function') {
                    if (action === 'acknowledge') {
                        if (predictionData && predictionData.id) {
                            this.gameContext.gameState.addEvent('PlayerInteraction_AckReinforce_AttackVector', {
                                predictedPathID: predictionData.id,
                                confidence: predictionData.confidence,
                                playerReinforceFocus: true
                            });
                        }
                    }
                    else if (action === 'dispute') {
                        if (predictionData && predictionData.id) {
                            this.gameContext.gameState.addEvent('PlayerInteraction_DisputeMonitor_AttackVector', {
                                predictedPathID: predictionData.id
                            });
                        }
                    }
                    else if (action === 'designate_threat') {
                        this.setDesignatingNewThreatArea(true);
                    }
                }
                this.hidePredictionContextMenu();
            });
            // Basic hover effect
            item.addEventListener('mouseenter', () => item.style.backgroundColor = '#34495e');
            item.addEventListener('mouseleave', () => item.style.backgroundColor = 'transparent');
        });
        // Global click listener to hide context menu
        document.addEventListener('click', (e) => {
            if (this.predictionContextMenuElement && this.predictionContextMenuElement.style.display === 'block') {
                if (!this.predictionContextMenuElement.contains(e.target)) {
                    this.hidePredictionContextMenu();
                }
            }
        }, true); // Use capture phase to catch clicks early
    }
    showPredictionContextMenu(screenX, screenY, predictionData) {
        if (!this.predictionContextMenuElement)
            return;
        this.predictionContextMenuElement.style.left = `${screenX}px`;
        this.predictionContextMenuElement.style.top = `${screenY}px`;
        this.predictionContextMenuElement.style.display = 'block';
        this.activePredictionForContextMenu = predictionData; // Store prediction data
    }
    hidePredictionContextMenu() {
        if (!this.predictionContextMenuElement)
            return;
        this.predictionContextMenuElement.style.display = 'none';
        this.activePredictionForContextMenu = null;
    }
    setDesignatingNewThreatArea(state) {
        this.isDesignatingNewThreatArea = state;
        console.log(`Designating new threat area mode explicitly set to: ${this.isDesignatingNewThreatArea}`);
        if (this.gameContext.gameState && typeof this.gameContext.gameState.addEvent === 'function') {
            this.gameContext.gameState.addEvent('ui_info', `Threat Designation Mode: ${this.isDesignatingNewThreatArea ? 'ON' : 'OFF'}`);
        }
    }
    toggleDesignatingNewThreatArea() {
        // If currently true, calling toggle will make it false.
        // If currently false, calling toggle will make it true.
        this.setDesignatingNewThreatArea(!this.isDesignatingNewThreatArea);
        return this.isDesignatingNewThreatArea;
    }
    initialize() {
        // Clear existing windows on game launch
        if (this.windowManager && Array.isArray(this.windowManager.windows)) {
            this.windowManager.windows = [];
        }
        else if (this.windowManager) {
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
exports.UIManager = UIManager;
