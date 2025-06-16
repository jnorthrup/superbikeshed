#!/usr/bin/env node

// Headless RTS Game Engine - Clean Strategic Event Telegraph
// No DOM, no rendering, just pure game logic and event reporting

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';
import { generateTerrain, findLandPosition } from '../core/terrain.js';

class HeadlessRTSEngine {
    constructor() {
        this.gameState = {
            gameTime: 0,
            winner: null,
            paused: false
        };
        
        this.units = [];
        this.buildings = [];
        this.resources = {
            blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
            red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        };
        
        this.terrain = [];
        this.resourceNodes = [];
        this.lastEventTime = 0;
        
        // Strategic event tracking
        this.eventFilters = {
            CRITICAL: ['COMMANDER_SPAWNED', 'COMMANDER_DESTROYED', 'VICTORY'],
            STRATEGIC: ['FIRST_EXTRACTOR', 'FIRST_FACTORY', 'TECH_ADVANCE', 'MAJOR_COMBAT'],
            ECONOMIC: ['RESOURCE_SECURED', 'ECONOMY_MILESTONE', 'RESOURCE_DEPLETION'],
            MILITARY: ['UNIT_PRODUCED', 'FORMATION_COMPLETE', 'ASSAULT_LAUNCHED']
        };
        
        this.initializeGame();
    }
    
    initializeGame() {
        this.telegraph('CRITICAL', 'GAME_START', 'Headless RTS Engine Initialized - Naked Commander Battle');
        
        // Generate terrain
        generateTerrain(this);
        
        // Find starting positions
        const blueStart = findLandPosition(this, WORLD_SIZE * 0.2, WORLD_SIZE * 0.5, 5);
        const redStart = findLandPosition(this, WORLD_SIZE * 0.8, WORLD_SIZE * 0.5, 5);
        
        if (!blueStart || !redStart) {
            this.telegraph('CRITICAL', 'TERRAIN_FAIL', 'Failed to find suitable starting positions');
            return;
        }
        
        // Spawn naked commanders
        this.units.push(new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander));
        this.units.push(new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander));
        
        this.telegraph('CRITICAL', 'COMMANDERS_DEPLOYED', 
            `Blue Commander at (${Math.floor(blueStart.x)}, ${Math.floor(blueStart.y)}) | Red Commander at (${Math.floor(redStart.x)}, ${Math.floor(redStart.y)})`);
        
        // Populate commander build lists
        if (UNIT_TYPES.commander) {
            UNIT_TYPES.commander.buildList = [
                BUILDING_TYPES.massExtractor,
                BUILDING_TYPES.energyExtractor,
                BUILDING_TYPES.landFactory
            ];
        }
        
        this.telegraph('STRATEGIC', 'NAKED_START', 
            `Both teams start with 100M/150E - Must bootstrap economy from nothing`);
    }
    
    telegraph(priority, eventType, message, data = null) {
        const timestamp = this.formatTime(this.gameState.gameTime);
        const fullMessage = `[${timestamp}] ${priority}:${eventType} - ${message}`;
        
        // Output to console with priority styling
        switch(priority) {
            case 'CRITICAL':
                console.log(`🚨 ${fullMessage}`);
                break;
            case 'STRATEGIC': 
                console.log(`⚡ ${fullMessage}`);
                break;
            case 'ECONOMIC':
                console.log(`💰 ${fullMessage}`);
                break;
            case 'MILITARY':
                console.log(`⚔️  ${fullMessage}`);
                break;
            default:
                console.log(`📋 ${fullMessage}`);
        }
        
        if (data) {
            console.log(`   └─ ${JSON.stringify(data)}`);
        }
    }
    
    update() {
        if (this.gameState.paused || this.gameState.winner) return;
        
        this.gameState.gameTime += 1/60; // 60 FPS simulation
        
        // Update units
        for (let i = this.units.length - 1; i >= 0; i--) {
            const unit = this.units[i];
            unit.update(this.createGameContext());
            
            if (unit.hp <= 0) {
                if (unit.type === UNIT_TYPES.commander) {
                    this.gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                    this.telegraph('CRITICAL', 'COMMANDER_DESTROYED', 
                        `${unit.team.toUpperCase()} Commander eliminated! ${this.gameState.winner} VICTORY!`);
                } else {
                    this.telegraph('MILITARY', 'UNIT_LOST', 
                        `${unit.team} ${unit.type.name} destroyed`);
                }
                this.units.splice(i, 1);
            }
        }
        
        // Update buildings  
        for (let i = this.buildings.length - 1; i >= 0; i--) {
            const building = this.buildings[i];
            building.update(this.units, this.createMainGameGlobals());
            
            if (building.hp <= 0) {
                this.telegraph('ECONOMIC', 'STRUCTURE_LOST', 
                    `${building.team} ${building.type.name} destroyed`);
                this.buildings.splice(i, 1);
            }
        }
        
        // Check for strategic milestones
        this.checkMilestones();
    }
    
    checkMilestones() {
        for (const team of ['blue', 'red']) {
            const teamBuildings = this.buildings.filter(b => b.team === team);
            const teamUnits = this.units.filter(u => u.team === team);
            
            // First extractor milestone
            const extractors = teamBuildings.filter(b => b.type.resourceGeneration);
            if (extractors.length === 1 && !this[`${team}FirstExtractor`]) {
                this[`${team}FirstExtractor`] = true;
                this.telegraph('STRATEGIC', 'FIRST_EXTRACTOR', 
                    `${team.toUpperCase()} completes first extractor - economy bootstrap begins`);
            }
            
            // First factory milestone  
            const factories = teamBuildings.filter(b => b.type.produces);
            if (factories.length === 1 && !this[`${team}FirstFactory`]) {
                this[`${team}FirstFactory`] = true;
                this.telegraph('STRATEGIC', 'FIRST_FACTORY', 
                    `${team.toUpperCase()} builds first factory - military production begins`,
                    { resources: `${Math.floor(this.resources[team].mass)}M/${Math.floor(this.resources[team].energy)}E` });
            }
            
            // Resource milestones
            const totalResources = this.resources[team].mass + this.resources[team].energy;
            if (totalResources > 500 && !this[`${team}ResourceMilestone500`]) {
                this[`${team}ResourceMilestone500`] = true;
                this.telegraph('ECONOMIC', 'RESOURCE_MILESTONE', 
                    `${team.toUpperCase()} reaches 500+ total resources - economic stability achieved`);
            }
            
            // Military milestones
            const combatUnits = teamUnits.filter(u => !u.type.support).length;
            if (combatUnits >= 5 && !this[`${team}Army5`]) {
                this[`${team}Army5`] = true;
                this.telegraph('MILITARY', 'FORCE_BUILDUP', 
                    `${team.toUpperCase()} fields 5+ combat units - military force established`);
            }
        }
    }
    
    createGameContext() {
        return {
            units: this.units,
            buildings: this.buildings,
            resources: this.resources,
            gameState: this.gameState,
            terrain: this.terrain,
            resourceNodes: this.resourceNodes,
            UNIT_TYPES,
            BUILDING_TYPES,
            WORLD_SIZE,
            TILE_SIZE,
            GRID_SIZE,
            TERRAIN_TYPES,
            Unit,
            Building,
            addEvent: (ctx, type, msg, importance, pos) => {
                if (importance >= 2) {
                    this.telegraph('STRATEGIC', type.toUpperCase(), msg);
                }
            },
            mainGameGlobals: this.createMainGameGlobals()
        };
    }
    
    createMainGameGlobals() {
        return {
            resources: this.resources,
            units: this.units,
            Unit,
            Building,
            addEvent: (type, msg) => this.telegraph('STRATEGIC', type.toUpperCase(), msg)
        };
    }
    
    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
    
    getStatus() {
        const blueUnits = this.units.filter(u => u.team === 'blue');
        const redUnits = this.units.filter(u => u.team === 'red');
        const blueBuildings = this.buildings.filter(b => b.team === 'blue');
        const redBuildings = this.buildings.filter(b => b.team === 'red');
        
        return {
            gameTime: this.formatTime(this.gameState.gameTime),
            winner: this.gameState.winner,
            blue: {
                resources: `${Math.floor(this.resources.blue.mass)}M/${Math.floor(this.resources.blue.energy)}E`,
                units: blueUnits.length,
                buildings: blueBuildings.length,
                commander: blueUnits.find(u => u.type === UNIT_TYPES.commander)?.hp || 0
            },
            red: {
                resources: `${Math.floor(this.resources.red.mass)}M/${Math.floor(this.resources.red.energy)}E`, 
                units: redUnits.length,
                buildings: redBuildings.length,
                commander: redUnits.find(u => u.type === UNIT_TYPES.commander)?.hp || 0
            }
        };
    }
    
    run(duration = 300) { // Default 5 minutes
        this.telegraph('CRITICAL', 'SIMULATION_START', `Running ${duration}s simulation`);
        
        const startTime = Date.now();
        const targetFrames = duration * 60; // 60 FPS
        
        for (let frame = 0; frame < targetFrames && !this.gameState.winner; frame++) {
            this.update();
            
            // Status report every 30 seconds
            if (frame % (30 * 60) === 0) {
                const status = this.getStatus();
                this.telegraph('STRATEGIC', 'STATUS_REPORT', 
                    `Blue: ${status.blue.resources} ${status.blue.units}U ${status.blue.buildings}B | Red: ${status.red.resources} ${status.red.units}U ${status.red.buildings}B`);
            }
        }
        
        const endTime = Date.now();
        const realTime = (endTime - startTime) / 1000;
        
        this.telegraph('CRITICAL', 'SIMULATION_END', 
            `Completed in ${realTime.toFixed(1)}s real-time | Winner: ${this.gameState.winner || 'NONE'}`);
            
        return this.getStatus();
    }
}

// Command line interface
if (import.meta.url === `file://${process.argv[1]}`) {
    const duration = parseInt(process.argv[2]) || 300;
    const engine = new HeadlessRTSEngine();
    engine.run(duration);
}

export { HeadlessRTSEngine };