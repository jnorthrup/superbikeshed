"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ALLOY_TYPES = void 0;
exports.ALLOY_TYPES = {
    STANDARD_PLASTEEL: {
        displayName: 'Standard Plasteel',
        modifiers: {
            hpFactor: 1.0, // Multiplier for maxHp
            armorAdd: 0, // Additive to armorValue
            speedFactor: 1.0, // Multiplier for speed
            costFactor: 1.0 // Multiplier for unit cost (mass & energy)
        }
    },
    AEGIS_STEEL: {
        displayName: 'Aegis Steel',
        modifiers: {
            hpFactor: 1.15, // +HP
            armorAdd: 20, // +++Armor
            speedFactor: 0.85, // --Speed
            costFactor: 1.2 // Higher cost
        }
    },
    QUICKSILVER_WEAVE: {
        displayName: 'Quicksilver Weave',
        modifiers: {
            hpFactor: 0.9, // Slightly less HP
            armorAdd: -5, // --Armor (or small positive if preferred)
            speedFactor: 1.25, // +++Speed
            costFactor: 1.15
        }
    },
    FLUX_RESONANCE_COMPOSITE: {
        displayName: 'Flux Resonance Composite',
        modifiers: {
            hpFactor: 0.95,
            armorAdd: 5,
            speedFactor: 0.95,
            costFactor: 1.3,
            shieldRegenFactor: 1.1, // Example: 10% faster shield regen
            computroniumEfficiencyFactor: 1.05 // Example: 5% more efficient computronium
        }
    },
    TRINIUM_ALLOY: {
        displayName: 'Trinium Alloy',
        modifiers: {
            hpFactor: 0.90, // Lighter means a bit less durable overall
            armorAdd: 5, // Decent protection for its weight
            speedFactor: 1.15, // Faster
            costFactor: 1.25 // More expensive
        }
    },
    CERAMITE_PLATING: {
        displayName: 'Ceramite Plating',
        modifiers: {
            hpFactor: 1.05,
            armorAdd: 15,
            speedFactor: 0.90,
            costFactor: 1.1,
            // Could add a specific resistance bonus here if damage system supports it beyond armorType
            // For now, this is represented by higher armorAdd.
        }
    }
};
