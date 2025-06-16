export class WeaponSystem {
    constructor() {
        this.projectileLifetime = 2000; // 2 seconds
        this.projectileSpeed = 200;
    }

    update(deltaTime, entities, gameContext) {
        // Update weapon capacitors
        for (const entity of entities) {
            if (!entity.weapon) continue;

            // Recharge capacitor
            if (entity.weapon.capacitorCharge < entity.weapon.capacitorSize) {
                const rechargeAmount = entity.weapon.energyCost * deltaTime;
                entity.weapon.capacitorCharge = Math.min(
                    entity.weapon.capacitorSize,
                    entity.weapon.capacitorCharge + rechargeAmount
                );
            }

            // Update weapon cooldown
            if (entity.weapon.lastFireTime > 0) {
                const timeSinceLastFire = performance.now() - entity.weapon.lastFireTime;
                if (timeSinceLastFire >= (1000 / entity.weapon.fireRate)) {
                    entity.weapon.canFire = true;
                }
            }
        }

        // Update projectiles
        this.updateProjectiles(gameContext);
    }

    updateProjectiles(gameContext) {
        const now = performance.now();
        const projectiles = gameContext.projectiles || [];

        for (let i = projectiles.length - 1; i >= 0; i--) {
            const projectile = projectiles[i];
            const age = now - projectile.createdAt;

            // Remove expired projectiles
            if (age > this.projectileLifetime) {
                projectiles.splice(i, 1);
                continue;
            }

            // Update projectile position
            const dx = projectile.targetX - projectile.x;
            const dy = projectile.targetY - projectile.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            const speed = this.projectileSpeed * (gameContext.deltaTime / 1000);

            if (distance <= speed) {
                // Projectile reached target
                this.handleProjectileHit(projectile, gameContext);
                projectiles.splice(i, 1);
            } else {
                // Move projectile
                const ratio = speed / distance;
                projectile.x += dx * ratio;
                projectile.y += dy * ratio;
            }
        }
    }

    handleProjectileHit(projectile, gameContext) {
        if (!projectile.target) return;

        // Apply damage to target
        if (projectile.target.health) {
            const damage = projectile.damage;
            const damageType = projectile.damageType;

            // Check for shield first
            if (projectile.target.shield && projectile.target.shield.isActive) {
                const shieldSystem = gameContext.systems.shield;
                const remainingDamage = shieldSystem.takeDamage(projectile.target, damage, damageType);
                if (remainingDamage > 0) {
                    projectile.target.health.currentHP -= remainingDamage;
                }
            } else {
                projectile.target.health.currentHP -= damage;
            }

            // Check for target death
            if (projectile.target.health.currentHP <= 0) {
                this.handleTargetDeath(projectile.target, gameContext);
            }
        }

        // Add impact effect
        this.addImpactEffect(projectile, gameContext);
    }

    handleTargetDeath(target, gameContext) {
        // Remove from units list
        const unitIndex = gameContext.units.indexOf(target);
        if (unitIndex !== -1) {
            gameContext.units.splice(unitIndex, 1);
        }

        // Add death effect
        this.addDeathEffect(target, gameContext);
    }

    addImpactEffect(projectile, gameContext) {
        const effect = {
            type: 'impact',
            x: projectile.x,
            y: projectile.y,
            damageType: projectile.damageType,
            createdAt: performance.now()
        };

        if (!gameContext.effects) {
            gameContext.effects = [];
        }
        gameContext.effects.push(effect);
    }

    addDeathEffect(target, gameContext) {
        const effect = {
            type: 'death',
            x: target.position.x,
            y: target.position.y,
            team: target.team,
            createdAt: performance.now()
        };

        if (!gameContext.effects) {
            gameContext.effects = [];
        }
        gameContext.effects.push(effect);
    }

    fireWeapon(entity, target, gameContext) {
        if (!entity.weapon || !entity.weapon.canFire) return null;

        // Check energy cost
        if (entity.weapon.capacitorCharge < entity.weapon.energyCost) return null;

        // Create projectile
        const projectile = {
            x: entity.position.x,
            y: entity.position.y,
            targetX: target.position.x,
            targetY: target.position.y,
            speed: entity.weapon.projectileSpeed || this.projectileSpeed,
            damage: entity.weapon.baseDamage,
            damageType: entity.weapon.damageType,
            team: entity.team,
            target,
            createdAt: performance.now()
        };

        // Update weapon state
        entity.weapon.capacitorCharge -= entity.weapon.energyCost;
        entity.weapon.lastFireTime = performance.now();
        entity.weapon.canFire = false;

        // Add projectile to game context
        if (!gameContext.projectiles) {
            gameContext.projectiles = [];
        }
        gameContext.projectiles.push(projectile);

        return projectile;
    }

    getWeaponStatus(entity) {
        if (!entity.weapon) return null;

        return {
            canFire: entity.weapon.canFire,
            capacitorCharge: entity.weapon.capacitorCharge,
            capacitorSize: entity.weapon.capacitorSize,
            lastFireTime: entity.weapon.lastFireTime,
            timeUntilReady: Math.max(0, (1000 / entity.weapon.fireRate) - (performance.now() - entity.weapon.lastFireTime))
        };
    }
} 