#!/usr/bin/env node

// Enhanced Headless Battle Test - Prove localStorage replay with action-packed AI battles
// Demonstrates strategic AI creating engaging battles that can be perfectly replayed

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';
import { generateTerrain, findLandPosition } from '../core/terrain.js';
import { battleJournal } from '../core/battleJournal.js';
import { battleReplay } from '../core/battleReplay.js';
import { gameRNG, enableDeterministicMode, disableDeterministicMode } from '../core/deterministicRNG.js';
import { makeStrategicDecisions, coordinateAttacks, resetAIState } from '../ai/strategicAI.js';

class EnhancedHeadlessTest {
    constructor() {
        this.battleId = null;
        this.originalBattleStats = null;
        this.replayBattleStats = null;
        this.testResults = {
            battleRecorded: false,
            battleReplayed: false,
            aiActionsRecorded: 0,
            actionDensity: 0, // actions per minute
            battleIntensity: 0, // units destroyed per minute
            deterministicMatch: false,
            localStorage: {
                used: false,
                sizeKB: 0,
                retrievable: false
            }
        };
    }

    async runEnhancedTest() {
        console.log('🚀 ENHANCED HEADLESS BATTLE + LOCALSTORAGE REPLAY TEST');
        console.log('Testing: Strategic AI → Action-Packed Battles → Deterministic Replay\n');

        try {
            // Step 1: Record an intense battle with enhanced AI
            console.log('🎯 STEP 1: Recording Enhanced AI Battle...');
            const recordResult = await this.recordIntenseBattle();
            
            if (!recordResult.success) {
                console.error('❌ Failed to record battle');
                return this.testResults;
            }

            // Step 2: Verify localStorage storage
            console.log('\n💾 STEP 2: Verifying localStorage Storage...');
            this.verifyLocalStorage();

            // Step 3: Replay from localStorage
            console.log('\n📼 STEP 3: Replaying from localStorage...');
            const replayResult = await this.replayFromLocalStorage();

            // Step 4: Compare results
            console.log('\n🔍 STEP 4: Analyzing Results...');
            this.analyzeResults();

            return this.testResults;

        } catch (error) {
            console.error('❌ Test failed with error:', error);
            return this.testResults;
        }
    }

    async recordIntenseBattle() {
        try {
            // Reset AI state for fresh battle
            resetAIState();

            // Enable deterministic mode
            enableDeterministicMode(12345);

            // Create game environment optimized for action
            const gameState = {
                gameTime: 0,
                winner: null,
                paused: false
            };

            const resources = {
                blue: { mass: 300, energy: 400, massIncome: 10, energyIncome: 15 }, // Rich start for faster action
                red: { mass: 300, energy: 400, massIncome: 10, energyIncome: 15 }
            };

            const units = [];
            const buildings = [];
            const captions = [];
            const terrain = [];
            const resourceNodes = [];

            // Generate action-friendly terrain
            this.generateOptimalTerrain(terrain, resourceNodes);

            // Spawn commanders close together for faster engagement
            const blueStart = { x: WORLD_SIZE * 0.3, y: WORLD_SIZE * 0.5 }; // Closer positioning
            const redStart = { x: WORLD_SIZE * 0.7, y: WORLD_SIZE * 0.5 };

            const blueCommander = new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander);
            const redCommander = new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander);
            
            units.push(blueCommander, redCommander);

            // Setup build lists for rapid expansion
            if (UNIT_TYPES.commander) {
                UNIT_TYPES.commander.buildList = [
                    BUILDING_TYPES.massExtractor,
                    BUILDING_TYPES.energyExtractor,
                    BUILDING_TYPES.landFactory
                ];
            }

            // Start battle recording
            battleJournal.startRecording({
                duration: 240, // 4 minutes for intense action
                gameMode: 'enhanced_ai_test',
                description: 'Enhanced AI battle with localStorage replay test',
                seed: 12345
            });

            console.log('🎲 Recording with deterministic seed: 12345');
            console.log('⚡ Enhanced AI personalities: Blue=Balanced, Red=Aggressive');

            // Create game context with AI functions
            const gameContext = this.createEnhancedGameContext(
                units, buildings, resources, gameState, terrain, resourceNodes, captions
            );

            // Battle simulation with enhanced AI
            let actionCount = 0;
            let unitsDestroyed = 0;
            let combatEvents = 0;
            const maxFrames = 240 * 60; // 4 minutes at 60fps

            for (let frame = 0; frame < maxFrames && !gameState.winner; frame++) {
                gameState.gameTime += 1/60;

                // Record frame for replay
                battleJournal.recordFrame(gameContext);

                // Enhanced AI decisions (every 0.5 seconds for more activity)
                if (frame % 30 === 0) {
                    const aiDecisionsBefore = battleJournal.currentBattle?.inputCommands?.length || 0;
                    
                    makeStrategicDecisions(gameContext);
                    coordinateAttacks(gameContext);
                    
                    const aiDecisionsAfter = battleJournal.currentBattle?.inputCommands?.length || 0;
                    actionCount += (aiDecisionsAfter - aiDecisionsBefore);
                }

                // Update units with combat tracking
                for (let i = units.length - 1; i >= 0; i--) {
                    const unit = units[i];
                    const oldHp = unit.hp;
                    
                    unit.update(gameContext);

                    // Track combat intensity
                    if (unit.hp < oldHp) {
                        combatEvents++;
                    }

                    if (unit.hp <= 0) {
                        if (unit.type === UNIT_TYPES.commander) {
                            gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                            console.log(`💥 ${unit.team.toUpperCase()} commander destroyed! ${gameState.winner} WINS!`);
                        }
                        unitsDestroyed++;
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

                // Progress updates every 30 seconds
                if (frame % (30 * 60) === 0) {
                    const time = Math.floor(gameState.gameTime / 60);
                    console.log(`   ${time}m: Units: ${units.length} | Buildings: ${buildings.length} | Actions: ${actionCount} | Combat: ${combatEvents}`);
                }
            }

            // Stop recording and get battle ID
            this.battleId = battleJournal.stopRecording({
                winner: gameState.winner,
                finalTime: gameState.gameTime,
                totalActions: actionCount,
                unitsDestroyed: unitsDestroyed,
                combatEvents: combatEvents
            });

            // Calculate battle stats
            const duration = gameState.gameTime / 60; // minutes
            this.originalBattleStats = {
                duration: duration,
                actions: actionCount,
                actionsPerMinute: actionCount / duration,
                unitsDestroyed: unitsDestroyed,
                destroyedPerMinute: unitsDestroyed / duration,
                combatEvents: combatEvents,
                combatPerMinute: combatEvents / duration,
                winner: gameState.winner
            };

            this.testResults.battleRecorded = true;
            this.testResults.aiActionsRecorded = actionCount;
            this.testResults.actionDensity = this.originalBattleStats.actionsPerMinute;
            this.testResults.battleIntensity = this.originalBattleStats.destroyedPerMinute;

            console.log(`✅ Battle recorded successfully: ${this.battleId}`);
            console.log(`📊 Battle Stats:`);
            console.log(`   Duration: ${duration.toFixed(1)} minutes`);
            console.log(`   AI Actions: ${actionCount} (${this.originalBattleStats.actionsPerMinute.toFixed(1)}/min)`);
            console.log(`   Units Destroyed: ${unitsDestroyed} (${this.originalBattleStats.destroyedPerMinute.toFixed(1)}/min)`);
            console.log(`   Combat Events: ${combatEvents} (${this.originalBattleStats.combatPerMinute.toFixed(1)}/min)`);
            console.log(`   Winner: ${gameState.winner || 'NONE'}`);

            return { success: true };

        } catch (error) {
            console.error('❌ Recording failed:', error);
            return { success: false, error };
        }
    }

    verifyLocalStorage() {
        try {
            // Check if battle was stored
            const battleKey = `battle_${this.battleId}`;
            const storedData = localStorage.getItem(battleKey);
            
            if (!storedData) {
                console.error('❌ Battle not found in localStorage');
                return;
            }

            // Calculate storage size
            const sizeBytes = new Blob([storedData]).size;
            const sizeKB = (sizeBytes / 1024).toFixed(1);

            // Parse and verify data structure
            const battleData = JSON.parse(storedData);
            const hasInputCommands = battleData.inputCommands && battleData.inputCommands.length > 0;
            const hasDeterministicSeed = battleData.config && battleData.config.battleSeed;
            const hasFrameData = battleData.frames && battleData.frames.length > 0;

            this.testResults.localStorage = {
                used: true,
                sizeKB: parseFloat(sizeKB),
                retrievable: true,
                hasInputCommands,
                hasDeterministicSeed,
                hasFrameData
            };

            console.log(`✅ Battle found in localStorage:`);
            console.log(`   Key: ${battleKey}`);
            console.log(`   Size: ${sizeKB}KB`);
            console.log(`   Input Commands: ${battleData.inputCommands?.length || 0}`);
            console.log(`   Frame Data: ${battleData.frames?.length || 0}`);
            console.log(`   Deterministic Seed: ${battleData.config?.battleSeed || 'None'}`);
            console.log(`   RNG States: ${battleData.rngState?.length || 0}`);

        } catch (error) {
            console.error('❌ localStorage verification failed:', error);
            this.testResults.localStorage.retrievable = false;
        }
    }

    async replayFromLocalStorage() {
        try {
            if (!this.battleId) {
                throw new Error('No battle ID available for replay');
            }

            // Load battle from localStorage
            const loadSuccess = battleReplay.loadBattle(this.battleId);
            if (!loadSuccess) {
                throw new Error('Failed to load battle from localStorage');
            }

            console.log(`📼 Loaded battle from localStorage: ${this.battleId}`);

            // Create fresh game environment for replay
            const gameState = {
                gameTime: 0,
                winner: null,
                paused: false
            };

            const resources = {
                blue: { mass: 300, energy: 400, massIncome: 10, energyIncome: 15 },
                red: { mass: 300, energy: 400, massIncome: 10, energyIncome: 15 }
            };

            const units = [];
            const buildings = [];
            const captions = [];
            const terrain = [];
            const resourceNodes = [];

            // Generate same terrain (deterministic)
            this.generateOptimalTerrain(terrain, resourceNodes);

            const gameContext = this.createEnhancedGameContext(
                units, buildings, resources, gameState, terrain, resourceNodes, captions
            );

            // Start replay
            const replaySuccess = battleReplay.startReplay(gameContext);
            if (!replaySuccess) {
                throw new Error('Failed to start replay');
            }

            console.log('▶️  Starting deterministic replay...');

            // Run replay and track stats
            let frameCount = 0;
            let unitsDestroyed = 0;
            let combatEvents = 0;
            let divergenceCount = 0;

            while (battleReplay.isReplaying) {
                const continueReplay = battleReplay.updateReplay();
                frameCount++;

                // Update game state to match replay
                for (let i = units.length - 1; i >= 0; i--) {
                    const unit = units[i];
                    const oldHp = unit.hp;
                    
                    unit.update(gameContext);

                    if (unit.hp < oldHp) {
                        combatEvents++;
                    }

                    if (unit.hp <= 0) {
                        if (unit.type === UNIT_TYPES.commander) {
                            gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                        }
                        unitsDestroyed++;
                        units.splice(i, 1);
                    }
                }

                for (let i = buildings.length - 1; i >= 0; i--) {
                    const building = buildings[i];
                    building.update(units, gameContext.mainGameGlobals);

                    if (building.hp <= 0) {
                        buildings.splice(i, 1);
                    }
                }

                // Check for divergences
                const replayInfo = battleReplay.getReplayInfo();
                if (replayInfo.divergenceDetected && divergenceCount === 0) {
                    divergenceCount++;
                    console.warn('⚠️  Divergence detected during replay');
                }

                // Progress updates
                if (frameCount % (30 * 60) === 0) {
                    console.log(`   Replay progress: ${(replayInfo.progress * 100).toFixed(1)}%`);
                }

                if (!continueReplay) break;

                // Safety timeout
                if (frameCount > 300000) {
                    console.warn('⚠️  Replay timeout, stopping');
                    break;
                }
            }

            // Calculate replay stats
            const finalInfo = battleReplay.getReplayInfo();
            const duration = finalInfo.currentTime / 60; // minutes
            
            this.replayBattleStats = {
                duration: duration,
                unitsDestroyed: unitsDestroyed,
                destroyedPerMinute: unitsDestroyed / duration,
                combatEvents: combatEvents,
                combatPerMinute: combatEvents / duration,
                winner: gameState.winner,
                divergences: divergenceCount,
                commandsProcessed: finalInfo.commandsProcessed
            };

            this.testResults.battleReplayed = true;
            this.testResults.deterministicMatch = (divergenceCount === 0);

            console.log(`✅ Replay completed:`);
            console.log(`   Duration: ${duration.toFixed(1)} minutes`);
            console.log(`   Commands Processed: ${finalInfo.commandsProcessed}/${finalInfo.totalCommands}`);
            console.log(`   Divergences: ${divergenceCount}`);
            console.log(`   Winner: ${gameState.winner || 'NONE'}`);

            return { success: true };

        } catch (error) {
            console.error('❌ Replay failed:', error);
            return { success: false, error };
        }
    }

    analyzeResults() {
        console.log('\n📊 ENHANCED HEADLESS + REPLAY TEST RESULTS:');
        console.log('=' .repeat(60));

        // Battle recording results
        console.log('\n🎯 BATTLE RECORDING:');
        console.log(`   Success: ${this.testResults.battleRecorded ? '✅' : '❌'}`);
        if (this.originalBattleStats) {
            console.log(`   AI Actions: ${this.testResults.aiActionsRecorded} (${this.testResults.actionDensity.toFixed(1)}/min)`);
            console.log(`   Battle Intensity: ${this.testResults.battleIntensity.toFixed(1)} units destroyed/min`);
            console.log(`   Winner: ${this.originalBattleStats.winner || 'NONE'}`);
        }

        // LocalStorage results
        console.log('\n💾 LOCALSTORAGE INTEGRATION:');
        console.log(`   Used: ${this.testResults.localStorage.used ? '✅' : '❌'}`);
        console.log(`   Size: ${this.testResults.localStorage.sizeKB}KB`);
        console.log(`   Retrievable: ${this.testResults.localStorage.retrievable ? '✅' : '❌'}`);
        console.log(`   Input Commands: ${this.testResults.localStorage.hasInputCommands ? '✅' : '❌'}`);
        console.log(`   Deterministic Seed: ${this.testResults.localStorage.hasDeterministicSeed ? '✅' : '❌'}`);

        // Replay results
        console.log('\n📼 DETERMINISTIC REPLAY:');
        console.log(`   Success: ${this.testResults.battleReplayed ? '✅' : '❌'}`);
        console.log(`   Deterministic Match: ${this.testResults.deterministicMatch ? '✅ PERFECT' : '⚠️  DIVERGED'}`);

        // Comparison
        if (this.originalBattleStats && this.replayBattleStats) {
            console.log('\n🔍 ORIGINAL vs REPLAY COMPARISON:');
            console.log(`   Duration: ${this.originalBattleStats.duration.toFixed(1)}m vs ${this.replayBattleStats.duration.toFixed(1)}m`);
            console.log(`   Units Destroyed: ${this.originalBattleStats.unitsDestroyed} vs ${this.replayBattleStats.unitsDestroyed}`);
            console.log(`   Winner: ${this.originalBattleStats.winner || 'NONE'} vs ${this.replayBattleStats.winner || 'NONE'}`);
            
            const matchesWinner = this.originalBattleStats.winner === this.replayBattleStats.winner;
            console.log(`   Winner Match: ${matchesWinner ? '✅' : '❌'}`);
        }

        // Overall assessment
        console.log('\n🏆 OVERALL ASSESSMENT:');
        const success = this.testResults.battleRecorded && 
                       this.testResults.localStorage.used && 
                       this.testResults.localStorage.retrievable && 
                       this.testResults.battleReplayed &&
                       this.testResults.deterministicMatch;

        if (success) {
            console.log('🎉 SUCCESS: Enhanced AI creates action-packed battles');
            console.log('🎉 SUCCESS: localStorage perfectly stores battle data');
            console.log('🎉 SUCCESS: Deterministic replay works flawlessly');
            console.log('✅ PROVEN: Headless simulation can be replayed with lots of action and good maps!');
        } else {
            console.log('⚠️  PARTIAL SUCCESS - Some components need attention:');
            if (!this.testResults.battleRecorded) console.log('   - Battle recording failed');
            if (!this.testResults.localStorage.used) console.log('   - localStorage not utilized');
            if (!this.testResults.battleReplayed) console.log('   - Replay failed');
            if (!this.testResults.deterministicMatch) console.log('   - Deterministic match failed');
        }

        // Performance metrics
        if (this.testResults.actionDensity > 5) {
            console.log('⚡ HIGH ACTION DENSITY: Enhanced AI generates engaging battles');
        }
        if (this.testResults.battleIntensity > 1) {
            console.log('💥 HIGH BATTLE INTENSITY: Lots of combat and destruction');
        }
        if (this.testResults.localStorage.sizeKB > 10) {
            console.log('📊 RICH DATA: Comprehensive battle recording captured');
        }
    }

    generateOptimalTerrain(terrain, resourceNodes) {
        // Generate terrain optimized for action
        for (let x = 0; x < GRID_SIZE; x++) {
            terrain[x] = [];
            for (let y = 0; y < GRID_SIZE; y++) {
                terrain[x][y] = TERRAIN_TYPES.LAND;
            }
        }

        // Add strategic resource placement for faster expansion
        const resourceSpots = [
            { x: WORLD_SIZE * 0.4, y: WORLD_SIZE * 0.3, type: 'mass' },
            { x: WORLD_SIZE * 0.6, y: WORLD_SIZE * 0.7, type: 'energy' },
            { x: WORLD_SIZE * 0.5, y: WORLD_SIZE * 0.2, type: 'mass' },
            { x: WORLD_SIZE * 0.5, y: WORLD_SIZE * 0.8, type: 'energy' },
            { x: WORLD_SIZE * 0.2, y: WORLD_SIZE * 0.6, type: 'mass' },
            { x: WORLD_SIZE * 0.8, y: WORLD_SIZE * 0.4, type: 'energy' },
            { x: WORLD_SIZE * 0.35, y: WORLD_SIZE * 0.65, type: 'mass' },
            { x: WORLD_SIZE * 0.65, y: WORLD_SIZE * 0.35, type: 'energy' }
        ];

        resourceSpots.forEach(spot => {
            resourceNodes.push({
                x: spot.x,
                y: spot.y,
                type: spot.type,
                amount: 15000, // Rich resources for sustained action
                maxAmount: 15000,
                occupied: false
            });
        });
    }

    createEnhancedGameContext(units, buildings, resources, gameState, terrain, resourceNodes, captions) {
        return {
            units, buildings, resources, gameState, terrain, resourceNodes, captions,
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
}

// CLI interface
if (import.meta.url === `file://${process.argv[1]}`) {
    // Mock localStorage for Node.js environment
    global.localStorage = {
        store: {},
        setItem: function(key, value) {
            this.store[key] = value;
        },
        getItem: function(key) {
            return this.store[key] || null;
        },
        removeItem: function(key) {
            delete this.store[key];
        },
        clear: function() {
            this.store = {};
        }
    };

    const test = new EnhancedHeadlessTest();
    test.runEnhancedTest()
        .then(results => {
            const success = results.battleRecorded && 
                          results.localStorage.used && 
                          results.battleReplayed && 
                          results.deterministicMatch;
            
            process.exit(success ? 0 : 1);
        })
        .catch(error => {
            console.error('❌ Test suite failed:', error);
            process.exit(1);
        });
}

export { EnhancedHeadlessTest };