export class ComputroniumComponent {
    constructor(config) {
        this.id = config.id;
        this.displayName = config.displayName;
        this.level = config.level || 1;
        this.focusMode = config.focusMode || 'balanced';
        this.allocatedFunctions = {};
        this.isActive = config.isActive || true;
        this.contributesToPoW = config.contributesToPoW || false;
        this.powContribution = config.powContribution || 0;
        this.lastUpdateTime = performance.now();
        this.efficiency = config.efficiency || 1.0;
        this.connectedTo = new Set(config.connectedTo || []);
    }

    static create(config) {
        return new ComputroniumComponent(config);
    }

    update(deltaTime) {
        if (!this.isActive) return;

        // Update last update time
        this.lastUpdateTime = performance.now();
    }

    setFocusMode(mode) {
        this.focusMode = mode;
    }

    setActive(active) {
        this.isActive = active;
    }

    setPowContribution(contributes) {
        this.contributesToPoW = contributes;
    }

    setEfficiency(efficiency) {
        this.efficiency = Math.max(0, Math.min(1, efficiency));
    }

    connectTo(coreId) {
        this.connectedTo.add(coreId);
    }

    disconnectFrom(coreId) {
        this.connectedTo.delete(coreId);
    }

    getForkCount() {
        // Level 1 = 2 forks, Level 5 = 5 forks
        return Math.min(5, Math.max(2, this.level));
    }

    getFunctionStatus(functionName) {
        return this.allocatedFunctions[functionName] || 'STARVED';
    }

    toJSON() {
        return {
            id: this.id,
            displayName: this.displayName,
            level: this.level,
            focusMode: this.focusMode,
            allocatedFunctions: this.allocatedFunctions,
            isActive: this.isActive,
            contributesToPoW: this.contributesToPoW,
            powContribution: this.powContribution,
            lastUpdateTime: this.lastUpdateTime,
            efficiency: this.efficiency,
            connectedTo: Array.from(this.connectedTo)
        };
    }

    static fromJSON(data) {
        const component = new ComputroniumComponent({
            id: data.id,
            displayName: data.displayName,
            level: data.level,
            focusMode: data.focusMode,
            isActive: data.isActive,
            contributesToPoW: data.contributesToPoW,
            powContribution: data.powContribution,
            efficiency: data.efficiency,
            connectedTo: data.connectedTo
        });
        component.allocatedFunctions = data.allocatedFunctions;
        component.lastUpdateTime = data.lastUpdateTime;
        return component;
    }
} 