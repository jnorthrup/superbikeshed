// js/core/enhancedCommandHierarchy.js - Enhanced C&C with Computronium integration

import { COMPUTRONIUM_CONFIG } from '../config/gameConstants.js';
// Temporary inline TrikeShed implementation
const j = (a, b) => ({ a, b });
const getSeriesValue = (series, i) => {
    if (i < 0 || i >= series.a) throw new RangeError(`Index ${i} out of bounds`);
    return series.b(i);
};
const seriesSize = (series) => series.a;

/**
 * Command Node - represents a unit or building in the command hierarchy
 * Enhanced with Computronium-based authority calculations
 */
export class CommandNode {
    constructor(entity, rank = 1) {
        this.entity = entity; // Unit or Building reference
        this.rank = rank; // Command rank (1-5, higher is better)
        this.subordinates = []; // Units under command
        this.superior = null; // Higher-ranking commander
        this.authority = 0; // Calculated authority level
        this.commandLatency = 0; // Command processing delay
        this.lastUpdate = Date.now();
        
        // Computronium integration
        this.computroniumCore = null; // Reference to associated core
        this.computroniumBonus = 0; // Authority bonus from Computronium
        this.isUnderComputationalAttack = false;
        this.commandEfficiency = 1.0;
        
        // Command fitness tracking
        this.commandFitness = 1.0; // Based on success/failure history
        this.recentCommands = []; // History of recent command outcomes
        this.veterancy = 1.0; // Experience multiplier
        
        // Autonomy system for extreme situations
        this.autonomyLevel = 0.0; // 0 = fully commanded, 1 = fully autonomous
        this.autonomyTriggers = {
            commanderLost: false,
            communicationDown: false,
            underHeavyAttack: false,
            isolatedFromCommand: false,
            lowComputronium: false
        };
        this.lastCommandReceived = Date.now();
        this.autonomyThreshold = this.calculateAutonomyThreshold();
        this.maxAutonomyTime = 30000; // 30 seconds max without commands before going autonomous
        
        console.log(`[C&C] Command node created for ${entity.type} (rank ${rank})`);
    }

    /**
     * Calculate dynamic authority based on multiple factors
     * Includes subsumption benefits from subordinate chain
     * @param {ComputroniumManager} computroniumManager - Team's computronium manager
     * @returns {number} Calculated authority level
     */
    calculateAuthority(computroniumManager) {
        if (!this.entity || this.entity.hp <= 0) {
            this.authority = 0;
            return 0;
        }

        // Base authority from rank (exponential scaling for massive unit control)
        let authority = Math.pow(this.rank, 2.5) * 50; // Rank 5 gets ~559 base authority
        
        // Health factor - damaged units have reduced authority
        const healthFactor = this.entity.hp / this.entity.maxHealth;
        authority *= healthFactor;
        
        // Veterancy bonus - experienced units command better
        authority *= this.veterancy;
        
        // Command fitness - successful commanders gain authority
        authority *= this.commandFitness;
        
        // SUBSUMPTION ARCHITECTURE: Authority flows UP from subordinates
        const subordinateAuthority = this.calculateSubordinateAuthorityBonus();
        authority += subordinateAuthority;
        
        // Span of control bonus - more subordinates = more authority (up to a limit)
        const spanBonus = Math.min(2.0, 1.0 + (this.subordinates.length * 0.1));
        authority *= spanBonus;
        
        // Computronium bonus - available processing power enhances command
        if (this.computroniumCore && computroniumManager) {
            const status = computroniumManager.getStatus();
            const availableComputronium = status.availableComputronium;
            
            // Computronium provides logarithmic authority bonus
            this.computroniumBonus = Math.min(2.0, 1.0 + Math.log10(1 + availableComputronium));
            authority *= this.computroniumBonus;
            
            // Processing wick effect: Higher rank commanders get exponentially more benefit
            const wickMultiplier = Math.pow(1.2, this.rank);
            authority *= wickMultiplier;
            
            // Reduce authority if under computational attack
            if (this.isUnderComputationalAttack) {
                authority *= 0.5;
            }
        }
        
        this.authority = Math.max(0, authority);
        return this.authority;
    }

    /**
     * Calculate authority bonus from subordinate chain (subsumption architecture)
     * Benefits flow UP - commanders benefit most from active squads, less from distant units
     * @returns {number} Bonus authority from subordinates
     */
    calculateSubordinateAuthorityBonus() {
        let totalBonus = 0;
        
        // Get detailed subordinate network statistics
        const networkStats = this.getSubordinateNetworkStats();
        
        // ACTIVE SQUAD BONUS: Direct subordinates provide maximum benefit
        const directSubordinates = this.subordinates.filter(sub => sub.entity && sub.entity.hp > 0);
        const activeSquadBonus = directSubordinates.length * 25; // High benefit from direct command
        totalBonus += activeSquadBonus;
        
        // Direct subordinate quality bonus (veterancy, fitness, rank)
        for (const subordinate of directSubordinates) {
            const qualityBonus = subordinate.rank * 15 + subordinate.veterancy * 10 + subordinate.commandFitness * 20;
            totalBonus += qualityBonus;
        }
        
        // INDIRECT NETWORK BONUS: Diminishing returns for distant units
        const indirectBonus = this.calculateIndirectNetworkBonus(networkStats);
        totalBonus += indirectBonus;
        
        // SPAN OF CONTROL: Exponential benefits for wider direct command
        const spanMultiplier = Math.log2(1 + directSubordinates.length) * 0.4;
        totalBonus *= (1 + spanMultiplier);
        
        return Math.floor(totalBonus);
    }

    /**
     * Calculate bonus from indirect subordinates with diminishing returns
     * @param {Object} networkStats - Network statistics
     * @returns {number} Indirect network bonus
     */
    calculateIndirectNetworkBonus(networkStats) {
        // Indirect subordinates provide much less benefit than direct ones
        const indirectCount = networkStats.totalSubordinates - this.subordinates.length;
        const indirectBonus = indirectCount * 3; // Much lower than direct subordinate bonus
        
        // Network depth bonus with strong diminishing returns
        const depthPenalty = Math.pow(0.7, networkStats.maxDepth - 1); // 30% reduction per level
        const depthAdjustedBonus = indirectBonus * depthPenalty;
        
        // Cap indirect bonus to prevent over-scaling
        return Math.min(depthAdjustedBonus, this.rank * 200);
    }

    /**
     * Get comprehensive statistics about subordinate network
     * @returns {Object} Network statistics
     */
    getSubordinateNetworkStats() {
        const visited = new Set();
        
        const traverse = (node, depth = 0) => {
            if (visited.has(node) || !node.entity || node.entity.hp <= 0) {
                return {
                    count: 0,
                    weightedRankSum: 0,
                    totalVeterancy: 0,
                    totalCommandFitness: 0,
                    maxDepth: depth
                };
            }
            
            visited.add(node);
            
            let stats = {
                count: 1, // Count this node
                weightedRankSum: node.rank,
                totalVeterancy: node.veterancy,
                totalCommandFitness: node.commandFitness,
                maxDepth: depth
            };
            
            // Recursively traverse all subordinates
            for (const subordinate of node.subordinates) {
                const subStats = traverse(subordinate, depth + 1);
                stats.count += subStats.count;
                stats.weightedRankSum += subStats.weightedRankSum;
                stats.totalVeterancy += subStats.totalVeterancy;
                stats.totalCommandFitness += subStats.totalCommandFitness;
                stats.maxDepth = Math.max(stats.maxDepth, subStats.maxDepth);
            }
            
            return stats;
        };
        
        // Start traversal from this node's subordinates (don't count self)
        let totalStats = {
            count: 0,
            weightedRankSum: 0,
            totalVeterancy: 0,
            totalCommandFitness: 0,
            maxDepth: 0
        };
        
        for (const subordinate of this.subordinates) {
            const subStats = traverse(subordinate, 1);
            totalStats.count += subStats.count;
            totalStats.weightedRankSum += subStats.weightedRankSum;
            totalStats.totalVeterancy += subStats.totalVeterancy;
            totalStats.totalCommandFitness += subStats.totalCommandFitness;
            totalStats.maxDepth = Math.max(totalStats.maxDepth, subStats.maxDepth);
        }
        
        return {
            totalSubordinates: totalStats.count,
            weightedRankSum: totalStats.weightedRankSum,
            totalVeterancy: totalStats.totalVeterancy,
            averageCommandFitness: totalStats.count > 0 ? totalStats.totalCommandFitness / totalStats.count : 0,
            maxDepth: totalStats.maxDepth
        };
    }

    /**
     * Calculate command latency based on Computronium availability
     * @param {ComputroniumManager} computroniumManager - Team's computronium manager
     * @returns {number} Command latency in seconds
     */
    calculateCommandLatency(computroniumManager) {
        let latency = COMPUTRONIUM_CONFIG.C2_LATENCY_BASE;
        
        // Distance-based latency (simplified)
        const subordinateDistance = this.getAverageSubordinateDistance();
        latency += subordinateDistance / 10000; // 1 second per 10k world units
        
        // Computronium reduces latency
        if (computroniumManager) {
            const status = computroniumManager.getStatus();
            latency *= (1.0 - status.c2LatencyBonus);
        }
        
        // Computational attacks increase latency
        if (this.isUnderComputationalAttack) {
            latency *= 3.0;
        }
        
        // Command efficiency affects latency
        latency /= this.commandEfficiency;
        
        this.commandLatency = Math.max(0.01, latency); // Minimum 10ms
        return this.commandLatency;
    }

    /**
     * Get average distance to subordinates
     * @returns {number} Average distance
     */
    getAverageSubordinateDistance() {
        if (this.subordinates.length === 0) return 0;
        
        let totalDistance = 0;
        for (const subordinate of this.subordinates) {
            const dx = this.entity.x - subordinate.entity.x;
            const dy = this.entity.y - subordinate.entity.y;
            totalDistance += Math.sqrt(dx * dx + dy * dy);
        }
        
        return totalDistance / this.subordinates.length;
    }

    /**
     * Issue a command to subordinates with Computronium-enhanced processing
     * Optimized for massive scale (thousands of units)
     * @param {string} commandType - Type of command
     * @param {Object} parameters - Command parameters
     * @param {ComputroniumManager} computroniumManager - Team's computronium manager
     * @returns {boolean} True if command was successfully issued
     */
    issueCommand(commandType, parameters, computroniumManager) {
        if (this.subordinates.length === 0) return false;
        
        // Check if enough authority to issue command
        const requiredAuthority = this.getCommandAuthorityRequirement(commandType);
        if (this.authority < requiredAuthority) {
            console.log(`[C&C] Insufficient authority for ${commandType}. Required: ${requiredAuthority}, Available: ${this.authority.toFixed(1)}`);
            return false;
        }
        
        // Scale Computronium cost with subordinate count (but with efficiency bonuses for higher ranks)
        const baseCost = this.getCommandComputroniumCost(commandType);
        const scalingFactor = Math.sqrt(this.subordinates.length); // Square root scaling for large armies
        const rankEfficiency = 1.0 - (this.rank * 0.15); // Higher ranks are more efficient
        const finalCost = baseCost * scalingFactor * Math.max(0.1, rankEfficiency);
        
        if (finalCost > 0 && !computroniumManager.spendComputronium(finalCost, `command_${commandType}`)) {
            console.log(`[C&C] Insufficient Computronium for ${commandType}. Required: ${finalCost.toFixed(2)}`);
            return false;
        }
        
        // Calculate command processing delay
        const latency = this.calculateCommandLatency(computroniumManager);
        
        // PROCESSING WICK: Batch process commands efficiently
        setTimeout(() => {
            this.executeBatchCommand(commandType, parameters, computroniumManager);
            this.recordCommandOutcome(commandType, true);
        }, latency * 1000);
        
        console.log(`[C&C] ${commandType} command issued to ${this.subordinates.length} subordinates (latency: ${(latency * 1000).toFixed(0)}ms, cost: ${finalCost.toFixed(2)})`);
        return true;
    }

    /**
     * Execute command using batch processing for massive scale efficiency
     * @param {string} commandType - Command type
     * @param {Object} parameters - Command parameters
     * @param {ComputroniumManager} computroniumManager - Computronium manager
     */
    executeBatchCommand(commandType, parameters, computroniumManager) {
        // Batch process subordinates in chunks to prevent performance issues
        const BATCH_SIZE = 50; // Process 50 units at a time
        let processed = 0;
        
        const processBatch = () => {
            const batch = this.subordinates.slice(processed, processed + BATCH_SIZE);
            
            for (const subordinate of batch) {
                if (subordinate.entity && subordinate.entity.hp > 0) {
                    // Skip autonomous units - they don't follow commands
                    if (subordinate.entity.isAutonomous) {
                        continue;
                    }
                    
                    // Track command receipt for autonomy system
                    subordinate.lastCommandReceived = Date.now();
                    
                    // Apply command to subordinate entity
                    this.applyCommandToEntity(subordinate.entity, commandType, parameters);
                    
                    // If subordinate has its own subordinates, cascade the command down
                    if (subordinate.subordinates.length > 0) {
                        subordinate.issueCommand(commandType, parameters, computroniumManager);
                    }
                }
            }
            
            processed += BATCH_SIZE;
            
            // Continue processing next batch if there are more subordinates
            if (processed < this.subordinates.length) {
                // Use requestAnimationFrame for smooth processing without blocking
                if (typeof requestAnimationFrame !== 'undefined') {
                    requestAnimationFrame(processBatch);
                } else {
                    // Fallback for headless mode
                    setTimeout(processBatch, 1);
                }
            }
        };
        
        processBatch();
    }

    /**
     * Get authority requirement for a command type
     * @param {string} commandType - Command type
     * @returns {number} Required authority
     */
    getCommandAuthorityRequirement(commandType) {
        const requirements = {
            'move': 50,
            'attack': 100,
            'defend': 75,
            'formation': 150,
            'strategic_retreat': 200,
            'coordinated_assault': 300
        };
        
        return requirements[commandType] || 100;
    }

    /**
     * Get Computronium cost for a command type
     * @param {string} commandType - Command type
     * @returns {number} Computronium cost
     */
    getCommandComputroniumCost(commandType) {
        const costs = {
            'move': 0.1,
            'attack': 0.2,
            'defend': 0.15,
            'formation': 0.5,
            'strategic_retreat': 1.0,
            'coordinated_assault': 2.0
        };
        
        return costs[commandType] || 0.1;
    }

    /**
     * Execute a command on subordinates
     * @param {string} commandType - Command type
     * @param {Object} parameters - Command parameters
     */
    executeCommand(commandType, parameters) {
        for (const subordinate of this.subordinates) {
            if (subordinate.entity && subordinate.entity.hp > 0) {
                // Apply command to subordinate entity
                this.applyCommandToEntity(subordinate.entity, commandType, parameters);
            }
        }
    }

    /**
     * Apply a specific command to an entity
     * @param {Unit|Building} entity - Target entity
     * @param {string} commandType - Command type
     * @param {Object} parameters - Command parameters
     */
    applyCommandToEntity(entity, commandType, parameters) {
        switch (commandType) {
            case 'move':
                if (entity.setTarget && parameters.target) {
                    entity.setTarget(parameters.target.x, parameters.target.y);
                }
                break;
            case 'attack':
                if (entity.setAttackTarget && parameters.target) {
                    entity.setAttackTarget(parameters.target);
                }
                break;
            case 'defend':
                if (parameters.position) {
                    entity.defendPosition = parameters.position;
                    entity.state = 'defending';
                }
                break;
            case 'formation':
                if (parameters.formation && entity.setFormation) {
                    entity.setFormation(parameters.formation);
                }
                break;
        }
    }

    /**
     * Record the outcome of a command for fitness tracking
     * @param {string} commandType - Command type
     * @param {boolean} success - Whether command was successful
     */
    recordCommandOutcome(commandType, success) {
        this.recentCommands.push({
            type: commandType,
            success: success,
            timestamp: Date.now()
        });
        
        // Keep only recent commands (last 20)
        if (this.recentCommands.length > 20) {
            this.recentCommands.shift();
        }
        
        // Update command fitness based on success rate
        const successRate = this.recentCommands.filter(cmd => cmd.success).length / this.recentCommands.length;
        this.commandFitness = 0.5 + (successRate * 0.5); // Range: 0.5 to 1.0
        
        // Update veterancy slowly over time
        if (success) {
            this.veterancy = Math.min(2.0, this.veterancy + 0.01);
        }
    }

    /**
     * Add a subordinate to this command node
     * @param {CommandNode} subordinate - Subordinate command node
     */
    addSubordinate(subordinate) {
        if (!this.subordinates.includes(subordinate)) {
            this.subordinates.push(subordinate);
            subordinate.superior = this;
            console.log(`[C&C] ${subordinate.entity.type} now under command of ${this.entity.type}`);
        }
    }

    /**
     * Remove a subordinate from this command node
     * @param {CommandNode} subordinate - Subordinate to remove
     */
    removeSubordinate(subordinate) {
        const index = this.subordinates.indexOf(subordinate);
        if (index !== -1) {
            this.subordinates.splice(index, 1);
            subordinate.superior = null;
            console.log(`[C&C] ${subordinate.entity.type} removed from command of ${this.entity.type}`);
        }
    }

    /**
     * Handle computational attack effects
     * @param {number} attackIntensity - Intensity of the attack
     */
    handleComputationalAttack(attackIntensity) {
        this.isUnderComputationalAttack = true;
        this.commandEfficiency = Math.max(0.1, this.commandEfficiency - (attackIntensity * 0.1));
        
        // Recovery after 5 seconds
        setTimeout(() => {
            this.isUnderComputationalAttack = false;
            this.commandEfficiency = Math.min(1.0, this.commandEfficiency + 0.1);
        }, 5000);
        
        console.log(`[C&C] ${this.entity.type} under computational attack! Efficiency reduced to ${(this.commandEfficiency * 100).toFixed(1)}%`);
    }

    /**
     * Calculate autonomy threshold based on unit characteristics
     * @returns {number} Autonomy threshold (higher = more likely to go autonomous)
     */
    calculateAutonomyThreshold() {
        let threshold = 0.3; // Base threshold
        
        // Higher rank units are more independent
        threshold += this.rank * 0.1;
        
        // Veteran units are more autonomous
        threshold += (this.veterancy - 1.0) * 0.2;
        
        // Units with good command fitness are more autonomous
        threshold += (this.commandFitness - 0.5) * 0.3;
        
        return Math.min(0.9, Math.max(0.1, threshold));
    }

    /**
     * Update autonomy level based on current situation
     * @param {ComputroniumManager} computroniumManager - Team's computronium manager
     */
    updateAutonomyLevel(computroniumManager) {
        const now = Date.now();
        let autonomyIncrease = 0;
        
        // Check for autonomy triggers
        
        // 1. Commander lost (superior destroyed)
        if (this.superior && (!this.superior.entity || this.superior.entity.hp <= 0)) {
            this.autonomyTriggers.commanderLost = true;
            autonomyIncrease += 0.4;
        }
        
        // 2. Communication breakdown (no commands for extended time)
        const timeSinceLastCommand = now - this.lastCommandReceived;
        if (timeSinceLastCommand > this.maxAutonomyTime) {
            this.autonomyTriggers.communicationDown = true;
            autonomyIncrease += 0.3;
        }
        
        // 3. Under heavy attack (health below 50%)
        if (this.entity && this.entity.hp < this.entity.maxHp * 0.5) {
            this.autonomyTriggers.underHeavyAttack = true;
            autonomyIncrease += 0.2;
        }
        
        // 4. Isolated from command (no nearby superior units)
        if (this.isIsolatedFromCommand()) {
            this.autonomyTriggers.isolatedFromCommand = true;
            autonomyIncrease += 0.25;
        }
        
        // 5. Low computronium (team running out of processing power)
        if (computroniumManager && computroniumManager.getStatus().availableComputronium < 5) {
            this.autonomyTriggers.lowComputronium = true;
            autonomyIncrease += 0.15;
        }
        
        // Increase autonomy level if triggers are active
        if (autonomyIncrease > 0) {
            this.autonomyLevel = Math.min(1.0, this.autonomyLevel + (autonomyIncrease * 0.01)); // Gradual increase
        } else {
            // Gradually return to commanded state if no triggers
            this.autonomyLevel = Math.max(0.0, this.autonomyLevel - 0.005);
        }
        
        // Check if unit should go fully autonomous
        if (this.autonomyLevel > this.autonomyThreshold) {
            this.goAutonomous();
        }
    }

    /**
     * Check if unit is isolated from command structure
     * @returns {boolean} True if isolated
     */
    isIsolatedFromCommand() {
        if (!this.entity || !this.superior) return true;
        
        // Check distance to superior
        const dx = this.entity.x - this.superior.entity.x;
        const dy = this.entity.y - this.superior.entity.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        
        // Isolated if superior is more than 1000 units away
        return distance > 1000;
    }

    /**
     * Make unit go autonomous in extreme situations
     */
    goAutonomous() {
        console.log(`[C&C] ${this.entity.type} going AUTONOMOUS due to:`, this.autonomyTriggers);
        
        // Remove from current command structure temporarily
        if (this.superior) {
            this.superior.removeSubordinate(this);
        }
        
        // Set autonomous behavior flags
        this.entity.isAutonomous = true;
        this.entity.autonomyStartTime = Date.now();
        
        // Autonomous units make their own decisions
        this.makeAutonomousDecision();
    }

    /**
     * Make autonomous tactical decisions
     */
    makeAutonomousDecision() {
        if (!this.entity) return;
        
        // Autonomous decision priorities:
        // 1. Self-preservation if heavily damaged
        if (this.entity.hp < this.entity.maxHp * 0.3) {
            this.entity.autonomousMode = 'retreat';
            this.entity.state = 'fleeing';
            return;
        }
        
        // 2. Find nearby friendly units to regroup with
        const nearbyFriendlies = this.findNearbyFriendlyUnits();
        if (nearbyFriendlies.length > 0) {
            this.entity.autonomousMode = 'regroup';
            const target = nearbyFriendlies[0];
            this.entity.setTarget(target.x, target.y);
            return;
        }
        
        // 3. Engage nearby enemies if strong enough
        const nearbyEnemies = this.findNearbyEnemyUnits();
        if (nearbyEnemies.length > 0 && this.entity.hp > this.entity.maxHp * 0.6) {
            this.entity.autonomousMode = 'engage';
            this.entity.setAttackTarget(nearbyEnemies[0]);
            return;
        }
        
        // 4. Default: patrol/defend current area
        this.entity.autonomousMode = 'patrol';
        this.entity.state = 'patrolling';
    }

    /**
     * Find nearby friendly units for regrouping
     * @returns {Array} Array of nearby friendly units
     */
    findNearbyFriendlyUnits() {
        // This would need access to the simulation's unit list
        // For now, return empty array - would be implemented with simulation reference
        return [];
    }

    /**
     * Find nearby enemy units for engagement
     * @returns {Array} Array of nearby enemy units
     */
    findNearbyEnemyUnits() {
        // This would need access to the simulation's unit list
        // For now, return empty array - would be implemented with simulation reference
        return [];
    }

    /**
     * Return unit to commanded state when conditions improve
     */
    returnToCommand() {
        if (this.entity && this.entity.isAutonomous) {
            console.log(`[C&C] ${this.entity.type} returning to command structure`);
            this.entity.isAutonomous = false;
            this.entity.autonomousMode = null;
            this.autonomyLevel = 0;
            
            // Clear autonomy triggers
            Object.keys(this.autonomyTriggers).forEach(key => {
                this.autonomyTriggers[key] = false;
            });
        }
    }

    /**
     * Update command node state
     * @param {ComputroniumManager} computroniumManager - Team's computronium manager
     */
    update(computroniumManager) {
        // Update autonomy level based on current conditions
        this.updateAutonomyLevel(computroniumManager);
        
        // Recalculate authority and latency (autonomy affects these)
        this.calculateAuthority(computroniumManager);
        this.calculateCommandLatency(computroniumManager);
        
        // Remove dead subordinates
        this.subordinates = this.subordinates.filter(sub => sub.entity && sub.entity.hp > 0);
        
        // If conditions improve, autonomous units can return to command
        if (this.entity && this.entity.isAutonomous && this.autonomyLevel < 0.1) {
            this.returnToCommand();
        }
    }

    /**
     * Calculate command radius based on rank and veterancy
     * @param {CommandNode} node - Command node
     * @returns {number} Command radius in world units
     */
    calculateCommandRadius(node) {
        let baseRadius = 0;
        
        // Base radius by rank
        switch(node.rank) {
            case 5: // Supreme Commander
                baseRadius = 400;
                break;
            case 4: // Field Commander
                baseRadius = 300;
                break;
            case 3: // Major
                baseRadius = 200;
                break;
            case 2: // Captain
                baseRadius = 150;
                break;
            case 1: // Lieutenant
                baseRadius = 120;
                break;
            default: // Sergeant and below
                baseRadius = 80;
        }
        
        // Veterancy bonus
        const veterancyBonus = this.calculateVeterancyRadiusBonus(node.entity.veterancyLevel);
        
        // Computronium bonus
        const computroniumBonus = node.entity.computroniumCoreLevel * 20;
        
        // Context modifier
        const contextModifier = this.calculateContextRadiusModifier(node);
        
        return baseRadius + veterancyBonus + computroniumBonus + contextModifier;
    }

    /**
     * Calculate veterancy-based radius bonus
     * @param {string} veterancyLevel - Unit's veterancy level
     * @returns {number} Radius bonus
     */
    calculateVeterancyRadiusBonus(veterancyLevel) {
        switch(veterancyLevel) {
            case 'HERO': return 60;
            case 'ELITE': return 40;
            case 'VETERAN': return 20;
            case 'REGULAR': return 10;
            default: return 0;
        }
    }

    /**
     * Calculate context-based radius modifier
     * @param {CommandNode} node - Command node
     * @returns {number} Radius modifier
     */
    calculateContextRadiusModifier(node) {
        let modifier = 0;
        
        // Combat operations bonus
        if (this.isInCombatZone(node)) {
            modifier += 30;
        }
        
        // Defensive position bonus
        if (this.isInDefensivePosition(node)) {
            modifier += 20;
        }
        
        // Emergency situation bonus
        if (this.isInEmergencySituation(node)) {
            modifier += 50;
        }
        
        // Economic mission bonus
        if (this.isInEconomicMission(node)) {
            modifier += 25;
        }
        
        return modifier;
    }

    /**
     * Check if node is in a combat zone
     * @param {CommandNode} node - Command node
     * @returns {boolean}
     */
    isInCombatZone(node) {
        const nearbyEnemies = this.findNearbyUnits(node.entity, 200)
            .filter(u => u.team !== node.entity.team);
        return nearbyEnemies.length > 0;
    }

    /**
     * Check if node is in a defensive position
     * @param {CommandNode} node - Command node
     * @returns {boolean}
     */
    isInDefensivePosition(node) {
        // Check if unit is near defensive structures
        const nearbyStructures = this.findNearbyUnits(node.entity, 150)
            .filter(u => u.type.isDefensiveStructure);
        return nearbyStructures.length > 0;
    }

    /**
     * Check if node is in an emergency situation
     * @param {CommandNode} node - Command node
     * @returns {boolean}
     */
    isInEmergencySituation(node) {
        // Check for critical health or overwhelming enemy presence
        const healthRatio = node.entity.hp / node.entity.maxHp;
        const nearbyEnemies = this.findNearbyUnits(node.entity, 100)
            .filter(u => u.team !== node.entity.team);
        
        return healthRatio < 0.3 || nearbyEnemies.length > 3;
    }

    /**
     * Check if node is in an economic mission
     * @param {CommandNode} node - Command node
     * @returns {boolean}
     */
    isInEconomicMission(node) {
        // Check if unit is near resource nodes or economic structures
        const nearbyResources = this.findNearbyUnits(node.entity, 150)
            .filter(u => u.type.isResourceNode || u.type.isEconomicStructure);
        return nearbyResources.length > 0;
    }

    /**
     * Calculate context-sensitive authority modifier
     * @param {CommandNode} node - Command node
     * @returns {number} Authority modifier
     */
    calculateContextAuthorityModifier(node) {
        let modifier = 0;
        
        // Combat operations bonus
        if (this.isInCombatZone(node)) {
            modifier += node.entity.type.isCombatUnit ? 3 : 1;
        }
        
        // Economic missions bonus
        if (this.isInEconomicMission(node)) {
            modifier += node.entity.type.isSupportUnit ? 3 : 1;
        }
        
        // Defensive positions bonus
        if (this.isInDefensivePosition(node)) {
            modifier += 2;
        }
        
        // Emergency situations bonus
        if (this.isInEmergencySituation(node)) {
            modifier += node.entity.commandFitness === 'FULL_COMMAND' ? 5 : 0;
        }
        
        return modifier;
    }

    /**
     * Update node's authority with context modifiers
     * @param {CommandNode} node - Command node
     */
    updateNodeAuthority(node) {
        // Get base authority
        const baseAuthority = node.entity.calculateEffectiveAuthority();
        
        // Add context modifier
        const contextModifier = this.calculateContextAuthorityModifier(node);
        
        // Update node authority
        node.authority = baseAuthority + contextModifier;
        
        // Update cache
        this.authorityCache.set(node.entity.id, {
            value: node.authority,
            timestamp: performance.now()
        });
    }
}

/**
 * Enhanced Command Hierarchy Manager with Computronium integration
 */
export class EnhancedCommandHierarchy {
    constructor(team, computroniumManager) {
        this.team = team;
        this.computroniumManager = computroniumManager;
        this.commandNodes = new Map(); // Entity ID -> CommandNode
        this.rootCommanders = []; // Top-level commanders
        this.totalAuthority = 0;
        this.commandEffectiveness = 1.0;

        // Performance optimization caches
        this.authorityCache = new Map(); // Entity ID -> {authority, timestamp}
        this.rangeCache = new Map(); // Entity ID -> {range, timestamp}
        this.tacticalCache = new Map(); // Entity ID -> {analysis, timestamp}
        this.cacheTimeout = 5000; // 5 seconds cache timeout

        // Spatial partitioning for efficient neighbor lookups
        this.spatialGrid = new Map(); // Grid cell -> Set of entity IDs
        this.gridCellSize = 100; // World units per grid cell

        // Event-driven update tracking
        this.dirtyNodes = new Set(); // Nodes that need updates
        this.lastFullUpdate = 0;
        this.fullUpdateInterval = 10000; // 10 seconds between full updates

        console.log(`[C&C] Enhanced command hierarchy initialized for team ${team}`);
    }

    /**
     * Register a unit or building in the command hierarchy
     * @param {Unit|Building} entity - Entity to register
     * @param {number} rank - Command rank (1-5)
     * @returns {CommandNode} Created command node
     */
    registerEntity(entity, rank = 1) {
        const node = new CommandNode(entity, rank);
        this.commandNodes.set(entity.id, node);
        
        // Associate with Computronium core if entity has one
        if (this.computroniumManager) {
            const core = this.computroniumManager.cores.find(c => c.owner === entity);
            if (core) {
                node.computroniumCore = core;
            }
        }
        
        // Auto-assign to hierarchy
        this.autoAssignToHierarchy(node);
        
        return node;
    }

    /**
     * Automatically assign a command node to the appropriate place in hierarchy
     * @param {CommandNode} node - Node to assign
     */
    autoAssignToHierarchy(node) {
        // Find best superior based on rank, proximity, and authority
        let bestSuperior = null;
        let bestScore = -1;
        
        for (const [entityId, candidateNode] of this.commandNodes) {
            if (candidateNode === node || candidateNode.rank <= node.rank) continue;
            
            // Calculate score based on rank difference, proximity, and authority
            const rankDiff = candidateNode.rank - node.rank;
            const distance = this.getDistance(node.entity, candidateNode.entity);
            const proximityScore = Math.max(0, 1000 - distance) / 1000; // Closer is better
            const authorityScore = candidateNode.authority / 1000;
            
            const score = rankDiff + proximityScore + authorityScore;
            
            if (score > bestScore) {
                bestScore = score;
                bestSuperior = candidateNode;
            }
        }
        
        if (bestSuperior) {
            bestSuperior.addSubordinate(node);
        } else {
            // No superior found, make this a root commander
            this.rootCommanders.push(node);
        }
    }

    /**
     * Get distance between two entities
     * @param {Unit|Building} entity1 - First entity
     * @param {Unit|Building} entity2 - Second entity
     * @returns {number} Distance
     */
    getDistance(entity1, entity2) {
        const dx = entity1.x - entity2.x;
        const dy = entity1.y - entity2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Remove an entity from the command hierarchy
     * @param {Unit|Building} entity - Entity to remove
     */
    unregisterEntity(entity) {
        const node = this.commandNodes.get(entity.id);
        if (!node) return;
        
        // Reassign subordinates to this node's superior
        if (node.superior) {
            for (const subordinate of node.subordinates) {
                node.superior.addSubordinate(subordinate);
            }
            node.superior.removeSubordinate(node);
        } else {
            // This was a root commander, promote the highest-ranking subordinate
            if (node.subordinates.length > 0) {
                const replacement = node.subordinates.reduce((best, current) => 
                    current.rank > best.rank ? current : best
                );
                
                // Remove replacement from subordinates and make it a root commander
                node.removeSubordinate(replacement);
                this.rootCommanders.push(replacement);
                
                // Reassign remaining subordinates to replacement
                for (const subordinate of node.subordinates) {
                    replacement.addSubordinate(subordinate);
                }
            }
            
            // Remove from root commanders
            const rootIndex = this.rootCommanders.indexOf(node);
            if (rootIndex !== -1) {
                this.rootCommanders.splice(rootIndex, 1);
            }
        }
        
        this.commandNodes.delete(entity.id);
        console.log(`[C&C] ${entity.type} removed from command hierarchy`);
    }

    /**
     * Issue a strategic command from the highest authority
     * @param {string} commandType - Type of command
     * @param {Object} parameters - Command parameters
     * @returns {boolean} True if command was issued
     */
    issueStrategicCommand(commandType, parameters) {
        // Find highest authority commander
        let topCommander = null;
        let highestAuthority = 0;
        
        for (const commander of this.rootCommanders) {
            if (commander.authority > highestAuthority) {
                highestAuthority = commander.authority;
                topCommander = commander;
            }
        }
        
        if (topCommander) {
            return topCommander.issueCommand(commandType, parameters, this.computroniumManager);
        }
        
        return false;
    }

    /**
     * Get cached authority value or calculate new one
     * @param {CommandNode} node - Node to get authority for
     * @returns {number} Authority value
     */
    getCachedAuthority(node) {
        const now = performance.now();
        const cached = this.authorityCache.get(node.entity.id);
        
        if (cached && (now - cached.timestamp) < this.cacheTimeout) {
            return cached.authority;
        }

        const authority = node.calculateAuthority(this.computroniumManager);
        this.authorityCache.set(node.entity.id, {
            authority,
            timestamp: now
        });

        return authority;
    }

    /**
     * Get cached command range or calculate new one
     * @param {CommandNode} node - Node to get range for
     * @returns {number} Command range
     */
    getCachedRange(node) {
        const now = performance.now();
        const cached = this.rangeCache.get(node.entity.id);
        
        if (cached && (now - cached.timestamp) < this.cacheTimeout) {
            return cached.range;
        }

        const range = this.getCommandRange(node);
        this.rangeCache.set(node.entity.id, {
            range,
            timestamp: now
        });

        return range;
    }

    /**
     * Get cached tactical analysis or calculate new one
     * @param {CommandNode} node - Node to analyze
     * @returns {Object} Tactical analysis
     */
    getCachedTacticalAnalysis(node) {
        const now = performance.now();
        const cached = this.tacticalCache.get(node.entity.id);
        
        if (cached && (now - cached.timestamp) < this.cacheTimeout) {
            return cached.analysis;
        }

        const analysis = this.performTacticalAnalysis(node);
        this.tacticalCache.set(node.entity.id, {
            analysis,
            timestamp: now
        });

        return analysis;
    }

    /**
     * Update spatial grid for efficient neighbor lookups
     * @param {Unit} unit - Unit to update
     */
    updateSpatialGrid(unit) {
        const cellX = Math.floor(unit.x / this.gridCellSize);
        const cellY = Math.floor(unit.y / this.gridCellSize);
        const cellKey = `${cellX},${cellY}`;

        // Remove from old cell
        for (const [key, units] of this.spatialGrid) {
            units.delete(unit.id);
            if (units.size === 0) {
                this.spatialGrid.delete(key);
            }
        }

        // Add to new cell
        if (!this.spatialGrid.has(cellKey)) {
            this.spatialGrid.set(cellKey, new Set());
        }
        this.spatialGrid.get(cellKey).add(unit.id);
    }

    /**
     * Find nearby units using spatial partitioning
     * @param {Unit} unit - Unit to check from
     * @param {number} range - Search range
     * @returns {Array} Array of nearby units
     */
    findNearbyUnits(unit, range) {
        const cellX = Math.floor(unit.x / this.gridCellSize);
        const cellY = Math.floor(unit.y / this.gridCellSize);
        const cellRange = Math.ceil(range / this.gridCellSize);
        
        const nearbyUnits = new Set();
        
        // Check surrounding cells
        for (let x = cellX - cellRange; x <= cellX + cellRange; x++) {
            for (let y = cellY - cellRange; y <= cellY + cellRange; y++) {
                const cellKey = `${x},${y}`;
                const cellUnits = this.spatialGrid.get(cellKey);
                
                if (cellUnits) {
                    for (const unitId of cellUnits) {
                        const otherUnit = this.commandNodes.get(unitId)?.entity;
                        if (otherUnit && otherUnit !== unit) {
                            const distance = unit.getDistance(otherUnit);
                            if (distance <= range) {
                                nearbyUnits.add(otherUnit);
                            }
                        }
                    }
                }
            }
        }
        
        return Array.from(nearbyUnits);
    }

    /**
     * Mark a node as needing updates
     * @param {CommandNode} node - Node to mark
     */
    markDirty(node) {
        this.dirtyNodes.add(node);
    }

    /**
     * Update the command hierarchy efficiently
     * @param {number} deltaTime - Time step
     */
    update(deltaTime) {
        const now = performance.now();
        
        // Full update if needed
        if (now - this.lastFullUpdate > this.fullUpdateInterval) {
            this.performFullUpdate();
            this.lastFullUpdate = now;
            return;
        }

        // Update only dirty nodes
        for (const node of this.dirtyNodes) {
            this.updateNode(node);
        }
        this.dirtyNodes.clear();

        // Update spatial grid for all units
        for (const [_, node] of this.commandNodes) {
            this.updateSpatialGrid(node.entity);
        }
    }

    /**
     * Perform a full update of the command hierarchy
     */
    performFullUpdate() {
        this.totalAuthority = 0;
        
        // Update all command nodes
        for (const [entityId, node] of this.commandNodes) {
            this.updateNode(node);
        }
        
        // Calculate overall command effectiveness
        const nodeCount = this.commandNodes.size;
        this.commandEffectiveness = nodeCount > 0 ? this.totalAuthority / (nodeCount * 100) : 0;
        
        // Clear caches
        this.authorityCache.clear();
        this.rangeCache.clear();
        this.tacticalCache.clear();
    }

    /**
     * Update a single command node
     * @param {CommandNode} node - Node to update
     */
    updateNode(node) {
        // Skip dead nodes
        if (!node.entity || node.entity.hp <= 0) {
            this.unregisterEntity(node.entity);
            return;
        }

        // Update authority and range
        const authority = this.getCachedAuthority(node);
        const range = this.getCachedRange(node);
        this.totalAuthority += authority;

        // Get nearby units for aura and tactical analysis
        const nearbyUnits = this.findNearbyUnits(node.entity, range);

        // Apply command aura effects
        this.applyCommandAura(node, nearbyUnits);

        // Perform predictive analysis
        const predictions = this.performPredictiveAnalysis(node);

        // Apply tactical recommendations
        this.applyTacticalRecommendations(node, predictions);

        // Validate command chain
        const validation = this.validateCommandChain(node);
        if (!validation.isValid) {
            this.handleCommandChainIssues(node, validation);
        }

        // Update computronium allocation
        this.updateComputroniumAllocation(node, predictions);

        // Update command efficiency
        this.updateCommandEfficiency(node);

        // Check for promotion opportunities
        this.checkPromotionOpportunities(node);

        // Update visualization data
        this.updateVisualizationData(node);
    }

    /**
     * Update computronium allocation based on predictions
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictive analysis
     */
    updateComputroniumAllocation(node, predictions) {
        if (!node.computroniumCore) return;

        const needs = predictions.resourceProjections.computronium;
        const currentLevel = node.entity.computroniumCoreLevel;

        // Request more computronium if needed
        if (needs > currentLevel * 2) {
            this.requestComputroniumAllocation(node, needs);
        }

        // Optimize focus mode based on predictions
        this.optimizeFocusMode(node, predictions);
    }

    /**
     * Request additional computronium allocation
     * @param {CommandNode} node - Command node
     * @param {number} amount - Amount needed
     */
    requestComputroniumAllocation(node, amount) {
        if (this.computroniumManager) {
            const status = this.computroniumManager.getStatus();
            if (status.availableComputronium >= amount) {
                this.computroniumManager.spendComputronium(amount, `command_${node.entity.id}`);
                node.entity.computroniumCoreLevel = Math.min(5, node.entity.computroniumCoreLevel + 1);
            }
        }
    }

    /**
     * Optimize focus mode based on predictions
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictive analysis
     */
    optimizeFocusMode(node, predictions) {
        const immediateThreats = predictions.threatPredictions.filter(p => p.timeToContact < 10);
        const tacticalOpportunities = predictions.tacticalOpportunities.length;

        // Switch to defensive mode if under immediate threat
        if (immediateThreats.length > 0) {
            node.entity.changeCoreFocusMode('DEFENSIVE');
        }
        // Switch to offensive mode if tactical opportunities exist
        else if (tacticalOpportunities > 0) {
            node.entity.changeCoreFocusMode('OFFENSIVE');
        }
        // Default to C&C mode for command units
        else if (node.subordinates.length > 0) {
            node.entity.changeCoreFocusMode('C_C');
        }
    }

    /**
     * Update command efficiency
     * @param {CommandNode} node - Command node
     */
    updateCommandEfficiency(node) {
        // Calculate base efficiency
        let efficiency = 1.0;

        // Authority impact
        efficiency *= (0.5 + (node.authority / 200));

        // Computronium impact
        if (node.computroniumCore) {
            efficiency *= (1 + (node.entity.computroniumCoreLevel * 0.1));
        }

        // Subordinate count impact
        const maxSubordinates = this.calculateMaxSubordinates(node);
        const subordinateRatio = node.subordinates.length / maxSubordinates;
        efficiency *= (1 - (subordinateRatio * 0.3)); // Penalty for being near capacity

        // Update node efficiency
        node.commandEfficiency = Math.max(0.1, Math.min(1.0, efficiency));
    }

    /**
     * Check for promotion opportunities
     * @param {CommandNode} node - Command node
     */
    checkPromotionOpportunities(node) {
        // Check veterancy level
        if (node.entity.veterancyLevel === 'HERO') return;

        // Check command success rate
        const successRate = node.commandSuccesses / (node.commandSuccesses + node.commandFailures);
        if (successRate < 0.7) return;

        // Check subordinate count
        const maxSubordinates = this.calculateMaxSubordinates(node);
        if (node.subordinates.length < maxSubordinates * 0.8) return;

        // Promote if all conditions are met
        this.promoteNode(node);
    }

    /**
     * Promote a command node
     * @param {CommandNode} node - Node to promote
     */
    promoteNode(node) {
        node.rank = Math.min(5, node.rank + 1);
        node.entity.veterancyLevel = this.calculateNextVeterancyLevel(node.entity.veterancyLevel);
        
        // Update authority and range
        this.authorityCache.delete(node.entity.id);
        this.rangeCache.delete(node.entity.id);
        
        // Notify of promotion
        console.log(`[C&C] ${node.entity.type} promoted to rank ${node.rank} (${node.entity.veterancyLevel})`);
    }

    /**
     * Calculate next veterancy level
     * @param {string} currentLevel - Current veterancy level
     * @returns {string} Next veterancy level
     */
    calculateNextVeterancyLevel(currentLevel) {
        const levels = ['GREEN', 'REGULAR', 'VETERAN', 'ELITE', 'HERO'];
        const currentIndex = levels.indexOf(currentLevel);
        return levels[Math.min(levels.length - 1, currentIndex + 1)];
    }

    /**
     * Update visualization data for a node
     * @param {CommandNode} node - Command node
     */
    updateVisualizationData(node) {
        // Update command range visualization
        node.visualizationData = {
            commandRange: this.getCachedRange(node),
            auraStrength: this.calculateAuraStrength(node),
            authority: node.authority,
            efficiency: node.commandEfficiency,
            veterancyLevel: node.entity.veterancyLevel,
            focusMode: node.entity.coreFocusMode,
            subordinateCount: node.subordinates.length,
            maxSubordinates: this.calculateMaxSubordinates(node)
        };
    }

    /**
     * Get command hierarchy statistics
     * @returns {Object} Statistics
     */
    getStats() {
        return {
            totalNodes: this.commandNodes.size,
            rootCommanders: this.rootCommanders.length,
            totalAuthority: this.totalAuthority,
            commandEffectiveness: this.commandEffectiveness,
            averageLatency: this.getAverageCommandLatency(),
            computroniumBonus: this.computroniumManager ? this.computroniumManager.getStatus().c2EfficiencyBonus : 1.0
        };
    }

    /**
     * Get average command latency across all nodes
     * @returns {number} Average latency in seconds
     */
    getAverageCommandLatency() {
        if (this.commandNodes.size === 0) return 0;
        
        let totalLatency = 0;
        for (const [entityId, node] of this.commandNodes) {
            totalLatency += node.commandLatency;
        }
        
        return totalLatency / this.commandNodes.size;
    }

    /**
     * Validate the entire command chain for a unit
     * @param {CommandNode} node - Node to validate
     * @returns {Object} Validation results
     */
    validateCommandChain(node) {
        const results = {
            isValid: true,
            issues: [],
            recommendations: []
        };

        // Check authority chain
        let current = node;
        while (current.superior) {
            if (current.superior.authority <= current.authority) {
                results.isValid = false;
                results.issues.push(`Authority chain break: ${current.entity.type} has higher authority than superior`);
                results.recommendations.push('Consider promoting unit or reassigning command');
            }
            current = current.superior;
        }

        // Check command range
        if (node.superior) {
            const distance = node.getDistance(node.superior.entity);
            const maxRange = this.getCommandRange(node);
            if (distance > maxRange) {
                results.isValid = false;
                results.issues.push(`Command range exceeded: ${distance.toFixed(0)} > ${maxRange}`);
                results.recommendations.push('Move units closer or establish intermediate command');
            }
        }

        // Check computational load
        const subordinateCount = node.subordinates.length;
        const maxSubordinates = this.calculateMaxSubordinates(node);
        if (subordinateCount > maxSubordinates) {
            results.isValid = false;
            results.issues.push(`Command overload: ${subordinateCount} > ${maxSubordinates} max subordinates`);
            results.recommendations.push('Split command or increase computronium allocation');
        }

        return results;
    }

    /**
     * Calculate maximum number of subordinates a node can effectively command
     * @param {CommandNode} node - Command node to check
     * @returns {number} Maximum number of subordinates
     */
    calculateMaxSubordinates(node) {
        let baseLimit = 10; // Base limit for rank 1

        // Rank multiplier (exponential scaling)
        baseLimit *= Math.pow(2, node.rank - 1);

        // Computronium bonus
        if (node.computroniumCore) {
            const coreLevel = node.entity.computroniumCoreLevel;
            const focusMode = node.entity.coreFocusMode;
            
            if (focusMode === 'C_C') {
                baseLimit *= (1 + (coreLevel * 0.5)); // C&C mode gets 50% more per level
            } else {
                baseLimit *= (1 + (coreLevel * 0.2)); // Other modes get 20% more per level
            }
        }

        // Command fitness modifier
        baseLimit *= node.commandFitness;

        return Math.floor(baseLimit);
    }

    /**
     * Get effective command range for a node
     * @param {CommandNode} node - Command node to check
     * @returns {number} Command range in world units
     */
    getCommandRange(node) {
        let baseRange = COMPUTRONIUM_CONFIG.COMMAND_RANGES.STRATEGIC;

        // Rank-based range increase
        baseRange *= (1 + (node.rank - 1) * 0.2);

        // Computronium enhancement
        if (node.computroniumCore) {
            const coreLevel = node.entity.computroniumCoreLevel;
            const focusMode = node.entity.coreFocusMode;
            
            if (focusMode === 'C_C') {
                baseRange *= (1 + (coreLevel * 0.3)); // C&C mode gets 30% more range per level
            }
        }

        // Veterancy bonus
        if (node.entity.veterancyLevel === 'VETERAN') baseRange *= 1.1;
        if (node.entity.veterancyLevel === 'ELITE') baseRange *= 1.2;
        if (node.entity.veterancyLevel === 'HERO') baseRange *= 1.3;

        return Math.floor(baseRange);
    }

    /**
     * Perform advanced tactical calculations for a command node
     * @param {CommandNode} node - Command node to analyze
     * @returns {Object} Tactical analysis results
     */
    performTacticalAnalysis(node) {
        const analysis = {
            threatLevel: 0,
            tacticalValue: 0,
            recommendedActions: [],
            resourceEfficiency: 0
        };

        // Calculate threat level based on nearby enemies
        const nearbyEnemies = this.findNearbyEnemies(node.entity);
        analysis.threatLevel = this.calculateThreatLevel(nearbyEnemies);

        // Calculate tactical value based on position and objectives
        analysis.tacticalValue = this.calculateTacticalValue(node);

        // Generate recommended actions
        analysis.recommendedActions = this.generateTacticalRecommendations(node, analysis);

        // Calculate resource efficiency
        analysis.resourceEfficiency = this.calculateResourceEfficiency(node);

        return analysis;
    }

    /**
     * Find nearby enemy units
     * @param {Unit} unit - Unit to check from
     * @returns {Array} Array of nearby enemy units
     */
    findNearbyEnemies(unit) {
        // Implementation would use spatial partitioning for efficiency
        return []; // Placeholder
    }

    /**
     * Calculate threat level based on nearby enemies
     * @param {Array} enemies - Array of enemy units
     * @returns {number} Threat level (0-1)
     */
    calculateThreatLevel(enemies) {
        let threat = 0;
        for (const enemy of enemies) {
            threat += enemy.combatPower * (1 / (1 + enemy.getDistance(this.entity)));
        }
        return Math.min(1, threat / 1000); // Normalize to 0-1
    }

    /**
     * Calculate tactical value of a position
     * @param {CommandNode} node - Node to analyze
     * @returns {number} Tactical value (0-1)
     */
    calculateTacticalValue(node) {
        let value = 0;

        // Position value (height, cover, etc.)
        value += this.calculatePositionValue(node.entity);

        // Strategic importance
        value += this.calculateStrategicImportance(node);

        // Resource control
        value += this.calculateResourceControl(node);

        return Math.min(1, value);
    }

    /**
     * Generate tactical recommendations
     * @param {CommandNode} node - Node to analyze
     * @param {Object} analysis - Tactical analysis results
     * @returns {Array} Array of recommended actions
     */
    generateTacticalRecommendations(node, analysis) {
        const recommendations = [];

        // High threat recommendations
        if (analysis.threatLevel > 0.7) {
            recommendations.push({
                type: 'DEFENSIVE',
                priority: 'HIGH',
                action: 'Consolidate forces and establish defensive perimeter'
            });
        }

        // High value position recommendations
        if (analysis.tacticalValue > 0.8) {
            recommendations.push({
                type: 'OFFENSIVE',
                priority: 'HIGH',
                action: 'Secure and fortify position'
            });
        }

        // Resource efficiency recommendations
        if (analysis.resourceEfficiency < 0.5) {
            recommendations.push({
                type: 'LOGISTICAL',
                priority: 'MEDIUM',
                action: 'Optimize resource allocation and supply lines'
            });
        }

        return recommendations;
    }

    /**
     * Calculate resource efficiency
     * @param {CommandNode} node - Node to analyze
     * @returns {number} Resource efficiency (0-1)
     */
    calculateResourceEfficiency(node) {
        let efficiency = 1.0;

        // Check computronium usage
        if (node.computroniumCore) {
            const coreLevel = node.entity.computroniumCoreLevel;
            const focusMode = node.entity.coreFocusMode;
            
            // C&C mode should be using computronium effectively
            if (focusMode === 'C_C' && node.computroniumBonus < coreLevel) {
                efficiency *= 0.8;
            }
        }

        // Check command efficiency
        if (node.commandFitness < 0.7) {
            efficiency *= 0.9;
        }

        // Check subordinate management
        const maxSubordinates = this.calculateMaxSubordinates(node);
        if (node.subordinates.length > maxSubordinates * 0.8) {
            efficiency *= 0.85;
        }

        return efficiency;
    }

    /**
     * Apply command aura effects to nearby units
     * @param {CommandNode} node - Command node generating aura
     * @param {Array} nearbyUnits - Array of nearby units
     */
    applyCommandAura(node, nearbyUnits) {
        const auraStrength = this.calculateAuraStrength(node);
        const auraRange = this.getCachedRange(node);

        for (const unit of nearbyUnits) {
            if (unit.team === node.entity.team) {
                const distance = node.entity.getDistance(unit);
                if (distance <= auraRange) {
                    const effectiveness = 1 - (distance / auraRange);
                    this.applyAuraEffects(unit, auraStrength * effectiveness, node);
                }
            }
        }
    }

    /**
     * Calculate command aura strength
     * @param {CommandNode} node - Command node
     * @returns {number} Aura strength (0-1)
     */
    calculateAuraStrength(node) {
        let strength = 0.5; // Base strength

        // Authority bonus
        strength += node.authority * 0.01;

        // Computronium enhancement
        if (node.computroniumCore) {
            const coreLevel = node.entity.computroniumCoreLevel;
            const focusMode = node.entity.coreFocusMode;
            
            if (focusMode === 'C_C') {
                strength *= (1 + (coreLevel * 0.2)); // C&C mode gets 20% more per level
            }
        }

        // Veterancy bonus
        if (node.entity.veterancyLevel === 'VETERAN') strength *= 1.1;
        if (node.entity.veterancyLevel === 'ELITE') strength *= 1.2;
        if (node.entity.veterancyLevel === 'HERO') strength *= 1.3;

        return Math.min(1, strength);
    }

    /**
     * Apply aura effects to a unit
     * @param {Unit} unit - Unit to affect
     * @param {number} strength - Aura strength
     * @param {CommandNode} source - Source command node
     */
    applyAuraEffects(unit, strength, source) {
        // Combat effectiveness
        unit.accuracy *= (1 + (strength * 0.1));
        unit.damageMultiplier *= (1 + (strength * 0.05));
        unit.defenseMultiplier *= (1 + (strength * 0.05));

        // Command efficiency
        unit.commandEfficiency *= (1 + (strength * 0.15));

        // Resource efficiency
        unit.resourceEfficiency *= (1 + (strength * 0.1));

        // Track aura source for visualization
        unit.activeAuras = unit.activeAuras || new Map();
        unit.activeAuras.set(source.entity.id, {
            strength,
            source: source.entity,
            timestamp: performance.now()
        });
    }

    /**
     * Perform predictive C&C analysis
     * @param {CommandNode} node - Command node to analyze
     * @returns {Object} Predictive analysis results
     */
    performPredictiveAnalysis(node) {
        const predictions = {
            threatPredictions: [],
            tacticalOpportunities: [],
            resourceProjections: [],
            recommendedActions: []
        };

        // Get current tactical state
        const currentAnalysis = this.getCachedTacticalAnalysis(node);
        const nearbyUnits = this.findNearbyUnits(node.entity, this.getCachedRange(node));

        // Predict enemy movements and threats
        predictions.threatPredictions = this.predictEnemyMovements(node, nearbyUnits);

        // Identify tactical opportunities
        predictions.tacticalOpportunities = this.identifyTacticalOpportunities(node, currentAnalysis);

        // Project resource needs
        predictions.resourceProjections = this.projectResourceNeeds(node, predictions);

        // Generate recommended actions
        predictions.recommendedActions = this.generatePredictiveRecommendations(node, predictions);

        return predictions;
    }

    /**
     * Predict enemy movements and threats
     * @param {CommandNode} node - Command node
     * @param {Array} nearbyUnits - Nearby units
     * @returns {Array} Array of threat predictions
     */
    predictEnemyMovements(node, nearbyUnits) {
        const predictions = [];
        const enemyUnits = nearbyUnits.filter(u => u.team !== node.entity.team);

        for (const enemy of enemyUnits) {
            // Predict movement based on current velocity and behavior
            const predictedPosition = this.predictUnitPosition(enemy, 5); // 5 seconds ahead

            // Calculate threat level at predicted position
            const threatLevel = this.calculateThreatLevel([enemy]);
            const distanceToPredicted = node.entity.getDistance(predictedPosition);

            predictions.push({
                unit: enemy,
                predictedPosition,
                threatLevel,
                distanceToPredicted,
                timeToContact: distanceToPredicted / (enemy.speed || 1)
            });
        }

        return predictions;
    }

    /**
     * Predict unit position based on current state
     * @param {Unit} unit - Unit to predict
     * @param {number} secondsAhead - How far ahead to predict
     * @returns {Object} Predicted position {x, y}
     */
    predictUnitPosition(unit, secondsAhead) {
        // Basic prediction using current velocity
        return {
            x: unit.x + (unit.velocityX * secondsAhead),
            y: unit.y + (unit.velocityY * secondsAhead)
        };
    }

    /**
     * Identify tactical opportunities
     * @param {CommandNode} node - Command node
     * @param {Object} currentAnalysis - Current tactical analysis
     * @returns {Array} Array of tactical opportunities
     */
    identifyTacticalOpportunities(node, currentAnalysis) {
        const opportunities = [];

        // Check for flanking opportunities
        const flankingOpportunities = this.findFlankingOpportunities(node);
        opportunities.push(...flankingOpportunities);

        // Check for resource control opportunities
        const resourceOpportunities = this.findResourceOpportunities(node);
        opportunities.push(...resourceOpportunities);

        // Check for defensive opportunities
        const defensiveOpportunities = this.findDefensiveOpportunities(node);
        opportunities.push(...defensiveOpportunities);

        return opportunities;
    }

    /**
     * Project resource needs based on predictions
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictive analysis
     * @returns {Object} Resource projections
     */
    projectResourceNeeds(node, predictions) {
        const projections = {
            computronium: 0,
            energy: 0,
            materials: 0
        };

        // Calculate computronium needs
        projections.computronium = this.calculateComputroniumNeeds(node, predictions);

        // Calculate energy needs
        projections.energy = this.calculateEnergyNeeds(node, predictions);

        // Calculate material needs
        projections.materials = this.calculateMaterialNeeds(node, predictions);

        return projections;
    }

    /**
     * Generate recommendations based on predictive analysis
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictive analysis
     * @returns {Array} Array of recommended actions
     */
    generatePredictiveRecommendations(node, predictions) {
        const recommendations = [];

        // Handle immediate threats
        const immediateThreats = predictions.threatPredictions.filter(p => p.timeToContact < 10);
        if (immediateThreats.length > 0) {
            recommendations.push({
                type: 'DEFENSIVE',
                priority: 'HIGH',
                action: 'Prepare defensive positions',
                details: immediateThreats
            });
        }

        // Handle tactical opportunities
        for (const opportunity of predictions.tacticalOpportunities) {
            recommendations.push({
                type: 'OFFENSIVE',
                priority: 'MEDIUM',
                action: 'Exploit tactical opportunity',
                details: opportunity
            });
        }

        // Handle resource needs
        if (predictions.resourceProjections.computronium > node.entity.computroniumCoreLevel * 2) {
            recommendations.push({
                type: 'LOGISTICAL',
                priority: 'MEDIUM',
                action: 'Increase computronium allocation',
                details: predictions.resourceProjections
            });
        }

        return recommendations;
    }

    /**
     * Resolve authority conflicts between nodes
     * @param {CommandNode} node1 - First command node
     * @param {CommandNode} node2 - Second command node
     * @returns {CommandNode} Node with higher effective authority
     */
    resolveAuthorityConflict(node1, node2) {
        // If authorities are equal, use tie-breaking
        if (node1.authority === node2.authority) {
            return this.breakAuthorityTie(node1, node2);
        }
        
        return node1.authority > node2.authority ? node1 : node2;
    }

    /**
     * Break authority tie between nodes
     * @param {CommandNode} node1 - First command node
     * @param {CommandNode} node2 - Second command node
     * @returns {CommandNode} Node that wins the tie
     */
    breakAuthorityTie(node1, node2) {
        // 1. Health Percentage
        const health1 = node1.entity.hp / node1.entity.maxHp;
        const health2 = node2.entity.hp / node2.entity.maxHp;
        if (health1 !== health2) {
            return health1 > health2 ? node1 : node2;
        }
        
        // 2. Veterancy Level
        const veterancyOrder = ['GREEN', 'REGULAR', 'VETERAN', 'ELITE', 'HERO'];
        const veterancy1 = veterancyOrder.indexOf(node1.entity.veterancyLevel);
        const veterancy2 = veterancyOrder.indexOf(node2.entity.veterancyLevel);
        if (veterancy1 !== veterancy2) {
            return veterancy1 > veterancy2 ? node1 : node2;
        }
        
        // 3. Time in Service
        const time1 = node1.entity.survivalTime;
        const time2 = node2.entity.survivalTime;
        if (time1 !== time2) {
            return time1 > time2 ? node1 : node2;
        }
        
        // 4. Unit Type Tier
        const tier1 = node1.entity.type.tier || 0;
        const tier2 = node2.entity.type.tier || 0;
        if (tier1 !== tier2) {
            return tier1 > tier2 ? node1 : node2;
        }
        
        // 5. Proximity to Objective
        const dist1 = this.calculateDistanceToObjective(node1);
        const dist2 = this.calculateDistanceToObjective(node2);
        if (dist1 !== dist2) {
            return dist1 < dist2 ? node1 : node2;
        }
        
        // If all else fails, use entity ID as final tiebreaker
        return node1.entity.id < node2.entity.id ? node1 : node2;
    }

    /**
     * Calculate distance to nearest objective
     * @param {CommandNode} node - Command node
     * @returns {number} Distance to nearest objective
     */
    calculateDistanceToObjective(node) {
        // Find nearest objective (resource node, enemy base, etc.)
        const objectives = this.findNearbyUnits(node.entity, 1000)
            .filter(u => u.type.isObjective || u.type.isResourceNode);
        
        if (objectives.length === 0) {
            return Infinity;
        }
        
        return Math.min(...objectives.map(obj => 
            this.calculateDistance(node.entity, obj)
        ));
    }

    /**
     * Calculate distance between two units
     * @param {Unit} unit1 - First unit
     * @param {Unit} unit2 - Second unit
     * @returns {number} Distance between units
     */
    calculateDistance(unit1, unit2) {
        const dx = unit1.x - unit2.x;
        const dy = unit1.y - unit2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Handle overlapping command zones
     * @param {CommandNode} node - Command node to check
     */
    handleOverlappingZones(node) {
        const radius = this.calculateCommandRadius(node);
        const nearbyNodes = this.findNearbyUnits(node.entity, radius)
            .filter(u => u !== node.entity && u.team === node.entity.team)
            .map(u => this.getCommandNode(u));
        
        for (const nearbyNode of nearbyNodes) {
            if (!nearbyNode) continue;
            
            // Check for authority conflict
            const winner = this.resolveAuthorityConflict(node, nearbyNode);
            
            // If current node loses, remove overlapping subordinates
            if (winner !== node) {
                this.removeOverlappingSubordinates(node, nearbyNode);
            }
        }
    }

    /**
     * Remove subordinates that are in overlapping command zones
     * @param {CommandNode} losingNode - Node losing authority
     * @param {CommandNode} winningNode - Node gaining authority
     */
    removeOverlappingSubordinates(losingNode, winningNode) {
        const losingRadius = this.calculateCommandRadius(losingNode);
        const winningRadius = this.calculateCommandRadius(winningNode);
        
        // Find subordinates in overlapping zone
        const overlappingSubordinates = losingNode.subordinates.filter(sub => {
            const distToLosing = this.calculateDistance(sub.entity, losingNode.entity);
            const distToWinning = this.calculateDistance(sub.entity, winningNode.entity);
            
            return distToLosing <= losingRadius && distToWinning <= winningRadius;
        });
        
        // Transfer subordinates to winning node
        for (const sub of overlappingSubordinates) {
            losingNode.subordinates = losingNode.subordinates.filter(s => s !== sub);
            winningNode.subordinates.push(sub);
            sub.currentCommander = winningNode.entity;
        }
    }

    /**
     * Initialize recursive command structure
     * @param {Unit} supremeCommander - The ACU or supreme commander
     */
    initializeRecursiveStructure(supremeCommander) {
        // Create supreme commander node
        const supremeNode = this.createCommandNode(supremeCommander, 5);
        this.commandNodes.set(supremeCommander.id, supremeNode);
        
        // Initialize sector-based field commanders
        this.initializeFieldCommanders(supremeNode);
    }

    /**
     * Initialize field commanders for different sectors
     * @param {CommandNode} supremeNode - Supreme commander node
     */
    initializeFieldCommanders(supremeNode) {
        const sectors = this.calculateSectors(supremeNode);
        
        for (const sector of sectors) {
            const fieldCommander = this.findBestFieldCommander(sector);
            if (fieldCommander) {
                const fieldNode = this.createCommandNode(fieldCommander, 4);
                this.commandNodes.set(fieldCommander.id, fieldNode);
                supremeNode.subordinates.push(fieldNode);
                
                // Initialize squad leaders for this sector
                this.initializeSquadLeaders(fieldNode, sector);
            }
        }
    }

    /**
     * Calculate sectors for field commanders
     * @param {CommandNode} supremeNode - Supreme commander node
     * @returns {Array} Array of sector definitions
     */
    calculateSectors(supremeNode) {
        const mapWidth = 2000; // Example map width
        const mapHeight = 2000; // Example map height
        const sectorSize = 500; // Size of each sector
        
        const sectors = [];
        for (let x = 0; x < mapWidth; x += sectorSize) {
            for (let y = 0; y < mapHeight; y += sectorSize) {
                sectors.push({
                    x1: x,
                    y1: y,
                    x2: x + sectorSize,
                    y2: y + sectorSize,
                    centerX: x + sectorSize / 2,
                    centerY: y + sectorSize / 2
                });
            }
        }
        
        return sectors;
    }

    /**
     * Find best field commander for a sector
     * @param {Object} sector - Sector definition
     * @returns {Unit} Best field commander unit
     */
    findBestFieldCommander(sector) {
        const unitsInSector = this.findUnitsInSector(sector);
        
        // Filter for potential commanders
        const commanders = unitsInSector.filter(u => 
            u.type.isCommandUnit && 
            u.veterancyLevel >= 'VETERAN' &&
            u.commandFitness === 'FULL_COMMAND'
        );
        
        if (commanders.length === 0) return null;
        
        // Find best commander based on authority and experience
        return commanders.reduce((best, current) => {
            const bestScore = this.calculateCommanderScore(best);
            const currentScore = this.calculateCommanderScore(current);
            return currentScore > bestScore ? current : best;
        });
    }

    /**
     * Calculate commander score
     * @param {Unit} unit - Unit to score
     * @returns {number} Commander score
     */
    calculateCommanderScore(unit) {
        return unit.calculateEffectiveAuthority() * 2 +
               unit.combatExperience +
               unit.commandExperience * 3 +
               unit.survivalTime / 60;
    }

    /**
     * Initialize squad leaders for a sector
     * @param {CommandNode} fieldNode - Field commander node
     * @param {Object} sector - Sector definition
     */
    initializeSquadLeaders(fieldNode, sector) {
        const unitsInSector = this.findUnitsInSector(sector);
        const squadSize = 5; // Units per squad
        
        // Group units into squads
        for (let i = 0; i < unitsInSector.length; i += squadSize) {
            const squadUnits = unitsInSector.slice(i, i + squadSize);
            const squadLeader = this.findBestSquadLeader(squadUnits);
            
            if (squadLeader) {
                const squadNode = this.createCommandNode(squadLeader, 1);
                this.commandNodes.set(squadLeader.id, squadNode);
                fieldNode.subordinates.push(squadNode);
                
                // Assign remaining units as subordinates
                squadUnits.forEach(unit => {
                    if (unit !== squadLeader) {
                        squadNode.subordinates.push(this.createCommandNode(unit, 0));
                    }
                });
            }
        }
    }

    /**
     * Find best squad leader from a group of units
     * @param {Array} units - Group of units
     * @returns {Unit} Best squad leader
     */
    findBestSquadLeader(units) {
        return units.reduce((best, current) => {
            if (!best) return current;
            
            const bestScore = this.calculateSquadLeaderScore(best);
            const currentScore = this.calculateSquadLeaderScore(current);
            return currentScore > bestScore ? current : best;
        }, null);
    }

    /**
     * Calculate squad leader score
     * @param {Unit} unit - Unit to score
     * @returns {number} Squad leader score
     */
    calculateSquadLeaderScore(unit) {
        return unit.calculateEffectiveAuthority() +
               (unit.veterancyLevel === 'REGULAR' ? 2 : 0) +
               (unit.veterancyLevel === 'VETERAN' ? 4 : 0) +
               (unit.veterancyLevel === 'ELITE' ? 6 : 0) +
               (unit.veterancyLevel === 'HERO' ? 8 : 0);
    }

    /**
     * Find units in a sector
     * @param {Object} sector - Sector definition
     * @returns {Array} Units in sector
     */
    findUnitsInSector(sector) {
        return Array.from(this.commandNodes.values())
            .map(node => node.entity)
            .filter(unit => 
                unit.x >= sector.x1 && unit.x <= sector.x2 &&
                unit.y >= sector.y1 && unit.y <= sector.y2
            );
    }

    /**
     * Optimize command radius for a node
     * @param {CommandNode} node - Command node to optimize
     */
    optimizeCommandRadius(node) {
        const currentRadius = this.calculateCommandRadius(node);
        const subordinates = node.subordinates;
        
        // Calculate optimal radius based on subordinate distribution
        let maxDistance = 0;
        for (const sub of subordinates) {
            const distance = this.calculateDistance(node.entity, sub.entity);
            maxDistance = Math.max(maxDistance, distance);
        }
        
        // Add buffer for movement
        const optimalRadius = maxDistance * 1.2;
        
        // Update radius if significantly different
        if (Math.abs(optimalRadius - currentRadius) > 50) {
            node.commandRadius = optimalRadius;
            this.rangeCache.delete(node.entity.id);
        }
    }

    /**
     * Update command structure
     * @param {number} deltaTime - Time since last update
     */
    updateCommandStructure(deltaTime) {
        // Update all nodes
        for (const node of this.commandNodes.values()) {
            // Update authority and radius
            this.updateNodeAuthority(node);
            this.optimizeCommandRadius(node);
            
            // Handle overlapping zones
            this.handleOverlappingZones(node);
            
            // Check for structural changes
            this.checkStructuralChanges(node);
        }
    }

    /**
     * Check for needed structural changes
     * @param {CommandNode} node - Command node to check
     */
    checkStructuralChanges(node) {
        // Check if node needs to be promoted
        if (node.subordinates.length > this.calculateMaxSubordinates(node) * 0.8) {
            this.considerPromotion(node);
        }
        
        // Check if node needs to be demoted
        if (node.subordinates.length < this.calculateMaxSubordinates(node) * 0.2) {
            this.considerDemotion(node);
        }
        
        // Check if new field commander is needed
        if (node.rank === 5) { // Supreme commander
            this.checkFieldCommanderNeeds(node);
        }
    }

    /**
     * Calculate position value for tactical analysis
     * @param {Unit} unit - Unit to analyze
     * @returns {number} Position value (0-1)
     */
    calculatePositionValue(unit) {
        let value = 0.5; // Base value
        
        // Height advantage (simplified)
        if (unit.elevation && unit.elevation > 0) {
            value += 0.1;
        }
        
        // Cover and concealment (simplified)
        if (unit.inCover) {
            value += 0.2;
        }
        
        return Math.min(1.0, value);
    }

    /**
     * Calculate strategic importance of a position
     * @param {CommandNode} node - Command node
     * @returns {number} Strategic importance (0-1)
     */
    calculateStrategicImportance(node) {
        let importance = 0.3; // Base importance
        
        // Near resource nodes
        const nearbyResources = this.findNearbyUnits(node.entity, 200)
            .filter(u => u.type && u.type.isResourceNode);
        importance += nearbyResources.length * 0.1;
        
        // Near chokepoints or strategic positions
        if (this.isNearChokepoint(node.entity)) {
            importance += 0.3;
        }
        
        return Math.min(1.0, importance);
    }

    /**
     * Calculate resource control value
     * @param {CommandNode} node - Command node
     * @returns {number} Resource control value (0-1)
     */
    calculateResourceControl(node) {
        let control = 0;
        
        // Count controlled resource nodes nearby
        const controlledResources = this.findNearbyUnits(node.entity, 300)
            .filter(u => u.type && u.type.isResourceNode && u.team === node.entity.team);
        
        control = Math.min(1.0, controlledResources.length * 0.2);
        
        return control;
    }

    /**
     * Check if unit is near a chokepoint
     * @param {Unit} unit - Unit to check
     * @returns {boolean} True if near chokepoint
     */
    isNearChokepoint(unit) {
        // Simplified chokepoint detection
        return false; // Would need terrain analysis for real implementation
    }

    /**
     * Find flanking opportunities
     * @param {CommandNode} node - Command node
     * @returns {Array} Array of flanking opportunities
     */
    findFlankingOpportunities(node) {
        return []; // Simplified implementation
    }

    /**
     * Find resource opportunities
     * @param {CommandNode} node - Command node
     * @returns {Array} Array of resource opportunities
     */
    findResourceOpportunities(node) {
        return []; // Simplified implementation
    }

    /**
     * Find defensive opportunities
     * @param {CommandNode} node - Command node
     * @returns {Array} Array of defensive opportunities
     */
    findDefensiveOpportunities(node) {
        return []; // Simplified implementation
    }

    /**
     * Calculate computronium needs
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictions
     * @returns {number} Computronium needed
     */
    calculateComputroniumNeeds(node, predictions) {
        let needs = 0;
        
        // Base needs for subordinates
        needs += node.subordinates.length * 0.1;
        
        // Additional needs for threats
        needs += predictions.threatPredictions.length * 0.5;
        
        return needs;
    }

    /**
     * Calculate energy needs
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictions
     * @returns {number} Energy needed
     */
    calculateEnergyNeeds(node, predictions) {
        return node.subordinates.length * 2; // Simplified
    }

    /**
     * Calculate material needs
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictions
     * @returns {number} Materials needed
     */
    calculateMaterialNeeds(node, predictions) {
        return node.subordinates.length * 1.5; // Simplified
    }

    /**
     * Apply tactical recommendations
     * @param {CommandNode} node - Command node
     * @param {Object} predictions - Predictions
     */
    applyTacticalRecommendations(node, predictions) {
        // Apply recommended actions based on analysis
        for (const recommendation of predictions.recommendedActions) {
            if (recommendation.priority === 'HIGH') {
                this.executeRecommendation(node, recommendation);
            }
        }
    }

    /**
     * Execute a tactical recommendation
     * @param {CommandNode} node - Command node
     * @param {Object} recommendation - Recommendation to execute
     */
    executeRecommendation(node, recommendation) {
        console.log(`[C&C] Executing ${recommendation.type} recommendation: ${recommendation.action}`);
        // Implementation would depend on recommendation type
    }

    /**
     * Handle command chain issues
     * @param {CommandNode} node - Command node
     * @param {Object} validation - Validation results
     */
    handleCommandChainIssues(node, validation) {
        console.warn(`[C&C] Command chain issues for ${node.entity.type}:`, validation.issues);
        
        // Apply recommendations
        for (const recommendation of validation.recommendations) {
            console.log(`[C&C] Recommendation: ${recommendation}`);
        }
    }

    /**
     * Get command node for an entity
     * @param {Unit} entity - Entity to get node for
     * @returns {CommandNode} Command node or null
     */
    getCommandNode(entity) {
        return this.commandNodes.get(entity.id) || null;
    }

    /**
     * Consider promoting a node
     * @param {CommandNode} node - Node to consider for promotion
     */
    considerPromotion(node) {
        if (node.rank < 5 && node.subordinates.length > this.calculateMaxSubordinates(node) * 0.9) {
            this.promoteNode(node);
        }
    }

    /**
     * Consider demoting a node
     * @param {CommandNode} node - Node to consider for demotion
     */
    considerDemotion(node) {
        if (node.rank > 1 && node.subordinates.length < this.calculateMaxSubordinates(node) * 0.1) {
            node.rank = Math.max(1, node.rank - 1);
            console.log(`[C&C] ${node.entity.type} demoted to rank ${node.rank}`);
        }
    }

    /**
     * Check if field commander is needed
     * @param {CommandNode} supremeNode - Supreme commander node
     */
    checkFieldCommanderNeeds(supremeNode) {
        if (supremeNode.subordinates.length > 20) {
            console.log(`[C&C] Supreme commander overloaded, need field commanders`);
            // Would implement field commander creation logic
        }
    }

    /**
     * Create a command node
     * @param {Unit} entity - Entity for the node
     * @param {number} rank - Command rank
     * @returns {CommandNode} Created command node
     */
    createCommandNode(entity, rank) {
        return new CommandNode(entity, rank);
    }
}