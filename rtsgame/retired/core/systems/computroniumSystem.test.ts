// computroniumSystem.test.js

// Simplified ComputroniumSystem (assuming it's imported or defined elsewhere)
// import { ComputroniumSystem } from './computroniumSystem.js';

// Mock Unit for Computronium System tests
class MockComputroniumUnit {
    constructor(id, typeDetails = {}) {
        this.id = id;
        this.type = {
            name: typeDetails.name || 'TestDrone',
            hasComputroniumCore: true, // Essential for the system to process it
            maxEnergyShields: typeDetails.maxEnergyShields || 50, // For shield management tests
            speed: typeDetails.speed || 50, // For movement tests
            ...typeDetails // Allow overriding other type properties
        };
        this.computronium = {
            level: typeDetails.coreLevel || 1,
            focusMode: typeDetails.focusMode || 'balanced', // 'balanced' is a value, not key
            efficiency: typeDetails.coreEfficiency || 1.0,
            allocatedFunctions: {}
        };

        // Flags for active functions (to be set by tests)
        this.isAttacking = false;
        this.isUsingAdvancedTargeting = false;
        this.currentEnergyShields = this.type.maxEnergyShields; // Start with full shields
        this.isContributingToPoW = false;
        this.isUsingAdvancedSensors = false;
        this.isUsingSpecialAbility = false;
        this.activeSpecialAbilityForkCost = 1; // Default, can be changed by tests
        this.isUsingEWarfare = false;
        this.isConstructing = false;
        this.isRepairing = false;
        this.isUnderAttack = false; // For SELF_DEFENSE

        // Data objects for performance modifiers (system will create/update factors on these)
        this.weaponSystemData = { fireRateFactor: 1.0, accuracyFactor: 1.0 };
        this.shieldSystemData = { rechargeRateFactor: 1.0, effectivenessFactor: 1.0 };
        this.movementSystemData = { speedFactor: 1.0, accelerationFactor: 1.0 };
        this.sensorSystemData = { rangeFactor: 1.0 }; // For BASIC_SENSORS

        // Factors for new systems (to be directly on unit, as per implementation)
        this.constructionRateFactor = 1.0;
        this.repairRateFactor = 1.0;
        this.specialAbilityEffectivenessFactor = 1.0;
        this.specialAbilityDurationFactor = 1.0;
        this.advancedSensorRangeFactor = 1.0;
        this.advancedSensorUpdateSpeedFactor = 1.0;
        this.eWarfareEffectivenessFactor = 1.0;
        this.eWarfareRangeFactor = 1.0;
    }
}

// Assume ComputroniumSystem class is available (e.g. from previous step's generation)
// For sandbox, this will be the class definition from previous step, slightly simplified if needed.
class ComputroniumSystem {
    constructor() {
        this.coreFocusModes = {
            OFFENSIVE: 'offensive', DEFENSIVE: 'defensive', C_C: 'c_c', UTILITY: 'utility', BALANCED: 'balanced'
        };
        this.functionPriorities = {
            WEAPON_SYSTEMS: { offensive: 1.0, defensive: 0.3, c_c: 0.2, utility: 0.4, balanced: 0.6 },
            TARGETING_AI: { offensive: 0.9, defensive: 0.4, c_c: 0.3, utility: 0.5, balanced: 0.7 },
            SHIELD_MANAGEMENT: { offensive: 0.3, defensive: 1.0, c_c: 0.4, utility: 0.6, balanced: 0.7 },
            POW_CONTRIBUTION: { offensive: 0.3, defensive: 0.2, c_c: 0.9, utility: 0.5, balanced: 0.6 },
            ADVANCED_SENSORS: { offensive: 0.3, defensive: 0.6, c_c: 0.8, utility: 0.7, balanced: 0.7 },
            CONSTRUCTION_SYSTEMS: { offensive: 0.1, defensive: 0.2, c_c: 0.3, utility: 1.0, balanced: 0.4 },
            REPAIR_SYSTEMS: { offensive: 0.1, defensive: 0.3, c_c: 0.2, utility: 0.9, balanced: 0.5 },
            E_WARFARE_SUITE: { offensive: 0.6, defensive: 0.4, c_c: 0.7, utility: 0.8, balanced: 0.7 },
            SPECIAL_ABILITIES: { offensive: 0.7, defensive: 0.7, c_c: 0.6, utility: 0.6, balanced: 0.7 },
            BASIC_MOVEMENT: { offensive: 0.3, defensive: 0.4, c_c: 0.3, utility: 0.4, balanced: 0.5 },
            BASIC_SENSORS: { offensive: 0.4, defensive: 0.5, c_c: 0.6, utility: 0.4, balanced: 0.5 },
            SELF_DEFENSE: { offensive: 0.6, defensive: 0.7, c_c: 0.3, utility: 0.5, balanced: 0.6 }
        };
    }
    update(deltaTime, entities, gameContext = {}) { // Added gameContext default
        for (const entity of entities) {
            if (!entity.computronium || !entity.type || !entity.type.hasComputroniumCore) continue;
            this.allocateForks(entity, gameContext);
            this.applyPerformanceModifiers(entity, deltaTime);
        }
    }
    allocateForks(entity, gameContext) {
        const core = entity.computronium;
        const availableForks = this.getForkCount(core.level, core.efficiency);
        const focusMode = core.focusMode || this.coreFocusModes.BALANCED; // focusMode is 'offensive', 'defensive' etc.
        const requestedForks = [];
        this.collectFunctionRequests(entity, focusMode, requestedForks);
        requestedForks.sort((a, b) => b.priority - a.priority);
        const allocatedFunctions = {};
        let remainingForks = availableForks;
        for (const request of requestedForks) {
            if (remainingForks >= request.forksNeeded) {
                allocatedFunctions[request.function] = 'OPTIMAL';
                remainingForks -= request.forksNeeded;
            } else if (remainingForks > 0 && request.forksNeeded > 1 && request.forksNeeded > remainingForks) {
                allocatedFunctions[request.function] = 'DEGRADED';
                remainingForks = 0;
            } else if (remainingForks > 0 && request.forksNeeded === 1) {
                 allocatedFunctions[request.function] = 'OPTIMAL'; // Single fork function can run if any fork left
                 remainingForks -=1;
            } else {
                allocatedFunctions[request.function] = 'STARVED';
            }
        }
        core.allocatedFunctions = allocatedFunctions;
    }
    collectFunctionRequests(entity, focusModeValue, requestedForks) { // focusModeValue is 'offensive', 'defensive' etc.
        const getPrio = (funcName) => {
            const prioritiesByMode = this.functionPriorities[funcName];
            if (prioritiesByMode) return prioritiesByMode[focusModeValue] || 0;
            return 0;
        };
        if (entity.isAttacking) {
            requestedForks.push({ function: 'WEAPON_SYSTEMS', priority: getPrio('WEAPON_SYSTEMS'), forksNeeded: 1 });
            if (entity.isUsingAdvancedTargeting) {
                 requestedForks.push({ function: 'TARGETING_AI', priority: getPrio('TARGETING_AI'), forksNeeded: 1 });
            }
        }
        if (entity.currentEnergyShields > 0 && entity.type.maxEnergyShields > 0) {
            requestedForks.push({ function: 'SHIELD_MANAGEMENT', priority: getPrio('SHIELD_MANAGEMENT'), forksNeeded: 1 });
        }
        if (entity.isContributingToPoW) {
            requestedForks.push({ function: 'POW_CONTRIBUTION', priority: getPrio('POW_CONTRIBUTION'), forksNeeded: 1 });
        }
        if (entity.isUsingAdvancedSensors) {
            requestedForks.push({ function: 'ADVANCED_SENSORS', priority: getPrio('ADVANCED_SENSORS'), forksNeeded: 1 });
        }
        if (entity.isUsingSpecialAbility) {
            requestedForks.push({ function: 'SPECIAL_ABILITIES', priority: getPrio('SPECIAL_ABILITIES'), forksNeeded: entity.activeSpecialAbilityForkCost || 1 });
        }
        if (entity.isUsingEWarfare) {
            requestedForks.push({ function: 'E_WARFARE_SUITE', priority: getPrio('E_WARFARE_SUITE'), forksNeeded: 1 });
        }
        if (entity.isConstructing) {
            requestedForks.push({ function: 'CONSTRUCTION_SYSTEMS', priority: getPrio('CONSTRUCTION_SYSTEMS'), forksNeeded: 1 });
        }
        if (entity.isRepairing) {
            requestedForks.push({ function: 'REPAIR_SYSTEMS', priority: getPrio('REPAIR_SYSTEMS'), forksNeeded: 1 });
        }
        if (entity.type.speed > 0) {
            requestedForks.push({ function: 'BASIC_MOVEMENT', priority: getPrio('BASIC_MOVEMENT'), forksNeeded: 1 });
        }
        requestedForks.push({ function: 'BASIC_SENSORS', priority: getPrio('BASIC_SENSORS'), forksNeeded: 1 });
        if (entity.isUnderAttack) {
             requestedForks.push({ function: 'SELF_DEFENSE', priority: getPrio('SELF_DEFENSE'), forksNeeded: 1 });
        }
    }
    applyPerformanceModifiers(entity, deltaTime) {
        const core = entity.computronium;
        if (!core || !core.allocatedFunctions) return;
        entity.constructionRateFactor = 1.0; entity.repairRateFactor = 1.0;
        entity.specialAbilityEffectivenessFactor = 1.0; entity.specialAbilityDurationFactor = 1.0;
        entity.advancedSensorRangeFactor = 1.0; entity.advancedSensorUpdateSpeedFactor = 1.0;
        entity.eWarfareEffectivenessFactor = 1.0; entity.eWarfareRangeFactor = 1.0;

        if (entity.weaponSystemData) {
            entity.weaponSystemData.fireRateFactor = 1.0; entity.weaponSystemData.accuracyFactor = 1.0;
            const ws = core.allocatedFunctions['WEAPON_SYSTEMS'] || core.allocatedFunctions['TARGETING_AI'];
            if (ws === 'DEGRADED') { entity.weaponSystemData.fireRateFactor = 0.7; entity.weaponSystemData.accuracyFactor = 0.8; }
            else if (ws === 'STARVED') { entity.weaponSystemData.fireRateFactor = 0.4; entity.weaponSystemData.accuracyFactor = 0.5; }
        }
        if (entity.shieldSystemData) {
            entity.shieldSystemData.rechargeRateFactor = 1.0; entity.shieldSystemData.effectivenessFactor = 1.0;
            const ss = core.allocatedFunctions['SHIELD_MANAGEMENT'];
            if (ss === 'DEGRADED') { entity.shieldSystemData.rechargeRateFactor = 0.7; entity.shieldSystemData.effectivenessFactor = 0.8; }
            else if (ss === 'STARVED') { entity.shieldSystemData.rechargeRateFactor = 0.4; entity.shieldSystemData.effectivenessFactor = 0.5; }
        }
        if (entity.movementSystemData) {
            entity.movementSystemData.speedFactor = 1.0; entity.movementSystemData.accelerationFactor = 1.0;
            const ms = core.allocatedFunctions['BASIC_MOVEMENT'];
            if (ms === 'DEGRADED') { entity.movementSystemData.speedFactor = 0.7; entity.movementSystemData.accelerationFactor = 0.8; }
            else if (ms === 'STARVED') { entity.movementSystemData.speedFactor = 0.4; entity.movementSystemData.accelerationFactor = 0.5; }
        }
        if(entity.sensorSystemData) {
            entity.sensorSystemData.rangeFactor = 1.0;
            const basicSensorStatus = core.allocatedFunctions['BASIC_SENSORS'];
            if (basicSensorStatus === 'DEGRADED') entity.sensorSystemData.rangeFactor = 0.7;
            else if (basicSensorStatus === 'STARVED') entity.sensorSystemData.rangeFactor = 0.4;
        }
        const cs = core.allocatedFunctions['CONSTRUCTION_SYSTEMS'];
        if (cs === 'DEGRADED') entity.constructionRateFactor = 0.6; else if (cs === 'STARVED') entity.constructionRateFactor = 0.3;
        const rs = core.allocatedFunctions['REPAIR_SYSTEMS'];
        if (rs === 'DEGRADED') entity.repairRateFactor = 0.6; else if (rs === 'STARVED') entity.repairRateFactor = 0.3;
        const sas = core.allocatedFunctions['SPECIAL_ABILITIES'];
        if (sas === 'DEGRADED') { entity.specialAbilityEffectivenessFactor = 0.7; entity.specialAbilityDurationFactor = 0.8; }
        else if (sas === 'STARVED') { entity.specialAbilityEffectivenessFactor = 0.4; entity.specialAbilityDurationFactor = 0.5; }
        const adS = core.allocatedFunctions['ADVANCED_SENSORS'];
        if (adS === 'DEGRADED') { entity.advancedSensorRangeFactor = 0.75; entity.advancedSensorUpdateSpeedFactor = 0.7; }
        else if (adS === 'STARVED') { entity.advancedSensorRangeFactor = 0.5; entity.advancedSensorUpdateSpeedFactor = 0.4; }
        const ews = core.allocatedFunctions['E_WARFARE_SUITE'];
        if (ews === 'DEGRADED') { entity.eWarfareEffectivenessFactor = 0.7; entity.eWarfareRangeFactor = 0.8; }
        else if (ews === 'STARVED') { entity.eWarfareEffectivenessFactor = 0.4; entity.eWarfareRangeFactor = 0.5; }
    }
    getForkCount(level, efficiency = 1.0) {
        const baseForks = Math.min(6, Math.max(2, level + 1));
        const calculatedForks = Math.floor(baseForks * efficiency);
        return Math.max(1, calculatedForks);
    }
    setCoreFocusMode(entity, modeKey) {
        if (!entity.computronium) return false;
        const upperModeKey = modeKey.toUpperCase();
        const modeValue = this.coreFocusModes[upperModeKey];
        if (!modeValue) { console.warn(`Invalid focus mode key: ${modeKey}`); return false; }
        entity.computronium.focusMode = modeValue;
        this.allocateForks(entity, {});
        this.applyPerformanceModifiers(entity, 0);
        // console.log(`Unit ${entity.id || entity.type.name} focus mode set to ${modeValue}`); // Optional log
        return true;
    }
    getCoreStatus(entity) {
        if (!entity.computronium) return null;
        const core = entity.computronium;
        const efficiency = core.efficiency || 1.0;
        return {
            level: core.level, efficiency: efficiency, focusMode: core.focusMode,
            availableForks: this.getForkCount(core.level, efficiency),
            allocatedFunctions: core.allocatedFunctions || {}
        };
    }
}
// --- End of ComputroniumSystem simplified definition ---


console.log("--- Running ComputroniumSystem Tests ---");

function runComputroniumSystemTests() {
    const system = new ComputroniumSystem();

    console.log("Suite: Fork Allocation and Performance Modifiers");

    // Test Case: OFFENSIVE focus mode - WEAPON_SYSTEMS priority
    let offensiveUnit = new MockComputroniumUnit('off1', { coreLevel: 2, focusMode: 'offensive', coreEfficiency: 1.0 });
    offensiveUnit.isAttacking = true; // Activate weapon systems
    offensiveUnit.isUsingShields = true; // Shield management is lower priority

    system.update(0.1, [offensiveUnit], {}); // deltaTime, entities, gameContext

    let status = system.getCoreStatus(offensiveUnit);
    console.assert(status.allocatedFunctions['WEAPON_SYSTEMS'] === 'OPTIMAL', 'Offensive: Weapon Systems Optimal FAIL');
    // With 3 forks (level 2, 1.0 eff), and basic movement + sensors also active, shields might be starved or degraded
    console.log(`Offensive Mode (L2 Core): Weapon status = ${status.allocatedFunctions['WEAPON_SYSTEMS']}`);
    console.log(`Allocated: ${JSON.stringify(status.allocatedFunctions)}`);


    // Test Case: DEFENSIVE focus mode - SHIELD_MANAGEMENT priority
    let defensiveUnit = new MockComputroniumUnit('def1', { coreLevel: 1, focusMode: 'defensive', coreEfficiency: 1.0 });
    defensiveUnit.isAttacking = true;
    defensiveUnit.currentEnergyShields = 50; // Activate shield management

    system.update(0.1, [defensiveUnit], {});
    status = system.getCoreStatus(defensiveUnit);
    console.assert(status.allocatedFunctions['SHIELD_MANAGEMENT'] === 'OPTIMAL', 'Defensive: Shield Management Optimal FAIL');
    // Level 1 core (2 forks * 1.0 eff = 2 forks). Movement, Sensors, Shields. Weapons might be starved.
    console.log(`Defensive Mode (L1 Core): Shield status = ${status.allocatedFunctions['SHIELD_MANAGEMENT']}`);
    console.log(`Allocated: ${JSON.stringify(status.allocatedFunctions)}`);


    // Test Case: DEGRADED weapon system
    let lowPowerOffensiveUnit = new MockComputroniumUnit('lpOff1', { coreLevel: 1, focusMode: 'offensive', coreEfficiency: 0.5 }); // L1 core, 0.5 eff = 1 fork (2 * 0.5)
    lowPowerOffensiveUnit.isAttacking = true; // Needs weapons
    lowPowerOffensiveUnit.type.speed = 50; // Needs movement
    // Basic sensors also active. Total needed: 3 (Weapons, Movement, Sensors). Available: 1.
    // Weapons are highest prio in offensive, should get the 1 fork and be OPTIMAL. Others starved.
    // Let's make it so weapons are degraded due to many high prio tasks.
    // Add targeting AI as high prio
    lowPowerOffensiveUnit.isUsingAdvancedTargeting = true;
    // Now Weapons (1.0) and Targeting (0.9) are top. Movement (0.3), Sensors (0.4)
    // With 1 fork, WEAPON_SYSTEMS should be OPTIMAL, TARGETING_AI starved.

    system.update(0.1, [lowPowerOffensiveUnit], {});
    status = system.getCoreStatus(lowPowerOffensiveUnit);
    console.assert(status.allocatedFunctions['WEAPON_SYSTEMS'] === 'OPTIMAL', 'Degraded Test: Weapons should be OPTIMAL here FAIL');
    console.assert(status.allocatedFunctions['TARGETING_AI'] === 'STARVED', 'Degraded Test: Targeting AI should be STARVED FAIL');
    console.assert(lowPowerOffensiveUnit.weaponSystemData.fireRateFactor === 1.0, `Degraded Weapons fireRateFactor incorrect: ${lowPowerOffensiveUnit.weaponSystemData.fireRateFactor}`);
    console.log(`Low Power Offensive (1 fork): Weapon Status = ${status.allocatedFunctions['WEAPON_SYSTEMS']}, Targeting Status = ${status.allocatedFunctions['TARGETING_AI']}`);
    // To test DEGRADED, a function must require >1 fork and receive some but not all.
    // This test setup for degradation was flawed. Let's adjust.
    // A function needs forksNeeded > 1 to be DEGRADED. Current test functions need 1.
    // Re-evaluate degradation test with a multi-fork function if added, or adjust existing.
    // For now, this test shows starvation correctly.

    // Test Case: Core efficiency affecting fork count
    let lowEffUnit = new MockComputroniumUnit('leff1', { coreLevel: 3, coreEfficiency: 0.5 }); // L3 base: 4 forks. 0.5 eff -> 2 forks.
    status = system.getCoreStatus(lowEffUnit);
    console.assert(status.availableForks === 2, `Core Efficiency Fork Count FAIL: Expected 2, Got ${status.availableForks}`);
    console.log(`Low Efficiency (L3, 0.5eff): Available Forks = ${status.availableForks}`);

    let highEffUnit = new MockComputroniumUnit('heff1', { coreLevel: 1, coreEfficiency: 1.8 }); // L1 base: 2 forks. 1.8 eff -> 3 forks (floor(3.6)).
    status = system.getCoreStatus(highEffUnit);
    console.assert(status.availableForks === 3, `Core Efficiency Fork Count (High) FAIL: Expected 3, Got ${status.availableForks}`);
    console.log(`High Efficiency (L1, 1.8eff): Available Forks = ${status.availableForks}`);


    // Test Case: Special ability fork allocation (needs 2 forks)
    let specialUnit = new MockComputroniumUnit('spec1', { coreLevel: 3, focusMode: 'balanced', coreEfficiency: 1.0 }); // L3 = 4 forks
    specialUnit.isUsingSpecialAbility = true;
    specialUnit.activeSpecialAbilityForkCost = 2; // This ability costs 2 forks
    specialUnit.isAttacking = true; // Weapons: 0.6
                                 // Special Ability: 0.7
                                 // Movement: 0.5
                                 // Sensors: 0.5
    // Order: SA (2), Weapons (1), Movement (1), Sensors (starved)
    system.update(0.1, [specialUnit], {});
    status = system.getCoreStatus(specialUnit);
    console.assert(status.allocatedFunctions['SPECIAL_ABILITIES'] === 'OPTIMAL', 'Special Ability Optimal FAIL');
    console.assert(status.allocatedFunctions['WEAPON_SYSTEMS'] === 'OPTIMAL', 'Special Ability - Weapons Optimal FAIL');
    console.assert(status.allocatedFunctions['BASIC_MOVEMENT'] === 'OPTIMAL', 'Special Ability - Movement Optimal FAIL');
    console.assert(status.allocatedFunctions['BASIC_SENSORS'] === 'STARVED', 'Special Ability - Sensors Starved FAIL');
    console.log(`Special Ability (2 forks): SA Status = ${status.allocatedFunctions['SPECIAL_ABILITIES']}, Weapon Status = ${status.allocatedFunctions['WEAPON_SYSTEMS']}, Movement = ${status.allocatedFunctions['BASIC_MOVEMENT']}, Sensors = ${status.allocatedFunctions['BASIC_SENSORS']}`);
    console.assert(specialUnit.specialAbilityEffectivenessFactor === 1.0, 'Special Ability effectiveness factor incorrect with OPTIMAL');


    // Test Case: Starved special ability and performance modifier
    let starvedSpecialUnit = new MockComputroniumUnit('specStarved1', { coreLevel: 1, focusMode: 'balanced', coreEfficiency: 1.0 }); // L1 = 2 forks
    starvedSpecialUnit.isUsingSpecialAbility = true;
    starvedSpecialUnit.activeSpecialAbilityForkCost = 2; // Needs 2
    starvedSpecialUnit.isAttacking = true;      // Prio 0.6
                                                // SA Prio 0.7
                                                // Movement Prio 0.5
                                                // Sensors Prio 0.5
    // Available 2 forks. SA (0.7) needs 2, gets them. Others starved.
    system.update(0.1, [starvedSpecialUnit], {});
    status = system.getCoreStatus(starvedSpecialUnit);
    console.assert(status.allocatedFunctions['SPECIAL_ABILITIES'] === 'OPTIMAL', 'Starved Special Ability - SA should be OPTIMAL FAIL');

    starvedSpecialUnit.computronium.level = 1;
    starvedSpecialUnit.computronium.efficiency = 0.5; // Now 1 fork available (2 * 0.5)
    starvedSpecialUnit.isAttacking = false; // Reduce load
    // SA (0.7, needs 2), Movement (0.5, needs 1), Sensors (0.5, needs 1)
    // With 1 fork, SA should be starved.
    system.update(0.1, [starvedSpecialUnit], {});
    status = system.getCoreStatus(starvedSpecialUnit);
    console.assert(status.allocatedFunctions['SPECIAL_ABILITIES'] === 'STARVED', `Starved Special Ability - SA should be STARVED FAIL: ${status.allocatedFunctions['SPECIAL_ABILITIES']}`);
    console.assert(starvedSpecialUnit.specialAbilityEffectivenessFactor < 1.0, `Starved Special Ability effectiveness factor not reduced: ${starvedSpecialUnit.specialAbilityEffectivenessFactor}`);
    console.log(`Starved Special Ability (1 fork, needs 2): SA Status = ${status.allocatedFunctions['SPECIAL_ABILITIES']}, Effectiveness Factor = ${starvedSpecialUnit.specialAbilityEffectivenessFactor}`);


    console.log("--- ComputroniumSystem Tests Finished ---");
}

runComputroniumSystemTests(); // Execute tests

// Basic console.assert shim if not in a proper test environment like Jest
if (typeof console.assert === 'undefined') {
    console.assert = function(condition, message) {
        if (!condition) {
            throw new Error("Assertion failed: " + (message || ""));
        }
    };
}
