"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EntityFactory = void 0;
const index_js_1 = require("./components/index.js");
// Import resource manager functions and types
const resourceManager_js_1 = require("./resourceManager.js");
// Assume gameState instance is accessible, e.g., via a global or passed in.
// For this example, let's assume a global `window.gameStateObject` for simplicity,
// though in a real app, it would be passed via constructor or context.
// import { gameState } from './gameState'; // This would be circular if gameState uses EntityFactory.
class EntityFactory {
    constructor(simulation) {
        this.nextId = 1;
        this.simulation = simulation; // Store reference to simulation to access gameState
    }
    createUnit(type, team, x, y, options = {}) {
        var _a, _b, _c;
        // TODO: Get actual cost for this entity/action (e.g., from unitTypes.js or buildingTypes.js)
        const costMass = ((_a = type.cost) === null || _a === void 0 ? void 0 : _a.mass) || 50; // Placeholder from type definition
        const costEnergy = ((_b = type.cost) === null || _b === void 0 ? void 0 : _b.energy) || 25; // Placeholder
        const costComputronium = ((_c = type.cost) === null || _c === void 0 ? void 0 : _c.computronium) || 0;
        // Determine acting player ID (e.g., based on 'team')
        // Placeholder: assume 'blue' is player 0, 'red' is player 1
        const playerId = team === 'blue' ? 0 : 1;
        if (this.simulation && this.simulation.gameState) {
            let currentPlayerResources = this.simulation.gameState.getPlayerResourcesState();
            const currentMass = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Mass);
            const currentEnergy = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Energy);
            const currentComputronium = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Computronium);
            if (currentMass >= costMass && currentEnergy >= costEnergy && currentComputronium >= costComputronium) {
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Mass, -costMass);
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Energy, -costEnergy);
                if (costComputronium > 0) {
                    currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Computronium, -costComputronium);
                }
                this.simulation.gameState.setPlayerResourcesState(currentPlayerResources);
                console.log(`Player ${playerId} (${team}) paid for unit ${type.name}: ${costMass} Mass, ${costEnergy} Energy, ${costComputronium} Computronium`);
            }
            else {
                console.log(`Player ${playerId} (${team}) cannot afford unit ${type.name}. Has M:${currentMass}/E:${currentEnergy}/C:${currentComputronium}, Needs M:${costMass}/E:${costEnergy}/C:${costComputronium}`);
                return null; // Cannot afford
            }
        }
        else {
            console.warn("EntityFactory: gameState not available for resource checking. Proceeding without cost.");
        }
        const id = `unit_${this.nextId++}`;
        return Object.assign({ id, identifier: new index_js_1.IdentifierComponent(id, type, team), position: new index_js_1.PositionComponent(x, y), health: new index_js_1.HealthComponent(options.hp || 100, options.maxHp || 100), movement: {
                speed: options.speed || 1,
                target: null,
                path: []
            }, combat: options.combat ? {
                target: null,
                attackRange: options.attackRange || 100,
                damage: options.damage || 10,
                cooldown: options.cooldown || 1,
                lastFireTime: 0
            } : null, type,
            team }, options);
    }
    createBuilding(type, team, x, y, options = {}) {
        var _a, _b, _c;
        // TODO: Get actual cost for this entity/action (e.g., from buildingTypes.js)
        const costMass = ((_a = type.cost) === null || _a === void 0 ? void 0 : _a.mass) || 100; // Placeholder from type definition
        const costEnergy = ((_b = type.cost) === null || _b === void 0 ? void 0 : _b.energy) || 50;
        const costComputronium = ((_c = type.cost) === null || _c === void 0 ? void 0 : _c.computronium) || 0;
        const playerId = team === 'blue' ? 0 : 1;
        if (this.simulation && this.simulation.gameState) {
            let currentPlayerResources = this.simulation.gameState.getPlayerResourcesState();
            const currentMass = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Mass);
            const currentEnergy = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Energy);
            const currentComputronium = (0, resourceManager_js_1.getPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Computronium);
            if (currentMass >= costMass && currentEnergy >= costEnergy && currentComputronium >= costComputronium) {
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Mass, -costMass);
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Energy, -costEnergy);
                if (costComputronium > 0) {
                    currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Computronium, -costComputronium);
                }
                this.simulation.gameState.setPlayerResourcesState(currentPlayerResources);
                console.log(`Player ${playerId} (${team}) paid for building ${type.name}: ${costMass} Mass, ${costEnergy} Energy, ${costComputronium} Computronium`);
            }
            else {
                console.log(`Player ${playerId} (${team}) cannot afford building ${type.name}. Has M:${currentMass}/E:${currentEnergy}/C:${currentComputronium}, Needs M:${costMass}/E:${costEnergy}/C:${costComputronium}`);
                return null; // Cannot afford
            }
        }
        else {
            console.warn("EntityFactory: gameState not available for resource checking. Proceeding without cost.");
        }
        const id = `building_${this.nextId++}`;
        return Object.assign({ id, identifier: new index_js_1.IdentifierComponent(id, type, team), position: new index_js_1.PositionComponent(x, y), health: new index_js_1.HealthComponent(options.hp || 200, options.maxHp || 200), production: options.production ? {
                queue: [],
                progress: 0,
                produces: options.produces || []
            } : null, resource: options.resource ? {
                type: options.resourceType || 'mass',
                amountPerTick: options.amountPerTick || 2,
                lastTickTime: 0
            } : null, type,
            team }, options);
    }
    createResourceNode(type, x, y, options = {}) {
        const id = `resource_${this.nextId++}`;
        return Object.assign({ id, identifier: new index_js_1.IdentifierComponent(id, type, 'neutral'), position: new index_js_1.PositionComponent(x, y), resource: {
                type: options.resourceType || 'mass',
                amount: options.amount || 1000,
                hasExtractor: false
            }, type }, options);
    }
    createProjectile(source, target, options = {}) {
        const id = `projectile_${this.nextId++}`;
        return Object.assign({ id, identifier: new index_js_1.IdentifierComponent(id, 'projectile', source.team), position: new index_js_1.PositionComponent(source.position.x, source.position.y), target: {
                x: target.position.x,
                y: target.position.y
            }, damage: options.damage || 10, speed: options.speed || 200, createdAt: performance.now() }, options);
    }
    createEffect(type, x, y, options = {}) {
        const id = `effect_${this.nextId++}`;
        return Object.assign({ id, identifier: new index_js_1.IdentifierComponent(id, type, 'neutral'), position: new index_js_1.PositionComponent(x, y), duration: options.duration || 1, createdAt: performance.now() }, options);
    }
}
exports.EntityFactory = EntityFactory;
