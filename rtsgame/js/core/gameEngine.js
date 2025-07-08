// High Performance Game Engine - Optimized for cache locality and minimal allocations
// Integrates spatial indexing and batch processing for maximum performance

import { ReplayManager, BattleJournal, ReplayPlayer } from './replay/index.js';
import { Renderer, Camera, ModelManager, BatchProcessor } from './rendering/index.js';
import { Effect, Projectile, Caption } from './entities/index.js';
import { PerformanceMonitor, SpatialIndex } from './optimization/index.js';
import { battleJournal } from '../ai/battleJournal.js';
import { gameRNG, enableDeterministicMode } from './deterministicRNG.js';
import { TerrainManager } from './terrainManager.js';
import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, GRID_SIZE } from '../config/gameConstants.js';
import { generateTerrain } from './terrain.js';
import { DeterministicRNG } from './deterministicRNG.js';
import { makeStrategicDecisions, coordinateAttacks } from '../ai/strategicAI.js';
import { GameState } from './gameState.js';
import { BattleLogger } from './battleLogger.js';

export class GameEngine {
    constructor(canvas) {
        this.gameState = new GameState();
        this.camera = new Camera(canvas);
        this.renderer = new Renderer(canvas, this.camera);
        this.rng = new DeterministicRNG(12345);
        this.battleJournal = new BattleJournal();
        this.performanceMonitor = new PerformanceMonitor();
        this.battleLogger = new BattleLogger();
        
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
        document.getElementById('center-view').addEventListener('click', () => this.camera.centerView(WORLD_SIZE));
        
        // Canvas mouse controls
        this.canvas.addEventListener('wheel', (e) => this.camera.handleZoom(e));
        this.canvas.addEventListener('mousedown', (e) => this.camera.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.camera.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', () => this.camera.handleMouseUp());
    }

    initializeGame() {
        this.battleLogger.addEntry('system', 'Initializing cache-stratified RTS engine...');
        
        // Generate terrain
        generateTerrain({ terrain: this.gameState.terrain, resourceNodes: this.gameState.resourceNodes });
        this.battleLogger.addEntry('system', `Generated ${GRID_SIZE}x${GRID_SIZE} terrain with ${this.gameState.resourceNodes.length} resource nodes`);
        
        // Setup build lists
        if (UNIT_TYPES.commander) {
            UNIT_TYPES.commander.buildList = [
                BUILDING_TYPES.massExtractor,
                BUILDING_TYPES.energyExtractor,
                BUILDING_TYPES.landFactory
            ];
        }
        
        this.battleLogger.addEntry('system', 'Engine initialized. Ready for battle.');
    }

    startSimulation() {
        if (this.gameState.isRunning) return;
        
        this.gameState.isRunning = true;
        this.gameState.paused = false;
        this.battleLogger.addEntry('system', 'Starting simulation...');
        
        this.spawnInitialUnits();
        this.gameLoop();
    }

    spawnInitialUnits() {
        // Spawn commanders
        const blueCommander = new UNIT_TYPES.commander({
            x: WORLD_SIZE * 0.25,
            y: WORLD_SIZE * 0.25,
            team: 'blue'
        });
        
        const redCommander = new UNIT_TYPES.commander({
            x: WORLD_SIZE * 0.75,
            y: WORLD_SIZE * 0.75,
            team: 'red'
        });
        
        this.gameState.units.push(blueCommander, redCommander);
        this.battleLogger.addEntry('system', 'Commanders deployed');
    }

    gameLoop() {
        if (!this.gameState.isRunning) return;
        
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
            makeStrategicDecisions(this.gameState.createGameContext());
            coordinateAttacks(this.gameState.createGameContext());
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
        this.camera.centerView(WORLD_SIZE);
        this.battleLogger.addEntry('system', 'Simulation reset');
    }

    checkGameEnd() {
        const blueCommander = this.gameState.units.find(u => u.type === 'commander' && u.team === 'blue');
        const redCommander = this.gameState.units.find(u => u.type === 'commander' && u.team === 'red');
        
        if (!blueCommander) {
            this.endSimulation('red');
        } else if (!redCommander) {
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