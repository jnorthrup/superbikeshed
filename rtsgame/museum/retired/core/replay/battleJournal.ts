// Battle Journal - localStorage-based battle recording and analysis
// Records complete battle data for post-game analysis and improvement
// Enhanced with deterministic action recording for synchronous replay

import { gameRNG, enableDeterministicMode, createReproducibleSeed } from '../deterministicRNG.js';

export class BattleJournal {
    constructor() {
        this.currentBattle = null;
        this.isRecording = false;
        this.frameCount = 0;
        this.maxStorageSize = 50 * 1024 * 1024; // 50MB limit
        this.frames = [];
        this.events = [];
        this.startTime = 0;
    }

    startRecording(battleConfig = {}) {
        const battleId = `battle_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const battleSeed = createReproducibleSeed(battleId, Date.now());
        
        // Enable deterministic mode for reproducible battles
        enableDeterministicMode(battleSeed);
        
        this.currentBattle = {
            id: battleId,
            startTime: Date.now(),
            config: {
                battleSeed: battleSeed,
                terrainSeed: gameRNG.randomInt(1, 1000000),
                startingResources: { mass: 100, energy: 150 },
                gameMode: 'standard',
                deterministic: true,
                ...battleConfig
            },
            metadata: {
                userAgent: navigator.userAgent,
                screenResolution: `${screen.width}x${screen.height}`,
                timestamp: new Date().toISOString(),
                version: '1.0.0'
            },
            frames: [],
            events: [],
            playerActions: [], // Traditional player actions
            inputCommands: [], // Specific input commands for replay
            rngState: [], // RNG state snapshots for deterministic replay
            performanceMetrics: [],
            stateSnapshots: [],
            analysis: {
                phases: [],
                keyMoments: [],
                balanceIssues: [],
                playerBehavior: [],
                outcomeData: {}
            }
        };

        this.isRecording = true;
        this.frameCount = 0;
        this.frames = [];
        this.events = [];
        this.startTime = Date.now();
        
        console.log(`📹 Battle Journal: Recording started - ${this.currentBattle.id}`);
        console.log(`🎲 Deterministic seed: ${battleSeed}`);
        this.recordEvent('BATTLE_START', 'Battle recording initiated', { config: battleConfig });
        this.recordRNGState();
    }

    recordFrame(gameContext) {
        if (!this.isRecording || !this.currentBattle) return;

        this.frameCount++;
        
        // Record every 30th frame (2fps recording for 60fps game)
        if (this.frameCount % 30 === 0) {
            const frameData = this.captureFrameData(gameContext);
            this.currentBattle.frames.push(frameData);
        }

        // Record state snapshots every 5 seconds
        if (this.frameCount % (5 * 60) === 0) {
            const snapshot = this.captureStateSnapshot(gameContext);
            this.currentBattle.stateSnapshots.push(snapshot);
        }

        // Monitor storage usage
        if (this.frameCount % (30 * 60) === 0) { // Every 30 seconds
            this.checkStorageUsage();
        }
    }

    captureFrameData(gameContext) {
        const { units, buildings, resources, gameState, camera } = gameContext;
        
        return {
            time: gameState.gameTime,
            frame: this.frameCount,
            units: units.map(unit => ({
                id: unit.id || `${unit.type.name}_${unit.x}_${unit.y}`,
                type: unit.type.name,
                team: unit.team,
                x: Math.floor(unit.x),
                y: Math.floor(unit.y),
                hp: unit.hp,
                maxHp: unit.maxHp,
                target: unit.target ? {
                    type: unit.target.type?.name || 'building',
                    team: unit.target.team,
                    distance: Math.floor(this.getDistance(unit, unit.target))
                } : null,
                state: this.getUnitState(unit)
            })),
            buildings: buildings.map(building => ({
                id: building.id || `${building.type.name}_${building.x}_${building.y}`,
                type: building.type.name,
                team: building.team,
                x: Math.floor(building.x),
                y: Math.floor(building.y),
                hp: building.hp,
                maxHp: building.maxHp,
                productionQueue: building.productionQueue?.length || 0
            })),
            resources: {
                blue: { ...resources.blue },
                red: { ...resources.red }
            },
            camera: {
                x: Math.floor(camera.x),
                y: Math.floor(camera.y),
                zoom: camera.zoom
            },
            metrics: this.calculateFrameMetrics(gameContext)
        };
    }

    getUnitState(unit) {
        if (unit.constructionTask) return 'building';
        if (unit.target) return 'combat';
        if (unit.patrolTarget) return 'moving';
        if (unit.isEscaping) return 'retreating';
        return 'idle';
    }

    getDistance(unit1, unit2) {
        return Math.sqrt((unit1.x - unit2.x) ** 2 + (unit1.y - unit2.y) ** 2);
    }

    calculateFrameMetrics(gameContext) {
        const { units, buildings } = gameContext;
        
        const activeCombats = units.filter(u => u.target && u.target.hp > 0).length;
        const idleUnits = units.filter(u => !u.target && !u.patrolTarget && !u.constructionTask).length;
        const buildingProgress = buildings.reduce((sum, b) => sum + (b.productionProgress || 0), 0);
        
        return {
            activeCombats,
            idleUnits,
            buildingProgress,
            totalUnits: units.length,
            totalBuildings: buildings.length,
            economicActivity: this.calculateEconomicActivity(gameContext)
        };
    }

    calculateEconomicActivity(gameContext) {
        const { buildings, resources } = gameContext;
        const extractors = buildings.filter(b => b.type.resourceGeneration).length;
        const factories = buildings.filter(b => b.type.produces).length;
        const totalResources = resources.blue.mass + resources.blue.energy + resources.red.mass + resources.red.energy;
        
        return extractors * 5 + factories * 3 + Math.floor(totalResources / 100);
    }

    captureStateSnapshot(gameContext) {
        const { units, buildings, resources, gameState } = gameContext;
        
        return {
            time: gameState.gameTime,
            gamePhase: this.determineGamePhase(gameContext),
            teamStats: {
                blue: this.calculateTeamStats('blue', units, buildings, resources),
                red: this.calculateTeamStats('red', units, buildings, resources)
            },
            mapControl: this.calculateMapControl(units),
            economicBalance: this.calculateEconomicBalance(resources),
            militaryBalance: this.calculateMilitaryBalance(units)
        };
    }

    determineGamePhase(gameContext) {
        const { units, buildings, gameState } = gameContext;
        const totalBuildings = buildings.length;
        const totalUnits = units.length;
        const time = gameState.gameTime;
        
        if (time < 60 || totalBuildings < 2) return 'early';
        if (time < 180 || totalBuildings < 6 || totalUnits < 8) return 'mid';
        return 'late';
    }

    calculateTeamStats(team, units, buildings, resources) {
        const teamUnits = units.filter(u => u.team === team);
        const teamBuildings = buildings.filter(b => b.team === team);
        
        return {
            units: {
                total: teamUnits.length,
                combat: teamUnits.filter(u => !u.type.support).length,
                support: teamUnits.filter(u => u.type.support).length,
                avgHealth: teamUnits.reduce((sum, u) => sum + (u.hp / u.maxHp), 0) / teamUnits.length || 0
            },
            buildings: {
                total: teamBuildings.length,
                extractors: teamBuildings.filter(b => b.type.resourceGeneration).length,
                factories: teamBuildings.filter(b => b.type.produces).length,
                avgHealth: teamBuildings.reduce((sum, b) => sum + (b.hp / b.maxHp), 0) / teamBuildings.length || 0
            },
            resources: {
                mass: resources[team].mass,
                energy: resources[team].energy,
                totalValue: resources[team].mass + resources[team].energy,
                income: (resources[team].massIncome || 0) + (resources[team].energyIncome || 0)
            }
        };
    }

    calculateMapControl(units) {
        const bluePositions = units.filter(u => u.team === 'blue').map(u => ({ x: u.x, y: u.y }));
        const redPositions = units.filter(u => u.team === 'red').map(u => ({ x: u.x, y: u.y }));
        
        // Simple map control calculation based on unit spread
        const blueSpread = this.calculatePositionSpread(bluePositions);
        const redSpread = this.calculatePositionSpread(redPositions);
        
        return {
            blue: blueSpread,
            red: redSpread,
            contested: Math.abs(blueSpread - redSpread) < 20
        };
    }

    calculatePositionSpread(positions) {
        if (positions.length === 0) return 0;
        
        const avgX = positions.reduce((sum, p) => sum + p.x, 0) / positions.length;
        const avgY = positions.reduce((sum, p) => sum + p.y, 0) / positions.length;
        
        return positions.reduce((sum, p) => {
            return sum + Math.sqrt((p.x - avgX) ** 2 + (p.y - avgY) ** 2);
        }, 0) / positions.length;
    }

    calculateEconomicBalance(resources) {
        const blueTotal = resources.blue.mass + resources.blue.energy;
        const redTotal = resources.red.mass + resources.red.energy;
        const total = blueTotal + redTotal;
        
        if (total === 0) return 0.5;
        return blueTotal / total;
    }

    calculateMilitaryBalance(units) {
        const blueCombat = units.filter(u => u.team === 'blue' && !u.type.support).length;
        const redCombat = units.filter(u => u.team === 'red' && !u.type.support).length;
        const total = blueCombat + redCombat;
        
        if (total === 0) return 0.5;
        return blueCombat / total;
    }

    recordEvent(type, message, data = null) {
        if (!this.isRecording || !this.currentBattle) return;

        this.currentBattle.events.push({
            time: this.currentBattle.stateSnapshots.length > 0 ? 
                  this.currentBattle.stateSnapshots[this.currentBattle.stateSnapshots.length - 1].time : 0,
            frame: this.frameCount,
            type,
            message,
            data,
            timestamp: Date.now()
        });
    }

    recordPlayerAction(action, target = null, position = null) {
        if (!this.isRecording || !this.currentBattle) return;

        this.currentBattle.playerActions.push({
            time: this.currentBattle.stateSnapshots.length > 0 ? 
                  this.currentBattle.stateSnapshots[this.currentBattle.stateSnapshots.length - 1].time : 0,
            frame: this.frameCount,
            action,
            target,
            position,
            timestamp: Date.now()
        });
    }

    // Record specific input commands for deterministic replay
    recordInputCommand(commandType, commandData, playerId = 'human') {
        if (!this.isRecording || !this.currentBattle) return;

        const command = {
            time: this.currentBattle.stateSnapshots.length > 0 ? 
                  this.currentBattle.stateSnapshots[this.currentBattle.stateSnapshots.length - 1].time : 0,
            frame: this.frameCount,
            playerId,
            commandType,
            commandData: { ...commandData },
            rngCallCount: gameRNG.callCount,
            timestamp: Date.now()
        };

        this.currentBattle.inputCommands.push(command);
        
        // Record RNG state if this is a command that might affect randomness
        if (this.isRandomnessAffectingCommand(commandType)) {
            this.recordRNGState();
        }
    }

    // Check if command type affects randomness
    isRandomnessAffectingCommand(commandType) {
        const randomnessCommands = [
            'UNIT_MOVE', 'UNIT_ATTACK', 'BUILD_STRUCTURE', 
            'SPAWN_UNIT', 'COMBAT_RESOLVE', 'PATHFIND'
        ];
        return randomnessCommands.includes(commandType);
    }

    // Record RNG state for deterministic replay
    recordRNGState() {
        if (!this.isRecording || !this.currentBattle) return;

        this.currentBattle.rngState.push({
            time: this.currentBattle.stateSnapshots.length > 0 ? 
                  this.currentBattle.stateSnapshots[this.currentBattle.stateSnapshots.length - 1].time : 0,
            frame: this.frameCount,
            state: gameRNG.getState(),
            timestamp: Date.now()
        });
    }

    recordPerformanceMetric(metric, value) {
        if (!this.isRecording || !this.currentBattle) return;

        this.currentBattle.performanceMetrics.push({
            time: this.currentBattle.stateSnapshots.length > 0 ? 
                  this.currentBattle.stateSnapshots[this.currentBattle.stateSnapshots.length - 1].time : 0,
            metric,
            value,
            timestamp: Date.now()
        });
    }

    stopRecording(gameOutcome = null) {
        if (!this.isRecording || !this.currentBattle) return null;

        this.currentBattle.endTime = Date.now();
        this.currentBattle.duration = this.currentBattle.endTime - this.currentBattle.startTime;
        this.currentBattle.analysis.outcomeData = gameOutcome || {};
        
        // Perform post-battle analysis
        this.analyzeBattle();
        
        // Save to localStorage
        this.saveBattleToStorage();
        
        console.log(`📹 Battle Journal: Recording stopped - ${this.currentBattle.id}`);
        console.log(`📊 Duration: ${(this.currentBattle.duration / 1000).toFixed(1)}s, Frames: ${this.frameCount}`);
        
        const battleId = this.currentBattle.id;
        this.isRecording = false;
        this.currentBattle = null;
        
        return battleId;
    }

    analyzeBattle() {
        if (!this.currentBattle) return;

        const analysis = this.currentBattle.analysis;
        
        // Analyze game phases
        analysis.phases = this.analyzeGamePhases();
        
        // Identify key moments
        analysis.keyMoments = this.identifyKeyMoments();
        
        // Detect balance issues
        analysis.balanceIssues = this.detectBalanceIssues();
        
        // Analyze player behavior patterns
        analysis.playerBehavior = this.analyzePlayerBehavior();
        
        console.log(`📋 Battle Analysis Complete:`, {
            phases: analysis.phases.length,
            keyMoments: analysis.keyMoments.length,
            balanceIssues: analysis.balanceIssues.length
        });
    }

    analyzeGamePhases() {
        const snapshots = this.currentBattle.stateSnapshots;
        const phases = [];
        let currentPhase = null;
        
        snapshots.forEach(snapshot => {
            if (snapshot.gamePhase !== currentPhase) {
                phases.push({
                    phase: snapshot.gamePhase,
                    startTime: snapshot.time,
                    economicBalance: snapshot.economicBalance,
                    militaryBalance: snapshot.militaryBalance
                });
                currentPhase = snapshot.gamePhase;
            }
        });
        
        return phases;
    }

    identifyKeyMoments() {
        const events = this.currentBattle.events;
        const snapshots = this.currentBattle.stateSnapshots;
        const keyMoments = [];
        
        // First building
        const firstBuilding = events.find(e => e.type === 'BUILDING_COMPLETED');
        if (firstBuilding) {
            keyMoments.push({
                type: 'FIRST_BUILDING',
                time: firstBuilding.time,
                significance: 'high'
            });
        }
        
        // First combat
        const firstCombat = events.find(e => e.type === 'UNIT_DESTROYED' || e.type === 'COMBAT_START');
        if (firstCombat) {
            keyMoments.push({
                type: 'FIRST_COMBAT',
                time: firstCombat.time,
                significance: 'medium'
            });
        }
        
        // Major resource milestones
        snapshots.forEach(snapshot => {
            const totalResources = snapshot.teamStats.blue.resources.totalValue + 
                                 snapshot.teamStats.red.resources.totalValue;
            
            if (totalResources > 1000 && !keyMoments.find(m => m.type === 'RESOURCE_BOOM')) {
                keyMoments.push({
                    type: 'RESOURCE_BOOM',
                    time: snapshot.time,
                    significance: 'medium'
                });
            }
        });
        
        return keyMoments;
    }

    detectBalanceIssues() {
        const snapshots = this.currentBattle.stateSnapshots;
        const issues = [];
        
        snapshots.forEach(snapshot => {
            // Economic imbalance
            if (Math.abs(snapshot.economicBalance - 0.5) > 0.3) {
                issues.push({
                    type: 'ECONOMIC_IMBALANCE',
                    time: snapshot.time,
                    severity: Math.abs(snapshot.economicBalance - 0.5),
                    favoring: snapshot.economicBalance > 0.5 ? 'blue' : 'red'
                });
            }
            
            // Military imbalance
            if (Math.abs(snapshot.militaryBalance - 0.5) > 0.4) {
                issues.push({
                    type: 'MILITARY_IMBALANCE', 
                    time: snapshot.time,
                    severity: Math.abs(snapshot.militaryBalance - 0.5),
                    favoring: snapshot.militaryBalance > 0.5 ? 'blue' : 'red'
                });
            }
        });
        
        return issues;
    }

    analyzePlayerBehavior() {
        const actions = this.currentBattle.playerActions;
        const behavior = {
            totalActions: actions.length,
            actionsPerMinute: actions.length / (this.currentBattle.duration / 60000),
            actionTypes: {},
            earlyGameFocus: 'unknown',
            microManagement: 'low'
        };
        
        // Count action types
        actions.forEach(action => {
            behavior.actionTypes[action.action] = (behavior.actionTypes[action.action] || 0) + 1;
        });
        
        // Determine early game focus
        const earlyActions = actions.filter(a => a.time < 120); // First 2 minutes
        const buildActions = earlyActions.filter(a => a.action.includes('build')).length;
        const combatActions = earlyActions.filter(a => a.action.includes('attack')).length;
        
        if (buildActions > combatActions * 2) {
            behavior.earlyGameFocus = 'economic';
        } else if (combatActions > buildActions) {
            behavior.earlyGameFocus = 'aggressive';
        } else {
            behavior.earlyGameFocus = 'balanced';
        }
        
        return behavior;
    }

    saveBattleToStorage() {
        try {
            // Check if localStorage is available (not available in headless environments)
            if (typeof localStorage === 'undefined') {
                console.log('📹 Battle Journal: localStorage not available in headless environment, skipping save');
                return;
            }
            
            const battleData = JSON.stringify(this.currentBattle);
            const battleKey = `battle_${this.currentBattle.id}`;
            
            // Check if we have space
            const estimatedSize = new Blob([battleData]).size;
            if (estimatedSize > this.maxStorageSize * 0.8) {
                console.warn('📹 Battle Journal: Battle data too large, compressing...');
                this.compressBattleData();
            }
            
            localStorage.setItem(battleKey, battleData);
            
            // Update battle index
            this.updateBattleIndex(this.currentBattle.id);
            
            console.log(`💾 Battle saved to localStorage: ${battleKey} (${(estimatedSize / 1024).toFixed(1)}KB)`);
            
        } catch (error) {
            console.error('📹 Battle Journal: Failed to save battle:', error);
            
            if (error.name === 'QuotaExceededError') {
                this.cleanupOldBattles();
                // Try again after cleanup
                try {
                    if (typeof localStorage !== 'undefined') {
                        localStorage.setItem(`battle_${this.currentBattle.id}`, JSON.stringify(this.currentBattle));
                    }
                } catch (retryError) {
                    console.error('📹 Battle Journal: Failed to save even after cleanup:', retryError);
                }
            }
        }
    }

    compressBattleData() {
        // Reduce frame data resolution
        this.currentBattle.frames = this.currentBattle.frames.filter((_, index) => index % 2 === 0);
        
        // Limit state snapshots
        if (this.currentBattle.stateSnapshots.length > 50) {
            this.currentBattle.stateSnapshots = this.currentBattle.stateSnapshots.filter((_, index) => index % 2 === 0);
        }
        
        // Remove redundant data
        this.currentBattle.frames.forEach(frame => {
            frame.units.forEach(unit => {
                delete unit.id;
                unit.x = Math.floor(unit.x / 10) * 10; // Round to 10s
                unit.y = Math.floor(unit.y / 10) * 10;
            });
        });
    }

    updateBattleIndex(battleId) {
        try {
            if (typeof localStorage === 'undefined') {
                console.log('📹 Battle Journal: localStorage not available, skipping index update');
                return;
            }
            
            const indexKey = 'battle_journal_index';
            let index = JSON.parse(localStorage.getItem(indexKey) || '[]');
            
            index.push({
                id: battleId,
                timestamp: Date.now(),
                duration: this.currentBattle.duration,
                outcome: this.currentBattle.analysis.outcomeData
            });
            
            // Keep only last 20 battles in index
            if (index.length > 20) {
                const oldBattles = index.splice(0, index.length - 20);
                oldBattles.forEach(oldBattle => {
                    localStorage.removeItem(`battle_${oldBattle.id}`);
                });
            }
            
            localStorage.setItem(indexKey, JSON.stringify(index));
            
        } catch (error) {
            console.error('📹 Battle Journal: Failed to update index:', error);
        }
    }

    getBattleIndex() {
        try {
            if (typeof localStorage === 'undefined') {
                return [];
            }
            return JSON.parse(localStorage.getItem('battle_journal_index') || '[]');
        } catch (error) {
            console.error('📹 Battle Journal: Failed to read index:', error);
            return [];
        }
    }

    loadBattle(battleId) {
        try {
            if (typeof localStorage === 'undefined') {
                return null;
            }
            const battleData = localStorage.getItem(`battle_${battleId}`);
            return battleData ? JSON.parse(battleData) : null;
        } catch (error) {
            console.error(`📹 Battle Journal: Failed to load battle ${battleId}:`, error);
            return null;
        }
    }

    deleteBattle(battleId) {
        try {
            if (typeof localStorage === 'undefined') {
                console.log('📹 Battle Journal: localStorage not available, skipping deletion');
                return false;
            }
            
            localStorage.removeItem(`battle_${battleId}`);
            
            // Update index
            const index = this.getBattleIndex();
            const updatedIndex = index.filter(battle => battle.id !== battleId);
            localStorage.setItem('battle_journal_index', JSON.stringify(updatedIndex));
            
            console.log(`🗑️ Battle deleted: ${battleId}`);
            return true;
        } catch (error) {
            console.error(`📹 Battle Journal: Failed to delete battle ${battleId}:`, error);
            return false;
        }
    }

    cleanupOldBattles() {
        const index = this.getBattleIndex();
        
        // Remove oldest battles to free up space
        const battlesToRemove = Math.max(1, Math.floor(index.length * 0.3));
        const oldestBattles = index.slice(0, battlesToRemove);
        
        oldestBattles.forEach(battle => {
            this.deleteBattle(battle.id);
        });
        
        console.log(`🧹 Cleaned up ${battlesToRemove} old battles`);
    }

    checkStorageUsage() {
        try {
            if (typeof localStorage === 'undefined') {
                return;
            }
            
            let totalSize = 0;
            for (let key in localStorage) {
                if (key.startsWith('battle_')) {
                    totalSize += localStorage[key].length;
                }
            }
            
            const sizeInMB = totalSize / (1024 * 1024);
            console.log(`💾 Battle Journal storage: ${sizeInMB.toFixed(1)}MB`);
            
            if (sizeInMB > this.maxStorageSize / (1024 * 1024) * 0.9) {
                console.warn('📹 Battle Journal: Approaching storage limit, cleaning up...');
                this.cleanupOldBattles();
            }
            
        } catch (error) {
            console.error('📹 Battle Journal: Failed to check storage usage:', error);
        }
    }

    exportBattleData(battleId, format = 'json') {
        const battle = this.loadBattle(battleId);
        if (!battle) return null;
        
        if (format === 'csv') {
            return this.convertToCSV(battle);
        }
        
        return JSON.stringify(battle, null, 2);
    }

    convertToCSV(battle) {
        // Convert state snapshots to CSV format
        const headers = ['time', 'blue_units', 'red_units', 'blue_buildings', 'red_buildings', 
                        'blue_resources', 'red_resources', 'economic_balance', 'military_balance'];
        
        let csv = headers.join(',') + '\n';
        
        battle.stateSnapshots.forEach(snapshot => {
            const row = [
                snapshot.time,
                snapshot.teamStats.blue.units.total,
                snapshot.teamStats.red.units.total,
                snapshot.teamStats.blue.buildings.total,
                snapshot.teamStats.red.buildings.total,
                snapshot.teamStats.blue.resources.totalValue,
                snapshot.teamStats.red.resources.totalValue,
                snapshot.economicBalance,
                snapshot.militaryBalance
            ];
            csv += row.join(',') + '\n';
        });
        
        return csv;
    }

    generateBattleReport(battleId) {
        const battle = this.loadBattle(battleId);
        if (!battle) return null;
        
        const report = {
            battleId: battle.id,
            duration: `${(battle.duration / 1000).toFixed(1)}s`,
            winner: battle.analysis.outcomeData.winner || 'Unknown',
            phases: battle.analysis.phases.length,
            keyMoments: battle.analysis.keyMoments.length,
            balanceIssues: battle.analysis.balanceIssues.length,
            frameCount: battle.frames.length,
            eventCount: battle.events.length,
            playerActions: battle.playerActions.length,
            finalSnapshot: battle.stateSnapshots[battle.stateSnapshots.length - 1] || null
        };
        
        return report;
    }

    loadReplay(replay) {
        this.frames = replay.frames;
        this.events = replay.events;
        this.startTime = replay.timestamp;
    }

    exportReplay() {
        return {
            timestamp: this.startTime,
            frames: this.frames,
            events: this.events
        };
    }

    getFrameAtTime(time) {
        return this.frames.find(frame => frame.time >= time) || this.frames[this.frames.length - 1];
    }

    getEventsAtTime(time) {
        return this.events.filter(event => event.time <= time);
    }

    getDuration() {
        if (this.frames.length === 0) return 0;
        return this.frames[this.frames.length - 1].time;
    }
}

// Export singleton instance
export const battleJournal = new BattleJournal();
