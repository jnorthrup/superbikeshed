#!/usr/bin/env node
"use strict";
// Deterministic Replay Demo - Test the complete record and replay system
// Shows how battles can be recorded with deterministic RNG and replayed exactly
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
exports.ReplayDemo = void 0;
const unitTypes_js_1 = require("../config/unitTypes.js");
const buildingTypes_js_1 = require("../config/buildingTypes.js");
const gameConstants_js_1 = require("../config/gameConstants.js");
const unit_js_1 = require("../core/unit.js");
const building_js_1 = require("../core/building.js");
const battleJournal_js_1 = require("../core/battleJournal.js");
const battleReplay_js_1 = require("../core/battleReplay.js");
const deterministicRNG_js_1 = require("../core/deterministicRNG.js");
class ReplayDemo {
    constructor() {
        this.recordedBattleId = null;
        this.testResults = {
            recordingSucceeded: false,
            replaySucceeded: false,
            deterministicVerified: false,
            divergenceCount: 0
        };
    }
    runDemo() {
        return __awaiter(this, void 0, void 0, function* () {
            console.log('🎬 DETERMINISTIC REPLAY SYSTEM DEMO\n');
            console.log('This demo will:');
            console.log('1. Record a battle with deterministic RNG');
            console.log('2. Replay the exact same battle');
            console.log('3. Verify deterministic reproduction\n');
            // Step 1: Record a battle
            console.log('📹 STEP 1: Recording Battle...');
            const recordSuccess = yield this.recordBattle();
            this.testResults.recordingSucceeded = recordSuccess;
            if (!recordSuccess) {
                console.error('❌ Failed to record battle');
                return this.testResults;
            }
            // Step 2: Replay the battle
            console.log('\n📼 STEP 2: Replaying Battle...');
            const replaySuccess = yield this.replayBattle();
            this.testResults.replaySucceeded = replaySuccess;
            // Step 3: Analyze results
            console.log('\n📊 STEP 3: Analysis Results...');
            this.analyzeResults();
            return this.testResults;
        });
    }
    recordBattle() {
        return __awaiter(this, void 0, void 0, function* () {
            try {
                // Create game environment
                const gameState = {
                    gameTime: 0,
                    winner: null,
                    paused: false
                };
                const resources = {
                    blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
                    red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
                };
                const units = [];
                const buildings = [];
                const terrain = [];
                const resourceNodes = [];
                // Generate terrain
                this.generateSimpleTerrain(terrain, resourceNodes);
                // Spawn commanders
                const blueStart = { x: gameConstants_js_1.WORLD_SIZE * 0.2, y: gameConstants_js_1.WORLD_SIZE * 0.5 };
                const redStart = { x: gameConstants_js_1.WORLD_SIZE * 0.8, y: gameConstants_js_1.WORLD_SIZE * 0.5 };
                units.push(new unit_js_1.Unit(blueStart.x, blueStart.y, 'blue', unitTypes_js_1.UNIT_TYPES.commander));
                units.push(new unit_js_1.Unit(redStart.x, redStart.y, 'red', unitTypes_js_1.UNIT_TYPES.commander));
                // Setup build lists
                if (unitTypes_js_1.UNIT_TYPES.commander) {
                    unitTypes_js_1.UNIT_TYPES.commander.buildList = [
                        buildingTypes_js_1.BUILDING_TYPES.massExtractor,
                        buildingTypes_js_1.BUILDING_TYPES.energyExtractor,
                        buildingTypes_js_1.BUILDING_TYPES.landFactory
                    ];
                }
                // Start recording
                battleJournal_js_1.battleJournal.startRecording({
                    duration: 120, // 2 minutes
                    gameMode: 'demo',
                    description: 'Deterministic replay demo battle'
                });
                console.log('🎲 Recording battle with deterministic RNG...');
                // Create game context
                const gameContext = this.createGameContext(units, buildings, resources, gameState, terrain, resourceNodes);
                // Simulate battle for 2 minutes
                const maxFrames = 120 * 60; // 2 minutes at 60fps
                for (let frame = 0; frame < maxFrames && !gameState.winner; frame++) {
                    gameState.gameTime += 1 / 60;
                    // Record frame data
                    battleJournal_js_1.battleJournal.recordFrame(gameContext);
                    // Simulate some player actions
                    if (frame % 600 === 0) { // Every 10 seconds
                        this.simulatePlayerActions(frame, units, buildings);
                    }
                    // Update units
                    for (let i = units.length - 1; i >= 0; i--) {
                        const unit = units[i];
                        unit.update(gameContext);
                        if (unit.hp <= 0) {
                            if (unit.type === unitTypes_js_1.UNIT_TYPES.commander) {
                                gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
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
                    // Status update every 30 seconds
                    if (frame % (30 * 60) === 0) {
                        console.log(`   Time: ${(gameState.gameTime / 60).toFixed(1)}m | Units: ${units.length} | Buildings: ${buildings.length}`);
                    }
                }
                // Stop recording
                this.recordedBattleId = battleJournal_js_1.battleJournal.stopRecording({
                    winner: gameState.winner,
                    finalTime: gameState.gameTime
                });
                console.log(`✅ Battle recorded successfully: ${this.recordedBattleId}`);
                return true;
            }
            catch (error) {
                console.error('❌ Recording failed:', error);
                return false;
            }
        });
    }
    replayBattle() {
        return __awaiter(this, void 0, void 0, function* () {
            if (!this.recordedBattleId) {
                console.error('❌ No recorded battle to replay');
                return false;
            }
            try {
                // Load battle for replay
                const loadSuccess = battleReplay_js_1.battleReplay.loadBattle(this.recordedBattleId);
                if (!loadSuccess) {
                    console.error('❌ Failed to load battle for replay');
                    return false;
                }
                // Create fresh game environment for replay
                const gameState = {
                    gameTime: 0,
                    winner: null,
                    paused: false
                };
                const resources = {
                    blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
                    red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
                };
                const units = [];
                const buildings = [];
                const terrain = [];
                const resourceNodes = [];
                const camera = { x: 0, y: 0, zoom: 1.0 };
                // Generate same terrain (deterministic)
                this.generateSimpleTerrain(terrain, resourceNodes);
                const gameContext = this.createGameContext(units, buildings, resources, gameState, terrain, resourceNodes, camera);
                // Start replay
                const replaySuccess = battleReplay_js_1.battleReplay.startReplay(gameContext);
                if (!replaySuccess) {
                    console.error('❌ Failed to start replay');
                    return false;
                }
                console.log('▶️ Starting deterministic replay...');
                // Run replay
                let frameCount = 0;
                while (battleReplay_js_1.battleReplay.isReplaying) {
                    const continueReplay = battleReplay_js_1.battleReplay.updateReplay();
                    frameCount++;
                    // Status update
                    if (frameCount % (30 * 60) === 0) {
                        const info = battleReplay_js_1.battleReplay.getReplayInfo();
                        console.log(`   Replay progress: ${(info.progress * 100).toFixed(1)}% | Time: ${info.currentTime.toFixed(1)}s`);
                        if (info.divergenceDetected) {
                            this.testResults.divergenceCount++;
                            console.warn('   ⚠️ Divergence detected!');
                        }
                    }
                    if (!continueReplay)
                        break;
                    // Safety limit
                    if (frameCount > 200000) {
                        console.warn('⚠️ Replay timeout, stopping');
                        break;
                    }
                }
                const finalInfo = battleReplay_js_1.battleReplay.getReplayInfo();
                console.log(`✅ Replay completed in ${frameCount} frames`);
                console.log(`📊 Final time: ${finalInfo.currentTime.toFixed(1)}s`);
                console.log(`🎯 Commands processed: ${finalInfo.commandsProcessed}/${finalInfo.totalCommands}`);
                if (finalInfo.divergenceDetected) {
                    console.warn(`⚠️ Divergences detected: ${this.testResults.divergenceCount}`);
                    this.testResults.deterministicVerified = false;
                }
                else {
                    console.log('✅ Perfect deterministic reproduction achieved!');
                    this.testResults.deterministicVerified = true;
                }
                return true;
            }
            catch (error) {
                console.error('❌ Replay failed:', error);
                return false;
            }
        });
    }
    simulatePlayerActions(frame, units, buildings) {
        const time = frame / 60; // Convert to seconds
        // Simulate some basic player commands
        const commanders = units.filter(u => u.type === unitTypes_js_1.UNIT_TYPES.commander);
        commanders.forEach(commander => {
            // Record simulated unit movement command
            battleJournal_js_1.battleJournal.recordInputCommand('UNIT_MOVE', {
                unitIds: [commander.id || `commander_${commander.team}`],
                targetX: commander.x + deterministicRNG_js_1.gameRNG.randomFloat(-50, 50),
                targetY: commander.y + deterministicRNG_js_1.gameRNG.randomFloat(-50, 50),
                formation: null
            }, commander.team);
            // Simulate build commands
            if (time > 30 && buildings.length < 3) {
                battleJournal_js_1.battleJournal.recordInputCommand('BUILD_STRUCTURE', {
                    builderId: commander.id || `commander_${commander.team}`,
                    structureType: buildingTypes_js_1.BUILDING_TYPES.massExtractor,
                    x: commander.x + deterministicRNG_js_1.gameRNG.randomFloat(-100, 100),
                    y: commander.y + deterministicRNG_js_1.gameRNG.randomFloat(-100, 100)
                }, commander.team);
            }
        });
    }
    generateSimpleTerrain(terrain, resourceNodes) {
        // Create simple terrain for demo
        for (let x = 0; x < gameConstants_js_1.GRID_SIZE; x++) {
            terrain[x] = [];
            for (let y = 0; y < gameConstants_js_1.GRID_SIZE; y++) {
                terrain[x][y] = gameConstants_js_1.TERRAIN_TYPES.LAND;
            }
        }
        // Add some resource nodes
        for (let i = 0; i < 10; i++) {
            resourceNodes.push({
                x: deterministicRNG_js_1.gameRNG.randomFloat(100, gameConstants_js_1.WORLD_SIZE - 100),
                y: deterministicRNG_js_1.gameRNG.randomFloat(100, gameConstants_js_1.WORLD_SIZE - 100),
                type: deterministicRNG_js_1.gameRNG.randomBool() ? 'mass' : 'energy',
                amount: 10000,
                maxAmount: 10000,
                occupied: false
            });
        }
    }
    createGameContext(units, buildings, resources, gameState, terrain, resourceNodes, camera = null) {
        return {
            units, buildings, resources, gameState, terrain, resourceNodes, camera,
            UNIT_TYPES: unitTypes_js_1.UNIT_TYPES, BUILDING_TYPES: buildingTypes_js_1.BUILDING_TYPES, WORLD_SIZE: gameConstants_js_1.WORLD_SIZE, TILE_SIZE: gameConstants_js_1.TILE_SIZE, GRID_SIZE: gameConstants_js_1.GRID_SIZE, TERRAIN_TYPES: gameConstants_js_1.TERRAIN_TYPES,
            Unit: unit_js_1.Unit, Building: building_js_1.Building,
            addEvent: (ctx, type, msg, importance, pos) => {
                if (importance >= 2) {
                    battleJournal_js_1.battleJournal.recordEvent(type.toUpperCase(), msg, pos);
                }
            },
            mainGameGlobals: {
                resources, units, Unit: unit_js_1.Unit, Building: building_js_1.Building,
                addEvent: (type, msg) => battleJournal_js_1.battleJournal.recordEvent(type.toUpperCase(), msg)
            }
        };
    }
    analyzeResults() {
        console.log('\n📋 DETERMINISTIC REPLAY DEMO RESULTS:');
        console.log(`   Recording: ${this.testResults.recordingSucceeded ? '✅ SUCCESS' : '❌ FAILED'}`);
        console.log(`   Replay: ${this.testResults.replaySucceeded ? '✅ SUCCESS' : '❌ FAILED'}`);
        console.log(`   Deterministic: ${this.testResults.deterministicVerified ? '✅ PERFECT' : '⚠️ DIVERGED'}`);
        console.log(`   Divergences: ${this.testResults.divergenceCount}`);
        if (this.testResults.recordingSucceeded && this.testResults.replaySucceeded && this.testResults.deterministicVerified) {
            console.log('\n🎉 DEMO SUCCESS: Deterministic battle replay system working perfectly!');
            console.log('🔄 Battles can now be recorded and replayed with perfect accuracy');
            console.log('🎲 Random number generation is fully deterministic and reproducible');
            console.log('⚡ Timeline-based action replay provides frame-perfect synchronization');
        }
        else {
            console.log('\n⚠️ DEMO ISSUES DETECTED - Check logs above for details');
        }
        // Export analysis if recording was successful
        if (this.recordedBattleId) {
            const analysis = battleReplay_js_1.battleReplay.exportReplayAnalysis();
            if (analysis) {
                console.log('\n📊 REPLAY ANALYSIS:');
                console.log(`   Commands recorded: ${analysis.commandCount}`);
                console.log(`   Command types: ${Object.keys(analysis.commandTypes).join(', ')}`);
                console.log(`   Deterministic seed: ${analysis.seed}`);
            }
        }
    }
    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
}
exports.ReplayDemo = ReplayDemo;
// CLI interface
if (import.meta.url === `file://${process.argv[1]}`) {
    const demo = new ReplayDemo();
    demo.runDemo()
        .then(results => {
        if (results.recordingSucceeded && results.replaySucceeded && results.deterministicVerified) {
            process.exit(0);
        }
        else {
            process.exit(1);
        }
    })
        .catch(error => {
        console.error('❌ Demo failed:', error);
        process.exit(1);
    });
}
