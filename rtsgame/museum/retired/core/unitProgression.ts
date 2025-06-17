import { VETERANCY_LEVELS, COMMAND_FITNESS_LEVELS } from '../config/gameConstants.js';

export class UnitProgression {
    constructor(unit) {
        this.unit = unit;
        
        // Experience tracking
        this.combatExperience = 0;
        this.survivalTime = 0;
        this.commandExperience = 0;
        this.killCount = 0;
        this.damageDealt = 0;
        this.lastPromotionTime = 0;
        
        // Authority modifiers
        this.healthAuthorityModifier = 0;
        this.veterancyAuthorityModifier = 0;
        this.contextAuthorityModifier = 0;
        
        // Command fitness
        this.commandFitness = COMMAND_FITNESS_LEVELS.FULL_COMMAND;
        this.lastAuthorityUpdate = 0;
        this.commandSuccesses = 0;
        this.commandFailures = 0;
        
        // Veterancy
        this.veterancyLevel = VETERANCY_LEVELS.GREEN;
        this.experienceThresholds = {
            [VETERANCY_LEVELS.GREEN]: 0,
            [VETERANCY_LEVELS.REGULAR]: 25,
            [VETERANCY_LEVELS.VETERAN]: 75,
            [VETERANCY_LEVELS.ELITE]: 150,
            [VETERANCY_LEVELS.HERO]: 300
        };
    }

    /**
     * Update unit progression
     * @param {Object} gameContext - Game context
     * @param {number} deltaTime - Time since last update
     */
    update(gameContext, deltaTime) {
        // Update survival time in combat
        if (this.isInCombat(gameContext)) {
            this.survivalTime += deltaTime;
        }

        // Update authority every 5 seconds
        const now = performance.now();
        if (now - this.lastAuthorityUpdate > 5000) {
            this.updateAuthority();
            this.lastAuthorityUpdate = now;
        }

        // Check for promotion
        this.checkPromotion();
    }

    /**
     * Check if unit is in combat
     * @param {Object} gameContext - Game context
     * @returns {boolean} Whether unit is in combat
     */
    isInCombat(gameContext) {
        return this.unit.target || 
               this.unit.hp < this.unit.maxHp || 
               this.isUnderFire(gameContext);
    }

    /**
     * Check if unit is under fire
     * @param {Object} gameContext - Game context
     * @returns {boolean} Whether unit is under fire
     */
    isUnderFire(gameContext) {
        // Add null checks for gameContext and units array
        if (!gameContext || !gameContext.units || !Array.isArray(gameContext.units)) {
            return false;
        }
        
        const nearbyEnemies = gameContext.units.filter(u =>
            u && u.team !== this.unit.team &&
            this.unit.getDistance && this.unit.getDistance(u) < 200
        );
        
        return nearbyEnemies.some(enemy =>
            enemy.target === this.unit ||
            (enemy.getDistance && enemy.attackRange && enemy.getDistance(this.unit) < enemy.attackRange)
        );
    }

    /**
     * Update unit authority
     */
    updateAuthority() {
        // Calculate health-based authority
        const healthRatio = this.unit.hp / this.unit.maxHp;
        this.updateHealthAuthority(healthRatio);
        
        // Calculate veterancy-based authority
        this.updateVeterancyAuthority();
        
        // Calculate context-based authority
        this.updateContextAuthority();
        
        // Update effective authority
        this.unit.effectiveAuthority = this.unit.baseAuthority + 
                                     this.healthAuthorityModifier + 
                                     this.veterancyAuthorityModifier + 
                                     this.contextAuthorityModifier;
    }

    /**
     * Update health-based authority
     * @param {number} healthRatio - Current health ratio
     */
    updateHealthAuthority(healthRatio) {
        if (healthRatio >= 0.8) {
            this.healthAuthorityModifier = 5;
            this.commandFitness = COMMAND_FITNESS_LEVELS.FULL_COMMAND;
        } else if (healthRatio >= 0.6) {
            this.healthAuthorityModifier = 2;
            this.commandFitness = COMMAND_FITNESS_LEVELS.REDUCED_AUTHORITY;
        } else if (healthRatio >= 0.4) {
            this.healthAuthorityModifier = -2;
            this.commandFitness = COMMAND_FITNESS_LEVELS.COMPROMISED_COMMAND;
        } else if (healthRatio >= 0.2) {
            this.healthAuthorityModifier = -5;
            this.commandFitness = COMMAND_FITNESS_LEVELS.CRITICAL_STATUS;
        } else {
            this.healthAuthorityModifier = -10;
            this.commandFitness = COMMAND_FITNESS_LEVELS.COMBAT_INEFFECTIVE;
        }
    }

    /**
     * Update veterancy-based authority
     */
    updateVeterancyAuthority() {
        const experiencePoints = this.calculateTotalExperience();
        
        if (experiencePoints >= this.experienceThresholds[VETERANCY_LEVELS.HERO]) {
            this.veterancyLevel = VETERANCY_LEVELS.HERO;
            this.veterancyAuthorityModifier = 15;
        } else if (experiencePoints >= this.experienceThresholds[VETERANCY_LEVELS.ELITE]) {
            this.veterancyLevel = VETERANCY_LEVELS.ELITE;
            this.veterancyAuthorityModifier = 10;
        } else if (experiencePoints >= this.experienceThresholds[VETERANCY_LEVELS.VETERAN]) {
            this.veterancyLevel = VETERANCY_LEVELS.VETERAN;
            this.veterancyAuthorityModifier = 5;
        } else if (experiencePoints >= this.experienceThresholds[VETERANCY_LEVELS.REGULAR]) {
            this.veterancyLevel = VETERANCY_LEVELS.REGULAR;
            this.veterancyAuthorityModifier = 2;
        } else {
            this.veterancyLevel = VETERANCY_LEVELS.GREEN;
            this.veterancyAuthorityModifier = 0;
        }
    }

    /**
     * Calculate total experience points
     * @returns {number} Total experience points
     */
    calculateTotalExperience() {
        return this.combatExperience + 
               (this.survivalTime / 60) + 
               (this.commandExperience * 3) + 
               (this.killCount * 2);
    }

    /**
     * Update context-based authority
     */
    updateContextAuthority() {
        // Default context modifier
        this.contextAuthorityModifier = 0;
        
        // Bonus for commanding units
        if (this.unit.subordinates && this.unit.subordinates.length > 0) {
            this.contextAuthorityModifier += Math.min(this.unit.subordinates.length, 5);
        }
        
        // Penalty for being isolated
        if (this.unit.subordinates && this.unit.subordinates.length === 0) {
            this.contextAuthorityModifier -= 2;
        }
        
        // Bonus for being near objectives
        if (this.isNearObjective()) {
            this.contextAuthorityModifier += 3;
        }
    }

    /**
     * Check if unit is near an objective
     * @returns {boolean} Whether unit is near an objective
     */
    isNearObjective() {
        // Implementation depends on game's objective system
        return false;
    }

    /**
     * Check for promotion eligibility
     */
    checkPromotion() {
        const oldLevel = this.veterancyLevel;
        this.updateVeterancyAuthority();
        
        if (oldLevel !== this.veterancyLevel) {
            this.processPromotion(oldLevel);
        }
    }

    /**
     * Process unit promotion
     * @param {string} oldLevel - Previous veterancy level
     */
    processPromotion(oldLevel) {
        this.lastPromotionTime = performance.now();
        
        // Apply promotion bonuses
        switch (this.veterancyLevel) {
            case VETERANCY_LEVELS.REGULAR:
                this.unit.maxHp *= 1.1;
                this.unit.hp = this.unit.maxHp;
                break;
            case VETERANCY_LEVELS.VETERAN:
                this.unit.maxHp *= 1.2;
                this.unit.hp = this.unit.maxHp;
                this.unit.attackDamage *= 1.15;
                break;
            case VETERANCY_LEVELS.ELITE:
                this.unit.maxHp *= 1.3;
                this.unit.hp = this.unit.maxHp;
                this.unit.attackDamage *= 1.25;
                this.unit.attackRange *= 1.1;
                break;
            case VETERANCY_LEVELS.HERO:
                this.unit.maxHp *= 1.5;
                this.unit.hp = this.unit.maxHp;
                this.unit.attackDamage *= 1.4;
                this.unit.attackRange *= 1.2;
                this.unit.movementSpeed *= 1.1;
                break;
        }
        
        // Notify game of promotion
        if (this.unit.onPromotion) {
            this.unit.onPromotion(oldLevel, this.veterancyLevel);
        }
    }

    /**
     * Add combat experience
     * @param {number} amount - Experience amount
     */
    addCombatExperience(amount) {
        this.combatExperience += amount;
    }

    /**
     * Add command experience
     * @param {number} amount - Experience amount
     */
    addCommandExperience(amount) {
        this.commandExperience += amount;
    }

    /**
     * Record successful command
     */
    recordCommandSuccess() {
        this.commandSuccesses++;
        this.addCommandExperience(1);
    }

    /**
     * Record failed command
     */
    recordCommandFailure() {
        this.commandFailures++;
    }

    /**
     * Record kill
     * @param {Object} target - Killed unit
     */
    recordKill(target) {
        this.killCount++;
        
        // Bonus experience for high-value targets
        if (target.type === 'commander') {
            this.addCombatExperience(10);
        } else if (target.veterancyLevel === VETERANCY_LEVELS.HERO) {
            this.addCombatExperience(5);
        } else if (target.veterancyLevel === VETERANCY_LEVELS.ELITE) {
            this.addCombatExperience(3);
        } else {
            this.addCombatExperience(1);
        }
    }

    /**
     * Record damage dealt
     * @param {number} amount - Damage amount
     */
    recordDamage(amount) {
        this.damageDealt += amount;
        this.addCombatExperience(amount / 100); // Experience based on damage
    }
} 