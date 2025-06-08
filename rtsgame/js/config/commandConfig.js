export const COMMAND_CONFIG = {
    // Authority calculation weights
    AUTHORITY_WEIGHTS: {
        BASE_TIER_MULTIPLIER: 10,
        SUPPORT_BONUS: 5,
        HEALTH_MAX_MODIFIER: 5,        // Max bonus/penalty from health
        VETERANCY_MAX_MODIFIER: 15,    // Max bonus from veterancy (HERO level)
        CONTEXT_MAX_MODIFIER: 3        // Max bonus from context (e.g. defensive stance)
    },

    // Veterancy thresholds (experience points)
    VETERANCY_THRESHOLDS: {
        REGULAR: 25,
        VETERAN: 75,
        ELITE: 150,
        HERO: 300
    },

    // Command ranges (distance units)
    COMMAND_RANGES: {
        STRATEGIC: 400,         // General operational range for high-level orders or group cohesion
        TACTICAL: 200,          // Range for direct command influence, finding successors
        SQUAD: 120,             // Typical squad interaction/cohesion range
        INDIVIDUAL: 80,         // Close-quarters interaction, minimum formation spacing
        FORMATION_MIN_DISTANCE: 80, // Specific for formation calculations
        FORMATION_MAX_DISTANCE: 150 // Specific for formation calculations
    },

    // Update intervals (milliseconds)
    UPDATE_INTERVALS: {
        AUTHORITY_RECALC: 5000,    // How often to recalculate effective authority
        VETERANCY_CHECK: 1000,     // How often to check for veterancy updates (currently every frame in updateVeterancyProgress)
        COMMAND_VALIDATION: 10000, // How often to validate command chains (not explicitly used yet)
        PROMOTION_COOLDOWN: 30000  // Cooldown between promotions
    },

    // Health-based command fitness thresholds (ratio of current HP to max HP)
    HEALTH_THRESHOLDS: {
        FULL_COMMAND: 0.8,
        REDUCED_AUTHORITY: 0.6,
        COMPROMISED_COMMAND: 0.4,
        CRITICAL_STATUS: 0.2,
        COMBAT_INEFFECTIVE: 0.0  // Represents HP < CRITICAL_STATUS threshold (e.g. < 0.2)
    }
};
