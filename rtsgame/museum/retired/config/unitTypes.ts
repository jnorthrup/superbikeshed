import { UNIT_SPEED, UNIT_ATTACK_RANGE, UNIT_ATTACK_COOLDOWN, UNIT_HEALTH } from './gameConstants.js';
// It's good practice to import constants if they are available and intended for use here.
// For now, we'll use string literals for armor and damage types as per the plan.

const UNIT_TYPES = {
    tank: {
        name: 'Tank',
        domain: 'land',
        size: 10,
        speed: UNIT_SPEED * 0.7,
        maxHp: UNIT_HEALTH * 1.5,
        damage: 25,
        inflictsDamageType: 'Kinetic',
        armorType: 'Heavy',
        armorValue: 50,
        alloyType: 'AEGIS_STEEL', // Tanks benefit from heavy armor
        range: UNIT_ATTACK_RANGE * 1.2,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.2,
        buildTime: 240,
        color: '#4a90e2',
        effectColor: '#ff0',
        cost: { mass: 150, energy: 75 },
        tier: 1,
        commandRank: 1,
        radius: 16,
        abilities: ['attack']
    },
    bot: {
        name: 'Bot',
        domain: 'land',
        size: 6,
        speed: UNIT_SPEED * 2,
        maxHp: UNIT_HEALTH * 0.7,
        damage: 10,
        inflictsDamageType: 'Kinetic',
        armorType: 'Light',
        armorValue: 10,
        alloyType: 'QUICKSILVER_WEAVE', // Bots are fast, light
        range: UNIT_ATTACK_RANGE * 0.8,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 0.7,
        buildTime: 30,
        color: '#aaa',
        effectColor: '#f80',
        cost: { mass: 50, energy: 25 },
        tier: 1,
        commandRank: 1,
        radius: 12,
        abilities: ['scout']
    },
    artillery: {
        name: 'Artillery',
        domain: 'land',
        size: 12,
        speed: UNIT_SPEED * 0.5,
        maxHp: UNIT_HEALTH * 0.8,
        damage: 40,
        inflictsDamageType: 'Kinetic', // Could be explosive, but Kinetic is a good base
        armorType: 'Medium',
        armorValue: 30,
        alloyType: 'STANDARD_PLASTEEL',
        range: UNIT_ATTACK_RANGE * 3,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 2,
        buildTime: 300,
        color: '#4a90e2',
        effectColor: '#f00',
        cost: { mass: 200, energy: 150 },
        tier: 2,
        commandRank: 2,
        radius: 14,
        abilities: ['attack']
    },
    battleship: {
        name: 'Battleship',
        domain: 'sea',
        size: 20,
        speed: UNIT_SPEED * 0.8,
        maxHp: UNIT_HEALTH * 1.2,
        damage: 40,
        inflictsDamageType: 'Kinetic',
        armorType: 'Heavy',
        armorValue: 60,
        alloyType: 'AEGIS_STEEL',
        range: UNIT_ATTACK_RANGE * 1.5,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.5,
        buildTime: 120,
        color: '#048',
        effectColor: '#0ff',
        cost: { mass: 200, energy: 150 },
        tier: 2
    },
    submarine: {
        name: 'Submarine',
        domain: 'sea',
        size: 15,
        speed: UNIT_SPEED * 1.2,
        maxHp: UNIT_HEALTH * 0.9,
        damage: 30, // Torpedoes - could be Kinetic or a specialized type
        inflictsDamageType: 'Kinetic',
        armorType: 'Medium',
        armorValue: 35,
        alloyType: 'STANDARD_PLASTEEL',
        range: UNIT_ATTACK_RANGE * 1.8,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.8,
        buildTime: 80,
        color: '#026',
        effectColor: '#088',
        cost: { mass: 120, energy: 80 },
        tier: 1
    },
    fighter: {
        name: 'Fighter',
        domain: 'air',
        size: 8,
        speed: UNIT_SPEED * 4,
        maxHp: UNIT_HEALTH * 0.6,
        damage: 15,
        inflictsDamageType: 'Kinetic', // Machine guns
        armorType: 'Light',
        armorValue: 15,
        alloyType: 'TRINIUM_ALLOY', // Good for air units
        range: UNIT_ATTACK_RANGE * 1.5,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.5,
        buildTime: 50,
        color: '#ccc',
        effectColor: '#fff',
        cost: { mass: 80, energy: 60 },
        tier: 1
    },
    bomber: {
        name: 'Bomber',
        domain: 'air',
        size: 12,
        speed: UNIT_SPEED * 2,
        maxHp: UNIT_HEALTH * 1.0,
        damage: 60, // Bombs - could be explosive/Thermal
        inflictsDamageType: 'Thermal',
        armorType: 'Medium',
        armorValue: 25,
        alloyType: 'TRINIUM_ALLOY', // Bombers also benefit from being light
        range: UNIT_ATTACK_RANGE * 1.0, // Represents drop range
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.0,
        buildTime: 100,
        color: '#999',
        effectColor: '#ff8',
        cost: { mass: 150, energy: 120 },
        tier: 2
    },
    gunship: {
        name: 'Gunship',
        domain: 'air',
        size: 10,
        speed: UNIT_SPEED * 3,
        maxHp: UNIT_HEALTH * 0.8,
        damage: 25,
        inflictsDamageType: 'Kinetic',
        armorType: 'Medium',
        armorValue: 30,
        alloyType: 'TRINIUM_ALLOY',
        range: UNIT_ATTACK_RANGE * 1.2,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 1.2,
        buildTime: 70,
        color: '#bbb',
        effectColor: '#f8f',
        cost: { mass: 100, energy: 80 },
        tier: 1
    },
    engineer: {
        name: 'Engineer',
        domain: 'land',
        size: 8,
        speed: UNIT_SPEED * 0.9,
        speedLand: UNIT_SPEED * 0.9,
        speedWater: UNIT_SPEED * 0.9,
        maxHp: UNIT_HEALTH * 0.6,
        damage: 2, // Minimal self-defense
        inflictsDamageType: 'Kinetic',
        armorType: 'Light',
        armorValue: 5,
        alloyType: 'STANDARD_PLASTEEL',
        range: UNIT_ATTACK_RANGE * 0.5,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 0.5,
        buildTime: 180,
        color: '#4a90e2',
        effectColor: '#0f0',
        cost: { mass: 75, energy: 50 },
        support: true,
        movementType: 'amphibious',
        abilities: ['build', 'repair', 'capture'],
        radius: 12,
        tier: 1
    },
    shieldGenerator: {
        name: 'Shield Generator',
        domain: 'land',
        size: 14,
        speed: UNIT_SPEED * 0.7,
        maxHp: UNIT_HEALTH * 1.5,
        damage: 0, // No direct attack
        inflictsDamageType: 'None', // Or some non-offensive type
        armorType: 'Medium', // Structure itself needs some armor
        armorValue: 20,
        alloyType: 'CERAMITE_PLATING', // Good for stationary, defensive structures
        range: 0,
        attackSpeed: 0,
        buildTime: 80,
        color: '#0ff',
        effectColor: '#0ff',
        cost: { mass: 120, energy: 200 },
        support: true,
        shields: 100,
        shieldRegen: 2,
        shieldRadius: 150,
        shieldBoost: 5,
        tier: 2
    },
    experimental: {
        name: 'Experimental', // e.g., a giant laser or super tank
        domain: 'land',
        size: 25,
        speed: UNIT_SPEED * 0.5,
        maxHp: UNIT_HEALTH * 5.0,
        damage: 100,
        inflictsDamageType: 'Energy', // Assuming a powerful energy weapon
        armorType: 'Heavy',
        armorValue: 80,
        alloyType: 'AEGIS_STEEL', // Experimental units are tough
        range: UNIT_ATTACK_RANGE * 2.0,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 40, // Very slow firing
        buildTime: 300,
        color: '#f0f',
        effectColor: '#f0f',
        cost: { mass: 500, energy: 400 },
        shields: 200,
        shieldRegen: 5,
        tier: 3
    },
    commander: {
        name: 'Commander',
        domain: 'land',
        size: 18,
        speed: UNIT_SPEED * 0.8,
        speedLand: UNIT_SPEED * 0.8,
        speedWater: UNIT_SPEED * 0.8,
        maxHp: UNIT_HEALTH * 2,
        damage: 20,
        inflictsDamageType: 'Kinetic',
        armorType: 'Medium',
        armorValue: 40,
        alloyType: 'CERAMITE_PLATING', // Commanders are valuable
        range: UNIT_ATTACK_RANGE * 1.5,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 0.8,
        color: '#4a90e2',
        effectColor: '#f0f',
        cost: { mass: 0, energy: 0 }, // Typically free or pre-spawned
        tier: 2.5, // Special tier
        support: true,
        buildList: [],
        buildRate: 1.0,
        shields: 200,
        shieldRegen: 2,
        movementType: 'amphibious',
        abilities: ['build', 'repair', 'capture'],
        radius: 20
    },
    ai_commander: {
        name: 'AI Commander',
        domain: 'land',
        size: 15,
        speed: UNIT_SPEED * 1.5,
        maxHp: UNIT_HEALTH * 800, // Base HP, shields are separate
        maxShields: 300,
        shieldRegenRate: 5,
        damage: 40,
        inflictsDamageType: 'Energy', // Advanced energy weapon
        armorType: 'AdvancedComposite',
        armorValue: 70,
        alloyType: 'FLUX_RESONANCE_COMPOSITE', // AI commander benefits from comp/shield boosts
        range: UNIT_ATTACK_RANGE * 200, // Assuming these are game units, not pixels
        attackSpeed: UNIT_ATTACK_COOLDOWN * 20,
        buildTime: 300,
        color: '#0ff',
        effectColor: '#0af',
        cost: { mass: 500, energy: 800, computronium: 25 },
        tier: 3,
        hasComputroniumCore: true,
        coreEfficiency: 1.2,
        commandRank: 4,
        provides: ['enhanced_c2', 'tactical_analysis'],
        description: 'Advanced AI-controlled commander with enhanced tactical capabilities'
    },
    quantum_scout: {
        name: 'Quantum Scout',
        domain: 'land', // Or 'air' if it flies
        size: 8,
        speed: UNIT_SPEED * 4,
        maxHp: UNIT_HEALTH * 150,
        maxShields: 100,
        shieldRegenRate: 8,
        damage: 15, // Could be a light phase weapon
        inflictsDamageType: 'Phase',
        armorType: 'PhaseResistant', // Makes sense for a quantum unit
        armorValue: 30, // Lightly armored but hard to hit due to phase
        alloyType: 'QUICKSILVER_WEAVE', // Quantum scout is very fast
        range: UNIT_ATTACK_RANGE * 250,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 15,
        buildTime: 120,
        color: '#f0f',
        effectColor: '#f8f',
        cost: { mass: 200, energy: 300, computronium: 10 },
        tier: 3,
        hasComputroniumCore: true,
        coreEfficiency: 0.6,
        abilities: ['quantum_entanglement', 'phase_shift'],
        description: 'Advanced scout with quantum-enhanced reconnaissance capabilities'
    },
    cyber_warfare_unit: {
        name: 'Cyber Warfare Specialist',
        domain: 'land',
        size: 10,
        speed: UNIT_SPEED * 1.8,
        maxHp: UNIT_HEALTH * 200,
        maxShields: 150,
        shieldRegenRate: 6,
        damage: 25, // This damage is likely EMP or a special PoW attack, not direct physical
        inflictsDamageType: 'EMP', // For its direct "attack" if it has one
        armorType: 'Standard', // Standard electronics casing
        armorValue: 20,
        alloyType: 'FLUX_RESONANCE_COMPOSITE', // Cyber warfare benefits from comp boosts
        range: UNIT_ATTACK_RANGE * 180, // Range for its abilities
        attackSpeed: UNIT_ATTACK_COOLDOWN * 25,
        buildTime: 180,
        color: '#808',
        effectColor: '#a0a',
        cost: { mass: 300, energy: 500, computronium: 20 },
        tier: 3,
        hasComputroniumCore: true,
        coreEfficiency: 1.0,
        abilities: ['pow_attack', 'electronic_warfare', 'system_infiltration'],
        description: 'Specialized unit capable of launching computational attacks on enemy systems'
    },
    quantum_tank: {
        name: 'Quantum-Enhanced Heavy Tank',
        domain: 'land',
        size: 14,
        speed: UNIT_SPEED * 1.2,
        maxHp: UNIT_HEALTH * 400,
        maxShields: 200,
        shieldRegenRate: 4,
        damage: 60,
        inflictsDamageType: 'Phase', // Advanced weaponry
        armorType: 'AdvancedComposite', // Could be PhaseResistant or a mix
        armorValue: 100, // Very high armor
        alloyType: 'FLUX_RESONANCE_COMPOSITE', // Quantum tank is advanced
        range: UNIT_ATTACK_RANGE * 220,
        attackSpeed: UNIT_ATTACK_COOLDOWN * 40,
        buildTime: 240,
        color: '#048',
        effectColor: '#06a',
        cost: { mass: 400, energy: 600, computronium: 15 },
        tier: 3,
        hasComputroniumCore: true,
        coreEfficiency: 0.8,
        abilities: ['adaptive_armor', 'predictive_targeting'],
        description: 'Heavy tank with quantum-enhanced targeting and adaptive defense systems'
    }
};

export { UNIT_TYPES };
