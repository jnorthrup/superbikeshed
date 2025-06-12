export class HealthComponent {
    constructor(hp, maxHp) {
        this.hp = hp;
        this.maxHp = maxHp;
        this.lastDamageTime = 0;
        this.damageHistory = [];
    }

    static create(hp, maxHp) {
        return new HealthComponent(hp, maxHp);
    }

    get healthRatio() {
        return this.hp / this.maxHp;
    }

    takeDamage(amount, source, gameContext) {
        const now = performance.now();
        this.hp = Math.max(0, this.hp - amount);
        this.lastDamageTime = now;

        // Record damage event
        this.damageHistory.push({
            time: now,
            amount,
            source: source?.id,
            remainingHp: this.hp
        });

        // Keep only last 10 damage events
        if (this.damageHistory.length > 10) {
            this.damageHistory.shift();
        }

        // Add visual feedback
        if (gameContext.captions) {
            gameContext.captions.push(new gameContext.Caption(
                this.x, this.y,
                `-${amount}`,
                '#f44',
                12
            ));
        }

        return this.hp <= 0;
    }

    heal(amount) {
        this.hp = Math.min(this.maxHp, this.hp + amount);
        return this.hp;
    }

    isDead() {
        return this.hp <= 0;
    }

    toJSON() {
        return {
            hp: this.hp,
            maxHp: this.maxHp,
            lastDamageTime: this.lastDamageTime,
            damageHistory: this.damageHistory
        };
    }

    static fromJSON(data) {
        const component = new HealthComponent(data.hp, data.maxHp);
        component.lastDamageTime = data.lastDamageTime;
        component.damageHistory = data.damageHistory;
        return component;
    }
} 