// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This script is deprecated and scheduled for deletion.                         #
// #   Its functionality for running a short, basic headless match is better covered by:              #
// #     - `headless-node.js` (for tests using the main simulation engine)                            #
// #     - `scenario-tester.js` (for specific simple scenarios with the main simulation engine)       #
// #                                                                                                  #
// ####################################################################################################

#!/usr/bin/env node

console.log('Running a simple 10-second bot match simulation with terrain...');

function simulateMatch(duration) {
    const startTime = Date.now();
    let gameTime = 0;
    const events = [];
    // Simple terrain grid: 10x10 grid with types 'land', 'water', 'mountain'
    const gridSize = 10;
    const terrain = initializeTerrain(gridSize);
    // Two bots starting at different positions
    const bots = [
        { id: 1, team: 'blue', x: 2, y: 2, hp: 100, action: 'idle' },
        { id: 2, team: 'red', x: 7, y: 7, hp: 100, action: 'idle' }
    ];

    console.log('Match started at ' + new Date().toLocaleTimeString());
    console.log('Initial positions: Bot 1 at (2,2) on ' + terrain[2][2] + ', Bot 2 at (7,7) on ' + terrain[7][7]);

    // Simulate a simple battle loop for the specified duration (in seconds)
    while (gameTime < duration) {
        gameTime += 1; // Simulate 1 second per iteration
        // Update each bot's action and position
        for (const bot of bots) {
            updateBot(bot, terrain, bots, gameTime, events);
        }
        // Log any events for this time step
        const timeEvents = events.filter(e => e.time === gameTime);
        for (const event of timeEvents) {
            console.log(`Time ${gameTime}s: ${event.message}`);
        }
    }

    const endTime = Date.now();
    const realDuration = (endTime - startTime) / 1000;

    console.log('Match completed at ' + new Date().toLocaleTimeString());
    console.log(`Real duration: ${realDuration.toFixed(2)} seconds`);
    console.log(`Total events: ${events.length}`);
    console.log(`Final positions: Bot 1 at (${bots[0].x},${bots[0].y}) on ${terrain[bots[0].x][bots[0].y]}, Bot 2 at (${bots[1].x},${bots[1].y}) on ${terrain[bots[1].x][bots[1].y]}`);
}

function initializeTerrain(size) {
    const terrain = [];
    for (let x = 0; x < size; x++) {
        terrain[x] = [];
        for (let y = 0; y < size; y++) {
            const rand = Math.random();
            if (rand < 0.2) {
                terrain[x][y] = 'water';
            } else if (rand < 0.4) {
                terrain[x][y] = 'mountain';
            } else {
                terrain[x][y] = 'land';
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
        // Attack if close to the other bot
        const damage = Math.floor(Math.random() * 10) + 5;
        otherBot.hp -= damage;
        bot.action = 'attack';
        events.push({
            time,
            message: `Bot ${bot.id} (${bot.team}) attacked Bot ${otherBot.id} for ${damage} damage at (${bot.x},${bot.y}) on ${terrain[bot.x][bot.y]}`
        });
    } else if (actionChance < 0.6) {
        // Move to a random adjacent position if possible
        const direction = Math.floor(Math.random() * 4); // 0: up, 1: right, 2: down, 3: left
        let newX = bot.x;
        let newY = bot.y;
        if (direction === 0 && bot.y > 0) newY--;
        else if (direction === 1 && bot.x < terrain.length - 1) newX++;
        else if (direction === 2 && bot.y < terrain.length - 1) newY++;
        else if (direction === 3 && bot.x > 0) newX--;

        // Check if the new position is valid terrain for movement (not water for simplicity)
        if (terrain[newX][newY] !== 'water') {
            bot.x = newX;
            bot.y = newY;
            bot.action = 'move';
            events.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) moved to (${bot.x},${bot.y}) on ${terrain[bot.x][bot.y]}`
            });
        } else {
            bot.action = 'idle';
        }
    } else {
        // Defend or idle
        bot.action = 'defend';
        if (Math.random() < 0.5) {
            events.push({
                time,
                message: `Bot ${bot.id} (${bot.team}) defended at (${bot.x},${bot.y}) on ${terrain[bot.x][bot.y]}`
            });
        }
    }
}

// Run the simulation for 10 seconds
simulateMatch(10);

console.log('Simulation finished.');