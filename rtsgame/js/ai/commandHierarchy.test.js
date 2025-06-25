// Test suite for commandHierarchy.js

// --- Imports from source files (assuming ES6 module structure or global availability) ---
// For real modules, you would use: import { CommandGroup, ... } from './commandHierarchy.js';
// And: import { calculateAssetScore, ... } from './strategicAI.js';

// --- Mocked/Copied strategicAI functions (if not true modules) ---
const ASSET_SCORE_HEALTH_K_FACTOR_strategicAI = 0.2; // Use a distinct name to avoid conflict if commandHierarchy also defines it

function getUnitMaxHP_strategicAI(unit) {
    if (unit && unit.type && typeof unit.type.hp === 'number' && unit.type.hp > 0) {
        return unit.type.hp;
    }
    return 100;
}

function calculateRelativeHealth_strategicAI(unit, maxHP) {
    if (!unit || typeof unit.hp !== 'number' || maxHP <= 0) {
        return 0;
    }
    return Math.max(0, Math.min(1, unit.hp / maxHP));
}

function calculateBasePower_strategicAI(unit) {
    if (!unit || !unit.type) return 10;
    let bp = unit.type.damage || 10;
    if (unit.type.tier) {
        bp *= (1 + (unit.type.tier - 1) * 0.5);
    }
    if (UNIT_TYPES_mock.commander && unit.type.name === UNIT_TYPES_mock.commander.name) {
        bp *= 5.0;
    } else if (unit.type.isExperimental) {
        bp *= 3.0;
    }
    return Math.max(1, bp);
}

function calculateAssetScore_strategicAI(targetUnit) {
    if (!targetUnit || typeof targetUnit.hp !== 'number' || targetUnit.hp <= 0) {
        return 0;
    }
    const maxHP = getUnitMaxHP_strategicAI(targetUnit);
    const rh = calculateRelativeHealth_strategicAI(targetUnit, maxHP);
    const bp = calculateBasePower_strategicAI(targetUnit);
    const assetScoreValue = bp / (rh + ASSET_SCORE_HEALTH_K_FACTOR_strategicAI);
    return Math.max(0, assetScoreValue);
}


// --- Constants from commandHierarchy.js (redefined for testing) ---
const LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK = 0.3;
const MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT = 4;
const NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET = 2;
const MIN_POWER_RATIO_FOR_GROUP_COMMITMENT = 0.4;

// --- Mock UNIT_TYPES ---
const UNIT_TYPES_mock = {
    commander: { name: 'Commander', hp: 2000, damage: 100, tier: 3, domain: 'land' },
    tank: { name: 'Tank', hp: 300, damage: 50, tier: 1, domain: 'land' },
    heavyTank: { name: 'HeavyTank', hp: 500, damage: 80, tier: 2, domain: 'land' },
    fighter: { name: 'Fighter', hp: 150, damage: 30, tier: 1, domain: 'air', range: 200 },
    bomber: { name: 'Bomber', hp: 250, damage: 100, tier: 2, domain: 'air', range: 250 },
    antiAirGun: { name: 'AntiAirGun', hp: 200, damage: 20, tier: 1, domain: 'land' }, // AA unit
    antiAirMissile: { name: 'AntiAirMissile', hp: 180, damage: 40, tier: 2, domain: 'land' } // AA unit
};

// --- Mock GameContext ---
const getMockGameContext_ch = () => ({
    units: [],
    buildings: [],
    resourceNodes: [], // If needed by any CommandGroup logic
    WORLD_SIZE: 2000, // If needed
    // Mock other parts of gameContext as required by CommandGroup methods
    // For example, if recordAIDecision is called via gameContext.battleJournal
    battleJournal: {
        recordEvent: () => {}, // Mock implementation
        isRecording: true
    },
    entityManager: { // If CommandGroup interacts with entityManager (e.g., for captions)
        addCaption: () => {}
    },
    seedRandom: { // If any part of CommandGroup uses seeded random
        random: () => Math.random()
    }
});

// --- Mock Unit Creation Helper ---
let nextUnitId = 1;
const createMockUnit = (config) => {
    const unitId = `u${nextUnitId++}`;
    return {
        id: unitId,
        team: 'blue',
        type: UNIT_TYPES_mock.tank, // Default type
        hp: (config.type && config.type.hp) || UNIT_TYPES_mock.tank.hp,
        x: 0,
        y: 0,
        target: null,
        patrolTarget: null,
        commandGroup: null, // Important for command hierarchy logic
        ...config,
    };
};

// --- commandHierarchy.js Code (Copied for testing if not using true modules) ---
// This is a significant dependency. In a real modular project, you'd import.
// For now, I'll assume the CommandGroup, MissionType, UnitRole, CommandRank are available globally
// or will be copied/mocked here.
// For this test, let's assume we can instantiate CommandGroup and it can find its dependencies.
// This would mean CommandGroup itself and its enums (MissionType, UnitRole, CommandRank)
// are either globally defined (e.g. by concatenating files before test) or properly imported.
// I will copy CommandGroup and its enums here for self-containment for now.

const CommandRank = { COMMANDER: 5, COLONEL: 4, MAJOR: 3, CAPTAIN: 2, LIEUTENANT: 1, PRIVATE: 0 };
const MissionType = { SEARCH: 'search', HUNT: 'hunt', COLLECT: 'collect', DEFEND: 'defend', ASSAULT: 'assault', SUPPORT: 'support', SORTIE: 'sortie' };
const UnitRole = { ANTI_AIR: 'anti_air', AIR: 'air', GROUND: 'ground', AMPHIBIOUS: 'amphibious', SUPPORT: 'support' };

class CommandGroup {
    constructor(leader, mission = MissionType.SEARCH, priority = 1) {
        this.id = Math.random().toString(36).substring(2, 8);
        this.leader = leader;
        this.members = [leader];
        this.mission = mission;
        this.target = null;
        // Simplified for testing - add other properties as needed by tested methods
        this.searchRadius = 300; // Used by findNearestEnemy
    }

    // Add only methods being directly tested or essential for them
    getRank(unit) { // Copied from source
        if (!unit.type) return CommandRank.PRIVATE;
        if (unit.type.name === 'Commander' || unit.type.name === 'ACU') return CommandRank.COMMANDER;
        if (unit.type.tier >= 3) return CommandRank.COLONEL;
        if (unit.type.tier >= 2) return CommandRank.MAJOR;
        if (unit.type.damage > 30 || unit.type.support) return CommandRank.CAPTAIN;
        if (unit.type.speed > 2.5) return CommandRank.LIEUTENANT;
        return CommandRank.PRIVATE;
    }

    getUnitRole(unit) { // Copied from source
        if (!unit.type) return UnitRole.GROUND;
        if (unit.type.domain === 'air') return UnitRole.AIR;
        if (unit.type.name.includes('AA') || unit.type.name.includes('Anti-Air')) return UnitRole.ANTI_AIR;
        if (unit.type.movementType === 'amphibious') return UnitRole.AMPHIBIOUS;
        if (unit.type.support) return UnitRole.SUPPORT;
        return UnitRole.GROUND;
    }

    findNearestEnemy(gameContext) { // Simplified mock for executeHunt tests
        // This would normally search gameContext.units
        // For tests, we'll often override this on the instance.
        if (this._mockNearestEnemy) return this._mockNearestEnemy;
        return null;
    }

    update(gameContext) { // Stub, real one calls executeMission
        this.members = this.members.filter(member => member.hp > 0); // Basic cleanup
        if (this.members.length === 0) return false;
        if (!this.members.includes(this.leader)) this.leader = this.members[0] || null; // Basic leader maintenance

        if(this.leader) this.executeMission(gameContext); // Call executeMission if leader exists
        return true;
    }

    executeMission(gameContext) { // Stub, real one calls specific mission types
        if (this.mission === MissionType.HUNT) {
            this.executeHunt(gameContext);
        }
        // Other mission types can be added if their tests are needed
    }

    executeHunt(gameContext) { // Copied and adapted from source
        const nearestEnemy = this.findNearestEnemy(gameContext);
        this.target = nearestEnemy;

        if (this.target) {
            const targetAssetScore = calculateAssetScore_strategicAI(this.target);
            const groupPower = this.members.reduce((sum, member) => sum + calculateBasePower_strategicAI(member), 0);

            if (this.target.type !== UNIT_TYPES_mock.commander &&
                groupPower < targetAssetScore * MIN_POWER_RATIO_FOR_GROUP_COMMITMENT &&
                this.members.length > 0) {
                this.target = null;
                this.mission = MissionType.SEARCH;
            }

            if (this.target) {
                const targetMaxHP = getUnitMaxHP_strategicAI(this.target);
                const targetRH = calculateRelativeHealth_strategicAI(this.target, targetMaxHP);

                if (this.members.length >= MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT &&
                    targetRH < LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK &&
                    targetRH > 0 &&
                    this.target.type && this.target.type.name !== UNIT_TYPES_mock.commander.name
                ) {
                    const sortedMembers = [...this.members].sort((a, b) => {
                        const distA = Math.sqrt(Math.pow(a.x - this.target.x, 2) + Math.pow(a.y - this.target.y, 2));
                        const distB = Math.sqrt(Math.pow(b.x - this.target.x, 2) + Math.pow(b.y - this.target.y, 2));
                        return distA - distB;
                    });
                    for (let i = 0; i < sortedMembers.length; i++) {
                        const member = sortedMembers[i];
                        if (i < NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET) {
                            member.target = this.target;
                        } else {
                            member.target = null;
                            member.patrolTarget = {
                                x: this.target.x + (Math.random() - 0.5) * 50,
                                y: this.target.y + (Math.random() - 0.5) * 50
                            };
                        }
                    }
                } else {
                    this.members.forEach(member => { member.target = this.target; });
                }
            } else {
                this.members.forEach(member => { member.target = null; });
            }
        } else {
            this.members.forEach(member => { member.target = null; });
        }
    }

    scoreStrikeTarget(target, gameContext) { // Copied and adapted from source
        let newScore = calculateAssetScore_strategicAI(target);
        const distance = Math.sqrt(Math.pow(target.x - this.leader.x, 2) + Math.pow(target.y - this.leader.y, 2));
        newScore -= distance / 20;

        const aaThreats = gameContext.units.filter(u =>
            u.team !== this.leader.team &&
            this.getUnitRole(u) === UnitRole.ANTI_AIR &&
            Math.sqrt(Math.pow(u.x - target.x, 2) + Math.pow(u.y - target.y, 2)) < 300
        ).length;
        newScore -= aaThreats * 15;
        return Math.max(0, newScore);
    }

    findBestTargetForAirUnit(airUnit, gameContext) { // Copied and adapted from source
        const enemies = gameContext.units.filter(u =>
            u.team !== airUnit.team &&
            u.hp > 0 &&
            Math.sqrt(Math.pow(u.x - airUnit.x, 2) + Math.pow(u.y - airUnit.y, 2)) < (airUnit.type.range || 100) * 1.5
        );
        if (enemies.length === 0) return null;
        let bestTarget = null;
        let bestScore = -Infinity;

        for (const enemy of enemies) {
            let newScore = calculateAssetScore_strategicAI(enemy);
            const enemyRole = this.getUnitRole(enemy);
            if (enemyRole === UnitRole.GROUND) newScore += 30;
            else if (enemyRole === UnitRole.ANTI_AIR) newScore *= 0.5;
            else if (enemyRole === UnitRole.AIR) newScore *= 1.1;

            const distance = Math.sqrt(Math.pow(enemy.x - airUnit.x, 2) + Math.pow(enemy.y - airUnit.y, 2));
            newScore -= distance / 10;

            if (newScore > bestScore) {
                bestScore = newScore;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }
}


// --- Assertion Helper & Test Runner (same as strategicAI.test.js) ---
function assert(condition, message) {
    if (!condition) {
        console.error(`Assertion failed: ${message}`);
        throw new Error(message || "Assertion failed");
    }
    console.log(`Test passed: ${message}`);
}
const tests_ch = [];
function test(description, fn) {
    tests_ch.push({ description, fn });
}

async function runCommandHierarchyTests() {
    console.log("Running commandHierarchy.js tests...");

    test('CommandGroup.scoreStrikeTarget: basic scoring', () => {
        const leader = createMockUnit({ x: 0, y: 0, type: UNIT_TYPES_mock.tank });
        const group = new CommandGroup(leader);
        const mockCtx = getMockGameContext_ch();

        const targetUnit = createMockUnit({ x: 100, y: 0, type: UNIT_TYPES_mock.heavyTank, hp: UNIT_TYPES_mock.heavyTank.hp }); // dist 100
        // HeavyTank BP = 120. AssetScore = 120 / 1.2 = 100
        // Distance penalty = 100/20 = 5. Score = 100 - 5 = 95
        let score = group.scoreStrikeTarget(targetUnit, mockCtx);
        assert(Math.abs(score - 95) < 0.01, `Score basic target. Expected ~95, Got ${score}`);

        mockCtx.units.push(createMockUnit({ team: 'red', type: UNIT_TYPES_mock.antiAirGun, x: 150, y: 0 })); // 1 AA threat near target
        // AA penalty = 15. Score = 95 - 15 = 80
        score = group.scoreStrikeTarget(targetUnit, mockCtx);
        assert(Math.abs(score - 80) < 0.01, `Score with AA threat. Expected ~80, Got ${score}`);
    });

    test('CommandGroup.findBestTargetForAirUnit: scoring and selection', () => {
        const airUnit = createMockUnit({ type: UNIT_TYPES_mock.fighter, team: 'blue', x:0, y:0 });
        const group = new CommandGroup(airUnit); // Leader is the air unit
        const mockCtx = getMockGameContext_ch();

        const enemyFighter = createMockUnit({ id: 'ef1', team: 'red', type: UNIT_TYPES_mock.fighter, hp: UNIT_TYPES_mock.fighter.hp, x: 50, y: 0 }); // dist 50
        const enemyBomber = createMockUnit({ id: 'eb1', team: 'red', type: UNIT_TYPES_mock.bomber, hp: UNIT_TYPES_mock.bomber.hp, x: 60, y: 0 }); // dist 60, higher base power
        const enemyAA = createMockUnit({ id: 'eaa1', team: 'red', type: UNIT_TYPES_mock.antiAirGun, hp: UNIT_TYPES_mock.antiAirGun.hp, x: 40, y: 0 }); // dist 40, AA threat
        mockCtx.units = [enemyFighter, enemyBomber, enemyAA];

        // Fighter: BP 30, AS ~25. Score vs Air: *1.1. Dist penalty: 50/10=5. Score ~ 25*1.1 - 5 = 22.5
        // Bomber: BP 100*1.5=150. AS ~125. Score vs Air: *1.1. Dist penalty: 60/10=6. Score ~ 125*1.1 - 6 = 131.5
        // AA Gun: BP 20. AS ~16.6. Score vs AA: *0.5. Dist penalty: 40/10=4. Score ~ 16.6*0.5 - 4 = 4.3
        const bestTarget = group.findBestTargetForAirUnit(airUnit, mockCtx);
        assert(bestTarget && bestTarget.id === enemyBomber.id, 'Prefers bomber over fighter and AA');
    });

    test('CommandGroup.executeHunt: Avoid Overkill - Weak Target', () => {
        const leader = createMockUnit({ x:0, y:0, type: UNIT_TYPES_mock.tank});
        const group = new CommandGroup(leader, MissionType.HUNT);
        for (let i = 0; i < MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT -1; i++) { // Group of 4
            group.addMember(createMockUnit({ x: i*5, y: 0, type: UNIT_TYPES_mock.tank }));
        }

        const weakTarget = createMockUnit({ team: 'red', type: UNIT_TYPES_mock.scout, hp: UNIT_TYPES_mock.scout.hp * (LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK - 0.05), x: 100, y: 0 });
        group._mockNearestEnemy = weakTarget; // Mock findNearestEnemy

        const mockCtx = getMockGameContext_ch();
        group.update(mockCtx); // This calls executeHunt

        let focusedCount = 0;
        group.members.forEach(m => { if (m.target === weakTarget) focusedCount++; });
        assert(focusedCount === NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET, `Overkill: Expected ${NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET} units on weak target, got ${focusedCount}`);
        group.members.forEach((m, idx) => {
            if(m.target !== weakTarget) assert(m.patrolTarget !== null, `Member ${idx} not targeting weak unit should have patrol target`);
        });
    });

    test('CommandGroup.executeHunt: Avoid Overkill - Commander Target', () => {
        const leader = createMockUnit({type: UNIT_TYPES_mock.heavyTank});
        const group = new CommandGroup(leader, MissionType.HUNT);
        for (let i = 0; i < MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT; i++) {
            group.addMember(createMockUnit({type: UNIT_TYPES_mock.heavyTank}));
        }

        const weakCommander = createMockUnit({ team: 'red', type: UNIT_TYPES_mock.commander, hp: UNIT_TYPES_mock.commander.hp * (LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK - 0.1), x: 100, y: 0 });
        group._mockNearestEnemy = weakCommander;

        const mockCtx = getMockGameContext_ch();
        group.update(mockCtx);

        let focusedCount = 0;
        group.members.forEach(m => { if (m.target === weakCommander) focusedCount++; });
        assert(focusedCount === group.members.length, 'Overkill (Commander): All units should target weak Commander');
    });

    test('CommandGroup.executeHunt: Avoid Pointless Attack - Strong Target', () => {
        const leader = createMockUnit({ type: UNIT_TYPES_mock.scout, x:0,y:0 }); // Weak group leader
        const group = new CommandGroup(leader, MissionType.HUNT);
        group.addMember(createMockUnit({ type: UNIT_TYPES_mock.scout, x:5,y:0 })); // Group of 2 scouts

        // Scout BP = 5. GroupPower = 10.
        // Strong target (Heavy Tank): BP = 120. AssetScore = 120/1.2 = 100.
        // Group needs 100 * 0.4 (MIN_POWER_RATIO_FOR_GROUP_COMMITMENT) = 40 power. Group has 10.
        const strongTarget = createMockUnit({ team: 'red', type: UNIT_TYPES_mock.heavyTank, hp: UNIT_TYPES_mock.heavyTank.hp, x: 100, y: 0 });
        group._mockNearestEnemy = strongTarget;

        const mockCtx = getMockGameContext_ch();
        group.update(mockCtx);

        assert(group.target === null, 'Pointless Attack: Target should be cleared');
        assert(group.mission === MissionType.SEARCH, 'Pointless Attack: Mission should change to SEARCH');
    });

    test('CommandGroup.executeHunt: Avoid Pointless Attack - Strong Commander Target', () => {
        const leader = createMockUnit({ type: UNIT_TYPES_mock.scout, x:0,y:0 });
        const group = new CommandGroup(leader, MissionType.HUNT);
        group.addMember(createMockUnit({ type: UNIT_TYPES_mock.scout, x:5,y:0 }));

        const strongCommander = createMockUnit({ team: 'red', type: UNIT_TYPES_mock.commander, hp: UNIT_TYPES_mock.commander.hp, x: 100, y: 0 });
        group._mockNearestEnemy = strongCommander;
        // Commander BP = 1000. AssetScore = 1000/1.2 = 833.33. Group needs 833.33 * 0.4 = 333 power. Group has 10.
        // But it's a commander, so the check should be skipped.

        const mockCtx = getMockGameContext_ch();
        group.update(mockCtx);

        assert(group.target === strongCommander, 'Pointless Attack (Commander): Target should NOT be cleared for Commander');
        assert(group.mission === MissionType.HUNT, 'Pointless Attack (Commander): Mission should remain HUNT');
    });


    // --- Run all tests ---
    let passed_ch = 0;
    let failed_ch = 0;
    console.log("\n--- Running commandHierarchy.js Test Suite ---");
    for (const t of tests_ch) {
        console.log(`--- Starting test: ${t.description} ---`);
        try {
            await t.fn();
            console.log(`--- Test PASSED: ${t.description} ---`);
            passed_ch++;
        } catch (e) {
            console.error(`--- Test FAILED: ${t.description} ---`);
            console.error(e.stack);
            failed_ch++;
        }
    }
    console.log(`\nCommandHierarchy.js Tests Finished. Passed: ${passed_ch}, Failed: ${failed_ch}\n`);
    if (failed_ch > 0) throw new Error(`${failed_ch} tests failed in commandHierarchy.js overall.`);
}

// Trigger test run
runCommandHierarchyTests().catch(e => {
    console.error("Critical error running commandHierarchy.js tests:", e.message);
});
