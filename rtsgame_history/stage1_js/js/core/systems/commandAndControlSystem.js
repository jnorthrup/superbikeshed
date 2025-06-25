import { ComputroniumSystem } from './computroniumSystem.js';
import { ProofOfWorkSystem } from './proofOfWorkSystem.js';

export class CommandAndControlSystem {
    constructor() {
        this.commandHierarchy = new Map(); // entityId -> {rank, subordinates, commander}
        this.networkNodes = new Map(); // entityId -> {position, range, latency}
        this.squadGroups = new Map(); // squadId -> {leader, members, formation, orders}
        this.lightSpeedDelay = 0.001; // Base delay per meter (light speed)
        this.rankBonuses = {
            COMMANDER: { range: 2.0, latency: 0.5, coordination: 2.0 },
            OFFICER: { range: 1.5, latency: 0.7, coordination: 1.5 },
            SERGEANT: { range: 1.2, latency: 0.8, coordination: 1.2 },
            UNIT: { range: 1.0, latency: 1.0, coordination: 1.0 }
        };
        
        // Network constants
        this.LIGHT_SPEED = 299792458; // meters per second
        this.FIBER_SPEED = 0.67 * this.LIGHT_SPEED; // ~67% of light speed in fiber
        this.MAX_ACCEPTABLE_LATENCY = 500; // ms, similar to SupCom
        this.HEARTBEAT_INTERVAL = 100; // ms
        this.PREDICTION_WINDOW = 1000; // ms to look ahead for predictions
        
        // Network state
        this.networkState = {
            lastHeartbeat: 0,
            latencyHistory: [],
            predictionConfidence: 1.0,
            desyncDetected: false,
            lastSyncTime: 0,
            activeCodecs: new Map(), // Map of active codec nodes
            codecLoad: new Map(),    // Current load on each codec
            relayPaths: new Map()    // Optimized paths through codecs
        };

        // Prediction and reconciliation state
        this.predictionState = {
            lastProcessedInput: 0,
            pendingInputs: [],
            entityStates: new Map(), // Last known server states
            predictedStates: new Map(), // Client-predicted states
            reconciliationQueue: [], // States to reconcile
            maxReconciliationDelay: 100, // ms
            maxPredictionSteps: 8, // Maximum steps to predict ahead
            dimensionalInstabilityMap: new Map() // Cache for dimensional instability calculations
        };

        // Cache and memory hierarchy constants
        this.CACHE_LATENCIES = {
            L1_CACHE: 4,      // 4 cycles
            L2_CACHE: 12,     // 12 cycles
            L3_CACHE: 40,     // 40 cycles
            MAIN_MEMORY: 100, // 100 cycles
            REMOTE_MEMORY: 300 // 300 cycles (NUMA)
        };

        this.CACHE_SIZES = {
            L1_CACHE: 64 * 1024,    // 64KB
            L2_CACHE: 256 * 1024,   // 256KB
            L3_CACHE: 8 * 1024 * 1024 // 8MB
        };

        // Cache coherence states
        this.CACHE_COHERENCE_STATES = {
            MODIFIED: 'MODIFIED',    // Cache line is modified and exclusive
            SHARED: 'SHARED',        // Cache line is shared with other caches
            EXCLUSIVE: 'EXCLUSIVE',  // Cache line is exclusive but not modified
            INVALID: 'INVALID'       // Cache line is invalid
        };

        // Prefetch strategies
        this.PREFETCH_STRATEGIES = {
            AGGRESSIVE: 'AGGRESSIVE',    // Prefetch multiple levels ahead
            MODERATE: 'MODERATE',        // Prefetch one level ahead
            CONSERVATIVE: 'CONSERVATIVE' // Only prefetch on high confidence
        };

        // MESI Protocol Messages
        this.MESI_MESSAGES = {
            READ_MISS: 'READ_MISS',
            WRITE_MISS: 'WRITE_MISS',
            INVALIDATE: 'INVALIDATE',
            READ_RESPONSE: 'READ_RESPONSE',
            WRITE_RESPONSE: 'WRITE_RESPONSE'
        };

        // Advanced Prefetch Patterns
        this.PREFETCH_PATTERNS = {
            SEQUENTIAL: 'SEQUENTIAL',           // Predict sequential access
            STRIDED: 'STRIDED',                 // Predict strided access
            LOOP: 'LOOP',                       // Predict loop-based access
            BRANCH_PREDICT: 'BRANCH_PREDICT',   // Predict branch-based access
            CONTEXT_SWITCH: 'CONTEXT_SWITCH'    // Predict context switch patterns
        };

        // Cache Performance Metrics
        this.CACHE_METRICS = {
            HIT_RATIO: 'HIT_RATIO',
            MISS_RATIO: 'MISS_RATIO',
            COHERENCE_EFFICIENCY: 'COHERENCE_EFFICIENCY',
            PREFETCH_ACCURACY: 'PREFETCH_ACCURACY',
            CACHE_UTILIZATION: 'CACHE_UTILIZATION',
            LATENCY_DISTRIBUTION: 'LATENCY_DISTRIBUTION'
        };

        // Pattern Analysis Parameters
        this.PATTERN_PARAMS = {
            MIN_SEQUENCE_LENGTH: 3,
            MAX_STRIDE: 16,
            LOOP_THRESHOLD: 5,
            BRANCH_CONFIDENCE: 0.7,
            CONTEXT_SWITCH_THRESHOLD: 1000
        };

        // Command Hierarchy Constants
        this.COMMAND_RANKS = {
            GENERAL: { level: 5, auraRadius: 1000, forkBonus: 2 },
            COLONEL: { level: 4, auraRadius: 800, forkBonus: 1 },
            MAJOR: { level: 3, auraRadius: 600, forkBonus: 1 },
            CAPTAIN: { level: 2, auraRadius: 400, forkBonus: 0 },
            LIEUTENANT: { level: 1, auraRadius: 200, forkBonus: 0 }
        };

        // Latency Constants
        this.LATENCY_FACTORS = {
            BASE_DISTANCE_LATENCY: 0.001, // ms per meter
            DIMENSIONAL_INSTABILITY: 0.1, // ms per sector
            COMPUTRONIUM_PROCESSING: 0.05  // ms per cycle
        };
    }

    update(deltaTime, entities, gameContext) {
        // Process command hierarchy first
        this.processCommandHierarchy(entities);
        
        // Update cache states and coordination
        this.updateCacheStates(entities);
        
        // Process squad behaviors
        this.processSquadBehaviors(entities, gameContext);
        
        // Update network effects
        this.updateNetworkEffects(entities, gameContext);
    }

    processCommandHierarchy(entities) {
        // Find all commanders
        const commanders = entities.filter(e => 
            e.hasComponent('CommandComponent') && 
            e.getComponent('CommandComponent').rank
        );
        
        // Sort by rank level
        commanders.sort((a, b) => {
            const rankA = this.COMMAND_RANKS[a.getComponent('CommandComponent').rank].level;
            const rankB = this.COMMAND_RANKS[b.getComponent('CommandComponent').rank].level;
            return rankB - rankA;
        });
        
        // Process each commander's aura and command chain
        commanders.forEach(commander => {
            const command = commander.getComponent('CommandComponent');
            const rankData = this.COMMAND_RANKS[command.rank];
            
            // Calculate aura effects
            const auraRadius = rankData.auraRadius;
            const nearbyUnits = this.findUnitsInRange(commander, entities, auraRadius);
            
            // Apply aura effects
            this.applyAuraEffects(commander, nearbyUnits, rankData);
            
            // Process command chain
            const subordinates = this.findSubordinateCommanders(commander, entities);
            this.processCommandChain(commander, subordinates);
        });
    }

    applyAuraEffects(commander, units, rankData) {
        units.forEach(unit => {
            if (unit.hasComponent('ComputroniumComponent')) {
                const computronium = unit.getComponent('ComputroniumComponent');
                
                // Apply fork bonus
                computronium.availableForks += rankData.forkBonus;
                
                // Apply other aura effects
                if (unit.hasComponent('CombatComponent')) {
                    const combat = unit.getComponent('CombatComponent');
                    combat.accuracy *= 1.1;
                    combat.rateOfFire *= 1.05;
                }
                
                if (unit.hasComponent('ShieldComponent')) {
                    const shield = unit.getComponent('ShieldComponent');
                    shield.rechargeRate *= 1.15;
                }
            }
        });
    }

    processCommandChain(commander, subordinates) {
        const command = commander.getComponent('CommandComponent');
        
        // Calculate command scope based on rank
        const scope = this.calculateCommandScope(commander);
        
        // Process strategic directives
        if (command.strategicDirective) {
            // Calculate command chain efficiency
            const chainEfficiency = this.calculateCommandChainEfficiency(commander, subordinates);
            
            // Process each subordinate with chain efficiency
            subordinates.forEach(subordinate => {
                this.issueTacticalOrders(
                    subordinate,
                    command.strategicDirective,
                    scope,
                    this.calculateCommandLatency(commander, subordinate),
                    chainEfficiency
                );
            });
            
            // Update command chain state
            this.updateCommandChainState(commander, subordinates, chainEfficiency);
        }
    }

    calculateCommandScope(commander) {
        const rank = commander.getComponent('CommandComponent').rank;
        const rankData = this.COMMAND_RANKS[rank];
        
        return {
            radius: rankData.auraRadius * 2,
            maxSubordinates: rankData.level * 2,
            tacticalOptions: this.getTacticalOptionsForRank(rank)
        };
    }

    getTacticalOptionsForRank(rank) {
        const options = {
            GENERAL: ['STRATEGIC_DEPLOYMENT', 'COORDINATED_ASSAULT', 'DEFENSIVE_POSITIONING'],
            COLONEL: ['TACTICAL_DEPLOYMENT', 'FOCUSED_ASSAULT', 'DEFENSIVE_FORMATION'],
            MAJOR: ['SQUAD_DEPLOYMENT', 'ENGAGEMENT', 'DEFENSIVE_STANCE'],
            CAPTAIN: ['UNIT_DEPLOYMENT', 'COMBAT', 'DEFENSIVE_ACTION'],
            LIEUTENANT: ['INDIVIDUAL_ORDERS', 'COMBAT', 'DEFENSIVE']
        };
        
        return options[rank] || options.LIEUTENANT;
    }

    calculateCommandLatency(commander, target) {
        const distance = this.calculateDistance(commander, target);
        const computronium = commander.getComponent('ComputroniumComponent');
        
        return (
            distance * this.LATENCY_FACTORS.BASE_DISTANCE_LATENCY +
            this.LATENCY_FACTORS.DIMENSIONAL_INSTABILITY +
            (computronium.processingDelay * this.LATENCY_FACTORS.COMPUTRONIUM_PROCESSING)
        );
    }

    processSquadBehaviors(entities, gameContext) {
        // Group units into squads
        const squads = this.groupUnitsIntoSquads(entities);
        
        // Process each squad
        squads.forEach(squad => {
            // Update squad formation
            this.updateSquadFormation(squad);
            
            // Apply RoE settings
            this.applyRulesOfEngagement(squad);
            
            // Update contextual behavior
            this.updateContextualBehavior(squad, gameContext);
            
            // Process support requests
            this.processSupportRequests(squad);
        });
    }

    groupUnitsIntoSquads(entities) {
        const squads = new Map();
        
        entities.forEach(entity => {
            if (entity.hasComponent('CommandComponent')) {
                const command = entity.getComponent('CommandComponent');
                if (command.squadId) {
                    if (!squads.has(command.squadId)) {
                        squads.set(command.squadId, []);
                    }
                    squads.get(command.squadId).push(entity);
                }
            }
        });
        
        return Array.from(squads.values());
    }

    updateNetworkEffects(entities, gameContext) {
        entities.forEach(entity => {
            if (entity.hasComponent('ComputroniumComponent')) {
                const computronium = entity.getComponent('ComputroniumComponent');
                
                // Update PoW validation
                this.updatePoWValidation(computronium, gameContext);
                
                // Update network latency effects
                this.updateNetworkLatency(computronium, gameContext);
                
                // Update predictive models
                this.updatePredictiveModels(computronium, gameContext);
            }
        });
    }

    updatePoWValidation(computronium, gameContext) {
        // Validate incoming commands with PoW
        if (computronium.pendingCommands) {
            computronium.pendingCommands = computronium.pendingCommands.filter(command => {
                return this.validateCommandPoW(command, computronium);
            });
        }
    }

    updateNetworkLatency(computronium, gameContext) {
        // Apply latency effects to command execution
        if (computronium.pendingCommands) {
            computronium.pendingCommands.forEach(command => {
                command.remainingLatency -= gameContext.deltaTime;
                if (command.remainingLatency <= 0) {
                    this.executeCommand(command, computronium);
                }
            });
        }
    }

    updatePredictiveModels(computronium, gameContext) {
        // Update predictive models based on computronium level
        if (computronium.coreLevel >= 3) {
            this.updateEnemyMovementPrediction(computronium, gameContext);
            this.updateAbilityTimingPrediction(computronium, gameContext);
            this.updateOptimalCommandSequence(computronium, gameContext);
        }
    }

    updateNetworkState(deltaTime) {
        const currentTime = Date.now();
        
        // Update heartbeat
        if (currentTime - this.networkState.lastHeartbeat >= this.HEARTBEAT_INTERVAL) {
            this.processHeartbeat(currentTime);
        }
        
        // Update codec system
        this.updateCodecSystem();
        
        // Update latency history
        this.updateLatencyHistory();
        
        // Check for desync conditions
        this.checkDesyncConditions();
        
        // Update prediction confidence
        this.updatePredictionConfidence();
    }

    processHeartbeat(currentTime) {
        // Calculate round-trip time
        const rtt = this.calculateRoundTripTime();
        
        // Update latency history
        this.networkState.latencyHistory.push({
            timestamp: currentTime,
            rtt: rtt
        });
        
        // Trim old history
        if (this.networkState.latencyHistory.length > 10) {
            this.networkState.latencyHistory.shift();
        }
        
        this.networkState.lastHeartbeat = currentTime;
    }

    calculateRoundTripTime() {
        // Simulate network conditions
        const baseLatency = 50; // ms, minimum latency
        const jitter = Math.random() * 20; // ms, random network jitter
        const congestion = this.calculateNetworkCongestion(); // ms, based on command volume
        
        return baseLatency + jitter + congestion;
    }

    calculateNetworkCongestion() {
        // Calculate congestion based on active commands and network load
        const activeCommands = this.getActiveCommandCount();
        const networkLoad = this.calculateNetworkLoad();
        
        // Exponential increase in congestion with load
        return Math.pow(activeCommands * networkLoad, 1.5);
    }

    updateLatencyHistory() {
        if (this.networkState.latencyHistory.length < 2) return;
        
        // Calculate average latency
        const avgLatency = this.networkState.latencyHistory.reduce((sum, entry) => sum + entry.rtt, 0) 
            / this.networkState.latencyHistory.length;
        
        // Calculate latency variance
        const variance = this.networkState.latencyHistory.reduce((sum, entry) => 
            sum + Math.pow(entry.rtt - avgLatency, 2), 0) / this.networkState.latencyHistory.length;
        
        // Update prediction confidence based on latency stability
        this.networkState.predictionConfidence = Math.max(0, 1 - (variance / this.MAX_ACCEPTABLE_LATENCY));
    }

    checkDesyncConditions() {
        const currentTime = Date.now();
        const avgLatency = this.getAverageLatency();
        
        // Check for desync conditions
        if (avgLatency > this.MAX_ACCEPTABLE_LATENCY) {
            this.networkState.desyncDetected = true;
            this.handleDesync();
        } else if (this.networkState.desyncDetected && 
                  currentTime - this.networkState.lastSyncTime > 5000) {
            // Attempt resync after 5 seconds
            this.attemptResync();
        }
    }

    handleDesync() {
        // Implement desync handling
        // 1. Reduce command complexity
        this.reduceCommandComplexity();
        
        // 2. Increase prediction window
        this.PREDICTION_WINDOW = Math.min(2000, this.PREDICTION_WINDOW * 1.5);
        
        // 3. Notify players
        this.notifyDesync();
    }

    reduceCommandComplexity() {
        // Simplify commands during high latency
        for (const [entityId, entity] of this.entities) {
            const command = entity.getComponent('CommandComponent');
            if (!command || !command.currentOrder) continue;
            
            // Simplify complex orders
            if (command.currentOrder.type === 'FORMATION') {
                command.currentOrder = this.simplifyFormationOrder(command.currentOrder);
            }
            
            // Reduce update frequency
            command.updateInterval = Math.max(200, command.updateInterval * 1.5);
        }
    }

    simplifyFormationOrder(order) {
        // Convert complex formations to simpler ones during high latency
        const simpleFormations = ['LINE', 'WEDGE'];
        if (!simpleFormations.includes(order.formation)) {
            return {
                ...order,
                formation: simpleFormations[Math.floor(Math.random() * simpleFormations.length)]
            };
        }
        return order;
    }

    attemptResync() {
        // Attempt to resync the game state
        const currentTime = Date.now();
        
        // Check if conditions have improved
        if (this.getAverageLatency() < this.MAX_ACCEPTABLE_LATENCY * 0.8) {
            this.networkState.desyncDetected = false;
            this.networkState.lastSyncTime = currentTime;
            
            // Gradually restore normal operation
            this.PREDICTION_WINDOW = Math.max(1000, this.PREDICTION_WINDOW * 0.8);
            this.restoreCommandComplexity();
        }
    }

    restoreCommandComplexity() {
        // Gradually restore command complexity
        for (const [entityId, entity] of this.entities) {
            const command = entity.getComponent('CommandComponent');
            if (!command) continue;
            
            // Restore update frequency
            command.updateInterval = Math.min(100, command.updateInterval * 0.8);
        }
    }

    calculateCommandArrivalTime(entity, order) {
        const target = order.target;
        const path = this.networkState.relayPaths.get(`${entity.id}-${target.id}`);
        
        if (path && path.codecs.length > 0) {
            // Use optimized path through codecs
            return this.calculatePathLatency(path);
        } else {
            // Fall back to direct connection
            const distance = this.calculateDistance(entity.position, target.position);
            const physicalLatency = (distance / this.FIBER_SPEED) * 1000;
            const processingDelay = this.calculateProcessingDelay(entity);
            const dimensionalFactor = this.calculateDimensionalInstability(entity.position);
            
            return physicalLatency + processingDelay + dimensionalFactor;
        }
    }

    getAverageLatency() {
        if (this.networkState.latencyHistory.length === 0) return 0;
        
        return this.networkState.latencyHistory.reduce((sum, entry) => sum + entry.rtt, 0) 
            / this.networkState.latencyHistory.length;
    }

    renderPredictionVisualization(ctx, entity, predictionQuality) {
        // Draw confidence indicator
        const radius = 20 + predictionQuality * 30;
        const alpha = 0.3 + predictionQuality * 0.4;
        
        ctx.beginPath();
        ctx.arc(entity.position.x, entity.position.y, radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(0, 255, 255, ${alpha})`;
        ctx.fill();
        
        // Draw prediction quality text
        ctx.fillStyle = 'rgba(255, 255, 255, 0.8)';
        ctx.font = '12px Arial';
        ctx.textAlign = 'center';
        ctx.fillText(`${Math.round(predictionQuality * 100)}%`, entity.position.x, entity.position.y);

        // Draw network status
        const latency = this.getAverageLatency();
        ctx.fillText(`Latency: ${Math.round(latency)}ms`, entity.position.x, entity.position.y + 15);
        
        // Draw command arrival time
        const command = entity.getComponent('CommandComponent');
        if (command && command.currentOrder) {
            const arrivalTime = this.calculateCommandArrivalTime(entity, command.currentOrder);
            ctx.fillText(`ETA: ${arrivalTime.toFixed(1)}ms`, entity.position.x, entity.position.y + 30);
        }

        // Draw codec status if applicable
        if (this.networkState.activeCodecs.has(entity.id)) {
            const load = this.networkState.codecLoad.get(entity.id);
            const maxLoad = this.calculateMaxCodecLoad(entity);
            ctx.fillStyle = 'rgba(0, 255, 0, 0.8)';
            ctx.fillText(`Codec: ${Math.round(load)}/${maxLoad}`, entity.position.x, entity.position.y + 45);
        }

        // Draw prediction error if significant
        const predictedState = this.predictionState.predictedStates.get(entity.id);
        const serverState = this.predictionState.entityStates.get(entity.id);
        
        if (predictedState && serverState) {
            const positionError = this.calculateDistance(predictedState.position, serverState.position);
            if (positionError > 1) {
                ctx.strokeStyle = 'rgba(255, 0, 0, 0.5)';
                ctx.beginPath();
                ctx.moveTo(predictedState.position.x, predictedState.position.y);
                ctx.lineTo(serverState.position.x, serverState.position.y);
                ctx.stroke();
            }
        }

        // Draw prediction quality indicator
        const quality = this.calculatePredictionQuality(entity);
        const qualityColor = this.getQualityColor(quality);
        
        ctx.fillStyle = qualityColor;
        ctx.beginPath();
        ctx.arc(entity.position.x, entity.position.y, 15, 0, Math.PI * 2);
        ctx.fill();
        
        // Draw dimensional instability effect if significant
        const dimensionalFactor = this.calculateDimensionalInstability(entity.position);
        if (dimensionalFactor > 0.1) {
            ctx.strokeStyle = 'rgba(255, 0, 255, 0.3)';
            ctx.beginPath();
            ctx.arc(entity.position.x, entity.position.y, 20 + dimensionalFactor * 30, 0, Math.PI * 2);
            ctx.stroke();
        }
    }

    updateNetworkTopology(entities) {
        // Clear existing network state
        this.networkNodes.clear();
        
        // Build network topology
        for (const entity of entities) {
            if (entity.hasComponent('ComputroniumComponent')) {
                const core = entity.getComponent('ComputroniumComponent');
                if (core.focusMode === 'C&C') {
                    this.networkNodes.set(entity.id, {
                        position: entity.position,
                        range: this.calculateNodeRange(entity),
                        latency: this.calculateBaseLatency(entity)
                    });
                }
            }
        }
    }

    processCommandHierarchy(entities) {
        // Find all commanders
        const commanders = entities.filter(e => 
            e.hasComponent('RankComponent') && 
            e.getComponent('RankComponent').level >= 3
        );
        
        commanders.forEach(commander => {
            const rank = commander.getComponent('RankComponent');
            const computronium = commander.getComponent('ComputroniumComponent');
            
            // Calculate cache-aware command processing
            const commandLatency = this.calculateCommandLatency(commander, computronium);
            
            const auraRadius = this.calculateAuraRadius(rank.level);
            const auraEffects = this.calculateAuraEffects(rank.level);
            
            // Find units within aura range
            const nearbyUnits = this.findUnitsInRange(commander, auraRadius);
            
            // Apply aura effects with cache consideration
            nearbyUnits.forEach(unit => {
                const rankBonus = this.calculateRankBonus(rank.level, unit);
                this.applyAuraEffects(unit, auraEffects, rankBonus, commandLatency);
            });
            
            // Process command chain with cache awareness
            this.processCommandChain(commander, nearbyUnits, commandLatency);
        });
    }

    calculateAuraRadius(rankLevel) {
        const baseRadius = 100; // Base radius in meters
        const rankMultiplier = {
            5: 3.0, // General
            4: 2.5, // Colonel
            3: 2.0  // Major
        };
        return baseRadius * (rankMultiplier[rankLevel] || 1.0);
    }

    calculateAuraEffects(rankLevel) {
        const effects = {
            accuracy: 0,
            rateOfFire: 0,
            shieldRecharge: 0,
            cooldownReduction: 0,
            forkEfficiency: 0,
            cncResistance: 0
        };

        switch (rankLevel) {
            case 5: // General
                effects.accuracy = 0.2;
                effects.rateOfFire = 0.15;
                effects.shieldRecharge = 0.25;
                effects.cooldownReduction = 0.2;
                effects.forkEfficiency = 0.3;
                effects.cncResistance = 0.4;
                break;
            case 4: // Colonel
                effects.accuracy = 0.15;
                effects.rateOfFire = 0.1;
                effects.shieldRecharge = 0.15;
                effects.cooldownReduction = 0.15;
                effects.forkEfficiency = 0.2;
                effects.cncResistance = 0.25;
                break;
            case 3: // Major
                effects.accuracy = 0.1;
                effects.rateOfFire = 0.05;
                effects.shieldRecharge = 0.1;
                effects.cooldownReduction = 0.1;
                effects.forkEfficiency = 0.1;
                effects.cncResistance = 0.15;
                break;
        }

        return effects;
    }

    processCommandChain(commander, subordinates, commandLatency) {
        const commandScope = this.calculateCommandScope(commander);
        const subordinateCommanders = this.findSubordinateCommanders(commander, commandScope);
        
        // Apply command chain effects
        this.applyCommandChainEffects(commander, subordinateCommanders, commandLatency);
        
        // Process tactical orders
        this.processTacticalOrders(commander, subordinateCommanders, commandLatency);
    }

    calculateCommandScope(commander) {
        const rank = commander.getComponent('RankComponent').level;
        const baseScope = 1000; // Base scope in meters
        return baseScope * (1 + (rank * 0.2));
    }

    findSubordinateCommanders(commander, scope) {
        return this.findUnitsInRange(commander, scope).filter(unit => {
            const unitRank = unit.getComponent('RankComponent')?.level || 0;
            const commanderRank = commander.getComponent('RankComponent').level;
            return unitRank < commanderRank && unitRank >= 2; // Lieutenant or higher
        });
    }

    applyCommandChainEffects(commander, subordinates, commandLatency) {
        const commanderRank = commander.getComponent('RankComponent').level;
        
        subordinates.forEach(subordinate => {
            const subordinateRank = subordinate.getComponent('RankComponent').level;
            const rankDifference = commanderRank - subordinateRank;
            
            // Apply effects based on rank difference and cache latency
            const commandEfficiency = 1 + (rankDifference * 0.1) * (1 - commandLatency / 1000);
            const coordinationBonus = 1 + (rankDifference * 0.05) * (1 - commandLatency / 1000);
            
            // Update subordinate's command component
            if (subordinate.hasComponent('CommandComponent')) {
                const command = subordinate.getComponent('CommandComponent');
                command.efficiency *= commandEfficiency;
                command.coordinationBonus = coordinationBonus;
            }
        });
    }

    processTacticalOrders(commander, subordinates, commandLatency) {
        const strategicDirective = commander.getComponent('CommandComponent')?.currentDirective;
        if (!strategicDirective) return;
        
        subordinates.forEach(subordinate => {
            const tacticalScope = this.calculateTacticalScope(subordinate);
            this.issueTacticalOrders(subordinate, strategicDirective, tacticalScope, commandLatency);
        });
    }

    calculateTacticalScope(subordinate) {
        const rank = subordinate.getComponent('RankComponent').level;
        const baseScope = 500; // Base tactical scope in meters
        return baseScope * (1 + (rank * 0.1));
    }

    issueTacticalOrders(subordinate, strategicDirective, tacticalScope, commandLatency) {
        if (!subordinate.hasComponent('CommandComponent')) return;
        
        const command = subordinate.getComponent('CommandComponent');
        const computronium = subordinate.getComponent('ComputroniumComponent');
        
        // Calculate available tactical options
        const availableOptions = this.getAvailableTacticalOptions(command, computronium);
        
        // Select best tactical option based on strategic directive
        const tacticalOption = this.selectTacticalOption(availableOptions, strategicDirective);
        
        // Create tactical order
        const order = {
            id: Date.now(),
            type: tacticalOption.name,
            strategicDirective: strategicDirective,
            latency: commandLatency,
            chainEfficiency: 1.0,
            effects: this.calculateTacticalEffects(tacticalOption, 1.0),
            assignedUnits: this.assignUnitsToTacticalOrder(subordinate, tacticalOption, tacticalScope)
        };
        
        // Apply tactical order
        this.applyTacticalOrder(subordinate, order);
    }

    getAvailableTacticalOptions(command, computronium) {
        return Object.entries(this.TACTICAL_OPTIONS)
            .filter(([_, option]) => {
                return command.rank === option.requirements.rank &&
                       computronium.coreLevel >= option.requirements.computroniumLevel;
            })
            .map(([key, option]) => ({
                key,
                ...option
            }));
    }

    selectTacticalOption(availableOptions, strategicDirective) {
        // Score each option based on strategic directive
        const scoredOptions = availableOptions.map(option => ({
            ...option,
            score: this.scoreTacticalOption(option, strategicDirective)
        }));
        
        // Select highest scoring option
        return scoredOptions.reduce((best, current) => 
            current.score > best.score ? current : best
        );
    }

    scoreTacticalOption(option, strategicDirective) {
        let score = 0;
        
        // Score based on strategic directive type
        switch (strategicDirective.type) {
            case 'ATTACK':
                score += option.effects.damageBonus ? option.effects.damageBonus * 2 : 0;
                score += option.effects.accuracyBonus ? option.effects.accuracyBonus : 0;
                break;
            case 'DEFEND':
                score += option.effects.shieldBonus ? option.effects.shieldBonus * 2 : 0;
                score += option.effects.armorBonus ? option.effects.armorBonus : 0;
                break;
            case 'MANEUVER':
                score += option.effects.formationBonus ? option.effects.formationBonus * 2 : 0;
                score += option.effects.coordinationBonus ? option.effects.coordinationBonus : 0;
                break;
        }
        
        // Adjust for fork efficiency
        score *= option.effects.forkEfficiency;
        
        return score;
    }

    calculateTacticalEffects(tacticalOption, chainEfficiency) {
        const effects = { ...tacticalOption.effects };
        
        // Adjust effects based on chain efficiency
        Object.keys(effects).forEach(key => {
            if (key !== 'forkEfficiency') {
                effects[key] = 1 + (effects[key] - 1) * chainEfficiency;
            }
        });
        
        return effects;
    }

    assignUnitsToTacticalOrder(commander, tacticalOption, scope) {
        const assignedUnits = [];
        const command = commander.getComponent('CommandComponent');
        
        // Get available units within scope
        const availableUnits = this.findUnitsInRange(commander, scope.radius);
        
        // Sort units by effectiveness for the tactical option
        const sortedUnits = this.sortUnitsByTacticalEffectiveness(availableUnits, tacticalOption);
        
        // Assign units up to max subordinates
        for (let i = 0; i < Math.min(sortedUnits.length, scope.maxSubordinates); i++) {
            assignedUnits.push(sortedUnits[i]);
        }
        
        return assignedUnits;
    }

    sortUnitsByTacticalEffectiveness(units, tacticalOption) {
        return units.sort((a, b) => {
            const scoreA = this.calculateUnitTacticalScore(a, tacticalOption);
            const scoreB = this.calculateUnitTacticalScore(b, tacticalOption);
            return scoreB - scoreA;
        });
    }

    calculateUnitTacticalScore(unit, tacticalOption) {
        let score = 0;
        
        // Score based on unit components and tactical option
        if (unit.hasComponent('CombatComponent')) {
            const combat = unit.getComponent('CombatComponent');
            if (tacticalOption.effects.damageBonus) {
                score += combat.damage * tacticalOption.effects.damageBonus;
            }
            if (tacticalOption.effects.accuracyBonus) {
                score += combat.accuracy * tacticalOption.effects.accuracyBonus;
            }
        }
        
        if (unit.hasComponent('ShieldComponent')) {
            const shield = unit.getComponent('ShieldComponent');
            if (tacticalOption.effects.shieldBonus) {
                score += shield.maxHP * tacticalOption.effects.shieldBonus;
            }
        }
        
        if (unit.hasComponent('ArmorComponent')) {
            const armor = unit.getComponent('ArmorComponent');
            if (tacticalOption.effects.armorBonus) {
                score += armor.value * tacticalOption.effects.armorBonus;
            }
        }
        
        return score;
    }

    applyTacticalOrder(commander, order) {
        const command = commander.getComponent('CommandComponent');
        
        // Update command component with new order
        command.currentOrder = order;
        
        // Apply effects to assigned units
        order.assignedUnits.forEach(unit => {
            this.applyTacticalEffects(unit, order.effects);
        });
        
        // Update computronium allocation
        if (commander.hasComponent('ComputroniumComponent')) {
            const computronium = commander.getComponent('ComputroniumComponent');
            this.allocateComputroniumForTacticalOrder(computronium, order);
        }
    }

    applyTacticalEffects(unit, effects) {
        // Apply combat effects
        if (unit.hasComponent('CombatComponent')) {
            const combat = unit.getComponent('CombatComponent');
            if (effects.damageBonus) combat.damage *= effects.damageBonus;
            if (effects.accuracyBonus) combat.accuracy *= effects.accuracyBonus;
        }
        
        // Apply defensive effects
        if (unit.hasComponent('ShieldComponent')) {
            const shield = unit.getComponent('ShieldComponent');
            if (effects.shieldBonus) shield.maxHP *= effects.shieldBonus;
        }
        
        if (unit.hasComponent('ArmorComponent')) {
            const armor = unit.getComponent('ArmorComponent');
            if (effects.armorBonus) armor.value *= effects.armorBonus;
        }
        
        // Apply formation effects
        if (unit.hasComponent('FormationComponent')) {
            const formation = unit.getComponent('FormationComponent');
            if (effects.formationBonus) formation.efficiency *= effects.formationBonus;
            if (effects.coordinationBonus) formation.coordination *= effects.coordinationBonus;
        }
    }

    allocateComputroniumForTacticalOrder(computronium, order) {
        // Calculate required computronium cycles
        const requiredCycles = this.calculateRequiredComputroniumCycles(order);
        
        // Allocate cycles based on fork efficiency
        const allocatedCycles = Math.floor(requiredCycles * order.effects.forkEfficiency);
        
        // Update computronium allocation
        computronium.allocatedCycles = allocatedCycles;
        computronium.currentFocus = order.type;
    }

    calculateRequiredComputroniumCycles(order) {
        // Base cycles required for the tactical option
        let cycles = 100; // Base cost
        
        // Add cycles for each assigned unit
        cycles += order.assignedUnits.length * 10;
        
        // Add cycles for chain efficiency
        cycles *= (1 + (1 - order.chainEfficiency));
        
        return cycles;
    }

    updateSquadBehaviors(entities, gameContext) {
        const squads = entities.filter(e => e.hasComponent('SquadComponent'));
        
        squads.forEach(squad => {
            const squadData = squad.getComponent('SquadComponent');
            
            // Apply RoE settings
            this.applyRulesOfEngagement(squad, squadData.roe);
            
            // Update squad formation
            this.updateSquadFormation(squad);
            
            // Process support requests
            this.processSupportRequests(squad);
        });
    }

    applyRulesOfEngagement(squad, roe) {
        const units = squad.getComponent('SquadComponent').units;
        
        units.forEach(unit => {
            // Apply aggression level
            switch(roe.aggressionLevel) {
                case 'PASSIVE':
                    unit.engagementRange = unit.maxRange * 0.5;
                    unit.retreatThreshold = 0.7;
                    break;
                case 'DEFENSIVE':
                    unit.engagementRange = unit.maxRange * 0.7;
                    unit.retreatThreshold = 0.5;
                    break;
                case 'AGGRESSIVE':
                    unit.engagementRange = unit.maxRange * 0.9;
                    unit.retreatThreshold = 0.3;
                    break;
                case 'RECKLESS':
                    unit.engagementRange = unit.maxRange;
                    unit.retreatThreshold = 0.1;
                    break;
            }
            
            // Apply PoW usage policy
            this.setPoWUsagePolicy(unit, roe.powUsagePolicy);
            
            // Set core focus mode
            this.setCoreFocusMode(unit, roe.coreFocusMode);
        });
    }

    setPoWUsagePolicy(unit, policy) {
        const computronium = unit.getComponent('ComputroniumComponent');
        if (!computronium) return;
        
        switch(policy) {
            case 'MINIMAL':
                computronium.powContribution = 0.1;
                computronium.powValidationThreshold = 0.3;
                break;
            case 'STANDARD':
                computronium.powContribution = 0.3;
                computronium.powValidationThreshold = 0.5;
                break;
            case 'HIGH_SECURITY':
                computronium.powContribution = 0.5;
                computronium.powValidationThreshold = 0.8;
                break;
        }
    }

    setCoreFocusMode(unit, focusMode) {
        const computronium = unit.getComponent('ComputroniumComponent');
        if (!computronium) return;
        
        computronium.coreFocusMode = focusMode;
        this.allocateComputroniumForks(unit);
    }

    updateSquadFormation(squad) {
        const squadData = squad.getComponent('SquadComponent');
        const leader = this.findSquadLeader(squad);
        
        if (!leader) return;
        
        // Calculate formation based on squad size and composition
        const formation = this.calculateOptimalFormation(squad);
        
        // Update unit positions
        squadData.units.forEach((unit, index) => {
            const targetPosition = this.calculateFormationPosition(formation, leader.position, index);
            this.updateUnitPosition(unit, targetPosition);
        });
    }

    findSquadLeader(squad) {
        const units = squad.getComponent('SquadComponent').units;
        
        // Find highest rank unit
        return units.reduce((leader, unit) => {
            if (!leader) return unit;
            
            const leaderRank = leader.getComponent('RankComponent')?.level || 0;
            const unitRank = unit.getComponent('RankComponent')?.level || 0;
            
            return unitRank > leaderRank ? unit : leader;
        }, null);
    }

    calculateOptimalFormation(squad) {
        const units = squad.getComponent('SquadComponent').units;
        const unitTypes = this.analyzeSquadComposition(units);
        
        // Choose formation based on composition
        if (unitTypes.ranged > unitTypes.melee) {
            return 'LINE'; // Ranged units in line formation
        } else if (unitTypes.melee > unitTypes.ranged) {
            return 'WEDGE'; // Melee units in wedge formation
        } else {
            return 'CIRCLE'; // Mixed units in defensive circle
        }
    }

    analyzeSquadComposition(units) {
        return units.reduce((composition, unit) => {
            if (unit.hasComponent('WeaponComponent')) {
                const weapon = unit.getComponent('WeaponComponent');
                if (weapon.range > 200) {
                    composition.ranged++;
                } else {
                    composition.melee++;
                }
            }
            return composition;
        }, { ranged: 0, melee: 0 });
    }

    calculateFormationPosition(formation, leaderPos, unitIndex) {
        const spacing = 50; // Base spacing between units
        
        switch(formation) {
            case 'LINE':
                return {
                    x: leaderPos.x + (unitIndex * spacing),
                    y: leaderPos.y
                };
            case 'WEDGE':
                const angle = (unitIndex * 30) - 45; // 30 degrees between units
                return {
                    x: leaderPos.x + (Math.cos(angle * Math.PI / 180) * spacing),
                    y: leaderPos.y + (Math.sin(angle * Math.PI / 180) * spacing)
                };
            case 'CIRCLE':
                const circleAngle = (unitIndex * (360 / 8)); // 8 units in circle
                return {
                    x: leaderPos.x + (Math.cos(circleAngle * Math.PI / 180) * spacing),
                    y: leaderPos.y + (Math.sin(circleAngle * Math.PI / 180) * spacing)
                };
            default:
                return leaderPos;
        }
    }

    processSupportRequests(squad) {
        const squadData = squad.getComponent('SquadComponent');
        const requests = squadData.supportRequests || [];
        
        requests.forEach(request => {
            const priority = this.calculateSupportPriority(request);
            const availableUnits = this.findAvailableSupportUnits(squad, request);
            
            if (availableUnits.length > 0) {
                this.assignSupportUnits(availableUnits, request, priority);
            }
        });
        
        // Clear processed requests
        squadData.supportRequests = [];
    }

    calculateSupportPriority(request) {
        let priority = 0;
        
        // Base priority from request type
        switch(request.type) {
            case 'MEDICAL':
                priority += 0.8;
                break;
            case 'COMBAT':
                priority += 0.6;
                break;
            case 'TACTICAL':
                priority += 0.4;
                break;
        }
        
        // Adjust for unit rank
        if (request.requester.hasComponent('RankComponent')) {
            const rank = request.requester.getComponent('RankComponent');
            priority += rank.level * 0.1;
        }
        
        // Adjust for unit health
        if (request.requester.hasComponent('HealthComponent')) {
            const health = request.requester.getComponent('HealthComponent');
            priority += (1 - health.current / health.max) * 0.3;
        }
        
        return priority;
    }

    findAvailableSupportUnits(squad, request) {
        return squad.getComponent('SquadComponent').units.filter(unit => {
            // Check if unit is available for support
            if (unit.hasComponent('CommandComponent')) {
                const command = unit.getComponent('CommandComponent');
                return !command.isBusy && command.canProvideSupport;
            }
            return false;
        });
    }

    assignSupportUnits(units, request, priority) {
        // Sort units by support effectiveness
        units.sort((a, b) => {
            const aEffectiveness = this.calculateSupportEffectiveness(a, request);
            const bEffectiveness = this.calculateSupportEffectiveness(b, request);
            return bEffectiveness - aEffectiveness;
        });
        
        // Assign units based on priority
        const numUnits = Math.ceil(priority * units.length);
        const assignedUnits = units.slice(0, numUnits);
        
        assignedUnits.forEach(unit => {
            this.issueSupportOrder(unit, request);
        });
    }

    calculateSupportEffectiveness(unit, request) {
        let effectiveness = 0;
        
        // Base effectiveness from unit type
        if (unit.hasComponent('UnitTypeComponent')) {
            const type = unit.getComponent('UnitTypeComponent').type;
            switch(request.type) {
                case 'MEDICAL':
                    effectiveness += type === 'SUPPORT' ? 0.8 : 0.2;
                    break;
                case 'COMBAT':
                    effectiveness += type === 'COMBAT' ? 0.8 : 0.3;
                    break;
                case 'TACTICAL':
                    effectiveness += type === 'TACTICAL' ? 0.8 : 0.4;
                    break;
            }
        }
        
        // Adjust for unit rank
        if (unit.hasComponent('RankComponent')) {
            const rank = unit.getComponent('RankComponent');
            effectiveness += rank.level * 0.1;
        }
        
        return effectiveness;
    }

    issueSupportOrder(unit, request) {
        const command = unit.getComponent('CommandComponent');
        if (!command) return;
        
        // Create support order
        const order = {
            type: 'SUPPORT',
            target: request.requester,
            supportType: request.type,
            priority: request.priority
        };
        
        // Add order to unit's command queue
        command.orders.push(order);
        command.isBusy = true;
    }

    updateContextualBehavior(squad, gameContext) {
        const units = squad.getComponent('SquadComponent').units;
        const threats = this.detectThreats(squad, gameContext);
        
        units.forEach(unit => {
            // Update behavior based on threats
            this.updateUnitBehavior(unit, threats);
            
            // Use special abilities if appropriate
            this.useSpecialAbilities(unit, threats);
            
            // Request support if needed
            this.checkSupportNeeds(unit, threats);
        });
    }

    detectThreats(squad, gameContext) {
        const units = squad.getComponent('SquadComponent').units;
        const threats = [];
        
        units.forEach(unit => {
            const sensorRange = unit.getComponent('SensorComponent')?.range || 0;
            const nearbyEnemies = this.findEnemiesInRange(unit, sensorRange);
            
            threats.push(...nearbyEnemies.map(enemy => ({
                entity: enemy,
                threatLevel: this.calculateThreatLevel(enemy, unit),
                distance: this.calculateDistance(unit.position, enemy.position)
            })));
        });
        
        return threats;
    }

    calculateThreatLevel(enemy, unit) {
        let threatLevel = 0;
        
        // Base threat from enemy rank
        if (enemy.hasComponent('RankComponent')) {
            threatLevel += enemy.getComponent('RankComponent').level * 0.2;
        }
        
        // Threat from weapons
        if (enemy.hasComponent('WeaponComponent')) {
            const weapon = enemy.getComponent('WeaponComponent');
            threatLevel += weapon.damage * 0.1;
        }
        
        // Threat from unit type matchups
        threatLevel += this.calculateUnitTypeThreat(enemy, unit);
        
        return threatLevel;
    }

    calculateUnitTypeThreat(enemy, unit) {
        // Implement unit type matchups (e.g., anti-air vs air units)
        const enemyType = enemy.getComponent('UnitTypeComponent')?.type;
        const unitType = unit.getComponent('UnitTypeComponent')?.type;
        
        const matchups = {
            'ANTI_AIR': { 'AIR': 0.5 },
            'ANTI_ARMOR': { 'HEAVY': 0.4 },
            'ANTI_INFANTRY': { 'LIGHT': 0.3 }
        };
        
        return matchups[enemyType]?.[unitType] || 0;
    }

    calculateDimensionalInstability(position) {
        // Check for dimensional rifts or unstable regions
        const instability = this.getDimensionalInstabilityAtPosition(position);
        return instability * this.lightSpeedDelay;
    }

    calculateProcessingDelay(entity) {
        const computronium = entity.getComponent('ComputroniumComponent');
        if (!computronium) return 0;

        return computronium.cyclesPerTick / computronium.coreLevel;
    }

    isCommandStale(entity, command) {
        if (!command || !command.currentOrder) return false;

        const orderAge = Date.now() - command.lastOrderTime;
        const maxAge = this.calculateMaxOrderAge(entity, command);
        
        return orderAge > maxAge;
    }

    calculateMaxOrderAge(entity, command) {
        const baseAge = 5000; // 5 seconds base
        const rankMultiplier = this.rankBonuses[command.rank] || 1.0;
        const computronium = entity.getComponent('ComputroniumComponent');
        const coreMultiplier = computronium ? (1 / computronium.coreLevel) : 1.0;
        
        return baseAge * rankMultiplier * coreMultiplier;
    }

    calculatePredictionQuality(entity) {
        const computronium = entity.getComponent('ComputroniumComponent');
        if (!computronium) return 0.5;
        
        // Base quality from core level
        let quality = 0.5 + (computronium.coreLevel * 0.1);
        
        // Adjust for available forks
        const availableForks = computronium.availableForks;
        const requiredForks = this.calculateRequiredForks(entity);
        quality *= Math.min(availableForks / requiredForks, 1);
        
        // Adjust for dimensional instability
        const dimensionalFactor = this.calculateDimensionalInstability(entity.position);
        quality *= (1 - dimensionalFactor);
        
        return Math.min(Math.max(quality, 0), 1);
    }

    calculateRequiredForks(entity) {
        const command = entity.getComponent('CommandComponent');
        if (!command || !command.currentOrder) return 1;
        
        // Base forks required
        let requiredForks = 1;
        
        // Additional forks based on order complexity
        switch (command.currentOrder.type) {
            case 'MOVE':
                requiredForks += 1;
                break;
            case 'ATTACK':
                requiredForks += 2;
                break;
            case 'FORMATION':
                requiredForks += 3;
                break;
        }
        
        // Additional forks for high-rank units
        const rank = entity.getComponent('RankComponent');
        if (rank) {
            requiredForks += rank.level;
        }
        
        return requiredForks;
    }

    predictEntityState(entity, deltaTime) {
        const command = entity.getComponent('CommandComponent');
        const order = command.currentOrder;
        const computronium = entity.getComponent('ComputroniumComponent');
        
        // Create predicted state
        const predictedState = {
            position: { ...entity.position },
            velocity: { ...entity.velocity },
            rotation: entity.rotation,
            timestamp: Date.now(),
            predictionQuality: this.calculatePredictionQuality(entity)
        };
        
        // Calculate dimensional instability factor
        const dimensionalFactor = this.calculateDimensionalInstability(entity.position);
        
        // Adjust prediction based on Computronium core level
        const predictionSteps = computronium ? Math.min(1 + computronium.coreLevel, this.predictionState.maxPredictionSteps) : 1;
        const predictionWindow = computronium ? 1000 + (computronium.coreLevel * 500) : 1000;
        
        // Predict based on current order
        switch (order.type) {
            case 'MOVE':
                this.predictMovementState(predictedState, order, deltaTime, predictionSteps, predictionWindow);
                break;
            case 'ATTACK':
                this.predictAttackState(predictedState, order, deltaTime, predictionSteps, predictionWindow);
                break;
            case 'FORMATION':
                this.predictFormationState(predictedState, order, deltaTime, predictionSteps, predictionWindow);
                break;
        }
        
        // Store prediction
        this.predictionState.predictedStates.set(entity.id, predictedState);
        
        // Apply prediction to entity
        this.applyPredictedState(entity, predictedState);
    }

    predictMovementState(state, order, deltaTime, predictionSteps, predictionWindow) {
        const targetPos = order.target.position;
        const direction = this.calculateDirection(state.position, targetPos);
        const distance = this.calculateDistance(state.position, targetPos);
        
        // Predict multiple steps ahead
        for (let step = 0; step < predictionSteps; step++) {
            const stepDelta = deltaTime * (step + 1);
            const speed = this.calculateMovementSpeed(order);
            const moveDistance = speed * stepDelta;
            
            if (distance > moveDistance) {
                state.position.x += direction.x * moveDistance;
                state.position.y += direction.y * moveDistance;
            } else {
                state.position = { ...targetPos };
                break;
            }
        }
        
        // Predict velocity
        state.velocity = {
            x: direction.x * speed,
            y: direction.y * speed
        };
        
        // Store prediction confidence
        state.predictionConfidence = this.calculatePredictionConfidence(state, order, predictionWindow);
    }

    predictAttackState(state, order, deltaTime, predictionSteps, predictionWindow) {
        // Predict movement to attack position
        this.predictMovementState(state, order, deltaTime, predictionSteps, predictionWindow);
        
        // Predict attack animation/effects
        const attackProgress = (Date.now() - order.startTime) / order.duration;
        state.attackProgress = Math.min(attackProgress, 1);
    }

    predictFormationState(state, order, deltaTime, predictionSteps, predictionWindow) {
        // Predict movement to formation position
        this.predictMovementState(state, order, deltaTime, predictionSteps, predictionWindow);
        
        // Predict formation alignment
        const targetRotation = this.calculateFormationRotation(order);
        state.rotation = this.lerpRotation(state.rotation, targetRotation, deltaTime);
    }

    processServerReconciliation(entities) {
        const currentTime = Date.now();
        
        // Process reconciliation queue
        while (this.predictionState.reconciliationQueue.length > 0) {
            const serverState = this.predictionState.reconciliationQueue[0];
            
            // Skip if too old
            if (currentTime - serverState.timestamp > this.predictionState.maxReconciliationDelay) {
                this.predictionState.reconciliationQueue.shift();
                continue;
            }
            
            // Find corresponding entity
            const entity = entities.find(e => e.id === serverState.entityId);
            if (!entity) {
                this.predictionState.reconciliationQueue.shift();
                continue;
            }
            
            // Reconcile state
            this.reconcileEntityState(entity, serverState);
            
            // Remove processed state
            this.predictionState.reconciliationQueue.shift();
        }
    }

    reconcileEntityState(entity, serverState) {
        // Get predicted state
        const predictedState = this.predictionState.predictedStates.get(entity.id);
        if (!predictedState) return;
        
        // Calculate error
        const positionError = this.calculateDistance(predictedState.position, serverState.position);
        const rotationError = Math.abs(predictedState.rotation - serverState.rotation);
        
        // If error is significant, snap to server state
        if (positionError > 1 || rotationError > 0.1) {
            this.applyServerState(entity, serverState);
        } else {
            // Smoothly interpolate to server state
            this.interpolateToServerState(entity, serverState);
        }
    }

    applyServerState(entity, serverState) {
        // Directly apply server state
        entity.position = { ...serverState.position };
        entity.velocity = { ...serverState.velocity };
        entity.rotation = serverState.rotation;
        
        // Update command component if needed
        const command = entity.getComponent('CommandComponent');
        if (command && serverState.commandState) {
            command.currentOrder = { ...serverState.commandState };
        }
    }

    interpolateToServerState(entity, serverState) {
        const interpolationFactor = 0.3; // Adjust for smoothness
        
        // Interpolate position
        entity.position.x = this.lerp(entity.position.x, serverState.position.x, interpolationFactor);
        entity.position.y = this.lerp(entity.position.y, serverState.position.y, interpolationFactor);
        
        // Interpolate velocity
        entity.velocity.x = this.lerp(entity.velocity.x, serverState.velocity.x, interpolationFactor);
        entity.velocity.y = this.lerp(entity.velocity.y, serverState.velocity.y, interpolationFactor);
        
        // Interpolate rotation
        entity.rotation = this.lerpRotation(entity.rotation, serverState.rotation, interpolationFactor);
    }

    storeEntityState(entity) {
        const state = {
            entityId: entity.id,
            position: { ...entity.position },
            velocity: { ...entity.velocity },
            rotation: entity.rotation,
            timestamp: Date.now(),
            commandState: entity.getComponent('CommandComponent')?.currentOrder
        };
        
        this.predictionState.entityStates.set(entity.id, state);
    }

    applyPredictedState(entity, predictedState) {
        // Apply predicted position
        entity.position = { ...predictedState.position };
        
        // Apply predicted velocity
        entity.velocity = { ...predictedState.velocity };
        
        // Apply predicted rotation
        entity.rotation = predictedState.rotation;
    }

    lerp(start, end, factor) {
        return start + (end - start) * factor;
    }

    lerpRotation(start, end, factor) {
        let diff = end - start;
        
        // Normalize angle difference
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        
        return start + diff * factor;
    }

    calculatePredictionSteps(computronium) {
        if (!computronium) return 1;
        
        // Base steps from core level
        const baseSteps = 1 + computronium.coreLevel;
        
        // Additional steps based on available forks
        const forkBonus = Math.floor(computronium.availableForks / 2);
        
        return Math.min(baseSteps + forkBonus, this.predictionState.maxPredictionSteps);
    }

    calculatePredictionWindow(computronium, dimensionalFactor) {
        if (!computronium) return 1000; // Base window without Computronium
        
        // Base window from core level
        const baseWindow = 1000 + (computronium.coreLevel * 500);
        
        // Adjust for dimensional instability
        const adjustedWindow = baseWindow * (1 - dimensionalFactor);
        
        return Math.min(adjustedWindow, 5000); // Cap at 5 seconds
    }

    calculatePredictionConfidence(state, order, predictionWindow) {
        // Base confidence
        let confidence = 1.0;
        
        // Reduce confidence based on prediction window
        confidence *= (1 - (predictionWindow / 5000));
        
        // Reduce confidence for complex orders
        if (order.type === 'FORMATION' || order.type === 'ATTACK') {
            confidence *= 0.8;
        }
        
        // Reduce confidence for high-speed movement
        const speed = Math.sqrt(state.velocity.x * state.velocity.x + state.velocity.y * state.velocity.y);
        if (speed > 10) {
            confidence *= (1 - (speed / 50));
        }
        
        return Math.max(confidence, 0.1);
    }

    getQualityColor(quality) {
        // Interpolate from red (low) to green (high)
        const r = Math.floor(255 * (1 - quality));
        const g = Math.floor(255 * quality);
        return `rgba(${r}, ${g}, 0, 0.7)`;
    }

    getPatternColor(pattern) {
        const colors = {
            linearity: 'rgba(255, 255, 255, 0.8)',
            circularity: 'rgba(255, 165, 0, 0.8)',
            zigzagness: 'rgba(255, 0, 0, 0.8)',
            stalking: 'rgba(0, 255, 0, 0.8)',
            flanking: 'rgba(0, 0, 255, 0.8)',
            pincer: 'rgba(128, 0, 128, 0.8)',
            feint: 'rgba(255, 255, 0, 0.8)',
            ambush: 'rgba(255, 0, 255, 0.8)',
            hitAndRun: 'rgba(0, 255, 255, 0.8)',
            defensive: 'rgba(128, 128, 128, 0.8)'
        };
        return colors[pattern] || 'rgba(255, 255, 255, 0.8)';
    }

    getPatternIcon(pattern) {
        const icons = {
            linearity: '→',
            circularity: '○',
            zigzagness: '⚡',
            stalking: '👣',
            flanking: '↗',
            pincer: '↔',
            feint: '↪',
            ambush: '⚔',
            hitAndRun: '↩',
            defensive: '🛡'
        };
        return icons[pattern] || '?';
    }

    updateCodecSystem() {
        // Update active codecs
        for (const [codecId, codec] of this.networkState.activeCodecs) {
            // Check if codec is still valid
            if (!this.isValidCodec(codec)) {
                this.removeCodec(codecId);
                continue;
            }

            // Update codec load
            this.updateCodecLoad(codec);
            
            // Optimize relay paths
            this.optimizeRelayPaths(codec);
        }
    }

    isValidCodec(codec) {
        // Check if codec meets requirements
        const computronium = codec.getComponent('ComputroniumComponent');
        if (!computronium) return false;

        // Must have sufficient computational resources
        return computronium.coreLevel >= 3 && 
               computronium.availableForks >= 2 &&
               this.getAverageLatency(codec) < 100; // Low latency requirement
    }

    updateCodecLoad(codec) {
        const currentLoad = this.networkState.codecLoad.get(codec.id) || 0;
        const maxLoad = this.calculateMaxCodecLoad(codec);
        
        // Update load based on active connections
        const activeConnections = this.getActiveCodecConnections(codec);
        const newLoad = this.calculateCodecLoad(activeConnections);
        
        this.networkState.codecLoad.set(codec.id, Math.min(newLoad, maxLoad));
    }

    calculateMaxCodecLoad(codec) {
        const computronium = codec.getComponent('ComputroniumComponent');
        return computronium.coreLevel * 10; // Each core level can handle 10 connections
    }

    calculateCodecLoad(connections) {
        return connections.reduce((load, conn) => {
            // Calculate load based on connection type and data volume
            const baseLoad = 1;
            const dataLoad = conn.dataVolume / 1000; // Normalize data volume
            const complexityLoad = conn.complexity * 0.5; // Complexity factor
            
            return load + baseLoad + dataLoad + complexityLoad;
        }, 0);
    }

    optimizeRelayPaths(codec) {
        // Find optimal paths through this codec
        const connections = this.getActiveCodecConnections(codec);
        
        for (const conn of connections) {
            const currentPath = this.networkState.relayPaths.get(conn.id);
            const newPath = this.findOptimalPath(conn, codec);
            
            if (this.isBetterPath(newPath, currentPath)) {
                this.networkState.relayPaths.set(conn.id, newPath);
                this.updateConnectionPath(conn, newPath);
            }
        }
    }

    findOptimalPath(connection, codec) {
        const source = connection.source;
        const target = connection.target;
        
        // Calculate direct path
        const directPath = {
            latency: this.calculateDirectLatency(source, target),
            hops: 0,
            codecs: []
        };
        
        // Calculate path through codec
        const codecPath = {
            latency: this.calculateCodecLatency(source, codec, target),
            hops: 1,
            codecs: [codec]
        };
        
        // Return better path
        return codecPath.latency < directPath.latency ? codecPath : directPath;
    }

    calculateCodecLatency(source, codec, target) {
        // Calculate latency through codec
        const sourceToCodec = this.calculateDirectLatency(source, codec);
        const codecToTarget = this.calculateDirectLatency(codec, target);
        const processingDelay = this.calculateProcessingDelay(codec);
        
        return sourceToCodec + codecToTarget + processingDelay;
    }

    registerCodec(entity) {
        if (!this.isValidCodec(entity)) return false;
        
        // Register as codec
        this.networkState.activeCodecs.set(entity.id, entity);
        this.networkState.codecLoad.set(entity.id, 0);
        
        // Initialize codec state
        const codecState = {
            connections: new Set(),
            dataVolume: 0,
            lastOptimization: Date.now()
        };
        
        entity.codecState = codecState;
        return true;
    }

    removeCodec(codecId) {
        // Remove codec and update affected connections
        const codec = this.networkState.activeCodecs.get(codecId);
        if (!codec) return;
        
        // Update affected connections
        const connections = this.getActiveCodecConnections(codec);
        for (const conn of connections) {
            this.findAlternativePath(conn);
        }
        
        // Remove codec
        this.networkState.activeCodecs.delete(codecId);
        this.networkState.codecLoad.delete(codecId);
    }

    findAlternativePath(connection) {
        // Find alternative path when codec becomes unavailable
        const availableCodecs = Array.from(this.networkState.activeCodecs.values())
            .filter(codec => this.isValidCodec(codec) && 
                           this.networkState.codecLoad.get(codec.id) < this.calculateMaxCodecLoad(codec));
        
        // Try to find best alternative codec
        let bestPath = null;
        for (const codec of availableCodecs) {
            const path = this.findOptimalPath(connection, codec);
            if (this.isBetterPath(path, bestPath)) {
                bestPath = path;
            }
        }
        
        if (bestPath) {
            this.networkState.relayPaths.set(connection.id, bestPath);
            this.updateConnectionPath(connection, bestPath);
        } else {
            // Fall back to direct connection
            this.networkState.relayPaths.delete(connection.id);
            this.updateConnectionPath(connection, null);
        }
    }

    calculatePathLatency(path) {
        let totalLatency = 0;
        
        // Add latency for each hop
        for (let i = 0; i < path.codecs.length; i++) {
            const codec = path.codecs[i];
            const next = i < path.codecs.length - 1 ? path.codecs[i + 1] : path.target;
            
            totalLatency += this.calculateDirectLatency(codec, next);
            totalLatency += this.calculateProcessingDelay(codec);
        }
        
        return totalLatency;
    }

    calculateDirectLatency(source, target) {
        const distance = this.calculateDistance(source, target);
        const latency = distance * this.lightSpeedDelay;
        return latency;
    }

    getActiveCodecConnections(codec) {
        const connections = [];
        for (const [connId, path] of this.networkState.relayPaths) {
            if (path.codecs.includes(codec)) {
                connections.push({ id: connId, source: path.source, target: path.target });
            }
        }
        return connections;
    }

    isBetterPath(path1, path2) {
        if (!path1) return true;
        if (!path2) return false;
        return path1.latency < path2.latency;
    }

    updateConnectionPath(connection, path) {
        // Implementation of updateConnectionPath method
    }

    processClientPrediction(entities) {
        const currentTime = Date.now();
        
        // Predict ahead for each entity
        for (const entity of entities) {
            const command = entity.getComponent('CommandComponent');
            if (!command || !command.currentOrder) continue;
            
            // Store current state for reconciliation
            this.storeEntityState(entity);
            
            // Predict future states
            this.predictEntityState(entity, currentTime - this.predictionState.lastProcessedInput);
        }
        
        // Update last processed input
        this.predictionState.lastProcessedInput = currentTime;
    }

    renderPredictedPath(ctx, entity, prediction) {
        if (!prediction) return;
        
        // Draw predicted path
        ctx.beginPath();
        ctx.moveTo(entity.position.x, entity.position.y);
        ctx.lineTo(prediction.x, prediction.y);
        ctx.strokeStyle = 'rgba(0, 255, 255, 0.6)';
        ctx.lineWidth = 2;
        ctx.stroke();
        
        // Draw prediction point
        ctx.beginPath();
        ctx.arc(prediction.x, prediction.y, 5, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(0, 255, 255, 0.8)';
        ctx.fill();
    }

    renderTacticalPatterns(ctx, entity, patterns) {
        const patternStrength = patterns.confidenceScores[patterns.dominantPattern] || 0;
        
        // Draw pattern strength indicator
        const radius = 15 + patternStrength * 25;
        ctx.beginPath();
        ctx.arc(entity.position.x, entity.position.y, radius, 0, Math.PI * 2);
        ctx.strokeStyle = this.getPatternColor(patterns.dominantPattern);
        ctx.lineWidth = 2;
        ctx.stroke();
        
        // Draw pattern type icon
        ctx.fillStyle = this.getPatternColor(patterns.dominantPattern);
        ctx.font = '14px Arial';
        ctx.textAlign = 'center';
        ctx.fillText(this.getPatternIcon(patterns.dominantPattern), entity.position.x, entity.position.y + 5);
    }

    renderMovementHistory(ctx, entity) {
        const history = entity.getComponent('CommandComponent').orderHistory;
        if (!history || history.length < 2) return;
        
        // Draw movement path
        ctx.beginPath();
        ctx.moveTo(history[0].position.x, history[0].position.y);
        
        for (let i = 1; i < history.length; i++) {
            ctx.lineTo(history[i].position.x, history[i].position.y);
        }
        
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
        ctx.lineWidth = 1;
        ctx.stroke();
        
        // Draw order points
        history.forEach((order, index) => {
            const alpha = 0.3 + (index / history.length) * 0.7;
            ctx.beginPath();
            ctx.arc(order.position.x, order.position.y, 3, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`;
            ctx.fill();
        });
    }

    renderTerrainAnalysis(ctx, entity, terrainAnalysis) {
        // Draw terrain features
        if (terrainAnalysis.obstacles) {
            terrainAnalysis.obstacles.forEach(obstacle => {
                ctx.beginPath();
                ctx.arc(obstacle.x, obstacle.y, 10, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255, 0, 0, 0.2)';
                ctx.fill();
            });
        }
        
        if (terrainAnalysis.paths) {
            terrainAnalysis.paths.forEach(path => {
                ctx.beginPath();
                ctx.arc(path.x, path.y, 5, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(0, 255, 0, 0.2)';
                ctx.fill();
            });
        }
        
        if (terrainAnalysis.hazards) {
            terrainAnalysis.hazards.forEach(hazard => {
                ctx.beginPath();
                ctx.arc(hazard.x, hazard.y, 8, 0, Math.PI * 2);
                ctx.fillStyle = 'rgba(255, 0, 255, 0.2)';
                ctx.fill();
            });
        }
    }

    renderNetworkEffects(ctx, entity, networkEffects) {
        // Draw network influence
        const radius = 30 + networkEffects.influence * 20;
        ctx.beginPath();
        ctx.arc(entity.position.x, entity.position.y, radius, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(0, 255, 0, ${networkEffects.influence})`;
        ctx.lineWidth = 2;
        ctx.stroke();
    }

    updateCacheStates(entities) {
        // Update cache coherence first
        this.updateCacheCoherence(entities);
        
        // Update individual cache states
        entities.forEach(entity => {
            if (entity.hasComponent('ComputroniumComponent')) {
                const computronium = entity.getComponent('ComputroniumComponent');
                this.updateEntityCacheState(computronium);
                this.updatePrefetchStrategy(computronium, entity);
                this.updateCacheMetrics(computronium);
            }
        });
        
        // Update cache-aware unit coordination
        this.updateCacheAwareCoordination(entities);
    }

    updateCacheCoherence(entities) {
        const computroniumEntities = entities.filter(e => e.hasComponent('ComputroniumComponent'));
        
        // Build cache line ownership map and message queue
        const cacheLineOwners = new Map();
        const messageQueue = new Map();
        
        computroniumEntities.forEach(entity => {
            const computronium = entity.getComponent('ComputroniumComponent');
            this.updateCacheLineOwnership(computronium, cacheLineOwners);
            this.processMESIMessages(computronium, messageQueue);
        });
        
        // Update coherence states with message processing
        computroniumEntities.forEach(entity => {
            const computronium = entity.getComponent('ComputroniumComponent');
            this.updateCoherenceStates(computronium, cacheLineOwners, messageQueue);
        });
    }

    updateCacheLineOwnership(computronium, cacheLineOwners) {
        if (!computronium.cacheState) return;
        
        // Update L1 cache line ownership
        for (const [line, entry] of computronium.cacheState.l1Cache.entries()) {
            if (entry.state === this.CACHE_COHERENCE_STATES.MODIFIED) {
                cacheLineOwners.set(line, computronium);
            }
        }
    }

    updateCoherenceStates(computronium, cacheLineOwners, messageQueue) {
        if (!computronium.cacheState) return;
        
        // Update L1 cache coherence states
        for (const [line, entry] of computronium.cacheState.l1Cache.entries()) {
            const owner = cacheLineOwners.get(line);
            if (owner && owner !== computronium) {
                // Another cache owns this line
                entry.state = this.CACHE_COHERENCE_STATES.SHARED;
            } else if (!owner) {
                // No owner, mark as exclusive
                entry.state = this.CACHE_COHERENCE_STATES.EXCLUSIVE;
            }
        }

        // Process pending messages for this cache
        const messages = messageQueue.get(computronium) || [];
        messages.forEach(message => {
            switch (message.type) {
                case this.MESI_MESSAGES.READ_MISS:
                    this.handleReadMiss(computronium, message);
                    break;
                case this.MESI_MESSAGES.WRITE_MISS:
                    this.handleWriteMiss(computronium, message);
                    break;
                case this.MESI_MESSAGES.INVALIDATE:
                    this.handleInvalidate(computronium, message);
                    break;
            }
        });
        
        // Clear processed messages
        messageQueue.set(computronium, []);
    }

    processMESIMessages(computronium, messageQueue) {
        if (!computronium.cacheState) return;
        
        // Process pending messages for this cache
        const messages = messageQueue.get(computronium) || [];
        messages.forEach(message => {
            switch (message.type) {
                case this.MESI_MESSAGES.READ_MISS:
                    this.handleReadMiss(computronium, message);
                    break;
                case this.MESI_MESSAGES.WRITE_MISS:
                    this.handleWriteMiss(computronium, message);
                    break;
                case this.MESI_MESSAGES.INVALIDATE:
                    this.handleInvalidate(computronium, message);
                    break;
            }
        });
        
        // Clear processed messages
        messageQueue.set(computronium, []);
    }

    handleReadMiss(computronium, message) {
        const cacheState = computronium.cacheState;
        const line = message.cacheLine;
        
        // Check if any other cache has the line in Modified state
        const owner = this.findCacheLineOwner(line);
        if (owner && owner !== computronium) {
            // Request data from owner
            this.sendMessage(owner, {
                type: this.MESI_MESSAGES.READ_RESPONSE,
                cacheLine: line,
                data: owner.cacheState.l1Cache.get(line).data
            });
            
            // Update state to Shared
            cacheState.l1Cache.set(line, {
                ...cacheState.l1Cache.get(line),
                state: this.CACHE_COHERENCE_STATES.SHARED
            });
        } else {
            // No owner, fetch from memory and set to Exclusive
            this.fetchFromMemory(computronium, line);
            cacheState.l1Cache.set(line, {
                ...cacheState.l1Cache.get(line),
                state: this.CACHE_COHERENCE_STATES.EXCLUSIVE
            });
        }
    }

    handleWriteMiss(computronium, message) {
        const cacheState = computronium.cacheState;
        const line = message.cacheLine;
        
        // Invalidate other caches
        this.broadcastInvalidate(line);
        
        // Fetch from memory and set to Modified
        this.fetchFromMemory(computronium, line);
        cacheState.l1Cache.set(line, {
            ...cacheState.l1Cache.get(line),
            state: this.CACHE_COHERENCE_STATES.MODIFIED
        });
    }

    handleInvalidate(computronium, message) {
        const cacheState = computronium.cacheState;
        const line = message.cacheLine;
        
        if (cacheState.l1Cache.has(line)) {
            const entry = cacheState.l1Cache.get(line);
            if (entry.state === this.CACHE_COHERENCE_STATES.MODIFIED) {
                // Write back to memory before invalidating
                this.writeBackToMemory(computronium, line, entry.data);
            }
            cacheState.l1Cache.delete(line);
        }
    }

    updatePrefetchStrategy(computronium, entity) {
        if (!computronium.cacheState) return;
        
        // Determine prefetch strategy based on unit behavior and context
        const strategy = this.determinePrefetchStrategy(entity);
        computronium.cacheState.prefetchStrategy = strategy;
        
        // Analyze access patterns and predict future accesses
        const patterns = this.analyzeAccessPatterns(computronium);
        
        // Execute prefetch based on strategy and patterns
        this.executePrefetch(computronium, entity, strategy, patterns);
    }

    analyzeAccessPatterns(computronium) {
        const patterns = new Map();
        const accessHistory = computronium.cacheState.accessHistory || [];
        
        // Enhanced pattern analysis
        this.analyzeSequentialPattern(accessHistory, patterns);
        this.analyzeStridedPattern(accessHistory, patterns);
        this.analyzeLoopPattern(accessHistory, patterns);
        this.analyzeBranchPattern(accessHistory, patterns);
        this.analyzeContextSwitchPattern(accessHistory, patterns);
        
        return patterns;
    }

    analyzeSequentialPattern(history, patterns) {
        let sequentialCount = 0;
        for (let i = 1; i < history.length; i++) {
            if (history[i].address === history[i-1].address + 1) {
                sequentialCount++;
            } else {
                if (sequentialCount >= 3) {
                    patterns.set(this.PREFETCH_PATTERNS.SEQUENTIAL, {
                        confidence: Math.min(0.9, sequentialCount / 10),
                        stride: 1
                    });
                }
                sequentialCount = 0;
            }
        }
    }

    analyzeStridedPattern(history, patterns) {
        const strides = new Map();
        for (let i = 2; i < history.length; i++) {
            const stride = history[i].address - history[i-1].address;
            if (stride === history[i-1].address - history[i-2].address) {
                strides.set(stride, (strides.get(stride) || 0) + 1);
            }
        }
        
        // Find most common stride
        let maxStride = 0;
        let maxCount = 0;
        for (const [stride, count] of strides.entries()) {
            if (count > maxCount) {
                maxStride = stride;
                maxCount = count;
            }
        }
        
        if (maxCount >= 3) {
            patterns.set(this.PREFETCH_PATTERNS.STRIDED, {
                confidence: Math.min(0.8, maxCount / 10),
                stride: maxStride
            });
        }
    }

    executePrefetch(computronium, entity, strategy, patterns) {
        const cacheState = computronium.cacheState;
        if (!cacheState) return;
        
        // Get predicted data access patterns
        const predictedAccesses = this.predictDataAccessPatterns(entity, patterns);
        
        // Execute prefetch based on strategy and patterns
        switch (strategy) {
            case this.PREFETCH_STRATEGIES.AGGRESSIVE:
                this.aggressivePrefetch(cacheState, predictedAccesses, patterns);
                break;
            case this.PREFETCH_STRATEGIES.MODERATE:
                this.moderatePrefetch(cacheState, predictedAccesses, patterns);
                break;
            case this.PREFETCH_STRATEGIES.CONSERVATIVE:
                this.conservativePrefetch(cacheState, predictedAccesses, patterns);
                break;
        }
    }

    predictDataAccessPatterns(entity, patterns) {
        const predictions = [];
        const behavior = entity.getComponent('BehaviorComponent');
        const command = entity.getComponent('CommandComponent');
        
        if (!behavior || !command) return predictions;
        
        // Predict based on current behavior, command, and access patterns
        if (behavior.isAggressive) {
            predictions.push({
                type: 'COMBAT_DATA',
                confidence: 0.9,
                size: 1024,
                pattern: patterns.get(this.PREFETCH_PATTERNS.SEQUENTIAL)
            });
        }
        
        if (command.currentDirective) {
            predictions.push({
                type: 'COMMAND_DATA',
                confidence: 0.8,
                size: 512,
                pattern: patterns.get(this.PREFETCH_PATTERNS.STRIDED)
            });
        }
        
        return predictions;
    }

    aggressivePrefetch(cacheState, predictedAccesses, patterns) {
        // Prefetch multiple levels ahead with high confidence
        predictedAccesses.forEach(prediction => {
            if (prediction.confidence > 0.7) {
                this.prefetchToCache(cacheState.l1Cache, prediction);
                this.prefetchToCache(cacheState.l2Cache, prediction);
                this.prefetchToCache(cacheState.l3Cache, prediction);
            }
        });
    }

    moderatePrefetch(cacheState, predictedAccesses, patterns) {
        // Prefetch one level ahead with moderate confidence
        predictedAccesses.forEach(prediction => {
            if (prediction.confidence > 0.5) {
                this.prefetchToCache(cacheState.l2Cache, prediction);
            }
        });
    }

    conservativePrefetch(cacheState, predictedAccesses, patterns) {
        // Only prefetch with high confidence
        predictedAccesses.forEach(prediction => {
            if (prediction.confidence > 0.9) {
                this.prefetchToCache(cacheState.l3Cache, prediction);
            }
        });
    }

    prefetchToCache(cacheLevel, prediction) {
        const entry = {
            type: prediction.type,
            size: prediction.size,
            lastAccess: Date.now(),
            accessFrequency: 1.0,
            isStale: false,
            state: this.CACHE_COHERENCE_STATES.EXCLUSIVE
        };
        
        cacheLevel.set(prediction.type, entry);
    }

    calculateCommandLatency(commander, computronium) {
        if (!computronium) return 0;
        
        // Calculate base command processing latency with coherence consideration
        const baseLatency = this.calculateCacheLatencyWithCoherence(computronium, 1024);
        
        // Add latency for command validation
        const validationLatency = this.calculateCacheLatencyWithCoherence(computronium, 512);
        
        // Add latency for command distribution
        const distributionLatency = this.calculateCacheLatencyWithCoherence(computronium, 256);
        
        return baseLatency + validationLatency + distributionLatency;
    }

    calculateCacheLatencyWithCoherence(computronium, dataSize) {
        const cacheState = computronium.cacheState;
        let latency = 0;
        
        // Check L1 cache with coherence consideration
        if (this.isInCacheWithCoherence(cacheState.l1Cache, dataSize)) {
            latency += this.CACHE_LATENCIES.L1_CACHE;
            cacheState.cacheHits++;
            return latency;
        }
        
        // Check L2 cache with coherence consideration
        if (this.isInCacheWithCoherence(cacheState.l2Cache, dataSize)) {
            latency += this.CACHE_LATENCIES.L1_CACHE + this.CACHE_LATENCIES.L2_CACHE;
            cacheState.cacheHits++;
            return latency;
        }
        
        // Check L3 cache with coherence consideration
        if (this.isInCacheWithCoherence(cacheState.l3Cache, dataSize)) {
            latency += this.CACHE_LATENCIES.L1_CACHE + this.CACHE_LATENCIES.L2_CACHE + this.CACHE_LATENCIES.L3_CACHE;
            cacheState.cacheHits++;
            return latency;
        }
        
        // Cache miss - must go to main memory
        cacheState.cacheMisses++;
        return this.CACHE_LATENCIES.L1_CACHE + this.CACHE_LATENCIES.L2_CACHE + 
               this.CACHE_LATENCIES.L3_CACHE + this.CACHE_LATENCIES.MAIN_MEMORY;
    }

    isInCacheWithCoherence(cacheLevel, dataSize) {
        for (const [key, entry] of cacheLevel.entries()) {
            if (entry.size >= dataSize && !entry.isStale) {
                // Check coherence state
                if (entry.state === this.CACHE_COHERENCE_STATES.MODIFIED ||
                    entry.state === this.CACHE_COHERENCE_STATES.EXCLUSIVE) {
                    return true;
                } else if (entry.state === this.CACHE_COHERENCE_STATES.SHARED) {
                    // Shared state requires coherence check
                    return this.checkCoherence(key);
                }
            }
        }
        return false;
    }

    checkCoherence(cacheLine) {
        // Implement MESI protocol check
        return true; // Simplified for now
    }

    renderCacheVisualization(ctx, entity) {
        if (!entity.hasComponent('ComputroniumComponent')) return;
        
        const computronium = entity.getComponent('ComputroniumComponent');
        if (!computronium.cacheState) return;
        
        const position = entity.position;
        const radius = 20;
        
        // Draw cache hit/miss ratio
        const totalAccesses = computronium.cacheState.cacheHits + computronium.cacheState.cacheMisses;
        const hitRatio = totalAccesses > 0 ? computronium.cacheState.cacheHits / totalAccesses : 0;
        
        // Draw cache efficiency indicator
        ctx.beginPath();
        ctx.arc(position.x, position.y, radius, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(0, 255, 0, ${hitRatio})`;
        ctx.lineWidth = 2;
        ctx.stroke();
        
        // Draw cache levels with coherence states
        const cacheLevels = [
            { level: 'L1', cache: computronium.cacheState.l1Cache },
            { level: 'L2', cache: computronium.cacheState.l2Cache },
            { level: 'L3', cache: computronium.cacheState.l3Cache }
        ];
        
        cacheLevels.forEach((cache, index) => {
            const y = position.y + radius + (index * 10);
            const coherenceStates = this.getCoherenceStateCounts(cache.cache);
            ctx.fillStyle = 'white';
            ctx.fillText(`${cache.level}: ${coherenceStates.MODIFIED}M ${coherenceStates.SHARED}S ${coherenceStates.EXCLUSIVE}E`, 
                        position.x - 40, y);
        });
        
        // Draw prefetch strategy indicator
        const strategy = computronium.cacheState.prefetchStrategy;
        ctx.fillStyle = 'yellow';
        ctx.fillText(`Prefetch: ${strategy}`, position.x - 40, position.y + radius + 40);
    }

    getCoherenceStateCounts(cacheLevel) {
        const counts = {
            MODIFIED: 0,
            SHARED: 0,
            EXCLUSIVE: 0,
            INVALID: 0
        };
        
        for (const entry of cacheLevel.values()) {
            counts[entry.state]++;
        }
        
        return counts;
    }

    makeTacticalDecision(entity, gameContext) {
        if (!entity.hasComponent('ComputroniumComponent')) return null;
        
        const computronium = entity.getComponent('ComputroniumComponent');
        const cacheState = computronium.cacheState;
        
        // Calculate detailed cache metrics
        const metrics = this.calculateDetailedCacheMetrics(cacheState);
        
        // Calculate base tactical decision
        const decision = this.calculateBaseTacticalDecision(entity, gameContext);
        
        // Adjust decision based on detailed metrics
        this.adjustTacticalDecision(decision, metrics);
        
        return decision;
    }

    calculateDetailedCacheMetrics(cacheState) {
        return {
            hitRatio: cacheState.metrics[this.CACHE_METRICS.HIT_RATIO],
            missRatio: cacheState.metrics[this.CACHE_METRICS.MISS_RATIO],
            coherenceEfficiency: cacheState.metrics[this.CACHE_METRICS.COHERENCE_EFFICIENCY],
            prefetchAccuracy: cacheState.metrics[this.CACHE_METRICS.PREFETCH_ACCURACY],
            cacheUtilization: cacheState.metrics[this.CACHE_METRICS.CACHE_UTILIZATION],
            latencyDistribution: cacheState.metrics[this.CACHE_METRICS.LATENCY_DISTRIBUTION]
        };
    }

    adjustTacticalDecision(decision, metrics) {
        // Adjust complexity based on cache performance
        const complexityFactor = this.calculateComplexityFactor(metrics);
        decision.complexity *= complexityFactor;
        
        // Adjust aggression based on prefetch accuracy
        const aggressionFactor = this.calculateAggressionFactor(metrics);
        decision.aggression *= aggressionFactor;
        
        // Adjust coordination based on coherence efficiency
        const coordinationFactor = this.calculateCoordinationFactor(metrics);
        decision.coordination *= coordinationFactor;
        
        // Adjust risk tolerance based on overall cache performance
        const riskFactor = this.calculateRiskFactor(metrics);
        decision.riskTolerance *= riskFactor;
    }

    calculateComplexityFactor(metrics) {
        const factors = [
            metrics.hitRatio * 0.4,
            metrics.prefetchAccuracy * 0.3,
            metrics.cacheUtilization * 0.3
        ];
        return factors.reduce((a, b) => a + b, 0);
    }

    calculateAggressionFactor(metrics) {
        const factors = [
            metrics.hitRatio * 0.5,
            metrics.latencyDistribution.l1 * 0.3,
            metrics.coherenceEfficiency * 0.2
        ];
        return factors.reduce((a, b) => a + b, 0);
    }

    calculateCoordinationFactor(metrics) {
        const factors = [
            metrics.coherenceEfficiency * 0.5,
            metrics.cacheUtilization * 0.3,
            metrics.prefetchAccuracy * 0.2
        ];
        return factors.reduce((a, b) => a + b, 0);
    }

    calculateRiskFactor(metrics) {
        const factors = [
            metrics.hitRatio * 0.3,
            metrics.coherenceEfficiency * 0.3,
            metrics.prefetchAccuracy * 0.2,
            metrics.cacheUtilization * 0.2
        ];
        return factors.reduce((a, b) => a + b, 0);
    }

    calculateBaseTacticalDecision(entity, gameContext) {
        // Base tactical decision calculation
        return {
            complexity: 1.0,
            aggression: 1.0,
            // Add other tactical parameters as needed
        };
    }

    updateCacheAwareCoordination(entities) {
        const computroniumEntities = entities.filter(e => e.hasComponent('ComputroniumComponent'));
        
        // Group units by cache efficiency
        const efficiencyGroups = this.groupUnitsByCacheEfficiency(computroniumEntities);
        
        // Coordinate units within each efficiency group
        for (const [efficiency, units] of efficiencyGroups.entries()) {
            this.coordinateUnits(units, efficiency);
        }
    }

    groupUnitsByCacheEfficiency(entities) {
        const groups = new Map();
        
        entities.forEach(entity => {
            const computronium = entity.getComponent('ComputroniumComponent');
            const efficiency = this.calculateCacheEfficiency(computronium.cacheState);
            
            // Group into efficiency ranges
            const range = Math.floor(efficiency * 10) / 10;
            if (!groups.has(range)) {
                groups.set(range, []);
            }
            groups.get(range).push(entity);
        });
        
        return groups;
    }

    coordinateUnits(units, efficiency) {
        // Sort units by cache performance
        units.sort((a, b) => {
            const aEfficiency = this.calculateCacheEfficiency(a.getComponent('ComputroniumComponent').cacheState);
            const bEfficiency = this.calculateCacheEfficiency(b.getComponent('ComputroniumComponent').cacheState);
            return bEfficiency - aEfficiency;
        });
        
        // Assign roles based on cache performance
        const leader = units[0];
        const support = units.slice(1);
        
        // Update coordination based on efficiency
        this.updateUnitCoordination(leader, support, efficiency);
    }

    updateUnitCoordination(leader, support, efficiency) {
        // Update leader's command component
        if (leader.hasComponent('CommandComponent')) {
            const command = leader.getComponent('CommandComponent');
            command.coordinationEfficiency = efficiency;
            command.supportUnits = support.length;
        }
        
        // Update support units
        support.forEach(unit => {
            if (unit.hasComponent('CommandComponent')) {
                const command = unit.getComponent('CommandComponent');
                command.followLeader = leader;
                command.coordinationEfficiency = efficiency;
            }
        });
    }

    updateCacheMetrics(computronium) {
        if (!computronium.cacheState) return;
        
        const metrics = computronium.cacheState.metrics || {};
        
        // Update hit/miss ratios
        const totalAccesses = computronium.cacheState.cacheHits + computronium.cacheState.cacheMisses;
        metrics[this.CACHE_METRICS.HIT_RATIO] = totalAccesses > 0 ? 
            computronium.cacheState.cacheHits / totalAccesses : 0;
        metrics[this.CACHE_METRICS.MISS_RATIO] = totalAccesses > 0 ? 
            computronium.cacheState.cacheMisses / totalAccesses : 0;
        
        // Update coherence efficiency
        metrics[this.CACHE_METRICS.COHERENCE_EFFICIENCY] = this.calculateCoherenceEfficiency(computronium.cacheState);
        
        // Update prefetch accuracy
        metrics[this.CACHE_METRICS.PREFETCH_ACCURACY] = this.calculatePrefetchAccuracy(computronium);
        
        // Update cache utilization
        metrics[this.CACHE_METRICS.CACHE_UTILIZATION] = this.calculateCacheUtilization(computronium.cacheState);
        
        // Update latency distribution
        metrics[this.CACHE_METRICS.LATENCY_DISTRIBUTION] = this.calculateLatencyDistribution(computronium);
        
        computronium.cacheState.metrics = metrics;
    }

    calculatePrefetchAccuracy(computronium) {
        const cacheState = computronium.cacheState;
        if (!cacheState.prefetchHistory) return 0;
        
        const history = cacheState.prefetchHistory;
        const totalPrefetches = history.length;
        if (totalPrefetches === 0) return 0;
        
        const successfulPrefetches = history.filter(entry => entry.used).length;
        return successfulPrefetches / totalPrefetches;
    }

    calculateCacheUtilization(cacheState) {
        let totalSize = 0;
        let usedSize = 0;
        
        for (const cache of [cacheState.l1Cache, cacheState.l2Cache, cacheState.l3Cache]) {
            for (const entry of cache.values()) {
                totalSize += this.CACHE_SIZES[`${cache.name.toUpperCase()}_CACHE`];
                usedSize += entry.size;
            }
        }
        
        return totalSize > 0 ? usedSize / totalSize : 0;
    }

    calculateLatencyDistribution(computronium) {
        const cacheState = computronium.cacheState;
        if (!cacheState.latencyHistory) return { l1: 0, l2: 0, l3: 0, memory: 0 };
        
        const distribution = { l1: 0, l2: 0, l3: 0, memory: 0 };
        const history = cacheState.latencyHistory;
        
        history.forEach(entry => {
            distribution[entry.level]++;
        });
        
        const total = history.length;
        if (total > 0) {
            Object.keys(distribution).forEach(key => {
                distribution[key] /= total;
            });
        }
        
        return distribution;
    }

    analyzeLoopPattern(history, patterns) {
        const loopCandidates = new Map();
        
        for (let i = 0; i < history.length - this.PATTERN_PARAMS.LOOP_THRESHOLD; i++) {
            const sequence = history.slice(i, i + this.PATTERN_PARAMS.LOOP_THRESHOLD);
            const hash = this.hashSequence(sequence);
            
            if (loopCandidates.has(hash)) {
                const candidate = loopCandidates.get(hash);
                candidate.count++;
                candidate.positions.push(i);
            } else {
                loopCandidates.set(hash, {
                    sequence,
                    count: 1,
                    positions: [i]
                });
            }
        }
        
        // Identify confirmed loops
        for (const [hash, candidate] of loopCandidates.entries()) {
            if (candidate.count >= 2) {
                const confidence = Math.min(0.9, candidate.count / 5);
                patterns.set(this.PREFETCH_PATTERNS.LOOP, {
                    confidence,
                    sequence: candidate.sequence,
                    positions: candidate.positions
                });
            }
        }
    }

    analyzeBranchPattern(history, patterns) {
        const branches = new Map();
        
        for (let i = 1; i < history.length; i++) {
            const current = history[i];
            const previous = history[i - 1];
            
            if (Math.abs(current.address - previous.address) > this.PATTERN_PARAMS.MAX_STRIDE) {
                const branchKey = `${previous.address}->${current.address}`;
                branches.set(branchKey, (branches.get(branchKey) || 0) + 1);
            }
        }
        
        // Identify common branches
        for (const [branch, count] of branches.entries()) {
            if (count >= 3) {
                const [from, to] = branch.split('->').map(Number);
                patterns.set(this.PREFETCH_PATTERNS.BRANCH_PREDICT, {
                    confidence: Math.min(this.PATTERN_PARAMS.BRANCH_CONFIDENCE, count / 10),
                    from,
                    to,
                    count
                });
            }
        }
    }

    analyzeContextSwitchPattern(history, patterns) {
        const contextSwitches = [];
        let lastContext = null;
        
        for (let i = 0; i < history.length; i++) {
            const current = history[i];
            const context = this.determineContext(current);
            
            if (lastContext && context !== lastContext) {
                contextSwitches.push({
                    position: i,
                    from: lastContext,
                    to: context
                });
            }
            
            lastContext = context;
        }
        
        // Analyze context switch patterns
        if (contextSwitches.length >= 2) {
            const switchPattern = this.identifySwitchPattern(contextSwitches);
            if (switchPattern) {
                patterns.set(this.PREFETCH_PATTERNS.CONTEXT_SWITCH, {
                    confidence: 0.8,
                    pattern: switchPattern
                });
            }
        }
    }

    determineContext(access) {
        // Determine the context of a memory access
        if (access.type === 'COMBAT_DATA') return 'COMBAT';
        if (access.type === 'COMMAND_DATA') return 'COMMAND';
        return 'GENERAL';
    }

    identifySwitchPattern(switches) {
        // Identify patterns in context switches
        const patterns = new Map();
        
        for (let i = 1; i < switches.length; i++) {
            const pattern = `${switches[i-1].from}->${switches[i-1].to}->${switches[i].from}->${switches[i].to}`;
            patterns.set(pattern, (patterns.get(pattern) || 0) + 1);
        }
        
        // Find most common pattern
        let maxPattern = null;
        let maxCount = 0;
        
        for (const [pattern, count] of patterns.entries()) {
            if (count > maxCount) {
                maxPattern = pattern;
                maxCount = count;
            }
        }
        
        return maxPattern;
    }

    hashSequence(sequence) {
        // Create a hash of a sequence for loop detection
        return sequence.map(entry => entry.address).join(',');
    }

    // Tactical Options
    static TACTICAL_OPTIONS = {
        STRATEGIC_DEPLOYMENT: {
            name: 'Strategic Deployment',
            requirements: { rank: 'GENERAL', computroniumLevel: 4 },
            effects: {
                formationBonus: 1.2,
                coordinationBonus: 1.3,
                forkEfficiency: 1.25
            }
        },
        COORDINATED_ASSAULT: {
            name: 'Coordinated Assault',
            requirements: { rank: 'GENERAL', computroniumLevel: 3 },
            effects: {
                damageBonus: 1.3,
                accuracyBonus: 1.2,
                forkEfficiency: 1.15
            }
        },
        DEFENSIVE_POSITIONING: {
            name: 'Defensive Positioning',
            requirements: { rank: 'GENERAL', computroniumLevel: 3 },
            effects: {
                shieldBonus: 1.3,
                armorBonus: 1.2,
                forkEfficiency: 1.1
            }
        },
        TACTICAL_DEPLOYMENT: {
            name: 'Tactical Deployment',
            requirements: { rank: 'COLONEL', computroniumLevel: 3 },
            effects: {
                formationBonus: 1.15,
                coordinationBonus: 1.2,
                forkEfficiency: 1.1
            }
        },
        FOCUSED_ASSAULT: {
            name: 'Focused Assault',
            requirements: { rank: 'COLONEL', computroniumLevel: 2 },
            effects: {
                damageBonus: 1.2,
                accuracyBonus: 1.15,
                forkEfficiency: 1.1
            }
        },
        DEFENSIVE_FORMATION: {
            name: 'Defensive Formation',
            requirements: { rank: 'COLONEL', computroniumLevel: 2 },
            effects: {
                shieldBonus: 1.2,
                armorBonus: 1.15,
                forkEfficiency: 1.05
            }
        }
    };

    calculateCommandChainEfficiency(commander, subordinates) {
        const command = commander.getComponent('CommandComponent');
        const computronium = commander.getComponent('ComputroniumComponent');
        
        // Base efficiency from rank
        let efficiency = this.COMMAND_RANKS[command.rank].level / 5;
        
        // Adjust for computronium level
        efficiency *= (1 + (computronium.coreLevel - 1) * 0.1);
        
        // Adjust for number of subordinates
        const optimalSubordinates = this.COMMAND_RANKS[command.rank].level * 2;
        const subordinateRatio = Math.min(subordinates.length / optimalSubordinates, 1);
        efficiency *= (0.8 + subordinateRatio * 0.2);
        
        // Adjust for distance to subordinates
        const avgDistance = this.calculateAverageDistance(commander, subordinates);
        const maxDistance = this.COMMAND_RANKS[command.rank].auraRadius * 2;
        efficiency *= (1 - (avgDistance / maxDistance) * 0.2);
        
        return Math.min(Math.max(efficiency, 0.5), 1.0);
    }

    updateCommandChainState(commander, subordinates, chainEfficiency) {
        const command = commander.getComponent('CommandComponent');
        
        // Update command chain state
        command.chainState = {
            efficiency: chainEfficiency,
            lastUpdate: Date.now(),
            subordinateCount: subordinates.length,
            activeDirectives: this.getActiveDirectives(commander, subordinates)
        };
        
        // Update subordinates' chain state
        subordinates.forEach(subordinate => {
            if (subordinate.hasComponent('CommandComponent')) {
                const subCommand = subordinate.getComponent('CommandComponent');
                subCommand.chainState = {
                    commanderId: commander.id,
                    efficiency: chainEfficiency,
                    lastUpdate: Date.now()
                };
            }
        });
    }

    getActiveDirectives(commander, subordinates) {
        const directives = new Map();
        
        subordinates.forEach(subordinate => {
            if (subordinate.hasComponent('CommandComponent')) {
                const directive = subordinate.getComponent('CommandComponent').currentDirective;
                if (directive) {
                    directives.set(directive.id, {
                        type: directive.type,
                        progress: directive.progress,
                        assignedUnits: directive.assignedUnits
                    });
                }
            }
        });
        
        return directives;
    }

    issueTacticalOrders(subordinate, strategicDirective, scope, latency, chainEfficiency) {
        if (!subordinate.hasComponent('CommandComponent')) return;
        
        const command = subordinate.getComponent('CommandComponent');
        const computronium = subordinate.getComponent('ComputroniumComponent');
        
        // Calculate available tactical options
        const availableOptions = this.getAvailableTacticalOptions(command, computronium);
        
        // Select best tactical option based on strategic directive
        const tacticalOption = this.selectTacticalOption(availableOptions, strategicDirective);
        
        // Create tactical order
        const order = {
            id: Date.now(),
            type: tacticalOption.name,
            strategicDirective: strategicDirective,
            latency: latency,
            chainEfficiency: chainEfficiency,
            effects: this.calculateTacticalEffects(tacticalOption, chainEfficiency),
            assignedUnits: this.assignUnitsToTacticalOrder(subordinate, tacticalOption, scope)
        };
        
        // Apply tactical order
        this.applyTacticalOrder(subordinate, order);
    }

    getAvailableTacticalOptions(command, computronium) {
        return Object.entries(this.TACTICAL_OPTIONS)
            .filter(([_, option]) => {
                return command.rank === option.requirements.rank &&
                       computronium.coreLevel >= option.requirements.computroniumLevel;
            })
            .map(([key, option]) => ({
                key,
                ...option
            }));
    }

    selectTacticalOption(availableOptions, strategicDirective) {
        // Score each option based on strategic directive
        const scoredOptions = availableOptions.map(option => ({
            ...option,
            score: this.scoreTacticalOption(option, strategicDirective)
        }));
        
        // Select highest scoring option
        return scoredOptions.reduce((best, current) => 
            current.score > best.score ? current : best
        );
    }

    scoreTacticalOption(option, strategicDirective) {
        let score = 0;
        
        // Score based on strategic directive type
        switch (strategicDirective.type) {
            case 'ATTACK':
                score += option.effects.damageBonus ? option.effects.damageBonus * 2 : 0;
                score += option.effects.accuracyBonus ? option.effects.accuracyBonus : 0;
                break;
            case 'DEFEND':
                score += option.effects.shieldBonus ? option.effects.shieldBonus * 2 : 0;
                score += option.effects.armorBonus ? option.effects.armorBonus : 0;
                break;
            case 'MANEUVER':
                score += option.effects.formationBonus ? option.effects.formationBonus * 2 : 0;
                score += option.effects.coordinationBonus ? option.effects.coordinationBonus : 0;
                break;
        }
        
        // Adjust for fork efficiency
        score *= option.effects.forkEfficiency;
        
        return score;
    }

    calculateTacticalEffects(tacticalOption, chainEfficiency) {
        const effects = { ...tacticalOption.effects };
        
        // Adjust effects based on chain efficiency
        Object.keys(effects).forEach(key => {
            if (key !== 'forkEfficiency') {
                effects[key] = 1 + (effects[key] - 1) * chainEfficiency;
            }
        });
        
        return effects;
    }

    assignUnitsToTacticalOrder(commander, tacticalOption, scope) {
        const assignedUnits = [];
        const command = commander.getComponent('CommandComponent');
        
        // Get available units within scope
        const availableUnits = this.findUnitsInRange(commander, scope.radius);
        
        // Sort units by effectiveness for the tactical option
        const sortedUnits = this.sortUnitsByTacticalEffectiveness(availableUnits, tacticalOption);
        
        // Assign units up to max subordinates
        for (let i = 0; i < Math.min(sortedUnits.length, scope.maxSubordinates); i++) {
            assignedUnits.push(sortedUnits[i]);
        }
        
        return assignedUnits;
    }

    sortUnitsByTacticalEffectiveness(units, tacticalOption) {
        return units.sort((a, b) => {
            const scoreA = this.calculateUnitTacticalScore(a, tacticalOption);
            const scoreB = this.calculateUnitTacticalScore(b, tacticalOption);
            return scoreB - scoreA;
        });
    }

    calculateUnitTacticalScore(unit, tacticalOption) {
        let score = 0;
        
        // Score based on unit components and tactical option
        if (unit.hasComponent('CombatComponent')) {
            const combat = unit.getComponent('CombatComponent');
            if (tacticalOption.effects.damageBonus) {
                score += combat.damage * tacticalOption.effects.damageBonus;
            }
            if (tacticalOption.effects.accuracyBonus) {
                score += combat.accuracy * tacticalOption.effects.accuracyBonus;
            }
        }
        
        if (unit.hasComponent('ShieldComponent')) {
            const shield = unit.getComponent('ShieldComponent');
            if (tacticalOption.effects.shieldBonus) {
                score += shield.maxHP * tacticalOption.effects.shieldBonus;
            }
        }
        
        if (unit.hasComponent('ArmorComponent')) {
            const armor = unit.getComponent('ArmorComponent');
            if (tacticalOption.effects.armorBonus) {
                score += armor.value * tacticalOption.effects.armorBonus;
            }
        }
        
        return score;
    }

    applyTacticalOrder(commander, order) {
        const command = commander.getComponent('CommandComponent');
        
        // Update command component with new order
        command.currentOrder = order;
        
        // Apply effects to assigned units
        order.assignedUnits.forEach(unit => {
            this.applyTacticalEffects(unit, order.effects);
        });
        
        // Update computronium allocation
        if (commander.hasComponent('ComputroniumComponent')) {
            const computronium = commander.getComponent('ComputroniumComponent');
            this.allocateComputroniumForTacticalOrder(computronium, order);
        }
    }

    applyTacticalEffects(unit, effects) {
        // Apply combat effects
        if (unit.hasComponent('CombatComponent')) {
            const combat = unit.getComponent('CombatComponent');
            if (effects.damageBonus) combat.damage *= effects.damageBonus;
            if (effects.accuracyBonus) combat.accuracy *= effects.accuracyBonus;
        }
        
        // Apply defensive effects
        if (unit.hasComponent('ShieldComponent')) {
            const shield = unit.getComponent('ShieldComponent');
            if (effects.shieldBonus) shield.maxHP *= effects.shieldBonus;
        }
        
        if (unit.hasComponent('ArmorComponent')) {
            const armor = unit.getComponent('ArmorComponent');
            if (effects.armorBonus) armor.value *= effects.armorBonus;
        }
        
        // Apply formation effects
        if (unit.hasComponent('FormationComponent')) {
            const formation = unit.getComponent('FormationComponent');
            if (effects.formationBonus) formation.efficiency *= effects.formationBonus;
            if (effects.coordinationBonus) formation.coordination *= effects.coordinationBonus;
        }
    }

    allocateComputroniumForTacticalOrder(computronium, order) {
        // Calculate required computronium cycles
        const requiredCycles = this.calculateRequiredComputroniumCycles(order);
        
        // Allocate cycles based on fork efficiency
        const allocatedCycles = Math.floor(requiredCycles * order.effects.forkEfficiency);
        
        // Update computronium allocation
        computronium.allocatedCycles = allocatedCycles;
        computronium.currentFocus = order.type;
    }

    calculateRequiredComputroniumCycles(order) {
        // Base cycles required for the tactical option
        let cycles = 100; // Base cost
        
        // Add cycles for each assigned unit
        cycles += order.assignedUnits.length * 10;
        
        // Add cycles for chain efficiency
        cycles *= (1 + (1 - order.chainEfficiency));
        
        return cycles;
    }
}