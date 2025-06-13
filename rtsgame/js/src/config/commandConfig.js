"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.COMMAND_CONFIG = void 0;
exports.COMMAND_CONFIG = {
    // Authority calculation weights
    AUTHORITY_WEIGHTS: {
        BASE_TIER_MULTIPLIER: 10,
        SUPPORT_BONUS: 5,
        HEALTH_MAX_MODIFIER: 5, // Max bonus/penalty from health
        VETERANCY_MAX_MODIFIER: 15, // Max bonus from veterancy (HERO level)
        CONTEXT_MAX_MODIFIER: 3 // Max bonus from context (e.g. defensive stance)
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
        STRATEGIC: 400, // General operational range for high-level orders or group cohesion
        TACTICAL: 200, // Range for direct command influence, finding successors
        SQUAD: 120, // Typical squad interaction/cohesion range
        INDIVIDUAL: 80, // Close-quarters interaction, minimum formation spacing
        FORMATION_MIN_DISTANCE: 80, // Specific for formation calculations
        FORMATION_MAX_DISTANCE: 150 // Specific for formation calculations
    },
    // Update intervals (milliseconds)
    UPDATE_INTERVALS: {
        AUTHORITY_RECALC: 5000, // How often to recalculate effective authority
        VETERANCY_CHECK: 1000, // How often to check for veterancy updates (currently every frame in updateVeterancyProgress)
        COMMAND_VALIDATION: 10000, // How often to validate command chains (not explicitly used yet)
        PROMOTION_COOLDOWN: 30000 // Cooldown between promotions
    },
    // Health-based command fitness thresholds (ratio of current HP to max HP)
    HEALTH_THRESHOLDS: {
        FULL_COMMAND: 0.8,
        REDUCED_AUTHORITY: 0.6,
        COMPROMISED_COMMAND: 0.4,
        CRITICAL_STATUS: 0.2,
        COMBAT_INEFFECTIVE: 0.0 // Represents HP < CRITICAL_STATUS threshold (e.g. < 0.2)
    },
    // Steering Behavior Configuration
    STEERING_WEIGHTS: {
        SEPARATION: 1.5,
        TERRAIN_AVOIDANCE: 2.0
        // Cohesion, Alignment, Seek/Arrive could be added here later
    },
    STEERING_FEELER_LENGTH_FACTOR: 1.5, // Multiplier for unit.type.size to determine feeler length
    NEIGHBOR_RADIUS_FACTOR: 2.0, // Multiplier for unit.type.size for separation check distance
    // Formation Rules
    FORMATION_RULES: {
        MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR: 0.75 // Factor of COMMAND_RANGES.STRATEGIC
    },
    FORMATION_BEHAVIOR: {
        PREDICTION_TIME_SECONDS: 0.5,
        ARRIVAL_RADIUS_FACTOR: 0.25, // Multiplier for unit.type.size
        DEFAULT_MAX_FORCE: 1.0,
        DEFAULT_MAX_TURN_RATE_RADIANS_PER_FRAME: 0.05235987755982988, // Math.PI / 60
        MIN_FORMATION_SLOT_DISTANCE: 50.0 // Used for radial offset in executeGroupMovement
    }
};
