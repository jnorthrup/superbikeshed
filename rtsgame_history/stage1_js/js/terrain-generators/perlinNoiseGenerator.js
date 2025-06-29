import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES, MIN_LAND_PERCENTAGE, MAX_TERRAIN_RETRIES } from '../config/gameConstants.js';

/**
 * Perlin noise terrain generator
 * Generates terrain using Perlin noise for natural-looking landscapes
 */

export class PerlinNoiseGenerator {
    constructor() {
        this.permutation = new Array(512);
        this.gradients = new Array(512);
    }

    initialize(seed) {
        // Initialize permutation table
        const p = new Array(256);
        for (let i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Shuffle using seed
        for (let i = 255; i > 0; i--) {
            const j = Math.floor(seed.random() * (i + 1));
            [p[i], p[j]] = [p[j], p[i]];
        }

        // Extend permutation table
        for (let i = 0; i < 512; i++) {
            this.permutation[i] = p[i & 255];
            this.gradients[i] = this.getGradient(i);
        }
    }

    getGradient(index) {
        const v = (index * 16807) % 2147483647;
        const angle = (v / 2147483647) * Math.PI * 2;
        return {
            x: Math.cos(angle),
            y: Math.sin(angle)
        };
    }

    fade(t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    lerp(a, b, t) {
        return a + t * (b - a);
    }

    dot(g, x, y) {
        return g.x * x + g.y * y;
    }

    noise(x, y) {
        const X = Math.floor(x) & 255;
        const Y = Math.floor(y) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);

        const u = this.fade(x);
        const v = this.fade(y);

        const A = this.permutation[X] + Y;
        const B = this.permutation[X + 1] + Y;

        return this.lerp(
            this.lerp(
                this.dot(this.gradients[this.permutation[A]], x, y),
                this.dot(this.gradients[this.permutation[B]], x - 1, y),
                u
            ),
            this.lerp(
                this.dot(this.gradients[this.permutation[A + 1]], x, y - 1),
                this.dot(this.gradients[this.permutation[B + 1]], x - 1, y - 1),
                u
            ),
            v
        );
    }

    generateTerrain(gameContext) {
        const { width, height, seedRandom } = gameContext;
        this.initialize(seedRandom);

        const terrain = new Array(height);
        const scale = 0.1;
        const octaves = 4;
        const persistence = 0.5;
        const lacunarity = 2.0;

        for (let y = 0; y < height; y++) {
            terrain[y] = new Array(width);
            for (let x = 0; x < width; x++) {
                let amplitude = 1;
                let frequency = 1;
                let noiseHeight = 0;
                let amplitudeSum = 0;

                for (let i = 0; i < octaves; i++) {
                    const sampleX = x * scale * frequency;
                    const sampleY = y * scale * frequency;

                    const perlinValue = this.noise(sampleX, sampleY);
                    noiseHeight += perlinValue * amplitude;
                    amplitudeSum += amplitude;

                    amplitude *= persistence;
                    frequency *= lacunarity;
                }

                // Normalize and map to terrain types
                noiseHeight = noiseHeight / amplitudeSum;
                terrain[y][x] = this.mapToTerrainType(noiseHeight);
            }
        }

        return terrain;
    }

    mapToTerrainType(value) {
        // Map noise value to terrain types
        if (value < 0.2) return 'DEEP_WATER';
        if (value < 0.3) return 'WATER';
        if (value < 0.4) return 'SHALLOW_WATER';
        if (value < 0.5) return 'SAND';
        if (value < 0.7) return 'GRASS';
        if (value < 0.85) return 'FOREST';
        return 'MOUNTAIN';
    }
}

/**
 * Generates terrain using proper Perlin noise with elevation model and sea level.
 * @param {object} gameContext - The game context object, containing seedRandom, terrain, and resourceNodes.
 * @param {number} retries - Current retry count for land percentage.
 */
export function generatePerlinNoiseTerrain(gameContext, retries = 0) {
    console.log("Generating Perlin noise terrain with elevation model...");
    
    const SEA_LEVEL = 0.0;
    const perlin = new PerlinNoiseGenerator();
    
    // Initialize terrain and mobility mesh
    gameContext.terrain = [];
    gameContext.mobilityMesh = [];
    
    for (let x = 0; x < GRID_SIZE; x++) {
        gameContext.terrain[x] = [];
        gameContext.mobilityMesh[x] = [];
        
        for (let y = 0; y < GRID_SIZE; y++) {
            // Generate elevation using multiple octaves of Perlin noise
            const baseElevation = perlin.generateTerrain({
                width: 1,
                height: 1,
                scale: 0.02,
                octaves: 4,
                persistence: 0.5
            })[0][0];
            const detailNoise = perlin.generateTerrain({
                width: 1,
                height: 1,
                scale: 0.08,
                octaves: 2,
                persistence: 0.3
            })[0][0] * 0.3;
            const elevation = baseElevation + detailNoise;
            
            // Determine terrain type based on elevation relative to sea level
            let terrainType;
            if (elevation < SEA_LEVEL - 0.1) {
                terrainType = TERRAIN_TYPES.WATER;
            } else if (elevation > SEA_LEVEL + 0.6) {
                terrainType = TERRAIN_TYPES.MOUNTAIN;
            } else {
                terrainType = TERRAIN_TYPES.LAND;
            }

            gameContext.terrain[x][y] = {
                type: terrainType,
                elevation: elevation
            };

            // Generate mobility mesh - movement cost based on terrain
            let movementCost = 1.0; // Default movement cost
            switch (terrainType) {
                case TERRAIN_TYPES.WATER:
                    movementCost = 999.0; // Very high cost for land units
                    break;
                case TERRAIN_TYPES.MOUNTAIN:
                    movementCost = 3.0; // High cost for mountains
                    break;
                case TERRAIN_TYPES.LAND:
                    const steepness = Math.abs(elevation - SEA_LEVEL);
                    movementCost = 1.0 + steepness * 0.5; // Gradual cost increase with elevation
                    break;
            }

            gameContext.mobilityMesh[x][y] = {
                cost: movementCost,
                passable: terrainType !== TERRAIN_TYPES.WATER
            };
        }
    }

    // Calculate land percentage for validation
    let landTiles = 0;
    for (let x = 0; x < GRID_SIZE; x++) {
        for (let y = 0; y < GRID_SIZE; y++) {
            if (gameContext.terrain[x][y].type === TERRAIN_TYPES.LAND) {
                landTiles++;
            }
        }
    }

    const totalTiles = GRID_SIZE * GRID_SIZE;
    const landPercentage = landTiles / totalTiles;

    console.log(`Terrain generated with ${landPercentage.toFixed(2)} land percentage.`);

    // Validate land percentage
    if (landPercentage < MIN_LAND_PERCENTAGE && retries < MAX_TERRAIN_RETRIES) {
        console.log(`Land percentage ${landPercentage.toFixed(2)} is less than ${MIN_LAND_PERCENTAGE}. Regenerating terrain (attempt ${retries + 1}/${MAX_TERRAIN_RETRIES}).`);
        return generatePerlinNoiseTerrain(gameContext, retries + 1);
    } else if (landPercentage < MIN_LAND_PERCENTAGE) {
        console.warn(`Failed to achieve minimum land percentage after ${MAX_TERRAIN_RETRIES} attempts. Current land: ${landPercentage.toFixed(2)}. Using current terrain anyway.`);
    }

    // Generate resource nodes on land tiles
    if (!gameContext.resourceNodes) {
        gameContext.resourceNodes = [];
    }
    
    // Place some resource nodes on suitable land tiles
    const resourceDensity = 0.002; // 0.2% of land tiles get resources
    const maxResources = Math.floor(landTiles * resourceDensity);
    let resourcesPlaced = 0;

    for (let attempts = 0; attempts < maxResources * 10 && resourcesPlaced < maxResources; attempts++) {
        const x = Math.floor(gameContext.seedRandom ? gameContext.seedRandom.random() * GRID_SIZE : Math.random() * GRID_SIZE);
        const y = Math.floor(gameContext.seedRandom ? gameContext.seedRandom.random() * GRID_SIZE : Math.random() * GRID_SIZE);
        
        if (gameContext.terrain[x] && gameContext.terrain[x][y] &&
            gameContext.terrain[x][y].type === TERRAIN_TYPES.LAND &&
            gameContext.terrain[x][y].elevation > SEA_LEVEL + 0.1) { // Only on elevated land
            
            // Check if resource already exists nearby
            let tooClose = false;
            for (const existing of gameContext.resourceNodes) {
                const dx = existing.x - x;
                const dy = existing.y - y;
                if (dx * dx + dy * dy < 100) { // Minimum distance between resources
                    tooClose = true;
                    break;
                }
            }
            
            if (!tooClose) {
                gameContext.terrain[x][y].type = TERRAIN_TYPES.RESOURCE;
                gameContext.resourceNodes.push({
                    x: x,
                    y: y,
                    type: 'mass', // Default resource type
                    amount: 1000
                });
                resourcesPlaced++;
            }
        }
    }

    console.log(`Placed ${resourcesPlaced} resource nodes on terrain.`);
    console.log("Terrain generated using perlinNoiseGenerator.");
}

// Helper function for generating fallback terrain with proper structure
function generateDefaultTerrain() {
    const defaultTerrain = [];
    for (let x = 0; x < GRID_SIZE; x++) {
        defaultTerrain[x] = [];
        for (let y = 0; y < GRID_SIZE; y++) {
            // Create proper terrain objects with type and elevation
            defaultTerrain[x][y] = {
                type: TERRAIN_TYPES.LAND,
                elevation: 0.1
            };
        }
    }
    return defaultTerrain;
}