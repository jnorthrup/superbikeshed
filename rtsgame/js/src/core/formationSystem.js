"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FormationSystem = void 0;
const commandConfig_js_1 = require("../config/commandConfig.js");
class FormationSystem {
    constructor() {
        this.formations = new Map(); // Map of formation ID to formation data
        this.formationShapes = {
            LINE: this.calculateLineFormation,
            COLUMN: this.calculateColumnFormation,
            WEDGE: this.calculateWedgeFormation,
            CIRCLE: this.calculateCircleFormation
        };
    }
    createFormation(leader, shape = 'LINE', options = {}) {
        const formationId = `formation_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const formation = {
            id: formationId,
            leader,
            shape,
            followers: new Set(),
            options: Object.assign({ spacing: options.spacing || commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.INDIVIDUAL, width: options.width || 3, depth: options.depth || 2 }, options),
            lastUpdate: Date.now()
        };
        this.formations.set(formationId, formation);
        leader.formation = formation;
        return formation;
    }
    addFollower(formation, unit) {
        if (!formation || !unit)
            return false;
        formation.followers.add(unit);
        unit.formation = formation;
        return true;
    }
    removeFollower(formation, unit) {
        if (!formation || !unit)
            return false;
        formation.followers.delete(unit);
        unit.formation = null;
        return true;
    }
    disbandFormation(formation) {
        if (!formation)
            return;
        // Clear formation reference from all units
        formation.leader.formation = null;
        for (const follower of formation.followers) {
            follower.formation = null;
        }
        this.formations.delete(formation.id);
    }
    updateFormation(formation, gameContext) {
        if (!formation || !formation.leader)
            return;
        const now = Date.now();
        if (now - formation.lastUpdate < 16)
            return; // Cap at ~60fps
        formation.lastUpdate = now;
        // Calculate target positions for all followers
        const targetPositions = this.calculateFormationPositions(formation);
        // Update each follower's movement
        for (const follower of formation.followers) {
            if (!follower)
                continue;
            const targetPos = targetPositions.get(follower);
            if (!targetPos)
                continue;
            this.updateFollowerMovement(follower, targetPos, gameContext);
        }
    }
    calculateFormationPositions(formation) {
        const positions = new Map();
        const shapeCalculator = this.formationShapes[formation.shape];
        if (!shapeCalculator) {
            console.warn(`Unknown formation shape: ${formation.shape}`);
            return positions;
        }
        return shapeCalculator.call(this, formation);
    }
    calculateLineFormation(formation) {
        const positions = new Map();
        const { leader, options } = formation;
        const { spacing, width } = options;
        // Calculate base line perpendicular to leader's facing
        const baseAngle = leader.angle + Math.PI / 2;
        const followerCount = formation.followers.size;
        const totalWidth = (followerCount + 1) * spacing;
        const startX = leader.x - (totalWidth / 2) * Math.cos(baseAngle);
        const startY = leader.y - (totalWidth / 2) * Math.sin(baseAngle);
        let index = 0;
        for (const follower of formation.followers) {
            const offset = (index + 1) * spacing;
            positions.set(follower, {
                x: startX + offset * Math.cos(baseAngle),
                y: startY + offset * Math.sin(baseAngle),
                angle: leader.angle
            });
            index++;
        }
        return positions;
    }
    calculateColumnFormation(formation) {
        const positions = new Map();
        const { leader, options } = formation;
        const { spacing } = options;
        let index = 0;
        for (const follower of formation.followers) {
            const offset = (index + 1) * spacing;
            positions.set(follower, {
                x: leader.x - offset * Math.cos(leader.angle),
                y: leader.y - offset * Math.sin(leader.angle),
                angle: leader.angle
            });
            index++;
        }
        return positions;
    }
    calculateWedgeFormation(formation) {
        const positions = new Map();
        const { leader, options } = formation;
        const { spacing } = options;
        let index = 0;
        for (const follower of formation.followers) {
            const row = Math.floor(index / 2);
            const isLeft = index % 2 === 0;
            const angleOffset = isLeft ? Math.PI / 4 : -Math.PI / 4;
            const distance = (row + 1) * spacing;
            const angle = leader.angle + angleOffset;
            positions.set(follower, {
                x: leader.x + distance * Math.cos(angle),
                y: leader.y + distance * Math.sin(angle),
                angle: leader.angle
            });
            index++;
        }
        return positions;
    }
    calculateCircleFormation(formation) {
        const positions = new Map();
        const { leader, options } = formation;
        const { spacing } = options;
        const followerCount = formation.followers.size;
        const angleStep = (2 * Math.PI) / followerCount;
        let index = 0;
        for (const follower of formation.followers) {
            const angle = index * angleStep;
            positions.set(follower, {
                x: leader.x + spacing * Math.cos(angle),
                y: leader.y + spacing * Math.sin(angle),
                angle: angle + Math.PI / 2 // Face outward
            });
            index++;
        }
        return positions;
    }
    updateFollowerMovement(follower, targetPos, gameContext) {
        if (!follower || !targetPos)
            return;
        // Calculate desired velocity
        const dx = targetPos.x - follower.x;
        const dy = targetPos.y - follower.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        // Apply steering behaviors
        const steering = this.calculateSteering(follower, targetPos, gameContext);
        // Update velocity
        follower.vx += steering.x;
        follower.vy += steering.y;
        // Limit speed
        const speed = Math.sqrt(follower.vx * follower.vx + follower.vy * follower.vy);
        if (speed > follower.speed) {
            follower.vx = (follower.vx / speed) * follower.speed;
            follower.vy = (follower.vy / speed) * follower.speed;
        }
        // Update position
        follower.x += follower.vx;
        follower.y += follower.vy;
        // Update angle
        if (distance > 0.1) {
            follower.angle = Math.atan2(dy, dx);
        }
    }
    calculateSteering(unit, target, gameContext) {
        const steering = { x: 0, y: 0 };
        // Separation
        const separation = this.calculateSeparation(unit, gameContext);
        steering.x += separation.x * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;
        steering.y += separation.y * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;
        // Terrain avoidance
        const avoidance = this.calculateTerrainAvoidance(unit, gameContext);
        steering.x += avoidance.x * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;
        steering.y += avoidance.y * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;
        return steering;
    }
    calculateSeparation(unit, gameContext) {
        const separation = { x: 0, y: 0 };
        const neighborRadius = unit.type.size * commandConfig_js_1.COMMAND_CONFIG.NEIGHBOR_RADIUS_FACTOR;
        for (const other of gameContext.entityManager.units) {
            if (other === unit)
                continue;
            const dx = unit.x - other.x;
            const dy = unit.y - other.y;
            const distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < neighborRadius) {
                const strength = (neighborRadius - distance) / neighborRadius;
                separation.x += (dx / distance) * strength;
                separation.y += (dy / distance) * strength;
            }
        }
        return separation;
    }
    calculateTerrainAvoidance(unit, gameContext) {
        const avoidance = { x: 0, y: 0 };
        const feelerLength = unit.type.size * commandConfig_js_1.COMMAND_CONFIG.STEERING_FEELER_LENGTH_FACTOR;
        // Check multiple feelers in front of the unit
        const feelerCount = 3;
        const feelerSpread = Math.PI / 4;
        for (let i = 0; i < feelerCount; i++) {
            const angle = unit.angle - feelerSpread + (i * feelerSpread / (feelerCount - 1));
            const feelerX = unit.x + Math.cos(angle) * feelerLength;
            const feelerY = unit.y + Math.sin(angle) * feelerLength;
            if (!gameContext.isTraversable(feelerX, feelerY)) {
                // Calculate avoidance force away from obstacle
                const dx = unit.x - feelerX;
                const dy = unit.y - feelerY;
                const distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > 0) {
                    avoidance.x += dx / distance;
                    avoidance.y += dy / distance;
                }
            }
        }
        return avoidance;
    }
    changeFormationShape(formation, newShape) {
        if (!formation || !this.formationShapes[newShape])
            return false;
        formation.shape = newShape;
        return true;
    }
    getFormationById(id) {
        return this.formations.get(id);
    }
    getFormationByUnit(unit) {
        return unit.formation;
    }
}
exports.FormationSystem = FormationSystem;
