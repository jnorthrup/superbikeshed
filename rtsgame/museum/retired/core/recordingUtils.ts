// js/core/recordingUtils.js
import battleJournal from '../ai/battleJournal.js';
import { SIMULATION_CONFIG } from '../config/simulationConfig.js';
export function generateRandomSeed() {
  return Math.floor(Math.random() * 1000000);
}

// Initialize recording utilities
export function initializeRecordingSystem() {
    // Set up full simulation journaling from config
    if (SIMULATION_CONFIG.JOURNALING_MODE === 'FULL') {
        console.log('Initializing full simulation journaling');
        battleJournal.startRecording(
            SIMULATION_CONFIG.GAME_SEED || generateRandomSeed(),
            SIMULATION_CONFIG.JOURNALING_TIMEOUT_SECONDS || SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS,
            true // journalAllEvents
        );
    } else {
        console.log('Initializing AI decision journaling only');
        battleJournal.startRecording(
            SIMULATION_CONFIG.GAME_SEED || generateRandomSeed(),
            SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS,
            false // journalAllEvents
        );
    }
}

// Start a 10-second recording with a random seed
export function startRandomSeedRecording(gameContext, durationSeconds = SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS) {
    const randomSeed = generateRandomSeed();
    const configDuration = durationSeconds || SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS;
    
    // Set up journaling based on config
    battleJournal.startRecording(randomSeed, configDuration, SIMULATION_CONFIG.JOURNALING_MODE === 'FULL');
    
    gameContext.GAME_SEED = randomSeed;
    gameContext.RECORD_AI_DECISIONS = true;
    gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = durationSeconds;
    
    console.log(`Starting ${durationSeconds} second recording with random seed: ${randomSeed}`);
    return randomSeed;
}

// Start a recording with a specific seed
export function startRecordingWithSeed(gameContext, seed, durationSeconds = 10) {
  gameContext.GAME_SEED = seed;
  gameContext.RECORD_AI_DECISIONS = true;
  gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = durationSeconds;
  
  console.log(`Starting recording with seed: ${seed} for ${durationSeconds} seconds`);
  return seed;
}

// Get and stop the current recording
export function getCurrentRecording(gameContext) {
    try {
        const recording = battleJournal.stopRecording();
        if (!recording) {
            console.warn('No recording found, starting one now');
            gameContext.RECORD_AI_DECISIONS = true;
            gameContext.GAME_SEED = generateRandomSeed();
            return battleJournal.startRecording(
                gameContext.GAME_SEED,
                SIMULATION_CONFIG.SIMULATION_DURATION_SECONDS,
                SIMULATION_CONFIG.JOURNALING_MODE === 'FULL'
            );
        }
        return recording;
    } catch (err) {
        console.error('Error getting recording:', err);
        return null;
    }
}

// Log a simulation event to the journal
export function logSimulationEvent(eventType, message, details = {}) {
    if (SIMULATION_CONFIG.LOG_SIMULATION_EVENTS) {
        console.log(`[${new Date().toISOString()}] [${eventType}] ${message}`, details);
    }
    try {
        battleJournal.recordEvent(eventType, message, Date.now(), details);
    } catch (err) {
        console.error('Error logging simulation event:', err);
    }
}

// Log an error event to the journal
export function logErrorEvent(error, context = {}) {
    if (SIMULATION_CONFIG.LOG_EXCEPTIONS) {
        console.error('Simulation Error:', error, context);
    }
    try {
        battleJournal.logException(error, context);
    } catch (err) {
        console.error('Error logging error event:', err);
    }
}