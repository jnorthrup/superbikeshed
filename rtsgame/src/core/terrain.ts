import { TerrainManager } from './terrainManager.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE } from '../config/gameConstants.js';

const TERRAIN_TYPES = {
    GRASS: { color: '#4CAF50', walkable: true, buildable: true },
    WATER: { color: '#2196F3', walkable: false, buildable: false },
    MOUNTAIN: { color: '#795548', walkable: false, buildable: false },
    FOREST: { color: '#2E7D32', walkable: true, buildable: true },
    SAND: { color: '#FFC107', walkable: true, buildable: true }
};

export class Terrain {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.terrainManager = new TerrainManager(gameContext);
        this.terrain = null;
        this.mobilityMesh = null;
    }

    /**
     * Initialize terrain
     * @param {Object} options - Terrain generation options
     */
    initialize(options = {}) {
        // Generate terrain using current generator
        this.terrain = this.terrainManager.generateTerrain({
            width: GRID_SIZE,
            height: GRID_SIZE,
            ...options
        });

        // Generate mobility mesh
        this.generateMobilityMesh();
    }

    /**
     * Generate mobility mesh for pathfinding
     */
    generateMobilityMesh() {
        this.mobilityMesh = new Array(GRID_SIZE).fill(0).map(() => new Array(GRID_SIZE).fill(0));
        
        for (let y = 0; y < GRID_SIZE; y++) {
            for (let x = 0; x < GRID_SIZE; x++) {
                const terrainType = this.terrain[y][x];
                const properties = this.terrainManager.getTerrainProperties(terrainType);
                this.mobilityMesh[y][x] = properties.walkable ? 1 : 0;
            }
        }
    }

    /**
     * Check if area is clear for building
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @param {number} radius - Check radius
     * @returns {boolean} Whether area is clear
     */
    isAreaClear(x, y, radius) {
        const gridX = Math.floor(x / TILE_SIZE);
        const gridY = Math.floor(y / TILE_SIZE);
        
        for (let dy = -radius; dy <= radius; dy++) {
            for (let dx = -radius; dx <= radius; dx++) {
                const checkX = gridX + dx;
                const checkY = gridY + dy;
                
                if (checkX < 0 || checkX >= GRID_SIZE || checkY < 0 || checkY >= GRID_SIZE) {
                    return false;
                }
                
                const terrainType = this.terrain[checkY][checkX];
                const properties = this.terrainManager.getTerrainProperties(terrainType);
                
                if (!properties.buildable) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Find valid land position
     * @param {number} radius - Required clear radius
     * @returns {Object|null} Valid position or null
     */
    findLandPosition(radius) {
        let attempts = 0;
        const maxAttempts = 100;
        
        while (attempts < maxAttempts) {
            const x = Math.floor(this.gameContext.seedRandom.random() * GRID_SIZE);
            const y = Math.floor(this.gameContext.seedRandom.random() * GRID_SIZE);
            
            if (this.isAreaClear(x * TILE_SIZE, y * TILE_SIZE, radius)) {
                return {
                    x: x * TILE_SIZE,
                    y: y * TILE_SIZE
                };
            }
            
            attempts++;
        }
        
        return null;
    }

    /**
     * Find valid water position
     * @param {number} radius - Required clear radius
     * @returns {Object|null} Valid position or null
     */
    findWaterPosition(radius) {
        let attempts = 0;
        const maxAttempts = 100;
        
        while (attempts < maxAttempts) {
            const x = Math.floor(this.gameContext.seedRandom.random() * GRID_SIZE);
            const y = Math.floor(this.gameContext.seedRandom.random() * GRID_SIZE);
            
            const terrainType = this.terrain[y][x];
            if (terrainType === 0 || terrainType === 1) { // Deep or shallow water
                return {
                    x: x * TILE_SIZE,
                    y: y * TILE_SIZE
                };
            }
            
            attempts++;
        }
        
        return null;
    }

    /**
     * Get terrain type at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {number} Terrain type
     */
    getTerrainType(x, y) {
        const gridX = Math.floor(x / TILE_SIZE);
        const gridY = Math.floor(y / TILE_SIZE);
        
        if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE) {
            return 0; // Default to deep water
        }
        
        return this.terrain[gridY][gridX];
    }

    /**
     * Get terrain properties at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {Object} Terrain properties
     */
    getTerrainProperties(x, y) {
        const terrainType = this.getTerrainType(x, y);
        return this.terrainManager.getTerrainProperties(terrainType);
    }

    /**
     * Check if position is walkable
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {boolean} Whether position is walkable
     */
    isWalkable(x, y) {
        const properties = this.getTerrainProperties(x, y);
        return properties.walkable;
    }

    /**
     * Check if position is buildable
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {boolean} Whether position is buildable
     */
    isBuildable(x, y) {
        const properties = this.getTerrainProperties(x, y);
        return properties.buildable;
    }

    /**
     * Get movement speed modifier at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {number} Movement speed modifier
     */
    getMovementSpeed(x, y) {
        const properties = this.getTerrainProperties(x, y);
        return properties.speed;
    }

    /**
     * Set terrain generator
     * @param {string} generatorName - Generator name
     */
    setGenerator(generatorName) {
        this.terrainManager.setGenerator(generatorName);
    }

    /**
     * Get available terrain generators
     * @returns {Array} List of generator names
     */
    getAvailableGenerators() {
        return this.terrainManager.getAvailableGenerators();
    }

    /**
     * Get current generator name
     * @returns {string} Current generator name
     */
    getCurrentGeneratorName() {
        return this.terrainManager.getCurrentGeneratorName();
    }
}

export function generateTerrain({ terrain, resourceNodes }) {
    // Initialize terrain array
    for (let y = 0; y < GRID_SIZE; y++) {
        terrain[y] = [];
        for (let x = 0; x < GRID_SIZE; x++) {
            terrain[y][x] = TERRAIN_TYPES.GRASS;
        }
    }
    
    // Generate water bodies
    generateWaterBodies(terrain);
    
    // Generate mountains
    generateMountains(terrain);
    
    // Generate forests
    generateForests(terrain);
    
    // Generate resource nodes
    generateResourceNodes(terrain, resourceNodes);
}

function generateWaterBodies(terrain) {
    const numLakes = Math.floor(Math.random() * 3) + 2; // 2-4 lakes
    
    for (let i = 0; i < numLakes; i++) {
        const centerX = Math.floor(Math.random() * GRID_SIZE);
        const centerY = Math.floor(Math.random() * GRID_SIZE);
        const radius = Math.floor(Math.random() * 5) + 3; // 3-7 radius
        
        for (let y = Math.max(0, centerY - radius); y <= Math.min(GRID_SIZE - 1, centerY + radius); y++) {
            for (let x = Math.max(0, centerX - radius); x <= Math.min(GRID_SIZE - 1, centerX + radius); x++) {
                const dx = x - centerX;
                const dy = y - centerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= radius) {
                    terrain[y][x] = TERRAIN_TYPES.WATER;
                }
            }
        }
    }
}

function generateMountains(terrain) {
    const numMountains = Math.floor(Math.random() * 4) + 3; // 3-6 mountains
    
    for (let i = 0; i < numMountains; i++) {
        const centerX = Math.floor(Math.random() * GRID_SIZE);
        const centerY = Math.floor(Math.random() * GRID_SIZE);
        const radius = Math.floor(Math.random() * 4) + 2; // 2-5 radius
        
        for (let y = Math.max(0, centerY - radius); y <= Math.min(GRID_SIZE - 1, centerY + radius); y++) {
            for (let x = Math.max(0, centerX - radius); x <= Math.min(GRID_SIZE - 1, centerX + radius); x++) {
                const dx = x - centerX;
                const dy = y - centerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= radius && terrain[y][x] !== TERRAIN_TYPES.WATER) {
                    terrain[y][x] = TERRAIN_TYPES.MOUNTAIN;
                }
            }
        }
    }
}

function generateForests(terrain) {
    const numForests = Math.floor(Math.random() * 5) + 4; // 4-8 forests
    
    for (let i = 0; i < numForests; i++) {
        const centerX = Math.floor(Math.random() * GRID_SIZE);
        const centerY = Math.floor(Math.random() * GRID_SIZE);
        const radius = Math.floor(Math.random() * 6) + 4; // 4-9 radius
        
        for (let y = Math.max(0, centerY - radius); y <= Math.min(GRID_SIZE - 1, centerY + radius); y++) {
            for (let x = Math.max(0, centerX - radius); x <= Math.min(GRID_SIZE - 1, centerX + radius); x++) {
                const dx = x - centerX;
                const dy = y - centerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= radius && 
                    terrain[y][x] !== TERRAIN_TYPES.WATER && 
                    terrain[y][x] !== TERRAIN_TYPES.MOUNTAIN) {
                    terrain[y][x] = TERRAIN_TYPES.FOREST;
                }
            }
        }
    }
}

function generateResourceNodes(terrain, resourceNodes) {
    const numNodes = Math.floor(Math.random() * 4) + 6; // 6-9 resource nodes
    
    for (let i = 0; i < numNodes; i++) {
        let x, y;
        let attempts = 0;
        
        // Try to find a valid position
        do {
            x = Math.floor(Math.random() * GRID_SIZE);
            y = Math.floor(Math.random() * GRID_SIZE);
            attempts++;
        } while (
            (terrain[y][x] !== TERRAIN_TYPES.GRASS && terrain[y][x] !== TERRAIN_TYPES.SAND) ||
            attempts < 100
        );
        
        if (attempts < 100) {
            resourceNodes.push({
                x: x * (WORLD_SIZE / GRID_SIZE),
                y: y * (WORLD_SIZE / GRID_SIZE),
                type: Math.random() < 0.5 ? 'mass' : 'energy',
                amount: Math.floor(Math.random() * 1000) + 1000 // 1000-2000 resources
            });
        }
    }
}

export function isWalkable(terrain, x, y) {
    const gridX = Math.floor(x / (WORLD_SIZE / GRID_SIZE));
    const gridY = Math.floor(y / (WORLD_SIZE / GRID_SIZE));
    
    if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE) {
        return false;
    }
    
    return terrain[gridY][gridX].walkable;
}

export function isBuildable(terrain, x, y) {
    const gridX = Math.floor(x / (WORLD_SIZE / GRID_SIZE));
    const gridY = Math.floor(y / (WORLD_SIZE / GRID_SIZE));
    
    if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE) {
        return false;
    }
    
    return terrain[gridY][gridX].buildable;
}

// Standalone findLandPosition function for AI use
export function findLandPosition(gameContext, x, y, radius) {
    if (!gameContext.terrain?.terrain) {
        return null;
    }
    
    let attempts = 0;
    const maxAttempts = 100;
    
    while (attempts < maxAttempts) {
        const testX = Math.floor(gameContext.seedRandom.random() * GRID_SIZE);
        const testY = Math.floor(gameContext.seedRandom.random() * GRID_SIZE);
        
        // Check if area around this position is clear
        let clear = true;
        for (let dy = -radius; dy <= radius; dy++) {
            for (let dx = -radius; dx <= radius; dx++) {
                const checkX = testX + dx;
                const checkY = testY + dy;
                
                if (checkX < 0 || checkX >= GRID_SIZE || checkY < 0 || checkY >= GRID_SIZE) {
                    clear = false;
                    break;
                }
                
                const terrainType = gameContext.terrain.terrain[checkY][checkX];
                const properties = gameContext.terrain.terrainManager.getTerrainProperties(terrainType);
                
                if (!properties.buildable) {
                    clear = false;
                    break;
                }
            }
            if (!clear) break;
        }
        
        if (clear) {
            return {
                x: testX * TILE_SIZE,
                y: testY * TILE_SIZE
            };
        }
        
        attempts++;
    }
    
    return null;
}

