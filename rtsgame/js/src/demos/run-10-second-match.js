"use strict";
// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This script is deprecated and scheduled for deletion.                         #
// #   It acts as a simple wrapper for `battle-simulator.js` with a fixed duration.                   #
// #   For running short simulations:                                                                 #
// #     - `battle-simulator.js` can be used directly if its configuration supports duration.         #
// #     - `headless-node.js` can run the main simulation engine for any configured duration.         #
// #                                                                                                  #
// ####################################################################################################
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
!/usr/bin / env;
node;
const battle_simulator_js_1 = require("./battle-simulator.js");
function runMatch() {
    return __awaiter(this, void 0, void 0, function* () {
        const simulator = new battle_simulator_js_1.BattleSimulator();
        // Override createGameContext to ensure seedRandom is available
        simulator.createGameContext = function (units, buildings, resources, gameState, terrain, resourceNodes, simulation) {
            return {
                units, buildings, resources, gameState, terrain, resourceNodes,
                UNIT_TYPES: this.UNIT_TYPES || {},
                BUILDING_TYPES: this.BUILDING_TYPES || {},
                WORLD_SIZE: this.WORLD_SIZE || 1000,
                TILE_SIZE: this.TILE_SIZE || 50,
                GRID_SIZE: this.GRID_SIZE || 20,
                TERRAIN_TYPES: this.TERRAIN_TYPES || { LAND: 'land', WATER: 'water', MOUNTAIN: 'mountain' },
                Unit: this.Unit || function () { },
                Building: this.Building || function () { },
                seedRandom: {
                    random: () => Math.random()
                },
                addEvent: (ctx, type, msg, importance, pos) => {
                    if (importance >= 2) {
                        this.logEvent(simulation, type.toUpperCase(), msg, pos);
                    }
                },
                mainGameGlobals: {
                    resources, units, Unit: this.Unit || function () { }, Building: this.Building || function () { },
                    addEvent: (type, msg) => this.logEvent(simulation, type.toUpperCase(), msg)
                }
            };
        };
        console.log('Running a 10-second bot match...');
        const result = yield simulator.runBattleSimulation({ duration: 10 });
        console.log('Match completed.');
        console.log('Summary:');
        simulator.printSimulationSummary(result);
    });
}
runMatch();
