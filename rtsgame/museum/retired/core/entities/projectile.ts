// js/core/projectile.js
import { TILE_SIZE, GRID_SIZE, WORLD_SIZE } from '../../config/gameConstants.js'; // If needed for drawing or physics
// Effect constructor will be passed via gameContext in its update or on impact.

class GrenadeProjectile {
    constructor(startX, startY, targetX, targetY, team, abilityConfig) {
        this.x = startX;
        this.y = startY;
        this.targetX = targetX;
        this.targetY = targetY;
        this.team = team; // To know who fired it, potentially for friendly fire logic later

        this.speed = abilityConfig.projectileSpeed;
        this.radius = abilityConfig.radius; // AOE radius on impact
        this.damage = abilityConfig.damage; // AOE damage on impact
        this.effectColor = abilityConfig.effectColor; // For explosion visual

        const dx = this.targetX - this.x;
        const dy = this.targetY - this.y;
        this.angle = Math.atan2(dy, dx);

        this.vx = Math.cos(this.angle) * this.speed;
        this.vy = Math.sin(this.angle) * this.speed;

        this.isExpired = false; // Flag to mark for removal after explosion
        this.distanceToTarget = Math.sqrt(dx*dx + dy*dy);
    }

    update(gameContext) {
        if (this.isExpired) return;

        // Move projectile
        this.x += this.vx;
        this.y += this.vy;

        // Check distance to target
        const dx = this.targetX - this.x;
        const dy = this.targetY - this.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        // A simple way to check if we've reached or passed the target
        // Or if distance traveled exceeds initial distance (in case of high speed/low framerate)
        this.distanceToTarget -= this.speed;


        if (dist < this.speed || this.distanceToTarget <= 0) { // Reached target (approx.)
            this.x = this.targetX; // Snap to target
            this.y = this.targetY;
            this.explode(gameContext);
            this.isExpired = true;
        }

        // Optional: Boundary checks if projectiles should expire if they go off-map
        if (this.x < 0 || this.x > WORLD_SIZE || this.y < 0 || this.y > WORLD_SIZE) {
            // this.isExpired = true; // Or handle differently
        }
    }

    explode(gameContext) {
        const { performAoeDamage, Effect, effects, addEvent } = gameContext;

        console.log(`Grenade exploded at ${this.x.toFixed(0)}, ${this.y.toFixed(0)}`);

        // Perform AOE damage
        if (performAoeDamage) {
            performAoeDamage(this.x, this.y, this.radius, this.damage, this.team, gameContext);
        } else {
            console.error("performAoeDamage function not found on gameContext!");
        }

        // Create visual explosion effect
        if (Effect && effects) {
            // Simple explosion: one large expanding effect
            const explosion = new Effect(this.x, this.y, this.x, this.y, this.effectColor, 'explosion', this.radius * 2, 30); // type, size, duration
            effects.push(explosion);
        } else {
            console.error("Effect class or gameContext.effects array not found for explosion visual!");
        }

        if (addEvent) {
            addEvent(gameContext, 'explosion', `Grenade impact!`, 2, { x: this.x, y: this.y });
        }
    }

    draw(ctx, camera) {
        if (this.isExpired) return;

        const screenX = (this.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
        const screenY = (this.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
        const size = 5 * camera.zoom; // Simple fixed size for projectile visual

        // Check if on screen
        if (screenX < -size || screenX > camera.canvasWidth + size ||
            screenY < -size || screenY > camera.canvasHeight + size) {
            return;
        }

        ctx.fillStyle = this.effectColor || '#FFFFFF'; // Use effect color or white
        ctx.beginPath();
        ctx.arc(screenX, screenY, size, 0, Math.PI * 2);
        ctx.fill();
    }
}

export { GrenadeProjectile };
