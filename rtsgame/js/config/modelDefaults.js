// js_rewritten/config/modelDefaults.js

/**
 * Default configurations for 3D models and entity settings.
 * Inspired by themes of large-scale mechanized warfare with strategic depth,
 * focusing on varied unit tiers and complex building functionalities.
 */

// Unit model defaults with tiered complexity and strategic roles
export const UNIT_MODELS = {
    commander: {
        name: "Strategic Commander Unit",
        tier: 3,
        modelPath: "commander",
        scale: 1.5,
        health: 10000,
        speed: 2,
        size: 60,
        buildCapabilities: ["landFactory", "airFactory", "navalFactory", "energyExtractor"],
        attack: { damage: 500, range: 100, cooldown: 2.0 },
        description: "The central unit controlling strategic operations, capable of constructing factories and commanding armies."
    },
    scout: {
        name: "Light Recon Unit",
        tier: 1,
        modelPath: "scout",
        scale: 0.8,
        health: 200,
        speed: 5,
        size: 20,
        attack: { damage: 10, range: 50, cooldown: 1.0 },
        description: "Fast-moving unit for reconnaissance with minimal combat capability."
    },
    tank: {
        name: "Armored Assault Unit",
        tier: 2,
        modelPath: "tank",
        scale: 1.0,
        health: 800,
        speed: 3,
        size: 30,
        attack: { damage: 100, range: 80, cooldown: 1.5 },
        description: "Heavy ground unit designed for frontline combat with significant firepower."
    },
    artillery: {
        name: "Long-Range Support Unit",
        tier: 3,
        modelPath: "artillery",
        scale: 1.2,
        health: 500,
        speed: 1.5,
        size: 40,
        attack: { damage: 300, range: 200, cooldown: 5.0, areaOfEffect: 20 },
        description: "Long-range unit capable of devastating area attacks from a distance."
    },
    fighter: {
        name: "Aerial Strike Unit",
        tier: 2,
        modelPath: "fighter",
        scale: 0.9,
        health: 300,
        speed: 6,
        size: 25,
        attack: { damage: 50, range: 60, cooldown: 1.2 },
        description: "Fast aerial unit for air superiority and quick strikes."
    },
    submarine: {
        name: "Naval Stealth Unit",
        tier: 2,
        modelPath: "submarine",
        scale: 1.1,
        health: 600,
        speed: 4,
        size: 35,
        attack: { damage: 80, range: 70, cooldown: 2.0 },
        description: "Underwater unit for naval warfare with stealth capabilities."
    }
};

// Building model defaults with strategic importance
export const BUILDING_MODELS = {
    landFactory: {
        name: "Ground Production Facility",
        modelPath: "landFactory",
        scale: 2.0,
        health: 2000,
        size: 100,
        produces: ["scout", "tank", "artillery"],
        cost: { mass: 1000, energy: 5000 },
        buildTime: 30,
        description: "Facility for producing ground-based combat units."
    },
    airFactory: {
        name: "Aerial Production Facility",
        modelPath: "airFactory",
        scale: 2.0,
        health: 1800,
        size: 100,
        produces: ["fighter"],
        cost: { mass: 1200, energy: 6000 },
        buildTime: 35,
        description: "Facility for constructing aerial units for air dominance."
    },
    navalFactory: {
        name: "Naval Production Facility",
        modelPath: "navalFactory",
        scale: 2.0,
        health: 2000,
        size: 120,
        produces: ["submarine"],
        cost: { mass: 1500, energy: 7000 },
        buildTime: 40,
        description: "Dockyard for building naval units suited for water combat."
    },
    energyExtractor: {
        name: "Energy Generation Plant",
        modelPath: "energyExtractor",
        scale: 1.8,
        health: 1000,
        size: 80,
        resourceGeneration: { energy: 50, mass: 0 },
        cost: { mass: 500, energy: 2000 },
        buildTime: 20,
        description: "Generates energy resources critical for unit production and upgrades."
    },
    massExtractor: {
        name: "Mass Extraction Site",
        modelPath: "massExtractor",
        scale: 1.8,
        health: 1000,
        size: 80,
        resourceGeneration: { energy: 0, mass: 10 },
        cost: { mass: 300, energy: 1500 },
        buildTime: 25,
        description: "Extracts mass resources essential for constructing units and buildings."
    }
};

// Default texture paths for terrain and effects
export const TERRAIN_MODELS = {
    resource: {
        name: "Resource Deposit",
        modelPath: "resource",
        scale: 0.05, // Adjust scale as needed for terrain
        size: 50,
        description: "A valuable resource deposit."
    }
};

export const TERRAIN_TEXTURES = {
    water: "textures/water.png",
    land: "textures/land.png",
    mountain: "textures/mountain.png"
};

export const EFFECT_TEXTURES = {
    explosion: "textures/explosion.png",
    smoke: "textures/smoke.png"
};

// Terrain constants
export const SEA_LEVEL = 0.0;