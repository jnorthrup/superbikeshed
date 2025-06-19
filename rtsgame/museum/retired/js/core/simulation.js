"use strict";
// js/core/simulation.js - Modern Simulation Engine
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.Simulation = exports.GameState = exports.EntityManager = void 0;
exports.formatTime = formatTime;
const unitTypes_js_1 = require("../config/unitTypes.js");
const buildingTypes_js_1 = require("../config/buildingTypes.js");
const gameConstants_js_1 = require("../config/gameConstants.js");
const simulationConfig_js_1 = require("../config/simulationConfig.js");
const unit_js_1 = require("./unit.js");
// const trikeshedEntityManager_js_1 = require("./trikeshedEntityManager.js"); // No longer needed
const computroniumManager_js_1 = require("./computroniumManager.js");
const enhancedCommandHierarchy_js_1 = require("./enhancedCommandHierarchy.js");
// Import specific functions from trikeshed-ts
const trikeshed_ts_1 = require("../../../Trikeshed/trikeshed-ts/src/index.js"); // Adjusted path
// Import resource manager functions and types
const resourceManager_js_1 = require("./resourceManager.js");
// EntityManager class to manage all game entities
class EntityManager {
    constructor(maxEntities = 1000) {
        this.nextEntityId = 0; // Simple ID generation for now
        this.entityIdToIndex = new Map(); // Map entity ID to Tensor row index

        // Core Component Cursors
        this.isActiveCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => false);
        this.entityIdsCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => null); // Stores entity ID string/number
        this.ownerTeamCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => null); // e.g., 'blue', 'red'
        this.unitTypeCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => null); // e.g., 'commander', 'tank'

        // Positional and Health Components
        this.positionsCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 2, () => 0.0); // x, y
        this.healthStatsCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 2, () => 0.0); // hp, maxHp

        // Combat and State Components
        this.currentActionCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => 'idle');
        this.attackDamageCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => 0);
        this.armorCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => 0);
        this.shieldCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 2, () => 0); // currentShield, maxShield
        this.energyCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 2, () => 0); // currentEnergy, maxEnergy
        this.computroniumCoresCursor = (0, trikeshed_ts_1.createCursor)(maxEntities, 1, () => 0);

        // Buildings, Projectiles, effects, captions are managed separately for now.
        // this.units = []; // Removed, entities are managed via Cursors
        this.buildings = []; // TODO: Migrate buildings to ECS
        this.projectiles = [];
        this.effects = [];
        this.captions = [];
    }
    _getNewEntityIndex(entityId) {
        if (this.entityIdToIndex.has(entityId)) {
            return this.entityIdToIndex.get(entityId);
        }
        // This simple index assignment assumes entities are never removed or IDs are not reused in Tensors.
        // A more robust system would manage free indices.
        const index = this.entityIdToIndex.size;
        if (index >= this.positionsCursor.rows) { // Check against .rows of one of the tensors
            console.error("EntityManager: Exceeded maximum entity capacity for Tensors.");
            // TODO: Implement dynamic resizing or better error handling
            return null;
        }
        this.entityIdToIndex.set(entityId, index);
        return index;
    }

    // Refactored addUnit to accept component values
    // Expects unit.id, unit.type.id, unit.team, unit.x, unit.y, unit.hp, unit.maxHp,
    // unit.type.stats.attackDamage, unit.type.stats.armor, 0, unit.type.stats.maxShield (if applicable)
    // unit.type.stats.energy (if applicable), unit.type.stats.maxEnergy (if applicable)
    // unit.type.stats.computroniumCores (if applicable)
    addUnit(id, type, team, x, y, hp, maxHp, attackDamage = 0, armor = 0, currentShield = 0, maxShield = 0, currentEnergy = 0, maxEnergy = 0, computroniumCores = 0, currentAction = 'idle') {
        const entityIndex = this._getNewEntityIndex(id);
        if (entityIndex === null) {
            console.error(`EntityManager: Could not add unit ${id}, max capacity reached or invalid ID.`);
            return;
        }

        // Set entity as active
        this.isActiveCursor = this.isActiveCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? true : this.isActiveCursor.get(coords));

        // Store entity ID
        this.entityIdsCursor = this.entityIdsCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? id : this.entityIdsCursor.get(coords));

        // Store owner/team
        this.ownerTeamCursor = this.ownerTeamCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? team : this.ownerTeamCursor.get(coords));

        // Store unit type (e.g., string ID 'commander', 'tank')
        this.unitTypeCursor = this.unitTypeCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? type : this.unitTypeCursor.get(coords));

        // Store position
        this.positionsCursor = this.positionsCursor.alpha((_value, coords) => {
            if (coords[0] === entityIndex) {
                if (coords[1] === 0) return x;
                if (coords[1] === 1) return y;
            }
            return this.positionsCursor.get(coords);
        });

        // Store health stats
        this.healthStatsCursor = this.healthStatsCursor.alpha((_value, coords) => {
            if (coords[0] === entityIndex) {
                if (coords[1] === 0) return hp;
                if (coords[1] === 1) return maxHp;
            }
            return this.healthStatsCursor.get(coords);
        });

        // Store combat stats
        this.attackDamageCursor = this.attackDamageCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? attackDamage : this.attackDamageCursor.get(coords));
        this.armorCursor = this.armorCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? armor : this.armorCursor.get(coords));

        // Store shield stats
        this.shieldCursor = this.shieldCursor.alpha((_value, coords) => {
            if (coords[0] === entityIndex) {
                if (coords[1] === 0) return currentShield;
                if (coords[1] === 1) return maxShield;
            }
            return this.shieldCursor.get(coords);
        });

        // Store energy stats
        this.energyCursor = this.energyCursor.alpha((_value, coords) => {
            if (coords[0] === entityIndex) {
                if (coords[1] === 0) return currentEnergy;
                if (coords[1] === 1) return maxEnergy;
            }
            return this.energyCursor.get(coords);
        });

        // Store computronium cores
        this.computroniumCoresCursor = this.computroniumCoresCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? computroniumCores : this.computroniumCoresCursor.get(coords));

        // Store current action
        this.currentActionCursor = this.currentActionCursor.alpha((_value, coords) => (coords[0] === entityIndex && coords[1] === 0) ? currentAction : this.currentActionCursor.get(coords));

        console.log(`EntityManager: Added unit ${id} (type: ${type}) at index ${entityIndex}.`);
    }

    getUnitPosition(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return { x: this.positionsCursor.get([index, 0]), y: this.positionsCursor.get([index, 1]) };
    }

    setUnitPosition(unitId, x, y) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.positionsCursor = this.positionsCursor.alpha((_value, coords) => {
            if (coords[0] === index && coords[1] === 0) return x;
            if (coords[0] === index && coords[1] === 1) return y;
            return this.positionsCursor.get(coords);
        });
    }

    getUnitHealth(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return { hp: this.healthStatsCursor.get([index, 0]), maxHp: this.healthStatsCursor.get([index, 1]) };
    }

    setUnitHealth(unitId, hp, maxHp) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.healthStatsCursor = this.healthStatsCursor.alpha((_value, coords) => {
            if (coords[0] === index && coords[1] === 0) return hp;
            if (coords[0] === index && coords[1] === 1) return maxHp;
            return this.healthStatsCursor.get(coords);
        });
    }

    // Getters for new components
    getUnitIsActive(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return false;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows) return false; // Check rows before get
        return this.isActiveCursor.get([index, 0]);
    }

    getUnitEntityIdString(unitId) { // Renamed to avoid conflict if unitId is number
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.entityIdsCursor.get([index, 0]);
    }

    getOwnerTeam(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.ownerTeamCursor.get([index, 0]);
    }

    getUnitType(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.unitTypeCursor.get([index, 0]);
    }

    getCurrentAction(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.currentActionCursor.get([index, 0]);
    }

    getAttackDamage(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null; // Or return 0 if preferred for non-existent/inactive
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.attackDamageCursor.get([index, 0]);
    }

    getArmor(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.armorCursor.get([index, 0]);
    }

    getShield(unitId) { // Returns { current: val, max: val }
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return { current: this.shieldCursor.get([index, 0]), max: this.shieldCursor.get([index, 1]) };
    }

    getEnergy(unitId) { // Returns { current: val, max: val }
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return { current: this.energyCursor.get([index, 0]), max: this.energyCursor.get([index, 1]) };
    }

    getComputroniumCores(unitId) {
        if (!this.entityIdToIndex.has(unitId)) return null;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return null;
        return this.computroniumCoresCursor.get([index, 0]);
    }

    // Setters for new components
    setCurrentAction(unitId, action) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.currentActionCursor = this.currentActionCursor.alpha((_v, coords) => (coords[0] === index && coords[1] === 0) ? action : this.currentActionCursor.get(coords));
    }

    setAttackDamage(unitId, damage) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.attackDamageCursor = this.attackDamageCursor.alpha((_v, coords) => (coords[0] === index && coords[1] === 0) ? damage : this.attackDamageCursor.get(coords));
    }

    setArmor(unitId, armorValue) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.armorCursor = this.armorCursor.alpha((_v, coords) => (coords[0] === index && coords[1] === 0) ? armorValue : this.armorCursor.get(coords));
    }

    setShield(unitId, current, max) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.shieldCursor = this.shieldCursor.alpha((_v, coords) => {
            if (coords[0] === index) {
                if (coords[1] === 0) return current;
                if (coords[1] === 1) return max;
            }
            return this.shieldCursor.get(coords);
        });
    }

    setEnergy(unitId, current, max) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.energyCursor = this.energyCursor.alpha((_v, coords) => {
            if (coords[0] === index) {
                if (coords[1] === 0) return current;
                if (coords[1] === 1) return max;
            }
            return this.energyCursor.get(coords);
        });
    }

    setComputroniumCores(unitId, cores) {
        if (!this.entityIdToIndex.has(unitId)) return;
        const index = this.entityIdToIndex.get(unitId);
        if (index === undefined || index >= this.isActiveCursor.rows || !this.isActiveCursor.get([index, 0])) return;
        this.computroniumCoresCursor = this.computroniumCoresCursor.alpha((_v, coords) => (coords[0] === index && coords[1] === 0) ? cores : this.computroniumCoresCursor.get(coords));
    }

    addBuilding(building) {
        this.buildings.push(building); // Still using old system for buildings
    }
    addProjectile(projectile) {
        this.projectiles.push(projectile);
    }
    addEffect(effect) {
        this.effects.push(effect);
    }
    addCaption(caption) {
        this.captions.push(caption);
    }
    update(simulation, deltaTime) {
        // Unit update logic will eventually iterate over active entities using cursors.
        // For now, unit logic (like movement, attacking) is not processed here yet.
        // This subtask focuses on setting up EntityManager data structure.
        // The Unit.js refactor (next major step) will handle how units read/write their state.

        // Process unit death
        // Iterate up to the current known size of entityIdToIndex, as entities might not be contiguous
        // or iterate all possible rows of isActiveCursor if entities are never fully deleted from cursors.
        // For now, using entityIdToIndex keys for existing entities that might die.
        const entityIds = Array.from(this.entityIdToIndex.keys());
        for (const unitId of entityIds) {
            const entityIndex = this.entityIdToIndex.get(unitId);
            if (entityIndex === undefined || entityIndex >= this.isActiveCursor.rows || !this.isActiveCursor.get([entityIndex, 0])) {
                continue; // Skip if not active or index out of bounds
            }

            // TODO: Unit logic (unit.update) should be called here eventually.
            // For now, we only handle death based on health.
            // Unit logic would update its health in the healthStatsCursor.
            // e.g., simulation.systems.aiSystem.updateUnit(unitId, deltaTime);
            //       simulation.systems.movementSystem.updateUnit(unitId, deltaTime);
            //       simulation.systems.combatSystem.updateUnit(unitId, deltaTime);


            const currentHealth = this.healthStatsCursor.get([entityIndex, 0]);
            if (currentHealth <= 0) {
                // Mark as inactive
                this.isActiveCursor = this.isActiveCursor.alpha((_v, coords) => (coords[0] === entityIndex && coords[1] === 0) ? false : this.isActiveCursor.get(coords));

                const unitType = this.unitTypeCursor.get([entityIndex, 0]);
                const team = this.ownerTeamCursor.get([entityIndex, 0]);
                console.log(`Unit ${unitId} (type: ${unitType}, team: ${team}) marked as inactive (dead).`);

                // Check for commander death (game over condition)
                // Ensure unitTypes_js_1.UNIT_TYPES.commander.id is the correct way to get the string ID
                if (unitType === unitTypes_js_1.UNIT_TYPES.commander.id) {
                    const winner = team === 'blue' ? 'RED' : 'BLUE';
                    simulation.gameState.winner = winner;
                    simulation.gameState.addEvent('game_over', `${winner} team wins! Enemy commander destroyed!`, 3);
                }

                // Remove from ID map to free up the ID (original behavior)
                // If entity IDs/indices should be permanent for inactive entities, this line would be removed.
                this.entityIdToIndex.delete(unitId);
            }
        }

        // Update buildings (still using old system)
        for (let i = this.buildings.length - 1; i >= 0; i--) {
            const building = this.buildings[i];
            building.update(simulation, deltaTime);
            if (building.hp <= 0) {
                this.buildings.splice(i, 1);
            }
        }
        // Update projectiles
        for (let i = this.projectiles.length - 1; i >= 0; i--) {
            const projectile = this.projectiles[i];
            projectile.update(simulation, deltaTime);
            if (projectile.shouldDestroy) {
                this.projectiles.splice(i, 1);
            }
        }
        // Update effects
        for (let i = this.effects.length - 1; i >= 0; i--) {
            const effect = this.effects[i];
            effect.update();
            if (effect.life <= 0) {
                this.effects.splice(i, 1);
            }
        }
        // Update captions
        for (let i = this.captions.length - 1; i >= 0; i--) {
            const caption = this.captions[i];
            caption.update();
            if (caption.life <= 0) {
                this.captions.splice(i, 1);
            }
        }
    }

    handleDamageApplication(attackerId, targetId, baseDamage) {
        if (!this.entityManager.getUnitIsActive(targetId)) {
            // console.log(`Target ${targetId} is already inactive.`);
            return;
        }

        const targetHealthStats = this.entityManager.getUnitHealth(targetId);
        const targetArmorValue = this.entityManager.getArmor(targetId); // Returns number or null
        const targetShieldStats = this.entityManager.getShield(targetId); // { current, max } or null

        if (!targetHealthStats) { // Shields and Armor might be null if unit has none, but health must exist.
            console.error(`Cannot apply damage: Target ${targetId} health stats not found.`);
            return;
        }

        let damageRemaining = baseDamage;
        let newShieldValue = targetShieldStats ? targetShieldStats.current : 0;

        // Apply to Shields first
        if (targetShieldStats && targetShieldStats.current > 0) {
            const damageToShield = Math.min(damageRemaining, targetShieldStats.current);
            newShieldValue = targetShieldStats.current - damageToShield;
            damageRemaining -= damageToShield;
            this.entityManager.setShield(targetId, newShieldValue, targetShieldStats.max);
        }

        // Apply Armor
        let damageAfterArmor = damageRemaining;
        if (damageRemaining > 0 && targetArmorValue !== null) { // targetArmorValue could be 0, which is valid
            // getArmor directly returns the numerical value or null.
            damageAfterArmor = Math.max(0, damageRemaining - targetArmorValue);
        }

        // Apply to HP
        const newHp = Math.max(0, targetHealthStats.hp - damageAfterArmor);
        this.entityManager.setUnitHealth(targetId, newHp, targetHealthStats.maxHp);

        // Handle Destruction
        if (newHp <= 0) {
            this.entityManager.setUnitIsActive(targetId, false);
            console.log(`Unit ${targetId} destroyed by ${attackerId}.`);
            if (this.gameState && typeof this.gameState.addEvent === 'function') {
                const targetType = this.entityManager.getUnitType(targetId) || 'Unknown Type';
                const attackerType = this.entityManager.getUnitType(attackerId) || 'Unknown Attacker';
                this.gameState.addEvent('death', `Unit ${targetType} (${targetId}) destroyed by ${attackerType} (${attackerId}).`, 2);
            }
        }
    }
}
exports.EntityManager = EntityManager;
// GameState class to manage game state and events
class GameState {
    constructor() {
        this.gameTime = 0;
        this.winner = null;
        this.paused = false;
        this.events = [];
        this.fpvMode = false;
        this.aimingGrenade = false;
    }
    addEvent(type, message, importance = 1, position = null) {
        const event = {
            time: this.gameTime,
            type: type,
            message: message,
            importance: importance,
            position: position
        };
        this.events.push(event);
        if (importance >= 2) {
            console.log(`[${this.gameTime.toFixed(1)}s] ${type.toUpperCase()}: ${message}`);
        }
        return event;
    }
}
exports.GameState = GameState;
// Main Simulation class
class Simulation {
    constructor(context) {
        this.GAME_SEED = context.GAME_SEED;
        this.seedRandom = context.seedRandom;
        this.HEADLESS_MODE = context.HEADLESS_MODE;
        this.RECORD_AI_DECISIONS = context.RECORD_AI_DECISIONS;
        this.RECORD_AI_DECISIONS_DURATION_SECONDS = context.RECORD_AI_DECISIONS_DURATION_SECONDS;
        this.battleJournal = context.battleJournal;
        // Initialize managers - use TrikeShed-based entity management
        this.entityManager = new EntityManager(); // Use local EntityManager
        this.gameState = new GameState();
        // Initialize Computronium managers for each team
        this.computroniumManagers = {
            blue: new computroniumManager_js_1.ComputroniumManager('blue'),
            red: new computroniumManager_js_1.ComputroniumManager('red')
        };
        // Initialize Enhanced Command Hierarchies for each team
        this.commandHierarchies = {
            blue: new enhancedCommandHierarchy_js_1.EnhancedCommandHierarchy('blue', this.computroniumManagers.blue),
            red: new enhancedCommandHierarchy_js_1.EnhancedCommandHierarchy('red', this.computroniumManagers.red)
        };
        // Initialize resources - now including Computronium
        this.resources = {
            blue: {
                mass: simulationConfig_js_1.SIMULATION_CONFIG.INITIAL_BLUE_MASS,
                energy: simulationConfig_js_1.SIMULATION_CONFIG.INITIAL_BLUE_ENERGY,
                computronium: 10, // Start with some Computronium for initial building
                massIncome: 0,
                energyIncome: 0,
                computroniumIncome: 0
            },
            red: {
                mass: simulationConfig_js_1.SIMULATION_CONFIG.INITIAL_RED_MASS,
                energy: simulationConfig_js_1.SIMULATION_CONFIG.INITIAL_RED_ENERGY,
                computronium: 10, // Start with some Computronium for initial building
                massIncome: 0,
                energyIncome: 0,
                computroniumIncome: 0
            }
        };
        // Initialize terrain and resource nodes
        this.terrain = [];
        this.resourceNodes = context.resourceNodes || [];
        // Create gameContext for compatibility with existing code
        this.gameContext = {
            terrain: this.terrain,
            resourceNodes: this.resourceNodes,
            UNIT_TYPES: unitTypes_js_1.UNIT_TYPES,
            BUILDING_TYPES: buildingTypes_js_1.BUILDING_TYPES,
            WORLD_SIZE: gameConstants_js_1.WORLD_SIZE,
            TILE_SIZE: gameConstants_js_1.TILE_SIZE,
            GRID_SIZE: gameConstants_js_1.GRID_SIZE,
            TERRAIN_TYPES: gameConstants_js_1.TERRAIN_TYPES
        };
        this.lastFrameTime = 0;
    }
    init() {
        return __awaiter(this, void 0, void 0, function* () {
            console.log('Initializing simulation...');
            // Generate terrain
            yield this.generateTerrain();
            // Setup commander build lists
            if (unitTypes_js_1.UNIT_TYPES.commander && unitTypes_js_1.UNIT_TYPES.commander.buildList) {
                unitTypes_js_1.UNIT_TYPES.commander.buildList = [
                    buildingTypes_js_1.BUILDING_TYPES.massExtractor,
                    buildingTypes_js_1.BUILDING_TYPES.energyExtractor,
                    buildingTypes_js_1.BUILDING_TYPES.landFactory,
                    buildingTypes_js_1.BUILDING_TYPES.computroniumExtractor,
                    buildingTypes_js_1.BUILDING_TYPES.advancedComputroniumCore
                ];
            }
            // Spawn commanders
            yield this.spawnCommanders();
            console.log('Simulation initialized successfully');
        });
    }
    generateTerrain() {
        return __awaiter(this, void 0, void 0, function* () {
            console.log('Generating terrain...');
            // Initialize terrain grid
            for (let x = 0; x < gameConstants_js_1.GRID_SIZE; x++) {
                this.terrain[x] = [];
                for (let y = 0; y < gameConstants_js_1.GRID_SIZE; y++) {
                    // Simple terrain generation - mostly land with some water and mountains
                    const noise = this.seedRandom.random();
                    if (noise < 0.1) {
                        this.terrain[x][y] = { type: gameConstants_js_1.TERRAIN_TYPES.WATER, elevation: -0.5 };
                    }
                    else if (noise > 0.85) {
                        this.terrain[x][y] = { type: gameConstants_js_1.TERRAIN_TYPES.MOUNTAIN, elevation: 1.0 };
                    }
                    else if (noise > 0.80) {
                        this.terrain[x][y] = { type: gameConstants_js_1.TERRAIN_TYPES.RESOURCE, elevation: 0.1 };
                    }
                    else {
                        this.terrain[x][y] = { type: gameConstants_js_1.TERRAIN_TYPES.LAND, elevation: 0.0 };
                    }
                }
            }
            // Generate resource nodes
            this.generateResourceNodes();
            console.log('Terrain generation complete');
        });
    }
    generateResourceNodes() {
        this.resourceNodes = [];
        // Place resource nodes on resource terrain tiles
        for (let x = 0; x < gameConstants_js_1.GRID_SIZE; x++) {
            for (let y = 0; y < gameConstants_js_1.GRID_SIZE; y++) {
                if (this.terrain[x][y].type === gameConstants_js_1.TERRAIN_TYPES.RESOURCE) {
                    const worldX = x * gameConstants_js_1.TILE_SIZE;
                    const worldY = y * gameConstants_js_1.TILE_SIZE;
                    // Generate different resource types including Computronium
                    const rand = this.seedRandom.random();
                    let type;
                    if (rand < 0.4) {
                        type = gameConstants_js_1.RESOURCE_TYPES.MASS;
                    }
                    else if (rand < 0.8) {
                        type = gameConstants_js_1.RESOURCE_TYPES.ENERGY;
                    }
                    else {
                        type = gameConstants_js_1.RESOURCE_TYPES.COMPUTRONIUM; // Rare but valuable
                    }
                    this.resourceNodes.push({
                        x: worldX,
                        y: worldY,
                        type: type,
                        amount: 10000,
                        maxAmount: 10000,
                        occupied: false
                    });
                }
            }
        }
        console.log(`Generated ${this.resourceNodes.length} resource nodes`);
    }
    spawnCommanders() {
        return __awaiter(this, void 0, void 0, function* () {
            console.log('Spawning commanders...');
            // Find spawn positions for commanders
            const blueSpawn = this.findSpawnPosition(0.2, 0.5);
            const redSpawn = this.findSpawnPosition(0.8, 0.5);
            if (!blueSpawn || !redSpawn) {
                throw new Error('Could not find valid spawn positions for commanders');
            }
            // Create commanders
            const blueCommander = new unit_js_1.Unit(blueSpawn.x, blueSpawn.y, 'blue', unitTypes_js_1.UNIT_TYPES.commander, this);
            const redCommander = new unit_js_1.Unit(redSpawn.x, redSpawn.y, 'red', unitTypes_js_1.UNIT_TYPES.commander, this);

        // Extract data for blue commander for the new addUnit signature
        const bcStats = unitTypes_js_1.UNIT_TYPES.commander.stats;
        this.entityManager.addUnit(blueCommander.id, unitTypes_js_1.UNIT_TYPES.commander.id, blueCommander.team, blueCommander.x, blueCommander.y, blueCommander.hp, blueCommander.maxHp, bcStats.attackDamage, bcStats.armor, 0, bcStats.maxShield || 0, bcStats.energy || 0, bcStats.maxEnergy || 0, bcStats.computroniumCores || 0);

        // Extract data for red commander
        const rcStats = unitTypes_js_1.UNIT_TYPES.commander.stats;
        this.entityManager.addUnit(redCommander.id, unitTypes_js_1.UNIT_TYPES.commander.id, redCommander.team, redCommander.x, redCommander.y, redCommander.hp, redCommander.maxHp, rcStats.attackDamage, rcStats.armor, 0, rcStats.maxShield || 0, rcStats.energy || 0, rcStats.maxEnergy || 0, rcStats.computroniumCores || 0);

            // Add Computronium cores to commanders (they are advanced units)
            this.computroniumManagers.blue.addCore(blueCommander, 1.0); // Full efficiency
            this.computroniumManagers.red.addCore(redCommander, 1.0);
            // Register commanders in command hierarchies (rank 5 = highest)
            this.commandHierarchies.blue.registerEntity(blueCommander, 5);
            this.commandHierarchies.red.registerEntity(redCommander, 5);
            console.log('[TrikeShed] Added Computronium cores and C&C nodes to commanders');
            this.gameState.addEvent('spawn', 'Commanders deployed to battlefield', 2);
            console.log('Commanders spawned successfully');
        });
    }
    findSpawnPosition(xRatio, yRatio) {
        const targetX = gameConstants_js_1.WORLD_SIZE * xRatio;
        const targetY = gameConstants_js_1.WORLD_SIZE * yRatio;
        // Try to find a land position near the target
        for (let radius = 0; radius < 200; radius += 20) {
            for (let angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                const x = targetX + Math.cos(angle) * radius;
                const y = targetY + Math.sin(angle) * radius;
                const tileX = Math.floor(x / gameConstants_js_1.TILE_SIZE);
                const tileY = Math.floor(y / gameConstants_js_1.TILE_SIZE);
                if (tileX >= 0 && tileX < gameConstants_js_1.GRID_SIZE && tileY >= 0 && tileY < gameConstants_js_1.GRID_SIZE &&
                    this.terrain[tileX] && this.terrain[tileX][tileY].type === gameConstants_js_1.TERRAIN_TYPES.LAND) {
                    return { x, y };
                }
            }
        }
        return null;
    }
    gameLoop(timestamp) {
        if (this.gameState.paused || this.gameState.winner) {
            return false;
        }
        // Calculate delta time
        const deltaTime = this.lastFrameTime > 0 ? (timestamp - this.lastFrameTime) / 1000 : 1 / 60;
        this.lastFrameTime = timestamp;
        // Update game time
        this.gameState.gameTime += deltaTime;
        // Update resource income (old system, to be replaced by new logic below)
        // this.updateResourceIncome();
        // --- New Resource Generation Logic ---
        // TODO: This is a placeholder. Actual income rates should be calculated based on game logic (buildings, tech, etc.)
        // Assuming player IDs are 0 for blue and 1 for red for this conceptual integration.
        const playerIds = { blue: 0, red: 1 };
        for (const team of ['blue', 'red']) {
            const playerId = playerIds[team];
            if (playerId === undefined)
                continue;
            // Placeholder income values per tick (deltaTime dependent)
            const massIncomePerTick = (this.resources[team].massIncome / 60) * deltaTime; // Example: Convert per-minute to per-tick
            const energyIncomePerTick = (this.resources[team].energyIncome / 60) * deltaTime;
            // Add other resource incomes (Computronium, Ferrite, Crylithium) if they generate over time
            // const computroniumIncomePerTick = (this.resources[team].computroniumIncome / 60) * deltaTime;
            let currentPlayerResources = this.gameState.getPlayerResourcesState();
            if (massIncomePerTick > 0) {
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Mass, massIncomePerTick);
            }
            if (energyIncomePerTick > 0) {
                currentPlayerResources = (0, resourceManager_js_1.addPlayerResource)(currentPlayerResources, playerId, resourceManager_js_1.PlayerResourceType.Energy, energyIncomePerTick);
            }
            // if (computroniumIncomePerTick > 0) {
            //    currentPlayerResources = addPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Computronium, computroniumIncomePerTick);
            // }
            // ... and for Ferrite, Crylithium if they have passive income.
            this.gameState.setPlayerResourcesState(currentPlayerResources);
            // console.log(`Player ${playerId} (${team}) resources updated. Mass: ${getPlayerResource(currentPlayerResources, playerId, PlayerResourceType.Mass)}`);
        }
        // --- End of New Resource Generation Logic ---
        // Update Computronium systems
        this.updateComputroniumSystems(deltaTime);
        // Update Command Hierarchies
        this.updateCommandHierarchies(deltaTime);
        // Update all entities
        this.entityManager.update(this, deltaTime);
        // Check win conditions
        this.checkWinConditions();
        // Check if we should stop recording
        if (this.RECORD_AI_DECISIONS && this.gameState.gameTime >= this.RECORD_AI_DECISIONS_DURATION_SECONDS) {
            this.gameState.winner = "RECORDING_COMPLETE";
            return false;
        }
        return true;
    }
    updateResourceIncome() {
        // Calculate income based on buildings
        for (const team of ['blue', 'red']) {
            this.resources[team].massIncome = 0;
            this.resources[team].energyIncome = 0;
            this.resources[team].computroniumIncome = 0;
            for (const building of this.entityManager.buildings) {
                if (building.team === team && building.type.resourceGeneration) {
                    const income = building.type.resourceGeneration.amount * 60; // per minute
                    if (building.type.resourceGeneration.type === 'mass') {
                        this.resources[team].massIncome += income;
                    }
                    else if (building.type.resourceGeneration.type === 'energy') {
                        this.resources[team].energyIncome += income;
                    }
                }
            }
            // Computronium income from cores
            const computroniumManager = this.computroniumManagers[team];
            const stats = computroniumManager.getGenerationStats();
            if (stats) {
                this.resources[team].computroniumIncome = stats.currentRate * 60; // per minute
            }
        }
    }
    updateComputroniumSystems(deltaTime) {
        // Update each team's computronium management
        for (const team of ['blue', 'red']) {
            const manager = this.computroniumManagers[team];
            const generated = manager.update(deltaTime);
            // Add generated computronium to team resources
            this.resources[team].computronium += generated;
            // Log significant computronium generation
            if (generated > 0.1) {
                console.log(`[${team.toUpperCase()}] Generated ${generated.toFixed(3)} computronium. Total: ${this.resources[team].computronium.toFixed(2)}`);
            }
        }
        // Handle potential computational warfare between teams
        this.handleComputationalWarfare();
    }
    handleComputationalWarfare() {
        // Simple AI-driven computational warfare
        for (const attackerTeam of ['blue', 'red']) {
            const defenderTeam = attackerTeam === 'blue' ? 'red' : 'blue';
            const attacker = this.computroniumManagers[attackerTeam];
            const defender = this.computroniumManagers[defenderTeam];
            // Randomly launch PoW attacks if attacker has enough capability
            if (attacker.powAttackCapability > 0 && this.seedRandom.random() < 0.001) { // 0.1% chance per frame
                const intensity = Math.min(3, attacker.powAttackCapability);
                if (attacker.launchPowAttack(defender, intensity)) {
                    this.gameState.addEvent('computational_warfare', `${attackerTeam.toUpperCase()} launches computational attack on ${defenderTeam.toUpperCase()}!`, 2);
                }
            }
        }
    }
    updateCommandHierarchies(deltaTime) {
        // Update each team's command hierarchy
        for (const team of ['blue', 'red']) {
            const hierarchy = this.commandHierarchies[team];
            hierarchy.update(deltaTime);
            // Periodically issue strategic commands
            if (this.gameState.gameTime % 5 < deltaTime) { // Every 5 seconds
                this.issueStrategicCommands(team);
            }
        }
    }
    issueStrategicCommands(team) {
        const hierarchy = this.commandHierarchies[team];
        const enemyTeam = team === 'blue' ? 'red' : 'blue';
        // Find enemy units to target
        // TODO: This needs to be refactored to use cursors to find enemy units
        const enemyUnits = []; // Placeholder this.entityManager.units.filter(u => u.team === enemyTeam && u.hp > 0);
        if (enemyUnits.length > 0) {
            // Issue attack command to nearest enemy
            const randomEnemy = enemyUnits[Math.floor(this.seedRandom.random() * enemyUnits.length)];
            const success = hierarchy.issueStrategicCommand('attack', {
                target: randomEnemy
            });
            if (success) {
                console.log(`[C&C] ${team.toUpperCase()} hierarchy issued attack command on ${randomEnemy.type}`);
            }
        }
        else {
            // No enemies, issue patrol command
            hierarchy.issueStrategicCommand('move', {
                target: {
                    x: this.seedRandom.random() * gameConstants_js_1.WORLD_SIZE,
                    y: this.seedRandom.random() * gameConstants_js_1.WORLD_SIZE
                }
            });
        }
    }
    checkWinConditions() {
        if (this.gameState.winner)
            return;
        // TODO: Refactor to use cursors to find commanders
        let blueCommanderAlive = false;
        let redCommanderAlive = false;
        for (let i = 0; i < this.entityManager.entityIdToIndex.size; i++) {
            if (this.entityManager.isActiveCursor.get([i, 0])) {
                const type = this.entityManager.unitTypeCursor.get([i, 0]);
                const team = this.entityManager.ownerTeamCursor.get([i, 0]);
                if (type === unitTypes_js_1.UNIT_TYPES.commander.id) { // Assuming type stores ID string
                    if (team === 'blue') blueCommanderAlive = true;
                    if (team === 'red') redCommanderAlive = true;
                }
            }
        }

        if (!blueCommanderAlive && !redCommanderAlive) {
            this.gameState.winner = 'DRAW';
            this.gameState.addEvent('game_over', 'Both commanders destroyed - Draw!', 3);
        }
        else if (!blueCommanderAlive) {
            this.gameState.winner = 'RED';
            this.gameState.addEvent('game_over', 'Red team wins! Blue commander destroyed!', 3);
        }
        else if (!redCommanderAlive) {
            this.gameState.winner = 'BLUE';
            this.gameState.addEvent('game_over', 'Blue team wins! Red commander destroyed!', 3);
        }
    }
}
exports.Simulation = Simulation;
// Utility function for time formatting
function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}
