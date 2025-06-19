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

        // Gather component values
        const unitTypeName = type.id || type.name || 'default_unit_type'; // Use type.id if available, then type.name
        const stats = type.stats || {}; // Ensure type.stats exists

        const hp = options.hp || stats.health || stats.hp || 100;
        const maxHp = options.maxHp || stats.maxHealth || stats.maxHp || stats.health || 100;

        // Combat stats: prioritize options if provided (e.g. for a unit spawned with temporary buff)
        // then fallback to type stats, then to 0
        const attackDamage = (options.combat && options.combat.damage !== undefined) ? options.combat.damage : (stats.attackDamage || stats.damage || 0);
        const armor = (options.combat && options.combat.armor !== undefined) ? options.combat.armor : (stats.armor || 0);

        const currentShield = (options.combat && options.combat.shield !== undefined) ? options.combat.shield : (stats.shield || 0);
        const maxShield = (options.combat && options.combat.maxShield !== undefined) ? options.combat.maxShield : (stats.maxShield || stats.shield || 0);

        // Energy stats for units (if they have individual energy pools)
        const currentEnergy = (options.energy !== undefined) ? options.energy : (stats.energy || 0);
        const maxEnergy = (options.maxEnergy !== undefined) ? options.maxEnergy : (stats.maxEnergy || stats.energy || 0);

        const computroniumCores = stats.computroniumCores || 0;
        const currentAction = 'idle'; // Default initial action

        // Call EntityManager.addUnit with all component values
        this.simulation.entityManager.addUnit(
            id,
            unitTypeName, // This should be a string identifier like 'commander', 'tank'
            team,
            x, y,
            hp, maxHp,
            attackDamage, armor,
            currentShield, maxShield,
            currentEnergy, maxEnergy,
            computroniumCores,
            currentAction
        );

        return id; // Return only the ID
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
        const id = `building_${this.nextId++}`; // Generate ID

        // Import Building class if not already (assuming it's in the same directory or path resolved)
        // This might require adjusting the import paths at the top of the file if Building is not already imported.
        // For this example, assuming Building class is available.
        // const { Building } = require('./building.js'); // Or appropriate path
        // Note: building.js was already imported in the original file as part of components/index.js,
        // but if it's a direct class, direct import might be cleaner.
        // For now, assuming Building is globally available or correctly imported via existing means.
        // The original file uses `const building_js_1 = require("./building.js");` implicitly via components/index.js.
        // Let's make sure `Building` class is directly available or change this line.
        // For now, assuming `Building` is available in the scope.
        // If `building_js_1.Building` is how it's accessed, that should be used.
        // Looking at the top, `index_js_1` from components is imported. If Building is part of that, it's fine.
        // However, `Unit` was directly imported. Let's assume Building should be too for clarity.
        // This will be a forward declaration if `Building` is not explicitly imported.
        // The original `simulation.js` creates `new Building(...)` directly.

        const building_js_1 = require("./building.js"); // Explicit import for clarity

        const newBuildingInstance = new building_js_1.Building(id, this.simulation, type, x, y, team);

        // Assuming EntityManager has an `addBuilding` method that takes the instance
        // and stores it (e.g., in an array `this.entityManager.buildings.push(newBuildingInstance)`).
        // This part remains consistent with how units were handled before full ECS migration for them.
        if (this.simulation && this.simulation.entityManager && typeof this.simulation.entityManager.addBuilding === 'function') {
            this.simulation.entityManager.addBuilding(newBuildingInstance);
        } else {
            console.warn(`EntityFactory: Could not add building ${id} to entityManager. entityManager or addBuilding method missing.`);
        }

        return id; // Return only the ID
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
