export const DAMAGE_TYPES = {
    KINETIC: 'Kinetic',
    ENERGY: 'Energy',
    THERMAL: 'Thermal',
    EMP: 'EMP',
    CORROSIVE: 'Corrosive',
    NANITE: 'Nanite',
    PHASE: 'Phase'
};

export const ARMOR_TYPES = {
    LIGHT: 'Light',
    MEDIUM: 'Medium',
    HEAVY: 'Heavy',
    ENERGY_RESISTANT: 'EnergyResistant',
    STANDARD: 'Standard',
    ADVANCED_COMPOSITE: 'AdvancedComposite',
    PHASE_RESISTANT: 'PhaseResistant'
};

export const DAMAGE_ARMOR_INTERACTIONS = {
    [DAMAGE_TYPES.KINETIC]: {
        [ARMOR_TYPES.LIGHT]: 1.2,
        [ARMOR_TYPES.MEDIUM]: 1.0,
        [ARMOR_TYPES.HEAVY]: 0.8,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 1.0,
        [ARMOR_TYPES.STANDARD]: 1.0,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.9,
        [ARMOR_TYPES.PHASE_RESISTANT]: 0.5, // Kinetic struggles against phase
    },
    [DAMAGE_TYPES.ENERGY]: {
        [ARMOR_TYPES.LIGHT]: 0.8,
        [ARMOR_TYPES.MEDIUM]: 1.0,
        [ARMOR_TYPES.HEAVY]: 1.2,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 0.5, // Specifically designed to resist energy
        [ARMOR_TYPES.STANDARD]: 1.0,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.1,
        [ARMOR_TYPES.PHASE_RESISTANT]: 1.0,
    },
    [DAMAGE_TYPES.THERMAL]: {
        [ARMOR_TYPES.LIGHT]: 1.5, // Melts light armor
        [ARMOR_TYPES.MEDIUM]: 1.1,
        [ARMOR_TYPES.HEAVY]: 0.9,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 1.2, // Can overwhelm energy resistant if not also heat resistant
        [ARMOR_TYPES.STANDARD]: 1.0,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.7, // Often designed to dissipate heat
        [ARMOR_TYPES.PHASE_RESISTANT]: 0.8,
    },
    [DAMAGE_TYPES.EMP]: { // EMP primarily affects shields and electronics, less so physical armor
        [ARMOR_TYPES.LIGHT]: 1.0,
        [ARMOR_TYPES.MEDIUM]: 1.0,
        [ARMOR_TYPES.HEAVY]: 1.0,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 0.8, // May have some EMP hardening
        [ARMOR_TYPES.STANDARD]: 1.0,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 0.9, // May have some EMP hardening
        [ARMOR_TYPES.PHASE_RESISTANT]: 1.0,
    },
    [DAMAGE_TYPES.CORROSIVE]: {
        [ARMOR_TYPES.LIGHT]: 1.1,
        [ARMOR_TYPES.MEDIUM]: 1.2,
        [ARMOR_TYPES.HEAVY]: 1.3, // Corrodes heavy armor effectively over time
        [ARMOR_TYPES.ENERGY_RESISTANT]: 1.0,
        [ARMOR_TYPES.STANDARD]: 1.2,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.0, // Some composites might be resistant
        [ARMOR_TYPES.PHASE_RESISTANT]: 0.7, // Phase armor might resist material corrosion
    },
    [DAMAGE_TYPES.NANITE]: { // Nanites might be good at disassembling any armor type if they can bypass shields
        [ARMOR_TYPES.LIGHT]: 1.1,
        [ARMOR_TYPES.MEDIUM]: 1.1,
        [ARMOR_TYPES.HEAVY]: 1.1,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 1.1,
        [ARMOR_TYPES.STANDARD]: 1.1,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.1,
        [ARMOR_TYPES.PHASE_RESISTANT]: 1.2, // Nanites might be specifically effective against phase tech
    },
    [DAMAGE_TYPES.PHASE]: { // Phase damage bypasses conventional armor to some extent
        [ARMOR_TYPES.LIGHT]: 1.3,
        [ARMOR_TYPES.MEDIUM]: 1.3,
        [ARMOR_TYPES.HEAVY]: 1.3,
        [ARMOR_TYPES.ENERGY_RESISTANT]: 1.3,
        [ARMOR_TYPES.STANDARD]: 1.3,
        [ARMOR_TYPES.ADVANCED_COMPOSITE]: 1.3,
        [ARMOR_TYPES.PHASE_RESISTANT]: 0.2, // Specifically designed to resist phase attacks
    },
};

// Constant for armor calculation
export const ARMOR_DAMAGE_REDUCTION_CONSTANT_K = 200;
export const MINIMUM_DAMAGE_PERCENTAGE = 0.05; // Minimum 5% of original damage
export const EMP_ENERGY_DRAIN_AMOUNT = 50;
