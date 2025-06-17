export class ResourceComponent {
    constructor(config) {
        this.id = config.id;
        this.displayName = config.displayName;
        this.resourceType = config.resourceType;
        this.capacity = config.capacity;
        this.currentAmount = config.currentAmount || 0;
        this.generationRate = config.generationRate || 0;
        this.consumptionRate = config.consumptionRate || 0;
        this.lastUpdateTime = performance.now();
        this.isActive = config.isActive || true;
        this.efficiency = config.efficiency || 1.0;
        this.connectedTo = new Set(config.connectedTo || []);
    }

    static create(config) {
        return new ResourceComponent(config);
    }

    update(deltaTime) {
        if (!this.isActive) return;

        const netRate = this.generationRate - this.consumptionRate;
        const change = netRate * deltaTime * this.efficiency;
        
        this.currentAmount = Math.max(0, Math.min(this.capacity, this.currentAmount + change));
    }

    canConsume(amount) {
        return this.currentAmount >= amount;
    }

    consume(amount) {
        if (!this.canConsume(amount)) return false;
        this.currentAmount -= amount;
        return true;
    }

    canGenerate(amount) {
        return this.currentAmount + amount <= this.capacity;
    }

    generate(amount) {
        if (!this.canGenerate(amount)) return false;
        this.currentAmount += amount;
        return true;
    }

    connectTo(resourceId) {
        this.connectedTo.add(resourceId);
    }

    disconnectFrom(resourceId) {
        this.connectedTo.delete(resourceId);
    }

    setEfficiency(efficiency) {
        this.efficiency = Math.max(0, Math.min(1, efficiency));
    }

    setActive(active) {
        this.isActive = active;
    }

    getFillPercentage() {
        return (this.currentAmount / this.capacity) * 100;
    }

    toJSON() {
        return {
            id: this.id,
            displayName: this.displayName,
            resourceType: this.resourceType,
            capacity: this.capacity,
            currentAmount: this.currentAmount,
            generationRate: this.generationRate,
            consumptionRate: this.consumptionRate,
            lastUpdateTime: this.lastUpdateTime,
            isActive: this.isActive,
            efficiency: this.efficiency,
            connectedTo: Array.from(this.connectedTo)
        };
    }

    static fromJSON(data) {
        const component = new ResourceComponent({
            id: data.id,
            displayName: data.displayName,
            resourceType: data.resourceType,
            capacity: data.capacity,
            generationRate: data.generationRate,
            consumptionRate: data.consumptionRate,
            isActive: data.isActive,
            efficiency: data.efficiency,
            connectedTo: data.connectedTo
        });
        component.currentAmount = data.currentAmount;
        component.lastUpdateTime = data.lastUpdateTime;
        return component;
    }
} 