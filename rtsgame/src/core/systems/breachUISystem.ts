import { ProofOfWorkSystem } from './proofOfWorkSystem.js';
import { BreachVisualizationSystem } from './breachVisualizationSystem.js';

export class BreachUISystem {
    constructor() {
        this.uiElements = new Map();
        this.selectedEntity = null;
        this.breachTypeIcons = {
            command_injection: '⚡',
            data_exfiltration: '📊',
            network_disruption: '🌐',
            core_compromise: '💻'
        };
        this.breachTypeNames = {
            command_injection: 'Command Injection',
            data_exfiltration: 'Data Exfiltration',
            network_disruption: 'Network Disruption',
            core_compromise: 'Core Compromise'
        };
        this.breachTypeDescriptions = {
            command_injection: 'Injects false commands or modifies existing ones, affecting command latency and accuracy.',
            data_exfiltration: 'Leaks sensitive information and reveals hidden units or structures.',
            network_disruption: 'Disrupts network communications and affects connected nodes.',
            core_compromise: 'Compromises computronium core efficiency and can change focus modes.'
        };
    }

    update(deltaTime, entities, gameContext) {
        // Update UI elements
        this.updateUIElements(deltaTime, gameContext);
        
        // Check for new breaches
        this.checkNewBreaches(gameContext);
        
        // Update selected entity info
        this.updateSelectedEntityInfo(gameContext);
    }

    updateUIElements(deltaTime, gameContext) {
        for (const [entityId, ui] of this.uiElements) {
            // Update progress bars
            if (ui.progressBar) {
                ui.progressBar.value = ui.breach.progress;
                ui.progressBar.style.width = `${ui.breach.progress * 100}%`;
            }

            // Update severity indicators
            if (ui.severityIndicator) {
                ui.severityIndicator.style.backgroundColor = this.getSeverityColor(ui.breach.severity);
            }

            // Update timers
            if (ui.timer) {
                const timeLeft = Math.max(0, ui.breach.duration - (Date.now() - ui.breach.startTime));
                ui.timer.textContent = this.formatTime(timeLeft);
            }
        }
    }

    checkNewBreaches(gameContext) {
        const powSystem = gameContext.getSystem(ProofOfWorkSystem);
        if (!powSystem) return;

        const activeBreaches = powSystem.activeBreaches;
        for (const [targetId, breach] of activeBreaches) {
            if (!this.uiElements.has(targetId)) {
                this.createBreachUI(targetId, breach);
            }
        }
    }

    createBreachUI(targetId, breach) {
        const ui = {
            breach,
            container: this.createContainer(),
            header: this.createHeader(breach),
            progressBar: this.createProgressBar(),
            severityIndicator: this.createSeverityIndicator(breach),
            timer: this.createTimer(),
            description: this.createDescription(breach),
            effects: this.createEffectsList(breach)
        };

        this.uiElements.set(targetId, ui);
        this.updateUI(ui, breach);
    }

    createContainer() {
        const container = document.createElement('div');
        container.className = 'breach-ui-container';
        container.style.cssText = `
            position: absolute;
            background: rgba(0, 0, 0, 0.8);
            border: 1px solid #444;
            border-radius: 4px;
            padding: 10px;
            color: white;
            font-family: monospace;
            min-width: 200px;
            max-width: 300px;
            z-index: 1000;
        `;
        return container;
    }

    createHeader(breach) {
        const header = document.createElement('div');
        header.className = 'breach-header';
        header.innerHTML = `
            <span class="breach-icon">${this.breachTypeIcons[breach.type]}</span>
            <span class="breach-title">${this.breachTypeNames[breach.type]}</span>
        `;
        header.style.cssText = `
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 8px;
            font-weight: bold;
        `;
        return header;
    }

    createProgressBar() {
        const progressBar = document.createElement('div');
        progressBar.className = 'breach-progress';
        progressBar.style.cssText = `
            height: 4px;
            background: #333;
            border-radius: 2px;
            margin: 8px 0;
        `;
        return progressBar;
    }

    createSeverityIndicator(breach) {
        const indicator = document.createElement('div');
        indicator.className = 'breach-severity';
        indicator.style.cssText = `
            width: 8px;
            height: 8px;
            border-radius: 50%;
            margin-right: 8px;
        `;
        return indicator;
    }

    createTimer() {
        const timer = document.createElement('div');
        timer.className = 'breach-timer';
        timer.style.cssText = `
            font-size: 12px;
            color: #888;
            margin-bottom: 8px;
        `;
        return timer;
    }

    createDescription(breach) {
        const description = document.createElement('div');
        description.className = 'breach-description';
        description.textContent = this.breachTypeDescriptions[breach.type];
        description.style.cssText = `
            font-size: 12px;
            color: #ccc;
            margin-bottom: 8px;
        `;
        return description;
    }

    createEffectsList(breach) {
        const effects = document.createElement('div');
        effects.className = 'breach-effects';
        effects.style.cssText = `
            font-size: 12px;
            color: #aaa;
        `;
        return effects;
    }

    updateUI(ui, breach) {
        // Update severity indicator
        ui.severityIndicator.style.backgroundColor = this.getSeverityColor(breach.severity);

        // Update effects list
        const effects = this.getBreachEffects(breach);
        ui.effects.innerHTML = effects.map(effect => `
            <div class="effect-item">
                <span class="effect-name">${effect.name}</span>
                <span class="effect-value">${effect.value}</span>
            </div>
        `).join('');

        // Position UI element
        this.positionUI(ui);
    }

    getBreachEffects(breach) {
        const effects = [];
        switch (breach.type) {
            case 'command_injection':
                effects.push(
                    { name: 'Command Latency', value: `+${Math.round(breach.severity * 100)}%` },
                    { name: 'Command Accuracy', value: `-${Math.round(breach.severity * 50)}%` }
                );
                break;
            case 'data_exfiltration':
                effects.push(
                    { name: 'Network Security', value: `-${Math.round(breach.severity * 70)}%` },
                    { name: 'Intel Leak Rate', value: `${Math.round(breach.severity * 50)}%` }
                );
                break;
            case 'network_disruption':
                effects.push(
                    { name: 'Network Stability', value: `-${Math.round(breach.severity * 80)}%` },
                    { name: 'Packet Loss', value: `${Math.round(breach.severity * 60)}%` }
                );
                break;
            case 'core_compromise':
                effects.push(
                    { name: 'Core Efficiency', value: `-${Math.round(breach.severity * 90)}%` },
                    { name: 'PoW Contribution', value: `-${Math.round(breach.severity * 70)}%` }
                );
                break;
        }
        return effects;
    }

    getSeverityColor(severity) {
        if (severity >= 0.8) return '#ff4444';
        if (severity >= 0.5) return '#ffaa44';
        if (severity >= 0.3) return '#ffff44';
        return '#44ff44';
    }

    formatTime(ms) {
        const seconds = Math.ceil(ms / 1000);
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
    }

    positionUI(ui) {
        if (!ui.container.parentElement) {
            document.body.appendChild(ui.container);
        }

        // Position based on entity position or screen position
        const position = this.getEntityPosition(ui.breach.targetId);
        if (position) {
            ui.container.style.left = `${position.x}px`;
            ui.container.style.top = `${position.y}px`;
        }
    }

    getEntityPosition(entityId) {
        // This would be implemented to get the screen position of the entity
        // For now, return a default position
        return { x: 100, y: 100 };
    }

    setSelectedEntity(entityId) {
        this.selectedEntity = entityId;
        this.updateSelectedEntityInfo();
    }

    updateSelectedEntityInfo(gameContext) {
        if (!this.selectedEntity) return;

        const powSystem = gameContext.getSystem(ProofOfWorkSystem);
        const breach = powSystem.activeBreaches.get(this.selectedEntity);

        if (breach) {
            const ui = this.uiElements.get(this.selectedEntity);
            if (ui) {
                this.updateUI(ui, breach);
            }
        }
    }

    render(ctx, camera) {
        // Render UI elements
        for (const [entityId, ui] of this.uiElements) {
            if (ui.container) {
                this.positionUI(ui);
            }
        }
    }
} 