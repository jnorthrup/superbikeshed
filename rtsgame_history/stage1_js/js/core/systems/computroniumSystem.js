export class ComputroniumSystem {
    constructor() {
        this.coreFocusModes = {
            OFFENSIVE: 'offensive',    // Mars
            DEFENSIVE: 'defensive',    // Juno
            C_C: 'c_c',               // Mercury
            UTILITY: 'utility',       // Vulcan
            BALANCED: 'balanced'      // Default
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
            LONG_RANGE_SENSORS: { offensive: 0.4, defensive: 0.5, c_c: 0.8, utility: 0.7, balanced: 0.6 },

            // Utility priorities
            CONSTRUCTION: { offensive: 0.2, defensive: 0.3, c_c: 0.4, utility: 1.0, balanced: 0.5 },
            REPAIR: { offensive: 0.2, defensive: 0.4, c_c: 0.3, utility: 0.9, balanced: 0.6 },
            E_WAR: { offensive: 0.5, defensive: 0.4, c_c: 0.6, utility: 0.8, balanced: 0.7 },

            // Common priorities
            BASIC_MOVEMENT: { offensive: 0.3, defensive: 0.4, c_c: 0.3, utility: 0.4, balanced: 0.5 },
            BASIC_SENSORS: { offensive: 0.4, defensive: 0.5, c_c: 0.6, utility: 0.4, balanced: 0.5 },
            SELF_DEFENSE: { offensive: 0.6, defensive: 0.7, c_c: 0.3, utility: 0.5, balanced: 0.6 }
        };
    }

    update(deltaTime, entities, gameContext) {
        for (const entity of entities) {
            if (!entity.computronium) continue;

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
            } else if (remainingForks > 0 && request.forksNeeded > 1) {
                allocatedFunctions[request.function] = 'DEGRADED';
                remainingForks = 0;
            } else {
                allocatedFunctions[request.function] = 'STARVED';
            }
        }

        // Store allocation results
        core.allocatedFunctions = allocatedFunctions;
    }

    collectFunctionRequests(entity, focusMode, requestedForks) {
        // Check weapon systems
        if (entity.weapon && entity.weapon.isFiring) {
            requestedForks.push({
                function: 'WEAPON_SYSTEMS',
                priority: this.functionPriorities.WEAPON_SYSTEMS[focusMode],
                forksNeeded: 1
            });
        }

        // Check shield systems
        if (entity.shield && entity.shield.isActive) {
            requestedForks.push({
                function: 'SHIELD_MANAGEMENT',
                priority: this.functionPriorities.SHIELD_MANAGEMENT[focusMode],
                forksNeeded: 1
            });
        }

        // Check C&C systems
        if (entity.isContributingToPoW) {
            requestedForks.push({
                function: 'POW_CONTRIBUTION',
                priority: this.functionPriorities.POW_CONTRIBUTION[focusMode],
                forksNeeded: 1
            });
        }

        // Add basic functions
        requestedForks.push({
            function: 'BASIC_MOVEMENT',
            priority: this.functionPriorities.BASIC_MOVEMENT[focusMode],
            forksNeeded: 1
        });

        requestedForks.push({
            function: 'BASIC_SENSORS',
            priority: this.functionPriorities.BASIC_SENSORS[focusMode],
            forksNeeded: 1
        });
    }

    applyPerformanceModifiers(entity, deltaTime) {
        const core = entity.computronium;
        if (!core.allocatedFunctions) return;

        // Apply modifiers to weapon systems
        if (entity.weapon) {
            const weaponStatus = core.allocatedFunctions['WEAPON_SYSTEMS'];
            if (weaponStatus === 'DEGRADED') {
                entity.weapon.fireRate *= 0.7;
                entity.weapon.accuracy *= 0.8;
            } else if (weaponStatus === 'STARVED') {
                entity.weapon.fireRate *= 0.4;
                entity.weapon.accuracy *= 0.5;
            }
        }

        // Apply modifiers to shield systems
        if (entity.shield) {
            const shieldStatus = core.allocatedFunctions['SHIELD_MANAGEMENT'];
            if (shieldStatus === 'DEGRADED') {
                entity.shield.rechargeRate *= 0.7;
                entity.shield.effectiveness *= 0.8;
            } else if (shieldStatus === 'STARVED') {
                entity.shield.rechargeRate *= 0.4;
                entity.shield.effectiveness *= 0.5;
            }
        }

        // Apply modifiers to movement
        if (entity.movement) {
            const movementStatus = core.allocatedFunctions['BASIC_MOVEMENT'];
            if (movementStatus === 'DEGRADED') {
                entity.movement.speed *= 0.7;
                entity.movement.acceleration *= 0.8;
            } else if (movementStatus === 'STARVED') {
                entity.movement.speed *= 0.4;
                entity.movement.acceleration *= 0.5;
            }
        }
    }

    getForkCount(level) {
        // Level 1 = 2 forks, Level 5 = 5 forks
        return Math.min(5, Math.max(2, level));
    }

    setCoreFocusMode(entity, mode) {
        if (!entity.computronium) return false;
        if (!this.coreFocusModes[mode]) return false;

        entity.computronium.focusMode = this.coreFocusModes[mode];
        return true;
    }

    getCoreStatus(entity) {
        if (!entity.computronium) return null;

        return {
            level: entity.computronium.level,
            focusMode: entity.computronium.focusMode,
            availableForks: this.getForkCount(entity.computronium.level),
            allocatedFunctions: entity.computronium.allocatedFunctions || {}
        };
    }
} 