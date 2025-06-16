"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EMP_ENERGY_DRAIN_AMOUNT = exports.MINIMUM_DAMAGE_PERCENTAGE = exports.ARMOR_DAMAGE_REDUCTION_CONSTANT_K = exports.DAMAGE_ARMOR_INTERACTIONS = exports.ARMOR_TYPES = exports.DAMAGE_TYPES = void 0;
exports.DAMAGE_TYPES = {
    KINETIC: 'Kinetic',
    ENERGY: 'Energy',
    THERMAL: 'Thermal',
    EMP: 'EMP',
    CORROSIVE: 'Corrosive',
    NANITE: 'Nanite',
    PHASE: 'Phase'
};
exports.ARMOR_TYPES = {
    LIGHT: 'Light',
    MEDIUM: 'Medium',
    HEAVY: 'Heavy',
    ENERGY_RESISTANT: 'EnergyResistant',
    STANDARD: 'Standard',
    ADVANCED_COMPOSITE: 'AdvancedComposite',
    PHASE_RESISTANT: 'PhaseResistant'
};
exports.DAMAGE_ARMOR_INTERACTIONS = {
    [exports.DAMAGE_TYPES.KINETIC]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.2,
        [exports.ARMOR_TYPES.MEDIUM]: 1.0,
        [exports.ARMOR_TYPES.HEAVY]: 0.8,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 1.0,
        [exports.ARMOR_TYPES.STANDARD]: 1.0,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.9,
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 0.5, // Kinetic struggles against phase
    },
    [exports.DAMAGE_TYPES.ENERGY]: {
        [exports.ARMOR_TYPES.LIGHT]: 0.8,
        [exports.ARMOR_TYPES.MEDIUM]: 1.0,
        [exports.ARMOR_TYPES.HEAVY]: 1.2,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 0.5, // Specifically designed to resist energy
        [exports.ARMOR_TYPES.STANDARD]: 1.0,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.1,
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 1.0,
    },
    [exports.DAMAGE_TYPES.THERMAL]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.5, // Melts light armor
        [exports.ARMOR_TYPES.MEDIUM]: 1.1,
        [exports.ARMOR_TYPES.HEAVY]: 0.9,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 1.2, // Can overwhelm energy resistant if not also heat resistant
        [exports.ARMOR_TYPES.STANDARD]: 1.0,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.7, // Often designed to dissipate heat
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 0.8,
    },
    [exports.DAMAGE_TYPES.EMP]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.0,
        [exports.ARMOR_TYPES.MEDIUM]: 1.0,
        [exports.ARMOR_TYPES.HEAVY]: 1.0,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 0.8, // May have some EMP hardening
        [exports.ARMOR_TYPES.STANDARD]: 1.0,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.9, // May have some EMP hardening
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 1.0,
    },
    [exports.DAMAGE_TYPES.CORROSIVE]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.1,
        [exports.ARMOR_TYPES.MEDIUM]: 1.2,
        [exports.ARMOR_TYPES.HEAVY]: 1.3, // Corrodes heavy armor effectively over time
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 1.0,
        [exports.ARMOR_TYPES.STANDARD]: 1.2,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.0, // Some composites might be resistant
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 0.7, // Phase armor might resist material corrosion
    },
    [exports.DAMAGE_TYPES.NANITE]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.1,
        [exports.ARMOR_TYPES.MEDIUM]: 1.1,
        [exports.ARMOR_TYPES.HEAVY]: 1.1,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 1.1,
        [exports.ARMOR_TYPES.STANDARD]: 1.1,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.1,
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 1.2, // Nanites might be specifically effective against phase tech
    },
    [exports.DAMAGE_TYPES.PHASE]: {
        [exports.ARMOR_TYPES.LIGHT]: 1.3,
        [exports.ARMOR_TYPES.MEDIUM]: 1.3,
        [exports.ARMOR_TYPES.HEAVY]: 1.3,
        [exports.ARMOR_TYPES.ENERGY_RESISTANT]: 1.3,
        [exports.ARMOR_TYPES.STANDARD]: 1.3,
        [exports.ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.3,
        [exports.ARMOR_TYPES.PHASE_RESISTANT]: 0.2, // Specifically designed to resist phase attacks
    },
};
// Constant for armor calculation
exports.ARMOR_DAMAGE_REDUCTION_CONSTANT_K = 200;
exports.MINIMUM_DAMAGE_PERCENTAGE = 0.05; // Minimum 5% of original damage
exports.EMP_ENERGY_DRAIN_AMOUNT = 50;
