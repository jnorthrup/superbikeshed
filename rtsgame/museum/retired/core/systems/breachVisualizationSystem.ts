import { ProofOfWorkSystem } from './proofOfWorkSystem.js';

export class BreachVisualizationSystem {
    constructor() {
        this.visualEffects = new Map();
        this.particleSystems = new Map();
        this.overlayEffects = new Map();
        this.breachColors = {
            command_injection: { primary: '#ff4444', secondary: '#ff8888' },
            data_exfiltration: { primary: '#44ff44', secondary: '#88ff88' },
            network_disruption: { primary: '#4444ff', secondary: '#8888ff' },
            core_compromise: { primary: '#ff44ff', secondary: '#ff88ff' }
        };
    }

    update(deltaTime, entities, gameContext) {
        // Update existing visual effects
        this.updateVisualEffects(deltaTime);
        
        // Update particle systems
        this.updateParticleSystems(deltaTime);
        
        // Update overlay effects
        this.updateOverlayEffects(deltaTime);
        
        // Check for new breaches to visualize
        this.checkNewBreaches(gameContext);
    }

    updateVisualEffects(deltaTime) {
        for (const [entityId, effects] of this.visualEffects) {
            for (const effect of effects) {
                effect.progress += deltaTime / effect.duration;
                if (effect.progress >= 1) {
                    effects.delete(effect);
                } else {
                    this.updateEffectVisuals(effect);
                }
            }
            if (effects.size === 0) {
                this.visualEffects.delete(entityId);
            }
        }
    }

    updateParticleSystems(deltaTime) {
        for (const [entityId, particles] of this.particleSystems) {
            for (const particle of particles) {
                particle.life -= deltaTime;
                if (particle.life <= 0) {
                    particles.delete(particle);
                } else {
                    this.updateParticle(particle, deltaTime);
                }
            }
            if (particles.size === 0) {
                this.particleSystems.delete(entityId);
            }
        }
    }

    updateOverlayEffects(deltaTime) {
        for (const [entityId, overlay] of this.overlayEffects) {
            overlay.duration -= deltaTime;
            if (overlay.duration <= 0) {
                this.overlayEffects.delete(entityId);
            } else {
                this.updateOverlay(overlay, deltaTime);
            }
        }
    }

    checkNewBreaches(gameContext) {
        const powSystem = gameContext.getSystem(ProofOfWorkSystem);
        if (!powSystem) return;

        const activeBreaches = powSystem.activeBreaches;
        for (const [targetId, breach] of activeBreaches) {
            if (!this.visualEffects.has(targetId)) {
                this.createBreachVisuals(targetId, breach);
            }
        }
    }

    createBreachVisuals(targetId, breach) {
        // Create visual effects
        const effects = new Set();
        effects.add({
            type: breach.type,
            severity: breach.severity,
            duration: breach.duration,
            progress: 0,
            color: this.breachColors[breach.type].primary
        });
        this.visualEffects.set(targetId, effects);

        // Create particle system
        const particles = new Set();
        for (let i = 0; i < 20; i++) {
            particles.add(this.createParticle(breach));
        }
        this.particleSystems.set(targetId, particles);

        // Create overlay effect
        this.overlayEffects.set(targetId, {
            type: breach.type,
            severity: breach.severity,
            duration: breach.duration,
            color: this.breachColors[breach.type].secondary,
            alpha: 0.3
        });
    }

    createParticle(breach) {
        const angle = Math.random() * Math.PI * 2;
        const speed = 50 + Math.random() * 50;
        return {
            x: 0,
            y: 0,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            life: 1.0,
            color: this.breachColors[breach.type].primary,
            size: 2 + Math.random() * 3
        };
    }

    updateEffectVisuals(effect) {
        // Update visual properties based on progress and severity
        const intensity = Math.sin(effect.progress * Math.PI) * effect.severity;
        effect.currentColor = this.interpolateColor(
            effect.color,
            this.breachColors[effect.type].secondary,
            intensity
        );
    }

    updateParticle(particle, deltaTime) {
        // Update particle position and properties
        particle.x += particle.vx * deltaTime;
        particle.y += particle.vy * deltaTime;
        particle.life -= deltaTime;
        particle.alpha = particle.life;
        particle.size *= 0.95;
    }

    updateOverlay(overlay, deltaTime) {
        // Update overlay properties
        overlay.alpha = 0.3 * (1 - overlay.duration / overlay.duration);
    }

    interpolateColor(color1, color2, factor) {
        const result = color1.match(/\w\w/g).map((c, i) => {
            const val1 = parseInt(c, 16);
            const val2 = parseInt(color2.match(/\w\w/g)[i], 16);
            const val = Math.round(val1 + (val2 - val1) * factor);
            return val.toString(16).padStart(2, '0');
        });
        return `#${result.join('')}`;
    }

    render(ctx, camera) {
        // Render visual effects
        for (const [entityId, effects] of this.visualEffects) {
            for (const effect of effects) {
                this.renderEffect(ctx, effect, camera);
            }
        }

        // Render particles
        for (const [entityId, particles] of this.particleSystems) {
            for (const particle of particles) {
                this.renderParticle(ctx, particle, camera);
            }
        }

        // Render overlays
        for (const [entityId, overlay] of this.overlayEffects) {
            this.renderOverlay(ctx, overlay, camera);
        }
    }

    renderEffect(ctx, effect, camera) {
        const { x, y } = camera.worldToScreen(effect.x, effect.y);
        const radius = 20 + effect.severity * 10;
        
        ctx.beginPath();
        ctx.arc(x, y, radius, 0, Math.PI * 2);
        ctx.strokeStyle = effect.currentColor;
        ctx.lineWidth = 2;
        ctx.stroke();

        // Add pulsing effect
        const pulseRadius = radius * (1 + Math.sin(effect.progress * Math.PI * 2) * 0.2);
        ctx.beginPath();
        ctx.arc(x, y, pulseRadius, 0, Math.PI * 2);
        ctx.strokeStyle = effect.currentColor;
        ctx.globalAlpha = 0.3;
        ctx.stroke();
        ctx.globalAlpha = 1.0;
    }

    renderParticle(ctx, particle, camera) {
        const { x, y } = camera.worldToScreen(particle.x, particle.y);
        
        ctx.beginPath();
        ctx.arc(x, y, particle.size, 0, Math.PI * 2);
        ctx.fillStyle = particle.color;
        ctx.globalAlpha = particle.alpha;
        ctx.fill();
        ctx.globalAlpha = 1.0;
    }

    renderOverlay(ctx, overlay, camera) {
        const { x, y, width, height } = camera.getViewport();
        
        ctx.fillStyle = overlay.color;
        ctx.globalAlpha = overlay.alpha;
        ctx.fillRect(x, y, width, height);
        ctx.globalAlpha = 1.0;
    }

    getBreachStatus(entityId) {
        const effects = this.visualEffects.get(entityId);
        const particles = this.particleSystems.get(entityId);
        const overlay = this.overlayEffects.get(entityId);

        return {
            hasActiveBreach: effects?.size > 0,
            breachType: effects?.values().next().value?.type,
            severity: effects?.values().next().value?.severity,
            progress: effects?.values().next().value?.progress,
            particleCount: particles?.size,
            hasOverlay: !!overlay
        };
    }
} 