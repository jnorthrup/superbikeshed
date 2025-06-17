// js/core/computroniumManager.js - Computronium Resource Management System

import { RESOURCE_TYPES, COMPUTRONIUM_CONFIG } from '../config/gameConstants.js';
// Temporary inline TrikeShed implementation
const j = (a, b) => ({ a, b });
const getSeriesValue = (series, i) => {
    if (i < 0 || i >= series.a) throw new RangeError(`Index ${i} out of bounds`);
    return series.b(i);
};
const seriesSize = (series) => series.a;

/**
 * Computronium Core - represents a single computational processing unit
 * Implements "Dining Philosophers" resource allocation pattern
 */
export class ComputroniumCore {
    constructor(id, owner, efficiency = 1.0) {
        this.id = id;
        this.owner = owner; // Unit or building that owns this core
        this.efficiency = Math.max(COMPUTRONIUM_CONFIG.MIN_CORE_EFFICIENCY, 
                                  Math.min(COMPUTRONIUM_CONFIG.MAX_CORE_EFFICIENCY, efficiency));
        this.isActive = true;
        this.currentTask = null; // Current computational task
        this.neighbors = []; // Adjacent cores for dining philosophers contention
        this.lastGenerationTime = 0;
        
        // Computational warfare state
        this.isUnderAttack = false;
        this.defenseStrength = 1.0;
        this.lastAttackTime = 0;
    }

    /**
     * Calculate actual generation rate considering dining philosophers contention
     * @returns {number} Actual computronium generation rate
     */
    getActualGenerationRate() {
        if (!this.isActive) return 0;
        
        // Base rate modified by efficiency
        let rate = COMPUTRONIUM_CONFIG.BASE_GENERATION_RATE * this.efficiency;
        
        // Apply dining philosophers penalty based on active neighbors
        const activeNeighbors = this.neighbors.filter(core => core.isActive && core.currentTask).length;
        const contentionPenalty = activeNeighbors * COMPUTRONIUM_CONFIG.DINING_PHILOSOPHERS_PENALTY;
        
        rate *= Math.max(0.1, 1.0 - contentionPenalty);
        
        // Apply computational warfare effects
        if (this.isUnderAttack) {
            rate *= this.defenseStrength;
        }
        
        return rate;
    }

    /**
     * Update core state and generate computronium
     * @param {number} deltaTime - Time step in seconds
     * @returns {number} Computronium generated this tick
     */
    update(deltaTime) {
        if (!this.isActive) return 0;
        
        this.lastGenerationTime += deltaTime;
        const generationRate = this.getActualGenerationRate();
        const generated = generationRate * deltaTime;
        
        // Clear attack status after some time
        if (this.isUnderAttack && (Date.now() - this.lastAttackTime) > 5000) {
            this.isUnderAttack = false;
            this.defenseStrength = Math.min(1.0, this.defenseStrength + 0.1);
        }
        
        return generated;
    }

    /**
     * Apply computational warfare attack
     * @param {number} attackStrength - Strength of the attack
     * @returns {boolean} True if attack was successful
     */
    receiveAttack(attackStrength) {
        this.isUnderAttack = true;
        this.lastAttackTime = Date.now();
        this.defenseStrength = Math.max(0.1, this.defenseStrength - (attackStrength * 0.1));
        
        // Chance to disable core temporarily
        if (attackStrength > this.defenseStrength * 2) {
            this.isActive = false;
            setTimeout(() => {
                this.isActive = true;
                console.log(`[Computronium] Core ${this.id} recovered from attack`);
            }, 3000);
            return true;
        }
        
        return false;
    }

    /**
     * Add a neighboring core for dining philosophers contention
     * @param {ComputroniumCore} core - Neighboring core
     */
    addNeighbor(core) {
        if (!this.neighbors.includes(core)) {
            this.neighbors.push(core);
        }
    }

    /**
     * Assign a computational task to this core
     * @param {string} taskType - Type of task (c2, pow_defense, pow_attack, research)
     * @param {number} priority - Task priority
     */
    assignTask(taskType, priority = 1.0) {
        this.currentTask = {
            type: taskType,
            priority: priority,
            startTime: Date.now()
        };
    }

    /**
     * Clear current task
     */
    clearTask() {
        this.currentTask = null;
    }
}

/**
 * Computronium Manager - handles all computronium operations for a team
 * Implements TrikeShed-based resource tracking
 */
export class ComputroniumManager {
    constructor(team) {
        this.team = team;
        this.cores = []; // Array of ComputroniumCore instances
        this.totalComputronium = 0;
        this.reservedComputronium = 0; // Reserved for ongoing operations
        this.generationHistory = []; // For performance tracking
        
        // Command & Control enhancement
        this.c2LatencyBonus = 0;
        this.c2EfficiencyBonus = 0;
        
        // Computational warfare capabilities
        this.powDefenseStrength = 1.0;
        this.powAttackCapability = 0;
        this.activePowOperations = [];
        
        console.log(`[Computronium] Manager initialized for team ${team}`);
    }

    /**
     * Add a computronium core to the manager
     * @param {Unit|Building} owner - Entity that owns the core
     * @param {number} efficiency - Core efficiency (0.2 to 1.0)
     * @returns {ComputroniumCore} The created core
     */
    addCore(owner, efficiency = 1.0) {
        const core = new ComputroniumCore(this.cores.length, owner, efficiency);
        this.cores.push(core);
        
        // Establish dining philosophers relationships with nearby cores
        this.establishCoreNeighborhoods();
        
        console.log(`[Computronium] Core ${core.id} added for ${owner.type || owner.constructor.name}. Total cores: ${this.cores.length}`);
        return core;
    }

    /**
     * Remove a computronium core (when unit/building is destroyed)
     * @param {ComputroniumCore} core - Core to remove
     */
    removeCore(core) {
        const index = this.cores.indexOf(core);
        if (index !== -1) {
            this.cores.splice(index, 1);
            
            // Remove from all neighbor lists
            this.cores.forEach(otherCore => {
                const neighborIndex = otherCore.neighbors.indexOf(core);
                if (neighborIndex !== -1) {
                    otherCore.neighbors.splice(neighborIndex, 1);
                }
            });
            
            console.log(`[Computronium] Core ${core.id} removed. Remaining cores: ${this.cores.length}`);
        }
    }

    /**
     * Establish dining philosophers relationships between nearby cores
     */
    establishCoreNeighborhoods() {
        const MAX_NEIGHBOR_DISTANCE = 500; // World units
        
        for (let i = 0; i < this.cores.length; i++) {
            for (let j = i + 1; j < this.cores.length; j++) {
                const core1 = this.cores[i];
                const core2 = this.cores[j];
                
                // Calculate distance between core owners
                const dx = core1.owner.x - core2.owner.x;
                const dy = core1.owner.y - core2.owner.y;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= MAX_NEIGHBOR_DISTANCE) {
                    core1.addNeighbor(core2);
                    core2.addNeighbor(core1);
                }
            }
        }
    }

    /**
     * Update all cores and generate computronium
     * @param {number} deltaTime - Time step in seconds
     */
    update(deltaTime) {
        let totalGenerated = 0;
        
        // Update each core and accumulate generation
        for (const core of this.cores) {
            const generated = core.update(deltaTime);
            totalGenerated += generated;
        }
        
        this.totalComputronium += totalGenerated;
        
        // Update C&C bonuses based on available computronium
        this.updateC2Bonuses();
        
        // Update PoW capabilities
        this.updatePowCapabilities();
        
        // Track generation history for optimization
        this.generationHistory.push({
            time: Date.now(),
            generated: totalGenerated,
            total: this.totalComputronium,
            activeCores: this.cores.filter(c => c.isActive).length
        });
        
        // Keep only last 100 entries
        if (this.generationHistory.length > 100) {
            this.generationHistory.shift();
        }
        
        return totalGenerated;
    }

    /**
     * Update Command & Control bonuses based on computronium availability
     */
    updateC2Bonuses() {
        const availableComputronium = this.totalComputronium - this.reservedComputronium;
        
        // Latency reduction bonus
        this.c2LatencyBonus = Math.min(0.5, availableComputronium * COMPUTRONIUM_CONFIG.C2_COMPUTRONIUM_BONUS);
        
        // Efficiency bonus for command processing
        this.c2EfficiencyBonus = Math.min(2.0, 1.0 + (availableComputronium * 0.05));
    }

    /**
     * Update Proof-of-Work capabilities
     */
    updatePowCapabilities() {
        const activeCores = this.cores.filter(c => c.isActive).length;
        
        // Defense strength based on core count and computronium
        this.powDefenseStrength = Math.min(5.0, 1.0 + (activeCores * 0.2) + (this.totalComputronium * 0.1));
        
        // Attack capability based on available computronium
        const availableComputronium = this.totalComputronium - this.reservedComputronium;
        this.powAttackCapability = Math.floor(availableComputronium / COMPUTRONIUM_CONFIG.COMPUTATIONAL_WARFARE_COST);
    }

    /**
     * Spend computronium for various operations
     * @param {number} amount - Amount to spend
     * @param {string} purpose - Purpose of spending (c2, pow, research, etc.)
     * @returns {boolean} True if spending was successful
     */
    spendComputronium(amount, purpose = 'unknown') {
        if (this.totalComputronium >= amount) {
            this.totalComputronium -= amount;
            console.log(`[Computronium] Spent ${amount.toFixed(2)} for ${purpose}. Remaining: ${this.totalComputronium.toFixed(2)}`);
            return true;
        }
        return false;
    }

    /**
     * Reserve computronium for ongoing operations
     * @param {number} amount - Amount to reserve
     * @param {string} operation - Operation name
     * @returns {boolean} True if reservation was successful
     */
    reserveComputronium(amount, operation) {
        if (this.totalComputronium >= amount) {
            this.reservedComputronium += amount;
            console.log(`[Computronium] Reserved ${amount.toFixed(2)} for ${operation}`);
            return true;
        }
        return false;
    }

    /**
     * Release reserved computronium
     * @param {number} amount - Amount to release
     */
    releaseReservedComputronium(amount) {
        this.reservedComputronium = Math.max(0, this.reservedComputronium - amount);
    }

    /**
     * Launch a Proof-of-Work computational warfare attack
     * @param {ComputroniumManager} targetManager - Target enemy manager
     * @param {number} intensity - Attack intensity (1-5)
     * @returns {boolean} True if attack was launched
     */
    launchPowAttack(targetManager, intensity = 1) {
        const cost = intensity * COMPUTRONIUM_CONFIG.COMPUTATIONAL_WARFARE_COST;
        
        if (this.spendComputronium(cost, 'pow_attack')) {
            // Select random target cores
            const targetCores = targetManager.cores.filter(c => c.isActive);
            if (targetCores.length === 0) return false;
            
            const attacksPerCore = Math.ceil(intensity);
            let successfulAttacks = 0;
            
            for (let i = 0; i < attacksPerCore && targetCores.length > 0; i++) {
                const randomIndex = Math.floor(Math.random() * targetCores.length);
                const targetCore = targetCores[randomIndex];
                
                if (targetCore.receiveAttack(intensity)) {
                    successfulAttacks++;
                }
            }
            
            console.log(`[Computronium] PoW attack launched: ${successfulAttacks}/${attacksPerCore} cores disrupted`);
            return true;
        }
        
        return false;
    }

    /**
     * Get current computronium status
     * @returns {Object} Status information
     */
    getStatus() {
        const activeCores = this.cores.filter(c => c.isActive).length;
        const totalEfficiency = this.cores.reduce((sum, core) => sum + core.getActualGenerationRate(), 0);
        
        return {
            totalComputronium: this.totalComputronium,
            reservedComputronium: this.reservedComputronium,
            availableComputronium: this.totalComputronium - this.reservedComputronium,
            totalCores: this.cores.length,
            activeCores: activeCores,
            totalEfficiency: totalEfficiency,
            c2LatencyBonus: this.c2LatencyBonus,
            c2EfficiencyBonus: this.c2EfficiencyBonus,
            powDefenseStrength: this.powDefenseStrength,
            powAttackCapability: this.powAttackCapability
        };
    }

    /**
     * Get generation statistics for monitoring
     * @returns {Object} Generation statistics
     */
    getGenerationStats() {
        if (this.generationHistory.length === 0) return null;
        
        const recent = this.generationHistory.slice(-10);
        const avgGeneration = recent.reduce((sum, entry) => sum + entry.generated, 0) / recent.length;
        const activeCores = this.cores.filter(c => c.isActive).length;
        
        return {
            currentRate: avgGeneration,
            peakGeneration: Math.max(...this.generationHistory.map(h => h.generated)),
            totalGenerated: this.generationHistory.reduce((sum, entry) => sum + entry.generated, 0),
            efficiency: activeCores > 0 ? avgGeneration / activeCores : 0
        };
    }
}