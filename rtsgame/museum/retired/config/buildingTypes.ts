import { UNIT_TYPES } from './unitTypes.js';
import { BUILDING_HEALTH, BUILDING_CONSTRUCTION_TIME, MASS_EXTRACTOR_RATE, ENERGY_EXTRACTOR_RATE } from './gameConstants.js';

export const BUILDING_TYPES = {
    landFactory: {
        name: 'Land Factory',
        health: BUILDING_HEALTH,
        cost: { mass: 200, energy: 100 },
        buildTime: BUILDING_CONSTRUCTION_TIME,
        radius: 25,
        color: '#4a90e2',
        abilities: ['build'],
        buildList: ['scout', 'tank', 'artillery', 'engineer']
    },
    advancedLandFactory: {
        name: 'Advanced Land Factory',
        size: 50,
        maxHp: 700,
        produces: [UNIT_TYPES.artillery, UNIT_TYPES.shieldGenerator, UNIT_TYPES.experimental],
        color: '#a60',
        cost: { mass: 400, energy: 300 }
    },
    airFactory: {
        name: 'Air Factory',
        health: BUILDING_HEALTH * 1.2,
        cost: { mass: 300, energy: 200 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 1.2,
        radius: 25,
        color: '#4a90e2',
        abilities: ['build'],
        buildList: ['scout', 'fighter', 'bomber']
    },
    navalFactory: {
        name: 'Naval Factory',
        size: 50,
        maxHp: 600,
        produces: [UNIT_TYPES.battleship, UNIT_TYPES.submarine],
        color: '#048',
        cost: { mass: 250, energy: 150 }
    },
    massExtractor: {
        name: 'Mass Extractor',
        health: BUILDING_HEALTH * 0.8,
        cost: { mass: 100, energy: 50 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 0.8,
        productionRate: MASS_EXTRACTOR_RATE,
        radius: 20,
        color: '#4a90e2',
        abilities: ['extract']
    },
    energyExtractor: {
        name: 'Energy Extractor',
        health: BUILDING_HEALTH * 0.8,
        cost: { mass: 75, energy: 25 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 0.8,
        productionRate: ENERGY_EXTRACTOR_RATE,
        radius: 20,
        color: '#4a90e2',
        abilities: ['extract']
    },
    computroniumExtractor: {
        name: 'Computronium Core Facility',
        size: 30,
        maxHp: 300,
        produces: null,
        color: '#0af',
        resourceGeneration: { type: 'computronium', amount: 0.5 },
        cost: { mass: 150, energy: 200, computronium: 5 },
        hasComputroniumCore: true,
        coreEfficiency: 0.8,
        description: 'Advanced facility that extracts and processes Computronium from specialized nodes'
    },
    advancedComputroniumCore: {
        name: 'Advanced Computational Matrix',
        size: 40,
        maxHp: 500,
        produces: null,
        color: '#08f',
        resourceGeneration: { type: 'computronium', amount: 1.0 },
        cost: { mass: 300, energy: 400, computronium: 15 },
        hasComputroniumCore: true,
        coreEfficiency: 1.0,
        provides: ['c2_enhancement', 'pow_defense'],
        description: 'Cutting-edge computational facility providing C&C enhancements and PoW defense'
    },
    quantumComputingCenter: {
        name: 'Quantum Computing Center',
        size: 50,
        maxHp: 800,
        produces: [UNIT_TYPES.tank, UNIT_TYPES.bot], // Temporary until advanced units implemented
        color: '#f0f',
        resourceGeneration: { type: 'computronium', amount: 2.0 },
        cost: { mass: 500, energy: 800, computronium: 50 },
        hasComputroniumCore: true,
        coreEfficiency: 1.5,
        provides: ['advanced_ai', 'quantum_entanglement', 'pow_attack'],
        description: 'Ultimate computational facility enabling quantum-enhanced warfare capabilities'
    },
    defenseTower: {
        name: 'Defense Tower',
        health: BUILDING_HEALTH * 0.6,
        cost: { mass: 150, energy: 75 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 0.6,
        attackRange: 200,
        attackCooldown: 30,
        damage: 15,
        radius: 15,
        color: '#4a90e2',
        abilities: ['attack']
    },
    shieldGenerator: {
        name: 'Shield Generator',
        health: BUILDING_HEALTH * 0.7,
        cost: { mass: 250, energy: 150 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 0.7,
        shieldRadius: 150,
        shieldStrength: 500,
        shieldRegen: 5,
        radius: 20,
        color: '#4a90e2',
        abilities: ['shield']
    },
    radar: {
        name: 'Radar',
        health: BUILDING_HEALTH * 0.5,
        cost: { mass: 100, energy: 75 },
        buildTime: BUILDING_CONSTRUCTION_TIME * 0.5,
        detectionRadius: 300,
        radius: 15,
        color: '#4a90e2',
        abilities: ['detect']
    }
};
