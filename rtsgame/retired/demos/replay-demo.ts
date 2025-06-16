#!/usr/bin/env node

// Deterministic Replay Demo - Test the complete record and replay system
// Shows how battles can be recorded with deterministic RNG and replayed exactly

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';
import { generateTerrain, findLandPosition } from '../core/terrain.js';
import { battleJournal } from '../core/battleJournal.js';
import { battleReplay } from '../core/battleReplay.js';
import { gameRNG, enableDeterministicMode, disableDeterministicMode } from '../core/deterministicRNG.js';

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

    async runDemo() {
        console.log('🎬 DETERMINISTIC REPLAY SYSTEM DEMO\n');
        console.log('This demo will:');
        console.log('1. Record a battle with deterministic RNG');
        console.log('2. Replay the exact same battle');
        console.log('3. Verify deterministic reproduction\n');

        // Step 1: Record a battle
        console.log('📹 STEP 1: Recording Battle...');
        const recordSuccess = await this.recordBattle();
        this.testResults.recordingSucceeded = recordSuccess;

        if (!recordSuccess) {
            console.error('❌ Failed to record battle');
            return this.testResults;
        }

        // Step 2: Replay the battle
        console.log('\n📼 STEP 2: Replaying Battle...');
        const replaySuccess = await this.replayBattle();
        this.testResults.replaySucceeded = replaySuccess;

        // Step 3: Analyze results
        console.log('\n📊 STEP 3: Analysis Results...');
        this.analyzeResults();

        return this.testResults;
    }

    async recordBattle() {
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
            const blueStart = { x: WORLD_SIZE * 0.2, y: WORLD_SIZE * 0.5 };
            const redStart = { x: WORLD_SIZE * 0.8, y: WORLD_SIZE * 0.5 };

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

            // Start recording
            battleJournal.startRecording({
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
                gameState.gameTime += 1/60;

                // Record frame data
                battleJournal.recordFrame(gameContext);

                // Simulate some player actions
                if (frame % 600 === 0) { // Every 10 seconds
                    this.simulatePlayerActions(frame, units, buildings);
                }

                // Update units
                for (let i = units.length - 1; i >= 0; i--) {
                    const unit = units[i];
                    unit.update(gameContext);

                    if (unit.hp <= 0) {
                        if (unit.type === UNIT_TYPES.commander) {
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
            this.recordedBattleId = battleJournal.stopRecording({
                winner: gameState.winner,
                finalTime: gameState.gameTime
            });

            console.log(`✅ Battle recorded successfully: ${this.recordedBattleId}`);
            return true;

        } catch (error) {
            console.error('❌ Recording failed:', error);
            return false;
        }
    }

    async replayBattle() {
        if (!this.recordedBattleId) {
            console.error('❌ No recorded battle to replay');
            return false;
        }

        try {
            // Load battle for replay
            const loadSuccess = battleReplay.loadBattle(this.recordedBattleId);
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
            const replaySuccess = battleReplay.startReplay(gameContext);
            if (!replaySuccess) {
                console.error('❌ Failed to start replay');
                return false;
            }

            console.log('▶️ Starting deterministic replay...');

            // Run replay
            let frameCount = 0;
            while (battleReplay.isReplaying) {
                const continueReplay = battleReplay.updateReplay();
                frameCount++;

                // Status update
                if (frameCount % (30 * 60) === 0) {
                    const info = battleReplay.getReplayInfo();
                    console.log(`   Replay progress: ${(info.progress * 100).toFixed(1)}% | Time: ${info.currentTime.toFixed(1)}s`);
                    
                    if (info.divergenceDetected) {
                        this.testResults.divergenceCount++;
                        console.warn('   ⚠️ Divergence detected!');
                    }
                }

                if (!continueReplay) break;

                // Safety limit
                if (frameCount > 200000) {
                    console.warn('⚠️ Replay timeout, stopping');
                    break;
                }
            }

            const finalInfo = battleReplay.getReplayInfo();
            console.log(`✅ Replay completed in ${frameCount} frames`);
            console.log(`📊 Final time: ${finalInfo.currentTime.toFixed(1)}s`);
            console.log(`🎯 Commands processed: ${finalInfo.commandsProcessed}/${finalInfo.totalCommands}`);

            if (finalInfo.divergenceDetected) {
                console.warn(`⚠️ Divergences detected: ${this.testResults.divergenceCount}`);
                this.testResults.deterministicVerified = false;
            } else {
                console.log('✅ Perfect deterministic reproduction achieved!');
                this.testResults.deterministicVerified = true;
            }

            return true;

        } catch (error) {
            console.error('❌ Replay failed:', error);
            return false;
        }
    }

    simulatePlayerActions(frame, units, buildings) {
        const time = frame / 60; // Convert to seconds

        // Simulate some basic player commands
        const commanders = units.filter(u => u.type === UNIT_TYPES.commander);
        
        commanders.forEach(commander => {
            // Record simulated unit movement command
            battleJournal.recordInputCommand('UNIT_MOVE', {
                unitIds: [commander.id || `commander_${commander.team}`],
                targetX: commander.x + gameRNG.randomFloat(-50, 50),
                targetY: commander.y + gameRNG.randomFloat(-50, 50),
                formation: null
            }, commander.team);

            // Simulate build commands
            if (time > 30 && buildings.length < 3) {
                battleJournal.recordInputCommand('BUILD_STRUCTURE', {
                    builderId: commander.id || `commander_${commander.team}`,
                    structureType: BUILDING_TYPES.massExtractor,
                    x: commander.x + gameRNG.randomFloat(-100, 100),
                    y: commander.y + gameRNG.randomFloat(-100, 100)
                }, commander.team);
            }
        });
    }

    generateSimpleTerrain(terrain, resourceNodes) {
        // Create simple terrain for demo
        for (let x = 0; x < GRID_SIZE; x++) {
            terrain[x] = [];
            for (let y = 0; y < GRID_SIZE; y++) {
                terrain[x][y] = TERRAIN_TYPES.LAND;
            }
        }

        // Add some resource nodes
        for (let i = 0; i < 10; i++) {
            resourceNodes.push({
                x: gameRNG.randomFloat(100, WORLD_SIZE - 100),
                y: gameRNG.randomFloat(100, WORLD_SIZE - 100),
                type: gameRNG.randomBool() ? 'mass' : 'energy',
                amount: 10000,
                maxAmount: 10000,
                occupied: false
            });
        }
    }

    createGameContext(units, buildings, resources, gameState, terrain, resourceNodes, camera = null) {
        return {
            units, buildings, resources, gameState, terrain, resourceNodes, camera,
            UNIT_TYPES, BUILDING_TYPES, WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES,
            Unit, Building,
            addEvent: (ctx, type, msg, importance, pos) => {
                if (importance >= 2) {
                    battleJournal.recordEvent(type.toUpperCase(), msg, pos);
                }
            },
            mainGameGlobals: {
                resources, units, Unit, Building,
                addEvent: (type, msg) => battleJournal.recordEvent(type.toUpperCase(), msg)
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
        } else {
            console.log('\n⚠️ DEMO ISSUES DETECTED - Check logs above for details');
        }

        // Export analysis if recording was successful
        if (this.recordedBattleId) {
            const analysis = battleReplay.exportReplayAnalysis();
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

// CLI interface
if (import.meta.url === `file://${process.argv[1]}`) {
    const demo = new ReplayDemo();
    demo.runDemo()
        .then(results => {
            if (results.recordingSucceeded && results.replaySucceeded && results.deterministicVerified) {
                process.exit(0);
            } else {
                process.exit(1);
            }
        })
        .catch(error => {
            console.error('❌ Demo failed:', error);
            process.exit(1);
        });
}

export { ReplayDemo };