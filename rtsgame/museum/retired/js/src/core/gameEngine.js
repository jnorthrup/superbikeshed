"use strict";
// High Performance Game Engine - Optimized for cache locality and minimal allocations
// Integrates spatial indexing and batch processing for maximum performance
Object.defineProperty(exports, "__esModule", { value: true });
exports.GameEngine = void 0;
const index_js_1 = require("./replay/index.js");
const index_js_2 = require("./rendering/index.js");
const index_js_3 = require("./optimization/index.js");
const unitTypes_js_1 = require("../config/unitTypes.js");
const buildingTypes_js_1 = require("../config/buildingTypes.js");
const gameConstants_js_1 = require("../config/gameConstants.js");
const terrain_js_1 = require("./terrain.js");
const deterministicRNG_js_1 = require("./deterministicRNG.js");
const strategicAI_js_1 = require("../ai/strategicAI.js");
const gameState_js_1 = require("./gameState.js");
const battleLogger_js_1 = require("./battleLogger.js");
class GameEngine {
    constructor(canvas) {
        this.gameState = new gameState_js_1.GameState();
        this.camera = new index_js_2.Camera(canvas);
        this.renderer = new index_js_2.Renderer(canvas, this.camera);
        this.rng = new deterministicRNG_js_1.DeterministicRNG(12345);
        this.battleJournal = new index_js_1.BattleJournal();
        this.performanceMonitor = new index_js_3.PerformanceMonitor();
        this.battleLogger = new battleLogger_js_1.BattleLogger();
        this.canvas = canvas;
        this.simulationSpeed = 1.0;
        this.animationFrameId = null;
        this.initializeEventListeners();
        this.initializeGame();
    }
    initializeEventListeners() {
        // Battle controls
        document.getElementById('start-simulation').addEventListener('click', () => this.startSimulation());
        document.getElementById('pause-simulation').addEventListener('click', () => this.togglePause());
        document.getElementById('reset-simulation').addEventListener('click', () => this.resetSimulation());
        // Simulation speed
        document.getElementById('simulation-speed').addEventListener('change', (e) => {
            this.simulationSpeed = parseFloat(e.target.value);
        });
        // Replay controls
        document.getElementById('start-recording').addEventListener('click', () => this.startRecording());
        document.getElementById('load-replay').addEventListener('click', () => this.loadReplay());
        document.getElementById('export-replay').addEventListener('click', () => this.exportReplay());
        // View controls
        document.getElementById('zoom-in').addEventListener('click', () => this.camera.zoomIn());
        document.getElementById('zoom-out').addEventListener('click', () => this.camera.zoomOut());
        document.getElementById('center-view').addEventListener('click', () => this.camera.centerView(gameConstants_js_1.WORLD_SIZE));
        // Canvas mouse controls
        this.canvas.addEventListener('wheel', (e) => this.camera.handleZoom(e));
        this.canvas.addEventListener('mousedown', (e) => this.camera.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.camera.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', () => this.camera.handleMouseUp());
    }
    initializeGame() {
        this.battleLogger.addEntry('system', 'Initializing cache-stratified RTS engine...');
        // Generate terrain
        (0, terrain_js_1.generateTerrain)({ terrain: this.gameState.terrain, resourceNodes: this.gameState.resourceNodes });
        this.battleLogger.addEntry('system', `Generated ${gameConstants_js_1.GRID_SIZE}x${gameConstants_js_1.GRID_SIZE} terrain with ${this.gameState.resourceNodes.length} resource nodes`);
        // Setup build lists
        if (unitTypes_js_1.UNIT_TYPES.commander) {
            unitTypes_js_1.UNIT_TYPES.commander.buildList = [
                buildingTypes_js_1.BUILDING_TYPES.massExtractor,
                buildingTypes_js_1.BUILDING_TYPES.energyExtractor,
                buildingTypes_js_1.BUILDING_TYPES.landFactory
            ];
        }
        this.battleLogger.addEntry('system', 'Engine initialized. Ready for battle.');
    }
    startSimulation() {
        if (this.gameState.isRunning)
            return;
        this.gameState.isRunning = true;
        this.gameState.paused = false;
        this.battleLogger.addEntry('system', 'Starting simulation...');
        this.spawnInitialUnits();
        this.gameLoop();
    }
    spawnInitialUnits() {
        // Spawn commanders
        const blueCommander = new unitTypes_js_1.UNIT_TYPES.commander({
            x: gameConstants_js_1.WORLD_SIZE * 0.25,
            y: gameConstants_js_1.WORLD_SIZE * 0.25,
            team: 'blue'
        });
        const redCommander = new unitTypes_js_1.UNIT_TYPES.commander({
            x: gameConstants_js_1.WORLD_SIZE * 0.75,
            y: gameConstants_js_1.WORLD_SIZE * 0.75,
            team: 'red'
        });
        this.gameState.units.push(blueCommander, redCommander);
        this.battleLogger.addEntry('system', 'Commanders deployed');
    }
    gameLoop() {
        if (!this.gameState.isRunning)
            return;
        this.performanceMonitor.startFrame();
        if (!this.gameState.paused) {
            this.updateSimulation();
        }
        this.renderer.render(this.gameState);
        this.updateUI();
        this.performanceMonitor.endFrame();
        this.animationFrameId = requestAnimationFrame(() => this.gameLoop());
    }
    updateSimulation() {
        // Update game time
        this.gameState.gameTime += this.simulationSpeed;
        // Update resource income
        this.gameState.updateResourceIncome();
        // Update units
        for (const unit of this.gameState.units) {
            unit.update(this.gameState.createGameContext());
        }
        // Update buildings
        for (const building of this.gameState.buildings) {
            building.update(this.gameState.createGameContext());
        }
        // Update effects
        this.gameState.effects = this.gameState.effects.filter(effect => {
            effect.update();
            return !effect.isFinished();
        });
        // Update projectiles
        this.gameState.projectiles = this.gameState.projectiles.filter(projectile => {
            projectile.update();
            return !projectile.isFinished();
        });
        // Update captions
        this.gameState.captions = this.gameState.captions.filter(caption => {
            caption.update();
            return !caption.isFinished();
        });
        // AI decisions
        if (this.gameState.gameTime % 60 === 0) {
            (0, strategicAI_js_1.makeStrategicDecisions)(this.gameState.createGameContext());
            (0, strategicAI_js_1.coordinateAttacks)(this.gameState.createGameContext());
        }
        // Check for game end
        this.checkGameEnd();
    }
    updateUI() {
        // Update resource displays
        for (const team of ['blue', 'red']) {
            document.getElementById(`${team}-mass`).textContent = Math.floor(this.gameState.resources[team].mass);
            document.getElementById(`${team}-energy`).textContent = Math.floor(this.gameState.resources[team].energy);
            document.getElementById(`${team}-mass-income`).textContent = `+${this.gameState.resources[team].massIncome.toFixed(1)}`;
            document.getElementById(`${team}-energy-income`).textContent = `+${this.gameState.resources[team].energyIncome.toFixed(1)}`;
        }
        // Update game time
        const minutes = Math.floor(this.gameState.gameTime / 60);
        const seconds = Math.floor(this.gameState.gameTime % 60);
        document.getElementById('game-time').textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
    }
    togglePause() {
        this.gameState.paused = !this.gameState.paused;
        document.getElementById('pause-simulation').textContent = this.gameState.paused ? 'Resume' : 'Pause';
    }
    resetSimulation() {
        this.gameState.reset();
        this.camera.centerView(gameConstants_js_1.WORLD_SIZE);
        this.battleLogger.addEntry('system', 'Simulation reset');
    }
    checkGameEnd() {
        const blueCommander = this.gameState.units.find(u => u.type === 'commander' && u.team === 'blue');
        const redCommander = this.gameState.units.find(u => u.type === 'commander' && u.team === 'red');
        if (!blueCommander) {
            this.endSimulation('red');
        }
        else if (!redCommander) {
            this.endSimulation('blue');
        }
    }
    endSimulation(winner) {
        this.gameState.isRunning = false;
        this.gameState.winner = winner;
        this.battleLogger.addEntry('system', `${winner.toUpperCase()} team wins!`);
        cancelAnimationFrame(this.animationFrameId);
    }
    startRecording() {
        this.battleJournal.startRecording();
        this.battleLogger.addEntry('system', 'Started recording battle');
    }
    loadReplay() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.replay';
        input.onchange = (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = (event) => {
                    const replay = JSON.parse(event.target.result);
                    this.battleJournal.loadReplay(replay);
                    this.battleLogger.addEntry('system', 'Loaded replay');
                };
                reader.readAsText(file);
            }
        };
        input.click();
    }
    exportReplay() {
        const replay = this.battleJournal.exportReplay();
        const blob = new Blob([JSON.stringify(replay)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `battle-${new Date().toISOString()}.replay`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        this.battleLogger.addEntry('system', 'Exported replay');
    }
}
exports.GameEngine = GameEngine;
