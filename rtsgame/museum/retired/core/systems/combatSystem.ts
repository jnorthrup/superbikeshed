import {
    DAMAGE_TYPES,
    ARMOR_TYPES,
    DAMAGE_ARMOR_INTERACTIONS,
    ARMOR_DAMAGE_REDUCTION_CONSTANT_K,
    MINIMUM_DAMAGE_PERCENTAGE,
    EMP_ENERGY_DRAIN_AMOUNT
} from '../config/combatConstants.js';
import { UNIT_TYPES } from '../config/unitTypes.js'; // To access default damage type if needed

export class CombatSystem {
    constructor() {
        this.projectileSpeed = 200;
        this.projectileLifetime = 2; // seconds
    }

    update(entity, gameContext) {
        if (!entity.combat || !entity.position || !entity.type) return;

        const { combat, position, type } = entity;
        const { target, attackRange, damage, cooldown, lastFireTime } = combat; // damage here is base damage from unitType

        if (!target) return;

        // Check if target is still valid
        if (this.isTargetInvalid(target, gameContext)) {
            combat.target = null;
            return;
        }

        const distance = position.getDistance(target.position);
        const now = performance.now();

        // Check if in range and cooldown is ready
        if (distance <= attackRange && now - lastFireTime >= cooldown * 1000) {
            this.attack(entity, target, gameContext);
            combat.lastFireTime = now;
        }
    }

    isTargetInvalid(target, gameContext) {
        return !target || 
               !target.health || 
               target.health.isDead() || 
               !gameContext.units.includes(target) ||
               !target.type; // Ensure target has a type for armor/damage calculations
    }

    attack(attacker, target, gameContext) {
        const { combat, position, type } = attacker;
        const initialDamage = type.damage || 0; // Use damage from unit's type definition
        const damageType = type.inflictsDamageType || DAMAGE_TYPES.KINETIC;

        // Create projectile
        const projectile = {
            x: position.x,
            y: position.y,
            targetX: target.position.x,
            targetY: target.position.y,
            speed: this.projectileSpeed,
            initialDamage, // Store initial damage before modifications
            damageType,    // Store the type of damage
            team: attacker.team,
            attacker, // Keep reference to attacker for context if needed
            target,
            createdAt: performance.now()
        };

        gameContext.projectiles.push(projectile);

        // Add visual feedback
        if (gameContext.captions) {
            gameContext.captions.push(new gameContext.Caption(
                position.x,
                position.y,
                'Attack!',
                '#f44',
                12
            ));
        }
    }

    updateProjectiles(gameContext) {
        const now = performance.now();
        const deltaTime = gameContext.deltaTime || (1/60);

        for (let i = gameContext.projectiles.length - 1; i >= 0; i--) {
            const projectile = gameContext.projectiles[i];

            // Check lifetime
            if (now - projectile.createdAt > this.projectileLifetime * 1000) {
                gameContext.projectiles.splice(i, 1);
                continue;
            }

            // Move projectile
            const dx = projectile.targetX - projectile.x;
            const dy = projectile.targetY - projectile.y;
            const distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < 5) { // Projectile reaches target vicinity
                if (projectile.target && projectile.target.health && projectile.target.type) {
                    const targetType = projectile.target.type;
                    const armorType = targetType.armorType || ARMOR_TYPES.STANDARD;
                    const armorValue = targetType.armorValue || 0;
                    const projectileDamageType = projectile.damageType;

                    // 1. Calculate Damage Multiplier based on Damage Type vs Armor Type
                    let multiplier = 1.0;
                    if (DAMAGE_ARMOR_INTERACTIONS[projectileDamageType] &&
                        DAMAGE_ARMOR_INTERACTIONS[projectileDamageType][armorType] !== undefined) {
                        multiplier = DAMAGE_ARMOR_INTERACTIONS[projectileDamageType][armorType];
                    }

                    // 2. Calculate Damage Reduction from Armor Value
                    // finalDamage = initialDamage * multiplier * (1 - (armorValue / (armorValue + K)))
                    let damageAfterMultiplier = projectile.initialDamage * multiplier;
                    let finalDamage = damageAfterMultiplier * (1 - (armorValue / (armorValue + ARMOR_DAMAGE_REDUCTION_CONSTANT_K)));

                    // 3. Ensure Minimum Damage
                    const minDamage = projectile.initialDamage * MINIMUM_DAMAGE_PERCENTAGE;
                    finalDamage = Math.max(finalDamage, minDamage);
                    finalDamage = Math.max(1, finalDamage); // Absolute minimum of 1 damage if not 0

                    // 4. EMP Special Handling
                    if (projectileDamageType === DAMAGE_TYPES.EMP) {
                        // EMP damage is expected to be handled by shieldSystem first due to takeDamage logic.
                        // Apply secondary effect: energy drain
                        if (projectile.target.currentEnergy !== undefined) {
                            projectile.target.currentEnergy = Math.max(0, projectile.target.currentEnergy - EMP_ENERGY_DRAIN_AMOUNT);
                            // TODO: Add visual effect for EMP energy drain?
                        }
                        // EMP might also affect computronium core, future enhancement.
                    }

                    // Apply calculated damage
                    const isDead = projectile.target.health.takeDamage(
                        finalDamage,
                        projectile.attacker, // Pass the original attacker
                        gameContext,
                        projectileDamageType // Pass damage type to takeDamage for shield interaction
                    );

                    if (isDead) {
                        this.handleTargetDeath(projectile.target, gameContext);
                    }
                }

                gameContext.projectiles.splice(i, 1);
            } else {
                // Move towards target
                const speed = this.projectileSpeed * deltaTime;
                const ratio = speed / distance;
                projectile.x += dx * ratio;
                projectile.y += dy * ratio;
            }
        }
    }

    handleTargetDeath(target, gameContext) {
        // Remove from units list
        const index = gameContext.units.indexOf(target);
        if (index !== -1) {
            gameContext.units.splice(index, 1);
        }

        // Add death effect
        if (gameContext.effects && target.position) {
            gameContext.effects.push({
                x: target.position.x,
                y: target.position.y,
                type: 'explosion',
                duration: 1,
                createdAt: performance.now()
            });
        }

        // Add visual feedback
        if (gameContext.captions && target.position) {
            gameContext.captions.push(new gameContext.Caption(
                target.position.x,
                target.position.y,
                'Destroyed!',
                '#f44',
                14
            ));
        }
    }
}