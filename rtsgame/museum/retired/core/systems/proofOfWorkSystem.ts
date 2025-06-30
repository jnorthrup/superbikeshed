import { ComputroniumComponent } from '../components/computroniumComponent.js';

export class ProofOfWorkSystem {
    constructor() {
        this.difficultyScaling = 1.0;
        this.networkHashRate = 0;
        this.breachAttempts = new Map();
        this.defensiveNodes = new Set();
        this.offensiveNodes = new Set();
        this.activeBreaches = new Map();
        this.breachTypes = {
            CMD_INJECTION: 'command_injection',
            DATA_EXFILTRATION: 'data_exfiltration',
            NETWORK_DISRUPTION: 'network_disruption',
            CORE_COMPROMISE: 'core_compromise'
        };
    }

    update(deltaTime, entities, gameContext) {
        // Update network hash rate
        this.updateNetworkHashRate(entities);
        
        // Process defensive PoW
        this.processDefensivePoW(deltaTime, entities, gameContext);
        
        // Process offensive PoW
        this.processOffensivePoW(deltaTime, entities, gameContext);
        
        // Update breach attempts
        this.updateBreachAttempts(deltaTime);
    }

    updateNetworkHashRate(entities) {
        let totalHashRate = 0;
        for (const entity of entities) {
            const computronium = entity.getComponent(ComputroniumComponent);
            if (computronium && computronium.contributesToPoW) {
                totalHashRate += computronium.powContribution * computronium.efficiency;
            }
        }
        this.networkHashRate = totalHashRate;
    }

    processDefensivePoW(deltaTime, entities, gameContext) {
        for (const entity of entities) {
            const computronium = entity.getComponent(ComputroniumComponent);
            if (!computronium || !computronium.contributesToPoW) continue;

            // Calculate defensive contribution
            const defensiveContribution = this.calculateDefensiveContribution(computronium, deltaTime);
            
            // Apply defensive benefits
            this.applyDefensiveBenefits(entity, defensiveContribution, gameContext);
        }
    }

    processOffensivePoW(deltaTime, entities, gameContext) {
        for (const entity of entities) {
            const computronium = entity.getComponent(ComputroniumComponent);
            if (!computronium || !computronium.contributesToPoW) continue;

            // Calculate offensive contribution
            const offensiveContribution = this.calculateOffensiveContribution(computronium, deltaTime);
            
            // Apply offensive effects
            this.applyOffensiveEffects(entity, offensiveContribution, gameContext);
        }
    }

    calculateDefensiveContribution(computronium, deltaTime) {
        const baseContribution = computronium.powContribution * computronium.efficiency;
        const focusBonus = computronium.focusMode === 'C&C' ? 1.5 : 1.0;
        return baseContribution * focusBonus * deltaTime;
    }

    calculateOffensiveContribution(computronium, deltaTime) {
        const baseContribution = computronium.powContribution * computronium.efficiency;
        const focusBonus = computronium.focusMode === 'Offensive' ? 1.5 : 1.0;
        return baseContribution * focusBonus * deltaTime;
    }

    applyDefensiveBenefits(entity, contribution, gameContext) {
        // Enhance C&C network security
        const securityBoost = contribution * 0.1;
        if (entity.commandAndControl) {
            entity.commandAndControl.networkSecurity += securityBoost;
        }

        // Reduce breach vulnerability
        const vulnerabilityReduction = contribution * 0.05;
        if (entity.breachVulnerability) {
            entity.breachVulnerability -= vulnerabilityReduction;
        }
    }

    applyOffensiveEffects(entity, contribution, gameContext) {
        // Find potential targets
        const targets = this.findVulnerableTargets(gameContext);
        
        // Apply breach attempts
        for (const target of targets) {
            this.attemptBreach(target, contribution, entity);
        }
    }

    findVulnerableTargets(gameContext) {
        return gameContext.entities.filter(entity => {
            const computronium = entity.getComponent(ComputroniumComponent);
            return computronium && computronium.contributesToPoW && 
                   entity.breachVulnerability > 0;
        });
    }

    attemptBreach(target, contribution, attacker) {
        const breachId = `${attacker.id}-${target.id}`;
        if (!this.breachAttempts.has(breachId)) {
            this.breachAttempts.set(breachId, {
                progress: 0,
                attacker: attacker.id,
                target: target.id,
                startTime: Date.now()
            });
        }

        const attempt = this.breachAttempts.get(breachId);
        attempt.progress += contribution;

        // Check for successful breach
        if (attempt.progress >= target.breachVulnerability) {
            this.handleSuccessfulBreach(target, attacker);
            this.breachAttempts.delete(breachId);
        }
    }

    handleSuccessfulBreach(target, attacker) {
        // Determine breach type based on attacker's capabilities and target's vulnerabilities
        const breachType = this.determineBreachType(attacker, target);
        
        // Create breach effect
        const breachEffect = {
            type: breachType,
            severity: this.calculateBreachSeverity(attacker, target),
            duration: this.calculateBreachDuration(breachType),
            startTime: Date.now(),
            cascadingEffects: new Set()
        };

        // Apply primary breach effects
        this.applyBreachEffects(target, breachEffect);

        // Check for cascading effects
        this.checkCascadingEffects(target, breachEffect);

        // Store active breach
        this.activeBreaches.set(target.id, breachEffect);

        // Notify game context
        if (target.gameContext) {
            target.gameContext.addEvent({
                type: 'breach',
                target: target.id,
                attacker: attacker.id,
                breachType: breachType,
                severity: breachEffect.severity,
                timestamp: Date.now()
            });
        }

        // Increase vulnerability for future breaches
        target.breachVulnerability *= 1.2;
    }

    determineBreachType(attacker, target) {
        const attackerComputronium = attacker.getComponent(ComputroniumComponent);
        const targetComputronium = target.getComponent(ComputroniumComponent);

        // Weight different factors to determine breach type
        const weights = {
            [this.breachTypes.CMD_INJECTION]: 0,
            [this.breachTypes.DATA_EXFILTRATION]: 0,
            [this.breachTypes.NETWORK_DISRUPTION]: 0,
            [this.breachTypes.CORE_COMPROMISE]: 0
        };

        // Command injection more likely if attacker has high C&C focus
        if (attackerComputronium.focusMode === 'C&C') {
            weights[this.breachTypes.CMD_INJECTION] += 2;
        }

        // Data exfiltration more likely if target has valuable data
        if (target.commandAndControl && target.commandAndControl.networkSecurity > 0.7) {
            weights[this.breachTypes.DATA_EXFILTRATION] += 1.5;
        }

        // Network disruption more likely if target is a network node
        if (target.isNetworkNode) {
            weights[this.breachTypes.NETWORK_DISRUPTION] += 2;
        }

        // Core compromise more likely if target has high-level computronium
        if (targetComputronium && targetComputronium.level >= 3) {
            weights[this.breachTypes.CORE_COMPROMISE] += 1.5;
        }

        // Select breach type based on weights
        const totalWeight = Object.values(weights).reduce((a, b) => a + b, 0);
        let random = Math.random() * totalWeight;
        for (const [type, weight] of Object.entries(weights)) {
            if (random < weight) return type;
            random -= weight;
        }
        return this.breachTypes.CMD_INJECTION; // Default fallback
    }

    calculateBreachSeverity(attacker, target) {
        const attackerComputronium = attacker.getComponent(ComputroniumComponent);
        const targetComputronium = target.getComponent(ComputroniumComponent);

        let severity = 0.5; // Base severity

        // Attacker factors
        severity += (attackerComputronium.level * 0.1);
        severity += (attackerComputronium.efficiency * 0.2);
        if (attackerComputronium.focusMode === 'Offensive') {
            severity += 0.2;
        }

        // Target factors
        if (targetComputronium) {
            severity -= (targetComputronium.level * 0.05);
            severity -= (targetComputronium.efficiency * 0.1);
        }
        if (target.commandAndControl) {
            severity -= (target.commandAndControl.networkSecurity * 0.3);
        }

        return Math.max(0.1, Math.min(1.0, severity));
    }

    calculateBreachDuration(breachType) {
        const baseDuration = 30000; // 30 seconds
        switch (breachType) {
            case this.breachTypes.CMD_INJECTION:
                return baseDuration * 0.8;
            case this.breachTypes.DATA_EXFILTRATION:
                return baseDuration * 1.2;
            case this.breachTypes.NETWORK_DISRUPTION:
                return baseDuration * 1.5;
            case this.breachTypes.CORE_COMPROMISE:
                return baseDuration * 2.0;
            default:
                return baseDuration;
        }
    }

    applyBreachEffects(target, breachEffect) {
        switch (breachEffect.type) {
            case this.breachTypes.CMD_INJECTION:
                this.applyCommandInjectionEffects(target, breachEffect);
                break;
            case this.breachTypes.DATA_EXFILTRATION:
                this.applyDataExfiltrationEffects(target, breachEffect);
                break;
            case this.breachTypes.NETWORK_DISRUPTION:
                this.applyNetworkDisruptionEffects(target, breachEffect);
                break;
            case this.breachTypes.CORE_COMPROMISE:
                this.applyCoreCompromiseEffects(target, breachEffect);
                break;
        }
    }

    applyCommandInjectionEffects(target, breachEffect) {
        if (target.commandAndControl) {
            // Inject false commands or modify existing ones
            target.commandAndControl.commandLatency *= (1 + breachEffect.severity);
            target.commandAndControl.commandAccuracy *= (1 - breachEffect.severity * 0.5);
            
            // Chance to issue incorrect orders
            if (Math.random() < breachEffect.severity * 0.3) {
                target.commandAndControl.issueIncorrectOrder();
            }
        }
    }

    applyDataExfiltrationEffects(target, breachEffect) {
        if (target.commandAndControl) {
            // Leak sensitive information
            target.commandAndControl.networkSecurity *= (1 - breachEffect.severity * 0.7);
            target.commandAndControl.intelLeakRate = breachEffect.severity * 0.5;
            
            // Reveal hidden units or structures
            if (target.revealHiddenEntities) {
                target.revealHiddenEntities(breachEffect.severity);
            }
        }
    }

    applyNetworkDisruptionEffects(target, breachEffect) {
        if (target.commandAndControl) {
            // Disrupt network communications
            target.commandAndControl.networkStability *= (1 - breachEffect.severity * 0.8);
            target.commandAndControl.packetLossRate = breachEffect.severity * 0.6;
            
            // Affect connected nodes
            if (target.connectedNodes) {
                target.connectedNodes.forEach(node => {
                    node.commandAndControl.networkStability *= (1 - breachEffect.severity * 0.3);
                });
            }
        }
    }

    applyCoreCompromiseEffects(target, breachEffect) {
        const computronium = target.getComponent(ComputroniumComponent);
        if (computronium) {
            // Compromise computronium core
            computronium.efficiency *= (1 - breachEffect.severity * 0.9);
            computronium.powContribution *= (1 - breachEffect.severity * 0.7);
            
            // Chance to temporarily change focus mode
            if (Math.random() < breachEffect.severity * 0.4) {
                const modes = ['Offensive', 'Defensive', 'C&C', 'Utility', 'Balanced'];
                computronium.setFocusMode(modes[Math.floor(Math.random() * modes.length)]);
            }
        }
    }

    checkCascadingEffects(target, breachEffect) {
        // Check for network propagation
        if (target.connectedNodes) {
            target.connectedNodes.forEach(node => {
                if (Math.random() < breachEffect.severity * 0.3) {
                    breachEffect.cascadingEffects.add({
                        target: node.id,
                        type: breachEffect.type,
                        severity: breachEffect.severity * 0.5
                    });
                }
            });
        }

        // Check for system-wide effects
        if (breachEffect.severity > 0.8) {
            if (target.gameContext) {
                target.gameContext.addEvent({
                    type: 'cascading_breach',
                    source: target.id,
                    severity: breachEffect.severity,
                    timestamp: Date.now()
                });
            }
        }
    }

    updateBreachAttempts(deltaTime) {
        // Update active breaches
        for (const [targetId, breachEffect] of this.activeBreaches) {
            const timeElapsed = Date.now() - breachEffect.startTime;
            
            // Check if breach has expired
            if (timeElapsed >= breachEffect.duration) {
                this.resolveBreach(targetId);
                continue;
            }

            // Apply ongoing effects
            const progress = timeElapsed / breachEffect.duration;
            this.applyOngoingBreachEffects(targetId, breachEffect, progress);

            // Process cascading effects
            this.processCascadingEffects(breachEffect);
        }

        // Update breach attempts (existing code)
        for (const [breachId, attempt] of this.breachAttempts) {
            attempt.progress *= 0.95;
            if (Date.now() - attempt.startTime > 30000) {
                this.breachAttempts.delete(breachId);
            }
        }
    }

    resolveBreach(targetId) {
        const breachEffect = this.activeBreaches.get(targetId);
        if (!breachEffect) return;

        // Apply recovery effects
        if (breachEffect.target) {
            const target = breachEffect.target;
            if (target.commandAndControl) {
                target.commandAndControl.networkSecurity *= 1.2;
                target.commandAndControl.networkStability *= 1.1;
            }
        }

        // Remove breach
        this.activeBreaches.delete(targetId);
    }

    applyOngoingBreachEffects(targetId, breachEffect, progress) {
        const target = breachEffect.target;
        if (!target) return;

        // Apply progressive effects based on breach type and progress
        switch (breachEffect.type) {
            case this.breachTypes.CMD_INJECTION:
                if (target.commandAndControl) {
                    target.commandAndControl.commandLatency *= (1 + breachEffect.severity * progress * 0.1);
                }
                break;
            case this.breachTypes.DATA_EXFILTRATION:
                if (target.commandAndControl) {
                    target.commandAndControl.intelLeakRate = breachEffect.severity * progress * 0.5;
                }
                break;
            case this.breachTypes.NETWORK_DISRUPTION:
                if (target.commandAndControl) {
                    target.commandAndControl.packetLossRate = breachEffect.severity * progress * 0.6;
                }
                break;
            case this.breachTypes.CORE_COMPROMISE:
                const computronium = target.getComponent(ComputroniumComponent);
                if (computronium) {
                    computronium.efficiency *= (1 - breachEffect.severity * progress * 0.1);
                }
                break;
        }
    }

    processCascadingEffects(breachEffect) {
        for (const cascadingEffect of breachEffect.cascadingEffects) {
            const target = cascadingEffect.target;
            if (!target) continue;

            // Apply cascading effect with reduced severity
            this.applyBreachEffects(target, {
                ...cascadingEffect,
                duration: breachEffect.duration * 0.7
            });
        }
    }

    getNetworkStatus() {
        return {
            hashRate: this.networkHashRate,
            activeBreaches: this.activeBreaches.size,
            defensiveNodes: this.defensiveNodes.size,
            offensiveNodes: this.offensiveNodes.size,
            breachTypes: Object.fromEntries(
                Array.from(this.activeBreaches.values())
                    .reduce((acc, breach) => {
                        acc[breach.type] = (acc[breach.type] || 0) + 1;
                        return acc;
                    }, {})
            )
        };
    }
} 