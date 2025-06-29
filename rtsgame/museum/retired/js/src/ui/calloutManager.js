"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CalloutManager = void 0;
// rtsgame/js/ui/calloutManager.js
class CalloutManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.activeCallouts = [];
        this.calloutIdCounter = 0;
        this.maxDisplayCallouts = 5;
    }
    init() {
        console.log("CalloutManager initialized.");
    }
    _isNear(loc1, loc2, distanceThresholdSq = 2500) {
        if (!loc1 || !loc2)
            return false;
        const dx = loc1.x - loc2.x;
        const dy = loc1.y - loc2.y;
        return (dx * dx + dy * dy) < distanceThresholdSq;
    }
    addCallout(data) {
        const newCallout = {
            id: `callout_${this.calloutIdCounter++}`,
            type: data.type || 'GENERIC',
            title: data.title || 'Alert!',
            message: data.message || '',
            priority: data.priority || 3,
            timestamp: Date.now(),
            duration: data.duration !== undefined ? data.duration : 10000,
            location: data.location || null,
            targetEntityId: data.targetEntityId || null,
            acknowledged: false,
            icon: data.icon || null,
        };
        const similarExists = this.activeCallouts.find(c => c.type === newCallout.type &&
            (Date.now() - c.timestamp < 5000) && // Within last 5 seconds
            ((c.targetEntityId && newCallout.targetEntityId && c.targetEntityId === newCallout.targetEntityId) ||
                (!c.targetEntityId && !newCallout.targetEntityId && c.location && newCallout.location &&
                    this._isNear(c.location, newCallout.location))));
        if (similarExists) {
            // console.log(`CalloutManager: Debounced similar callout: ${newCallout.title}`);
            return;
        }
        this.activeCallouts.push(newCallout);
        this.activeCallouts.sort((a, b) => {
            if (a.priority !== b.priority) {
                return a.priority - b.priority;
            }
            return b.timestamp - a.timestamp;
        });
        if (this.activeCallouts.length > this.maxDisplayCallouts) {
            this.activeCallouts = this.activeCallouts.slice(0, this.maxDisplayCallouts);
        }
        // console.log(`CalloutManager: Callout added: ${newCallout.title}, Total active: ${this.activeCallouts.length}`);
    }
    removeCallout(calloutId) {
        this.activeCallouts = this.activeCallouts.filter(c => c.id !== calloutId);
    }
    acknowledgeCallout(calloutId) {
        const callout = this.activeCallouts.find(c => c.id === calloutId);
        if (callout) {
            callout.acknowledged = true;
            // console.log(`CalloutManager: Callout acknowledged: ${callout.title}`);
        }
    }
    update(deltaTime) {
        const now = Date.now();
        for (let i = this.activeCallouts.length - 1; i >= 0; i--) {
            const callout = this.activeCallouts[i];
            if (callout.duration !== null && (now - callout.timestamp) > callout.duration) {
                this.activeCallouts.splice(i, 1);
            }
        }
    }
}
exports.CalloutManager = CalloutManager;
