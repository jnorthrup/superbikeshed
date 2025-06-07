// Modern RTS UI Manager - Replaces the deprecated ui.js system
import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { drawMinimap } from './minimap_canvas2d.js';

export class ModernUIManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.selectedUnits = [];
        this.alertQueue = [];
        this.lastAlertTime = 0;
        this.minimapCanvas = null;
        this.minimapCtx = null;
        
        this.init();
    }
    
    init() {
        // Initialize minimap
        this.minimapCanvas = document.getElementById('minimapCanvas');
        if (this.minimapCanvas) {
            this.minimapCtx = this.minimapCanvas.getContext('2d');
        }
        
        // Set up event listeners
        this.setupEventListeners();
        
        console.log('Modern UI Manager initialized');
    }
    
    setupEventListeners() {
        // Command button handlers
        const commandButtons = document.querySelectorAll('.command-button');
        commandButtons.forEach(button => {
            button.addEventListener('click', (e) => {
                const action = e.currentTarget.dataset.action;
                this.handleCommand(action);
            });
        });
        
        // Minimap click handler
        if (this.minimapCanvas) {
            this.minimapCanvas.addEventListener('click', (e) => {
                this.handleMinimapClick(e);
            });
        }
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            this.handleKeyboard(e);
        });
    }
    
    update(gameContext) {
        // Update all UI elements
        this.updateResourceBar(gameContext);
        this.updateGameInfo(gameContext);
        this.updateTeamStatus(gameContext);
        this.updateMinimap(gameContext);
        this.updateUnitSelection(gameContext);
        this.updateAlerts(gameContext);
        this.updateProductionQueue(gameContext);
    }
    
    updateResourceBar(gameContext) {
        const { resources } = gameContext;
        
        if (resources && resources.blue) {
            // Mass
            const massEl = document.getElementById('blue-mass');
            const massIncomeEl = document.getElementById('blue-mass-income');
            if (massEl) massEl.textContent = Math.floor(resources.blue.mass || 0);
            if (massIncomeEl) massIncomeEl.textContent = Math.floor(resources.blue.massIncome || 0);
            
            // Energy
            const energyEl = document.getElementById('blue-energy');
            const energyIncomeEl = document.getElementById('blue-energy-income');
            if (energyEl) energyEl.textContent = Math.floor(resources.blue.energy || 0);
            if (energyIncomeEl) energyIncomeEl.textContent = Math.floor(resources.blue.energyIncome || 0);
            
            // Computronium
            const compEl = document.getElementById('blue-computronium');
            const compIncomeEl = document.getElementById('blue-computronium-income');
            if (compEl) compEl.textContent = (resources.blue.computronium || 0).toFixed(1);
            if (compIncomeEl) compIncomeEl.textContent = (resources.blue.computroniumIncome || 0).toFixed(1);
        }
    }
    
    updateGameInfo(gameContext) {
        const { gameState, camera, units } = gameContext;
        
        // Game time
        const gameTimeEl = document.getElementById('gameTime');
        if (gameTimeEl && gameState) {
            gameTimeEl.textContent = this.formatTime(gameState.gameTime || 0);
        }
        
        // Zoom level
        const zoomEl = document.getElementById('zoomLevel');
        if (zoomEl && camera) {
            zoomEl.textContent = (camera.zoom || 1.0).toFixed(1) + 'x';
        }
        
        // Unit counts
        if (units && Array.isArray(units)) {
            const blueUnits = units.filter(u => u.team === 'blue').length;
            const redUnits = units.filter(u => u.team === 'red').length;
            
            const blueEl = document.getElementById('blueUnits');
            const redEl = document.getElementById('redUnits');
            if (blueEl) blueEl.textContent = blueUnits;
            if (redEl) redEl.textContent = redUnits;
        }
    }
    
    updateTeamStatus(gameContext) {
        const { units, buildings } = gameContext;
        
        if (!units || !Array.isArray(units) || !buildings || !Array.isArray(buildings)) {
            return;
        }
        
        // Blue team
        const blueUnits = units.filter(u => u.team === 'blue');
        const blueBuildings = buildings.filter(b => b.team === 'blue');
        const blueCommander = blueUnits.find(u => u.type === UNIT_TYPES.commander);
        
        const blueCommanderEl = document.getElementById('blueCommander');
        const blueArmySizeEl = document.getElementById('blueArmySize');
        const blueBuildingsEl = document.getElementById('blueBuildings');
        
        if (blueCommanderEl) {
            blueCommanderEl.textContent = blueCommander ? 
                `${Math.round(blueCommander.hp / blueCommander.maxHp * 100)}%` : 'DESTROYED';
        }
        if (blueArmySizeEl) blueArmySizeEl.textContent = blueUnits.length;
        if (blueBuildingsEl) blueBuildingsEl.textContent = blueBuildings.length;
        
        // Red team
        const redUnits = units.filter(u => u.team === 'red');
        const redBuildings = buildings.filter(b => b.team === 'red');
        const redCommander = redUnits.find(u => u.type === UNIT_TYPES.commander);
        
        const redCommanderEl = document.getElementById('redCommander');
        const redArmySizeEl = document.getElementById('redArmySize');
        const redBuildingsEl = document.getElementById('redBuildings');
        
        if (redCommanderEl) {
            redCommanderEl.textContent = redCommander ? 
                `${Math.round(redCommander.hp / redCommander.maxHp * 100)}%` : 'DESTROYED';
        }
        if (redArmySizeEl) redArmySizeEl.textContent = redUnits.length;
        if (redBuildingsEl) redBuildingsEl.textContent = redBuildings.length;
    }
    
    updateMinimap(gameContext) {
        if (!this.minimapCtx) return;
        
        const { terrain, resourceNodes, units, buildings, camera } = gameContext;
        
        const minimapRenderContext = {
            minimapCtx: this.minimapCtx,
            terrain: terrain?.terrain || null,
            resourceNodes: resourceNodes || [],
            units: units || [],
            buildings: buildings || [],
            camera: camera || {}
        };
        
        drawMinimap(minimapRenderContext);
    }
    
    updateUnitSelection(gameContext) {
        const selectionContent = document.getElementById('selectionContent');
        if (!selectionContent) return;
        
        if (this.selectedUnits.length === 0) {
            selectionContent.innerHTML = `
                <div style="color: #888; text-align: center; margin-top: 50px;">
                    No units selected<br>
                    <small>Click units to select them</small>
                </div>
            `;
            return;
        }
        
        // Show selected units
        let html = '';
        this.selectedUnits.forEach((unit, index) => {
            const healthPercent = Math.round((unit.hp / unit.maxHp) * 100);
            const healthColor = healthPercent > 75 ? '#00ff00' : healthPercent > 25 ? '#ffff00' : '#ff0000';
            
            html += `
                <div class="selected-unit">
                    <div class="unit-name">${unit.type.name || 'Unknown Unit'}</div>
                    <div class="unit-health">
                        <span style="font-size: 11px;">HP:</span>
                        <div class="health-bar">
                            <div class="health-fill" style="width: ${healthPercent}%; background: ${healthColor};"></div>
                        </div>
                        <span style="font-size: 11px;">${unit.hp}/${unit.maxHp}</span>
                    </div>
                    <div class="unit-stats">
                        Team: ${unit.team} | 
                        Damage: ${unit.type.damage || 0} | 
                        ${unit.target ? 'In Combat' : unit.patrolTarget ? 'Moving' : 'Idle'}
                    </div>
                </div>
            `;
        });
        
        selectionContent.innerHTML = html;
    }
    
    updateAlerts(gameContext) {
        const { units } = gameContext;
        const alertContainer = document.getElementById('alertContainer');
        if (!alertContainer || !units) return;
        
        const now = Date.now();
        
        // Check for combat events
        if (now - this.lastAlertTime > 5000) { // Check every 5 seconds
            const combatUnits = units.filter(u => u.target && u.hp < u.maxHp);
            
            if (combatUnits.length > 0) {
                this.addAlert('Combat detected! Units are under attack.', 'warning');
                this.lastAlertTime = now;
            }
            
            // Check for destroyed commanders
            const blueCommander = units.find(u => u.team === 'blue' && u.type === UNIT_TYPES.commander);
            const redCommander = units.find(u => u.team === 'red' && u.type === UNIT_TYPES.commander);
            
            if (!blueCommander && this.lastBlueCommander) {
                this.addAlert('Blue Commander destroyed!', 'error');
            }
            if (!redCommander && this.lastRedCommander) {
                this.addAlert('Red Commander destroyed!', 'error');
            }
            
            this.lastBlueCommander = blueCommander;
            this.lastRedCommander = redCommander;
        }
        
        // Remove old alerts
        this.alertQueue = this.alertQueue.filter(alert => now - alert.time < 10000);
        
        // Update DOM
        alertContainer.innerHTML = this.alertQueue.map(alert => 
            `<div class="alert ${alert.type}">${alert.message}</div>`
        ).join('');
    }
    
    updateProductionQueue(gameContext) {
        const queueEl = document.getElementById('productionQueue');
        if (!queueEl) return;
        
        // Mock production queue for now
        queueEl.innerHTML = `
            <div class="queue-item" style="background: #333; color: #888;">Empty</div>
            <div class="queue-item" style="background: #333; color: #888;">Empty</div>
            <div class="queue-item" style="background: #333; color: #888;">Empty</div>
        `;
    }
    
    addAlert(message, type = 'info') {
        this.alertQueue.push({
            message,
            type,
            time: Date.now()
        });
        
        // Keep only last 5 alerts
        if (this.alertQueue.length > 5) {
            this.alertQueue.shift();
        }
    }
    
    handleCommand(action) {
        console.log(`Command: ${action}`);
        
        switch (action) {
            case 'move':
                this.addAlert('Move command selected. Click to set destination.', 'info');
                break;
            case 'attack':
                this.addAlert('Attack command selected. Click target to attack.', 'info');
                break;
            case 'stop':
                this.addAlert('Stop command issued to selected units.', 'info');
                break;
            case 'patrol':
                this.addAlert('Patrol command selected. Click to set patrol point.', 'info');
                break;
            case 'build':
                this.addAlert('Build menu opened.', 'info');
                break;
        }
    }
    
    handleMinimapClick(event) {
        const rect = this.minimapCanvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;
        
        // Convert minimap coordinates to world coordinates
        const MINIMAP_SIZE = 200;
        const WORLD_SIZE = 5000; // Assuming 5000x5000 world
        
        const worldX = (x / MINIMAP_SIZE) * WORLD_SIZE;
        const worldY = (y / MINIMAP_SIZE) * WORLD_SIZE;
        
        // Move camera to clicked position
        if (this.gameContext.camera) {
            this.gameContext.camera.targetX = worldX;
            this.gameContext.camera.targetY = worldY;
            this.addAlert(`Camera moved to (${Math.round(worldX)}, ${Math.round(worldY)})`, 'info');
        }
    }
    
    handleKeyboard(event) {
        // Prevent handling if typing in input field
        if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
            return;
        }
        
        switch (event.key.toLowerCase()) {
            case 'm':
                this.handleCommand('move');
                break;
            case 'a':
                this.handleCommand('attack');
                break;
            case 's':
                this.handleCommand('stop');
                break;
            case 'p':
                this.handleCommand('patrol');
                break;
            case 'b':
                this.handleCommand('build');
                break;
        }
    }
    
    selectUnit(unit) {
        if (!this.selectedUnits.includes(unit)) {
            this.selectedUnits.push(unit);
        }
    }
    
    selectUnits(units) {
        this.selectedUnits = [...units];
    }
    
    clearSelection() {
        this.selectedUnits = [];
    }
    
    formatTime(seconds) {
        const minutes = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
}