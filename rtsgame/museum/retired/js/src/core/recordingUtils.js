"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.generateRandomSeed = generateRandomSeed;
exports.initializeRecordingSystem = initializeRecordingSystem;
exports.startRandomSeedRecording = startRandomSeedRecording;
exports.startRecordingWithSeed = startRecordingWithSeed;
exports.getCurrentRecording = getCurrentRecording;
exports.logSimulationEvent = logSimulationEvent;
exports.logErrorEvent = logErrorEvent;
// js/core/recordingUtils.js
const battleJournal_js_1 = __importDefault(require("../ai/battleJournal.js"));
const simulationConfig_js_1 = require("../config/simulationConfig.js");
function generateRandomSeed() {
    return Math.floor(Math.random() * 1000000);
}
// Initialize recording utilities
function initializeRecordingSystem() {
    // Set up full simulation journaling from config
    if (simulationConfig_js_1.SIMULATION_CONFIG.JOURNALING_MODE === 'FULL') {
        console.log('Initializing full simulation journaling');
        battleJournal_js_1.default.startRecording(simulationConfig_js_1.SIMULATION_CONFIG.GAME_SEED || generateRandomSeed(), simulationConfig_js_1.SIMULATION_CONFIG.JOURNALING_TIMEOUT_SECONDS || simulationConfig_js_1.SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS, true // journalAllEvents
        );
    }
    else {
        console.log('Initializing AI decision journaling only');
        battleJournal_js_1.default.startRecording(simulationConfig_js_1.SIMULATION_CONFIG.GAME_SEED || generateRandomSeed(), simulationConfig_js_1.SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS, false // journalAllEvents
        );
    }
}
// Start a 10-second recording with a random seed
function startRandomSeedRecording(gameContext, durationSeconds = simulationConfig_js_1.SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS) {
    const randomSeed = generateRandomSeed();
    const configDuration = durationSeconds || simulationConfig_js_1.SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS;
    // Set up journaling based on config
    battleJournal_js_1.default.startRecording(randomSeed, configDuration, simulationConfig_js_1.SIMULATION_CONFIG.JOURNALING_MODE === 'FULL');
    gameContext.GAME_SEED = randomSeed;
    gameContext.RECORD_AI_DECISIONS = true;
    gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = durationSeconds;
    console.log(`Starting ${durationSeconds} second recording with random seed: ${randomSeed}`);
    return randomSeed;
}
// Start a recording with a specific seed
function startRecordingWithSeed(gameContext, seed, durationSeconds = 10) {
    gameContext.GAME_SEED = seed;
    gameContext.RECORD_AI_DECISIONS = true;
    gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = durationSeconds;
    console.log(`Starting recording with seed: ${seed} for ${durationSeconds} seconds`);
    return seed;
}
// Get and stop the current recording
function getCurrentRecording(gameContext) {
    try {
        const recording = battleJournal_js_1.default.stopRecording();
        if (!recording) {
            console.warn('No recording found, starting one now');
            gameContext.RECORD_AI_DECISIONS = true;
            gameContext.GAME_SEED = generateRandomSeed();
            return battleJournal_js_1.default.startRecording(gameContext.GAME_SEED, simulationConfig_js_1.SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS, simulationConfig_js_1.SIMULATION_CONFIG.JOURNALING_MODE === 'FULL');
        }
        return recording;
    }
    catch (err) {
        console.error('Error getting recording:', err);
        return null;
    }
}
// Log a simulation event to the journal
function logSimulationEvent(eventType, message, details = {}) {
    if (simulationConfig_js_1.SIMULATION_CONFIG.LOG_SIMULATION_EVENTS) {
        console.log(`[${new Date().toISOString()}] [${eventType}] ${message}`, details);
    }
    try {
        battleJournal_js_1.default.recordEvent(eventType, message, Date.now(), details);
    }
    catch (err) {
        console.error('Error logging simulation event:', err);
    }
}
// Log an error event to the journal
function logErrorEvent(error, context = {}) {
    if (simulationConfig_js_1.SIMULATION_CONFIG.LOG_EXCEPTIONS) {
        console.error('Simulation Error:', error, context);
    }
    try {
        battleJournal_js_1.default.logException(error, context);
    }
    catch (err) {
        console.error('Error logging error event:', err);
    }
}
