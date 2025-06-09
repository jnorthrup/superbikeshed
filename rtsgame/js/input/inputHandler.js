// ####################################################################################################
// #   DEPRECATION IN PROGRESS: This InputHandler is being refactored to delegate responsibilities    #
// #   to the new InputManager (js/input/inputManager.js).                                          #
// #   Eventually, this file might be removed or become a very thin layer.                            #
// ####################################################################################################

// Imports for functions that might be called, if not accessed via gameContext:
// import { initGame, addEvent } from '../core/game.js'; // These will be on gameContext
import { WORLD_SIZE } from '../config/gameConstants.js'; // WORLD_SIZE is used in minimap click
import { WindowManager, BorderLayoutContainer, BorderRegion, TextComponent } from '../ui/borderLayout.js';
import { IntrospectionManager } from '../ui/talentRenderers.js';
import { CommandStatusRenderer } from '../ui/commandStatusRenderer.js';
import SelectionManager from '../ui/selectionManager.js'; // NEW IMPORT

export function initInputHandling(gameContext) {
    // Destructure what's needed from gameContext for convenience
    const { canvas, minimap, camera, gameState, units, buildings, initGame, addEvent, UNIT_TYPES, selectionManager, inputManager } = gameContext; // Added inputManager

    let mouseDown = false; // This state is for UI interaction like dragging within this handler, not camera panning.
    let lastMouseX = 0;
    let lastMouseY = 0;

    // Initialize window manager and introspection system
    gameContext.windowManager = new WindowManager();
    gameContext.introspectionManager = new IntrospectionManager(gameContext.windowManager);
    
    // Initialize command status renderers
    gameContext.commandStatusRenderers = new Map();
    gameContext.commandStatusRenderers.set('blue', new CommandStatusRenderer('blue', gameContext));
    gameContext.commandStatusRenderers.set('red', new CommandStatusRenderer('red', gameContext));

    // Add a flag to control window creation (e.g., disable by default)
    gameContext.allowWindowCreation = true;  // Enable window creation for UI to function properly

    // --- Canvas Event Listeners (Mouse for selection, panning) ---
    if (canvas) {
        canvas.addEventListener('mousedown', (e) => {
            // Check if window manager handled the click first
            if (gameContext.windowManager && gameContext.windowManager.handleMouseDown(e.clientX, e.clientY, e.button)) {
                e.preventDefault();
                return; // Window system consumed the click
            }

            // Get currently selected entity from selection manager
            const currentSelectedEntity = selectionManager.getSelected();

            if (gameState.aimingGrenade) {
                if (e.button === 0) { // Left click
                    const worldX = (e.clientX - camera.canvasWidth / 2) / camera.zoom + camera.x;
                    const worldY = (e.clientY - camera.canvasHeight / 2) / camera.zoom + camera.y;

                    // Use currentSelectedEntity from SelectionManager
                    if (currentSelectedEntity && typeof currentSelectedEntity.launchGrenade === 'function') {
                        currentSelectedEntity.launchGrenade(worldX, worldY, gameContext);
                    }
                    console.log(`Grenade targeted at ${worldX.toFixed(0)}, ${worldY.toFixed(0)}`);
                    gameState.aimingGrenade = false;
                } else if (e.button === 2) { // Right click to cancel
                    gameState.aimingGrenade = false;
                    console.log("Grenade aiming cancelled by Right Click.");
                }
                e.preventDefault(); // Prevent other actions like panning/selection
                e.stopPropagation(); // Stop event from bubbling further
                return; // Important to stop further processing of this click
            }

            mouseDown = true;
            lastMouseX = e.clientX;
            lastMouseY = e.clientY;

            // Check minimap click first
            if (minimap && gameContext.minimapCtx) { // Ensure minimap and its context exist
                const rect = minimap.getBoundingClientRect();
                if (e.clientX >= rect.left && e.clientX <= rect.right &&
                    e.clientY >= rect.top && e.clientY <= rect.bottom) {

                    // WORLD_SIZE is imported directly into this module
                    const mx = (e.clientX - rect.left) / minimap.width; // Use minimap.width
                    const my = (e.clientY - rect.top) / minimap.height; // Use minimap.height
                    camera.x = mx * WORLD_SIZE;
                    camera.y = my * WORLD_SIZE;
                    camera.autoCamera = false;
                    return; // Prevent further processing like unit selection
                }
            }

            // Left-click for selection (or other actions if aiming grenade)
            if (e.button === 0) {
                const worldX = (e.clientX - camera.canvasWidth / 2) / camera.zoom + camera.x;
                const worldY = (e.clientY - camera.canvasHeight / 2) / camera.zoom + camera.y;
                
                // Grenade aiming takes precedence if active
                if (gameState.aimingGrenade) {
                    // Logic for launching grenade remains here for now, as it uses selectedEntity from selectionManager
                    const currentSelectedEntity = selectionManager.getSelected();
                    if (currentSelectedEntity && typeof currentSelectedEntity.launchGrenade === 'function') {
                        currentSelectedEntity.launchGrenade(worldX, worldY, gameContext);
                    }
                    console.log(`Grenade targeted at ${worldX.toFixed(0)}, ${worldY.toFixed(0)}`);
                    gameState.aimingGrenade = false;
                    e.preventDefault();
                    e.stopPropagation();
                    return;
                }

                // If not aiming grenade and not in FPV, handle general left click via InputManager
                if (!gameState.fpvMode) {
                    inputManager.handleLeftClick(worldX, worldY, e.shiftKey);

                    // Introspection window on Ctrl+Click is a UI feature, can remain here.
                    // It uses selectionManager, which is not yet command-driven for UI feedback.
                    const currentSelectionForIntrospection = selectionManager.getSelected(); 
                    if (currentSelectionForIntrospection && e.ctrlKey && gameContext.introspectionManager) {
                        gameContext.introspectionManager.createIntrospectionWindow(
                            currentSelectionForIntrospection, 
                            e.clientX + 20, 
                            e.clientY + 20, 
                            gameContext
                        );
                    }
                }
            } else if (e.button === 2) { // Right click for commands (e.g., move)
                const worldX = (e.clientX - camera.canvasWidth / 2) / camera.zoom + camera.x;
                const worldY = (e.clientY - camera.canvasHeight / 2) / camera.zoom + camera.y;
                inputManager.handleRightClick(worldX, worldY); // Pass false for shiftKey, or e.shiftKey if needed
                e.preventDefault(); // Prevent context menu
            }
        });

        canvas.addEventListener('mousemove', (e) => {
            // Update window manager
            if (gameContext.windowManager) {
                gameContext.windowManager.handleMouseMove(e.clientX, e.clientY);
            }

            // Camera panning logic is now handled by the new camera controls in app.js
            // if (mouseDown && !gameState.fpvMode) {
            //     const dx = e.clientX - lastMouseX;
            //     const dy = e.clientY - lastMouseY;
            //     camera.x -= dx / camera.zoom;
            //     camera.y -= dy / camera.zoom;
            //     camera.autoCamera = false;
            //     lastMouseX = e.clientX;
            //     lastMouseY = e.clientY;
            // }
        });

        canvas.addEventListener('mouseup', (e) => {
            // Update window manager
            if (gameContext.windowManager) {
                gameContext.windowManager.handleMouseUp(e.clientX, e.clientY, e.button);
            }
            
            mouseDown = false; // Still track mouseDown for selection logic, just not for panning here.
        });

        canvas.addEventListener('wheel', (e) => {
            // Camera zooming logic is now handled by the new camera controls in app.js
            // if (!gameState.fpvMode) {
            //     const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
            //     camera.zoom *= zoomFactor;
            //     camera.zoom = Math.max(camera.minZoom, Math.min(camera.maxZoom, camera.zoom));
            // }
            // e.preventDefault(); // Prevent page scrolling - this is handled by new wheel listener in app.js if needed
        }, { passive: true }); 
    } else {
        console.error("Canvas element not found in gameContext for input handling.");
    }

    canvas.addEventListener('contextmenu', (event) => {
        event.preventDefault(); // Prevent default browser context menu.

        const worldPos = this.simulation.camera.screenToWorld(event.clientX, event.clientY);

        // Assuming gameContext.strategicAI holds the AI instance
        const prediction = this.simulation.strategicAI ? this.simulation.strategicAI.currentPrediction : null;

        if (prediction && prediction.type === 'ENEMY_GROUND_ATTACK') {
            if (isPointOnPrediction(worldPos.x, worldPos.y, prediction, this.simulation.camera)) {
                console.log(`Player interacted (right-click) with prediction: ${prediction.id}`);

                // For now, default to "Acknowledge & Reinforce"
                if (this.simulation.gameState && typeof this.simulation.gameState.addEvent === 'function') {
                    this.simulation.gameState.addEvent('PlayerInteraction_AckReinforce_AttackVector', {
                        predictedPathID: prediction.id,
                        confidence: prediction.confidence, // Send current confidence at time of interaction
                        playerReinforceFocus: true
                    });
                    // Optional: Add visual feedback directly on the prediction object
                    // prediction.acknowledged = true; // The renderer would need to check this
                }
                // Here you would ideally open a context menu to choose the interaction type.
                // For this step, we directly trigger one type of interaction.
            }
        }
    });

    // --- Context Menu Event Listener for AI Prediction Interaction ---
    canvas.addEventListener('contextmenu', (event) => {
        event.preventDefault(); // Prevent default browser context menu.

        // Convert screen click coordinates to world coordinates.
        const worldPos = this.simulation.camera.screenToWorld(event.clientX, event.clientY);

        // Access the current AI prediction from the StrategicAI instance attached to the simulation.
        const prediction = this.simulation.strategicAI ? this.simulation.strategicAI.currentPrediction : null;

        // Check if there's an active prediction and it's of the type we can interact with.
        if (prediction && prediction.type === 'ENEMY_GROUND_ATTACK') {
            // Use the helper function to determine if the click was on the visualized prediction.
            if (isPointOnPrediction(worldPos.x, worldPos.y, prediction, this.simulation.camera)) {
                console.log(`Player interacted (right-click) with prediction: ${prediction.id}`);

                // For this initial implementation, a right-click defaults to the "Acknowledge & Reinforce" action.
                // A more complete system would involve a UI context menu to select different interactions.
                if (this.simulation.gameState && typeof this.simulation.gameState.addEvent === 'function') {
                    this.simulation.gameState.addEvent('PlayerInteraction_AckReinforce_AttackVector', {
                        predictedPathID: prediction.id, // ID of the prediction being interacted with.
                        confidence: prediction.confidence, // Current confidence of the AI in this prediction.
                        playerReinforceFocus: true // Flag indicating player's intent to reinforce.
                    });
                    // Optional: Visual feedback could be triggered here or by the AI's reaction to the event.
                    // e.g., prediction.acknowledged = true; (The renderer would then visually update the prediction).
                }
            }
        }
    });

    // --- Document Event Listeners (Keyboard shortcuts) ---
    document.addEventListener('keydown', (e) => {
        // Get selected entity from selection manager for key events
        const selectedEntity = selectionManager.getSelected(); // This might be stale if InputManager now owns selection state for commands.
                                                          // For now, keep for FPV/grenade checks.

        // Allow camera controls in app.js to handle WASD, Q, E, Shift, Ctrl, Alt without interference here.
        const camControlKeys = ['w', 'a', 's', 'd', 'q', 'e', 'shift', 'control', 'alt'];
        if (camControlKeys.includes(e.key.toLowerCase())) {
            // If it's a camera control key, let app.js camera handlers deal with it primarily.
            // We might still want Shift/Ctrl/Alt for command modifiers here, so don't return yet.
        }
        
        // Game state related commands are now sent to InputManager
        switch (e.key.toLowerCase()) {
            case ' ': 
                inputManager.handleKeyPress(' '); 
                break;
            case 'p': 
                inputManager.handleKeyPress('p');
                break;
            case 'r':
                if (gameContext.initGame) gameContext.initGame(gameContext); 
                break;
            case 'f':
                // Use selectedEntity from manager
                if (selectedEntity && selectedEntity.type && selectedEntity.type.speed !== undefined) { // Check if it's a unit (has speed)
                    gameState.fpvMode = !gameState.fpvMode;
                }
                break;
            case 'c':
                camera.autoCamera = !camera.autoCamera;
                if (camera.autoCamera) {
                    addEvent(gameContext, 'strategic', 'Auto-camera enabled', 1); // addEvent is from gameContext
                }
                break;
            case 'g':
                // Use selectedEntity from manager, ensure it's a commander unit
                if (selectedEntity &&
                    selectedEntity.type === UNIT_TYPES.commander && 
                    (selectedEntity.grenadeCooldown === undefined || selectedEntity.grenadeCooldown <= 0)) { 
                    gameState.aimingGrenade = true; 
                    console.log("Grenade aiming activated.");
                } else if (gameState.aimingGrenade) {
                    gameState.aimingGrenade = false; 
                    console.log("Grenade aiming cancelled by G.");
                }
                // Potentially: inputManager.handleKeyPress('g');
                break;
            case 'tab':
                e.preventDefault(); // Prevent default browser focus shifting and scrolling
                // console.log("Tab key pressed and default behavior prevented."); // Optional: for testing
                break;
            case 'escape':
                gameState.fpvMode = false;
                if (gameState.aimingGrenade) {
                    gameState.aimingGrenade = false;
                    console.log("Grenade aiming cancelled by Escape.");
                }
                break;
            case 'w':
                if (gameContext.allowWindowCreation) {
                    createSampleWindow(gameContext);
                } else {
                    console.log("Window creation is disabled.");
                }
                break;
            case 'q':
                if (gameContext.allowWindowCreation) {
                    createUnitInspectorWindow(gameContext);
                } else {
                    console.log("Window creation is disabled.");
                }
                break;
            case 'i':
                // Create introspection window for selected object
                if (selectedEntity && gameContext.introspectionManager) { // Use selectedEntity from manager
                    gameContext.introspectionManager.createIntrospectionWindow(
                        selectedEntity,
                        200,
                        100,
                        gameContext
                    );
                }
                break;
            case 'h':
                // Show command hierarchy for blue team
                const blueRenderer = gameContext.commandStatusRenderers.get('blue');
                if (blueRenderer && !blueRenderer.isVisible() && gameContext.allowWindowCreation) {
                    const window = blueRenderer.createWindow(50, 50);
                    gameContext.windowManager.addWindow(window);
                }
                break;
            case 'j':
                // Show command hierarchy for red team
                const redRenderer = gameContext.commandStatusRenderers.get('red');
                if (redRenderer && !redRenderer.isVisible() && gameContext.allowWindowCreation) {
                    const window = redRenderer.createWindow(600, 50);
                    gameContext.windowManager.addWindow(window);
                }
                break;
        }
    });

    // --- Window Resize Listener ---
    window.addEventListener('resize', () => {
        if (canvas && camera) { // Ensure canvas and camera exist on gameContext
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            camera.canvasWidth = canvas.width;
            camera.canvasHeight = canvas.height; // CORRECTED
            // Note: The actual rendering update due to resize happens in the next gameLoop call.
        }
    });
}

function createSampleWindow(gameContext) {
    const canvasRect = gameContext.canvas.getBoundingClientRect();  // Get canvas position for relative alignment
    const adjustedX = canvasRect.left + 100;  // Adjust relative to canvas
    const adjustedY = canvasRect.top + 100;   // Adjust relative to canvas
    const window = new BorderLayoutContainer(adjustedX, adjustedY, 400, 300, {
        title: 'Sample Border Layout Window',
        backgroundColor: 'rgba(40, 40, 50, 0.95)',
        borderColor: '#666666'
    });
    // Add components to different regions
    window.addComponent(
        new TextComponent('North Panel\nToolbar Area', { 
            backgroundColor: 'rgba(60, 60, 80, 0.8)',
            alignment: 'center'
        }), 
        BorderRegion.NORTH
    );

    window.addComponent(
        new TextComponent('West\nSidebar\nNavigation', { 
            backgroundColor: 'rgba(80, 60, 60, 0.8)',
            fontSize: 10
        }), 
        BorderRegion.WEST
    );

    window.addComponent(
        new TextComponent('East\nProperties\nPanel', { 
            backgroundColor: 'rgba(60, 80, 60, 0.8)',
            fontSize: 10
        }), 
        BorderRegion.EAST
    );

    window.addComponent(
        new TextComponent('Center Content Area\n\nThis is where the main content goes.\nIt automatically fills the remaining space\nafter other regions are laid out.', { 
            backgroundColor: 'rgba(50, 50, 70, 0.8)',
            alignment: 'center'
        }), 
        BorderRegion.CENTER
    );

    window.addComponent(
        new TextComponent('South Status Bar', { 
            backgroundColor: 'rgba(70, 70, 60, 0.8)',
            alignment: 'center',
            fontSize: 10
        }), 
        BorderRegion.SOUTH
    );

    // Set custom region sizes
    window.setRegionSize(BorderRegion.NORTH, 60);
    window.setRegionSize(BorderRegion.SOUTH, 30);
    window.setRegionSize(BorderRegion.WEST, 120);
    window.setRegionSize(BorderRegion.EAST, 100);

    gameContext.windowManager.addWindow(window);
}

/**
 * Calculates the Euclidean distance between two points.
 * @param {object} p1 - The first point with x, y properties.
 * @param {object} p2 - The second point with x, y properties.
 * @returns {number} The distance between p1 and p2.
 */
function getDistanceSimple(p1, p2) {
    const dx = p1.x - p2.x;
    const dy = p1.y - p2.y;
    return Math.sqrt(dx * dx + dy * dy);
}

/**
 * Checks if a given world coordinate point is on or near a visualized AI prediction.
 * @param {number} worldX - The x-coordinate of the click in world space.
 * @param {number} worldY - The y-coordinate of the click in world space.
 * @param {object} prediction - The AI prediction object. Expected to have `targetArea` and `path`.
 * @param {object} camera - The game camera object (not used in current distance checks but kept for future potential use with screen-space thresholds).
 * @returns {boolean} True if the point is considered on the prediction, false otherwise.
 */
function isPointOnPrediction(worldX, worldY, prediction, camera) {
    if (!prediction) return false;

    const clickPoint = { x: worldX, y: worldY };
    // Define a tolerance for clicking near a path. This is in world units.
    const interactionThreshold = 10;

    // 1. Target Area Hit Check: Check if the click is within the prediction's target area circle.
    if (prediction.targetArea) {
        const distToCenter = getDistanceSimple(clickPoint, prediction.targetArea);
        if (distToCenter <= prediction.targetArea.radius) {
            return true; // Click is inside the target area.
        }
    }

    // 2. Path Hit Check: Check if the click is close to any segment of the predicted path.
    if (prediction.path && prediction.path.length >= 2) {
        for (let i = 0; i < prediction.path.length - 1; i++) {
            const p1 = prediction.path[i]; // Start point of the current path segment.
            const p2 = prediction.path[i+1]; // End point of the current path segment.

            // Calculate squared length of the segment.
            const lenSq = Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2);
            if (lenSq === 0) { // If the segment is just a point.
                if (getDistanceSimple(clickPoint, p1) <= interactionThreshold) return true;
                continue; // Move to the next segment.
            }

            // Calculate the projection of the click point onto the line defined by the segment.
            // 't' is the normalized position of the projection along the segment.
            let t = ((clickPoint.x - p1.x) * (p2.x - p1.x) + (clickPoint.y - p1.y) * (p2.y - p1.y)) / lenSq;
            // Clamp 't' to the range [0, 1] to ensure the projection is on the segment itself.
            t = Math.max(0, Math.min(1, t));

            // Calculate the coordinates of the projected point on the segment.
            const projectionX = p1.x + t * (p2.x - p1.x);
            const projectionY = p1.y + t * (p2.y - p1.y);

            // Calculate the distance from the click point to this projected point.
            const distToSegment = getDistanceSimple(clickPoint, {x: projectionX, y: projectionY});

            if (distToSegment <= interactionThreshold) {
                return true; // Click is close enough to this path segment.
            }
        }
    }

    return false; // Click is not on the target area or any path segment.
}


function createUnitInspectorWindow(gameContext) {
    // Duplicate declaration removed to fix redeclaration error
    const canvasRect = gameContext.canvas.getBoundingClientRect();  // Get canvas position
    const adjustedX = canvasRect.left + 200;  // Adjust relative to canvas
    const adjustedY = canvasRect.top + 50;    // Adjust relative to canvas
    const window = new BorderLayoutContainer(adjustedX, adjustedY, 350, 400, {
        title: 'Inspector',
        backgroundColor: 'rgba(30, 40, 50, 0.95)',
        borderColor: '#888888'
    }); // Correctly close the BorderLayoutContainer constructor

    // Get selected entity from the selection manager
    const selectedEntity = gameContext.selectionManager.getSelected();

    // Entity selection display
    window.addComponent(
        new TextComponent('Selected Entity Info', { // Updated title
            backgroundColor: 'rgba(50, 60, 70, 0.8)',
            alignment: 'center',
            fontSize: 11
        }),
        BorderRegion.NORTH
    );

    // Entity stats
    let entityInfo = 'No entity selected';
    if (selectedEntity) {
        // Basic info common to both units and buildings (from summaries)
        entityInfo = `Type: ${selectedEntity.type.name}\n` +
                     `Team: ${selectedEntity.team.toUpperCase()}\n` +
                     `HP: ${Math.ceil(selectedEntity.hp)}/${selectedEntity.maxHp}\n` +
                     `Position: ${Math.floor(selectedEntity.x)}, ${Math.floor(selectedEntity.y)}`;
        
        // Differentiate based on unique properties from summaries
        // Unit has 'speed', 'range', 'shields', 'target'
        // Building has 'productionQueue', 'productionProgress', 'resourceGeneration'
        if (selectedEntity.type.speed !== undefined) { // Indicates a unit by checking for speed property
            entityInfo += `\nSpeed: ${selectedEntity.type.speed}\n` +
                          `Range: ${selectedEntity.type.range}`;
            if (selectedEntity.shields !== undefined && selectedEntity.shields > 0) {
                entityInfo += `\nShields: ${Math.ceil(selectedEntity.shields)}/${selectedEntity.maxShields}`;
            }
            if (selectedEntity.target) {
                entityInfo += `\nTarget: ${selectedEntity.target.type ? selectedEntity.target.type.name : 'Position'}`;
            }
        } else if (selectedEntity.productionQueue !== undefined || selectedEntity.type.resourceGeneration !== undefined) { // Indicates a building by checking for production or resource generation properties
            if (selectedEntity.productionQueue && selectedEntity.productionQueue.length > 0) {
                entityInfo += `\nProducing: ${selectedEntity.productionQueue[0].name}`;
            } else {
                entityInfo += `\nProduction: Idle`;
            }
            // Check for resource generation properties directly if available in type
            if (selectedEntity.type.resourceGeneration) {
                entityInfo += `\nGenerating: ${selectedEntity.type.resourceGeneration.mass || 0} Mass, ${selectedEntity.type.resourceGeneration.energy || 0} Energy`;
            }
        }
        // If it's something else with hp/type but not clearly unit/building, it just shows basic info.
    }

    window.addComponent(
        new TextComponent(entityInfo, {
            backgroundColor: 'rgba(40, 50, 60, 0.8)',
            fontSize: 10,
            padding: 10
        }),
        BorderRegion.CENTER
    );

    // Commands (generalized)
    window.addComponent(
        new TextComponent('Commands\n• Click to select\n• F for FPV mode (if unit selected)\n• G for grenade (if Commander selected)', {
            backgroundColor: 'rgba(60, 50, 40, 0.8)',
            fontSize: 9,
            padding: 8
        }),
        BorderRegion.SOUTH
    );

    window.setRegionSize(BorderRegion.NORTH, 40);
    window.setRegionSize(BorderRegion.SOUTH, 80);

    gameContext.windowManager.addWindow(window);
}
