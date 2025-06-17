// js/config/gameConstants.js

// World configuration
export const WORLD_SIZE = 800;
export const GRID_SIZE = 32;
export const TILE_SIZE = WORLD_SIZE / GRID_SIZE;

// Camera configuration
export const MIN_ZOOM = 0.5;
export const MAX_ZOOM = 2.0;
export const ZOOM_STEP = 0.1;

// Resource configuration
export const INITIAL_MASS = 100;
export const INITIAL_ENERGY = 150;
export const MASS_EXTRACTOR_RATE = 1.0;
export const ENERGY_EXTRACTOR_RATE = 2.0;

// Unit configuration
export const UNIT_SPEED = 2.0;
export const UNIT_ATTACK_RANGE = 100;
export const UNIT_ATTACK_COOLDOWN = 60; // frames
export const UNIT_HEALTH = 100;

// Building configuration
export const BUILDING_HEALTH = 500;
export const BUILDING_CONSTRUCTION_TIME = 300; // frames

// Projectile configuration
export const PROJECTILE_SPEED = 5.0;
export const PROJECTILE_DAMAGE = 10;
export const PROJECTILE_BLAST_RADIUS = 5;

// Effect configuration
export const EFFECT_DURATION = 30; // frames
export const EFFECT_SCALE = 1.0;

// AI configuration
export const AI_DECISION_INTERVAL = 60; // frames
export const AI_ATTACK_COORDINATION_RADIUS = 200;

const TERRAIN_TYPES = {
    WATER: 0,
    LAND: 1,
    MOUNTAIN: 2,
    RESOURCE: 3 // New terrain type for resources
};

// Resource Types - Core RTS resources plus Computronium
const RESOURCE_TYPES = {
    MASS: 'MASS',
    ENERGY: 'ENERGY'
};

// Computronium Configuration
const COMPUTRONIUM_CONFIG = {
    BASE_GENERATION_RATE: 0.1, // Base computronium per second from cores
    DINING_PHILOSOPHERS_PENALTY: 0.15, // Efficiency loss when cores compete
    MAX_CORE_EFFICIENCY: 1.0,
    MIN_CORE_EFFICIENCY: 0.2,
    COMPUTATIONAL_WARFARE_COST: 5.0, // Computronium cost per PoW operation
    C2_LATENCY_BASE: 0.1, // Base command latency in seconds
    C2_COMPUTRONIUM_BONUS: 0.02, // Latency reduction per computronium point
    
    // Command & Control ranges
    COMMAND_RANGES: {
        TACTICAL: 150,   // Close-range tactical command
        OPERATIONAL: 300, // Medium-range operational command
        STRATEGIC: 500   // Long-range strategic command
    }
};

const MIN_LAND_PERCENTAGE = 0.3;
const MAX_TERRAIN_RETRIES = 5;

// Veterancy levels
export const VETERANCY_LEVELS = {
    GREEN: 'GREEN',
    REGULAR: 'REGULAR',
    VETERAN: 'VETERAN',
    ELITE: 'ELITE',
    HERO: 'HERO'
};

// Command fitness levels
export const COMMAND_FITNESS_LEVELS = {
    FULL_COMMAND: 'FULL_COMMAND',
    REDUCED_AUTHORITY: 'REDUCED_AUTHORITY',
    COMPROMISED_COMMAND: 'COMPROMISED_COMMAND',
    CRITICAL_STATUS: 'CRITICAL_STATUS',
    COMBAT_INEFFECTIVE: 'COMBAT_INEFFECTIVE'
};

// Economic phases
export const ECONOMIC_PHASES = {
    SURVIVAL: 'SURVIVAL',      // 1-2 extractors
    EXPANSION: 'EXPANSION',    // 3-4 extractors
    ADVANCED: 'ADVANCED',      // 5-6 extractors
    EXPERIMENTAL: 'EXPERIMENTAL' // 7+ extractors
};

// Building costs
export const BUILDING_COSTS = {
    EXTRACTOR: {
        mass: 50,
        energy: 25
    },
    ENERGY_PLANT: {
        mass: 30,
        energy: 20
    },
    LAND_FACTORY: {
        mass: 200,
        energy: 100
    },
    ADVANCED_FACTORY: {
        mass: 400,
        energy: 300
    },
    AIR_FACTORY: {
        mass: 150,
        energy: 120
    },
    NAVAL_FACTORY: {
        mass: 250,
        energy: 150
    }
};

// Building yields
export const BUILDING_YIELDS = {
    EXTRACTOR: {
        mass: 2
    },
    ENERGY_PLANT: {
        energy: 3
    }
};

export {
    TERRAIN_TYPES,
    RESOURCE_TYPES,
    COMPUTRONIUM_CONFIG,
    MIN_LAND_PERCENTAGE,
    MAX_TERRAIN_RETRIES
};
