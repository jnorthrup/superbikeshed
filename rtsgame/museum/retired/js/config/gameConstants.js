"use strict";
// js/config/gameConstants.js
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAX_TERRAIN_RETRIES = exports.MIN_LAND_PERCENTAGE = exports.COMPUTRONIUM_CONFIG = exports.RESOURCE_TYPES = exports.TERRAIN_TYPES = exports.BUILDING_YIELDS = exports.BUILDING_COSTS = exports.ECONOMIC_PHASES = exports.COMMAND_FITNESS_LEVELS = exports.VETERANCY_LEVELS = exports.AI_ATTACK_COORDINATION_RADIUS = exports.AI_DECISION_INTERVAL = exports.EFFECT_SCALE = exports.EFFECT_DURATION = exports.PROJECTILE_BLAST_RADIUS = exports.PROJECTILE_DAMAGE = exports.PROJECTILE_SPEED = exports.BUILDING_CONSTRUCTION_TIME = exports.BUILDING_HEALTH = exports.UNIT_HEALTH = exports.UNIT_ATTACK_COOLDOWN = exports.UNIT_ATTACK_RANGE = exports.UNIT_SPEED = exports.ENERGY_EXTRACTOR_RATE = exports.MASS_EXTRACTOR_RATE = exports.INITIAL_ENERGY = exports.INITIAL_MASS = exports.ZOOM_STEP = exports.MAX_ZOOM = exports.MIN_ZOOM = exports.TILE_SIZE = exports.GRID_SIZE = exports.WORLD_SIZE = void 0;
// World configuration
exports.WORLD_SIZE = 800;
exports.GRID_SIZE = 32;
exports.TILE_SIZE = exports.WORLD_SIZE / exports.GRID_SIZE;
// Camera configuration
exports.MIN_ZOOM = 0.5;
exports.MAX_ZOOM = 2.0;
exports.ZOOM_STEP = 0.1;
// Resource configuration
exports.INITIAL_MASS = 100;
exports.INITIAL_ENERGY = 150;
exports.MASS_EXTRACTOR_RATE = 1.0;
exports.ENERGY_EXTRACTOR_RATE = 2.0;
// Unit configuration
exports.UNIT_SPEED = 2.0;
exports.UNIT_ATTACK_RANGE = 100;
exports.UNIT_ATTACK_COOLDOWN = 60; // frames
exports.UNIT_HEALTH = 100;
// Building configuration
exports.BUILDING_HEALTH = 500;
exports.BUILDING_CONSTRUCTION_TIME = 300; // frames
// Projectile configuration
exports.PROJECTILE_SPEED = 5.0;
exports.PROJECTILE_DAMAGE = 10;
exports.PROJECTILE_BLAST_RADIUS = 5;
// Effect configuration
exports.EFFECT_DURATION = 30; // frames
exports.EFFECT_SCALE = 1.0;
// AI configuration
exports.AI_DECISION_INTERVAL = 60; // frames
exports.AI_ATTACK_COORDINATION_RADIUS = 200;
const TERRAIN_TYPES = {
    WATER: 0,
    LAND: 1,
    MOUNTAIN: 2,
    RESOURCE: 3 // New terrain type for resources
};
exports.TERRAIN_TYPES = TERRAIN_TYPES;
// Resource Types - Core RTS resources plus Computronium
const RESOURCE_TYPES = {
    MASS: 'MASS',
    ENERGY: 'ENERGY'
};
exports.RESOURCE_TYPES = RESOURCE_TYPES;
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
        TACTICAL: 150, // Close-range tactical command
        OPERATIONAL: 300, // Medium-range operational command
        STRATEGIC: 500 // Long-range strategic command
    }
};
exports.COMPUTRONIUM_CONFIG = COMPUTRONIUM_CONFIG;
const MIN_LAND_PERCENTAGE = 0.3;
exports.MIN_LAND_PERCENTAGE = MIN_LAND_PERCENTAGE;
const MAX_TERRAIN_RETRIES = 5;
exports.MAX_TERRAIN_RETRIES = MAX_TERRAIN_RETRIES;
// Veterancy levels
exports.VETERANCY_LEVELS = {
    GREEN: 'GREEN',
    REGULAR: 'REGULAR',
    VETERAN: 'VETERAN',
    ELITE: 'ELITE',
    HERO: 'HERO'
};
// Command fitness levels
exports.COMMAND_FITNESS_LEVELS = {
    FULL_COMMAND: 'FULL_COMMAND',
    REDUCED_AUTHORITY: 'REDUCED_AUTHORITY',
    COMPROMISED_COMMAND: 'COMPROMISED_COMMAND',
    CRITICAL_STATUS: 'CRITICAL_STATUS',
    COMBAT_INEFFECTIVE: 'COMBAT_INEFFECTIVE'
};
// Economic phases
exports.ECONOMIC_PHASES = {
    SURVIVAL: 'SURVIVAL', // 1-2 extractors
    EXPANSION: 'EXPANSION', // 3-4 extractors
    ADVANCED: 'ADVANCED', // 5-6 extractors
    EXPERIMENTAL: 'EXPERIMENTAL' // 7+ extractors
};
// Building costs
exports.BUILDING_COSTS = {
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
exports.BUILDING_YIELDS = {
    EXTRACTOR: {
        mass: 2
    },
    ENERGY_PLANT: {
        energy: 3
    }
};
