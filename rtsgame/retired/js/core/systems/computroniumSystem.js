"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ComputroniumSystem = void 0;
class ComputroniumSystem {
    constructor() {
        this.coreFocusModes = {
            OFFENSIVE: 'offensive', // Mars
            DEFENSIVE: 'defensive', // Juno
            C_C: 'c_c', // Mercury
            UTILITY: 'utility', // Vulcan
            BALANCED: 'balanced' // Default
        };
        this.functionPriorities = {
            // Offensive priorities
            WEAPON_SYSTEMS: { offensive: 1.0, defensive: 0.3, c_c: 0.2, utility: 0.4, balanced: 0.6 },
            TARGETING_AI: { offensive: 0.9, defensive: 0.4, c_c: 0.3, utility: 0.5, balanced: 0.7 },
            MULTI_TARGET: { offensive: 0.8, defensive: 0.2, c_c: 0.1, utility: 0.3, balanced: 0.5 },
            // Defensive priorities
            SHIELD_MANAGEMENT: { offensive: 0.3, defensive: 1.0, c_c: 0.4, utility: 0.6, balanced: 0.7 },
            POINT_DEFENSE: { offensive: 0.4, defensive: 0.9, c_c: 0.3, utility: 0.5, balanced: 0.6 },
            DAMAGE_CONTROL: { offensive: 0.2, defensive: 0.8, c_c: 0.3, utility: 0.7, balanced: 0.5 },
            EVASION: { offensive: 0.5, defensive: 0.7, c_c: 0.2, utility: 0.4, balanced: 0.6 },
            // C&C priorities
            C_C_UPLINK: { offensive: 0.2, defensive: 0.3, c_c: 1.0, utility: 0.6, balanced: 0.5 },
            POW_CONTRIBUTION: { offensive: 0.3, defensive: 0.2, c_c: 0.9, utility: 0.5, balanced: 0.6 },
            ADVANCED_SENSORS: { offensive: 0.3, defensive: 0.6, c_c: 0.8, utility: 0.7, balanced: 0.7 }, // Renamed & adjusted
            // Utility priorities
            CONSTRUCTION_SYSTEMS: { offensive: 0.1, defensive: 0.2, c_c: 0.3, utility: 1.0, balanced: 0.4 }, // Renamed & adjusted
            REPAIR_SYSTEMS: { offensive: 0.1, defensive: 0.3, c_c: 0.2, utility: 0.9, balanced: 0.5 }, // Renamed & adjusted
            E_WARFARE_SUITE: { offensive: 0.6, defensive: 0.4, c_c: 0.7, utility: 0.8, balanced: 0.7 }, // Renamed
            // Special Abilities - Generic category
            SPECIAL_ABILITIES: { offensive: 0.7, defensive: 0.7, c_c: 0.6, utility: 0.6, balanced: 0.7 },
            // Common priorities
            BASIC_MOVEMENT: { offensive: 0.3, defensive: 0.4, c_c: 0.3, utility: 0.4, balanced: 0.5 },
            BASIC_SENSORS: { offensive: 0.4, defensive: 0.5, c_c: 0.6, utility: 0.4, balanced: 0.5 }, // Basic sensors for all
            SELF_DEFENSE: { offensive: 0.6, defensive: 0.7, c_c: 0.3, utility: 0.5, balanced: 0.6 } // For when unit is directly attacked
        };
    }
    update(deltaTime, entities, gameContext) {
        for (const entity of entities) {
            // Ensure entity has computronium and is of a type that uses it
            if (!entity.computronium || !entity.type || !entity.type.hasComputroniumCore)
                continue;
            // Allocate forks based on current state and focus mode
            this.allocateForks(entity, gameContext);
            // Apply performance modifiers based on fork allocation
            this.applyPerformanceModifiers(entity, deltaTime);
        }
    }
    allocateForks(entity, gameContext) {
        const core = entity.computronium;
        const availableForks = this.getForkCount(core.level);
        const focusMode = core.focusMode || this.coreFocusModes.BALANCED;
        const requestedForks = [];
        // Collect all active functions and their priorities
        this.collectFunctionRequests(entity, focusMode, requestedForks);
        // Sort by priority
        requestedForks.sort((a, b) => b.priority - a.priority);
        // Allocate forks
        const allocatedFunctions = {};
        let remainingForks = availableForks;
        for (const request of requestedForks) {
            if (remainingForks >= request.forksNeeded) {
                allocatedFunctions[request.function] = 'OPTIMAL';
                remainingForks -= request.forksNeeded;
            }
            else if (remainingForks > 0 && request.forksNeeded > 1) {
                allocatedFunctions[request.function] = 'DEGRADED';
                remainingForks = 0;
            }
            else {
                allocatedFunctions[request.function] = 'STARVED';
            }
        }
        // Store allocation results
        core.allocatedFunctions = allocatedFunctions;
    }
    collectFunctionRequests(entity, focusMode, requestedForks) {
        // Check weapon systems
        if (entity.isAttacking || (entity.weapon && entity.weapon.isFiring)) { // Assuming isAttacking or similar general flag
            requestedForks.push({
                function: 'WEAPON_SYSTEMS',
                priority: this.functionPriorities.WEAPON_SYSTEMS[focusMode],
                forksNeeded: 1
            });
            if (entity.isUsingAdvancedTargeting) { // Example for a more specific sub-function
                requestedForks.push({
                    function: 'TARGETING_AI',
                    priority: this.functionPriorities.TARGETING_AI[focusMode],
                    forksNeeded: 1
                });
            }
        }
        // Check shield systems
        // Assuming currentEnergyShields and maxEnergyShields exist from previous subtasks
        if (entity.currentEnergyShields > 0 && entity.type && entity.type.maxEnergyShields > 0) {
            requestedForks.push({
                function: 'SHIELD_MANAGEMENT',
                priority: this.functionPriorities.SHIELD_MANAGEMENT[focusMode],
                forksNeeded: 1
            });
        }
        // Check PoW Contribution
        if (entity.isContributingToPoW) {
            requestedForks.push({
                function: 'POW_CONTRIBUTION',
                priority: this.functionPriorities.POW_CONTRIBUTION[focusMode],
                forksNeeded: 1
            });
        }
        // New function checks:
        if (entity.isUsingAdvancedSensors) {
            requestedForks.push({
                function: 'ADVANCED_SENSORS',
                priority: this.functionPriorities.ADVANCED_SENSORS[focusMode],
                forksNeeded: 1
            });
        }
        if (entity.isUsingSpecialAbility) {
            requestedForks.push({
                function: 'SPECIAL_ABILITIES',
                priority: this.functionPriorities.SPECIAL_ABILITIES[focusMode],
                forksNeeded: entity.activeSpecialAbilityForkCost || 1 // Allow abilities to define their cost
            });
        }
        if (entity.isUsingEWarfare) {
            requestedForks.push({
                function: 'E_WARFARE_SUITE',
                priority: this.functionPriorities.E_WARFARE_SUITE[focusMode],
                forksNeeded: 1
            });
        }
        if (entity.isConstructing) {
            requestedForks.push({
                function: 'CONSTRUCTION_SYSTEMS',
                priority: this.functionPriorities.CONSTRUCTION_SYSTEMS[focusMode],
                forksNeeded: 1
            });
        }
        if (entity.isRepairing) {
            requestedForks.push({
                function: 'REPAIR_SYSTEMS',
                priority: this.functionPriorities.REPAIR_SYSTEMS[focusMode],
                forksNeeded: 1
            });
        }
        // Basic functions that are always active to some degree
        if (entity.type && entity.type.speed > 0) { // Only request movement if unit can move
            requestedForks.push({
                function: 'BASIC_MOVEMENT',
                priority: this.functionPriorities.BASIC_MOVEMENT[focusMode],
                forksNeeded: 1
            });
        }
        requestedForks.push({
            function: 'BASIC_SENSORS',
            priority: this.functionPriorities.BASIC_SENSORS[focusMode],
            forksNeeded: 1
        });
        if (entity.isUnderAttack) { // Example flag for self-defense needs
            requestedForks.push({
                function: 'SELF_DEFENSE', // Could cover basic targeting/evasion if not covered by other specific systems
                priority: this.functionPriorities.SELF_DEFENSE[focusMode],
                forksNeeded: 1
            });
        }
    }
    applyPerformanceModifiers(entity, deltaTime) {
        const core = entity.computronium;
        if (!core || !core.allocatedFunctions)
            return;
        // Initialize/Reset performance factors to defaults before applying modifiers
        // These factors are multipliers; 1.0 means no change.
        entity.constructionRateFactor = 1.0;
        entity.repairRateFactor = 1.0;
        entity.specialAbilityEffectivenessFactor = 1.0;
        entity.specialAbilityDurationFactor = 1.0; // Example for duration
        entity.advancedSensorRangeFactor = 1.0;
        entity.advancedSensorUpdateSpeedFactor = 1.0;
        entity.eWarfareEffectivenessFactor = 1.0;
        entity.eWarfareRangeFactor = 1.0; // Example for range
        // Weapon systems
        if (entity.weaponSystemData) { // Assuming a specific data object for weapon performance
            entity.weaponSystemData.fireRateFactor = 1.0;
            entity.weaponSystemData.accuracyFactor = 1.0;
            const weaponStatus = core.allocatedFunctions['WEAPON_SYSTEMS'] || core.allocatedFunctions['TARGETING_AI']; // Combine or prioritize
            if (weaponStatus === 'DEGRADED') {
                entity.weaponSystemData.fireRateFactor = 0.7;
                entity.weaponSystemData.accuracyFactor = 0.8;
            }
            else if (weaponStatus === 'STARVED') {
                entity.weaponSystemData.fireRateFactor = 0.4;
                entity.weaponSystemData.accuracyFactor = 0.5;
            }
        }
        // Shield systems
        if (entity.shieldSystemData) { // Assuming a specific data object for shield performance
            entity.shieldSystemData.rechargeRateFactor = 1.0;
            entity.shieldSystemData.effectivenessFactor = 1.0;
            const shieldStatus = core.allocatedFunctions['SHIELD_MANAGEMENT'];
            if (shieldStatus === 'DEGRADED') {
                entity.shieldSystemData.rechargeRateFactor = 0.7;
                entity.shieldSystemData.effectivenessFactor = 0.8;
            }
            else if (shieldStatus === 'STARVED') {
                entity.shieldSystemData.rechargeRateFactor = 0.4;
                entity.shieldSystemData.effectivenessFactor = 0.5;
            }
        }
        // Movement systems
        if (entity.movementSystemData) { // Assuming a specific data object for movement performance
            entity.movementSystemData.speedFactor = 1.0;
            entity.movementSystemData.accelerationFactor = 1.0; // Example
            const movementStatus = core.allocatedFunctions['BASIC_MOVEMENT'];
            if (movementStatus === 'DEGRADED') {
                entity.movementSystemData.speedFactor = 0.7;
                entity.movementSystemData.accelerationFactor = 0.8;
            }
            else if (movementStatus === 'STARVED') {
                entity.movementSystemData.speedFactor = 0.4;
                entity.movementSystemData.accelerationFactor = 0.5;
            }
        }
        // Basic Sensors
        if (entity.sensorSystemData) { // Assuming a specific data object for basic sensor performance
            entity.sensorSystemData.rangeFactor = 1.0;
            const sensorStatus = core.allocatedFunctions['BASIC_SENSORS'];
            if (sensorStatus === 'DEGRADED')
                entity.sensorSystemData.rangeFactor = 0.7;
            else if (sensorStatus === 'STARVED')
                entity.sensorSystemData.rangeFactor = 0.4;
        }
        // Apply modifiers for new functions
        const constructionStatus = core.allocatedFunctions['CONSTRUCTION_SYSTEMS'];
        if (constructionStatus === 'DEGRADED')
            entity.constructionRateFactor = 0.6;
        else if (constructionStatus === 'STARVED')
            entity.constructionRateFactor = 0.3;
        const repairStatus = core.allocatedFunctions['REPAIR_SYSTEMS'];
        if (repairStatus === 'DEGRADED')
            entity.repairRateFactor = 0.6;
        else if (repairStatus === 'STARVED')
            entity.repairRateFactor = 0.3;
        const specialAbilityStatus = core.allocatedFunctions['SPECIAL_ABILITIES'];
        if (specialAbilityStatus === 'DEGRADED') {
            entity.specialAbilityEffectivenessFactor = 0.7;
            entity.specialAbilityDurationFactor = 0.8; // e.g. lasts shorter
        }
        else if (specialAbilityStatus === 'STARVED') {
            entity.specialAbilityEffectivenessFactor = 0.4;
            entity.specialAbilityDurationFactor = 0.5;
            // Consider deactivating ability if starved: if (entity.activeSpecialAbility) entity.deactivateSpecialAbility();
        }
        const advSensorsStatus = core.allocatedFunctions['ADVANCED_SENSORS'];
        if (advSensorsStatus === 'DEGRADED') {
            entity.advancedSensorRangeFactor = 0.75;
            entity.advancedSensorUpdateSpeedFactor = 0.7;
        }
        else if (advSensorsStatus === 'STARVED') {
            entity.advancedSensorRangeFactor = 0.5;
            entity.advancedSensorUpdateSpeedFactor = 0.4;
        }
        const eWarfareStatus = core.allocatedFunctions['E_WARFARE_SUITE'];
        if (eWarfareStatus === 'DEGRADED') {
            entity.eWarfareEffectivenessFactor = 0.7;
            entity.eWarfareRangeFactor = 0.8;
        }
        else if (eWarfareStatus === 'STARVED') {
            entity.eWarfareEffectivenessFactor = 0.4;
            entity.eWarfareRangeFactor = 0.5;
        }
    }
    getForkCount(level, efficiency = 1.0) {
        // Base forks: Level 1 = 2, Level 2 = 3, Level 3 = 4, Level 4 = 5, Level 5 = 6 (example progression)
        const baseForks = Math.min(6, Math.max(2, level + 1));
        // Efficiency multiplier, e.g., 0.8 efficiency results in 80% of base forks, rounded down.
        // Efficiency of 1.2 results in 120% of base forks.
        const calculatedForks = Math.floor(baseForks * efficiency);
        return Math.max(1, calculatedForks); // Ensure at least 1 fork if core is active at all
    }
    setCoreFocusMode(entity, modeKey) {
        if (!entity.computronium)
            return false;
        const upperModeKey = modeKey.toUpperCase(); // Ensure key is uppercase for matching
        const modeValue = this.coreFocusModes[upperModeKey];
        if (!modeValue) {
            console.warn(`[ComputroniumSystem] Invalid focus mode key: ${modeKey}`);
            return false;
        }
        entity.computronium.focusMode = modeValue;
        // Re-allocate forks immediately when mode changes
        // Assuming gameContext is not strictly needed by collectFunctionRequests for this call, or a minimal {} is fine.
        // If gameContext is needed for dynamic requests, it should be passed or accessible.
        this.allocateForks(entity, {});
        this.applyPerformanceModifiers(entity, 0); // Apply modifiers immediately (deltaTime 0 for instant effect)
        console.log(`[ComputroniumSystem] Unit ${entity.id || entity.type.name} focus mode set to ${modeValue}`);
        return true;
    }
    getCoreStatus(entity) {
        if (!entity.computronium)
            return null;
        const core = entity.computronium;
        const efficiency = core.efficiency || 1.0; // Default to 1.0 if not defined
        return {
            level: core.level,
            efficiency: efficiency,
            focusMode: core.focusMode,
            availableForks: this.getForkCount(core.level, efficiency),
            allocatedFunctions: core.allocatedFunctions || {}
        };
    }
}
exports.ComputroniumSystem = ComputroniumSystem;
