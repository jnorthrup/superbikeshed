// Note: Other dependencies like TERRAIN_TYPES, TILE_SIZE, GRID_SIZE, WORLD_SIZE,
// and access to global arrays like 'captions', 'effects', 'buildings', 'resources', 'resourceNodes'
// will be addressed in subsequent refactoring steps. For now, the Unit class methods
// will continue to reference these as if they are in the same scope or global.

class Effect {
    constructor(x, y, targetX, targetY, color, type = 'projectile', size = 10, duration = 20) {
        this.x1 = x; // For projectiles: startX. For explosions: centerX
        this.y1 = y; // For projectiles: startY. For explosions: centerY
        this.x2 = targetX; // For projectiles: endX. Not directly used by explosion type.
        this.y2 = targetY; // For projectiles: endY. Not directly used by explosion type.
        this.color = color;
        this.type = type;
        this.size = size; // Max radius for explosions, or general size
        this.duration = duration;
        this.life = duration; // Lifespan in frames

        this.particles = [];

        if (this.type === 'explosion') {
            this.currentRadius = 0;
            // Create more outward-bursting particles for explosion
            for (let i = 0; i < 15; i++) { // More particles for explosion
                const angle = Math.random() * Math.PI * 2;
                const speed = 2 + Math.random() * 3; // Slightly higher speed
                this.particles.push({
                    x: this.x1, // Start from center
                    y: this.y1,
                    vx: Math.cos(angle) * speed,
                    vy: Math.sin(angle) * speed,
                    life: this.duration * (0.5 + Math.random() * 0.5) // Vary particle life
                });
            }
        } else { // Default 'projectile' type particles
            for (let i = 0; i < 5; i++) {
                this.particles.push({
                    x: this.x2, // Particles at impact point for projectile
                    y: this.y2,
                    vx: (Math.random() - 0.5) * 3, // Slower particle spread for projectile impact
                    vy: (Math.random() - 0.5) * 3,
                    life: this.duration * (0.8 + Math.random() * 0.4)
                });
            }
        }
    }

    update() {
        this.life--;

        if (this.type === 'explosion') {
            // Calculate expansion: currentRadius grows from 0 to this.size
            this.currentRadius = (1 - (this.life / this.duration)) * this.size;
        }

        for (const particle of this.particles) {
            if (particle.life > 0) {
                particle.x += particle.vx;
                particle.y += particle.vy;
                if (this.type === 'explosion') { // Explosion particles might slow down differently
                    particle.vx *= 0.95;
                    particle.vy *= 0.95;
                } else {
                    particle.vx *= 0.9;
                    particle.vy *= 0.9;
                }
                particle.life--;
            }
        }
    }

    draw(ctx, camera) {
        if (this.type === 'explosion') {
            const screenX = (this.x1 - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const screenY = (this.y1 - camera.y) * camera.zoom + camera.canvasHeight / 2;

            if (this.currentRadius > 0) {
                ctx.fillStyle = this.color;
                ctx.globalAlpha = 0.8 * (this.life / this.duration); // Fade out
                ctx.beginPath();
                ctx.arc(screenX, screenY, this.currentRadius * camera.zoom, 0, Math.PI * 2);
                ctx.fill();
            }
        } else { // 'projectile' or default
            const screenX1 = (this.x1 - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const screenY1 = (this.y1 - camera.y) * camera.zoom + camera.canvasHeight / 2;
            const screenX2 = (this.x2 - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const screenY2 = (this.y2 - camera.y) * camera.zoom + camera.canvasHeight / 2;

            // Check if the line is roughly on screen to avoid unnecessary drawing
            if (Math.max(screenX1, screenX2) > 0 && Math.min(screenX1, screenX2) < camera.canvasWidth &&
                Math.max(screenY1, screenY2) > 0 && Math.min(screenY1, screenY2) < camera.canvasHeight) {

                ctx.strokeStyle = this.color;
                ctx.globalAlpha = this.life / this.duration; // Use duration for alpha calculation
                ctx.lineWidth = 2 * camera.zoom;
                ctx.beginPath();
                ctx.moveTo(screenX1, screenY1);
                ctx.lineTo(screenX2, screenY2);
                ctx.stroke();
            }
        }

        // Draw all particles
        ctx.globalAlpha = 1; // Reset alpha for particles or use particle-specific alpha
        for (const particle of this.particles) {
            if (particle.life > 0) {
                const px = (particle.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
                const py = (particle.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
                // Check if particle is on screen
                if (px > 0 && px < camera.canvasWidth && py > 0 && py < camera.canvasHeight) {
                    ctx.fillStyle = this.color;
                    ctx.globalAlpha = particle.life / (this.duration * 0.75); // Particle alpha based on its own life and effect duration
                    ctx.fillRect(px - (1*camera.zoom), py - (1*camera.zoom), (2*camera.zoom), (2*camera.zoom)); // Scale particle size
                }
            }
        }
        ctx.globalAlpha = 1;
    }
}

export { Effect };
