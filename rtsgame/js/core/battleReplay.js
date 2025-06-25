// Battle Replay System - Deterministic replay from recorded actions
// Synchronous timeline replay using recorded input commands and deterministic RNG

import { gameRNG, enableDeterministicMode, disableDeterministicMode } from './deterministicRNG.js';
import { battleJournal } from './battleJournal.js';

export class BattleReplay {
    constructor() {
        this.battleData = null;
        this.isReplaying = false;
        this.replayFrame = 0;
        this.replayTime = 0;
        this.commandQueue = [];
        this.nextCommandIndex = 0;
        this.replaySpeed = 1.0; // 1.0 = normal speed
        this.gameContext = null;
        this.divergenceDetected = false;
        this.stateValidation = {
            enabled: true,
            checkInterval: 300, // frames
            tolerances: {
                position: 5,
                health: 1,
                resources: 10
            }
        };
    }

    // Load battle data for replay
    loadBattle(battleId) {
        const battle = battleJournal.loadBattle(battleId);
        if (!battle) {
            console.error(`❌ Failed to load battle: ${battleId}`);
            return false;
        }

        if (!battle.config.deterministic) {
            console.warn('⚠️ Battle was not recorded with deterministic mode');
            return false;
        }

        this.battleData = battle;
        this.commandQueue = [...battle.inputCommands].sort((a, b) => a.frame - b.frame);
        this.nextCommandIndex = 0;
        this.replayFrame = 0;
        this.replayTime = 0;
        this.divergenceDetected = false;

        console.log(`📼 Battle loaded for replay: ${battleId}`);
        console.log(`📊 Commands to replay: ${this.commandQueue.length}`);
        console.log(`🎲 Original seed: ${battle.config.battleSeed}`);

        return true;
    }

    // Start replay with given game context
    startReplay(gameContext) {
        if (!this.battleData) {
            console.error('❌ No battle data loaded');
            return false;
        }

        this.gameContext = gameContext;
        this.isReplaying = true;

        // Initialize deterministic mode with original seed
        enableDeterministicMode(this.battleData.config.battleSeed);

        // Reset game state to match original battle start
        this.resetGameState();

        console.log(`▶️ Replay started - ${this.battleData.id}`);
        console.log(`🎮 Replay speed: ${this.replaySpeed}x`);

        return true;
    }

    // Reset game state to match original battle
    resetGameState() {
        if (!this.gameContext) return;

        const { units, buildings, resources, gameState } = this.gameContext;

        // Clear existing state
        units.length = 0;
        buildings.length = 0;

        // Reset game time
        gameState.gameTime = 0;
        gameState.winner = null;
        gameState.paused = false;

        // Reset resources to original values
        Object.assign(resources.blue, this.battleData.config.startingResources);
        Object.assign(resources.red, this.battleData.config.startingResources);
        resources.blue.massIncome = 0;
        resources.blue.energyIncome = 0;
        resources.red.massIncome = 0;
        resources.red.energyIncome = 0;

        console.log('🔄 Game state reset to battle start conditions');
    }

    // Update replay by one frame
    updateReplay() {
        if (!this.isReplaying || !this.gameContext) return false;

        this.replayFrame++;
        this.replayTime += (1/60) * this.replaySpeed;

        // Process commands for this frame
        this.processCommands();

        // Validate state periodically
        if (this.stateValidation.enabled && 
            this.replayFrame % this.stateValidation.checkInterval === 0) {
            this.validateState();
        }

        // Check if replay is complete
        if (this.nextCommandIndex >= this.commandQueue.length) {
            this.completeReplay();
            return false;
        }

        return true;
    }

    // Process commands scheduled for current frame
    processCommands() {
        while (this.nextCommandIndex < this.commandQueue.length) {
            const command = this.commandQueue[this.nextCommandIndex];
            
            // Check if this command should execute now
            if (command.frame > this.replayFrame) {
                break; // Wait for later frame
            }

            this.executeCommand(command);
            this.nextCommandIndex++;
        }
    }

    // Execute a specific command
    executeCommand(command) {
        const { commandType, commandData, playerId } = command;

        console.log(`🎬 Frame ${this.replayFrame}: ${commandType} by ${playerId}`);

        try {
            // Restore RNG state for this command if available
            if (command.rngCallCount !== undefined) {
                // Ensure RNG is in correct state for deterministic execution
                while (gameRNG.callCount < command.rngCallCount) {
                    gameRNG.random(); // Advance to correct state
                }
            }

            switch (commandType) {
                case 'UNIT_MOVE':
                    this.replayUnitMove(commandData);
                    break;
                case 'UNIT_ATTACK':
                    this.replayUnitAttack(commandData);
                    break;
                case 'BUILD_STRUCTURE':
                    this.replayBuildStructure(commandData);
                    break;
                case 'SPAWN_UNIT':
                    this.replaySpawnUnit(commandData);
                    break;
                case 'SELECT_UNITS':
                    this.replaySelectUnits(commandData);
                    break;
                case 'CAMERA_MOVE':
                    this.replayCameraMove(commandData);
                    break;
                default:
                    console.warn(`⚠️ Unknown command type: ${commandType}`);
            }

        } catch (error) {
            console.error(`❌ Error executing command ${commandType}:`, error);
            this.divergenceDetected = true;
        }
    }

    // Replay unit movement command
    replayUnitMove(data) {
        const { unitIds, targetX, targetY, formation } = data;
        const units = this.gameContext.units.filter(u => unitIds.includes(u.id));
        
        units.forEach(unit => {
            if (formation) {
                // Apply formation movement logic
                const offset = this.calculateFormationOffset(unit, units, formation);
                unit.setTarget(targetX + offset.x, targetY + offset.y);
            } else {
                unit.setTarget(targetX, targetY);
            }
        });
    }

    // Replay unit attack command
    replayUnitAttack(data) {
        const { attackerIds, targetId, targetPosition } = data;
        const attackers = this.gameContext.units.filter(u => attackerIds.includes(u.id));
        
        if (targetId) {
            const target = [...this.gameContext.units, ...this.gameContext.buildings]
                            .find(t => t.id === targetId);
            if (target) {
                attackers.forEach(attacker => attacker.setTarget(target));
            }
        } else if (targetPosition) {
            attackers.forEach(attacker => attacker.setTarget(targetPosition.x, targetPosition.y));
        }
    }

    // Replay build structure command
    replayBuildStructure(data) {
        const { builderId, structureType, x, y } = data;
        const builder = this.gameContext.units.find(u => u.id === builderId);
        
        if (builder && builder.type.buildList?.includes(structureType)) {
            // Execute build command through game system
            builder.startConstruction(structureType, x, y);
        }
    }

    // Replay spawn unit command
    replaySpawnUnit(data) {
        const { factoryId, unitType, rallyPoint } = data;
        const factory = this.gameContext.buildings.find(b => b.id === factoryId);
        
        if (factory && factory.type.produces?.includes(unitType)) {
            factory.queueUnit(unitType);
            if (rallyPoint) {
                factory.setRallyPoint(rallyPoint.x, rallyPoint.y);
            }
        }
    }

    // Replay unit selection command
    replaySelectUnits(data) {
        const { unitIds, addToSelection } = data;
        
        if (!addToSelection) {
            // Clear current selection (visual only for replay)
            this.gameContext.selectedUnits = [];
        }
        
        const selectedUnits = this.gameContext.units.filter(u => unitIds.includes(u.id));
        this.gameContext.selectedUnits.push(...selectedUnits);
    }

    // Replay camera movement command
    replayCameraMove(data) {
        const { x, y, zoom } = data;
        
        if (this.gameContext.camera) {
            this.gameContext.camera.x = x;
            this.gameContext.camera.y = y;
            if (zoom !== undefined) {
                this.gameContext.camera.zoom = zoom;
            }
        }
    }

    // Calculate formation offset for unit
    calculateFormationOffset(unit, allUnits, formation) {
        const unitIndex = allUnits.indexOf(unit);
        const spacing = formation.spacing || 32;
        
        switch (formation.type) {
            case 'line':
                return { x: unitIndex * spacing, y: 0 };
            case 'column':
                return { x: 0, y: unitIndex * spacing };
            case 'box':
                const sideLength = Math.ceil(Math.sqrt(allUnits.length));
                return {
                    x: (unitIndex % sideLength) * spacing,
                    y: Math.floor(unitIndex / sideLength) * spacing
                };
            default:
                return { x: 0, y: 0 };
        }
    }

    // Validate current state against recorded snapshots
    validateState() {
        const currentSnapshot = this.captureCurrentState();
        const originalSnapshot = this.findOriginalSnapshot(this.replayTime);
        
        if (!originalSnapshot) return;

        const divergences = this.compareStates(currentSnapshot, originalSnapshot);
        
        if (divergences.length > 0) {
            console.warn(`⚠️ State divergences detected at ${this.replayTime.toFixed(1)}s:`);
            divergences.forEach(div => console.warn(`   ${div}`));
            this.divergenceDetected = true;
        } else {
            console.log(`✅ State validation passed at ${this.replayTime.toFixed(1)}s`);
        }
    }

    // Capture current game state for validation
    captureCurrentState() {
        const { units, buildings, resources } = this.gameContext;
        
        return {
            time: this.replayTime,
            unitCount: units.length,
            buildingCount: buildings.length,
            blueResources: resources.blue.mass + resources.blue.energy,
            redResources: resources.red.mass + resources.red.energy,
            units: units.map(u => ({
                id: u.id,
                x: Math.floor(u.x),
                y: Math.floor(u.y),
                hp: u.hp,
                type: u.type.name
            }))
        };
    }

    // Find original snapshot closest to replay time
    findOriginalSnapshot(time) {
        const snapshots = this.battleData.stateSnapshots;
        return snapshots.find(s => Math.abs(s.time - time) < 2.5) || null;
    }

    // Compare current state with original snapshot
    compareStates(current, original) {
        const divergences = [];
        const tol = this.stateValidation.tolerances;

        // Check unit counts
        if (Math.abs(current.unitCount - (original.teamStats.blue.units.total + original.teamStats.red.units.total)) > 0) {
            divergences.push(`Unit count: ${current.unitCount} vs ${original.teamStats.blue.units.total + original.teamStats.red.units.total}`);
        }

        // Check building counts
        if (Math.abs(current.buildingCount - (original.teamStats.blue.buildings.total + original.teamStats.red.buildings.total)) > 0) {
            divergences.push(`Building count: ${current.buildingCount} vs ${original.teamStats.blue.buildings.total + original.teamStats.red.buildings.total}`);
        }

        // Check resource totals
        const originalTotal = original.teamStats.blue.resources.totalValue + original.teamStats.red.resources.totalValue;
        const currentTotal = current.blueResources + current.redResources;
        if (Math.abs(currentTotal - originalTotal) > tol.resources) {
            divergences.push(`Resources: ${currentTotal} vs ${originalTotal}`);
        }

        return divergences;
    }

    // Complete replay
    completeReplay() {
        console.log(`🏁 Replay completed: ${this.battleData.id}`);
        console.log(`📊 Total frames: ${this.replayFrame}`);
        console.log(`⏱️ Total time: ${this.replayTime.toFixed(1)}s`);
        
        if (this.divergenceDetected) {
            console.warn('⚠️ Divergences were detected during replay');
        } else {
            console.log('✅ Replay completed with perfect determinism');
        }

        this.stopReplay();
    }

    // Stop replay and cleanup
    stopReplay() {
        this.isReplaying = false;
        disableDeterministicMode();
        
        // Reset replay state
        this.replayFrame = 0;
        this.replayTime = 0;
        this.nextCommandIndex = 0;
        this.divergenceDetected = false;
        
        console.log('⏹️ Replay stopped');
    }

    // Set replay speed multiplier
    setReplaySpeed(speed) {
        this.replaySpeed = Math.max(0.1, Math.min(10.0, speed));
        console.log(`⚡ Replay speed set to ${this.replaySpeed}x`);
    }

    // Skip to specific time in replay
    skipToTime(targetTime) {
        if (!this.isReplaying) return false;

        console.log(`⏭️ Skipping to ${targetTime.toFixed(1)}s`);
        
        // Reset and fast-forward
        this.resetGameState();
        this.replayFrame = 0;
        this.replayTime = 0;
        this.nextCommandIndex = 0;

        // Fast execution to target time
        const originalSpeed = this.replaySpeed;
        this.replaySpeed = 10.0; // Max speed for skipping
        
        while (this.replayTime < targetTime && this.isReplaying) {
            if (!this.updateReplay()) break;
        }
        
        this.replaySpeed = originalSpeed;
        return true;
    }

    // Get replay progress info
    getReplayInfo() {
        if (!this.battleData) return null;

        const totalDuration = this.battleData.duration / 1000;
        const progress = Math.min(1.0, this.replayTime / totalDuration);

        return {
            battleId: this.battleData.id,
            currentTime: this.replayTime,
            totalDuration,
            progress,
            frame: this.replayFrame,
            commandsProcessed: this.nextCommandIndex,
            totalCommands: this.commandQueue.length,
            speed: this.replaySpeed,
            divergenceDetected: this.divergenceDetected,
            isReplaying: this.isReplaying
        };
    }

    // Export replay for analysis
    exportReplayAnalysis() {
        if (!this.battleData) return null;

        return {
            battleId: this.battleData.id,
            originalDuration: this.battleData.duration,
            commandCount: this.battleData.inputCommands.length,
            deterministic: this.battleData.config.deterministic,
            seed: this.battleData.config.battleSeed,
            commandTypes: this.analyzeCommandTypes(),
            replayMetrics: {
                divergenceDetected: this.divergenceDetected,
                finalFrame: this.replayFrame,
                finalTime: this.replayTime
            }
        };
    }

    // Analyze command types in battle
    analyzeCommandTypes() {
        const types = {};
        this.battleData.inputCommands.forEach(cmd => {
            types[cmd.commandType] = (types[cmd.commandType] || 0) + 1;
        });
        return types;
    }
}

// Export singleton instance
export const battleReplay = new BattleReplay();