// Test suite for strategicAI.js

// --- Constants (redefined if not exported from source) ---
const ASSET_SCORE_HEALTH_K_FACTOR = 0.2;
const TARGET_SCORE_DISTANCE_DIVISOR = 50;
const MIN_POWER_RATIO_TO_ENGAGE_ENEMY = 0.5;

// Mock UNIT_TYPES for testing purposes
const UNIT_TYPES = {
    commander: { name: 'Commander', hp: 2000, damage: 100, tier: 3 }, // Example commander
    tank: { name: 'Tank', hp: 300, damage: 50, tier: 1 },
    heavyTank: { name: 'HeavyTank', hp: 500, damage: 80, tier: 2 },
    experimentalTank: { name: 'ExperimentalTank', hp: 3000, damage: 200, tier: 3, isExperimental: true },
    scout: { name: 'Scout', hp: 50, damage: 5, tier: 1},
    noDamageUnit: { name: 'NoDamageUnit', hp: 100, tier: 1} // No damage property
};

// --- Mocking and Setup ---
let recordedDecisions = [];
const mockRecordAIDecision = (gameContext, team, decisionType, data) => {
    recordedDecisions.push({ team, decisionType, data });
};

// Simple Mock Unit class for AI tests
class MockUnit {
    constructor(id, team, type, x, y, hp, maxHp, initialEffectiveAuthority = 10) {
        this.id = id;
        this.team = team;
        this.type = type; // e.g., UNIT_TYPES.tank
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.maxHp = maxHp;
        this.target = null;
        this.patrolTarget = null;
        this.angle = 0; // Added for repositionForOffensive
        this.militaryRank = 'SERGEANT'; // Default rank
        this.currentCommander = null;
        this.effectiveAuthority = initialEffectiveAuthority;
        this.canPromoteSubordinates = false;
        this.provideMoraleBonus = false;
        this.lastAuthorityUpdate = 0;
        this.commandAuthority = type.tier ? type.tier * 10 : 10;
        this.baseAuthority = this.commandAuthority;
        this.healthAuthorityModifier = 0;
        this.veterancyAuthorityModifier = 0;
        this.contextAuthorityModifier = 0;
        this.computroniumAuthorityModifier = 0;
        this.combatExperience = 0;
        this.survivalTime = 0;
        this.commandExperience = 0;
        this.killCount = 0;
        this.commandFitness = 'FULL_COMMAND';
        this.veterancyLevel = 'GREEN';
        this.coreFocusMode = 'BALANCED'; // For selectOptimalTarget test

        this.calculateEffectiveAuthority = jest.fn(() => {
            let healthRatio = this.hp / this.maxHp;
            if (healthRatio >= 0.8) this.healthAuthorityModifier = 5;
            else if (healthRatio >= 0.5) this.healthAuthorityModifier = 2;
            else if (healthRatio >= 0.2) this.healthAuthorityModifier = -2;
            else this.healthAuthorityModifier = -5;

            this.effectiveAuthority = this.baseAuthority + this.healthAuthorityModifier +
                                      this.veterancyAuthorityModifier + this.contextAuthorityModifier +
                                      this.computroniumAuthorityModifier;
            this.lastAuthorityUpdate = Date.now();
            return this.effectiveAuthority;
        });
        this.getDistance = (otherEntity) => {
            if (!otherEntity) return Infinity;
            const dx = this.x - otherEntity.x;
            const dy = this.y - otherEntity.y;
            return Math.sqrt(dx * dx + dy * dy);
        };
    }
}

const jest = {
    fn: () => {
        const mockFn = (...args) => {
            mockFn.mock.calls.push(args);
        };
        mockFn.mock = { calls: [] };
        return mockFn;
    },
    spyOn: (obj, methodName) => {
        const originalMethod = obj[methodName];
        const spy = jest.fn();
        obj[methodName] = spy; // Replace method with spy
        return spy; // Return the spy itself
    }
};


const getMockGameContext = (friendlyUnits = [], enemyUnits = [], buildings = []) => {
    const allUnits = [...friendlyUnits, ...enemyUnits];
    return {
        units: allUnits,
        buildings: buildings,
        entityManager: {
            units: allUnits,
            buildings: buildings,
            addCaption: jest.fn(),
        },
        resources: { blue: { mass: 1000, energy: 1000 }, red: { mass: 1000, energy: 1000 }},
        gameState: { gameTime: 0, addEvent: jest.fn() },
        seedRandom: { random: () => Math.random() },
    };
};

const getMockAiStateTeam = () => ({
    personality: AI_PERSONALITIES.BALANCED,
    lastMajorDecision: 0,
    economicPhase: 'military',
    militaryStrategy: 'aggressive',
    targetPriorities: [],
    expansionTargets: [],
    raidCooldown: 0
});

// --- Assertion Helper ---
function assert(condition, message) {
    if (!condition) {
        console.error(`Assertion failed: ${message}`);
        throw new Error(message || "Assertion failed");
    }
    console.log(`Test passed: ${message}`);
}

// --- Test Runner ---
const tests = [];
function test(description, fn) {
    tests.push({ description, fn });
}

async function runTests() {
    console.log("Running strategicAI.js tests...");
    // Import functions from strategicAI.js
    // Assuming strategicAI.js exports these functions.
    // If strategicAI.js is not a module, these functions would need to be globally available or copied.
    // For this test, we'll assume they are available (e.g., copied or from an import if refactored)

    // Placeholder: Manually copying functions for now due to potential import/export limitations
    // In a real setup, we'd use ES6 modules properly.
    // These would be: getUnitMaxHP, calculateRelativeHealth, calculateBasePower, calculateAssetScore, selectOptimalTarget,
    // coordinateTacticalGroups, repositionForDefense (and their helpers)

    // --- Helper to get original functions from strategicAI.js for testing ---
    // Manually define/copy functions from strategicAI.js that are not class methods
    // For a modular codebase, these would be imported.

    // Assuming strategicAI.js content is loaded and these functions are in its scope.
    // If not, they need to be copied here or imported if strategicAI.js exports them.
    // For the sandbox, we assume they are available or copied as previously shown.
    // The test runner itself copies some of them. We'll add coordinateTacticalGroups and repositionForDefense.

    // Functions from strategicAI.js needed for tests (ensure these match the source file if not importing)
    function getUnitMaxHP(unit) {
        if (unit && unit.type && typeof unit.type.hp === 'number' && unit.type.hp > 0) {
            return unit.type.hp;
        }
        return 100;
    }

    function calculateRelativeHealth(unit, maxHP) {
        if (!unit || typeof unit.hp !== 'number' || maxHP <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, unit.hp / maxHP));
    }

    function calculateBasePower(unit) {
        if (!unit || !unit.type) return 10;
        let bp = unit.type.damage || 10;
        if (unit.type.tier) {
            bp *= (1 + (unit.type.tier - 1) * 0.5);
        }
        if (UNIT_TYPES.commander && unit.type.name === UNIT_TYPES.commander.name) {
            bp *= 5.0;
        } else if (unit.type.isExperimental) {
            bp *= 3.0;
        }
        return Math.max(1, bp);
    }

    function calculateAssetScore(targetUnit) {
        if (!targetUnit || typeof targetUnit.hp !== 'number' || targetUnit.hp <= 0) {
            return 0;
        }
        const maxHP = getUnitMaxHP(targetUnit);
        const rh = calculateRelativeHealth(targetUnit, maxHP);
        const bp = calculateBasePower(targetUnit);
        const assetScoreValue = bp / (rh + ASSET_SCORE_HEALTH_K_FACTOR);
        return Math.max(0, assetScoreValue);
    }

    function selectOptimalTarget(gameContext, team, group) {
        const enemies = [...gameContext.units.filter(u => u.team !== team && u.hp > 0), // Ensure targets are alive
                        ...gameContext.buildings.filter(b => b.team !== team && b.hp > 0)]; // Ensure targets are alive
        let bestTarget = null;
        let bestScore = -Infinity;
        const groupEffectivePower = group.strength; // Using group.strength as proxy

        for (const enemy of enemies) {
            // if (enemy.hp <= 0) continue; // Redundant if filtered above

            const dist = Math.sqrt(
                (enemy.x - group.center.x) ** 2 + (enemy.y - group.center.y) ** 2
            );
            const baseAssetScore = calculateAssetScore(enemy);
            let currentScore = baseAssetScore / (dist + TARGET_SCORE_DISTANCE_DIVISOR);

            // Check if enemy.type exists before trying to access enemy.type.name
            const isCommander = enemy.type && UNIT_TYPES.commander && enemy.type.name === UNIT_TYPES.commander.name;

            if (!isCommander && groupEffectivePower < baseAssetScore * MIN_POWER_RATIO_TO_ENGAGE_ENEMY) {
                currentScore *= 0.1;
            }
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestTarget = enemy;
            }
        }
        return bestTarget;
    }


    // --- Test Suites ---

    test('getUnitMaxHP: returns correct max HP or fallback', () => {
        assert(getUnitMaxHP({ type: { hp: 150 } }) === 150, 'Defined HP');
        assert(getUnitMaxHP({ type: { name: 'Test', hp: -10 } }) === 100, 'Invalid HP fallback');
        assert(getUnitMaxHP({ type: {} }) === 100, 'Missing HP fallback');
        assert(getUnitMaxHP({}) === 100, 'Missing type fallback');
    });

    test('calculateRelativeHealth: calculates relative health correctly', () => {
        assert(calculateRelativeHealth({ hp: 100 }, 100) === 1.0, 'Full health');
        assert(calculateRelativeHealth({ hp: 50 }, 100) === 0.5, 'Half health');
        assert(calculateRelativeHealth({ hp: 0 }, 100) === 0.0, 'Zero health');
        assert(calculateRelativeHealth({ hp: 120 }, 100) === 1.0, 'Over max health (clamped)');
        assert(calculateRelativeHealth({ hp: -10 }, 100) === 0.0, 'Negative health (clamped)');
        assert(calculateRelativeHealth({ hp: 50 }, 0) === 0.0, 'Zero maxHP');
        assert(calculateRelativeHealth({ hp: 50 }, -10) === 0.0, 'Negative maxHP');
        assert(calculateRelativeHealth(null, 100) === 0.0, 'Null unit');
    });

    test('calculateBasePower: calculates base power correctly', () => {
        assert(calculateBasePower({ type: UNIT_TYPES.tank }) === (50 * (1 + (1-1)*0.5)), 'T1 Tank'); // 50
        assert(calculateBasePower({ type: UNIT_TYPES.heavyTank }) === (80 * (1 + (2-1)*0.5)), 'T2 Heavy Tank'); // 80 * 1.5 = 120
        assert(calculateBasePower({ type: UNIT_TYPES.commander }) === (100 * (1 + (3-1)*0.5) * 5.0), 'Commander'); // 100 * 2 * 5 = 1000
        assert(calculateBasePower({ type: UNIT_TYPES.experimentalTank }) === (200 * (1 + (3-1)*0.5) * 3.0), 'Experimental'); // 200 * 2 * 3 = 1200
        assert(calculateBasePower({ type: UNIT_TYPES.scout }) === (5 * (1 + (1-1)*0.5)), 'Scout'); // 5
        assert(calculateBasePower({ type: UNIT_TYPES.noDamageUnit }) === 10, 'Unit with no damage property'); // Default 10
        assert(calculateBasePower({ type: { name: 'Weakling', hp:10, damage: 0.1, tier: 1}}) === 1, 'Unit with very low damage (min 1)');
        assert(calculateBasePower(null) === 10, 'Null unit default power');
    });

    test('calculateAssetScore: calculates asset score correctly', () => {
        const fullHealthTank = { type: UNIT_TYPES.tank, hp: UNIT_TYPES.tank.hp };
        const tankMaxHP = getUnitMaxHP(fullHealthTank);
        const tankBP = calculateBasePower(fullHealthTank); // 50
        // Score = BP / (RH + K) = 50 / (1.0 + 0.2) = 50 / 1.2 = 41.66
        assert(Math.abs(calculateAssetScore(fullHealthTank) - (tankBP / (1.0 + ASSET_SCORE_HEALTH_K_FACTOR))) < 0.01, 'Full health tank score');

        const halfHealthTank = { type: UNIT_TYPES.tank, hp: UNIT_TYPES.tank.hp / 2 };
        // Score = BP / (RH + K) = 50 / (0.5 + 0.2) = 50 / 0.7 = 71.42
        assert(Math.abs(calculateAssetScore(halfHealthTank) - (tankBP / (0.5 + ASSET_SCORE_HEALTH_K_FACTOR))) < 0.01, 'Half health tank score');

        const deadTank = { type: UNIT_TYPES.tank, hp: 0 };
        assert(calculateAssetScore(deadTank) === 0, 'Dead unit score is 0');
    });

    test('selectOptimalTarget: basic prioritization', () => {
        const mockCtx = getMockGameContext();
        const group = { strength: 100, center: { x: 0, y: 0 }, units: [] };

        mockCtx.units = [
            { id: 1, team: 'red', type: UNIT_TYPES.tank, hp: UNIT_TYPES.tank.hp, x: 10, y: 0 },
            { id: 2, team: 'red', type: UNIT_TYPES.heavyTank, hp: UNIT_TYPES.heavyTank.hp, x: 100, y: 0 }
        ];
        // Enemy 1 (Tank): baseAssetScore = 50 / (1+0.2) = 41.66. dist = 10. currentScore = 41.66 / (10+50) = 0.694
        // Enemy 2 (HeavyTank): baseAssetScore = 120 / (1+0.2) = 100. dist = 100. currentScore = 100 / (100+50) = 0.666
        const best = selectOptimalTarget(mockCtx, 'blue', group);
        assert(best && best.id === 1, 'Selects closer, lower-asset target if score is higher due to proximity');

        mockCtx.units[1].x = 5; // Make HeavyTank much closer
        mockCtx.units[1].y = 0;
        // Enemy 1 (Tank): Still 0.694
        // Enemy 2 (HeavyTank): dist = 5. currentScore = 100 / (5+50) = 1.81
        const best2 = selectOptimalTarget(mockCtx, 'blue', group);
        assert(best2 && best2.id === 2, 'Selects closer, higher-asset target when distance makes its score higher');
    });

    test('selectOptimalTarget: Avoid Pointless - Strong Target', () => {
        const mockCtx = getMockGameContext();
        const group = { strength: 50, center: { x: 0, y: 0 }, units: [] };

        const strongNonCommanderType = { ...UNIT_TYPES.experimentalTank, name: "StrongNonCmd", hp: UNIT_TYPES.experimentalTank.hp, damage: UNIT_TYPES.experimentalTank.damage, tier: UNIT_TYPES.experimentalTank.tier, isExperimental: false }; // Ensure not commander
        const tankType = UNIT_TYPES.tank;

        mockCtx.units = [
            { id: 'A', team: 'red', type: strongNonCommanderType, hp: strongNonCommanderType.hp, x: 10, y: 0 },
            { id: 'B', team: 'red', type: tankType, hp: tankType.hp, x: 12, y: 0 }
        ];

        // Enemy A: BP = 200 * (1+(3-1)*0.5) = 400. AssetScore = 400 / 1.2 = 333.33
        // Group needs 333.33 * 0.5 = 166.66 power. Group has 50. Penalized.
        // Score A (penalized): (333.33 / (10+50)) * 0.1 = (5.55) * 0.1 = 0.555
        // Enemy B: BP = 50. AssetScore = 50 / 1.2 = 41.66
        // Group needs 41.66 * 0.5 = 20.83 power. Group has 50. Not penalized.
        // Score B: (41.66 / (12+50)) = 41.66 / 62 = 0.671

        const best = selectOptimalTarget(mockCtx, 'blue', group);
        assert(best && best.id === 'B', `Selects weaker target B (score ${best ? (calculateAssetScore(best) / (Math.sqrt(Math.pow(best.x - group.center.x,2) + Math.pow(best.y - group.center.y,2)) + TARGET_SCORE_DISTANCE_DIVISOR)) : 'N/A'}) over penalized strong target A`);
    });

    test('selectOptimalTarget: Avoid Pointless - Strong Commander', () => {
        const mockCtx = getMockGameContext();
        const group = { strength: 100, center: { x: 0, y: 0 }, units: [] };

        mockCtx.units = [
            { id: 'A', team: 'red', type: UNIT_TYPES.commander, hp: UNIT_TYPES.commander.hp, x: 10, y: 0 },
            { id: 'B', team: 'red', type: UNIT_TYPES.tank, hp: UNIT_TYPES.tank.hp, x: 12, y: 0 }
        ];
        // Commander A: BP = 100 * 2 * 5 = 1000. AssetScore = 1000 / 1.2 = 833.33
        // Group needs 833.33 * 0.5 = 416.6 power. Group has 100. Would be penalized, but is Commander.
        // Score A: (833.33 / (10+50)) = 13.88
        // Tank B: BP = 50. AssetScore = 50 / 1.2 = 41.66
        // Score B: (41.66 / (12+50)) = 0.671

        const best = selectOptimalTarget(mockCtx, 'blue', group);
        assert(best && best.id === 'A', 'Selects Commander A despite power disparity (penalty skipped)');
    });


    // --- Run all tests ---
    let passed = 0;
    let failed = 0;
    console.log("\n--- Running strategicAI.js Test Suite ---");
    for (const t of tests) {
        recordedDecisions = []; // Reset for each test
        // Reset mock function calls for spies if any were created in a test
        // (Example, not strictly needed if spies are re-created in `beforeEach`-like setups per test)
        if (global.coordinateTacticalGroups && global.coordinateTacticalGroups.mock) global.coordinateTacticalGroups.mock.calls = [];
        if (global.repositionForDefense && global.repositionForDefense.mock) global.repositionForDefense.mock.calls = [];


        console.log(`--- Starting test: ${t.description} ---`);
        try {
            await t.fn(); // Assuming tests might be async
            console.log(`--- Test PASSED: ${t.description} ---`);
            passed++;
        } catch (e) {
            console.error(`--- Test FAILED: ${t.description} ---`);
            console.error(e.message);
            console.error(e.stack);
            failed++;
        }
    }
    console.log(`\nStrategicAI.js Tests Finished. Passed: ${passed}, Failed: ${failed}\n`);
    if (failed > 0) {
        // Instead of throwing an error that stops the script in some environments,
        // log a clear failure message. The calling environment can check console output.
        console.error(`STRATEGIC AI TESTS: ${failed} tests failed overall.`);
        // Optionally, if a global error flag is useful for CI:
        // process.exitCode = 1; // Requires Node.js environment
    }
}

// It's assumed that the functions like coordinateTacticalGroups, repositionForDefense, etc.,
// are made available in the scope of this test file, either by direct copy,
// or if this file were an ES module, through imports from strategicAI.js.
// For this environment, we'll rely on the test runner's existing pattern of copying functions.
// The following are placeholders for where these copied functions would be if not already present.
// function coordinateTacticalGroups(gameContext, team, teamUnits, ai) { /* ... copied ... */ }
// function repositionForDefense(units, gameContext, team) { /* ... copied ... */ }
// function calculateGroupCenter(units) { /* ... copied ... */ }
// function calculateGroupStrength(units) { /* ... copied ... */ }
// function determineGroupRole(units, leader) { /* ... copied ... */ }
// function assignGroupTarget(group, target, leader) { /* ... copied ... */ }

// Trigger test run
// Ensure UNIT_TYPES has some defaults for tests if not fully mocked for each test
UNIT_TYPES.tank = UNIT_TYPES.tank || { name: 'Tank', tier: 1, hp:100, damage:10};
UNIT_TYPES.scout = UNIT_TYPES.scout || { name: 'Scout', tier: 1, hp:50, damage:5};
UNIT_TYPES.commander = UNIT_TYPES.commander || { name: 'Commander', tier: 3, hp: 1000, damage: 50};


// Adding new tests for AI behavior
test('coordinateTacticalGroups: should select high-authority unit as leader and assign target', () => {
    const friendlyTeam = 'blue';
    const highAuthUnit = new MockUnit('leader1', friendlyTeam, UNIT_TYPES.commander, 0, 0, 200, 200, 50);
    const lowAuthUnit1 = new MockUnit('sub1', friendlyTeam, UNIT_TYPES.tank, 10, 10, 100, 100, 10);
    const lowAuthUnit2 = new MockUnit('sub2', friendlyTeam, UNIT_TYPES.tank, -10, -10, 100, 100, 10);
    const lowAuthUnit3 = new MockUnit('sub3', friendlyTeam, UNIT_TYPES.scout, 5, 5, 50, 50, 5);
    const friendlyUnits = [highAuthUnit, lowAuthUnit1, lowAuthUnit2, lowAuthUnit3];

    const enemyTarget = new MockUnit('enemy1', 'red', UNIT_TYPES.tank, 100, 100, 100, 100, 10);
    const mockCtx = getMockGameContext(friendlyUnits, [enemyTarget]);
    const mockAIState = getMockAiStateTeam();

    // The actual coordinateTacticalGroups function is assumed to be in scope
    coordinateTacticalGroups(mockCtx, friendlyTeam, friendlyUnits, mockAIState);

    let leaderLedAttack = false;
    mockCtx.entityManager.addCaption.mock.calls.forEach(callArgs => {
        const captionObj = callArgs[0]; // Caption object itself
        if (captionObj && captionObj.text && captionObj.text.includes(`(L: ${highAuthUnit.type.name.substring(0,3)})`)) {
            leaderLedAttack = true;
        }
    });
    assert(leaderLedAttack, 'High-authority unit should lead a group attack (indicated by caption).');
    assert(highAuthUnit.target === enemyTarget, "Leader unit should target the enemy.");
    // Check if subordinates also target the same enemy
    assert(lowAuthUnit1.target === enemyTarget, "Subordinate unit 1 should target the enemy.");
    assert(lowAuthUnit2.target === enemyTarget, "Subordinate unit 2 should target the enemy.");
});

test('repositionForDefense: should move units to defend high-authority (commander) unit', () => {
    const friendlyTeam = 'blue';
    const commander = new MockUnit('cmd1', friendlyTeam, UNIT_TYPES.commander, 50, 50, 100, 200, 50); // Damaged commander
    const defender1 = new MockUnit('def1', friendlyTeam, UNIT_TYPES.tank, 0, 0, 100, 100, 15);
    const defender2 = new MockUnit('def2', friendlyTeam, UNIT_TYPES.tank, 10, 0, 100, 100, 15);
    const friendlyUnits = [commander, defender1, defender2];
    const mockCtx = getMockGameContext(friendlyUnits, []);

    repositionForDefense(friendlyUnits, mockCtx, friendlyTeam);

    assert(commander.patrolTarget !== null, 'Commander (high-authority) should receive a defensive patrol order.');
    assert(defender1.patrolTarget !== null, 'Defender 1 should be assigned a patrol target.');
    assert(defender2.patrolTarget !== null, 'Defender 2 should be assigned a patrol target.');

    // Check that defenders are patrolling near the commander's new defensive position
    if (defender1.patrolTarget && commander.patrolTarget) {
        const distDef1ToCmd = defender1.getDistance(commander.patrolTarget);
        assert(distDef1ToCmd < 150, `Defender 1 patrols near commander (dist: ${distDef1ToCmd.toFixed(0)}).`);
    }
    if (defender2.patrolTarget && commander.patrolTarget) {
        const distDef2ToCmd = defender2.getDistance(commander.patrolTarget);
        assert(distDef2ToCmd < 150, `Defender 2 patrols near commander (dist: ${distDef2ToCmd.toFixed(0)}).`);
    }
});


runTests().catch(e => {
    console.error("Critical error running strategicAI.js tests:", e.message);
});

// Placeholder for trade logic tests from previous subtasks (if any)
// test('executeTrade: successful BUY mass', () => { /* ... */ });
// test('evaluateTradeOpportunities: sell mass condition met', () => { /* ... */ });
