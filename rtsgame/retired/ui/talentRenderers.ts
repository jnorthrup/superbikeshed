// js/ui/talentRenderers.js
// Talent-specific renderers for introspection

import { TextComponent, BorderLayoutContainer, BorderRegion } from './borderLayout.js';

// Base introspection renderer - provides common functionality
export class BaseIntrospectionRenderer {
    constructor(target, options = {}) {
        this.target = target;
        this.width = options.width || 300;
        this.height = options.height || 250;
        this.backgroundColor = options.backgroundColor || 'rgba(40, 40, 50, 0.95)';
        this.updateInterval = options.updateInterval || 500; // ms
        this.lastUpdate = 0;
    }

    createWindow(x, y, gameContext) {
        const window = new BorderLayoutContainer(x, y, this.width, this.height, {
            title: this.getTitle(),
            backgroundColor: this.backgroundColor,
            borderColor: this.getBorderColor(),
            resizable: true,
            movable: true,
            closable: true
        });

        this.setupLayout(window, gameContext);
        this.updateContent(window, gameContext);
        
        return window;
    }

    getTitle() {
        return `${this.target.team.toUpperCase()} ${this.target.type.name}`;
    }

    getBorderColor() {
        return this.target.team === 'blue' ? '#4488ff' : '#ff4444';
    }

    setupLayout(window, gameContext) {
        // Basic layout - override in subclasses
        this.addHeader(window);
        this.addMainContent(window, gameContext);
        this.addFooter(window);
    }

    addHeader(window) {
        const headerText = this.getHeaderText();
        window.addComponent(
            new TextComponent(headerText, {
                backgroundColor: 'rgba(60, 60, 80, 0.8)',
                alignment: 'center',
                fontSize: 11,
                color: '#ffffff'
            }),
            BorderRegion.NORTH
        );
        window.setRegionSize(BorderRegion.NORTH, 35);
    }

    addMainContent(window, gameContext) {
        const contentText = this.getMainContentText(gameContext);
        window.addComponent(
            new TextComponent(contentText, {
                backgroundColor: 'rgba(50, 50, 70, 0.8)',
                fontSize: 10,
                padding: 8,
                color: '#cccccc'
            }),
            BorderRegion.CENTER
        );
    }

    addFooter(window) {
        const footerText = this.getFooterText();
        if (footerText) {
            window.addComponent(
                new TextComponent(footerText, {
                    backgroundColor: 'rgba(70, 60, 50, 0.8)',
                    alignment: 'center',
                    fontSize: 9,
                    color: '#aaaaaa'
                }),
                BorderRegion.SOUTH
            );
            window.setRegionSize(BorderRegion.SOUTH, 25);
        }
    }

    getHeaderText() {
        const statusIcon = this.target.hp < this.target.maxHp * 0.3 ? '⚠️' : '●';
        return `${statusIcon} ${this.target.type.name} Status`;
    }

    getMainContentText(gameContext) {
        return `HP: ${Math.ceil(this.target.hp)}/${this.target.maxHp}\n` +
               `Position: ${Math.floor(this.target.x)}, ${Math.floor(this.target.y)}\n` +
               `Team: ${this.target.team.toUpperCase()}`;
    }

    getFooterText() {
        const time = new Date().toLocaleTimeString();
        return `Updated: ${time}`;
    }

    updateContent(window, gameContext) {
        const now = Date.now();
        if (now - this.lastUpdate < this.updateInterval) return;

        // Update main content
        const centerComponent = window.regions.get(BorderRegion.CENTER);
        if (centerComponent) {
            centerComponent.text = this.getMainContentText(gameContext);
        }

        // Update footer
        const southComponent = window.regions.get(BorderRegion.SOUTH);
        if (southComponent) {
            southComponent.text = this.getFooterText();
        }

        this.lastUpdate = now;
    }
}

// Commander-specific renderer
export class CommanderRenderer extends BaseIntrospectionRenderer {
    constructor(target, options = {}) {
        super(target, options);
        this.width = 400;
        this.height = 350;
        this.backgroundColor = 'rgba(60, 40, 40, 0.95)';
    }

    getTitle() {
        return `⚔️ ${super.getTitle()} - ACU Command Interface`;
    }

    setupLayout(window, gameContext) {
        super.setupLayout(window, gameContext);
        
        // Add side panel for commander abilities
        this.addAbilitiesPanel(window);
        window.setRegionSize(BorderRegion.EAST, 120);
    }

    addAbilitiesPanel(window) {
        const abilities = this.getAbilitiesText();
        window.addComponent(
            new TextComponent(abilities, {
                backgroundColor: 'rgba(80, 50, 50, 0.8)',
                fontSize: 9,
                padding: 6,
                color: '#ffdddd'
            }),
            BorderRegion.EAST
        );
    }

    getMainContentText(gameContext) {
        let content = super.getMainContentText(gameContext);
        
        // Add commander-specific info
        if (this.target.shields > 0) {
            content += `\nShields: ${Math.ceil(this.target.shields)}/${this.target.maxShields}`;
        }

        if (this.target.constructionTask) {
            const progress = Math.floor((this.target.constructionTask.progress / this.target.constructionTask.type.buildTime) * 100);
            content += `\n\n🔨 BUILDING:\n${this.target.constructionTask.type.name}\nProgress: ${progress}%`;
        }

        if (this.target.grenadeCooldown > 0) {
            content += `\n\n💣 Grenade: ${Math.ceil(this.target.grenadeCooldown / 60)}s`;
        } else if (this.target.type.grenadeAbility) {
            content += `\n\n💣 Grenade: READY`;
        }

        if (this.target.target) {
            content += `\n\n🎯 Target: ${this.target.target.type ? this.target.target.type.name : 'Position'}`;
        }

        return content;
    }

    getAbilitiesText() {
        return `ABILITIES:\n\n` +
               `🔨 Build\nStructures\n\n` +
               `💣 Grenade\nLauncher\n\n` +
               `🛡️ Personal\nShields\n\n` +
               `📡 Command\nRadius`;
    }
}

// Engineer-specific renderer
export class EngineerRenderer extends BaseIntrospectionRenderer {
    constructor(target, options = {}) {
        super(target, options);
        this.width = 350;
        this.height = 280;
        this.backgroundColor = 'rgba(40, 60, 40, 0.95)';
    }

    getTitle() {
        return `🔧 ${super.getTitle()} - Support Unit`;
    }

    setupLayout(window, gameContext) {
        super.setupLayout(window, gameContext);
        
        // Add task queue panel
        this.addTaskPanel(window, gameContext);
        window.setRegionSize(BorderRegion.WEST, 100);
    }

    addTaskPanel(window, gameContext) {
        const tasks = this.getTasksText(gameContext);
        window.addComponent(
            new TextComponent(tasks, {
                backgroundColor: 'rgba(50, 80, 50, 0.8)',
                fontSize: 9,
                padding: 6,
                color: '#ddffdd'
            }),
            BorderRegion.WEST
        );
    }

    getMainContentText(gameContext) {
        let content = super.getMainContentText(gameContext);
        
        // Add engineer-specific info
        content += `\nRole: Support Engineer`;
        
        if (this.target.task) {
            content += `\n\n🔧 CURRENT TASK:\n${this.target.task}`;
        }

        // Find nearby damaged buildings
        const nearbyBuildings = gameContext.buildings.filter(b => 
            b.team === this.target.team && 
            b.hp < b.maxHp &&
            Math.sqrt((b.x - this.target.x)**2 + (b.y - this.target.y)**2) < 200
        );

        if (nearbyBuildings.length > 0) {
            content += `\n\n🏗️ REPAIR TARGETS:\n`;
            nearbyBuildings.slice(0, 3).forEach(b => {
                const damage = Math.floor((1 - b.hp/b.maxHp) * 100);
                content += `${b.type.name} (${damage}% dmg)\n`;
            });
        }

        // Find nearby resource nodes
        const nearbyNodes = gameContext.resourceNodes?.filter(n => 
            !n.occupied && 
            n.amount > 0 &&
            Math.sqrt((n.x - this.target.x)**2 + (n.y - this.target.y)**2) < 300
        ) || [];

        if (nearbyNodes.length > 0) {
            content += `\n\n⛏️ BUILD OPPORTUNITIES:\n`;
            nearbyNodes.slice(0, 2).forEach(n => {
                const distance = Math.floor(Math.sqrt((n.x - this.target.x)**2 + (n.y - this.target.y)**2));
                content += `${n.type} extractor (${distance}u)\n`;
            });
        }

        return content;
    }

    getTasksText(gameContext) {
        return `TASKS:\n\n` +
               `🔧 Repair\nBuildings\n\n` +
               `🏗️ Build\nExtractors\n\n` +
               `⚡ Assist\nConstruction`;
    }
}

// Combat unit renderer
export class CombatUnitRenderer extends BaseIntrospectionRenderer {
    constructor(target, options = {}) {
        super(target, options);
        this.width = 320;
        this.height = 270;
        this.backgroundColor = 'rgba(60, 40, 40, 0.95)';
    }

    getTitle() {
        return `⚔️ ${super.getTitle()} - Combat Unit`;
    }

    setupLayout(window, gameContext) {
        super.setupLayout(window, gameContext);
        
        // Add combat stats panel
        this.addCombatPanel(window);
        window.setRegionSize(BorderRegion.EAST, 110);
    }

    addCombatPanel(window) {
        const stats = this.getCombatStatsText();
        window.addComponent(
            new TextComponent(stats, {
                backgroundColor: 'rgba(80, 50, 50, 0.8)',
                fontSize: 9,
                padding: 6,
                color: '#ffdddd'
            }),
            BorderRegion.EAST
        );
    }

    getMainContentText(gameContext) {
        let content = super.getMainContentText(gameContext);
        
        // Add combat-specific info
        content += `\nRole: ${this.target.tacticalRole || 'Infantry'}`;
        content += `\nSpeed: ${this.target.type.speed}`;
        content += `\nRange: ${this.target.type.range}`;
        content += `\nDamage: ${this.target.type.damage}`;

        if (this.target.shields > 0) {
            content += `\nShields: ${Math.ceil(this.target.shields)}/${this.target.maxShields}`;
        }

        if (this.target.cooldown > 0) {
            content += `\n\n⏰ Weapon Cooldown: ${this.target.cooldown}`;
        }

        if (this.target.target) {
            const distance = Math.floor(Math.sqrt(
                (this.target.target.x - this.target.x)**2 + 
                (this.target.target.y - this.target.y)**2
            ));
            const inRange = distance <= this.target.type.range ? '✅' : '❌';
            content += `\n\n🎯 TARGET:\n${this.target.target.type ? this.target.target.type.name : 'Position'}`;
            content += `\nDistance: ${distance}u ${inRange}`;
        } else if (this.target.patrolTarget) {
            content += `\n\n🚶 PATROLLING`;
        } else {
            content += `\n\n💤 IDLE`;
        }

        // Pathfinding info
        if (this.target.path && this.target.path.length > 0) {
            content += `\n\n🗺️ Path: ${this.target.path.length} waypoints`;
            content += `\nNext: WP ${this.target.currentWaypointIndex + 1}`;
        }

        return content;
    }

    getCombatStatsText() {
        return `COMBAT:\n\n` +
               `⚔️ Attack\n${this.target.type.damage} dmg\n\n` +
               `🎯 Range\n${this.target.type.range}u\n\n` +
               `⚡ Speed\n${this.target.type.speed}u/s\n\n` +
               `🛡️ Armor\nStandard`;
    }
}

// Factory building renderer
export class FactoryRenderer extends BaseIntrospectionRenderer {
    constructor(target, options = {}) {
        super(target, options);
        this.width = 380;
        this.height = 300;
        this.backgroundColor = 'rgba(40, 40, 60, 0.95)';
    }

    getTitle() {
        return `🏭 ${super.getTitle()} - Production Facility`;
    }

    setupLayout(window, gameContext) {
        super.setupLayout(window, gameContext);
        
        // Add production queue
        this.addProductionPanel(window, gameContext);
        window.setRegionSize(BorderRegion.SOUTH, 80);
    }

    addProductionPanel(window, gameContext) {
        const production = this.getProductionText(gameContext);
        window.addComponent(
            new TextComponent(production, {
                backgroundColor: 'rgba(50, 50, 80, 0.8)',
                fontSize: 9,
                padding: 6,
                color: '#ddddff'
            }),
            BorderRegion.SOUTH
        );
    }

    getMainContentText(gameContext) {
        let content = super.getMainContentText(gameContext);
        
        // Add factory-specific info
        content += `\nType: ${this.target.type.name}`;
        
        if (this.target.type.produces) {
            content += `\n\nPRODUCES:\n${this.target.type.produces.name}`;
            content += `\nBuild Time: ${this.target.type.produces.buildTime || 'N/A'}s`;
            content += `\nCost: ${this.target.type.produces.cost?.mass || 0}M / ${this.target.type.produces.cost?.energy || 0}E`;
        }

        // Resource costs and availability
        const teamResources = gameContext.resources[this.target.team];
        if (teamResources) {
            content += `\n\nRESOURCES:\nMass: ${Math.floor(teamResources.mass)}\nEnergy: ${Math.floor(teamResources.energy)}`;
        }

        // Production status
        if (this.target.productionQueue && this.target.productionQueue.length > 0) {
            content += `\n\n🔄 PRODUCING:\n${this.target.productionQueue[0].name}`;
            if (this.target.productionProgress) {
                const progress = Math.floor(this.target.productionProgress * 100);
                content += `\nProgress: ${progress}%`;
            }
        } else {
            content += `\n\n💤 IDLE`;
        }

        return content;
    }

    getProductionText(gameContext) {
        return `PRODUCTION QUEUE:\n\n` +
               `[Empty]\n\n` +
               `Click to add units to queue\n` +
               `Shift+Click for multiple`;
    }
}

// Talent renderer factory
export class TalentRendererFactory {
    static createRenderer(target, gameContext) {
        if (!target.type) {
            return new BaseIntrospectionRenderer(target);
        }

        // Unit renderers
        if (target.hp !== undefined) { // It's a unit
            if (target.type.name === 'Commander' || target.type.name === 'ACU') {
                return new CommanderRenderer(target);
            } else if (target.type.name === 'Engineer') {
                return new EngineerRenderer(target);
            } else if (target.type.support) {
                return new EngineerRenderer(target); // Use engineer renderer for support units
            } else {
                return new CombatUnitRenderer(target);
            }
        }
        
        // Building renderers
        else {
            if (target.type.name.includes('Factory')) {
                return new FactoryRenderer(target);
            } else {
                return new BaseIntrospectionRenderer(target);
            }
        }
    }
}

// Introspection window manager
export class IntrospectionManager {
    constructor(windowManager) {
        this.windowManager = windowManager;
        this.activeIntrospections = new Map(); // target -> {window, renderer}
        this.updateInterval = 16; // 60fps
        this.lastUpdate = 0;
    }

    createIntrospectionWindow(target, x, y, gameContext) {
        // Close existing window for this target
        this.closeIntrospectionWindow(target);

        // Create appropriate renderer
        const renderer = TalentRendererFactory.createRenderer(target, gameContext);
        const window = renderer.createWindow(x, y, gameContext);

        // Store reference
        this.activeIntrospections.set(target, { window, renderer });

        // Add to window manager
        this.windowManager.addWindow(window);

        return window;
    }

    closeIntrospectionWindow(target) {
        const introspection = this.activeIntrospections.get(target);
        if (introspection) {
            this.windowManager.removeWindow(introspection.window);
            this.activeIntrospections.delete(target);
        }
    }

    update(gameContext) {
        const now = Date.now();
        if (now - this.lastUpdate < this.updateInterval) return;

        // Update all active introspection windows
        for (const [target, introspection] of this.activeIntrospections) {
            // Check if target still exists
            if (target.hp !== undefined && target.hp <= 0) {
                this.closeIntrospectionWindow(target);
                continue;
            }

            // Update content
            introspection.renderer.updateContent(introspection.window, gameContext);
        }

        this.lastUpdate = now;
    }

    getIntrospectionWindow(target) {
        const introspection = this.activeIntrospections.get(target);
        return introspection ? introspection.window : null;
    }

    hasIntrospectionWindow(target) {
        return this.activeIntrospections.has(target);
    }
}