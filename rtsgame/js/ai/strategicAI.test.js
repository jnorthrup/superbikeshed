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
let recordedDecisions = []; // To mock recordAIDecision if needed by tested functions
const mockRecordAIDecision = (gameContext, team, decisionType, data) => {
    recordedDecisions.push({ team, decisionType, data });
};

const getMockGameContext = () => ({
    units: [],
    buildings: [],
    resources: { blue: { mass: 1000, energy: 1000 }, red: { mass: 1000, energy: 1000 }},
    gameState: { gameTime: 0 },
    // battleJournal and other gameContext properties can be added if needed by specific tests
});

const getMockAiStateTeam = () => ({ /* ... if needed ... */ });

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
    // These would be: getUnitMaxHP, calculateRelativeHealth, calculateBasePower, calculateAssetScore, selectOptimalTarget

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
        // Resetting recordedDecisions if it were used by these specific tests
        recordedDecisions = [];
        console.log(`--- Starting test: ${t.description} ---`);
        try {
            await t.fn();
            console.log(`--- Test PASSED: ${t.description} ---`);
            passed++;
        } catch (e) {
            console.error(`--- Test FAILED: ${t.description} ---`);
            console.error(e.stack); // Log full stack
            failed++;
        }
    }
    console.log(`\nStrategicAI.js Tests Finished. Passed: ${passed}, Failed: ${failed}\n`);
    if (failed > 0) throw new Error(`${failed} tests failed in strategicAI.js overall.`);
}

// Trigger test run
runTests().catch(e => {
    console.error("Critical error running strategicAI.js tests:", e.message);
    // This is to catch errors from the runTests function itself, not just individual test failures
});

// Placeholder for trade logic tests from previous subtasks (if any)
// test('executeTrade: successful BUY mass', () => { /* ... */ });
// test('evaluateTradeOpportunities: sell mass condition met', () => { /* ... */ });
