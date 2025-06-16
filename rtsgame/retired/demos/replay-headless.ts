import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { Unit } from '../core/unit.js';
import { Building } from '../core/building.js';
import fs from 'fs';
import path from 'path';

async function replayHeadlessSimulation(battleId) {
    console.log(`\n🚀 Starting Headless Replay for Battle ID: ${battleId}`);
    const recordingsDir = path.join(process.cwd(), 'recordings');
    const filename = path.join(recordingsDir, `${battleId}.json`);

    if (!fs.existsSync(filename)) {
        console.error(`❌ Recording file not found: ${filename}`);
        return;
    }

    const battleData = JSON.parse(fs.readFileSync(filename, 'utf8'));
    console.log(`Loaded recording with ${battleData.frames.length} frames and ${battleData.events.length} events.`);

    const startReplay = performance.now();

    // Reconstruct initial state from the first frame or config
    // For a true replay, we'd iterate through saved unit/building states.
    // For this proof-of-concept, we'll simulate the game loop with minimal data,
    // focusing on showing the cost of *processing* recorded events rather than full state reconstruction.
    // A more robust replay would need to save more detailed state per frame.

    const units = []; // In a full replay, these would be initialized from the first frame's data
    const buildings = []; // Same for buildings
    const gameState = {
        gameTime: 0,
        winner: null,
        paused: false
    };
    const resources = {
        blue: { mass: 0, energy: 0, massIncome: 0, energyIncome: 0 },
        red: { mass: 0, energy: 0, massIncome: 0, energyIncome: 0 }
    };
    const terrain = []; // Terrain needs to be consistent with recording
    const resourceNodes = [];

    // Simulate gameContext for replay; many elements are not needed for simple event replay
    const gameContext = {
        units, buildings, resources, gameState, terrain, resourceNodes,
        UNIT_TYPES, BUILDING_TYPES, WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES,
        addEvent: (ctx, type, msg, importance, pos) => {
            // In replay, we just log, not record to new journal
            // console.log(`[REPLAY EVENT] [${type}] ${msg}`);
        },
        // Minimal necessary for unit/building updates if they were part of replay logic
        mainGameGlobals: {
            resources, units, Unit, Building,
            addEvent: (type, msg) => { /* no-op during replay */ }
        }
    };

    let frameIndex = 0;
    let eventIndex = 0;

    // A simplified replay loop that just iterates through recorded frames and events
    // For a true visual replay, you'd apply state and render. Here we measure processing.
    for (let i = 0; i < battleData.frames.length; i++) {
        const frame = battleData.frames[i];
        gameState.gameTime = frame.time; // Sync game time to recorded frame time

        // Process events that occurred up to this frame
        while (eventIndex < battleData.events.length && battleData.events[eventIndex].time <= frame.time) {
            const event = battleData.events[eventIndex];
            // console.log(`[REPLAY] Event at ${event.time.toFixed(2)}s: [${event.type}] ${event.message}`);
            eventIndex++;
        }

        // Simulate some processing for each frame, e.g., iterating dummy units/buildings
        for (let j = 0; j < frame.units; j++) {
            // Placeholder for unit update logic if units were re-simulated
        }
        for (let j = 0; j < frame.buildings; j++) {
            // Placeholder for building update logic if buildings were re-simulated
        }
    }

    const endReplay = performance.now();
    const replayDuration = endReplay - startReplay;

    console.log('\n📊 REPLAY RESULTS:');
    console.log('=' .repeat(50));
    console.log(`   Replay Duration: ${replayDuration.toFixed(1)}ms`);
    console.log(`   Original Simulation Duration: ${battleData.outcome.simDuration.toFixed(1)}ms`);
    console.log(`   Difference: ${(replayDuration - battleData.outcome.simDuration).toFixed(1)}ms`);
    console.log(`   Total Frames Replayed: ${battleData.frames.length}`);
    console.log(`   Total Events Processed: ${battleData.events.length}`);

    // Simple performance assessment
    if (replayDuration <= battleData.outcome.simDuration * 1.1) { // Within 10%
        console.log('\n✅ REPLAY COST: SIMILAR TO SIMULATION COST');
    } else {
        console.log('\n⚠️  REPLAY COST: SIGNIFICANTLY HIGHER THAN SIMULATION COST');
    }

    console.log('\n🎉 Headless replay completed.');
}

// Command line execution
if (import.meta.url === `file://${process.argv[1]}`) {
    const battleIdArg = process.argv[2];
    if (!battleIdArg) {
        console.error('Usage: node replay-headless.js <battle_id>');
        console.error('Example: node replay-headless.js battle_1700000000000');
        process.exit(1);
    }
    replayHeadlessSimulation(battleIdArg)
        .then(() => process.exit(0))
        .catch(error => {
            console.error('❌ Replay failed:', error);
            process.exit(1);
        });
}