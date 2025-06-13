/**
 * Island terrain generator
 * Generates terrain with a central landmass surrounded by water
 */

import { PerlinNoiseGenerator } from './perlinNoiseGenerator.js';

export class IslandGenerator {
    constructor() {
        this.perlinGenerator = new PerlinNoiseGenerator();
    }

    generateTerrain(gameContext) {
        const { width, height, seedRandom } = gameContext;
        this.perlinGenerator.initialize(seedRandom);

        const terrain = new Array(height);
        const centerX = width / 2;
        const centerY = height / 2;
        const maxDistance = Math.sqrt(centerX * centerX + centerY * centerY);

        // Generate base noise
        for (let y = 0; y < height; y++) {
            terrain[y] = new Array(width);
            for (let x = 0; x < width; x++) {
                // Calculate distance from center
                const dx = x - centerX;
                const dy = y - centerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                const normalizedDistance = distance / maxDistance;

                // Generate noise
                const noiseX = x * 0.1;
                const noiseY = y * 0.1;
                const noiseValue = this.perlinGenerator.noise(noiseX, noiseY);

                // Create island shape using distance falloff
                const falloff = this.calculateFalloff(normalizedDistance);
                const combinedValue = (noiseValue + 1) * 0.5 * falloff;

                terrain[y][x] = this.mapToTerrainType(combinedValue);
            }
        }

        // Add coastal features
        this.addCoastalFeatures(terrain);

        return terrain;
    }

    calculateFalloff(distance) {
        // Smooth falloff function
        const a = 3;
        const b = 2.2;
        return Math.pow(Math.max(0, 1 - Math.pow(distance, a)), b);
    }

    addCoastalFeatures(terrain) {
        const height = terrain.length;
        const width = terrain[0].length;

        // Add beaches and shallow water
        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                if (terrain[y][x] === 'WATER') {
                    // Check surrounding tiles
                    const hasLand = this.hasAdjacentLand(terrain, x, y);
                    if (hasLand) {
                        terrain[y][x] = 'SHALLOW_WATER';
                    }
                } else if (terrain[y][x] === 'GRASS') {
                    // Check surrounding tiles
                    const hasWater = this.hasAdjacentWater(terrain, x, y);
                    if (hasWater) {
                        terrain[y][x] = 'SAND';
                    }
                }
            }
        }
    }

    hasAdjacentLand(terrain, x, y) {
        const height = terrain.length;
        const width = terrain[0].length;
        const directions = [
            [-1, -1], [0, -1], [1, -1],
            [-1, 0],           [1, 0],
            [-1, 1],  [0, 1],  [1, 1]
        ];

        for (const [dx, dy] of directions) {
            const newX = x + dx;
            const newY = y + dy;
            if (newX >= 0 && newX < width && newY >= 0 && newY < height) {
                if (terrain[newY][newX] !== 'WATER' && 
                    terrain[newY][newX] !== 'SHALLOW_WATER' && 
                    terrain[newY][newX] !== 'DEEP_WATER') {
                    return true;
                }
            }
        }
        return false;
    }

    hasAdjacentWater(terrain, x, y) {
        const height = terrain.length;
        const width = terrain[0].length;
        const directions = [
            [-1, -1], [0, -1], [1, -1],
            [-1, 0],           [1, 0],
            [-1, 1],  [0, 1],  [1, 1]
        ];

        for (const [dx, dy] of directions) {
            const newX = x + dx;
            const newY = y + dy;
            if (newX >= 0 && newX < width && newY >= 0 && newY < height) {
                if (terrain[newY][newX] === 'WATER' || 
                    terrain[newY][newX] === 'SHALLOW_WATER' || 
                    terrain[newY][newX] === 'DEEP_WATER') {
                    return true;
                }
            }
        }
        return false;
    }

    mapToTerrainType(value) {
        // Map value to terrain types
        if (value < 0.2) return 'DEEP_WATER';
        if (value < 0.3) return 'WATER';
        if (value < 0.4) return 'SHALLOW_WATER';
        if (value < 0.5) return 'SAND';
        if (value < 0.7) return 'GRASS';
        if (value < 0.85) return 'FOREST';
        return 'MOUNTAIN';
    }
} 