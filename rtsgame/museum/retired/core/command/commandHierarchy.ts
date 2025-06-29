import { AuthoritySystem } from './authoritySystem.js';

export class CommandHierarchy {
    constructor() {
        this.authoritySystem = new AuthoritySystem();
        this.commandRadius = {
            SUPREME: 400,  // General/Colonel
            FIELD: 200,    // Major/Captain
            SQUAD: 120,    // Lieutenant/Sergeant
            INDIVIDUAL: 80 // Private
        };
        
        this.commandStructure = new Map();
        this.lastAuthorityUpdate = 0;
        this.authorityUpdateInterval = 5000; // 5 seconds
    }

    initializeCommandStructure(units) {
        this.commandStructure.clear();
        
        // Initialize with all units as potential commanders
        units.forEach(unit => {
            this.commandStructure.set(unit.id, {
                unit,
                subordinates: new Set(),
                commander: null,
                commandRadius: this.getCommandRadius(unit)
            });
        });

        this.updateCommandStructure();
    }

    getCommandRadius(unit) {
        const veterancyData = this.authoritySystem.calculateVeterancyAuthorityModifier(unit);
        const baseRadius = this.getBaseCommandRadius(unit);
        return baseRadius + (veterancyData?.bonuses?.commandRadius || 0);
    }

    getBaseCommandRadius(unit) {
        const authority = this.authoritySystem.calculateEffectiveAuthority(unit).effectiveAuthority;
        
        if (authority >= 30) return this.commandRadius.SUPREME;
        if (authority >= 20) return this.commandRadius.FIELD;
        if (authority >= 15) return this.commandRadius.SQUAD;
        return this.commandRadius.INDIVIDUAL;
    }

    updateCommandStructure() {
        const currentTime = Date.now();
        if (currentTime - this.lastAuthorityUpdate < this.authorityUpdateInterval) {
            return;
        }
        
        this.lastAuthorityUpdate = currentTime;
        
        // Clear existing command relationships
        this.commandStructure.forEach(node => {
            node.subordinates.clear();
            node.commander = null;
        });

        // Sort units by authority for top-down assignment
        const sortedUnits = Array.from(this.commandStructure.values())
            .map(node => node.unit)
            .sort((a, b) => {
                const authA = this.authoritySystem.calculateEffectiveAuthority(a).effectiveAuthority;
                const authB = this.authoritySystem.calculateEffectiveAuthority(b).effectiveAuthority;
                return authB - authA;
            });

        // Assign commanders to units
        sortedUnits.forEach(unit => {
            const node = this.commandStructure.get(unit.id);
            if (!node) return;

            // Find potential commander
            const commander = this.findCommander(unit, sortedUnits);
            if (commander && commander.id !== unit.id) {
                node.commander = commander;
                this.commandStructure.get(commander.id).subordinates.add(unit);
            }
        });
    }

    findCommander(unit, sortedUnits) {
        const node = this.commandStructure.get(unit.id);
        if (!node) return null;

        // Check each potential commander in order of authority
        for (const potentialCommander of sortedUnits) {
            if (potentialCommander.id === unit.id) continue;

            const commanderNode = this.commandStructure.get(potentialCommander.id);
            if (!commanderNode) continue;

            // Check if within command radius
            const distance = this.calculateDistance(unit, potentialCommander);
            if (distance > commanderNode.commandRadius) continue;

            // Check if commander has capacity
            if (commanderNode.subordinates.size >= this.getMaxSubordinates(potentialCommander)) continue;

            // Check authority
            const unitAuth = this.authoritySystem.calculateEffectiveAuthority(unit).effectiveAuthority;
            const commanderAuth = this.authoritySystem.calculateEffectiveAuthority(potentialCommander).effectiveAuthority;
            
            if (commanderAuth > unitAuth) {
                return potentialCommander;
            }
        }

        return null;
    }

    calculateDistance(unit1, unit2) {
        const dx = unit1.x - unit2.x;
        const dy = unit1.y - unit2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    getMaxSubordinates(unit) {
        const authority = this.authoritySystem.calculateEffectiveAuthority(unit).effectiveAuthority;
        if (authority >= 30) return 50;  // Supreme Commander
        if (authority >= 20) return 25;  // Field Commander
        if (authority >= 15) return 10;  // Squad Leader
        return 0;  // Individual
    }

    getSubordinates(unitId) {
        const node = this.commandStructure.get(unitId);
        return node ? Array.from(node.subordinates) : [];
    }

    getCommander(unitId) {
        const node = this.commandStructure.get(unitId);
        return node ? node.commander : null;
    }

    getCommandChain(unitId) {
        const chain = [];
        let currentId = unitId;
        
        while (currentId) {
            const node = this.commandStructure.get(currentId);
            if (!node) break;
            
            chain.push(node.unit);
            currentId = node.commander ? node.commander.id : null;
        }
        
        return chain;
    }
} 