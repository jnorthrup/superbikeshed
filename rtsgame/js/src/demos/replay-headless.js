"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const unitTypes_js_1 = require("../config/unitTypes.js");
const buildingTypes_js_1 = require("../config/buildingTypes.js");
const gameConstants_js_1 = require("../config/gameConstants.js");
const unit_js_1 = require("../core/unit.js");
const building_js_1 = require("../core/building.js");
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
function replayHeadlessSimulation(battleId) {
    return __awaiter(this, void 0, void 0, function* () {
        console.log(`\n🚀 Starting Headless Replay for Battle ID: ${battleId}`);
        const recordingsDir = path_1.default.join(process.cwd(), 'recordings');
        const filename = path_1.default.join(recordingsDir, `${battleId}.json`);
        if (!fs_1.default.existsSync(filename)) {
            console.error(`❌ Recording file not found: ${filename}`);
            return;
        }
        const battleData = JSON.parse(fs_1.default.readFileSync(filename, 'utf8'));
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
            UNIT_TYPES: unitTypes_js_1.UNIT_TYPES, BUILDING_TYPES: buildingTypes_js_1.BUILDING_TYPES, WORLD_SIZE: gameConstants_js_1.WORLD_SIZE, TILE_SIZE: gameConstants_js_1.TILE_SIZE, GRID_SIZE: gameConstants_js_1.GRID_SIZE, TERRAIN_TYPES: gameConstants_js_1.TERRAIN_TYPES,
            addEvent: (ctx, type, msg, importance, pos) => {
                // In replay, we just log, not record to new journal
                // console.log(`[REPLAY EVENT] [${type}] ${msg}`);
            },
            // Minimal necessary for unit/building updates if they were part of replay logic
            mainGameGlobals: {
                resources, units, Unit: unit_js_1.Unit, Building: building_js_1.Building,
                addEvent: (type, msg) => { }
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
        console.log('='.repeat(50));
        console.log(`   Replay Duration: ${replayDuration.toFixed(1)}ms`);
        console.log(`   Original Simulation Duration: ${battleData.outcome.simDuration.toFixed(1)}ms`);
        console.log(`   Difference: ${(replayDuration - battleData.outcome.simDuration).toFixed(1)}ms`);
        console.log(`   Total Frames Replayed: ${battleData.frames.length}`);
        console.log(`   Total Events Processed: ${battleData.events.length}`);
        // Simple performance assessment
        if (replayDuration <= battleData.outcome.simDuration * 1.1) { // Within 10%
            console.log('\n✅ REPLAY COST: SIMILAR TO SIMULATION COST');
        }
        else {
            console.log('\n⚠️  REPLAY COST: SIGNIFICANTLY HIGHER THAN SIMULATION COST');
        }
        console.log('\n🎉 Headless replay completed.');
    });
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
