// js/core/game.js
// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This entire file is deprecated and scheduled for deletion.                    #
// #   Core simulation logic has been moved to js_rewritten/core/simulation.js.                       #
// #   UI, rendering, and main loop control should be handled by their respective modules.            #
// #                                                                                                  #
// ####################################################################################################

import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES, MIN_LAND_PERCENTAGE, MAX_TERRAIN_RETRIES } from '../config/gameConstants.js';
import { SIMULATION_CONFIG } from '../config/simulationConfig.js';
import { Unit } from './unit.js';
import { Building } from './building.js';
import { Effect } from './effect.js';
import { Caption } from './caption.js';
import { GrenadeProjectile } from './projectile.js';
import { findLandPosition, findWaterPosition } from './terrain.js';
import { generateTerrain } from './terrainManager.js';
import { makeStrategicDecisions, coordinateAttacks } from '../ai/strategicAI.js';
// import { render } from '../rendering/renderer.js'; // Old 2D renderer
import battleJournal from '../ai/battleJournal.js';

export function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function addEvent(gameContext, type, message, importance = 1) {
    const event = {
        type: type,
        message: message,
        time: gameContext.gameState.gameTime,
        importance: importance
    };

    gameContext.gameState.events.unshift(event);
    if (gameContext.gameState.events.length > 10) {
        gameContext.gameState.events.pop();
    }

    const eventLog = document.getElementById('events');
    // Only update DOM if not in HEADLESS_MODE
    if (eventLog && !gameContext.HEADLESS_MODE) {
        const eventDiv = document.createElement('div');
        eventDiv.className = `event event-${type}`;
        eventDiv.textContent = `[${formatTime(gameContext.gameState.gameTime)}] ${message}`;
        eventLog.insertBefore(eventDiv, eventLog.firstChild);

        if (eventLog.children.length > 10) {
            eventLog.removeChild(eventLog.lastChild);
        }
    } else if (!eventLog && !gameContext.HEADLESS_MODE) {
        console.warn("'events' DOM element not found. Skipping event log update.");
    }

    if (importance >= 2 && gameContext.camera.autoCamera && event.position) {
        gameContext.camera.cameraTarget = event.position;
        gameContext.camera.cameraTimer = 180; // 3 seconds
    }

    // NEW: Record game events in the battle journal
    if (gameContext.battleJournal && gameContext.RECORD_AI_DECISIONS) { // Only record if recording is enabled
        gameContext.battleJournal.recordEvent(type, message, gameContext.gameState.gameTime);
    }

    return event;
}

function placeFactoriesAroundCommander(gameContext, commanderPos, team) {
    const offset = 150;
    const factoryAreaSize = 3;
    let landPos, airPos, navalPos, energyPos1;

    landPos = findLandPosition(gameContext, commanderPos.x + offset, commanderPos.y, factoryAreaSize);
    if (!landPos) landPos = findLandPosition(gameContext, commanderPos.x - offset, commanderPos.y, factoryAreaSize);
    if (!landPos) {
        console.warn(`Could not find ideal spot for Land Factory for ${team}, placing at fallback.`);
        landPos = { x: commanderPos.x + offset, y: commanderPos.y };
    }
    gameContext.buildings.push(new Building(landPos.x, landPos.y, team, BUILDING_TYPES.landFactory, gameContext));

    airPos = findLandPosition(gameContext, commanderPos.x - offset, commanderPos.y - offset, factoryAreaSize);
    if (!airPos) airPos = findLandPosition(gameContext, commanderPos.x + offset, commanderPos.y + offset, factoryAreaSize);
    if (!airPos) {
        console.warn(`Could not find ideal spot for Air Factory for ${team}, placing at fallback.`);
        airPos = { x: commanderPos.x - offset, y: commanderPos.y - offset };
    }
    gameContext.buildings.push(new Building(airPos.x, airPos.y, team, BUILDING_TYPES.airFactory, gameContext));

    navalPos = findWaterPosition(gameContext, commanderPos.x, commanderPos.y + offset * 1.5);
    if (navalPos && navalPos.x !== undefined && navalPos.y !== undefined) {
         const distToCommander = Math.sqrt((navalPos.x - commanderPos.x) ** 2 + (navalPos.y - commanderPos.y) ** 2);
         if (distToCommander < 700) {
            gameContext.buildings.push(new Building(navalPos.x, navalPos.y, team, BUILDING_TYPES.navalFactory, gameContext));
         } else {
            console.log(`Naval factory position for ${team} was too far from commander (${distToCommander.toFixed(0)} units), skipping.`);
         }
    } else {
        console.log(`No suitable water position found for Naval factory for ${team}.`);
    }

    energyPos1 = findLandPosition(gameContext, commanderPos.x, commanderPos.y - offset, factoryAreaSize);
    if (!energyPos1) {
        console.warn(`Could not find ideal spot for Energy Plant for ${team}, placing at fallback.`);
        energyPos1 = { x: commanderPos.x, y: commanderPos.y - offset};
    }
    gameContext.buildings.push(new Building(energyPos1.x, energyPos1.y, team, BUILDING_TYPES.energyExtractor, gameContext));
}

export function initGame(gameContext) {
    // NEW: Initialize seeded random number generator
    gameContext.seedRandom.init(gameContext.GAME_SEED);
    console.log(`Game initialized with seed: ${gameContext.GAME_SEED}`);

    // NEW: Start recording if enabled
    if (gameContext.RECORD_AI_DECISIONS) {
        gameContext.battleJournal.startRecording(gameContext.GAME_SEED, gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS);
        console.log(`Battle Journal recording started with seed: ${gameContext.GAME_SEED} for ${gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS} seconds.`);
    }

    gameContext.units.length = 0;
    gameContext.buildings.length = 0;
    gameContext.effects.length = 0;
    gameContext.captions.length = 0;
    gameContext.projectiles.length = 0;
    gameContext.gameState.winner = null;
    gameContext.gameState.gameTime = 0;
    gameContext.gameState.events = [];
    gameContext.selectionManager.clearSelection(); // Clear selection on game init

    gameContext.resources.blue = {
        mass: SIMULATION_CONFIG.INITIAL_BLUE_MASS,
        energy: SIMULATION_CONFIG.INITIAL_BLUE_ENERGY,
        massIncome: 0,
        energyIncome: 0
    };
    gameContext.resources.red = {
        mass: SIMULATION_CONFIG.INITIAL_RED_MASS,
        energy: SIMULATION_CONFIG.INITIAL_RED_ENERGY,
        massIncome: 0,
        energyIncome: 0
    };

    let blueStart = null;
    let redStart = null;
    const MAX_INIT_RETRIES = 10;
    const FOCUSED_PLACEMENT_RETRIES = 5;
    const COMMANDER_START_AREA_SIZE = 5;

    console.log("Initializing game, attempting to generate terrain and find starting positions...");
    
    function printTerrainMap(gameContext) {
        if (gameContext.HEADLESS_MODE) return; // Skip printing in headless mode
        console.log("=== TERRAIN MAP (L=Land, W=Water, M=Mountain) ===");
        let mapStr = "";
        for (let y = 0; y < GRID_SIZE; y += 2) { // Show every 2nd row for readability
            let row = "";
            for (let x = 0; x < GRID_SIZE; x += 2) { // Show every 2nd column
                if (gameContext.terrain[x] && gameContext.terrain[x][y] !== undefined) {
                    const terrain = gameContext.terrain[x][y];
                    if (terrain === TERRAIN_TYPES.LAND) row += "L";
                    else if (terrain === TERRAIN_TYPES.WATER) row += "W";
                    else if (terrain === TERRAIN_TYPES.MOUNTAIN) row += "M";
                    else row += "?";
                } else {
                    row += "?";
                }
            }
            console.log(row);
        }
        console.log("=== END TERRAIN MAP ===");
    }

    for (let i = 0; i < MAX_INIT_RETRIES; i++) {
        console.log(`Attempt ${i + 1}/${MAX_INIT_RETRIES} to generate terrain and find start spots for both teams.`);
        generateTerrain(gameContext, 'perlinNoiseGenerator'); // Specify the terrain generator
        blueStart = findLandPosition(gameContext, WORLD_SIZE * 0.2, WORLD_SIZE * 0.5, COMMANDER_START_AREA_SIZE);
        redStart = findLandPosition(gameContext, WORLD_SIZE * 0.8, WORLD_SIZE * 0.5, COMMANDER_START_AREA_SIZE);

        if (blueStart && redStart) {
            console.log(`Successfully found starting positions for both teams after ${i + 1} attempt(s).`);
            printTerrainMap(gameContext);
            console.log(`Blue spawn: (${blueStart.x}, ${blueStart.y}), Red spawn: (${redStart.x}, ${redStart.y})`);
            break;
        } else {
            console.warn(`Could not find suitable ${COMMANDER_START_AREA_SIZE}x${COMMANDER_START_AREA_SIZE} starting areas in attempt ${i + 1}. Blue found: ${!!blueStart}, Red found: ${!!redStart}. Regenerating terrain...`);
        }
    }

    if (!blueStart) {
        console.log("Initial attempts failed for BLUE commander. Starting focused retries for BLUE...");
        for (let i = 0; i < FOCUSED_PLACEMENT_RETRIES; i++) {
            console.log(`Focused attempt ${i + 1}/${FOCUSED_PLACEMENT_RETRIES} for BLUE commander placement.`);
            generateTerrain(gameContext, 'perlinNoiseGenerator');
            blueStart = findLandPosition(gameContext, WORLD_SIZE * 0.2, WORLD_SIZE * 0.5, COMMANDER_START_AREA_SIZE);
            if (blueStart) {
                console.log(`Successfully found starting position for BLUE team after focused attempt ${i + 1}.`);
                break;
            }
            console.log(`Retrying terrain generation for BLUE commander placement (attempt ${i + 1}/${FOCUSED_PLACEMENT_RETRIES} failed)...`);
        }
    }

    if (!redStart) {
        console.log("Initial attempts failed for RED commander (or Blue was found but Red wasn't). Starting focused retries for RED...");
        for (let i = 0; i < FOCUSED_PLACEMENT_RETRIES; i++) {
            console.log(`Focused attempt ${i + 1}/${FOCUSED_PLACEMENT_RETRIES} for RED commander placement.`);
            generateTerrain(gameContext, 'perlinNoiseGenerator');
            if (blueStart && !findLandPosition(gameContext, blueStart.x, blueStart.y, COMMANDER_START_AREA_SIZE)) {
                 console.warn("Previously found BLUE start position is no longer valid after terrain regeneration for RED. Blue may need to use fallback.");
            }
            redStart = findLandPosition(gameContext, WORLD_SIZE * 0.8, WORLD_SIZE * 0.5, COMMANDER_START_AREA_SIZE);
            if (redStart) {
                console.log(`Successfully found starting position for RED team after focused attempt ${i + 1}.`);
                break;
            }
            console.log(`Retrying terrain generation for RED commander placement (attempt ${i + 1}/${FOCUSED_PLACEMENT_RETRIES} failed)...`);
        }
    }

    if (!blueStart) {
        console.error(`CRITICAL: Failed to find suitable starting position for BLUE team after all attempts. Placing at default fallback.`);
        blueStart = { x: WORLD_SIZE * 0.2, y: WORLD_SIZE * 0.5 };
    }
    if (!redStart) {
        console.error(`CRITICAL: Failed to find suitable starting position for RED team after all attempts. Placing at default fallback.`);
        redStart = { x: WORLD_SIZE * 0.8, y: WORLD_SIZE * 0.5 };
    }

    if (UNIT_TYPES.commander) {
        gameContext.units.push(new Unit(blueStart.x, blueStart.y, 'blue', UNIT_TYPES.commander, gameContext)); // Pass gameContext to Unit constructor
        gameContext.units.push(new Unit(redStart.x, redStart.y, 'red', UNIT_TYPES.commander, gameContext)); // Pass gameContext to Unit constructor
        addEvent(gameContext, 'strategic', 'Commanders deployed!', 3);
    } else {
        console.error("UNIT_TYPES.commander is not defined! Cannot spawn ACUs.");
    }

    gameContext.camera.x = WORLD_SIZE / 2;
    gameContext.camera.y = WORLD_SIZE / 2;
    gameContext.camera.zoom = 0.5;
// Add demo recording functions to gameContext
gameContext.selectDemoRecording = selectDemoRecording;
gameContext.playDemo = playDemo;
    addEvent(gameContext, 'strategic', 'Battle commenced!', 3);
}
 
export function update(gameContext) {
    if (gameContext.gameState.paused || gameContext.gameState.winner) return;

    // Calculate delta time for frame-rate independent updates
    const now = performance.now();
    if (!gameContext.lastUpdateTime) {
        gameContext.lastUpdateTime = now;
    }
    const deltaTime = (now - gameContext.lastUpdateTime) / 1000; // in seconds
    gameContext.lastUpdateTime = now;

    gameContext.gameState.gameTime += deltaTime; // Advance game time based on actual elapsed time

    // NEW: Stop recording if duration reached
    if (gameContext.RECORD_AI_DECISIONS && gameContext.gameState.gameTime >= gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS) {
        const recording = gameContext.battleJournal.stopRecording();
        if (recording) {
            console.log("Recording complete. Sending to Node.js server...");
            fetch('http://localhost:3000/recordings', { // Assuming Node.js server runs on port 3000
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(recording),
            })
            .then(response => {
                if (response.ok) {
                    console.log('Recording successfully sent to server.');
                } else {
                    console.error('Failed to send recording to server:', response.status, response.statusText);
                }
            })
            .catch(error => {
                console.error('Error sending recording to server:', error);
            });
        }
        // Set winner to end game loop in main.js
        gameContext.gameState.winner = "RECORDING_COMPLETE";
        return; // Stop further updates in this frame
    }

    // Get the currently selected unit from the selection manager
    const currentSelectedUnit = gameContext.selectionManager.getSelected();

    if (gameContext.camera.autoCamera && gameContext.camera.cameraTarget && gameContext.camera.cameraTimer > 0) {
        const dx = gameContext.camera.cameraTarget.x - gameContext.camera.x;
        const dy = gameContext.camera.cameraTarget.y - gameContext.camera.y;
        gameContext.camera.x += dx * 0.1;
        gameContext.camera.y += dy * 0.1;
        gameContext.camera.cameraTimer--;
        if (gameContext.camera.cameraTimer <= 0) {
            gameContext.camera.cameraTarget = null;
        }
    }

    if (gameContext.camera.autoCamera && !gameContext.camera.cameraTarget && gameContext.seedRandom.random() < 0.005) { // Using seeded random
        // Add null checks for units and buildings arrays
        const units = gameContext.units || [];
        const buildings = gameContext.buildings || [];
        const targets = [...units.filter(u => u.target), ...buildings];
        if (targets.length > 0) {
            const target = targets[Math.floor(gameContext.seedRandom.random() * targets.length)]; // Using seeded random
            gameContext.camera.cameraTarget = { x: target.x, y: target.y };
            gameContext.camera.cameraTimer = 180;
        }
    }

    for (let i = gameContext.units.length - 1; i >= 0; i--) {
        const unit = gameContext.units[i];
        unit.update(gameContext, deltaTime);
        if (unit.hp <= 0) {
            if (unit.type === UNIT_TYPES.commander) {
                gameContext.gameState.winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                gameContext.captions.push(new Caption(unit.x, unit.y, `${unit.team.toUpperCase()} COMMANDER DESTROYED!`, '#ff0', 20));
                const event = addEvent(gameContext, 'strategic', `${gameContext.gameState.winner} achieves victory! ${unit.team.toUpperCase()} ACU eliminated.`, 3);
                event.position = { x: unit.x, y: unit.y };
            } else if (unit.type.tier >= 2) {
                const event = addEvent(gameContext, 'battle', `${unit.team.toUpperCase()} ${unit.type.name} destroyed!`, 2);
                event.position = { x: unit.x, y: unit.y };
            }
            // If the destroyed unit was selected, clear the selection
            if (unit === currentSelectedUnit) {
                gameContext.selectionManager.clearSelection(); // Use selection manager
                gameContext.gameState.fpvMode = false; // Also exit FPV if the selected unit is destroyed
            }
            gameContext.units.splice(i, 1);
        }
    }

    for (let i = gameContext.buildings.length - 1; i >= 0; i--) {
        const building = gameContext.buildings[i];
        // Pass the entire gameContext to building.update to access all global components
        // (including the selectionManager if a building ever needs to interact with selection)
        building.update(gameContext, deltaTime);
        if (building.hp <= 0) {
            if (building.type.produces || building.type.resourceGeneration) {
                gameContext.captions.push(new Caption(building.x, building.y, 'Structure lost!', '#f88', 12));
                const event = addEvent(gameContext, 'battle', `${building.team.toUpperCase()} ${building.type.name} destroyed!`, 2);
                event.position = { x: building.x, y: building.y };
            }
            // If the destroyed building was selected, clear the selection
            if (building === currentSelectedUnit) { // Note: currentSelectedUnit could be a building now
                gameContext.selectionManager.clearSelection();
            }
            gameContext.buildings.splice(i, 1);
        }
    }

    for (let i = gameContext.effects.length - 1; i >= 0; i--) {
        const effect = gameContext.effects[i];
        effect.update(deltaTime);
        if (effect.life <= 0 && effect.particles.every(p => p.life <= 0)) {
            gameContext.effects.splice(i, 1);
        }
    }

    for (let i = gameContext.captions.length - 1; i >= 0; i--) {
        const caption = gameContext.captions[i];
        caption.update(deltaTime);
        if (caption.life <= 0) {
            gameContext.captions.splice(i, 1);
        }
    }

    // Update projectiles
    if (gameContext.projectiles) {
        for (let i = gameContext.projectiles.length - 1; i >= 0; i--) {
            const projectile = gameContext.projectiles[i];
            projectile.update(gameContext, deltaTime);
            if (projectile.isExpired) {
                gameContext.projectiles.splice(i, 1);
            }
        }
    }

    makeStrategicDecisions(gameContext); // This function will need to use seedRandom.random()
    coordinateAttacks(gameContext); // This function will need to use seedRandom.random()

    // Update introspection windows
    if (gameContext.introspectionManager) {
        gameContext.introspectionManager.update(gameContext);
    }

    // Update command status renderers
    if (gameContext.commandStatusRenderers) {
        gameContext.commandStatusRenderers.forEach(renderer => {
            renderer.update();
        });
    }

    // FPV mode: Use the selected entity from selection manager, ensure it's a unit
    if (gameContext.gameState.fpvMode && currentSelectedUnit && currentSelectedUnit.type && currentSelectedUnit.type.speed !== undefined) {
        gameContext.camera.x = currentSelectedUnit.x;
        gameContext.camera.y = currentSelectedUnit.y;
        gameContext.camera.zoom = 10;
    }
}

// Define demo recordings with specific strategic events
const demoRecordings = [
    // Recording 1: Basic attack on red commander
    {
        name: "Demo 1: Commander Attack",
        description: "Basic recording showing how to attack an enemy commander",
        duration: 10,
        actions: [
            { time: 2, type: "strategic", message: "Blue commander spots red commander and initiates attack", importance: 3 },
            { time: 4, type: "battle", message: "Blue commander fires on red commander", importance: 2, position: { x: 400, y: 300 } },
            { time: 6, type: "strategic", message: "Blue commander retreats slightly to avoid counter-attack", importance: 2 },
            { time: 8, type: "battle", message: "Red commander fires back at blue commander", importance: 2, position: { x: 410, y: 310 } },
            { time: 9, type: "strategic", message: "Blue commander returns fire", importance: 2 }
        ]
    },
    
    // Recording 2: Unit support coordination
    {
        name: "Demo 2: Unit Support",
        description: "Shows how units can provide support during battle",
        duration: 10,
        actions: [
            { time: 1, type: "strategic", message: "Blue tier 1 units advancing towards red commander", importance: 2 },
            { time: 3, type: "battle", message: "Red commander fires on approaching blue units", importance: 2, position: { x: 420, y: 320 } },
            { time: 5, type: "strategic", message: "Blue tier 2 units move in to support", importance: 2 },
            { time: 7, type: "battle", message: "Blue tier 1 units destroy red tier 1 units", importance: 2, position: { x: 400, y: 300 } },
            { time: 9, type: "strategic", message: "Blue commander finishes off weakened red commander", importance: 2 }
        ]
    },
];

// Function to play a demo recording
function playDemo(gameContext, recordingIndex) {
    if (!gameContext.demoRecordings || !gameContext.demoRecordings.length) {
        gameContext.demoRecordings = [...demoRecordings];
    }
    
    const recording = gameContext.demoRecordings[recordingIndex % gameContext.demoRecordings.length];
    gameContext.RECORD_AI_DECISIONS = true;
    gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = recording.duration;
    gameContext.currentDemoIndex = recordingIndex % gameContext.demoRecordings.length;
    
    // Start the recording
    initGame(gameContext);
    gameContext.battleJournal.startRecording(gameContext.GAME_SEED, recording.duration);
    
    // Log the start of the demo
    console.log(`Starting demo recording: ${recording.name}`);
    addEvent(gameContext, 'info', `Starting ${recording.name}: ${recording.description}`, 1);
    
    // Play the recording events
    recording.actions.forEach(action => {
        // Schedule the action to occur at the specified time
        const timeout = setTimeout(() => {
            addEvent(gameContext, action.type, action.message, action.importance, action.position);
        }, action.time * 1000);
        
        // Store the timeout ID to clear it if the recording ends early
        gameContext.demoTimers = gameContext.demoTimers || [];
        gameContext.demoTimers.push(timeout);
    });
    
    return recording.name;
}

// Add demo selection function to the game context
function selectDemoRecording(gameContext, recordingIndex) {
    if (!gameContext.demoRecordings || !gameContext.demoRecordings.length) {
        gameContext.demoRecordings = [...demoRecordings];
    }
    
    const recording = gameContext.demoRecordings[recordingIndex % gameContext.demoRecordings.length];
    gameContext.RECORD_AI_DECISIONS = true;
    gameContext.RECORD_AI_DECISIONS_DURATION_SECONDS = recording.duration;
    gameContext.currentDemoIndex = recordingIndex % gameContext.demoRecordings.length;
    
    // Log the selected demo
    console.log(`Selected demo recording: ${recording.name}`);
    if (gameContext.RECORD_AI_DECISIONS && gameContext.battleJournal) {
        gameContext.battleJournal.recordEvent('info', `Demo recording selected: ${recording.name}`, gameContext.gameState.gameTime);
    }
    
    // Add the selection event to the journal
    addEvent(gameContext, 'info', `Selected demo recording: ${recording.name}`, 2);
    
    return recording.name;
}


export function gameLoop(timestamp, gameContext) {
    if (!gameContext.lastFpsTime) gameContext.lastFpsTime = 0;
    if (!gameContext.frameCount) gameContext.frameCount = 0;

    gameContext.frameCount++;
    if (timestamp - gameContext.lastFpsTime >= 1000) {
        const fpsDisplay = document.getElementById('fps');
        // Only update DOM if not in HEADLESS_MODE
        if (fpsDisplay && !gameContext.HEADLESS_MODE) {
            fpsDisplay.textContent = gameContext.frameCount;
        }
        gameContext.frameCount = 0;
        gameContext.lastFpsTime = timestamp;
    }

    // NEW: Check if game should terminate due to recording duration or winner
    if (gameContext.gameState.winner === "RECORDING_COMPLETE") {
        // Ensure all journal entries are properly saved
        if (gameContext.battleJournal && gameContext.battleJournal.isRecording) {
            gameContext.battleJournal.stopRecording();
        }
        return false; // Signal to main loop to stop
    }
    if (gameContext.gameState.winner && gameContext.gameState.winner !== "RECORDING_COMPLETE") {
        return false; // Signal to main loop to stop
    }


    update(gameContext);

    // Only render if not in headless mode
    if (!gameContext.HEADLESS_MODE) {
        try {
            // Update the WebGL camera with the latest game camera data
            gameContext.renderer.updateCamera(gameContext.camera);
            // Call the WebGL render method
            gameContext.renderer.render(gameContext);
        } catch (e) {
            console.error("Error during WebGL rendering:", e);
            // Optionally, set winner or pause game for critical rendering errors
            // gameContext.gameState.winner = "RENDER_ERROR";
        }
    } else {
        // console.log(`Headless update at gameTime: ${gameContext.gameState.gameTime.toFixed(2)}`); // Optional: log progress
    }
    
    return true; // Signal to continue loop
}

export function performAoeDamage(centerX, centerY, radius, damageAmount, ownerTeam, gameContext) {
    const { units, addEvent } = gameContext; // Assuming addEvent is for logging hits, optional
    let unitsHitCount = 0;

    if (!units) {
        console.error("gameContext.units not available in performAoeDamage");
        return;
    }

    units.forEach(unit => {
        const dx = unit.x - centerX;
        const dy = unit.y - centerY;
        const distanceSquared = dx * dx + dy * dy; // Use squared distance for efficiency
        const radiusSquared = radius * radius;

        if (distanceSquared < radiusSquared) {
            // Unit is within AOE radius
            // TODO: Implement team check / friendly fire rules if necessary based on ownerTeam
            unitsHitCount++;
            let remainingDamage = damageAmount;

            // Apply damage to shields first
            if (unit.shields !== undefined && unit.shields > 0) {
                const shieldDamage = Math.min(remainingDamage, unit.shields);
                unit.shields -= shieldDamage;
                remainingDamage -= shieldDamage;
                // console.log(`Unit ${unit.type.name} took ${shieldDamage} shield damage. Shields left: ${unit.shields}`);
            }

            // Apply remaining damage to HP
            if (remainingDamage > 0 && unit.hp > 0) {
                // console.log(`Applying ${remainingDamage} HP damage to ${unit.type.name}`);
                unit.takeDamage(remainingDamage, gameContext); // unit.takeDamage should handle HP reduction
            }
        }
    });

    if (unitsHitCount > 0 && addEvent) {
        // Optional: Add a generic event for AOE hit, maybe less spammy than per-unit
        // addEvent(gameContext, 'aoe_hit', `AOE hit ${unitsHitCount} units.`, 1, { x: centerX, y: centerY });
    }
}
