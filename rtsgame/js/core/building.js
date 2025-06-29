import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { UNIT_TYPES } from '../config/unitTypes.js';
import { TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { findLandPosition } from './terrain.js';
import { Unit } from './unit.js'; // Added import
import { Caption } from './entities/caption.js'; // Added import
// Global variables like 'resources', 'units', 'captions', 'addEvent'
// are accessed directly. This tight coupling will be addressed in later refactoring.

class Building {
    constructor(x, y, team, type, simulation = null) { // Renamed gameContext to simulation
        this.x = x;
        this.y = y;
        this.team = team;
        this.type = type;
        this.hp = type.maxHp;
        this.maxHp = type.maxHp;
        this.productionQueue = [];
        this.productionProgress = 0;
        this.setRallyPoint(x, y, simulation);
        this.shields = 0;
        this.maxShields = 0;
        this.captionCooldown = 0;
        
        // Add Computronium core if this building type has one
        if (type.hasComputroniumCore && simulation && simulation.computroniumManagers) {
            const manager = simulation.computroniumManagers[team];
            if (manager) {
                this.computroniumCore = manager.addCore(this, type.coreEfficiency || 1.0);
                console.log(`[Building] Added Computronium core to ${type.name}`);
            }
        }
        
        // Register in command hierarchy if simulation supports it
        if (simulation && simulation.commandHierarchies && type.commandRank) {
            const hierarchy = simulation.commandHierarchies[team];
            if (hierarchy) {
                this.commandNode = hierarchy.registerEntity(this, type.commandRank);
                console.log(`[Building] Registered ${type.name} in command hierarchy (rank ${type.commandRank})`);
            }
        }
    }

    setRallyPoint(buildingX, buildingY, simulation) { // Renamed parameter for clarity
        // Try to find a valid land position for rally point
        // Access terrain directly from the simulation instance
        const terrain = simulation ? simulation.terrain : null; 
        if (terrain) {
            const rallyPositions = [
                { x: buildingX + 100, y: buildingY },     // Right
                { x: buildingX - 100, y: buildingY },     // Left  
                { x: buildingX, y: buildingY + 100 },     // Down
                { x: buildingX, y: buildingY - 100 },     // Up
                { x: buildingX + 70, y: buildingY + 70 }, // Diagonal
                { x: buildingX - 70, y: buildingY - 70 }  // Diagonal
            ];

            for (const pos of rallyPositions) {
                const tileX = Math.floor(pos.x / TILE_SIZE);
                const tileY = Math.floor(pos.y / TILE_SIZE);
                
                if (tileX >= 0 && tileX < GRID_SIZE && tileY >= 0 && tileY < GRID_SIZE &&
                    terrain[tileX] && terrain[tileX][tileY] === TERRAIN_TYPES.LAND) {
                    this.rallyx = pos.x;
                    this.rallyy = pos.y;
                    return;
                }
            }
        }
        
        // Fallback to building position if no valid rally point found
        this.rallyx = buildingX;
        this.rallyy = buildingY;
    }

    update(simulation, deltaTime) {
        const { entityManager, gameState, seedRandom } = simulation; // Destructure main components from simulation
        const { resources, addEvent } = gameState;

        // Resource generation
        if (this.type.resourceGeneration) {
            resources[this.team][this.type.resourceGeneration.type] += this.type.resourceGeneration.amount;
            resources[this.team][this.type.resourceGeneration.type + 'Income'] =
                this.type.resourceGeneration.amount * 60; 
        }

        // Auto-production with resource check
        if (this.type.produces && this.productionQueue.length === 0) {
            const unitTypesToBuild = this.type.produces.filter(unitType => {
                return resources[this.team].mass >= (unitType.cost?.mass || 0) &&
                       resources[this.team].energy >= (unitType.cost?.energy || 0) &&
                       resources[this.team].computronium >= (unitType.cost?.computronium || 0);
            });

            if (unitTypesToBuild.length > 0 && seedRandom && seedRandom.random() < 0.02) { 
                const unitType = unitTypesToBuild[Math.floor(seedRandom.random() * unitTypesToBuild.length)]; 
                this.productionQueue.push(unitType);

                if (unitType.cost) {
                    resources[this.team].mass -= unitType.cost.mass || 0;
                    resources[this.team].energy -= unitType.cost.energy || 0;
                    resources[this.team].computronium -= unitType.cost.computronium || 0;
                }
            }
        }

        // Production progress
        if (this.productionQueue.length > 0) {
            // Assuming buildTime is in seconds, deltaTime is in seconds.
            // Production progress should accumulate towards buildTime.
            this.productionProgress += deltaTime; 

            if (this.captionCooldown <= 0 && seedRandom && seedRandom.random() < 0.01) {
                const progressPercent = Math.floor((this.productionProgress / this.productionQueue[0].buildTime) * 100);
                entityManager.addCaption(new Caption(this.x, this.y - this.type.size,
                    `Building ${this.productionQueue[0].name} ${progressPercent}%`, '#ff0', 10));
                this.captionCooldown = 1.5; // Cooldown in seconds
            }

            if (this.productionProgress >= this.productionQueue[0].buildTime) {
                const unitType = this.productionQueue.shift();
                const newUnit = new Unit(this.rallyx, this.rallyy, this.team, unitType, simulation);
                entityManager.addUnit(newUnit);
                this.productionProgress = 0;

                entityManager.addCaption(new Caption(this.x, this.y - this.type.size,
                   `${unitType.name} ready!`, '#0f0', 12));

                if (unitType.name === 'Experimental' || unitType.tier === 3) {
                    const event = addEvent('build', 
                        `${this.team.toUpperCase()} completed ${unitType.name}!`, 3);
                    event.position = { x: this.x, y: this.y };
                }
            }
        }

        if (this.captionCooldown > 0) {
            this.captionCooldown -= deltaTime;
        }
    }

    // Removed duplicated cooldown line from here

    draw(ctx, camera) {
        // TILE_SIZE is now imported and available if needed for calculations.
        // Current drawing is based on this.type.size which is world units.
        const screenX = (this.x - camera.x) * camera.zoom + camera.canvasWidth / 2; // Assuming camera has canvasWidth
        const screenY = (this.y - camera.y) * camera.zoom + camera.canvasHeight / 2; // Assuming camera has canvasHeight
        const size = this.type.size * camera.zoom;

        if (screenX < -size || screenX > camera.canvasWidth + size ||
            screenY < -size || screenY > camera.canvasHeight + size) {
            return;
        }

        // Building base
        ctx.fillStyle = this.team === 'blue' ? '#226' : '#622';
        ctx.fillRect(screenX - size / 2, screenY - size / 2, size, size);

        // Building type
        ctx.fillStyle = this.type.color;
        ctx.fillRect(screenX - size / 3, screenY - size / 3, size * 2 / 3, size * 2 / 3);

        // Resource extractor animation
        if (this.type.resourceGeneration) {
            ctx.save();
            ctx.translate(screenX, screenY);
            ctx.rotate(Date.now() * 0.001);
            ctx.strokeStyle = this.type.resourceGeneration.type === 'mass' ? '#888' : '#ff0';
            ctx.lineWidth = 2;
            ctx.beginPath();
            for (let i = 0; i < 4; i++) {
                const angle = (i / 4) * Math.PI * 2;
                ctx.moveTo(0, 0);
                ctx.lineTo(Math.cos(angle) * size * 0.4, Math.sin(angle) * size * 0.4);
            }
            ctx.stroke();
            ctx.restore();
        }

        // Health bar
        if (this.hp < this.maxHp) {
            ctx.fillStyle = '#000';
            ctx.fillRect(screenX - size / 2, screenY - size / 2 - 10, size, 5);
            ctx.fillStyle = '#0f0';
            ctx.fillRect(screenX - size / 2, screenY - size / 2 - 10, size * (this.hp / this.maxHp), 5);
        }

        // Production progress
        if (this.productionQueue.length > 0) {
            ctx.fillStyle = '#ff0';
            ctx.fillRect(screenX - size / 2, screenY + size / 2 + 5,
                size * (this.productionProgress / this.productionQueue[0].buildTime), 3);
        }

        // Commander indicator (Note: Commander is a Unit, defined in UNIT_TYPES.commander)
        // The check for BUILDING_TYPES.commander was removed as it's undefined.
        // If a specific "Command Center" building type is added later, its drawing logic would go here,
        // referencing its own type from BUILDING_TYPES.
    }
}

export { Building };
