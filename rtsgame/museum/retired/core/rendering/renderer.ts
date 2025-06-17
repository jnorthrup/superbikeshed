import { TILE_SIZE } from '../../config/gameConstants.js';

export class Renderer {
    constructor(canvas, camera) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.camera = camera;
    }

    render(gameState) {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        // Render terrain
        this.renderTerrain(gameState.terrain);
        
        // Render resource nodes
        this.renderResourceNodes(gameState.resourceNodes);
        
        // Render buildings
        for (const building of gameState.buildings) {
            if (this.camera.isInView(building.x, building.y, building.size)) {
                this.renderBuilding(building);
            }
        }
        
        // Render units
        for (const unit of gameState.units) {
            if (this.camera.isInView(unit.x, unit.y, unit.size)) {
                this.renderUnit(unit);
            }
        }
        
        // Render effects
        for (const effect of gameState.effects) {
            if (this.camera.isInView(effect.x, effect.y, effect.size)) {
                this.renderEffect(effect);
            }
        }
        
        // Render projectiles
        for (const projectile of gameState.projectiles) {
            if (this.camera.isInView(projectile.x, projectile.y, projectile.size)) {
                this.renderProjectile(projectile);
            }
        }
        
        // Render captions
        for (const caption of gameState.captions) {
            if (this.camera.isInView(caption.x, caption.y, caption.size)) {
                this.renderCaption(caption);
            }
        }
    }

    renderTerrain(terrain) {
        for (let y = 0; y < terrain.length; y++) {
            for (let x = 0; x < terrain[y].length; x++) {
                const tile = terrain[y][x];
                if (this.camera.isInView(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE)) {
                    const screenPos = this.camera.worldToScreen(x * TILE_SIZE, y * TILE_SIZE);
                    this.ctx.fillStyle = tile.color;
                    this.ctx.fillRect(
                        screenPos.x,
                        screenPos.y,
                        TILE_SIZE * this.camera.zoom,
                        TILE_SIZE * this.camera.zoom
                    );
                }
            }
        }
    }

    renderResourceNodes(resourceNodes) {
        for (const node of resourceNodes) {
            if (this.camera.isInView(node.x, node.y, node.size)) {
                const screenPos = this.camera.worldToScreen(node.x, node.y);
                this.ctx.fillStyle = node.color;
                this.ctx.beginPath();
                this.ctx.arc(
                    screenPos.x,
                    screenPos.y,
                    node.size * this.camera.zoom,
                    0,
                    Math.PI * 2
                );
                this.ctx.fill();
            }
        }
    }

    renderUnit(unit) {
        const screenPos = this.camera.worldToScreen(unit.x, unit.y);
        
        // Draw unit body
        this.ctx.fillStyle = unit.team === 'blue' ? '#4444ff' : '#ff4444';
        this.ctx.beginPath();
        this.ctx.arc(
            screenPos.x,
            screenPos.y,
            unit.size * this.camera.zoom,
            0,
            Math.PI * 2
        );
        this.ctx.fill();
        
        // Draw health bar
        const healthBarWidth = unit.size * 2 * this.camera.zoom;
        const healthBarHeight = 4 * this.camera.zoom;
        const healthPercentage = unit.health / unit.maxHealth;
        
        this.ctx.fillStyle = '#333333';
        this.ctx.fillRect(
            screenPos.x - healthBarWidth / 2,
            screenPos.y - unit.size * this.camera.zoom - healthBarHeight - 2,
            healthBarWidth,
            healthBarHeight
        );
        
        this.ctx.fillStyle = healthPercentage > 0.5 ? '#44ff44' : '#ff4444';
        this.ctx.fillRect(
            screenPos.x - healthBarWidth / 2,
            screenPos.y - unit.size * this.camera.zoom - healthBarHeight - 2,
            healthBarWidth * healthPercentage,
            healthBarHeight
        );
    }

    renderBuilding(building) {
        const screenPos = this.camera.worldToScreen(building.x, building.y);
        
        // Draw building
        this.ctx.fillStyle = building.team === 'blue' ? '#4444ff' : '#ff4444';
        this.ctx.fillRect(
            screenPos.x - building.size * this.camera.zoom,
            screenPos.y - building.size * this.camera.zoom,
            building.size * 2 * this.camera.zoom,
            building.size * 2 * this.camera.zoom
        );
        
        // Draw health bar
        const healthBarWidth = building.size * 2 * this.camera.zoom;
        const healthBarHeight = 4 * this.camera.zoom;
        const healthPercentage = building.health / building.maxHealth;
        
        this.ctx.fillStyle = '#333333';
        this.ctx.fillRect(
            screenPos.x - healthBarWidth / 2,
            screenPos.y - building.size * this.camera.zoom - healthBarHeight - 2,
            healthBarWidth,
            healthBarHeight
        );
        
        this.ctx.fillStyle = healthPercentage > 0.5 ? '#44ff44' : '#ff4444';
        this.ctx.fillRect(
            screenPos.x - healthBarWidth / 2,
            screenPos.y - building.size * this.camera.zoom - healthBarHeight - 2,
            healthBarWidth * healthPercentage,
            healthBarHeight
        );
    }

    renderEffect(effect) {
        const screenPos = this.camera.worldToScreen(effect.x, effect.y);
        
        this.ctx.fillStyle = effect.color;
        this.ctx.globalAlpha = effect.alpha;
        this.ctx.beginPath();
        this.ctx.arc(
            screenPos.x,
            screenPos.y,
            effect.size * this.camera.zoom,
            0,
            Math.PI * 2
        );
        this.ctx.fill();
        this.ctx.globalAlpha = 1.0;
    }

    renderProjectile(projectile) {
        const screenPos = this.camera.worldToScreen(projectile.x, projectile.y);
        
        this.ctx.fillStyle = projectile.color;
        this.ctx.beginPath();
        this.ctx.arc(
            screenPos.x,
            screenPos.y,
            projectile.size * this.camera.zoom,
            0,
            Math.PI * 2
        );
        this.ctx.fill();
    }

    renderCaption(caption) {
        const screenPos = this.camera.worldToScreen(caption.x, caption.y);
        
        this.ctx.font = `${caption.size * this.camera.zoom}px Arial`;
        this.ctx.fillStyle = caption.color;
        this.ctx.textAlign = 'center';
        this.ctx.fillText(
            caption.text,
            screenPos.x,
            screenPos.y
        );
    }
} 