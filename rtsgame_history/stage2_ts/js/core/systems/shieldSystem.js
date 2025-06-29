export class ShieldSystem {
    constructor() {
        this.damageTypeInteractions = {
            KINETIC: { shieldFactor: 0.8, bleedThrough: 0.2 },
            ENERGY: { shieldFactor: 0.6, bleedThrough: 0.4 },
            THERMAL: { shieldFactor: 0.7, bleedThrough: 0.3 },
            EMP: { shieldFactor: 0.3, bleedThrough: 0.7 },
            CORROSIVE: { shieldFactor: 0.9, bleedThrough: 0.1 },
            NANITE: { shieldFactor: 1.0, bleedThrough: 0.0 },
            PHASE: { shieldFactor: 0.5, bleedThrough: 0.5 }
        };
    }

    update(deltaTime, entities, gameContext) {
        for (const entity of entities) {
            if (!entity.shield || !entity.shield.isActive) continue;

            // Update shield recharge
            if (entity.shield.currentShieldHP < entity.shield.maxShieldHP) {
                const timeSinceLastHit = performance.now() - entity.shield.lastHitTime;
                if (timeSinceLastHit >= entity.shield.rechargeDelay) {
                    const rechargeAmount = entity.shield.rechargeRate * deltaTime;
                    entity.shield.currentShieldHP = Math.min(
                        entity.shield.maxShieldHP,
                        entity.shield.currentShieldHP + rechargeAmount
                    );
                }
            }

            // Update harmonics if active
            if (entity.shield.harmonicsActive) {
                this.updateHarmonics(entity, deltaTime);
            }
        }
    }

    takeDamage(entity, damage, damageType) {
        if (!entity.shield || !entity.shield.isActive) return damage;

        const interaction = this.damageTypeInteractions[damageType] || this.damageTypeInteractions.KINETIC;
        const shieldDamage = damage * interaction.shieldFactor;
        const bleedThroughDamage = damage * interaction.bleedThrough;

        // Apply shield damage
        entity.shield.currentShieldHP = Math.max(0, entity.shield.currentShieldHP - shieldDamage);
        entity.shield.lastHitTime = performance.now();

        // If shield is depleted, deactivate it
        if (entity.shield.currentShieldHP <= 0) {
            entity.shield.isActive = false;
            return damage; // Full damage passes through when shield is down
        }

        return bleedThroughDamage;
    }

    updateHarmonics(entity, deltaTime) {
        if (!entity.shield.harmonicsActive) return;

        // Gradually adjust harmonics towards target values
        const adjustmentRate = 0.1 * deltaTime;
        for (const [damageType, targetValue] of Object.entries(entity.shield.harmonicTargets)) {
            const currentValue = entity.shield.harmonics[damageType] || 0;
            const difference = targetValue - currentValue;
            entity.shield.harmonics[damageType] = currentValue + (difference * adjustmentRate);
        }

        // Update shield effectiveness based on current harmonics
        this.updateShieldEffectiveness(entity);
    }

    updateShieldEffectiveness(entity) {
        if (!entity.shield.harmonicsActive) return;

        // Calculate overall shield effectiveness based on harmonics
        let totalEffectiveness = 0;
        let totalWeight = 0;

        for (const [damageType, harmonicValue] of Object.entries(entity.shield.harmonics)) {
            const weight = entity.shield.harmonicWeights[damageType] || 1;
            totalEffectiveness += harmonicValue * weight;
            totalWeight += weight;
        }

        // Apply effectiveness modifier to shield
        entity.shield.effectiveness = totalEffectiveness / totalWeight;
    }

    tuneHarmonics(entity, damageType, value) {
        if (!entity.shield) return false;

        // Ensure harmonics object exists
        if (!entity.shield.harmonics) {
            entity.shield.harmonics = {};
        }

        // Set harmonic value for damage type
        entity.shield.harmonics[damageType] = Math.max(0, Math.min(1, value));
        entity.shield.harmonicsActive = true;

        // Update shield effectiveness
        this.updateShieldEffectiveness(entity);
        return true;
    }

    setHarmonicTargets(entity, targets) {
        if (!entity.shield) return false;

        entity.shield.harmonicTargets = targets;
        entity.shield.harmonicsActive = true;
        return true;
    }

    setHarmonicWeights(entity, weights) {
        if (!entity.shield) return false;

        entity.shield.harmonicWeights = weights;
        return true;
    }

    getShieldStatus(entity) {
        if (!entity.shield) return null;

        return {
            currentHP: entity.shield.currentShieldHP,
            maxHP: entity.shield.maxShieldHP,
            isActive: entity.shield.isActive,
            rechargeRate: entity.shield.rechargeRate,
            effectiveness: entity.shield.effectiveness || 1.0,
            harmonics: entity.shield.harmonics || {},
            timeUntilRecharge: Math.max(0, entity.shield.rechargeDelay - (performance.now() - entity.shield.lastHitTime))
        };
    }
} 