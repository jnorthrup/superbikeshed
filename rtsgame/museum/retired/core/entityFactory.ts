import { 
    IdentifierComponent, 
    PositionComponent, 
    HealthComponent 
} from './components/index.js';
// Import resource manager functions and types
import { addPlayerResource, getPlayerResource, PlayerResourceType } from './resourceManager.js';
// Assume gameState instance is accessible, e.g., via a global or passed in.
// For this example, let's assume a global `window.gameStateObject` for simplicity,
// though in a real app, it would be passed via constructor or context.
// import { gameState } from './gameState'; // This would be circular if gameState uses EntityFactory.

export class EntityFactory {
    constructor(simulation) { // Expect simulation or gameState to be passed for resource access
        this.nextId = 1;
        this.simulation = simulation; // Store reference to simulation to access gameState
    }

    createUnit(type, team, x, y, options = {}) {
        // TODO: Get actual cost for this entity/action (e.g., from unitTypes.js or buildingTypes.js)
        const costMass = type.cost?.mass || 50; // Placeholder from type definition
        const costEnergy = type.cost?.energy || 25; // Placeholder
        const costComputronium = type.cost?.computronium || 0;

        // Determine acting player ID (e.g., based on 'team')
        // Placeholder: assume 'blue' is player 0, 'red' is player 1
        const playerId = team === 'blue' ? 0 : 1;

        if (this.simulation && this.simulation.gameState) {
            let currentPlayerResources = this.simulation.gameState.getPlayerResourcesState();
            const currentMass = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Mass);
            const currentEnergy = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Energy);
            const currentComputronium = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Computronium);

            if (currentMass >= costMass && currentEnergy >= costEnergy && currentComputronium >= costComputronium) {
              currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Mass, -costMass);
              currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Energy, -costEnergy);
              if (costComputronium > 0) {
                currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Computronium, -costComputronium);
              }
              this.simulation.gameState.setPlayerResourcesState(currentPlayerResources);
              console.log(`Player ${playerId} (${team}) paid for unit ${type.name}: ${costMass} Mass, ${costEnergy} Energy, ${costComputronium} Computronium`);
            } else {
              console.log(`Player ${playerId} (${team}) cannot afford unit ${type.name}. Has M:${currentMass}/E:${currentEnergy}/C:${currentComputronium}, Needs M:${costMass}/E:${costEnergy}/C:${costComputronium}`);
              return null; // Cannot afford
            }
        } else {
            console.warn("EntityFactory: gameState not available for resource checking. Proceeding without cost.");
        }

        const id = `unit_${this.nextId++}`;
        
        return {
            id,
            identifier: new IdentifierComponent(id, type, team),
            position: new PositionComponent(x, y),
            health: new HealthComponent(
                options.hp || 100,
                options.maxHp || 100
            ),
            movement: {
                speed: options.speed || 1,
                target: null,
                path: []
            },
            combat: options.combat ? {
                target: null,
                attackRange: options.attackRange || 100,
                damage: options.damage || 10,
                cooldown: options.cooldown || 1,
                lastFireTime: 0
            } : null,
            type,
            team,
            ...options
        };
    }

    createBuilding(type, team, x, y, options = {}) {
        // TODO: Get actual cost for this entity/action (e.g., from buildingTypes.js)
        const costMass = type.cost?.mass || 100; // Placeholder from type definition
        const costEnergy = type.cost?.energy || 50;
        const costComputronium = type.cost?.computronium || 0;

        const playerId = team === 'blue' ? 0 : 1;

        if (this.simulation && this.simulation.gameState) {
            let currentPlayerResources = this.simulation.gameState.getPlayerResourcesState();
            const currentMass = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Mass);
            const currentEnergy = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Energy);
            const currentComputronium = getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Computronium);

            if (currentMass >= costMass && currentEnergy >= costEnergy && currentComputronium >= costComputronium) {
              currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Mass, -costMass);
              currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Energy, -costEnergy);
              if (costComputronium > 0) {
                currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Computronium, -costComputronium);
              }
              this.simulation.gameState.setPlayerResourcesState(currentPlayerResources);
              console.log(`Player ${playerId} (${team}) paid for building ${type.name}: ${costMass} Mass, ${costEnergy} Energy, ${costComputronium} Computronium`);
            } else {
              console.log(`Player ${playerId} (${team}) cannot afford building ${type.name}. Has M:${currentMass}/E:${currentEnergy}/C:${currentComputronium}, Needs M:${costMass}/E:${costEnergy}/C:${costComputronium}`);
              return null; // Cannot afford
            }
        } else {
            console.warn("EntityFactory: gameState not available for resource checking. Proceeding without cost.");
        }

        const id = `building_${this.nextId++}`;
        
        return {
            id,
            identifier: new IdentifierComponent(id, type, team),
            position: new PositionComponent(x, y),
            health: new HealthComponent(
                options.hp || 200,
                options.maxHp || 200
            ),
            production: options.production ? {
                queue: [],
                progress: 0,
                produces: options.produces || []
            } : null,
            resource: options.resource ? {
                type: options.resourceType || 'mass',
                amountPerTick: options.amountPerTick || 2,
                lastTickTime: 0
            } : null,
            type,
            team,
            ...options
        };
    }

    createResourceNode(type, x, y, options = {}) {
        const id = `resource_${this.nextId++}`;
        
        return {
            id,
            identifier: new IdentifierComponent(id, type, 'neutral'),
            position: new PositionComponent(x, y),
            resource: {
                type: options.resourceType || 'mass',
                amount: options.amount || 1000,
                hasExtractor: false
            },
            type,
            ...options
        };
    }

    createProjectile(source, target, options = {}) {
        const id = `projectile_${this.nextId++}`;
        
        return {
            id,
            identifier: new IdentifierComponent(id, 'projectile', source.team),
            position: new PositionComponent(source.position.x, source.position.y),
            target: {
                x: target.position.x,
                y: target.position.y
            },
            damage: options.damage || 10,
            speed: options.speed || 200,
            createdAt: performance.now(),
            ...options
        };
    }

    createEffect(type, x, y, options = {}) {
        const id = `effect_${this.nextId++}`;
        
        return {
            id,
            identifier: new IdentifierComponent(id, type, 'neutral'),
            position: new PositionComponent(x, y),
            duration: options.duration || 1,
            createdAt: performance.now(),
            ...options
        };
    }
} 