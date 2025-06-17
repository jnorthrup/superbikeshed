#!/usr/bin/env node

// RTS Scenario Tester - Plot income curves and early game behavior
// Test specific terrain/resource configurations headlessly

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';

class ScenarioTester {
    constructor() {
        this.scenarios = {};
        this.results = {};
    }

    // Create custom terrain with specific resource placement
    createTestTerrain(scenario) {
        const terrain = [];
        const resourceNodes = [];

        // Initialize as all land
        for (let x = 0; x < GRID_SIZE; x++) {
            terrain[x] = [];
            for (let y = 0; y < GRID_SIZE; y++) {
                terrain[x][y] = TERRAIN_TYPES.LAND;
            }
        }

        // Apply scenario-specific terrain modifications
        if (scenario.waterBarrier) {
            // Create water barrier between commander and resources
            const barrierX = Math.floor(GRID_SIZE * 0.4);
            for (let y = 0; y < GRID_SIZE; y++) {
                for (let x = barrierX; x < barrierX + scenario.waterBarrier.width; x++) {
                    if (x < GRID_SIZE) {
                        terrain[x][y] = TERRAIN_TYPES.WATER;
                    }
                }
            }
        }

        if (scenario.mountainRange) {
            // Create impassable mountain range
            const mountainY = Math.floor(GRID_SIZE * 0.5);
            for (let x = 0; x < GRID_SIZE; x++) {
                for (let y = mountainY; y < mountainY + scenario.mountainRange.height; y++) {
                    if (y < GRID_SIZE) {
                        terrain[x][y] = TERRAIN_TYPES.MOUNTAIN;
                    }
                }
            }
        }

        // Place resources at specific distances
        scenario.resourcePlacements.forEach(placement => {
            const worldX = placement.x * TILE_SIZE;
            const worldY = placement.y * TILE_SIZE;
            
            resourceNodes.push({
                x: worldX,
                y: worldY,
                type: placement.type,
                amount: placement.amount || 10000,
                maxAmount: placement.amount || 10000,
                occupied: false
            });
        });

        return { terrain, resourceNodes };
    }

    // Test specific scenario
    async testScenario(name, config) {
        console.log(`\n🧪 TESTING SCENARIO: ${name}`);
        console.log(`📋 Config: ${JSON.stringify(config, null, 2)}`);

        const results = {
            name,
            config,
            timeline: [],
            finalStats: {},
            economicCurve: [],
            buildOrder: [],
            pathingEfficiency: {}
        };

        // Create test environment
        const gameState = {
            gameTime: 0,
            winner: null,
            paused: false
        };

        const resources = {
            blue: { mass: config.startingMass || 100, energy: config.startingEnergy || 150, massIncome: 0, energyIncome: 0 },
            red: { mass: config.startingMass || 100, energy: config.startingEnergy || 150, massIncome: 0, energyIncome: 0 }
        };

        const units = [];
        const buildings = [];

        // Generate custom terrain
        const { terrain, resourceNodes } = this.createTestTerrain(config);

        // Spawn commanders at specified positions
        const blueStart = { x: config.blueSpawn.x * TILE_SIZE, y: config.blueSpawn.y * TILE_SIZE };
        const redStart = { x: config.redSpawn.x * TILE_SIZE, y: config.redSpawn.y * TILE_SIZE };

        units.push(new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander));
        units.push(new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander));

        // Setup commander build lists
        if (UNIT_TYPES.commander) {
            UNIT_TYPES.commander.buildList = [
                BUILDING_TYPES.massExtractor,
                BUILDING_TYPES.energyExtractor,
                BUILDING_TYPES.landFactory
            ];
        }

        console.log(`⚡ Blue Commander at (${Math.floor(blueStart.x)}, ${Math.floor(blueStart.y)})`);
        console.log(`⚡ Red Commander at (${Math.floor(redStart.x)}, ${Math.floor(redStart.y)})`);

        // Calculate distances to resources
        resourceNodes.forEach((node, i) => {
            const distToBlue = Math.sqrt((node.x - blueStart.x) ** 2 + (node.y - blueStart.y) ** 2);
            const distToRed = Math.sqrt((node.x - redStart.x) ** 2 + (node.y - redStart.y) ** 2);
            console.log(`💎 Resource ${i + 1} (${node.type}): Blue=${Math.floor(distToBlue)}, Red=${Math.floor(distToRed)} units away`);
        });

        // Run simulation
        const maxFrames = (config.duration || 180) * 60; // Default 3 minutes
        let lastResourceCheck = 0;

        for (let frame = 0; frame < maxFrames && !gameState.winner; frame++) {
            gameState.gameTime += 1/60;

            // Create game context
            const gameContext = {
                units, buildings, resources, gameState, terrain, resourceNodes,
                UNIT_TYPES, BUILDING_TYPES, WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES,
                Unit, Building,
                addEvent: (ctx, type, msg, importance, pos) => {
                    if (importance >= 2) {
                        results.timeline.push({
                            time: gameState.gameTime,
                            event: `${type}: ${msg}`,
                            position: pos
                        });
                    }
                },
                mainGameGlobals: {
                    resources,
                    units,
                    Unit, Building,
                    addEvent: (type, msg) => {
                        results.timeline.push({
                            time: gameState.gameTime,
                            event: `${type}: ${msg}`
                        });
                    }
                }
            };

            // Update units
            for (let i = units.length - 1; i >= 0; i--) {
                const unit = units[i];
                unit.update(gameContext);

                if (unit.hp <= 0) {
                    if (unit.type === UNIT_TYPES.commander) {
                        gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                        results.timeline.push({
                            time: gameState.gameTime,
                            event: `VICTORY: ${gameState.winner} wins! ${unit.team} commander destroyed`
                        });
                    }
                    units.splice(i, 1);
                }
            }

            // Update buildings
            for (let i = buildings.length - 1; i >= 0; i--) {
                const building = buildings[i];
                building.update(units, gameContext.mainGameGlobals);

                if (building.hp <= 0) {
                    buildings.splice(i, 1);
                }
            }

            // Record economic data every 5 seconds
            if (frame % (5 * 60) === 0) {
                results.economicCurve.push({
                    time: gameState.gameTime,
                    blue: {
                        mass: Math.floor(resources.blue.mass),
                        energy: Math.floor(resources.blue.energy),
                        income: resources.blue.massIncome + resources.blue.energyIncome,
                        buildings: buildings.filter(b => b.team === 'blue').length,
                        units: units.filter(u => u.team === 'blue').length
                    },
                    red: {
                        mass: Math.floor(resources.red.mass),
                        energy: Math.floor(resources.red.energy),
                        income: resources.red.massIncome + resources.red.energyIncome,
                        buildings: buildings.filter(b => b.team === 'red').length,
                        units: units.filter(u => u.team === 'red').length
                    }
                });
            }

            // Track build orders
            buildings.forEach(building => {
                if (!results.buildOrder.find(b => b.id === building.id)) {
                    results.buildOrder.push({
                        id: building.id || Math.random(),
                        time: gameState.gameTime,
                        team: building.team,
                        type: building.type.name,
                        position: { x: building.x, y: building.y }
                    });
                }
            });
        }

        // Calculate final statistics
        results.finalStats = {
            duration: gameState.gameTime,
            winner: gameState.winner,
            blue: this.calculateTeamStats('blue', units, buildings, resources),
            red: this.calculateTeamStats('red', units, buildings, resources)
        };

        // Calculate pathing efficiency
        results.pathingEfficiency = this.calculatePathingEfficiency(config, results);

        this.results[name] = results;
        this.printResults(results);
        return results;
    }

    calculateTeamStats(team, units, buildings, resources) {
        const teamUnits = units.filter(u => u.team === team);
        const teamBuildings = buildings.filter(b => b.team === team);
        
        return {
            resources: `${Math.floor(resources[team].mass)}M/${Math.floor(resources[team].energy)}E`,
            income: `${resources[team].massIncome || 0}M/min, ${resources[team].energyIncome || 0}E/min`,
            units: teamUnits.length,
            buildings: teamBuildings.length,
            extractors: teamBuildings.filter(b => b.type.resourceGeneration).length,
            factories: teamBuildings.filter(b => b.type.produces).length,
            commander: teamUnits.find(u => u.type === UNIT_TYPES.commander)?.hp || 0
        };
    }

    calculatePathingEfficiency(config, results) {
        // Analyze how terrain affected expansion efficiency
        const firstExtractorTime = results.timeline.find(e => e.event.includes('extractor'))?.time || 999;
        const resourceDistance = Math.min(...config.resourcePlacements.map(r => 
            Math.sqrt((r.x * TILE_SIZE - config.blueSpawn.x * TILE_SIZE) ** 2 + 
                     (r.y * TILE_SIZE - config.blueSpawn.y * TILE_SIZE) ** 2)
        ));
        
        return {
            firstExtractorTime,
            avgResourceDistance: resourceDistance,
            efficiency: resourceDistance > 0 ? (300 / firstExtractorTime) * (200 / resourceDistance) : 0
        };
    }

    printResults(results) {
        console.log(`\n📊 RESULTS FOR: ${results.name}`);
        console.log(`⏱️  Duration: ${this.formatTime(results.finalStats.duration)}`);
        console.log(`🏆 Winner: ${results.finalStats.winner || 'NONE'}`);
        
        console.log(`\n💙 BLUE TEAM:`);
        console.log(`   Resources: ${results.finalStats.blue.resources}`);
        console.log(`   Income: ${results.finalStats.blue.income}`);
        console.log(`   Buildings: ${results.finalStats.blue.buildings} (${results.finalStats.blue.extractors} extractors, ${results.finalStats.blue.factories} factories)`);
        console.log(`   Units: ${results.finalStats.blue.units}`);
        
        console.log(`\n❤️  RED TEAM:`);
        console.log(`   Resources: ${results.finalStats.red.resources}`);
        console.log(`   Income: ${results.finalStats.red.income}`);  
        console.log(`   Buildings: ${results.finalStats.red.buildings} (${results.finalStats.red.extractors} extractors, ${results.finalStats.red.factories} factories)`);
        console.log(`   Units: ${results.finalStats.red.units}`);

        console.log(`\n📈 ECONOMIC CURVE:`);
        results.economicCurve.forEach(point => {
            console.log(`   ${this.formatTime(point.time)}: Blue ${point.blue.mass}M/${point.blue.energy}E | Red ${point.red.mass}M/${point.red.energy}E`);
        });

        console.log(`\n🏗️  BUILD ORDER:`);
        results.buildOrder.forEach(build => {
            console.log(`   ${this.formatTime(build.time)}: ${build.team.toUpperCase()} built ${build.type}`);
        });

        console.log(`\n🛤️  PATHING EFFICIENCY:`);
        console.log(`   First Extractor: ${this.formatTime(results.pathingEfficiency.firstExtractorTime)}`);
        console.log(`   Avg Resource Distance: ${Math.floor(results.pathingEfficiency.avgResourceDistance)} units`);
        console.log(`   Efficiency Score: ${results.pathingEfficiency.efficiency.toFixed(2)}`);
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    // Predefined test scenarios
    async runStandardTests() {
        console.log('🚀 RUNNING STANDARD EARLY GAME BEHAVIOR TESTS\n');

        // Test 1: Close resources
        await this.testScenario('CLOSE_RESOURCES', {
            blueSpawn: { x: 10, y: 25 },
            redSpawn: { x: 40, y: 25 },
            resourcePlacements: [
                { x: 15, y: 25, type: 'mass', amount: 10000 },
                { x: 12, y: 28, type: 'energy', amount: 10000 },
                { x: 35, y: 25, type: 'mass', amount: 10000 },
                { x: 38, y: 22, type: 'energy', amount: 10000 }
            ],
            duration: 180
        });

        // Test 2: Distant resources
        await this.testScenario('DISTANT_RESOURCES', {
            blueSpawn: { x: 10, y: 25 },
            redSpawn: { x: 40, y: 25 },
            resourcePlacements: [
                { x: 25, y: 10, type: 'mass', amount: 10000 },
                { x: 25, y: 40, type: 'energy', amount: 10000 },
                { x: 30, y: 15, type: 'mass', amount: 10000 },
                { x: 20, y: 35, type: 'energy', amount: 10000 }
            ],
            duration: 180
        });

        // Test 3: Water barrier scenario
        await this.testScenario('WATER_BARRIER', {
            blueSpawn: { x: 10, y: 25 },
            redSpawn: { x: 40, y: 25 },
            waterBarrier: { width: 5 },
            resourcePlacements: [
                { x: 30, y: 25, type: 'mass', amount: 10000 },
                { x: 32, y: 28, type: 'energy', amount: 10000 },
                { x: 18, y: 22, type: 'mass', amount: 10000 },
                { x: 8, y: 30, type: 'energy', amount: 10000 }
            ],
            duration: 300
        });

        // Test 4: Resource scarcity
        await this.testScenario('RESOURCE_SCARCITY', {
            startingMass: 50,
            startingEnergy: 75,
            blueSpawn: { x: 10, y: 25 },
            redSpawn: { x: 40, y: 25 },
            resourcePlacements: [
                { x: 25, y: 25, type: 'mass', amount: 5000 },
                { x: 27, y: 27, type: 'energy', amount: 5000 }
            ],
            duration: 240
        });

        console.log('\n✅ ALL TESTS COMPLETED');
        this.compareResults();
    }

    compareResults() {
        console.log('\n📋 COMPARATIVE ANALYSIS:');
        
        Object.values(this.results).forEach(result => {
            const efficiency = result.pathingEfficiency.efficiency;
            const firstExtractor = result.pathingEfficiency.firstExtractorTime;
            console.log(`${result.name}: Efficiency=${efficiency.toFixed(2)}, First Extractor=${this.formatTime(firstExtractor)}`);
        });
    }
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}`) {
    const tester = new ScenarioTester();
    tester.runStandardTests();
}

export { ScenarioTester };