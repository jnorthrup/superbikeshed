export class MovementSystem {
    constructor() {
        this.stuckThreshold = 0.1; // Minimum distance to be considered moving
        this.stuckFrames = 0;
        this.maxStuckFrames = 60; // 1 second at 60fps
    }

    update(entity, gameContext) {
        if (!entity.position || !entity.movement) return;

        const { position, movement } = entity;
        const { target, speed, path } = movement;

        if (!target && (!path || path.length === 0)) return;

        // Check if stuck
        if (this.isStuck(entity)) {
            this.handleStuck(entity, gameContext);
            return;
        }

        // Move along path if available
        if (path && path.length > 0) {
            this.followPath(entity, gameContext);
        }
        // Move directly to target
        else if (target) {
            position.moveTowards(target, speed, gameContext.deltaTime);
        }

        // Update last position for stuck detection
        this.updateLastPosition(entity);
    }

    isStuck(entity) {
        if (!entity.lastPosition) return false;

        const distance = entity.position.getDistance(entity.lastPosition);
        return distance < this.stuckThreshold;
    }

    handleStuck(entity, gameContext) {
        this.stuckFrames++;

        if (this.stuckFrames >= this.maxStuckFrames) {
            // Try to escape by moving in a random direction
            const escapeAngle = Math.random() * Math.PI * 2;
            const escapeDistance = 50; // Escape distance in units

            entity.position.x += Math.cos(escapeAngle) * escapeDistance;
            entity.position.y += Math.sin(escapeAngle) * escapeDistance;
            entity.position.angle = escapeAngle;

            // Reset path and target
            if (entity.movement) {
                entity.movement.path = [];
                entity.movement.target = null;
            }

            this.stuckFrames = 0;

            // Add visual feedback
            if (gameContext.captions) {
                gameContext.captions.push(new gameContext.Caption(
                    entity.position.x,
                    entity.position.y,
                    'Stuck!',
                    '#f44',
                    12
                ));
            }
        }
    }

    followPath(entity, gameContext) {
        const { position, movement } = entity;
        const { path, speed } = movement;

        if (!path || path.length === 0) return;

        const currentWaypoint = path[0];
        const distance = position.getDistance(currentWaypoint);

        if (distance < 5) { // Reached waypoint
            path.shift(); // Remove reached waypoint
            if (path.length === 0) {
                movement.target = null; // Clear target when path is complete
            }
        } else {
            position.moveTowards(currentWaypoint, speed, gameContext.deltaTime);
        }
    }

    updateLastPosition(entity) {
        if (!entity.lastPosition) {
            entity.lastPosition = { x: entity.position.x, y: entity.position.y };
        } else {
            entity.lastPosition.x = entity.position.x;
            entity.lastPosition.y = entity.position.y;
        }
    }

    setPath(entity, path) {
        if (!entity.movement) return;
        entity.movement.path = path;
        entity.movement.target = path[path.length - 1];
    }

    setTarget(entity, target) {
        if (!entity.movement) return;
        entity.movement.target = target;
        entity.movement.path = [];
    }
} 