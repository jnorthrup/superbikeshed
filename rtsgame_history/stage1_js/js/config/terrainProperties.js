export const TERRAIN_PROPERTIES = {
    // Deep Water
    0: {
        name: 'Deep Water',
        walkable: false,
        buildable: false,
        speed: 0.5,
        resourceYield: 0,
        defenseBonus: 0,
        visionPenalty: 0.5
    },
    // Shallow Water
    1: {
        name: 'Shallow Water',
        walkable: true,
        buildable: false,
        speed: 0.7,
        resourceYield: 0,
        defenseBonus: 0.1,
        visionPenalty: 0.3
    },
    // Sand
    2: {
        name: 'Sand',
        walkable: true,
        buildable: true,
        speed: 0.9,
        resourceYield: 0,
        defenseBonus: 0,
        visionPenalty: 0
    },
    // Grass
    3: {
        name: 'Grass',
        walkable: true,
        buildable: true,
        speed: 1.0,
        resourceYield: 0,
        defenseBonus: 0.1,
        visionPenalty: 0
    },
    // Forest
    4: {
        name: 'Forest',
        walkable: true,
        buildable: false,
        speed: 0.8,
        resourceYield: 1,
        defenseBonus: 0.3,
        visionPenalty: 0.2
    },
    // Mountain
    5: {
        name: 'Mountain',
        walkable: false,
        buildable: false,
        speed: 0,
        resourceYield: 2,
        defenseBonus: 0.5,
        visionPenalty: 0
    },
    // Hill
    6: {
        name: 'Hill',
        walkable: true,
        buildable: true,
        speed: 0.9,
        resourceYield: 1,
        defenseBonus: 0.2,
        visionPenalty: 0
    },
    // Swamp
    7: {
        name: 'Swamp',
        walkable: true,
        buildable: false,
        speed: 0.6,
        resourceYield: 0,
        defenseBonus: 0.2,
        visionPenalty: 0.4
    },
    // Ice
    8: {
        name: 'Ice',
        walkable: true,
        buildable: true,
        speed: 1.2,
        resourceYield: 0,
        defenseBonus: 0,
        visionPenalty: 0.1
    },
    // Lava
    9: {
        name: 'Lava',
        walkable: false,
        buildable: false,
        speed: 0,
        resourceYield: 0,
        defenseBonus: 0,
        visionPenalty: 0
    }
};

// Helper functions for terrain properties
export function getTerrainProperties(terrainType) {
    return TERRAIN_PROPERTIES[terrainType] || TERRAIN_PROPERTIES[0]; // Default to deep water if type not found
}

export function isWalkable(terrainType) {
    return getTerrainProperties(terrainType).walkable;
}

export function isBuildable(terrainType) {
    return getTerrainProperties(terrainType).buildable;
}

export function getMovementSpeed(terrainType) {
    return getTerrainProperties(terrainType).speed;
}

export function getResourceYield(terrainType) {
    return getTerrainProperties(terrainType).resourceYield;
}

export function getDefenseBonus(terrainType) {
    return getTerrainProperties(terrainType).defenseBonus;
}

export function getVisionPenalty(terrainType) {
    return getTerrainProperties(terrainType).visionPenalty;
} 