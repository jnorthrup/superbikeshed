import { 
    IdentifierComponent, 
    PositionComponent, 
    HealthComponent 
} from './components/index.js';

export class EntityFactory {
    constructor() {
        this.nextId = 1;
    }

    createUnit(type, team, x, y, options = {}) {
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