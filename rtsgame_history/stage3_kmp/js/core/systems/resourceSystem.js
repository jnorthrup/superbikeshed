export class ResourceSystem {
    constructor() {
        this.resourceTypes = {
            MASS: 'mass',
            ENERGY: 'energy',
            COMPUTRONIUM: 'computronium',
            ALLOY: 'alloy',
            BATTERY: 'battery',
            COMPUTATIONAL_CYCLES: 'computational_cycles',
            INFORMATION: 'information',
            POPULATION: 'population'
        };
    }

    update(deltaTime, entities, gameContext) {
        // Update resource generation and consumption
        for (const entity of entities) {
            if (!entity.resource) continue;

            // Skip inactive resource components
            if (!entity.resource.isActive) continue;

            // Update resource amounts
            this.updateResourceAmounts(entity, deltaTime);

            // Update network connections
            this.updateNetworkConnections(entity, gameContext);
        }

        // Update global resource pools
        this.updateGlobalResources(gameContext);
    }

    updateResourceAmounts(entity, deltaTime) {
        const resource = entity.resource;
        const netRate = resource.generationRate - resource.consumptionRate;
        const change = netRate * deltaTime * resource.efficiency;

        // Update current amount
        resource.currentAmount = Math.max(0, Math.min(resource.capacity, resource.currentAmount + change));

        // Update last update time
        resource.lastUpdateTime = performance.now();
    }

    updateNetworkConnections(entity, gameContext) {
        if (!entity.resource.connectedTo) return;

        // Process each connection
        for (const targetId of entity.resource.connectedTo) {
            const target = this.findEntityById(gameContext, targetId);
            if (!target || !target.resource) continue;

            // Transfer resources based on connection type
            this.transferResources(entity, target, gameContext);
        }
    }

    transferResources(source, target, gameContext) {
        if (!source.resource || !target.resource) return;

        // Skip if either component is inactive
        if (!source.resource.isActive || !target.resource.isActive) return;

        // Calculate transfer amount based on source generation and target capacity
        const transferAmount = Math.min(
            source.resource.generationRate * gameContext.deltaTime,
            target.resource.capacity - target.resource.currentAmount
        );

        // Perform transfer if possible
        if (transferAmount > 0 && source.resource.canConsume(transferAmount)) {
            source.resource.consume(transferAmount);
            target.resource.generate(transferAmount);
        }
    }

    updateGlobalResources(gameContext) {
        // Update faction resource pools
        if (gameContext.factions) {
            for (const faction of Object.values(gameContext.factions)) {
                if (!faction.resources) continue;

                // Update each resource type
                for (const [resourceType, amount] of Object.entries(faction.resources)) {
                    // Apply income and consumption
                    const income = faction.income?.[resourceType] || 0;
                    const consumption = faction.consumption?.[resourceType] || 0;
                    const netChange = (income - consumption) * gameContext.deltaTime;

                    // Update amount
                    faction.resources[resourceType] = Math.max(0, amount + netChange);
                }
            }
        }
    }

    findEntityById(gameContext, id) {
        // Search in units
        const unit = gameContext.units?.find(u => u.id === id);
        if (unit) return unit;

        // Search in buildings
        const building = gameContext.buildings?.find(b => b.id === id);
        if (building) return building;

        // Search in resource nodes
        const node = gameContext.resourceNodes?.find(n => n.id === id);
        if (node) return node;

        return null;
    }

    connectResources(source, target) {
        if (!source.resource || !target.resource) return false;

        source.resource.connectTo(target.id);
        return true;
    }

    disconnectResources(source, target) {
        if (!source.resource || !target.resource) return false;

        source.resource.disconnectFrom(target.id);
        return true;
    }

    setResourceEfficiency(entity, efficiency) {
        if (!entity.resource) return false;

        entity.resource.setEfficiency(efficiency);
        return true;
    }

    setResourceActive(entity, active) {
        if (!entity.resource) return false;

        entity.resource.setActive(active);
        return true;
    }

    getResourceStatus(entity) {
        if (!entity.resource) return null;

        return {
            type: entity.resource.resourceType,
            currentAmount: entity.resource.currentAmount,
            capacity: entity.resource.capacity,
            generationRate: entity.resource.generationRate,
            consumptionRate: entity.resource.consumptionRate,
            efficiency: entity.resource.efficiency,
            isActive: entity.resource.isActive,
            connectedTo: Array.from(entity.resource.connectedTo || []),
            fillPercentage: entity.resource.getFillPercentage()
        };
    }

    getGlobalResourceStatus(gameContext, factionId) {
        const faction = gameContext.factions?.[factionId];
        if (!faction) return null;

        return {
            resources: faction.resources || {},
            income: faction.income || {},
            consumption: faction.consumption || {}
        };
    }
} 