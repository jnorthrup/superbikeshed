// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This entire file is deprecated and scheduled for deletion.                    #
// #   The main browser entry point is now js/app.js.                                                 #
// #   This file represented an old WebGL 2D-based rendering and simulation approach.                 #
// #                                                                                                  #
// ####################################################################################################

import { WebGL2DRenderer } from './webgl_2d_renderer.js';
import { TERRAIN_TYPES, TILE_SIZE, GRID_SIZE } from '../config/gameConstants.js';

const canvas = document.getElementById('game-canvas');
const renderer = new WebGL2DRenderer(canvas);

// Define colors for terrain types
const TERRAIN_COLORS = {
    [TERRAIN_TYPES.WATER]: [0.0, 0.2, 0.5, 1.0],    // Blue
    [TERRAIN_TYPES.LAND]: [0.2, 0.6, 0.2, 1.0],     // Green
    [TERRAIN_TYPES.MOUNTAIN]: [0.4, 0.4, 0.4, 1.0]  // Gray
};

// Define colors for bot teams
const TEAM_COLORS = {
    'blue': [0.1, 0.1, 0.8, 1.0], // Dark Blue
    'red': [0.8, 0.1, 0.1, 1.0]   // Dark Red
};

function initializeTerrain(size) {
    const terrain = [];
    for (let x = 0; x < size; x++) {
        terrain[x] = [];
        for (let y = 0; y < size; y++) {
            const rand = Math.random();
            if (rand < 0.2) {
                terrain[x][y] = TERRAIN_TYPES.WATER; // 0
            } else if (rand < 0.4) {
                terrain[x][y] = TERRAIN_TYPES.MOUNTAIN; // 2
            } else {
                terrain[x][y] = TERRAIN_TYPES.LAND; // 1
            }
        }
    }
    return terrain;
}

function updateBot(bot, terrain, bots, time, events) {
    const actionChance = Math.random();
    const otherBot = bots.find(b => b.id !== bot.id);
    const distToOther = Math.sqrt(Math.pow(otherBot.x - bot.x, 2) + Math.pow(otherBot.y - bot.y, 2));

    if (actionChance < 0.3 && distToOther < 3) {
        const damage = Math.floor(Math.random() * 10) + 5;
        otherBot.hp -= damage;
        bot.action = 'attack';
        events.push({
            time,
            message: `Bot ${bot.id} (${bot.team}) attacked Bot ${otherBot.id} for ${damage} damage at (${bot.x},${bot.y})`
        });
    } else if (actionChance < 0.6) {
        const direction = Math.floor(Math.random() * 4);
        let newX = bot.x;
        let newY = bot.y;
        if (direction === 0 && bot.y > 0) newY--;
        else if (direction === 1 && bot.x < terrain.length - 1) newX++;
        else if (direction === 2 && bot.y < terrain.length - 1) newY++;
        else if (direction === 3 && bot.x > 0) newX--;

        if (newX >= 0 && newX < GRID_SIZE && newY >= 0 && newY < GRID_SIZE && terrain[newX][newY] !== TERRAIN_TYPES.WATER) { // Use numerical WATER type
            bot.x = newX;
            bot.y = newY;
            bot.action = 'move';
            events.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) moved to (${bot.x},${bot.y})`
            });
        } else {
            bot.action = 'idle';
        }
    } else {
        bot.action = 'defend';
        if (Math.random() < 0.5) {
            events.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) defended at (${bot.x},${bot.y})`
            });
        }
    }
}

function simulateMatch(duration) {
    let gameTime = 0;
    const events = [];
    const terrain = initializeTerrain(GRID_SIZE);
    const bots = [
        { id: 1, team: 'blue', x: 2, y: 2, hp: 100, action: 'idle', type: {name: 'bot'} }, // Added type for rendering
        { id: 2, team: 'red', x: 7, y: 7, hp: 100, action: 'idle', type: {name: 'bot'} } // Added type for rendering
    ];

    const gameContext = {
        terrain: terrain,
        units: bots, // Treat bots as units for the renderer
        buildings: [] // No buildings in this simulation
    };

    // Initial render
    renderScene(terrain, bots);

    const simulationInterval = setInterval(() => {
        if (gameTime >= duration) {
            clearInterval(simulationInterval);
            console.log('Simulation finished.');
            return;
        }

        gameTime += 1;
        for (const bot of bots) {
            updateBot(bot, terrain, bots, gameTime, events);
        }

        // Re-render the scene with updated bot positions
        renderScene(terrain, bots);

        const timeEvents = events.filter(e => e.time === gameTime);
        for (const event of timeEvents) {
            console.log(`Time ${gameTime}s: ${event.message}`);
        }
    }, 1000);

    function renderScene(terrain, bots) {
        renderer.clear();

        // Render terrain
        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                const terrainType = terrain[x][y];
                const color = TERRAIN_COLORS[terrainType];
                renderer.drawRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE, color);
            }
        }

        // Render bots (as squares for simplicity in 2D WebGL)
        bots.forEach(bot => {
            const botSize = TILE_SIZE * 0.6; // Make bot a bit smaller than a tile
            const botColor = TEAM_COLORS[bot.team];
            const centerX = bot.x * TILE_SIZE + TILE_SIZE / 2;
            const centerY = bot.y * TILE_SIZE + TILE_SIZE / 2;
            renderer.drawRect(centerX - botSize / 2, centerY - botSize / 2, botSize, botSize, botColor);
        });
    }
}

// Run the simulation for 10 seconds
simulateMatch(10);