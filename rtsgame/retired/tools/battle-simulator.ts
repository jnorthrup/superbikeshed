#!/usr/bin/env node

// Battle Simulation Engine - Improve Customer Experience Through Data Analysis
// Run hundreds of battles to identify gameplay issues and balance problems

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';
import { generateTerrain } from '../core/terrainManager.js';
import { findLandPosition } from '../core/terrain.js';

class BattleSimulator {
    constructor() {
        this.simulations = [];
        this.customerExperienceMetrics = {
            gameLength: [],
            decisiveness: [],
            actionIntensity: [],
            economicProgression: [],
            balanceIssues: [],
            frustrationPoints: [],
            engagementScore: []
        };
    }

    async runBattleSimulation(config = {}) {
        const simulation = {
            id: Date.now() + Math.random(),
            startTime: Date.now(),
            config: {
                duration: config.duration || 600, // 10 minutes default
                startingResources: config.startingResources || { mass: 100, energy: 150 },
                terrainType: config.terrainType || 'random',
                aiDifficulty: config.aiDifficulty || 'normal',
                ...config
            },
            events: [],
            metrics: {
                gamePhases: [],
                combatEvents: [],
                economicMilestones: [],
                playerActions: [],
                balanceData: []
            },
            customerExperience: {
                boredomScore: 0,
                frustrationScore: 0,
                excitementPeaks: [],
                paceRating: 0,
                outcomeQuality: 0
            }
        };

        // Initialize game state
        const gameState = {
            gameTime: 0,
            winner: null,
            paused: false,
            phase: 'early' // early, mid, late
        };

        const resources = {
            blue: { ...simulation.config.startingResources, massIncome: 0, energyIncome: 0 },
            red: { ...simulation.config.startingResources, massIncome: 0, energyIncome: 0 }
        };

        const units = [];
        const buildings = [];
        
        // Generate terrain
        const terrain = [];
        const resourceNodes = [];
        this.generateCustomTerrain(terrain, resourceNodes, simulation.config.terrainType);

        // Spawn commanders
        const blueStart = findLandPosition({ terrain, resourceNodes }, WORLD_SIZE * 0.2, WORLD_SIZE * 0.5, 5);
        const redStart = findLandPosition({ terrain, resourceNodes }, WORLD_SIZE * 0.8, WORLD_SIZE * 0.5, 5);

        if (!blueStart || !redStart) {
            simulation.customerExperience.frustrationScore += 50; // Bad map generation
            return simulation;
        }

        units.push(new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander));
        units.push(new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander));

        // Setup build lists
        if (UNIT_TYPES.commander) {
            UNIT_TYPES.commander.buildList = [
                BUILDING_TYPES.massExtractor,
                BUILDING_TYPES.energyExtractor,
                BUILDING_TYPES.landFactory
            ];
        }

        this.logEvent(simulation, 'GAME_START', `Battle simulation ${simulation.id} commenced`);

        // Main simulation loop
        const maxFrames = simulation.config.duration * 60;
        let lastIntensityCheck = 0;
        let lastPhaseCheck = 0;
        let combatIntensity = 0;
        let economicActivity = 0;

        for (let frame = 0; frame < maxFrames && !gameState.winner; frame++) {
            gameState.gameTime += 1/60;

            const gameContext = this.createGameContext(units, buildings, resources, gameState, terrain, resourceNodes, simulation);

            // Update units
            for (let i = units.length - 1; i >= 0; i--) {
                const unit = units[i];
                const prevHP = unit.hp;
                unit.update(gameContext);

                // Track combat activity
                if (unit.target && unit.hp < prevHP) {
                    combatIntensity += 10;
                    simulation.metrics.combatEvents.push({
                        time: gameState.gameTime,
                        type: 'combat_damage',
                        unit: unit.type.name,
                        team: unit.team
                    });
                }

                if (unit.hp <= 0) {
                    if (unit.type === UNIT_TYPES.commander) {
                        gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                        this.logEvent(simulation, 'COMMANDER_DESTROYED', 
                            `${unit.team} commander eliminated! ${gameState.winner} victory!`);
                        
                        // Analyze victory quality
                        this.analyzeVictoryQuality(simulation, gameState, units, buildings);
                    } else {
                        this.logEvent(simulation, 'UNIT_DESTROYED', `${unit.team} ${unit.type.name} destroyed`);
                    }
                    units.splice(i, 1);
                }
            }

            // Update buildings
            for (let i = buildings.length - 1; i >= 0; i--) {
                const building = buildings[i];
                building.update(units, gameContext.mainGameGlobals);

                if (building.hp <= 0) {
                    this.logEvent(simulation, 'BUILDING_DESTROYED', `${building.team} ${building.type.name} destroyed`);
                    buildings.splice(i, 1);
                }
            }

            // Track economic activity
            if (frame % 60 === 0) { // Every second
                const newEconomicActivity = this.calculateEconomicActivity(units, buildings, resources);
                if (newEconomicActivity > economicActivity) {
                    economicActivity = newEconomicActivity;
                    simulation.metrics.economicMilestones.push({
                        time: gameState.gameTime,
                        activity: economicActivity,
                        blueResources: resources.blue.mass + resources.blue.energy,
                        redResources: resources.red.mass + resources.red.energy
                    });
                }
            }

            // Analyze customer experience every 10 seconds
            if (frame % (10 * 60) === 0) {
                this.analyzeCustomerExperience(simulation, gameState, units, buildings, resources, combatIntensity);
                combatIntensity *= 0.8; // Decay intensity
            }

            // Detect game phases
            if (frame % (30 * 60) === 0) { // Every 30 seconds
                this.detectGamePhase(simulation, gameState, units, buildings, resources);
            }

            // Check for balance issues
            if (frame % (60 * 60) === 0) { // Every minute
                this.detectBalanceIssues(simulation, units, buildings, resources);
            }
        }

        // Final analysis
        simulation.endTime = Date.now();
        simulation.realDuration = (simulation.endTime - simulation.startTime) / 1000;
        simulation.customerExperience.outcomeQuality = this.calculateOutcomeQuality(simulation, gameState);
        
        this.simulations.push(simulation);
        this.updateGlobalMetrics(simulation);
        
        return simulation;
    }

    generateCustomTerrain(terrain, resourceNodes, terrainType) {
        // Initialize terrain
        for (let x = 0; x < GRID_SIZE; x++) {
            terrain[x] = [];
            for (let y = 0; y < GRID_SIZE; y++) {
                terrain[x][y] = TERRAIN_TYPES.LAND;
            }
        }

        // Apply terrain type
        switch (terrainType) {
            case 'islands':
                this.generateIslandTerrain(terrain);
                break;
            case 'chokepoints':
                this.generateChokepointTerrain(terrain);
                break;
            case 'open':
                // Mostly land, few obstacles
                break;
            case 'random':
            default:
                this.generateRandomTerrain(terrain);
                break;
        }

        // Place resource nodes
        for (let i = 0; i < 20; i++) {
            const x = Math.random() * WORLD_SIZE;
            const y = Math.random() * WORLD_SIZE;
            const type = Math.random() < 0.5 ? 'mass' : 'energy';
            
            resourceNodes.push({
                x, y, type,
                amount: 10000,
                maxAmount: 10000,
                occupied: false
            });
        }
    }

    generateIslandTerrain(terrain) {
        const centerX = GRID_SIZE / 2;
        const centerY = GRID_SIZE / 2;
        
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const distToCenter = Math.sqrt((x - centerX) ** 2 + (y - centerY) ** 2);
                if (distToCenter > GRID_SIZE * 0.3) {
                    terrain[x][y] = TERRAIN_TYPES.WATER;
                }
            }
        }
    }

    generateChokepointTerrain(terrain) {
        // Create narrow passages
        const chokeY = GRID_SIZE / 2;
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = chokeY - 2; y <= chokeY + 2; y++) {
                if (y >= 0 && y < GRID_SIZE) {
                    if (Math.abs(x - GRID_SIZE/2) < 3) {
                        terrain[x][y] = TERRAIN_TYPES.LAND; // Passage
                    } else {
                        terrain[x][y] = TERRAIN_TYPES.MOUNTAIN;
                    }
                }
            }
        }
    }

    generateRandomTerrain(terrain) {
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const noise = Math.random();
                if (noise < 0.1) terrain[x][y] = TERRAIN_TYPES.WATER;
                else if (noise > 0.9) terrain[x][y] = TERRAIN_TYPES.MOUNTAIN;
            }
        }
    }

    calculateEconomicActivity(units, buildings, resources) {
        let activity = 0;
        activity += buildings.filter(b => b.type.resourceGeneration).length * 10;
        activity += buildings.filter(b => b.type.produces).length * 5;
        activity += (resources.blue.mass + resources.blue.energy + resources.red.mass + resources.red.energy) / 100;
        return activity;
    }

    analyzeCustomerExperience(simulation, gameState, units, buildings, resources, combatIntensity) {
        const ce = simulation.customerExperience;
        
        // Boredom: Low activity periods
        if (combatIntensity < 5 && buildings.length < 3) {
            ce.boredomScore += 2;
        }

        // Excitement: High combat intensity
        if (combatIntensity > 50) {
            ce.excitementPeaks.push({
                time: gameState.gameTime,
                intensity: combatIntensity
            });
        }

        // Frustration: Resource starvation or unit stagnation
        const totalUnits = units.length;
        const totalResources = resources.blue.mass + resources.blue.energy + resources.red.mass + resources.red.energy;
        
        if (totalUnits <= 2 && gameState.gameTime > 120) { // Only commanders after 2 minutes
            ce.frustrationScore += 5;
        }
        
        if (totalResources < 100 && gameState.gameTime > 60) { // Resource starvation
            ce.frustrationScore += 3;
        }

        // Pace rating
        const expectedProgression = gameState.gameTime / 10; // Expected buildings per 10 seconds
        const actualProgression = buildings.length;
        ce.paceRating = Math.min(100, (actualProgression / expectedProgression) * 100);
    }

    detectGamePhase(simulation, gameState, units, buildings, resources) {
        const totalBuildings = buildings.length;
        const totalUnits = units.length;
        const avgResources = (resources.blue.mass + resources.blue.energy + resources.red.mass + resources.red.energy) / 4;

        let newPhase = 'early';
        
        if (totalBuildings >= 4 && avgResources > 200) {
            newPhase = 'mid';
        }
        
        if (totalBuildings >= 8 && totalUnits >= 10) {
            newPhase = 'late';
        }

        if (newPhase !== gameState.phase) {
            gameState.phase = newPhase;
            simulation.metrics.gamePhases.push({
                time: gameState.gameTime,
                phase: newPhase,
                buildings: totalBuildings,
                units: totalUnits,
                avgResources
            });
        }
    }

    detectBalanceIssues(simulation, units, buildings, resources) {
        const blueUnits = units.filter(u => u.team === 'blue').length;
        const redUnits = units.filter(u => u.team === 'red').length;
        const blueBuildings = buildings.filter(b => b.team === 'blue').length;
        const redBuildings = buildings.filter(b => b.team === 'red').length;

        // Detect severe imbalances
        if (Math.abs(blueUnits - redUnits) > 5) {
            simulation.metrics.balanceData.push({
                time: simulation.gameState?.gameTime || 0,
                issue: 'UNIT_IMBALANCE',
                blue: blueUnits,
                red: redUnits,
                severity: Math.abs(blueUnits - redUnits)
            });
        }

        if (Math.abs(blueBuildings - redBuildings) > 3) {
            simulation.metrics.balanceData.push({
                time: simulation.gameState?.gameTime || 0,
                issue: 'BUILDING_IMBALANCE',
                blue: blueBuildings,
                red: redBuildings,
                severity: Math.abs(blueBuildings - redBuildings)
            });
        }
    }

    analyzeVictoryQuality(simulation, gameState, units, buildings) {
        const ce = simulation.customerExperience;
        const gameDuration = gameState.gameTime;
        
        // Quick victories might feel unsatisfying
        if (gameDuration < 120) {
            ce.outcomeQuality = 30; // Too quick
        } else if (gameDuration > 480) {
            ce.outcomeQuality = 40; // Too long
        } else {
            ce.outcomeQuality = 80; // Good duration
        }

        // Close battles are more exciting
        const unitDifference = Math.abs(units.filter(u => u.team === 'blue').length - units.filter(u => u.team === 'red').length);
        if (unitDifference <= 2) {
            ce.outcomeQuality += 20; // Close battle bonus
        }
    }

    calculateOutcomeQuality(simulation, gameState) {
        let quality = 50; // Base score
        
        const duration = gameState.gameTime;
        if (duration >= 180 && duration <= 360) quality += 20; // Good duration
        if (simulation.customerExperience.excitementPeaks.length > 2) quality += 15; // Multiple exciting moments
        if (simulation.customerExperience.boredomScore < 10) quality += 10; // Not boring
        if (simulation.customerExperience.frustrationScore < 20) quality += 5; // Not frustrating
        
        return Math.min(100, quality);
    }

    createGameContext(units, buildings, resources, gameState, terrain, resourceNodes, simulation) {
        return {
            units, buildings, resources, gameState, terrain, resourceNodes,
            UNIT_TYPES, BUILDING_TYPES, WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES,
            Unit, Building,
            seedRandom: {
                random: () => Math.random()
            },
            addEvent: (ctx, type, msg, importance, pos) => {
                if (importance >= 2) {
                    this.logEvent(simulation, type.toUpperCase(), msg, pos);
                }
            },
            mainGameGlobals: {
                resources, units, Unit, Building,
                addEvent: (type, msg) => this.logEvent(simulation, type.toUpperCase(), msg)
            }
        };
    }

    logEvent(simulation, type, message, position = null) {
        simulation.events.push({
            time: simulation.gameState?.gameTime || 0,
            type,
            message,
            position
        });
    }

    updateGlobalMetrics(simulation) {
        const ce = simulation.customerExperience;
        const duration = simulation.config.duration;
        
        this.customerExperienceMetrics.gameLength.push(duration);
        this.customerExperienceMetrics.actionIntensity.push(simulation.events.length / (duration / 60));
        this.customerExperienceMetrics.engagementScore.push(ce.outcomeQuality);
        this.customerExperienceMetrics.frustrationPoints.push(ce.frustrationScore);
    }

    async runBattleAnalysisSuite() {
        console.log('🎮 BATTLE SIMULATION SUITE - CUSTOMER EXPERIENCE ANALYSIS\n');

        const scenarios = [
            { name: 'Standard Battle', terrainType: 'random', duration: 300 },
            { name: 'Island Warfare', terrainType: 'islands', duration: 400 },
            { name: 'Chokepoint Control', terrainType: 'chokepoints', duration: 350 },
            { name: 'Resource Rush', startingResources: { mass: 200, energy: 300 }, duration: 240 },
            { name: 'Survival Mode', startingResources: { mass: 50, energy: 75 }, duration: 450 },
            { name: 'Quick Skirmish', duration: 180 },
            { name: 'Extended Campaign', duration: 600 }
        ];

        const results = [];
        for (const scenario of scenarios) {
            console.log(`🧪 Running: ${scenario.name}`);
            const result = await this.runBattleSimulation(scenario);
            results.push(result);
            this.printSimulationSummary(result);
        }

        console.log('\n📊 CUSTOMER EXPERIENCE ANALYSIS:');
        this.generateCustomerExperienceReport();
        
        console.log('\n🎯 RECOMMENDATIONS:');
        this.generateRecommendations();

        return results;
    }

    printSimulationSummary(simulation) {
        const ce = simulation.customerExperience;
        console.log(`   Duration: ${this.formatTime(simulation.config.duration)}`);
        console.log(`   Engagement Score: ${ce.outcomeQuality}/100`);
        console.log(`   Boredom Score: ${ce.boredomScore} (lower is better)`);
        console.log(`   Frustration Score: ${ce.frustrationScore} (lower is better)`);
        console.log(`   Excitement Peaks: ${ce.excitementPeaks.length}`);
        console.log(`   Events Generated: ${simulation.events.length}\n`);
    }

    generateCustomerExperienceReport() {
        const metrics = this.customerExperienceMetrics;
        
        const avgEngagement = metrics.engagementScore.reduce((a, b) => a + b, 0) / metrics.engagementScore.length;
        const avgFrustration = metrics.frustrationPoints.reduce((a, b) => a + b, 0) / metrics.frustrationPoints.length;
        const avgActionIntensity = metrics.actionIntensity.reduce((a, b) => a + b, 0) / metrics.actionIntensity.length;
        
        console.log(`   Average Engagement Score: ${avgEngagement.toFixed(1)}/100`);
        console.log(`   Average Frustration Level: ${avgFrustration.toFixed(1)} (target: <15)`);
        console.log(`   Average Action Intensity: ${avgActionIntensity.toFixed(1)} events/minute`);
        
        // Identify problematic areas
        if (avgEngagement < 60) console.log(`   ⚠️  LOW ENGAGEMENT - Games not exciting enough`);
        if (avgFrustration > 25) console.log(`   ⚠️  HIGH FRUSTRATION - Players getting stuck/bored`);
        if (avgActionIntensity < 5) console.log(`   ⚠️  LOW ACTION - Games too slow-paced`);
    }

    generateRecommendations() {
        const metrics = this.customerExperienceMetrics;
        const avgEngagement = metrics.engagementScore.reduce((a, b) => a + b, 0) / metrics.engagementScore.length;
        const avgFrustration = metrics.frustrationPoints.reduce((a, b) => a + b, 0) / metrics.frustrationPoints.length;
        
        if (avgEngagement < 60) {
            console.log('   📈 INCREASE starting resources to speed up early game');
            console.log('   📈 ADD more aggressive AI behavior');
            console.log('   📈 REDUCE building times for faster progression');
        }
        
        if (avgFrustration > 25) {
            console.log('   🔧 IMPROVE pathfinding to reduce unit getting stuck');
            console.log('   🔧 ADD better resource node placement');
            console.log('   🔧 BALANCE unit costs vs income rates');
        }
        
        console.log('   ✅ MONITOR battles for stalemate conditions');
        console.log('   ✅ OPTIMIZE terrain generation for better gameplay flow');
        console.log('   ✅ CONSIDER dynamic difficulty adjustment');
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}`) {
    const simulator = new BattleSimulator();
    simulator.runBattleAnalysisSuite();
}

export { BattleSimulator };