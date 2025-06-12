// Extracted content from concatenated strategicAI.js file
// This includes initInputHandling function and related app initialization code

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

            mouseDown = true;
            lastMouseX = e.clientX;
            lastMouseY = e.clientY;

            // Prevent default only for middle mouse
            if (e.button === 1) {
                e.preventDefault();
            }

            if (e.button === 0) { // Left mouse button
                const rect = canvas.getBoundingClientRect();
                const mouseX = e.clientX - rect.left;
                const mouseY = e.clientY - rect.top;

                // Convert screen coordinates to world coordinates
                const worldX = (mouseX - canvas.width / 2) / camera.zoom + camera.x;
                const worldY = (mouseY - canvas.height / 2) / camera.zoom + camera.y;

                // Handle unit selection and commands
                if (e.shiftKey) {
                    // Add to selection
                    const clickedUnit = units.find(unit => {
                        const distance = Math.hypot(unit.x - worldX, unit.y - worldY);
                        return distance < 30; // Selection radius
                    });
                    
                    if (clickedUnit && clickedUnit.team === 'blue') {
                        selectionManager.addToSelection(clickedUnit);
                    }
                } else if (e.ctrlKey || e.metaKey) {
                    // Command selected units
                    const selectedUnits = selectionManager.getSelectedUnits().filter(unit => unit.team === 'blue');
                    if (selectedUnits.length > 0) {
                        // Check if clicking on an enemy unit (attack command)
                        const targetUnit = units.find(unit => {
                            const distance = Math.hypot(unit.x - worldX, unit.y - worldY);
                            return distance < 30 && unit.team !== 'blue';
                        });

                        if (targetUnit) {
                            // Attack command
                            selectedUnits.forEach(unit => {
                                addEvent('unit_command', {
                                    unitId: unit.id,
                                    command: 'attack',
                                    target: targetUnit
                                });
                            });
                        } else {
                            // Move command
                            selectedUnits.forEach(unit => {
                                addEvent('unit_command', {
                                    unitId: unit.id,
                                    command: 'move',
                                    targetX: worldX,
                                    targetY: worldY
                                });
                            });
                        }
                    }
                } else {
                    // Normal selection
                    const clickedUnit = units.find(unit => {
                        const distance = Math.hypot(unit.x - worldX, unit.y - worldY);
                        return distance < 30; // Selection radius
                    });
                    
                    if (clickedUnit && clickedUnit.team === 'blue') {
                        selectionManager.selectUnit(clickedUnit);
                    } else {
                        selectionManager.clearSelection();
                    }
                }
            }
        });

        canvas.addEventListener('mouseup', (e) => {
            // Check if window manager handled the event
            if (gameContext.windowManager && gameContext.windowManager.handleMouseUp(e.clientX, e.clientY, e.button)) {
                return; // Window system consumed the event
            }

            mouseDown = false;
        });

        canvas.addEventListener('mousemove', (e) => {
            // Check if window manager handled the event
            if (gameContext.windowManager && gameContext.windowManager.handleMouseMove(e.clientX, e.clientY)) {
                return; // Window system consumed the event
            }

            if (mouseDown && e.button === 1) { // Middle mouse drag
                const deltaX = e.clientX - lastMouseX;
                const deltaY = e.clientY - lastMouseY;
                
                // Pan camera (note: deltaX/Y are inverted to make panning feel natural)
                camera.targetX -= deltaX / camera.zoom;
                camera.targetY -= deltaY / camera.zoom;
                
                lastMouseX = e.clientX;
                lastMouseY = e.clientY;
            }
        });

        canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            
            const rect = canvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            
            // Calculate zoom
            const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
            const oldZoom = camera.zoom;
            camera.zoom *= zoomFactor;
            
            // Clamp zoom
            camera.zoom = Math.max(0.1, Math.min(camera.zoom, 3.0));
            
            // Zoom towards mouse position
            const zoomChange = camera.zoom / oldZoom;
            const worldMouseX = (mouseX - canvas.width / 2) / oldZoom + camera.x;
            const worldMouseY = (mouseY - canvas.height / 2) / oldZoom + camera.y;
            
            camera.x = worldMouseX - (mouseX - canvas.width / 2) / camera.zoom;
            camera.y = worldMouseY - (mouseY - canvas.height / 2) / camera.zoom;
            camera.targetX = camera.x;
            camera.targetY = camera.y;
        });

        // Add context menu prevention
        canvas.addEventListener('contextmenu', (e) => {
            e.preventDefault();
        });
    }

    // --- Keyboard Event Listeners ---
    document.addEventListener('keydown', (e) => {
        // Check if window manager handled the key event
        if (gameContext.windowManager && gameContext.windowManager.handleKeyDown(e.key, e.ctrlKey, e.shiftKey, e.altKey)) {
            e.preventDefault();
            return; // Window system consumed the event
        }

        switch (e.key) {
            case 'p':
            case 'P':
                gameState.paused = !gameState.paused;
                console.log(gameState.paused ? 'Game paused' : 'Game resumed');
                break;
            
            case 'r':
            case 'R':
                if (e.ctrlKey) {
                    // Restart game
                    console.log('Restarting game...');
                    initGame();
                }
                break;
                
            case 'f':
            case 'F':
                gameState.fpvMode = !gameState.fpvMode;
                console.log(gameState.fpvMode ? 'FPV mode enabled' : 'FPV mode disabled');
                break;
                
            case 'g':
            case 'G':
                gameState.aimingGrenade = !gameState.aimingGrenade;
                console.log(gameState.aimingGrenade ? 'Grenade aiming mode' : 'Normal mode');
                break;
                
            case 'Escape':
                // Clear selection
                selectionManager.clearSelection();
                break;
                
            // Camera controls
            case 'ArrowUp':
            case 'w':
            case 'W':
                camera.targetY -= 100 / camera.zoom;
                break;
            case 'ArrowDown':
            case 's':
            case 'S':
                camera.targetY += 100 / camera.zoom;
                break;
            case 'ArrowLeft':
            case 'a':
            case 'A':
                camera.targetX -= 100 / camera.zoom;
                break;
            case 'ArrowRight':
            case 'd':
            case 'D':
                camera.targetX += 100 / camera.zoom;
                break;
                
            // Zoom controls
            case '=':
            case '+':
                camera.zoom = Math.min(camera.zoom * 1.2, 3.0);
                break;
            case '-':
            case '_':
                camera.zoom = Math.max(camera.zoom / 1.2, 0.1);
                break;
        }
    });

    // --- Window Creation Test Function ---
    function createTestWindow() {
        if (!gameContext.allowWindowCreation) {
            console.log('Window creation is disabled');
            return;
        }

        const window = new BorderLayoutContainer();
        window.x = 200 + Math.random() * 300;
        window.y = 100 + Math.random() * 200;
        window.width = 300;
        window.height = 200;
        window.title = `Test Window ${Date.now()}`;

        // Add some content
        const content = new TextComponent();
        content.setText("This is a test window!\nYou can drag it around.");
        window.setRegionContent(BorderRegion.CENTER, content);

        window.setRegionSize(BorderRegion.NORTH, 40);
        window.setRegionSize(BorderRegion.SOUTH, 80);

        gameContext.windowManager.addWindow(window);
    }

    // Initialize input handling function
    if (!window.gameContext.initInputHandling) {
        window.gameContext.initInputHandling = initInputHandling;
    }
}