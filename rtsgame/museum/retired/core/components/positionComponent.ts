export class PositionComponent {
    constructor(x, y, angle = 0) {
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    static create(x, y, angle = 0) {
        return new PositionComponent(x, y, angle);
    }

    getDistance(target) {
        const dx = this.x - target.x;
        const dy = this.y - target.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    getDirection(target) {
        const dx = target.x - this.x;
        const dy = target.y - this.y;
        return Math.atan2(dy, dx);
    }

    moveTowards(target, speed, deltaTime) {
        const direction = this.getDirection(target);
        this.x += Math.cos(direction) * speed * deltaTime;
        this.y += Math.sin(direction) * speed * deltaTime;
        this.angle = direction;
    }

    toJSON() {
        return {
            x: this.x,
            y: this.y,
            angle: this.angle
        };
    }

    static fromJSON(data) {
        return new PositionComponent(data.x, data.y, data.angle);
    }
} 