export class WeaponComponent {
    constructor(config) {
        this.id = config.id;
        this.displayName = config.displayName;
        this.damageType = config.damageType;
        this.baseDamage = config.baseDamage;
        this.range = config.range;
        this.fireRate = config.fireRate;
        this.energyCost = config.energyCost;
        this.capacitorSize = config.capacitorSize;
        this.capacitorCharge = this.capacitorSize;
        this.lastFireTime = 0;
        this.target = null;
        this.projectileSpeed = config.projectileSpeed || 200;
        this.accuracy = config.accuracy || 1.0;
        this.spread = config.spread || 0;
    }

    static create(config) {
        return new WeaponComponent(config);
    }

    canFire() {
        const now = performance.now();
        return this.capacitorCharge >= this.energyCost &&
               now - this.lastFireTime >= (1000 / this.fireRate);
    }

    fire(source, target, gameContext) {
        if (!this.canFire()) return null;

        const now = performance.now();
        this.lastFireTime = now;
        this.capacitorCharge -= this.energyCost;

        // Calculate accuracy
        const accuracyRoll = Math.random();
        if (accuracyRoll > this.accuracy) {
            // Miss
            const missAngle = (Math.random() * 2 - 1) * this.spread;
            const targetAngle = Math.atan2(
                target.position.y - source.position.y,
                target.position.x - source.position.x
            );
            const missAngleRad = targetAngle + missAngle;

            return {
                x: source.position.x,
                y: source.position.y,
                targetX: source.position.x + Math.cos(missAngleRad) * this.range,
                targetY: source.position.y + Math.sin(missAngleRad) * this.range,
                speed: this.projectileSpeed,
                damage: this.baseDamage,
                damageType: this.damageType,
                team: source.team,
                createdAt: now
            };
        }

        // Hit
        return {
            x: source.position.x,
            y: source.position.y,
            targetX: target.position.x,
            targetY: target.position.y,
            speed: this.projectileSpeed,
            damage: this.baseDamage,
            damageType: this.damageType,
            team: source.team,
            target,
            createdAt: now
        };
    }

    update(deltaTime) {
        // Recharge capacitor
        this.capacitorCharge = Math.min(
            this.capacitorSize,
            this.capacitorCharge + this.energyCost * deltaTime
        );
    }

    toJSON() {
        return {
            id: this.id,
            displayName: this.displayName,
            damageType: this.damageType,
            baseDamage: this.baseDamage,
            range: this.range,
            fireRate: this.fireRate,
            energyCost: this.energyCost,
            capacitorSize: this.capacitorSize,
            capacitorCharge: this.capacitorCharge,
            lastFireTime: this.lastFireTime,
            projectileSpeed: this.projectileSpeed,
            accuracy: this.accuracy,
            spread: this.spread
        };
    }

    static fromJSON(data) {
        const component = new WeaponComponent({
            id: data.id,
            displayName: data.displayName,
            damageType: data.damageType,
            baseDamage: data.baseDamage,
            range: data.range,
            fireRate: data.fireRate,
            energyCost: data.energyCost,
            capacitorSize: data.capacitorSize,
            projectileSpeed: data.projectileSpeed,
            accuracy: data.accuracy,
            spread: data.spread
        });
        component.capacitorCharge = data.capacitorCharge;
        component.lastFireTime = data.lastFireTime;
        return component;
    }
} 