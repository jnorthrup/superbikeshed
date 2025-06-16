// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This script is deprecated and scheduled for deletion.                         #
// #   It acts as a simple wrapper for `battle-simulator.js` with a fixed duration.                   #
// #   For running short simulations:                                                                 #
// #     - `battle-simulator.js` can be used directly if its configuration supports duration.         #
// #     - `headless-node.js` can run the main simulation engine for any configured duration.         #
// #                                                                                                  #
// ####################################################################################################

#!/usr/bin/env node

import { BattleSimulator } from './battle-simulator.js';

async function runMatch() {
    const simulator = new BattleSimulator();
    // Override createGameContext to ensure seedRandom is available
    simulator.createGameContext = function(units, buildings, resources, gameState, terrain, resourceNodes, simulation) {
        return {
            units, buildings, resources, gameState, terrain, resourceNodes,
            UNIT_TYPES: this.UNIT_TYPES || {},
            BUILDING_TYPES: this.BUILDING_TYPES || {},
            WORLD_SIZE: this.WORLD_SIZE || 1000,
            TILE_SIZE: this.TILE_SIZE || 50,
            GRID_SIZE: this.GRID_SIZE || 20,
            TERRAIN_TYPES: this.TERRAIN_TYPES || { LAND: 'land', WATER: 'water', MOUNTAIN: 'mountain' },
            Unit: this.Unit || function() {},
            Building: this.Building || function() {},
            seedRandom: {
                random: () => Math.random()
            },
            addEvent: (ctx, type, msg, importance, pos) => {
                if (importance >= 2) {
                    this.logEvent(simulation, type.toUpperCase(), msg, pos);
                }
            },
            mainGameGlobals: {
                resources, units, Unit: this.Unit || function() {}, Building: this.Building || function() {},
                addEvent: (type, msg) => this.logEvent(simulation, type.toUpperCase(), msg)
            }
        };
    };
    console.log('Running a 10-second bot match...');
    const result = await simulator.runBattleSimulation({ duration: 10 });
    console.log('Match completed.');
    console.log('Summary:');
    simulator.printSimulationSummary(result);
}

runMatch();