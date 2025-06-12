// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This entire file is deprecated and scheduled for deletion.                    #
// #   The main browser entry point is now js/app.js.                                                 #
// #   Features from this file (like advanced camera controls) have been merged into js/app.js.       #
// #   The renderer selection and old simulation logic are now superseded.                            #
// #                                                                                                  #
// ####################################################################################################

import { WebGL2DRenderer } from '../rendering/webgl_2d_renderer.js';
import { WebGLRenderer } from '../rendering/webglRenderer.js';
import { TERRAIN_TYPES, TILE_SIZE, GRID_SIZE, WORLD_SIZE } from '../config/gameConstants.js';

// Simple seeded random number generator for deterministic simulation
class SeededRandom {
    constructor(seed = Date.now()) {
        this.seed = seed % 2147483647;
        if (this.seed <= 0) this.seed += 2147483646;
    }
    
    random() {
        this.seed = this.seed * 16807 % 2147483647;
        return (this.seed - 1) / 2147483646;
    }
    
    randomInt(min, max) {
        return Math.floor(this.random() * (max - min + 1)) + min;
    }
}

// Create separate random instance for simulation
const simulationRandom = new SeededRandom(12345); // Fixed seed for reproducible results

// Constants for 2D WebGL Renderer (for visual consistency with terrain types)
const TERRAIN_COLORS = {
    [TERRAIN_TYPES.WATER]: [0.0, 0.2, 0.5, 1.0],    // Blue
    [TERRAIN_TYPES.LAND]: [0.2, 0.6, 0.2, 1.0],     // Green
    [TERRAIN_TYPES.MOUNTAIN]: [0.4, 0.4, 0.4, 1.0]  // Gray
};

const TEAM_COLORS = {
    'blue': [0.3, 0.5, 1.0, 1.0], // Bright Blue
    'red': [0.8, 0.1, 0.1, 1.0]   // Dark Red
};

const rendererSelect = document.getElementById('renderer-select');
const startButton = document.getElementById('start-simulation');
const zoomInButton = document.getElementById('zoom-in');
const zoomOutButton = document.getElementById('zoom-out');
const gameCanvas = document.getElementById('game-canvas');
const gameSvg = document.getElementById('game-svg');

let currentRenderer = null;
let simulationInterval = null;
let bots = [];
let terrain = [];
let events = [];
let gameTime = 0;
const SIMULATION_DURATION = 10; // 10 seconds

let camera = {
    x: 0, y: 0, zoom: 1.0,
    targetX: 0, targetY: 0, targetZoom: 1.0,
    velocityX: 0, velocityY: 0, velocityZoom: 0,
    isDragging: false, lastMouseX: 0, lastMouseY: 0,
    angle: 0, targetAngle: 0, // Camera pitch angle (0 = top-down, higher = more angled)
    rotation: 0, targetRotation: 0 // Camera yaw rotation
}; // Enhanced camera state with smooth interpolation and angle control
const ZOOM_FACTOR = 1.2; // How much to zoom in/out
const MIN_ZOOM = 0.01; // Roughly "10 miles up" (very zoomed out)
const MAX_ZOOM = 50.0;  // Roughly "1 meter" (very zoomed in) - increased for trait viewing
const CAMERA_SMOOTHING = 0.15; // Camera interpolation factor (lower = smoother)
const MOMENTUM_DECAY = 0.95; // How quickly momentum dies down

// Dynamic angle constants
const MAX_STRATEGIC_ZOOM = 2.0; // Above this zoom = top-down strategic view
const MIN_TACTICAL_ZOOM = 8.0; // Below this zoom = maximum tactical angle
const MAX_TACTICAL_ANGLE = 45; // Maximum camera angle in degrees for tactical view

// WASD movement speed
const KEYBOARD_MOVE_SPEED = 5; // Base movement speed
const MODIFIER_SPEED_MULTIPLIER = 3; // Speed multiplier for shift key

// Keyboard state tracking
const keys = {
    w: false, a: false, s: false, d: false,
    shift: false, ctrl: false, alt: false
};

// Topographical terrain generation with elevation and sea level
const SEA_LEVEL = 0.3; // Elevation threshold for water (0.0 = lowest, 1.0 = highest)
const MOUNTAIN_LEVEL = 0.7; // Elevation threshold for mountains
const RESOURCE_FREQUENCY = 0.05; // Chance for resource deposits

function generateHeightmap(size) {
    const heightmap = [];
    const resourceMap = [];
    
    // Initialize arrays
    for (let x = 0; x < size; x++) {
        heightmap[x] = [];
        resourceMap[x] = [];
        for (let y = 0; y < size; y++) {
            heightmap[x][y] = 0;
            resourceMap[x][y] = false;
        }
    }
    
    // Generate multiple octaves of noise for realistic terrain
    for (let octave = 0; octave < 4; octave++) {
        const frequency = Math.pow(2, octave) * 0.1;
        const amplitude = Math.pow(0.5, octave);
        
        for (let x = 0; x < size; x++) {
            for (let y = 0; y < size; y++) {
                // Simple noise generation using multiple sine waves
                const noise1 = Math.sin(x * frequency) * Math.cos(y * frequency);
                const noise2 = Math.sin(x * frequency * 1.7) * Math.cos(y * frequency * 1.3);
                const noise3 = simulationRandom.random() * 0.2 - 0.1; // Random variation
                
                heightmap[x][y] += (noise1 + noise2 + noise3) * amplitude;
            }
        }
    }
    
    // Normalize heightmap to 0-1 range
    let minHeight = Infinity, maxHeight = -Infinity;
    for (let x = 0; x < size; x++) {
        for (let y = 0; y < size; y++) {
            minHeight = Math.min(minHeight, heightmap[x][y]);
            maxHeight = Math.max(maxHeight, heightmap[x][y]);
        }
    }
    
    const heightRange = maxHeight - minHeight;
    for (let x = 0; x < size; x++) {
        for (let y = 0; y < size; y++) {
            heightmap[x][y] = (heightmap[x][y] - minHeight) / heightRange;
            
            // Place resources on elevated areas near sea level
            if (heightmap[x][y] > SEA_LEVEL + 0.1 && heightmap[x][y] < SEA_LEVEL + 0.3) {
                if (simulationRandom.random() < RESOURCE_FREQUENCY) {
                    resourceMap[x][y] = true;
                }
            }
        }
    }
    
    return { heightmap, resourceMap };
}

function initializeTerrain(size) {
    const { heightmap, resourceMap } = generateHeightmap(size);
    const newTerrain = [];
    
    for (let x = 0; x < size; x++) {
        newTerrain[x] = [];
        for (let y = 0; y < size; y++) {
            const elevation = heightmap[x][y];
            
            if (resourceMap[x][y]) {
                newTerrain[x][y] = {
                    type: TERRAIN_TYPES.RESOURCE,
                    elevation: elevation,
                    hasResource: true
                };
            } else if (elevation <= SEA_LEVEL) {
                newTerrain[x][y] = {
                    type: TERRAIN_TYPES.WATER,
                    elevation: elevation,
                    hasResource: false
                };
            } else if (elevation >= MOUNTAIN_LEVEL) {
                newTerrain[x][y] = {
                    type: TERRAIN_TYPES.MOUNTAIN,
                    elevation: elevation,
                    hasResource: false
                };
            } else {
                newTerrain[x][y] = {
                    type: TERRAIN_TYPES.LAND,
                    elevation: elevation,
                    hasResource: false
                };
            }
        }
    }
    
    return newTerrain;
}

function updateBot(bot, currentTerrain, allBots, time, eventLog) {
    const actionChance = simulationRandom.random();
    const otherBot = allBots.find(b => b.id !== bot.id);
    const distToOther = Math.sqrt(Math.pow(otherBot.x - bot.x, 2) + Math.pow(otherBot.y - bot.y, 2));

    if (actionChance < 0.3 && distToOther < 3) {
        const damage = Math.floor(simulationRandom.random() * 10) + 5;
        otherBot.hp -= damage;
        bot.action = 'attack';
        eventLog.push({
            time,
            message: `Bot ${bot.id} (${bot.team}) attacked Bot ${otherBot.id} for ${damage} damage at (${bot.x},${bot.y})`
        });
    } else if (actionChance < 0.6) {
        const direction = Math.floor(simulationRandom.random() * 4);
        let newX = bot.x;
        let newY = bot.y;
        if (direction === 0 && bot.y > 0) newY--;
        else if (direction === 1 && bot.x < currentTerrain.length - 1) newX++;
        else if (direction === 2 && bot.y < currentTerrain.length - 1) newY++;
        else if (direction === 3 && bot.x > 0) newX--;

        if (newX >= 0 && newX < GRID_SIZE && newY >= 0 && newY < GRID_SIZE && currentTerrain[newX][newY].type !== TERRAIN_TYPES.WATER) {
            bot.x = newX;
            bot.y = newY;
            bot.action = 'move';
            eventLog.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) moved to (${bot.x},${bot.y})`
            });
        } else {
            bot.action = 'idle';
        }
    } else {
        bot.action = 'defend';
        if (simulationRandom.random() < 0.5) {
            eventLog.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) defended at (${bot.x},${bot.y})`
            });
        }
    }
}

function renderScene(currentTerrain, currentBots, rendererType) {
    if (rendererType === 'svg') {
        // Clear SVG
        while (gameSvg.firstChild) {
            gameSvg.removeChild(gameSvg.firstChild);
        }

        // Render terrain
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                rect.setAttribute('x', x * TILE_SIZE);
                rect.setAttribute('y', y * TILE_SIZE);
                rect.setAttribute('width', TILE_SIZE);
                rect.setAttribute('height', TILE_SIZE);
                rect.classList.add('terrain-cell');
                // Map numerical terrain type to class name for CSS styling
                switch (currentTerrain[x][y].type) {
                    case TERRAIN_TYPES.WATER: rect.classList.add('water'); break;
                    case TERRAIN_TYPES.LAND: rect.classList.add('land'); break;
                    case TERRAIN_TYPES.MOUNTAIN: rect.classList.add('mountain'); break;
                    case TERRAIN_TYPES.RESOURCE: rect.classList.add('resource'); break;
                }
                gameSvg.appendChild(rect);
            }
        }

        // Render bots
        currentBots.forEach(bot => {
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('id', `bot-${bot.id}`);
            circle.setAttribute('cx', bot.x * TILE_SIZE + TILE_SIZE / 2);
            circle.setAttribute('cy', bot.y * TILE_SIZE + TILE_SIZE / 2);
            circle.setAttribute('r', TILE_SIZE / 3);
            circle.classList.add('bot');
            circle.classList.add(`team-${bot.team === 'blue' ? 0 : 1}`);
            gameSvg.appendChild(circle);
        });

    } else if (rendererType === 'canvas2d') {
        const ctx = gameCanvas.getContext('2d');
        ctx.clearRect(0, 0, gameCanvas.width, gameCanvas.height); // Clear canvas

        // Render terrain
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const terrain = currentTerrain[x][y];
                const terrainType = terrain.type;
                const elevation = terrain.elevation;
                let color;
                switch (terrainType) {
                    case TERRAIN_TYPES.WATER: color = `rgba(0, ${Math.floor(51 + elevation * 50)}, 128, 1.0)`; break; // Varying blue depth
                    case TERRAIN_TYPES.LAND: color = `rgba(${Math.floor(51 + elevation * 100)}, 153, 51, 1.0)`; break;  // Varying green height
                    case TERRAIN_TYPES.MOUNTAIN: color = `rgba(${Math.floor(102 + elevation * 100)}, ${Math.floor(102 + elevation * 100)}, ${Math.floor(102 + elevation * 100)}, 1.0)`; break; // Varying gray
                    case TERRAIN_TYPES.RESOURCE: color = 'rgba(255, 215, 0, 1.0)'; break; // Gold for resources
                    default: color = 'rgba(0, 0, 0, 1.0)'; // Black
                }
                ctx.fillStyle = color;
                ctx.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }

        // Render bots
        currentBots.forEach(bot => {
            const botSize = TILE_SIZE * 0.6;
            const botColor = bot.team === 'blue' ? 'rgba(25, 25, 204, 1.0)' : 'rgba(204, 25, 25, 1.0)'; // Darker colors
            const centerX = bot.x * TILE_SIZE + TILE_SIZE / 2;
            const centerY = bot.y * TILE_SIZE + TILE_SIZE / 2;

            ctx.fillStyle = botColor;
            ctx.beginPath();
            ctx.arc(centerX, centerY, botSize / 2, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = 'black';
            ctx.lineWidth = 1;
            ctx.stroke();
        });

    } else if (rendererType === 'webgl2d') {
        currentRenderer.clear();

        // Render terrain
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const terrain = currentTerrain[x][y];
                const terrainType = terrain.type;
                const elevation = terrain.elevation;
                let color = TERRAIN_COLORS[terrainType];
                
                // Adjust color intensity based on elevation
                if (color) {
                    color = [
                        Math.min(1.0, color[0] + elevation * 0.3),
                        Math.min(1.0, color[1] + elevation * 0.3),
                        Math.min(1.0, color[2] + elevation * 0.3),
                        color[3]
                    ];
                }
                currentRenderer.drawRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE, color);
            }
        }

        // Render bots (as squares for simplicity in 2D WebGL)
        currentBots.forEach(bot => {
            const botSize = TILE_SIZE * 0.6;
            const botColor = TEAM_COLORS[bot.team];
            const centerX = bot.x * TILE_SIZE + TILE_SIZE / 2;
            const centerY = bot.y * TILE_SIZE + TILE_SIZE / 2;
            currentRenderer.drawRect(centerX - botSize / 2, centerY - botSize / 2, botSize, botSize, botColor);
        });
    } else if (rendererType === 'webgl3d') {
        const gameContext = {
            terrain: currentTerrain,
            units: currentBots,
            buildings: []
        };
        currentRenderer.render(gameContext);
    }
}

function startSimulation() {
    clearInterval(simulationInterval); // Clear any existing simulation

    // Reset simulation random seed for deterministic but varied results
    simulationRandom.seed = Date.now() % 2147483647;
    if (simulationRandom.seed <= 0) simulationRandom.seed += 2147483646;

    gameTime = 0;
    events = [];
    terrain = initializeTerrain(GRID_SIZE);
    bots = [
        { id: 1, team: 'blue', x: 2, y: 2, hp: 100, action: 'idle', type: {name: 'Armored Assault Unit'} },
        { id: 2, team: 'red', x: 7, y: 7, hp: 100, action: 'idle', type: {name: 'Strategic Commander Unit'} }
    ];

    const selectedRenderer = rendererSelect.value;

    // Hide/show canvas/svg based on selection
    if (selectedRenderer === 'svg') {
        gameCanvas.style.display = 'none';
        gameSvg.style.display = 'block';
        currentRenderer = null; // No specific renderer class for SVG DOM manipulation
    } else {
        gameCanvas.style.display = 'block';
        gameSvg.style.display = 'none';
        if (selectedRenderer === 'webgl2d') {
            currentRenderer = new WebGL2DRenderer(gameCanvas);
        } else if (selectedRenderer === 'webgl3d') {
            currentRenderer = new WebGLRenderer(gameCanvas);
            // Initialize camera for 3D renderer focused on unit area (0-500 world coordinates)
            camera.x = camera.targetX = 250; // Center of unit area
            camera.y = camera.targetY = 250; // Center of unit area
            camera.zoom = camera.targetZoom = 1.5; // Good zoom level to see units
            currentRenderer.updateCamera({
                x: camera.x,
                y: camera.y,
                zoom: camera.zoom,
                canvasWidth: gameCanvas.width,
                canvasHeight: gameCanvas.height
            });
        }
    }

    renderScene(terrain, bots, selectedRenderer); // Initial render

    // Start smooth camera animation loop for 3D renderer
    if (selectedRenderer === 'webgl3d') {
        startCameraAnimation();
    }

    simulationInterval = setInterval(() => {
        if (gameTime >= SIMULATION_DURATION) {
            clearInterval(simulationInterval);
            console.log('Simulation finished.');
            return;
        }

        gameTime += 1;
        for (const bot of bots) {
            updateBot(bot, terrain, bots, gameTime, events);
        }

        // Only re-render for non-3D renderers (3D uses animation loop)
        if (selectedRenderer !== 'webgl3d') {
            renderScene(terrain, bots, selectedRenderer);
        }

        const timeEvents = events.filter(e => e.time === gameTime);
        for (const event of timeEvents) {
            console.log(`Time ${gameTime}s: ${event.message}`);
        }
    }, 1000); // 1 second per iteration
}

startButton.addEventListener('click', startSimulation);
zoomInButton.addEventListener('click', () => adjustZoom(ZOOM_FACTOR));
zoomOutButton.addEventListener('click', () => adjustZoom(1 / ZOOM_FACTOR));

// Smooth mouse interactions
gameCanvas.addEventListener('wheel', handleMouseWheel, { passive: false });
gameCanvas.addEventListener('mousedown', handleMouseDown);
gameCanvas.addEventListener('mousemove', handleMouseMove);
gameCanvas.addEventListener('mouseup', handleMouseUp);
gameCanvas.addEventListener('mouseleave', handleMouseUp);

// Keyboard controls for camera movement (like FAF/Supreme Commander)
document.addEventListener('keydown', handleKeyDown);
document.addEventListener('keyup', handleKeyUp);

function handleKeyDown(e) {
    switch(e.code) {
        case 'KeyW': keys.w = true; break;
        case 'KeyA': keys.a = true; break;
        case 'KeyS': keys.s = true; break;
        case 'KeyD': keys.d = true; break;
        case 'ShiftLeft':
        case 'ShiftRight': keys.shift = true; break;
        case 'ControlLeft':
        case 'ControlRight': keys.ctrl = true; break;
        case 'AltLeft':
        case 'AltRight': keys.alt = true; break;
    }
}

function handleKeyUp(e) {
    switch(e.code) {
        case 'KeyW': keys.w = false; break;
        case 'KeyA': keys.a = false; break;
        case 'KeyS': keys.s = false; break;
        case 'KeyD': keys.d = false; break;
        case 'ShiftLeft':
        case 'ShiftRight': keys.shift = false; break;
        case 'ControlLeft':
        case 'ControlRight': keys.ctrl = false; break;
        case 'AltLeft':
        case 'AltRight': keys.alt = false; break;
    }
}

function processKeyboardInput() {
    if (!currentRenderer || rendererSelect.value !== 'webgl3d') return;
    
    const baseSpeed = KEYBOARD_MOVE_SPEED;
    const speed = keys.shift ? baseSpeed * MODIFIER_SPEED_MULTIPLIER : baseSpeed;
    const moveAmount = speed / camera.zoom;
    
    // WASD movement
    if (keys.w) camera.targetY -= moveAmount; // Move up
    if (keys.s) camera.targetY += moveAmount; // Move down
    if (keys.a) camera.targetX -= moveAmount; // Move left
    if (keys.d) camera.targetX += moveAmount; // Move right
    
    // Ctrl + mouse wheel for rotation (future enhancement)
    // Alt + WASD for different movement modes (future enhancement)
}

function calculateDynamicCameraAngle() {
    // Calculate target angle based on zoom level (FAF-style)
    let targetAngle = 0;
    
    if (camera.zoom > MAX_STRATEGIC_ZOOM) {
        // Strategic view: top-down (0 degrees)
        targetAngle = 0;
    } else if (camera.zoom < MIN_TACTICAL_ZOOM) {
        // Maximum tactical angle
        targetAngle = MAX_TACTICAL_ANGLE;
    } else {
        // Interpolate between strategic and tactical angles
        const zoomRange = MIN_TACTICAL_ZOOM - MAX_STRATEGIC_ZOOM;
        const zoomFactor = (camera.zoom - MAX_STRATEGIC_ZOOM) / zoomRange;
        targetAngle = MAX_TACTICAL_ANGLE * (1 - zoomFactor);
    }
    
    camera.targetAngle = targetAngle;
}

function adjustZoom(factor) {
    if (currentRenderer && rendererSelect.value === 'webgl3d') {
        const newZoom = camera.targetZoom * factor;
        if (newZoom >= MIN_ZOOM && newZoom <= MAX_ZOOM) {
            camera.targetZoom = newZoom;
        }
    }
}

function handleMouseWheel(e) {
    e.preventDefault();
    if (currentRenderer && rendererSelect.value === 'webgl3d') {
        const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
        const newZoom = camera.targetZoom * zoomFactor;
        if (newZoom >= MIN_ZOOM && newZoom <= MAX_ZOOM) {
            // Zoom towards mouse cursor
            const rect = gameCanvas.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;
            
            // Convert mouse position to world coordinates
            const worldX = (mouseX - gameCanvas.width / 2) / camera.zoom + camera.x;
            const worldY = (mouseY - gameCanvas.height / 2) / camera.zoom + camera.y;
            
            // Update target zoom and adjust camera position
            camera.targetZoom = newZoom;
            const zoomChange = newZoom / camera.zoom;
            camera.targetX += (worldX - camera.targetX) * (1 - 1/zoomChange) * 0.1;
            camera.targetY += (worldY - camera.targetY) * (1 - 1/zoomChange) * 0.1;
        }
    }
}

function handleMouseDown(e) {
    if (e.button === 0) { // Left mouse button
        camera.isDragging = true;
        camera.lastMouseX = e.clientX;
        camera.lastMouseY = e.clientY;
        camera.velocityX = 0;
        camera.velocityY = 0;
        gameCanvas.style.cursor = 'grabbing';
    }
}

function handleMouseMove(e) {
    if (camera.isDragging) {
        const deltaX = e.clientX - camera.lastMouseX;
        const deltaY = e.clientY - camera.lastMouseY;
        
        // Update velocity for momentum
        camera.velocityX = -deltaX * 0.3;
        camera.velocityY = deltaY * 0.3;
        
        // Update target camera position
        camera.targetX -= deltaX / camera.zoom;
        camera.targetY += deltaY / camera.zoom;
        
        camera.lastMouseX = e.clientX;
        camera.lastMouseY = e.clientY;
    }
}

function handleMouseUp(e) {
    camera.isDragging = false;
    gameCanvas.style.cursor = 'grab';
}

let animationId = null;

// Smooth camera animation loop
function startCameraAnimation() {
    function animate() {
        updateCamera();
        
        // Render the scene continuously for smooth camera movement
        if (currentRenderer && rendererSelect.value === 'webgl3d') {
            const gameContext = {
                terrain: terrain,
                units: bots,
                buildings: []
            };
            currentRenderer.render(gameContext);
            renderTraitOverlays(); // Add trait rendering for close-up viewing
        }
        
        animationId = requestAnimationFrame(animate);
    }
    animate();
}

function stopCameraAnimation() {
    if (animationId) {
        cancelAnimationFrame(animationId);
        animationId = null;
    }
}

// Smooth camera interpolation function
function updateCamera() {
    if (currentRenderer && rendererSelect.value === 'webgl3d') {
        // Process keyboard input for WASD movement
        processKeyboardInput();
        
        // Calculate dynamic camera angle based on zoom
        calculateDynamicCameraAngle();
        
        // Apply momentum when not dragging
        if (!camera.isDragging) {
            camera.targetX += camera.velocityX / camera.zoom;
            camera.targetY += camera.velocityY / camera.zoom;
            camera.velocityX *= MOMENTUM_DECAY;
            camera.velocityY *= MOMENTUM_DECAY;
        }
        
        // Smooth interpolation towards target
        camera.x += (camera.targetX - camera.x) * CAMERA_SMOOTHING;
        camera.y += (camera.targetY - camera.y) * CAMERA_SMOOTHING;
        camera.zoom += (camera.targetZoom - camera.zoom) * CAMERA_SMOOTHING;
        camera.angle += (camera.targetAngle - camera.angle) * CAMERA_SMOOTHING;
        camera.rotation += (camera.targetRotation - camera.rotation) * CAMERA_SMOOTHING;
        
        // Update renderer camera with angle and rotation
        currentRenderer.updateCamera({
            x: camera.x,
            y: camera.y,
            zoom: camera.zoom,
            angle: camera.angle,
            rotation: camera.rotation,
            canvasWidth: gameCanvas.width,
            canvasHeight: gameCanvas.height
        });
    }
}

// Introspective trait renderer for close-up viewing
function renderTraitOverlays() {
    if (camera.zoom > 8.0) { // Show traits when zoomed in close
        const ctx = gameCanvas.getContext('2d');
        
        bots.forEach(bot => {
            // Convert world position to screen position
            const screenX = (bot.x * TILE_SIZE - camera.x) * camera.zoom + gameCanvas.width / 2;
            const screenY = (bot.y * TILE_SIZE - camera.y) * camera.zoom + gameCanvas.height / 2;
            
            // Only render traits for visible units
            if (screenX > -100 && screenX < gameCanvas.width + 100 &&
                screenY > -100 && screenY < gameCanvas.height + 100) {
                
                renderUnitTraits(ctx, bot, screenX, screenY);
            }
        });
    }
}

function renderUnitTraits(ctx, unit, screenX, screenY) {
    // Save context
    ctx.save();
    
    // Semi-transparent background
    ctx.fillStyle = 'rgba(0, 0, 0, 0.8)';
    const boxWidth = 200;
    const boxHeight = 120;
    const boxX = screenX + 40;
    const boxY = screenY - boxHeight / 2;
    
    // Draw rounded rectangle background
    ctx.beginPath();
    ctx.roundRect(boxX, boxY, boxWidth, boxHeight, 8);
    ctx.fill();
    
    // Border
    ctx.strokeStyle = unit.team === 'blue' ? '#4A90E2' : '#E24A4A';
    ctx.lineWidth = 2;
    ctx.stroke();
    
    // Text styling
    ctx.fillStyle = '#FFFFFF';
    ctx.font = '14px Arial';
    ctx.textAlign = 'left';
    
    // Unit information
    const info = [
        `${unit.type.name}`,
        `Team: ${unit.team.toUpperCase()}`,
        `HP: ${unit.hp}/100`,
        `Position: (${unit.x}, ${unit.y})`,
        `Status: ${unit.action.toUpperCase()}`,
        `ID: #${unit.id}`
    ];
    
    // Draw each line of information
    info.forEach((line, index) => {
        ctx.fillText(line, boxX + 10, boxY + 20 + (index * 16));
    });
    
    // Status indicator
    ctx.beginPath();
    ctx.arc(boxX + boxWidth - 15, boxY + 15, 6, 0, Math.PI * 2);
    ctx.fillStyle = unit.action === 'attack' ? '#FF4444' :
                   unit.action === 'move' ? '#44FF44' :
                   unit.action === 'defend' ? '#FFAA44' : '#AAAAAA';
    ctx.fill();
    
    // Connection line from unit to info box
    ctx.strokeStyle = unit.team === 'blue' ? '#4A90E2' : '#E24A4A';
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 5]);
    ctx.beginPath();
    ctx.moveTo(screenX, screenY);
    ctx.lineTo(boxX, boxY + boxHeight / 2);
    ctx.stroke();
    ctx.setLineDash([]);
    
    // Restore context
    ctx.restore();
}

// Initial setup on page load
startSimulation();