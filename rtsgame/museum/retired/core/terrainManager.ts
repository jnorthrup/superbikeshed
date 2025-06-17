// js/core/terrainManager.js

import { getTerrainProperties } from '../config/terrainProperties.js';
import { PerlinNoiseGenerator } from '../terrain-generators/perlinNoiseGenerator.js';
import { IslandGenerator } from '../terrain-generators/islandGenerator.js';

/**
 * Terrain Manager
 * Manages terrain generation and provides a unified interface for terrain operations
 */

export class TerrainManager {
    constructor() {
        this.generators = new Map();
        this.currentGenerator = null;
        this.terrain = null;
        
        // Register default generators
        this.registerGenerator('perlin', new PerlinNoiseGenerator());
        this.registerGenerator('island', new IslandGenerator());
    }

    /**
     * Register a new terrain generator
     * @param {string} name - Generator name
     * @param {Object} generator - Generator instance
     */
    registerGenerator(name, generator) {
        this.generators.set(name, generator);
    }

    /**
     * Set current terrain generator
     * @param {string} name - Generator name
     */
    setGenerator(name) {
        const generator = this.generators.get(name);
        if (!generator) {
            throw new Error(`Terrain generator '${name}' not found`);
        }
        this.currentGenerator = generator;
    }

    /**
     * Generate terrain using current generator
     * @param {Object} gameContext - Game context
     * @returns {Array} Generated terrain
     */
    generateTerrain(gameContext) {
        if (!this.currentGenerator) {
            throw new Error('No terrain generator selected');
        }
        
        this.terrain = this.currentGenerator.generateTerrain(gameContext);
        return this.terrain;
    }

    /**
     * Get terrain properties for a type
     * @param {number} terrainType - Terrain type
     * @returns {Object} Terrain properties
     */
    getTerrainProperties(terrainType) {
        return getTerrainProperties(terrainType);
    }

    /**
     * Get list of available generators
     * @returns {Array} List of generator names
     */
    getAvailableGenerators() {
        return Array.from(this.generators.keys());
    }

    /**
     * Get current generator name
     * @returns {string} Current generator name
     */
    getCurrentGeneratorName() {
        for (const [name, generator] of this.generators) {
            if (generator === this.currentGenerator) {
                return name;
            }
        }
        return null;
    }

    /**
     * Validate terrain grid
     * @param {Array} terrain - Terrain grid to validate
     * @returns {boolean} Whether terrain is valid
     */
    validateTerrain(terrain) {
        if (!Array.isArray(terrain) || terrain.length === 0) {
            return false;
        }

        const width = terrain[0].length;
        for (const row of terrain) {
            if (!Array.isArray(row) || row.length !== width) {
                return false;
            }
            for (const cell of row) {
                if (typeof cell !== 'number' || cell < 0 || cell > 9) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get terrain statistics
     * @param {Array} terrain - Terrain grid
     * @returns {Object} Terrain statistics
     */
    getTerrainStats(terrain) {
        const stats = {
            totalCells: 0,
            terrainCounts: {},
            walkableCells: 0,
            buildableCells: 0,
            resourceYield: 0
        };

        for (const row of terrain) {
            for (const cell of row) {
                stats.totalCells++;
                stats.terrainCounts[cell] = (stats.terrainCounts[cell] || 0) + 1;

                const properties = this.getTerrainProperties(cell);
                if (properties.walkable) stats.walkableCells++;
                if (properties.buildable) stats.buildableCells++;
                stats.resourceYield += properties.resourceYield;
            }
        }

        return stats;
    }

    /**
     * Check if a position is valid for unit placement
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @param {Array} terrain - Terrain grid
     * @returns {boolean} Whether position is valid
     */
    isPositionValid(x, y, terrain) {
        if (!terrain[y] || !terrain[y][x]) {
            return false;
        }
        
        // Check if position is walkable
        const terrainType = terrain[y][x];
        return terrainType >= 2; // 2 = Sand, 3 = Grass, 4 = Forest, 5 = Mountain
    }

    /**
     * Find nearest valid position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @param {Array} terrain - Terrain grid
     * @param {number} maxDistance - Maximum search distance
     * @returns {Object|null} Nearest valid position or null
     */
    findNearestValidPosition(x, y, terrain, maxDistance = 100) {
        if (!terrain) return null;
        
        const width = terrain.length;
        const height = terrain[0].length;
        
        // Search in expanding squares
        for (let d = 0; d <= maxDistance; d++) {
            for (let dx = -d; dx <= d; dx++) {
                for (let dy = -d; dy <= d; dy++) {
                    // Only check positions at current distance
                    if (Math.abs(dx) === d || Math.abs(dy) === d) {
                        const nx = Math.floor(x + dx);
                        const ny = Math.floor(y + dy);
                        
                        if (this.isPositionValid(nx, ny, terrain)) {
                            return { x: nx, y: ny };
                        }
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Get terrain type at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @param {Array} terrain - Terrain grid
     * @returns {number} Terrain type
     */
    getTerrainType(x, y, terrain) {
        if (!terrain[y] || !terrain[y][x]) {
            return 0; // Default to deep water
        }
        return terrain[y][x];
    }

    /**
     * Check if position is valid for unit placement
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {boolean} True if position is valid
     */
    isValidPosition(x, y) {
        if (!this.terrain) return false;
        
        const width = this.terrain.length;
        const height = this.terrain[0].length;
        
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }
        
        const terrainType = this.terrain[x][y];
        return terrainType !== 'WATER' && terrainType !== 'MOUNTAIN';
    }

    /**
     * Get terrain properties at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {Object|null} Terrain properties or null
     */
    getTerrainProperties(x, y) {
        if (!this.terrain) return null;
        
        const width = this.terrain.length;
        const height = this.terrain[0].length;
        
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        
        const terrainType = this.terrain[x][y];
        
        // Define terrain properties
        const properties = {
            WATER: {
                movementCost: Infinity,
                buildable: false,
                passable: false
            },
            SAND: {
                movementCost: 1.5,
                buildable: true,
                passable: true
            },
            GRASS: {
                movementCost: 1.0,
                buildable: true,
                passable: true
            },
            FOREST: {
                movementCost: 2.0,
                buildable: true,
                passable: true
            },
            MOUNTAIN: {
                movementCost: Infinity,
                buildable: false,
                passable: false
            }
        };
        
        return properties[terrainType] || null;
    }

    /**
     * Get terrain type at position
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {string|null} Terrain type or null
     */
    getTerrainType(x, y) {
        if (!this.terrain) return null;
        
        const width = this.terrain.length;
        const height = this.terrain[0].length;
        
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        
        return this.terrain[x][y];
    }

    getTerrain() {
        return this.terrain;
    }

    isAreaClear(x, y, width, height) {
        if (!this.terrain) return false;

        for (let dy = 0; dy < height; dy++) {
            for (let dx = 0; dx < width; dx++) {
                const terrainX = x + dx;
                const terrainY = y + dy;

                if (terrainX < 0 || terrainX >= this.terrain[0].length ||
                    terrainY < 0 || terrainY >= this.terrain.length) {
                    return false;
                }

                const tile = this.terrain[terrainY][terrainX];
                if (tile === 'WATER' || tile === 'DEEP_WATER' || tile === 'MOUNTAIN') {
                    return false;
                }
            }
        }

        return true;
    }

    findLandPosition(width, height, maxAttempts = 100) {
        if (!this.terrain) return null;

        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            const x = Math.floor(Math.random() * (this.terrain[0].length - width));
            const y = Math.floor(Math.random() * (this.terrain.length - height));

            if (this.isAreaClear(x, y, width, height)) {
                return { x, y };
            }
        }

        return null;
    }

    findWaterPosition(width, height, maxAttempts = 100) {
        if (!this.terrain) return null;

        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            const x = Math.floor(Math.random() * (this.terrain[0].length - width));
            const y = Math.floor(Math.random() * (this.terrain.length - height));

            let isWater = true;
            for (let dy = 0; dy < height && isWater; dy++) {
                for (let dx = 0; dx < width && isWater; dx++) {
                    const tile = this.terrain[y + dy][x + dx];
                    if (tile !== 'WATER' && tile !== 'SHALLOW_WATER') {
                        isWater = false;
                    }
                }
            }

            if (isWater) {
                return { x, y };
            }
        }

        return null;
    }

    getTerrainAt(x, y) {
        if (!this.terrain) return null;
        if (x < 0 || x >= this.terrain[0].length || y < 0 || y >= this.terrain.length) {
            return null;
        }
        return this.terrain[y][x];
    }
}