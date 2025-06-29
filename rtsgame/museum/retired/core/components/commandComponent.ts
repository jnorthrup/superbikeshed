export class CommandComponent {
    constructor(config = {}) {
        this.id = config.id || 'command';
        this.displayName = config.displayName || 'Command';
        this.rank = config.rank || 'UNIT';
        this.effectiveRange = config.effectiveRange || 1000;
        this.latency = config.latency || 1.0;
        this.coordination = config.coordination || 1.0;
        this.currentOrder = null;
        this.orderDelay = 0;
        this.subordinates = new Set();
        this.commander = null;
        this.squadId = null;
        this.formation = 'LINE';
        this.lastOrderTime = 0;
        this.orderHistory = [];
    }

    static create(config) {
        return new CommandComponent(config);
    }

    setRank(rank) {
        this.rank = rank;
    }

    setCommander(commanderId) {
        if (this.commander) {
            // Remove from old commander's subordinates
            this.commander.subordinates.delete(this.id);
        }
        this.commander = commanderId;
    }

    addSubordinate(subordinateId) {
        this.subordinates.add(subordinateId);
    }

    removeSubordinate(subordinateId) {
        this.subordinates.delete(subordinateId);
    }

    setSquad(squadId) {
        this.squadId = squadId;
    }

    setFormation(formation) {
        this.formation = formation;
    }

    issueOrder(order) {
        this.currentOrder = order;
        this.orderDelay = this.calculateOrderDelay(order);
        this.lastOrderTime = Date.now();
        this.orderHistory.push({
            order,
            timestamp: this.lastOrderTime,
            delay: this.orderDelay
        });
    }

    calculateOrderDelay(order) {
        // Base delay based on order complexity
        let delay = 0.1; // Base 100ms
        
        // Add complexity factors
        switch (order.type) {
            case 'MOVE':
                delay += 0.1;
                break;
            case 'ATTACK':
                delay += 0.2;
                break;
            case 'FORMATION':
                delay += 0.3;
                break;
            // Add more order types as needed
        }
        
        // Apply rank and coordination modifiers
        delay *= this.latency;
        delay /= this.coordination;
        
        return delay;
    }

    getOrderStatus() {
        return {
            currentOrder: this.currentOrder,
            orderDelay: this.orderDelay,
            lastOrderTime: this.lastOrderTime,
            orderHistory: this.orderHistory.slice(-5) // Last 5 orders
        };
    }

    toJSON() {
        return {
            id: this.id,
            displayName: this.displayName,
            rank: this.rank,
            effectiveRange: this.effectiveRange,
            latency: this.latency,
            coordination: this.coordination,
            currentOrder: this.currentOrder,
            orderDelay: this.orderDelay,
            subordinates: Array.from(this.subordinates),
            commander: this.commander,
            squadId: this.squadId,
            formation: this.formation,
            lastOrderTime: this.lastOrderTime,
            orderHistory: this.orderHistory
        };
    }

    fromJSON(data) {
        this.id = data.id;
        this.displayName = data.displayName;
        this.rank = data.rank;
        this.effectiveRange = data.effectiveRange;
        this.latency = data.latency;
        this.coordination = data.coordination;
        this.currentOrder = data.currentOrder;
        this.orderDelay = data.orderDelay;
        this.subordinates = new Set(data.subordinates);
        this.commander = data.commander;
        this.squadId = data.squadId;
        this.formation = data.formation;
        this.lastOrderTime = data.lastOrderTime;
        this.orderHistory = data.orderHistory;
    }
} 