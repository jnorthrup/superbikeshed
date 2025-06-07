import { RESOURCE_TYPES } from '../config/gameConstants.js';

export class AutonomousBehavior {
    constructor(unit, gameContext) {
        this.unit = unit;
        this.gameContext = gameContext;
        this.currentBehavior = null;
        this.behaviorState = {};
        this.lastBehaviorUpdate = 0;
        this.updateInterval = 1000; // Update behavior every second
    }
    
    /**
     * Update autonomous behavior
     * @param {number} deltaTime - Time since last update
     */
    update(deltaTime) {
        const now = performance.now();
        if (now - this.lastBehaviorUpdate > this.updateInterval) {
            this.updateBehavior();
            this.lastBehaviorUpdate = now;
        }
        
        this.executeCurrentBehavior(deltaTime);
    }
    
    /**
     * Update unit behavior based on context
     */
    updateBehavior() {
        // Skip if unit has explicit orders
        if (this.unit.hasExplicitOrders()) {
            return;
        }
        
        // Determine appropriate behavior based on unit type and context
        switch (this.unit.type) {
            case 'engineer':
                this.updateEngineerBehavior();
                break;
            case 'scout':
                this.updateScoutBehavior();
                break;
            case 'combat':
                this.updateCombatBehavior();
                break;
            default:
                this.updateDefaultBehavior();
        }
    }
    
    /**
     * Update engineer behavior
     */
    updateEngineerBehavior() {
        const { resourceManager } = this.gameContext;
        
        // Check for damaged buildings to repair
        const damagedBuilding = this.findDamagedBuilding();
        if (damagedBuilding) {
            this.setBehavior('repair', { target: damagedBuilding });
            return;
        }
        
        // Look for unoccupied resource nodes
        const resourceNode = resourceManager.findNearestResourceNode(
            this.unit.position,
            RESOURCE_TYPES.MASS
        );
        
        if (resourceNode) {
            this.setBehavior('build_extractor', { target: resourceNode });
            return;
        }
        
        // Default to protecting commander
        const commander = this.findCommander();
        if (commander) {
            this.setBehavior('protect', { target: commander });
        }
    }
    
    /**
     * Update scout behavior
     */
    updateScoutBehavior() {
        // 5% chance to change patrol target
        if (Math.random() < 0.05) {
            const patrolTarget = this.findPatrolTarget();
            if (patrolTarget) {
                this.setBehavior('patrol', { target: patrolTarget });
                return;
            }
        }
        
        // Check for enemies
        const enemy = this.findNearestEnemy();
        if (enemy) {
            this.setBehavior('evade', { target: enemy });
            return;
        }
        
        // Default to exploring
        if (!this.currentBehavior || this.currentBehavior.type !== 'explore') {
            this.setBehavior('explore', {});
        }
    }
    
    /**
     * Update combat unit behavior
     */
    updateCombatBehavior() {
        // Check for enemies
        const enemy = this.findNearestEnemy();
        if (enemy) {
            this.setBehavior('attack', { target: enemy });
            return;
        }
        
        // Check for defensive positions
        const defensivePosition = this.findDefensivePosition();
        if (defensivePosition) {
            this.setBehavior('defend', { position: defensivePosition });
            return;
        }
        
        // Default to patrolling
        if (!this.currentBehavior || this.currentBehavior.type !== 'patrol') {
            const patrolTarget = this.findPatrolTarget();
            if (patrolTarget) {
                this.setBehavior('patrol', { target: patrolTarget });
            }
        }
    }
    
    /**
     * Update default behavior
     */
    updateDefaultBehavior() {
        // Default to following commander
        const commander = this.findCommander();
        if (commander) {
            this.setBehavior('follow', { target: commander });
        }
    }
    
    /**
     * Execute current behavior
     * @param {number} deltaTime - Time since last update
     */
    executeCurrentBehavior(deltaTime) {
        if (!this.currentBehavior) return;
        
        switch (this.currentBehavior.type) {
            case 'repair':
                this.executeRepairBehavior(deltaTime);
                break;
            case 'build_extractor':
                this.executeBuildExtractorBehavior(deltaTime);
                break;
            case 'protect':
                this.executeProtectBehavior(deltaTime);
                break;
            case 'patrol':
                this.executePatrolBehavior(deltaTime);
                break;
            case 'evade':
                this.executeEvadeBehavior(deltaTime);
                break;
            case 'explore':
                this.executeExploreBehavior(deltaTime);
                break;
            case 'attack':
                this.executeAttackBehavior(deltaTime);
                break;
            case 'defend':
                this.executeDefendBehavior(deltaTime);
                break;
            case 'follow':
                this.executeFollowBehavior(deltaTime);
                break;
        }
    }
    
    /**
     * Set current behavior
     * @param {string} type - Behavior type
     * @param {Object} state - Behavior state
     */
    setBehavior(type, state) {
        this.currentBehavior = { type, ...state };
        this.behaviorState = state;
    }
    
    /**
     * Find damaged building
     * @returns {Object|null} Damaged building or null
     */
    findDamagedBuilding() {
        const { buildings } = this.gameContext;
        return buildings.find(building => 
            building.team === this.unit.team && 
            building.hp < building.maxHp &&
            this.unit.getDistance(building) < 200
        );
    }
    
    /**
     * Find commander
     * @returns {Object|null} Commander unit or null
     */
    findCommander() {
        const { units } = this.gameContext;
        return units.find(unit => 
            unit.team === this.unit.team && 
            unit.type === 'commander'
        );
    }
    
    /**
     * Find patrol target
     * @returns {Object|null} Patrol target or null
     */
    findPatrolTarget() {
        const { resourceManager } = this.gameContext;
        return resourceManager.findNearestResourceNode(this.unit.position);
    }
    
    /**
     * Find nearest enemy
     * @returns {Object|null} Nearest enemy or null
     */
    findNearestEnemy() {
        const { units } = this.gameContext;
        let nearestEnemy = null;
        let minDistance = Infinity;
        
        units.forEach(unit => {
            if (unit.team !== this.unit.team) {
                const distance = this.unit.getDistance(unit);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestEnemy = unit;
                }
            }
        });
        
        return nearestEnemy;
    }
    
    /**
     * Find defensive position
     * @returns {Object|null} Defensive position or null
     */
    findDefensivePosition() {
        // Implementation depends on game's terrain system
        return null;
    }
    
    /**
     * Execute repair behavior
     * @param {number} deltaTime - Time since last update
     */
    executeRepairBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target || target.hp >= target.maxHp) {
            this.currentBehavior = null;
            return;
        }
        
        if (this.unit.getDistance(target) > this.unit.buildRange) {
            this.unit.moveTo(target.x, target.y);
        } else {
            this.unit.repair(target);
        }
    }
    
    /**
     * Execute build extractor behavior
     * @param {number} deltaTime - Time since last update
     */
    executeBuildExtractorBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target || target.isOccupied) {
            this.currentBehavior = null;
            return;
        }
        
        if (this.unit.getDistance(target.position) > this.unit.buildRange) {
            this.unit.moveTo(target.position.x, target.position.y);
        } else {
            this.unit.buildExtractor(target.position);
        }
    }
    
    /**
     * Execute protect behavior
     * @param {number} deltaTime - Time since last update
     */
    executeProtectBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target) {
            this.currentBehavior = null;
            return;
        }
        
        // Stay within protection range
        const protectionRange = 100;
        if (this.unit.getDistance(target) > protectionRange) {
            this.unit.moveTo(target.x, target.y);
        }
    }
    
    /**
     * Execute patrol behavior
     * @param {number} deltaTime - Time since last update
     */
    executePatrolBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target) {
            this.currentBehavior = null;
            return;
        }
        
        if (this.unit.getDistance(target.position) > 10) {
            this.unit.moveTo(target.position.x, target.position.y);
        }
    }
    
    /**
     * Execute evade behavior
     * @param {number} deltaTime - Time since last update
     */
    executeEvadeBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target) {
            this.currentBehavior = null;
            return;
        }
        
        // Move away from enemy
        const angle = Math.atan2(
            this.unit.y - target.y,
            this.unit.x - target.x
        );
        
        const distance = 200;
        const targetX = this.unit.x + Math.cos(angle) * distance;
        const targetY = this.unit.y + Math.sin(angle) * distance;
        
        this.unit.moveTo(targetX, targetY);
    }
    
    /**
     * Execute explore behavior
     * @param {number} deltaTime - Time since last update
     */
    executeExploreBehavior(deltaTime) {
        // Move to random position within exploration range
        if (!this.behaviorState.targetPosition || 
            this.unit.getDistance(this.behaviorState.targetPosition) < 10) {
            const angle = Math.random() * Math.PI * 2;
            const distance = 100 + Math.random() * 100;
            
            this.behaviorState.targetPosition = {
                x: this.unit.x + Math.cos(angle) * distance,
                y: this.unit.y + Math.sin(angle) * distance
            };
        }
        
        this.unit.moveTo(
            this.behaviorState.targetPosition.x,
            this.behaviorState.targetPosition.y
        );
    }
    
    /**
     * Execute attack behavior
     * @param {number} deltaTime - Time since last update
     */
    executeAttackBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target || target.hp <= 0) {
            this.currentBehavior = null;
            return;
        }
        
        if (this.unit.getDistance(target) > this.unit.attackRange) {
            this.unit.moveTo(target.x, target.y);
        } else {
            this.unit.attack(target);
        }
    }
    
    /**
     * Execute defend behavior
     * @param {number} deltaTime - Time since last update
     */
    executeDefendBehavior(deltaTime) {
        const position = this.behaviorState.position;
        if (!position) {
            this.currentBehavior = null;
            return;
        }
        
        if (this.unit.getDistance(position) > 10) {
            this.unit.moveTo(position.x, position.y);
        }
    }
    
    /**
     * Execute follow behavior
     * @param {number} deltaTime - Time since last update
     */
    executeFollowBehavior(deltaTime) {
        const target = this.behaviorState.target;
        if (!target) {
            this.currentBehavior = null;
            return;
        }
        
        const followDistance = 50;
        if (this.unit.getDistance(target) > followDistance) {
            this.unit.moveTo(target.x, target.y);
        }
    }
} 