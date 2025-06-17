export class AuthoritySystem {
    constructor() {
        this.healthAuthorityModifiers = {
            HIGH: { threshold: 0.8, bonus: 5 },
            MEDIUM: { threshold: 0.6, bonus: 2 },
            LOW: { threshold: 0.4, penalty: -2 },
            CRITICAL: { threshold: 0.2, penalty: -5 },
            COMBAT_INEFFECTIVE: { threshold: 0.0, penalty: -10 }
        };

        this.veterancyLevels = {
            GREEN: { min: 0, max: 25, bonuses: { accuracy: 0, speed: 0, commandRadius: 0 } },
            REGULAR: { min: 26, max: 75, bonuses: { accuracy: 0.1, speed: 0.05, commandRadius: 0 } },
            VETERAN: { min: 76, max: 150, bonuses: { accuracy: 0.2, speed: 0.1, commandRadius: 1 } },
            ELITE: { min: 151, max: 300, bonuses: { accuracy: 0.3, speed: 0.15, commandRadius: 2 } },
            HERO: { min: 301, max: Infinity, bonuses: { accuracy: 0.4, speed: 0.2, commandRadius: 3 } }
        };

        this.contextModifiers = {
            COMBAT: { type: 'combat', bonus: 3 },
            ECONOMIC: { type: 'support', bonus: 3 },
            DEFENSIVE: { type: 'stationary', bonus: 2 },
            EMERGENCY: { type: 'healthy', bonus: 5 }
        };
    }

    calculateHealthAuthorityModifier(unit) {
        const healthRatio = unit.health / unit.maxHealth;
        
        for (const [level, { threshold, bonus, penalty }] of Object.entries(this.healthAuthorityModifiers)) {
            if (healthRatio >= threshold) {
                return bonus || penalty || 0;
            }
        }
        
        return this.healthAuthorityModifiers.COMBAT_INEFFECTIVE.penalty;
    }

    calculateVeterancyAuthorityModifier(unit) {
        const veterancyPoints = 
            unit.combatExperience +
            unit.survivalTime +
            (unit.commandExperience * 3) +
            (unit.killCount * 2) +
            (unit.strategicImpact * 5);

        for (const [level, { min, max, bonuses }] of Object.entries(this.veterancyLevels)) {
            if (veterancyPoints >= min && veterancyPoints <= max) {
                return {
                    level,
                    bonuses,
                    veterancyPoints
                };
            }
        }
    }

    calculateEffectiveAuthority(unit, context) {
        const baseAuthority = unit.tier * 10 + (unit.isSupport ? 5 : 0);
        const healthModifier = this.calculateHealthAuthorityModifier(unit);
        const veterancyData = this.calculateVeterancyAuthorityModifier(unit);
        const contextModifier = this.getContextModifier(unit, context);

        return {
            baseAuthority,
            healthModifier,
            veterancyData,
            contextModifier,
            effectiveAuthority: baseAuthority + healthModifier + (veterancyData?.bonuses?.commandRadius || 0) + contextModifier
        };
    }

    getContextModifier(unit, context) {
        if (context === 'EMERGENCY' && unit.health / unit.maxHealth > 0.8) {
            return this.contextModifiers.EMERGENCY.bonus;
        }
        
        if (unit.isSupport && context === 'ECONOMIC') {
            return this.contextModifiers.ECONOMIC.bonus;
        }
        
        if (!unit.isSupport && context === 'COMBAT') {
            return this.contextModifiers.COMBAT.bonus;
        }
        
        if (unit.isStationary && context === 'DEFENSIVE') {
            return this.contextModifiers.DEFENSIVE.bonus;
        }
        
        return 0;
    }

    resolveAuthorityConflict(unit1, unit2, context) {
        const auth1 = this.calculateEffectiveAuthority(unit1, context);
        const auth2 = this.calculateEffectiveAuthority(unit2, context);

        if (auth1.effectiveAuthority !== auth2.effectiveAuthority) {
            return auth1.effectiveAuthority > auth2.effectiveAuthority ? unit1 : unit2;
        }

        // Tie-breaking rules
        if (unit1.health / unit1.maxHealth !== unit2.health / unit2.maxHealth) {
            return (unit1.health / unit1.maxHealth) > (unit2.health / unit2.maxHealth) ? unit1 : unit2;
        }

        if (unit1.veterancyLevel !== unit2.veterancyLevel) {
            return unit1.veterancyLevel > unit2.veterancyLevel ? unit1 : unit2;
        }

        if (unit1.timeInService !== unit2.timeInService) {
            return unit1.timeInService > unit2.timeInService ? unit1 : unit2;
        }

        if (unit1.tier !== unit2.tier) {
            return unit1.tier > unit2.tier ? unit1 : unit2;
        }

        // Default to unit1 if all else is equal
        return unit1;
    }
} 