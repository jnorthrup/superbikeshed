// Cache-Stratified RTS Engine - Main Application Entry Point
// Professional interface with performance monitoring and deterministic replay

import { UNIT_TYPES } from './config/unitTypes.js';
import { BUILDING_TYPES } from './config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from './config/gameConstants.js';
import { Unit } from './core/unit.js';
import { Building } from './core/building.js';
import { generateTerrain, findLandPosition } from './core/terrain.js';
import { DeterministicRNG } from './core/deterministicRNG.js';
import { BattleJournal } from './core/battleJournal.js';
import { makeStrategicDecisions, coordinateAttacks } from './ai/strategicAI.js';

// Performance monitoring system
class PerformanceMonitor {
    constructor() {
        this.frameCount = 0;
        this.lastFpsUpdate = performance.now();
        this.frameTimes = [];
        this.maxFrameTimes = 60; // Keep last 60 frame times
    }

    startFrame() {
        this.frameStart = performance.now();
    }

    endFrame() {
        const frameTime = performance.now() - this.frameStart;
        this.frameTimes.push(frameTime);
        
        if (this.frameTimes.length > this.maxFrameTimes) {
            this.frameTimes.shift();
        }
        
        this.frameCount++;
        
        // Update UI every 10 frames for smooth display
        if (this.frameCount % 10 === 0) {
            this.updateUI();
        }
    }

    updateUI() {
        const avgFrameTime = this.frameTimes.reduce((a, b) => a + b, 0) / this.frameTimes.length;
        const fps = 1000 / avgFrameTime;
        
        // Update performance metrics
        document.getElementById('frame-time').textContent = `${avgFrameTime.toFixed(2)}ms`;
        document.getElementById('fps-counter').textContent = Math.round(fps);
        
        // Update cache efficiency based on frame time consistency
        const frameTimeVariance = this.frameTimes.reduce((sum, time) => {
            return sum + Math.pow(time - avgFrameTime, 2);
        }, 0) / this.frameTimes.length;
        const stdDev = Math.sqrt(frameTimeVariance);
        
        let efficiency = 'EXCELLENT';
        if (stdDev > 0.5) efficiency = 'GOOD';
        if (stdDev > 1.0) efficiency = 'FAIR';
        if (stdDev > 2.0) efficiency = 'POOR';
        
        document.getElementById('cache-efficiency').textContent = efficiency;
    }
}

// Battle log management
class BattleLogger {
    constructor() {
        this.logContainer = document.getElementById('battle-log');
        this.maxEntries = 50;
    }

    addEntry(type, message, time = null) {
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        
        const timeSpan = document.createElement('span');
        timeSpan.className = 'log-time';
        timeSpan.textContent = time || this.formatTime(Date.now());
        
        const messageSpan = document.createElement('span');
        messageSpan.className = 'log-message';
        messageSpan.textContent = message;
        
        entry.appendChild(timeSpan);
        entry.appendChild(messageSpan);
        
        this.logContainer.appendChild(entry);
        
        // Remove old entries
        while (this.logContainer.children.length > this.maxEntries) {
            this.logContainer.removeChild(this.logContainer.firstChild);
        }
        
        // Auto-scroll to bottom
        this.logContainer.scrollTop = this.logContainer.scrollHeight;
    }

    formatTime(timestamp) {
        const seconds = Math.floor(timestamp / 1000) % 60;
        const minutes = Math.floor(timestamp / 60000) % 60;
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
}

// Main game engine class
class RTSEngine {
    constructor() {
        this.gameState = {
            gameTime: 0,
            winner: null,
            paused: false,
            isRunning: false
        };
        
        this.resources = {
            blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
            red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        };
        
        this.units = [];
        this.buildings = [];
        this.terrain = [];
        this.resourceNodes = [];
        this.effects = [];
        this.projectiles = [];
        this.captions = [];
        
        this.rng = new DeterministicRNG(12345); // Seeded for deterministic replay
        this.battleJournal = new BattleJournal();
        this.performanceMonitor = new PerformanceMonitor();
        this.battleLogger = new BattleLogger();
        
        this.canvas = document.getElementById('game-canvas');
        this.ctx = this.canvas.getContext('2d');
        this.camera = {
            x: WORLD_SIZE / 2,
            y: WORLD_SIZE / 2,
            zoom: 0.5,
            canvasWidth: this.canvas.width,
            canvasHeight: this.canvas.height
        };
        
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
        document.getElementById('zoom-in').addEventListener('click', () => this.zoomIn());
        document.getElementById('zoom-out').addEventListener('click', () => this.zoomOut());
        document.getElementById('center-view').addEventListener('click', () => this.centerView());
        
        // Canvas mouse controls
        this.canvas.addEventListener('wheel', (e) => this.handleZoom(e));
        this.canvas.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleMouseUp(e));
    }

    initializeGame() {
        this.battleLogger.addEntry('system', 'Initializing cache-stratified RTS engine...');
        
        // Generate terrain
        generateTerrain({ terrain: this.terrain, resourceNodes: this.resourceNodes });
        this.battleLogger.addEntry('system', `Generated ${GRID_SIZE}x${GRID_SIZE} terrain with ${this.resourceNodes.length} resource nodes`);
        
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

    createGameContext() {
        return {
            units: this.units,
            buildings: this.buildings,
            resources: this.resources,
            gameState: this.gameState,
            terrain: this.terrain,
            resourceNodes: this.resourceNodes,
            effects: this.effects,
            projectiles: this.projectiles,
            captions: this.captions,
            seedRandom: this.rng,
            battleJournal: this.battleJournal,
            UNIT_TYPES,
            BUILDING_TYPES,
            WORLD_SIZE,
            TILE_SIZE,
            GRID_SIZE,
            TERRAIN_TYPES,
            Unit,
            Building,
            addEvent: (ctx, type, message, importance, pos) => {
                if (importance >= 2) {
                    this.battleLogger.addEntry(type, message);
                    if (this.battleJournal.isRecording) {
                        this.battleJournal.recordEvent(type, message, this.gameState.gameTime, pos);
                    }
                }
            }
        };
    }

    startSimulation() {
        if (this.gameState.isRunning) return;
        
        this.resetSimulation();
        this.spawnInitialUnits();
        
        this.gameState.isRunning = true;
        this.gameState.paused = false;
        
        // Update UI
        document.getElementById('start-simulation').disabled = true;
        document.getElementById('pause-simulation').disabled = false;
        document.getElementById('reset-simulation').disabled = false;
        
        this.battleLogger.addEntry('battle', 'Battle commenced! Blue vs Red');
        
        // Start game loop
        this.gameLoop();
    }

    spawnInitialUnits() {
        // Find spawn positions
        const blueStart = findLandPosition(
            { terrain: this.terrain, resourceNodes: this.resourceNodes },
            WORLD_SIZE * 0.2, WORLD_SIZE * 0.5, 5
        );
        const redStart = findLandPosition(
            { terrain: this.terrain, resourceNodes: this.resourceNodes },
            WORLD_SIZE * 0.8, WORLD_SIZE * 0.5, 5
        );
        
        if (!blueStart || !redStart) {
            this.battleLogger.addEntry('system', 'ERROR: Failed to find valid spawn positions');
            return;
        }
        
        const gameContext = this.createGameContext();
        
        // Spawn commanders
        this.units.push(new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander, gameContext));
        this.units.push(new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander, gameContext));
        
        this.battleLogger.addEntry('battle', `Blue Commander deployed at (${Math.floor(blueStart.x)}, ${Math.floor(blueStart.y)})`);
        this.battleLogger.addEntry('battle', `Red Commander deployed at (${Math.floor(redStart.x)}, ${Math.floor(redStart.y)})`);
    }

    gameLoop() {
        if (!this.gameState.isRunning) return;
        
        this.performanceMonitor.startFrame();
        
        if (!this.gameState.paused) {
            this.updateSimulation();
        }
        
        this.render();
        this.updateUI();
        
        this.performanceMonitor.endFrame();
        
        // Continue loop
        this.animationFrameId = requestAnimationFrame(() => this.gameLoop());
    }

    updateSimulation() {
        const deltaTime = (1/60) * this.simulationSpeed; // 60 FPS base
        this.gameState.gameTime += deltaTime;
        
        const gameContext = this.createGameContext();
        
        // Update units
        for (let i = this.units.length - 1; i >= 0; i--) {
            const unit = this.units[i];
            unit.update(gameContext);
            
            if (unit.hp <= 0) {
                if (unit.type === UNIT_TYPES.commander) {
                    this.gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                    this.battleLogger.addEntry('battle', `${this.gameState.winner} WINS! Commander eliminated!`);
                    this.endSimulation();
                    return;
                }
                this.units.splice(i, 1);
            }
        }
        
        // Update buildings
        for (let i = this.buildings.length - 1; i >= 0; i--) {
            const building = this.buildings[i];
            building.update(this.units, gameContext);
            
            if (building.hp <= 0) {
                this.buildings.splice(i, 1);
            }
        }
        
        // AI decision making
        if (Math.floor(this.gameState.gameTime * 4) % 15 === 0) { // Every ~4 seconds
            makeStrategicDecisions(gameContext);
            coordinateAttacks(gameContext);
        }
        
        // Update resource income
        this.updateResourceIncome();
        
        // Record frame for replay
        if (this.battleJournal.isRecording) {
            this.battleJournal.recordFrame(gameContext);
        }
    }

    updateResourceIncome() {
        for (const team of ['blue', 'red']) {
            const extractors = this.buildings.filter(b => 
                b.team === team && b.type.resourceGeneration
            );
            
            let massIncome = 0;
            let energyIncome = 0;
            
            for (const extractor of extractors) {
                if (extractor.type.name === 'Mass Extractor') {
                    massIncome += extractor.type.resourceGeneration.mass || 0;
                } else if (extractor.type.name === 'Energy Plant') {
                    energyIncome += extractor.type.resourceGeneration.energy || 0;
                }
            }
            
            this.resources[team].massIncome = massIncome;
            this.resources[team].energyIncome = energyIncome;
            this.resources[team].mass += massIncome / 60; // Per frame
            this.resources[team].energy += energyIncome / 60;
        }
    }

    render() {
        // Clear canvas
        this.ctx.fillStyle = '#1a1a3e';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        
        // Save context
        this.ctx.save();
        
        // Apply camera transform
        this.ctx.translate(this.canvas.width / 2, this.canvas.height / 2);
        this.ctx.scale(this.camera.zoom, this.camera.zoom);
        this.ctx.translate(-this.camera.x, -this.camera.y);
        
        // Render terrain (simplified for performance)
        this.renderTerrain();
        
        // Render resource nodes
        this.renderResourceNodes();
        
        // Render buildings
        for (const building of this.buildings) {
            this.renderBuilding(building);
        }
        
        // Render units
        for (const unit of this.units) {
            this.renderUnit(unit);
        }
        
        // Restore context
        this.ctx.restore();
    }

    renderTerrain() {
        const cellSize = TILE_SIZE;
        const startX = Math.max(0, Math.floor((this.camera.x - this.canvas.width / (2 * this.camera.zoom)) / cellSize));
        const endX = Math.min(GRID_SIZE, Math.ceil((this.camera.x + this.canvas.width / (2 * this.camera.zoom)) / cellSize));
        const startY = Math.max(0, Math.floor((this.camera.y - this.canvas.height / (2 * this.camera.zoom)) / cellSize));
        const endY = Math.min(GRID_SIZE, Math.ceil((this.camera.y + this.canvas.height / (2 * this.camera.zoom)) / cellSize));
        
        for (let x = startX; x < endX; x++) {
            for (let y = startY; y < endY; y++) {
                const terrainType = this.terrain[x] && this.terrain[x][y];
                if (terrainType === undefined) continue;
                
                switch (terrainType) {
                    case TERRAIN_TYPES.WATER:
                        this.ctx.fillStyle = '#003366';
                        break;
                    case TERRAIN_TYPES.LAND:
                        this.ctx.fillStyle = '#2d5a2d';
                        break;
                    case TERRAIN_TYPES.MOUNTAIN:
                        this.ctx.fillStyle = '#666666';
                        break;
                    default:
                        this.ctx.fillStyle = '#333333';
                }
                
                this.ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
            }
        }
    }

    renderResourceNodes() {
        for (const node of this.resourceNodes) {
            if (!this.isInView(node.x, node.y, 20)) continue;
            
            this.ctx.fillStyle = node.type === 'mass' ? '#ffaa00' : '#00aaff';
            this.ctx.beginPath();
            this.ctx.arc(node.x, node.y, 15, 0, Math.PI * 2);
            this.ctx.fill();
            
            if (node.occupied) {
                this.ctx.strokeStyle = '#ffffff';
                this.ctx.lineWidth = 2;
                this.ctx.stroke();
            }
        }
    }

    renderUnit(unit) {
        if (!this.isInView(unit.x, unit.y, unit.type.size)) return;
        
        this.ctx.save();
        this.ctx.translate(unit.x, unit.y);
        this.ctx.rotate(unit.angle);
        
        // Unit body
        this.ctx.fillStyle = unit.team === 'blue' ? '#4488ff' : '#ff4444';
        this.ctx.fillRect(-unit.type.size/2, -unit.type.size/2, unit.type.size, unit.type.size);
        
        // Unit type indicator
        this.ctx.fillStyle = unit.type.color || '#ffffff';
        this.ctx.fillRect(-unit.type.size/4, -unit.type.size/4, unit.type.size/2, unit.type.size/2);
        
        this.ctx.restore();
        
        // Health bar
        if (unit.hp < unit.maxHp) {
            const barWidth = unit.type.size;
            const barHeight = 4;
            const barY = unit.y - unit.type.size/2 - 8;
            
            this.ctx.fillStyle = '#000000';
            this.ctx.fillRect(unit.x - barWidth/2, barY, barWidth, barHeight);
            this.ctx.fillStyle = '#00ff00';
            this.ctx.fillRect(unit.x - barWidth/2, barY, barWidth * (unit.hp / unit.maxHp), barHeight);
        }
        
        // Selection indicator
        if (unit.selected) {
            this.ctx.strokeStyle = '#ffff00';
            this.ctx.lineWidth = 2;
            this.ctx.strokeRect(unit.x - unit.type.size/2 - 5, unit.y - unit.type.size/2 - 5, 
                              unit.type.size + 10, unit.type.size + 10);
        }
    }

    renderBuilding(building) {
        if (!this.isInView(building.x, building.y, building.type.size)) return;
        
        // Building body
        this.ctx.fillStyle = building.team === 'blue' ? '#2266aa' : '#aa2222';
        this.ctx.fillRect(building.x - building.type.size/2, building.y - building.type.size/2, 
                         building.type.size, building.type.size);
        
        // Building type indicator
        this.ctx.fillStyle = building.type.color || '#ffffff';
        this.ctx.fillRect(building.x - building.type.size/4, building.y - building.type.size/4, 
                         building.type.size/2, building.type.size/2);
        
        // Health bar
        if (building.hp < building.maxHp) {
            const barWidth = building.type.size;
            const barHeight = 6;
            const barY = building.y - building.type.size/2 - 10;
            
            this.ctx.fillStyle = '#000000';
            this.ctx.fillRect(building.x - barWidth/2, barY, barWidth, barHeight);
            this.ctx.fillStyle = '#00ff00';
            this.ctx.fillRect(building.x - barWidth/2, barY, barWidth * (building.hp / building.maxHp), barHeight);
        }
    }

    isInView(x, y, size) {
        const margin = size;
        return (x + margin >= this.camera.x - this.canvas.width / (2 * this.camera.zoom) &&
                x - margin <= this.camera.x + this.canvas.width / (2 * this.camera.zoom) &&
                y + margin >= this.camera.y - this.canvas.height / (2 * this.camera.zoom) &&
                y - margin <= this.camera.y + this.canvas.height / (2 * this.camera.zoom));
    }

    updateUI() {
        // Update resource displays
        document.getElementById('blue-mass').textContent = Math.floor(this.resources.blue.mass);
        document.getElementById('blue-energy').textContent = Math.floor(this.resources.blue.energy);
        document.getElementById('red-mass').textContent = Math.floor(this.resources.red.mass);
        document.getElementById('red-energy').textContent = Math.floor(this.resources.red.energy);
        
        // Update unit and building counts
        document.getElementById('unit-count').textContent = this.units.length;
        document.getElementById('building-count').textContent = this.buildings.length;
        
        // Update game time
        const minutes = Math.floor(this.gameState.gameTime / 60);
        const seconds = Math.floor(this.gameState.gameTime % 60);
        document.getElementById('game-time').textContent = 
            `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        
        // Update zoom display
        document.getElementById('zoom-level').textContent = `${Math.round(this.camera.zoom * 100)}%`;
    }

    // Control methods
    togglePause() {
        this.gameState.paused = !this.gameState.paused;
        document.getElementById('pause-simulation').textContent = this.gameState.paused ? 'Resume' : 'Pause';
    }

    resetSimulation() {
        this.gameState.isRunning = false;
        this.gameState.paused = false;
        this.gameState.gameTime = 0;
        this.gameState.winner = null;
        
        this.units.length = 0;
        this.buildings.length = 0;
        this.effects.length = 0;
        this.projectiles.length = 0;
        this.captions.length = 0;
        
        this.resources.blue = { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 };
        this.resources.red = { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 };
        
        if (this.animationFrameId) {
            cancelAnimationFrame(this.animationFrameId);
        }
        
        // Update UI
        document.getElementById('start-simulation').disabled = false;
        document.getElementById('pause-simulation').disabled = true;
        document.getElementById('reset-simulation').disabled = true;
        document.getElementById('pause-simulation').textContent = 'Pause';
        
        this.battleLogger.addEntry('system', 'Simulation reset. Ready for new battle.');
    }

    endSimulation() {
        this.gameState.isRunning = false;
        document.getElementById('start-simulation').disabled = false;
        document.getElementById('pause-simulation').disabled = true;
        
        if (this.battleJournal.isRecording) {
            this.battleJournal.stopRecording();
            document.getElementById('export-replay').disabled = false;
        }
    }

    // Camera controls
    zoomIn() {
        this.camera.zoom = Math.min(2.0, this.camera.zoom * 1.2);
    }

    zoomOut() {
        this.camera.zoom = Math.max(0.1, this.camera.zoom / 1.2);
    }

    centerView() {
        this.camera.x = WORLD_SIZE / 2;
        this.camera.y = WORLD_SIZE / 2;
    }

    handleZoom(e) {
        e.preventDefault();
        const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
        this.camera.zoom = Math.max(0.1, Math.min(2.0, this.camera.zoom * zoomFactor));
    }

    // Mouse handling for camera movement
    handleMouseDown(e) {
        this.mouseDown = true;
        this.lastMouseX = e.clientX;
        this.lastMouseY = e.clientY;
    }

    handleMouseMove(e) {
        if (!this.mouseDown) return;
        
        const deltaX = (e.clientX - this.lastMouseX) / this.camera.zoom;
        const deltaY = (e.clientY - this.lastMouseY) / this.camera.zoom;
        
        this.camera.x -= deltaX;
        this.camera.y -= deltaY;
        
        this.lastMouseX = e.clientX;
        this.lastMouseY = e.clientY;
    }

    handleMouseUp(e) {
        this.mouseDown = false;
    }

    // Replay system
    startRecording() {
        if (this.battleJournal.isRecording) {
            this.battleJournal.stopRecording();
            document.getElementById('start-recording').textContent = 'Start Recording';
            document.getElementById('recording-status').textContent = 'Recording stopped';
            document.getElementById('export-replay').disabled = false;
        } else {
            this.battleJournal.startRecording();
            document.getElementById('start-recording').textContent = 'Stop Recording';
            document.getElementById('recording-status').textContent = 'Recording...';
        }
    }

    loadReplay() {
        // Create file input for loading replay files
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        input.onchange = (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = (e) => {
                    try {
                        const replayData = JSON.parse(e.target.result);
                        this.battleLogger.addEntry('system', `Loaded replay: ${replayData.id}`);
                        // TODO: Implement replay playback
                    } catch (error) {
                        this.battleLogger.addEntry('system', 'ERROR: Invalid replay file');
                    }
                };
                reader.readAsText(file);
            }
        };
        input.click();
    }

    exportReplay() {
        const replayData = this.battleJournal.getBattleData();
        if (!replayData) {
            this.battleLogger.addEntry('system', 'No replay data to export');
            return;
        }
        
        const dataStr = JSON.stringify(replayData, null, 2);
        const dataBlob = new Blob([dataStr], { type: 'application/json' });
        const url = URL.createObjectURL(dataBlob);
        
        const link = document.createElement('a');
        link.href = url;
        link.download = `battle_${replayData.id}.json`;
        link.click();
        
        URL.revokeObjectURL(url);
        this.battleLogger.addEntry('system', 'Replay exported successfully');
    }
}

// Initialize the engine when the page loads
document.addEventListener('DOMContentLoaded', () => {
    new RTSEngine();
});