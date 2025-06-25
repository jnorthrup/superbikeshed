export class CombatSystem {
    constructor() {
        this.projectileSpeed = 200;
        this.projectileLifetime = 2; // seconds
    }

    update(entity, gameContext) {
        if (!entity.combat || !entity.position) return;

        const { combat, position } = entity;
        const { target, attackRange, damage, cooldown, lastFireTime } = combat;

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
               !gameContext.units.includes(target);
    }

    attack(attacker, target, gameContext) {
        const { combat, position } = attacker;
        const { damage } = combat;

        // Create projectile
        const projectile = {
            x: position.x,
            y: position.y,
            targetX: target.position.x,
            targetY: target.position.y,
            speed: this.projectileSpeed,
            damage,
            team: attacker.team,
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

            if (distance < 5) {
                // Hit target
                if (projectile.target && projectile.target.health) {
                    const isDead = projectile.target.health.takeDamage(
                        projectile.damage,
                        gameContext.units.find(u => u.team === projectile.team),
                        gameContext
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
        if (gameContext.effects) {
            gameContext.effects.push({
                x: target.position.x,
                y: target.position.y,
                type: 'explosion',
                duration: 1,
                createdAt: performance.now()
            });
        }

        // Add visual feedback
        if (gameContext.captions) {
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