import { UNIT_TYPES } from '../config/unitTypes.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE, TERRAIN_TYPES } from '../config/gameConstants.js';
import { findPath } from '../pathfinding/astar.js';
import { GrenadeProjectile } from './entities/projectile.js';
import { Effect } from './entities/effect.js';
import { Caption } from './entities/caption.js';
import { Building } from './building.js';
import { UnitProgression } from './unitProgression.js';
import { AutonomousBehavior } from './autonomousBehavior.js';
import { COMMAND_CONFIG } from '../config/commandConfig.js';
import { isTraversable } from '../pathfinding/astar.js';
import { ALLOY_TYPES } from '../config/alloyTypes.js';

const WEIGHT_SPEED_PENALTY_FACTOR = 0.01;
const DEFAULT_UNIT_SPEED = 1.0;
const DEFAULT_UNIT_WEIGHT = 0;
const SHIELD_EFFECTIVENESS_WATTAGE_BASELINE = 20; // Baseline weaponEnergyCost for 1x effectiveness vs shields.
const DEFAULT_SHIELD_REGEN_RATE = 1.0; // Default shield regen per second if not specified.

class Unit {
    constructor(x, y, team, type, simulation) {
        this.x = x;
        this.y = y;
        this.team = team;
        this.type = type; // Store the original type definition

        // Initialize base stats from type that will be modified by alloys
        this.maxHp = type.maxHp;
        this.hp = type.maxHp; // Current HP should also be initialized based on maxHp
        this.armorValue = type.armorValue || 0; // Initialize armorValue from type definition

        this.target = null;
        this.cooldown = 0; // Represents attack cooldown time remaining
        // Access seedRandom from the simulation instance's direct property
        this.angle = simulation.seedRandom ? simulation.seedRandom.random() * Math.PI * 2 : Math.random() * Math.PI * 2; 
        this.vx = 0;
        this.currentEnergy = type.batteryCapacity || 0; // Initialize to full capacity
        this.vy = 0;
        this.selected = false;
        this.task = null; // Generic task, could be expanded
        this.shields = type.shields || 0; // This is the old HP shield, will be replaced/complemented by energy shields
        this.maxShields = type.shields || 0; // Old HP shield
        this.shieldRegen = type.shieldRegen || 0; // Old HP shield regen
        this.currentEnergyShields = this.type.maxEnergyShields || 0; // New energy shield
        this.patrolTarget = null;
        this.lastTargetSwitch = 0;
        // Access seedRandom from the simulation instance's direct property
        this.aggressiveness = 0.7 + (simulation.seedRandom ? simulation.seedRandom.random() * 0.3 : Math.random() * 0.3); 
        this.tacticalRole = this.determineTacticalRole();
        this.lastFireTime = 0;
        this.preferredRange = this.type.range * 0.8;
        this.militaryRank = this.determineMilitaryRank();
        this.survivalPriority = this.calculateSurvivalPriority();
        this.commandAuthority = this.type.tier * 10 + (this.type.support ? 5 : 0); // Original command authority
        this.protectionNeeds = [];
        this.lastThreatAssessment = 0;
        this.fleeThreshold = this.maxHp * 0.2; // Will be updated after alloy modification
        this.formation = null;
        
        // Speed calculation considering weight (initial speed based on type)
        const baseSpeed = type.speed || DEFAULT_UNIT_SPEED;
        const weight = type.unitWeight || DEFAULT_UNIT_WEIGHT; // Assuming unitWeight might be a future property
        let speedDenominator = 1 + (weight * WEIGHT_SPEED_PENALTY_FACTOR);
        if (speedDenominator <= 0.1) {
            speedDenominator = 0.1;
        }
        this.speed = baseSpeed / speedDenominator; // Instance property for speed
        if (this.speed < 0) { // Ensure speed is not negative
            this.speed = 0;
        }

        // Initialize other potentially modifiable stats as instance properties
        this.shieldRegenRate = type.shieldRegenRate || (type.maxEnergyShields > 0 ? DEFAULT_SHIELD_REGEN_RATE : 0);
        this.coreEfficiency = type.coreEfficiency || 1.0; // Base efficiency, to be modified by alloy

        // Apply Alloy Modifiers
        const unitAlloyKey = type.alloyType || 'STANDARD_PLASTEEL'; // Default to standard if not specified
        const alloy = ALLOY_TYPES[unitAlloyKey];

        if (alloy) {
            if (alloy.modifiers.hpFactor) {
                this.maxHp = Math.round(this.maxHp * alloy.modifiers.hpFactor);
                this.hp = this.maxHp; // Also set current HP to new max
            }
            if (alloy.modifiers.armorAdd) {
                this.armorValue += alloy.modifiers.armorAdd;
            }
            if (alloy.modifiers.speedFactor) {
                this.speed *= alloy.modifiers.speedFactor;
            }
            // Cost factor modification is noted to be handled at purchase time, not on the instance.

            if (alloy.modifiers.shieldRegenFactor) {
                this.shieldRegenRate *= alloy.modifiers.shieldRegenFactor;
            }

            if (alloy.modifiers.computroniumEfficiencyFactor) {
                // This will be used if/when the computroniumCore is added or its efficiency is set.
                this.coreEfficiency *= alloy.modifiers.computroniumEfficiencyFactor;
            }
        }

        // Ensure stats don't go below reasonable minimums after modification
        this.speed = Math.max(0.1, this.speed);
        this.maxHp = Math.max(1, this.maxHp);
        // Ensure current hp is not greater than maxHp, and at least 1 if maxHp is > 0
        this.hp = Math.min(this.maxHp, Math.max(1, this.hp));
        if (this.maxHp === 0) this.hp = 0;
        this.armorValue = Math.max(0, this.armorValue);

        // Update fleeThreshold based on new maxHp
        this.fleeThreshold = this.maxHp * 0.2;


        // Add Computronium core if this unit type has one
        // Note: type.coreEfficiency is the base, this.coreEfficiency has alloy modification applied above.
        if (type.hasComputroniumCore && simulation && simulation.computroniumManagers) {
            const manager = simulation.computroniumManagers[team];
            if (manager) {
                // Pass the potentially alloy-modified coreEfficiency (this.coreEfficiency) to the manager
                this.computroniumCore = manager.addCore(this, this.coreEfficiency);
                // If the core was added and has its own efficiency property, make sure it reflects this.coreEfficiency
                if (this.computroniumCore && typeof this.computroniumCore.efficiency === 'number') {
                    this.computroniumCore.efficiency = this.coreEfficiency;
                }
                console.log(`[Unit] Added Computronium core to ${type.name} with efficiency ${this.coreEfficiency}`);
            }
        }
        
        // Register in command hierarchy
        if (simulation && simulation.commandHierarchies) {
            const hierarchy = simulation.commandHierarchies[team];
            if (hierarchy) {
                const commandRank = type.commandRank || Math.max(1, type.tier || 1);
                this.commandNode = hierarchy.registerEntity(this, commandRank);
                console.log(`[Unit] Registered ${type.name} in command hierarchy (rank ${commandRank})`);
            }
        }
        this.captionCooldown = 0;
        this.constructionTask = null; // Specific for ACU/Engineer type units
        if (this.type.grenadeAbility) {
            this.grenadeCooldown = 0;
        }
        this.stuckFrames = 0;
        this.significantMoveThreshold = (this.type.size / 4) || 2.5; // Min distance in world units
        this.lastPositionForStuckCheck = { x: this.x, y: this.y };
        this.isEscaping = false;
        this.escapeAngle = 0;
        this.escapeDuration = 0;
        this.STUCK_FRAMES_THRESHOLD = 30;
        this.ESCAPE_MODE_DURATION_FRAMES = 60;
        this.path = null;
        this.currentWaypointIndex = 0;
        this.pathRequestCooldown = 0;
        this.PATH_REQUEST_INTERVAL = 30; // Request path every ~0.5s at 60fps

        // Track who this unit is following and last command change
        this.currentCommander = null;
        this.lastCommandChange = 0;

        // Computronium integration
        this.computroniumCoreLevel = type.computroniumCoreLevel || 0;
        this.coreFocusMode = type.defaultCoreFocusMode || 'BALANCED';
        this.computroniumAuthorityModifier = 0;
        // this.computroniumCore is initialized above after alloy mods to coreEfficiency
        this.lastFocusModeChange = 0;
        this.focusModeCooldown = 10000; // 10 seconds cooldown between mode changes

        // Core Focus Mode effects
        this.focusModeEffects = {
            OFFENSIVE: {
                weaponEfficiency: 1.2,
                c2Efficiency: 0.8,
                powEfficiency: 0.7,
                sensorEfficiency: 0.9
            },
            DEFENSIVE: {
                weaponEfficiency: 0.9,
                c2Efficiency: 0.9,
                powEfficiency: 0.8,
                sensorEfficiency: 1.1
            },
            C_C: {
                weaponEfficiency: 0.7,
                c2Efficiency: 1.3,
                powEfficiency: 1.2,
                sensorEfficiency: 1.2
            },
            UTILITY: {
                weaponEfficiency: 0.8,
                c2Efficiency: 1.1,
                powEfficiency: 0.9,
                sensorEfficiency: 1.0
            },
            BALANCED: {
                weaponEfficiency: 1.0,
                c2Efficiency: 1.0,
                powEfficiency: 1.0,
                sensorEfficiency: 1.0
            }
        };

        // Initialize progression system
        this.progression = new UnitProgression(this);
        
        // Add progression-related properties
        // this.baseAuthority = this.calculateBaseAuthority(); // This line is now part of the new properties block
        // this.effectiveAuthority = this.baseAuthority; // This line is now part of the new properties block
        
        // Add promotion callback
        this.onPromotion = null;

        // Add autonomous behavior
        this.autonomousBehavior = null;
        this.hasExplicitOrders = false;

        // Enhanced authority properties (as per implementation-guide.md Section 1.1)
        this.baseAuthority = this.commandAuthority; // Use original commandAuthority for base
        this.healthAuthorityModifier = 0;
        this.veterancyAuthorityModifier = 0;
        this.contextAuthorityModifier = 0;
        this.computroniumAuthorityModifier = 0;
        this.effectiveAuthority = this.baseAuthority;

        // Veterancy tracking
        this.combatExperience = 0;
        this.survivalTime = 0;
        this.commandExperience = 0;
        this.killCount = 0;
        this.damageDelt = 0; // Corrected typo from damageDelt to damageDealt if intended, guide says damageDelt
        this.lastPromotionTime = 0;
        this.veterancyLevel = 'GREEN';

        // Health-based command fitness
        this.commandFitness = 'FULL_COMMAND';
        this.lastAuthorityUpdate = 0;
        this.commandSuccesses = 0;
        this.commandFailures = 0;

        // Veterancy benefits flags
        this.canPromoteSubordinates = false;
        this.provideMoraleBonus = false;

        // Random angle for initial radial formation slot position.
        this.formationAngle = Math.random() * Math.PI * 2;

        // --- Formation Movement & Steering Properties ---
        // Defines a specific offset {x, y, angle} from the leader; more precise than formationAngle. (Currently unused, formationAngle provides simpler radial slots).
        this.formationOffset = { x: 0, y: 0, angle: 0 };
        // For leaders: their current A* path waypoint or patrol target. For followers: null when in formation.
        this.leaderTargetPosition = null;
        // For followers: the leader's predicted future position.
        this.leaderPredictedPosition = null;
        // For followers: the calculated world coordinates of their ideal formation slot.
        this.idealFormationSlotWorld = null;
        // Maximum magnitude of combined steering forces applicable per frame.
        this.maxForce = COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_FORCE;
        // Maximum rate (radians per frame) at which the unit can turn.
        this.maxTurnRate = COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_TURN_RATE_RADIANS_PER_FRAME;
        // Accumulator for steering forces (seek, separation, avoidance) each frame.
        this.steering = { x: 0, y: 0 };
        // Flag to enable detailed console logging for this unit's formation behavior.
        this.debugFormation = false;
    }

    // Removed the duplicate placeholder calculateBaseAuthority() method from here

    getCurrentSpeed(simulation) { // Renamed gameContext to simulation
        const terrain = simulation.terrain; // Access terrain directly from simulation
        // TILE_SIZE, GRID_SIZE, TERRAIN_TYPES are imported globally

        const tileX = Math.floor(this.x / TILE_SIZE);
        const tileY = Math.floor(this.y / TILE_SIZE);

        if (tileX >= 0 && tileX < GRID_SIZE && tileY >= 0 && tileY < GRID_SIZE &&
            terrain[tileX] && terrain[tileX][tileY] !== undefined) {
            const terrainType = terrain[tileX][tileY];

            if (this.type.movementType === 'amphibious') {
                // Amphibious units might have different base speeds for land/water,
                // these base speeds would then be adjusted by the single weight factor.
                let terrainSpecificBaseSpeed = this.type.speed; // default to generic speed
                if (terrainType === TERRAIN_TYPES.WATER && typeof this.type.speedWater === 'number') {
                    terrainSpecificBaseSpeed = this.type.speedWater;
                } else if (terrainType === TERRAIN_TYPES.LAND && typeof this.type.speedLand === 'number') {
                    terrainSpecificBaseSpeed = this.type.speedLand;
                }
                // The weight penalty is applied to this terrain-specific base speed.
                // We re-use this.speed which was already calculated with the generic this.type.speed.
                // A more accurate way would be to calculate effective speed here *each time* based on current terrain.
                // For now, this.speed (calculated once in constructor) is used by movement logic.
                // To make it fully terrain-dependent with weight:
                // const weight = this.type.unitWeight || DEFAULT_UNIT_WEIGHT;
                // let speedDenominator = 1 + (weight * WEIGHT_SPEED_PENALTY_FACTOR);
                // if (speedDenominator <= 0.1) speedDenominator = 0.1;
                // return (terrainSpecificBaseSpeed || DEFAULT_UNIT_SPEED) / speedDenominator;
                // However, getCurrentSpeed is used to get the *current* effective speed.
                // this.speed (instance property) should store the final, adjusted speed.
                // The current implementation of movement logic uses getCurrentSpeed(), so let's adjust it there.

                // Re-evaluating: this.speed should be the *effective* speed.
                // getCurrentSpeed() should return this.speed, potentially adjusted for temporary effects (not terrain base speed).
                // The constructor correctly sets this.speed based on general type.speed.
                // If amphibious units have different *base* speeds on land/water, then getCurrentSpeed should
                // calculate the effective speed based on *that terrain's base speed* and the *unit's weight*.
                // This means the constructor's single this.speed might be too simple if base speed varies by terrain.
                // Let's assume for now the constructor sets a general effective speed, and terrain might apply a multiplier later if needed.
                // For this subtask, the goal is that this.speed (used by movement logic) is weight-adjusted.
                // The current getCurrentSpeed() returns type.speedWater or type.speedLand *unadjusted* by weight.
                // This needs to be fixed: getCurrentSpeed should return the *effective* speed.
                // The constructor sets this.speed. getCurrentSpeed should use that.
                // If base speed for amphibious units changes, it should be a multiplier on this.speed or recalculate.

                // Corrected logic: this.speed is the single, weight-adjusted speed.
                // If amphibious units have different base speeds, those should be adjusted by weight too.
                // Let's assume this.type.speed is the primary speed, adjusted by weight.
                // If speedWater/speedLand exist, they are *alternative base speeds* that also need weight adjustment.
                const weightFactor = 1 + ((this.type.unitWeight || DEFAULT_UNIT_WEIGHT) * WEIGHT_SPEED_PENALTY_FACTOR);
                const denominator = Math.max(0.1, weightFactor);

                if (terrainType === TERRAIN_TYPES.WATER && typeof this.type.speedWater === 'number') {
                    return (this.type.speedWater || DEFAULT_UNIT_SPEED) / denominator;
                } else if (terrainType === TERRAIN_TYPES.LAND && typeof this.type.speedLand === 'number') {
                    return (this.type.speedLand || DEFAULT_UNIT_SPEED) / denominator;
                }
            }
        }
        // For non-amphibious units, or amphibious units on default terrain, this.speed (already weight-adjusted) is used.
        // If getCurrentSpeed is *only* for terrain specific base speed (before weight), then movement logic must use this.speed.
        // The current code uses getCurrentSpeed() in movement. So getCurrentSpeed MUST return the final effective speed.
        return this.speed; // this.speed is already weight-adjusted.
    }

    update(simulation, deltaTime) { // Renamed gameContext to simulation, deltaTime is passed
        const { entityManager, gameState, seedRandom } = simulation;
        const { units, buildings } = entityManager; // Get entities from entityManager
        // deltaTime is now a direct parameter

        // Energy Regeneration
        if (typeof this.type.generatorOutput === 'number' && typeof this.type.batteryCapacity === 'number') {
            this.currentEnergy += this.type.generatorOutput * deltaTime;
            if (this.currentEnergy > this.type.batteryCapacity) {
                this.currentEnergy = this.type.batteryCapacity;
            }
        }

        // Shield Regeneration (New Energy Shields)
        if (this.type.maxEnergyShields > 0) { // Check if the unit type is supposed to have shields
            if (this.currentEnergyShields < this.type.maxEnergyShields) { // Check if current shields are less than max defined by type
                const regenRate = this.shieldRegenRate; // Use instance-specific shieldRegenRate (already alloy-modified)
                this.currentEnergyShields += regenRate * deltaTime;
                if (this.currentEnergyShields > this.type.maxEnergyShields) { // Ensure shields do not exceed max for type
                    this.currentEnergyShields = this.type.maxEnergyShields;
                }
            }
        }

        // Old HP Shield Regeneration (if still intended to be separate)
        if (this.maxShields > 0 && this.shields < this.maxShields) { // Assuming this.shields is the old HP shield
            this.shields = Math.min(this.maxShields, this.shields + (this.shieldRegen || 0) * deltaTime);
        }


        if (this.type.support) {
            this.performSupportRole(simulation, deltaTime); // Pass simulation and deltaTime
            if (this.type === UNIT_TYPES.commander && this.constructionTask) {
                 /* Commander busy */
            } else if (this.type.name === 'Shield Generator') {
                /* Shield gen busy */
            } else if (this.type.name === 'Engineer' && this.target) {
                /* Engineer busy */
            } else {
                 this.defaultMovementAndTargeting(simulation, deltaTime); // Pass simulation and deltaTime
            }
        } else {
            this.defaultMovementAndTargeting(simulation, deltaTime); // Pass simulation and deltaTime
        }

        this.updateStuckDetection(simulation); // Pass simulation
        this.updateTacticalBehavior(simulation); // Pass simulation
        this.updateSurvivalBehaviors(simulation); // Pass simulation
        this.executeCommandHierarchy(simulation); // Pass simulation
        
        if (this.cooldown > 0) this.cooldown -= deltaTime;
        if (this.pathRequestCooldown > 0) this.pathRequestCooldown -= deltaTime;
        if (this.type.grenadeAbility && this.grenadeCooldown > 0) {
            this.grenadeCooldown -= deltaTime;
        }
        if (this.captionCooldown > 0) this.captionCooldown -= deltaTime;

        // Update progression
        this.progression.update(simulation, deltaTime);

        // Update autonomous behavior if no explicit orders
        if (this.autonomousBehavior && !this.hasExplicitOrders) {
            this.autonomousBehavior.update(deltaTime);
        }

        // Update authority every 5 seconds (as per implementation-guide.md Section 1.3)
        const now = performance.now();
        if (now - this.lastAuthorityUpdate > COMMAND_CONFIG.UPDATE_INTERVALS.AUTHORITY_RECALC) {
            this.calculateEffectiveAuthority();
            this.lastAuthorityUpdate = now;

            // Check for command succession needs
            if (this.commandFitness === 'COMBAT_INEFFECTIVE' ||
                this.commandFitness === 'CRITICAL_STATUS') {
                this.triggerCommandSuccession(simulation); // Pass simulation as gameContext
            }
        }

        // Update veterancy tracking (as per implementation-guide.md Section 1.3)
        this.updateVeterancyProgress(simulation); // Pass simulation as gameContext
<<<<<<< HEAD
        
        // Apply morale bonus if capable
        if (this.provideMoraleBonus) {
            this.applyMoraleBonus(simulation);
        }
        
        // Handle subordinate promotions if capable
        if (this.canPromoteSubordinates) {
            this.handleSubordinatePromotions(simulation);
        }
=======
>>>>>>> origin/jules_wip_12008771546559725757
    }

    defaultMovementAndTargeting(simulation, deltaTime) { // Renamed gameContext, added deltaTime
        const { entityManager, gameState, seedRandom } = simulation;
        const { units, buildings } = entityManager;
        
        // Add null checks for units and buildings arrays
        if (!units || !Array.isArray(units) || !buildings || !Array.isArray(buildings)) {
            return;
        }
        
        const { resourceNodes } = simulation.gameContext; // Assuming resourceNodes is on the original gameContext object

        if (this.isEscaping) {
            if (this.escapeDuration > 0) {
                this.angle = this.escapeAngle;
                const currentSpeed = this.getCurrentSpeed(simulation);
                this.vx = Math.cos(this.angle) * currentSpeed;
                this.vy = Math.sin(this.angle) * currentSpeed;
                this.escapeDuration -= deltaTime * 60; // Assuming 60 FPS for duration conversion
            } else {
                this.isEscaping = false;
                this.stuckFrames = 0;
                this.path = null; 
            }
        } else {
            if (!this.target || this.target.hp <= 0 || (Date.now() - this.lastTargetSwitch > 15000 && seedRandom.random() < 0.05)) {
                this.findTarget(simulation);
                this.lastTargetSwitch = Date.now();
                this.path = null;
            }
            if (seedRandom.random() < 0.005 && !this.target && !this.patrolTarget) {
                if (resourceNodes && resourceNodes.length > 0) {
                    const targetNode = resourceNodes[Math.floor(seedRandom.random() * resourceNodes.length)];
                    if (targetNode) {
                        this.patrolTarget = { x: targetNode.x, y: targetNode.y };
                        this.path = null;
                    }
                }
            }

            const currentPrimaryDestination = this.target || this.patrolTarget;

            if (currentPrimaryDestination) {
                let needsNewPath = false;
                if (!this.path) {
                    needsNewPath = true;
                } else {
                    if (this.pathRequestCooldown <= 0) {
                        const pathEndPoint = this.path[this.path.length-1];
                        const dxTarget = currentPrimaryDestination.x - pathEndPoint.x;
                        const dyTarget = currentPrimaryDestination.y - pathEndPoint.y;
                        if (Math.sqrt(dxTarget*dxTarget + dyTarget*dyTarget) > TILE_SIZE * 2) {
                           needsNewPath = true;
                        }
                    }
                }

                if (needsNewPath && this.pathRequestCooldown <= 0) {
                    const moveType = this.type.movementType || 'land';
                    this.path = findPath(
                        { x: this.x, y: this.y },
                        { x: currentPrimaryDestination.x, y: currentPrimaryDestination.y },
                        simulation.gameContext, // findPath expects the original gameContext with terrain etc.
                        moveType
                    );
                    this.currentWaypointIndex = 0;
                    this.pathRequestCooldown = this.PATH_REQUEST_INTERVAL * deltaTime; // Adjust by deltaTime
                    if (!this.path) {
                        gameState.addEvent('debug', `Path not found: ${this.type.name} to ${currentPrimaryDestination.type ? currentPrimaryDestination.type.name : 'point'}`, 0);
                    } else {
                         gameState.addEvent('debug', `Path found for ${this.type.name} with ${this.path.length} waypoints.`, 0);
                    }
                }

                if (this.path && this.currentWaypointIndex < this.path.length) {
                    const waypoint = this.path[this.currentWaypointIndex];
                    const dx = waypoint.x - this.x;
                    const dy = waypoint.y - this.y;
                    const distanceToWaypoint = Math.sqrt(dx * dx + dy * dy);
                    const WAYPOINT_REACH_THRESHOLD = Math.max(this.type.size || 10, TILE_SIZE * 0.75);

                    if (distanceToWaypoint < WAYPOINT_REACH_THRESHOLD) {
                        this.currentWaypointIndex++;
                        if (this.currentWaypointIndex >= this.path.length) { 
                            this.path = null;
                            if (this.patrolTarget && Math.abs(this.x - this.patrolTarget.x) < WAYPOINT_REACH_THRESHOLD && Math.abs(this.y - this.patrolTarget.y) < WAYPOINT_REACH_THRESHOLD) {
                                this.patrolTarget = null; 
                            }
                        }
                    }

                    if (this.path && this.currentWaypointIndex < this.path.length) {
                        const nextWaypoint = this.path[this.currentWaypointIndex];
                        this.angle = Math.atan2(nextWaypoint.y - this.y, nextWaypoint.x - this.x);
                        const currentSpeed = this.getCurrentSpeed(simulation);
                        this.vx = Math.cos(this.angle) * currentSpeed;
                        this.vy = Math.sin(this.angle) * currentSpeed;
                    } else { 
                        this.vx = 0; this.vy = 0;
                    }
                } else if (this.target) {
                    const distToTarget = this.getDistance(this.target);
                    this.handleCombatPositioning(distToTarget, simulation);
                } else if (this.patrolTarget) { 
                    const distToPatrol = Math.sqrt(Math.pow(this.patrolTarget.x - this.x, 2) + Math.pow(this.patrolTarget.y - this.y, 2));
                     if (distToPatrol < TILE_SIZE * 2) {
                        this.angle = Math.atan2(this.patrolTarget.y - this.y, this.patrolTarget.x - this.x);
                        const currentSpeed = this.getCurrentSpeed(simulation);
                        this.vx = Math.cos(this.angle) * currentSpeed;
                        this.vy = Math.sin(this.angle) * currentSpeed;
                     } else {
                        this.vx = 0; this.vy = 0;
                     }
                      if (distToPatrol < TILE_SIZE * 0.5) this.patrolTarget = null; 
                } else { 
                    if (seedRandom.random() < 0.02) { this.angle += (seedRandom.random() - 0.5) * 0.5; } 
                    const currentSpeed = this.getCurrentSpeed(simulation);
                    this.vx = Math.cos(this.angle) * currentSpeed * 0.5;
                    this.vy = Math.sin(this.angle) * currentSpeed * 0.5;
                }
            } else { 
                if (seedRandom.random() < 0.02) { this.angle += (seedRandom.random() - 0.5) * 0.5; } 
                const currentSpeed = this.getCurrentSpeed(simulation);
                this.vx = Math.cos(this.angle) * currentSpeed * 0.5;
                this.vy = Math.sin(this.angle) * currentSpeed * 0.5;
            }
        } 

        this.applyMovement(simulation); // Pass simulation
        // Cooldowns are handled in the main update method with deltaTime
    }

    launchGrenade(targetX, targetY, simulation) { // Renamed gameContext to simulation
        const { entityManager, gameState, seedRandom } = simulation;

        if (!this.type.grenadeAbility) {
            console.warn(`${this.type.name} does not have grenade ability.`);
            return;
        }

        if (this.grenadeCooldown > 0) { // Cooldown is now time-based
            console.log(`${this.type.name} grenade is on cooldown: ${this.grenadeCooldown.toFixed(1)}s left.`);
            gameState.addEvent('ui_error', 'Grenade ability on cooldown!', 1);
            return;
        }

        const dx = targetX - this.x;
        const dy = targetY - this.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist > this.type.grenadeAbility.range) {
            console.log(`Target out of grenade range. Max: ${this.type.grenadeAbility.range}, Target: ${dist.toFixed(0)}`);
            gameState.addEvent('ui_error', 'Target out of grenade range!', 1);
            return;
        }
        
        console.log(`${this.team} ${this.type.name} launching grenade at ${targetX.toFixed(0)}, ${targetY.toFixed(0)}`);

        const projectile = new GrenadeProjectile( // Direct constructor
            this.x, this.y, 
            targetX, targetY,
            this.team,
            this.type.grenadeAbility
        );
        entityManager.addProjectile(projectile); // Use entityManager

        this.grenadeCooldown = this.type.grenadeAbility.cooldownTime; // Cooldown in seconds

        gameState.addEvent('ability_used', `${this.type.name} launched grenade.`, 2, { x: this.x, y: this.y });
    }


    performSupportRole(simulation, deltaTime) { // Renamed gameContext, added deltaTime
        const { entityManager, gameState, seedRandom, resources } = simulation;
        const { units, buildings, addBuilding, addCaption } = entityManager;
        const { addEvent } = gameState;
        const { resourceNodes } = simulation.gameContext; // Assuming resourceNodes on original gameContext

        if (this.type === UNIT_TYPES.commander) {
            if (!this.constructionTask && this.type.buildList && this.type.buildList.length > 0) {
                let buildingToBuildType = null;
                const teamResources = resources[this.team];
                
                // Add null checks for buildings array
                if (!buildings || !Array.isArray(buildings)) return;
                
                const teamExtractors = buildings.filter(b => b.team === this.team && (b.type.name === 'Mass Extractor' || b.type.name === 'Energy Plant')).length;
                const teamFactories = buildings.filter(b => b.team === this.team && b.type.produces && b.type.produces.length > 0).length;
                
                if (teamExtractors === 0) {
                    buildingToBuildType = this.type.buildList.find(bt => bt.name === 'Mass Extractor' && teamResources.mass >= bt.cost.mass && teamResources.energy >= bt.cost.energy);
                }
                if (!buildingToBuildType && teamExtractors >= 1 && buildings.filter(b => b.team === this.team && b.type.name === 'Energy Plant').length === 0) {
                    buildingToBuildType = this.type.buildList.find(bt => bt.name === 'Energy Plant' && teamResources.mass >= bt.cost.mass && teamResources.energy >= bt.cost.energy);
                }
                // ... (simplified other phases for brevity, apply similar resource checks) ...

                if (buildingToBuildType) {
                    // ... (build offset logic remains similar) ...
                    // Example for one offset:
                    const buildX = this.x + 100; const buildY = this.y;
                    // ... (spot clear check) ...
                    if (true /* isSpotClear */) {
                         this.constructionTask = { targetX: buildX, targetY: buildY, type: buildingToBuildType, progress: 0, buildingStarted: false };
                    }
                }
            }

            if (this.constructionTask) {
                if (!this.constructionTask.buildingStarted) {
                    // ... (move to site logic) ...
                    // On arrival:
                    // resources[this.team].mass -= this.constructionTask.type.cost.mass; // Already handled by gameState.resources
                    // ...
                    // addCaption(new Caption(...));
                } else {
                    this.constructionTask.progress += (this.type.buildRate || 1.0) * deltaTime; // Progress based on deltaTime
                    // ... (check progress, create building) ...
                    if (this.constructionTask.progress >= this.constructionTask.type.buildTime) {
                        const newBuilding = new Building(this.constructionTask.targetX, this.constructionTask.targetY, this.team, this.constructionTask.type, simulation); // Pass simulation
                        addBuilding(newBuilding);
                        addEvent('build', `${this.team.toUpperCase()} ACU completed ${this.constructionTask.type.name}!`, 2, { x: newBuilding.x, y: newBuilding.y });
                        this.constructionTask = null;
                    }
                }
                return; 
            }
            this.defaultMovementAndTargeting(simulation, deltaTime);

        } else if (this.type.name === 'Engineer') {
            // ... (similar refactoring for Engineer logic, using simulation.entityManager, simulation.gameState, simulation.seedRandom) ...
            // Example:
            // buildings.push(...) -> addBuilding(new Building(...simulation...))
            // addEvent(...) -> gameState.addEvent(...)
        } else if (this.type.name === 'Shield Generator') {
            // ... (Shield Generator logic) ...
            this.defaultMovementAndTargeting(simulation, deltaTime);
        }
    }

    moveTowards(target, simulation) { // Renamed gameContext
        const dx = target.x - this.x;
        const dy = target.y - this.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const moveThreshold = target.type ? target.type.size / 2 : 50;

        if (dist > moveThreshold) {
            this.angle = Math.atan2(dy, dx);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed;
            this.vy = Math.sin(this.angle) * currentSpeed;
        } else {
            this.vx = 0;
            this.vy = 0;
        }
        this.applyMovement(simulation);
    }

    applyMovement(simulation) { // Renamed gameContext
        const terrain = simulation.terrain; 
        // ... (rest of applyMovement logic, using simulation.entityManager.units and simulation.seedRandom for separation) ...
        const allUnits = simulation.entityManager.units;
        // ...
        // pushX = (simulation.seedRandom.random() - 0.5) * SEPARATION_STRENGTH * overlap; 
    }

    findTarget(simulation) { // Renamed gameContext
        const { entityManager } = simulation;
        const { units, buildings } = entityManager;
        
        // Add null checks for units and buildings arrays
        if (!units || !Array.isArray(units) || !buildings || !Array.isArray(buildings)) {
            return;
        }
        
        let closestTarget = null;
        let closestDistance = Infinity;
        
        // Look for enemy units first
        const enemyUnits = units.filter(u => u.team !== this.team && u.hp > 0);
        for (const unit of enemyUnits) {
            const distance = this.getDistance(unit);
            if (distance <= this.type.range && distance < closestDistance) {
                closestTarget = unit;
                closestDistance = distance;
            }
        }
        
        // If no units in range, look for enemy buildings
        if (!closestTarget) {
            const enemyBuildings = buildings.filter(b => b.team !== this.team && b.hp > 0);
            for (const building of enemyBuildings) {
                const distance = this.getDistance(building);
                if (distance <= this.type.range && distance < closestDistance) {
                    closestTarget = building;
                    closestDistance = distance;
                }
            }
        }
        
        this.target = closestTarget;
    }

    getDistance(target) {
        if (!target) return Infinity;
        const dx = target.x - this.x;
        const dy = target.y - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    showStateCaption(simulation) { // Renamed gameContext
        const { entityManager, seedRandom } = simulation;
        if (this.captionCooldown <= 0 && seedRandom.random() < 0.01) {
            // ... (caption logic) ...
            // entityManager.addCaption(new Caption(...));
            this.captionCooldown = 120 * (1/60); // Approx 2 seconds
        }
    }

    attack(target, simulation) { // Renamed gameContext
        const { entityManager, gameState } = simulation;
        let damage = this.type.damage;

        // Note: The old HP shield logic (target.shields) is now superseded by energy shields in takeDamage.
        // If both shield types are meant to co-exist, the logic in takeDamage would need to be more complex.
        // For now, takeDamage will prioritize energy shields.

        if (damage > 0 && target.hp > 0) { // Ensure target is alive before attempting to deal damage
             target.takeDamage(damage, this, simulation); // Pass attacker (this) as sourceUnit
        }

        entityManager.addEffect(new Effect(this.x, this.y, target.x, target.y, this.type.effectColor));

        if (target.type && target.type === UNIT_TYPES.commander && simulation.seedRandom.random() < 0.1) {
            gameState.addEvent('battle', `${this.team.toUpperCase()} attacking enemy Commander!`, 3, { x: target.x, y: target.y });
        } else if (simulation.seedRandom.random() < 0.01 && target.type && target.type.tier >= 2) {
            gameState.addEvent('battle', `Major engagement: ${this.type.name} vs ${target.type.name}`, 2, { x: this.x, y: this.y });
        }

        // Record damage and experience (as per implementation-guide.md Section 3.2)
        // Note: The guide mentions `damage` as the variable, but existing code seems to use `actualDamage`
        // if we consider the damage after reductions. For simplicity with the guide, using `damage` parameter.
        // If `actualDamage` (damage after shield/armor) is intended, this needs adjustment.
        // The original `this.progression.recordDamage(damage)` is kept as it might serve a different purpose.
        // This new logic is specifically for `damageDelt` and `combatExperience`.

        if (damage > 0) { // Assuming 'damage' is the intended variable from the guide for raw damage output by this unit
            this.combatExperience += 1;
            this.damageDelt += damage; // Use the 'damage' parameter from the attack method
            this.progression.recordDamage(damage); // Keeping original call, might be for other progression aspects
        }
        
        // Record kill (as per implementation-guide.md Section 3.2)
        if (target.hp <= 0) {
            this.killCount += 1;
            // Bonus experience for high-value targets
            if (target.type && target.type.id === 'acu') { // Specific check for ACU commander type
                this.combatExperience += 10;
            } else if (target.type && target.type.tier && target.type.tier >= 2) {
                this.combatExperience += 3;
            }
            this.progression.recordKill(target); // Keeping original call
        }
    }

    takeDamage(damage, sourceUnit, simulation, damageType = null) { // Added damageType parameter
        const { entityManager, seedRandom } = simulation; // simulation is the new gameContext
        let remainingDamage = damage;

        // New Energy Shield Logic
        if (this.currentEnergyShields > 0 && sourceUnit && sourceUnit.type) {
            const attackerWeaponEnergyCost = sourceUnit.type.weaponEnergyCost || SHIELD_EFFECTIVENESS_WATTAGE_BASELINE;
            let shieldEffectiveness = attackerWeaponEnergyCost / SHIELD_EFFECTIVENESS_WATTAGE_BASELINE;
            shieldEffectiveness = Math.max(0.1, shieldEffectiveness);

            const damageToDealToShields = remainingDamage * shieldEffectiveness;

            if (damageToDealToShields >= this.currentEnergyShields) {
                remainingDamage -= this.currentEnergyShields / shieldEffectiveness;
                this.currentEnergyShields = 0;
            } else {
                this.currentEnergyShields -= damageToDealToShields;
                remainingDamage = 0;
            }
        } else if (this.currentEnergyShields > 0) {
            // Shields exist, but no sourceUnit info or sourceUnit has no type (e.g. environmental damage)
            // Apply damage to shields with 1x effectiveness
            const damageToDealToShields = remainingDamage; // 1x effectiveness
            if (damageToDealToShields >= this.currentEnergyShields) {
                remainingDamage -= this.currentEnergyShields;
                this.currentEnergyShields = 0;
            } else {
                this.currentEnergyShields -= damageToDealToShields;
                remainingDamage = 0;
            }
        }

        // Old HP Shield Logic (if it's still intended to be a separate mechanic)
        // If energy shields and HP shields are separate pools, this could come after energy shields.
        // For now, let's assume energy shields are the primary shield mechanic being implemented.
        // If this.shields refers to the old HP shield:
        if (remainingDamage > 0 && this.shields > 0) { // this.shields is the old HP-like shield
            const damageToOldShield = Math.min(remainingDamage, this.shields);
            this.shields -= damageToOldShield;
            remainingDamage -= damageToOldShield;
        }


        // Apply any remaining damage to HP
        if (remainingDamage > 0) {
            this.hp -= remainingDamage;
        }

        if (this.hp <= 0) {
            this.hp = 0;
            this.isDead = true; // Flag for removal by EntityManager or simulation loop
            // Actual removal and explosion effects should be handled by EntityManager or main simulation loop
            // to avoid self-removal issues during iteration.
            // Example: simulation.entityManager.addEffect(new Effect(this.x, this.y, 'explosion_medium', simulation));
            // Example: simulation.gameState.addEvent('death', `${this.type.name} destroyed!`);

        // If damageType was passed and is relevant for shield interaction here, it could be used.
        // For now, existing shield logic based on weaponEnergyCost is maintained.
        // A future refactor might centralize shield damage calculation using damageType in ShieldSystem.
        }


        // Visual feedback for damage
        if (damage > 0 && seedRandom && seedRandom.random() < 0.2) { // ensure seedRandom exists
            if (this.hp <= 0) {
                // Death caption/effect handled elsewhere or by specific death event
            } else if (this.hp < this.maxHp * 0.3) {
                if (entityManager) entityManager.addCaption(new Caption(this.x, this.y, 'Critical damage!', '#f00', 10));
            } else if (damage > 30) { // Only show big damage numbers
                if (entityManager) entityManager.addCaption(new Caption(this.x, this.y, `${Math.floor(damage)}!`, '#f88', 9));
            }
        }
    }

    // draw method does not use gameContext/simulation for sim logic, only for camera/canvas info. No change needed.
    // draw(ctx, camera, gameContext) { ... }


    handleCombatPositioning(distToTarget, simulation) { // Renamed gameContext
        const inRange = distToTarget <= this.type.range;
        const tooClose = distToTarget < this.preferredRange * 0.5;
        
        if (tooClose && this.tacticalRole === 'sniper') {
            this.angle = Math.atan2(this.y - this.target.y, this.x - this.target.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed * 0.5;
            this.vy = Math.sin(this.angle) * currentSpeed * 0.5;
        } else if (!inRange) {
            this.angle = Math.atan2(this.target.y - this.y, this.target.x - this.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed;
            this.vy = Math.sin(this.angle) * currentSpeed;
        } else { // In range
            this.vx = 0;
            this.vy = 0;
            if (this.cooldown <= 0 && this.target && this.target.hp > 0) { // Check target validity again
                const energyCost = this.type.weaponEnergyCost || 0;
                if (this.currentEnergy >= energyCost) {
                    this.attack(this.target, simulation); // Actual attack call
                    this.currentEnergy -= energyCost;     // Consume energy
                    this.cooldown = this.type.attackSpeed || 1.0; // Reset cooldown (attackSpeed is likely 'fireRate' or similar)
                    this.lastFireTime = Date.now();
                } else {
                    // Insufficient energy, cannot fire. Cooldown does not reset.
                    // Optional: log low energy: console.log(`Unit ${this.id} low energy for weapon.`);
                }
            }
        }
    }

    updateTacticalBehavior(simulation) { /* Renamed gameContext */ this.executeAgglomeration(simulation); this.executeGroupMovement(simulation); this.executeUnitInteractions(simulation); this.executeRepositioning(simulation); }
    executeAgglomeration(simulation) {
        const units = simulation.entityManager.units;
        if (!units || !Array.isArray(units)) return;
        const teamUnits = units.filter(u => u.team === this.team);
        
        if (teamUnits.length <= 1) return;
        
        // Calculate center of mass for team units
        let centerX = 0, centerY = 0;
        for (const unit of teamUnits) {
            centerX += unit.x;
            centerY += unit.y;
        }
        centerX /= teamUnits.length;
        centerY /= teamUnits.length;
        
        const distToCenter = Math.sqrt((this.x - centerX) ** 2 + (this.y - centerY) ** 2);
        
        if (!this.target && !this.isEscaping && distToCenter > 100) {
            this.angle = Math.atan2(centerY - this.y, centerX - this.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed * 0.3; // Slower movement toward group
            this.vy = Math.sin(this.angle) * currentSpeed * 0.3;
        }
    }
    executeGroupMovement(simulation) {
        const units = simulation.entityManager.units;
        if (!units || !Array.isArray(units)) return;
        const teamUnits = units.filter(u => u.team === this.team);
        
        if (teamUnits.length <= 1) return;
        
        // Find group leader (highest authority unit in vicinity)
        const nearbyUnits = teamUnits.filter(u => {
            const dist = Math.sqrt((u.x - this.x) ** 2 + (u.y - this.y) ** 2);
            return dist < 200 && u !== this;
        });
        
        const groupLeader = nearbyUnits.reduce((leader, unit) => {
            return (!leader || unit.commandAuthority > leader.commandAuthority) ? unit : leader;
        }, null);
        
        if (groupLeader && groupLeader !== this) {
            const leaderDist = Math.sqrt((groupLeader.x - this.x) ** 2 + (groupLeader.y - this.y) ** 2);
            const currentSpeed = this.getCurrentSpeed(simulation);
            
            if (leaderDist > 80) {
                // Move towards leader if too far
                this.angle = Math.atan2(groupLeader.y - this.y, groupLeader.x - this.x);
                this.vx = Math.cos(this.angle) * currentSpeed * 0.8;
                this.vy = Math.sin(this.angle) * currentSpeed * 0.8;
            } else if (leaderDist < 60 && leaderDist > 20) {
                // Maintain formation spacing
                const spreadAngle = this.angle + (simulation.seedRandom.random() - 0.5) * Math.PI / 2;
                this.vx = Math.cos(spreadAngle) * currentSpeed * 0.4;
                this.vy = Math.sin(spreadAngle) * currentSpeed * 0.4;
            }
        }
    }
    executeUnitInteractions(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeRepositioning(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    updateSurvivalBehaviors(simulation) { /* Renamed gameContext */ if (Date.now() - this.lastThreatAssessment > 2000) { this.assessThreats(simulation); this.lastThreatAssessment = Date.now(); } if (this.hp < this.fleeThreshold && !this.isEscaping) { this.executeTacticalRetreat(simulation); } if (this.type.support && this.shields < this.maxShields * 0.5) { this.seekProtection(simulation); } }
    assessThreats(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeTacticalRetreat(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    seekProtection(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeCommandHierarchy(simulation) { /* Renamed gameContext */ if (this.militaryRank === 'GENERAL' || this.militaryRank === 'COLONEL') { this.issueStrategicOrders(simulation); } if (this.commandAuthority < 50) { this.followSuperiorOrders(simulation); } }
    issueStrategicOrders(simulation) {
        const units = simulation.entityManager.units;
        if (!units || !Array.isArray(units)) return;
        const teamUnits = units.filter(u => u.team === this.team && u !== this);
        
        // Find subordinates (units with lower command authority within range)
        const subordinates = teamUnits.filter(unit => {
            const distance = Math.sqrt((unit.x - this.x) ** 2 + (unit.y - this.y) ** 2);
            return distance < 300 && unit.commandAuthority < this.commandAuthority;
        });
        
        for (const sub of subordinates) {
            if (sub.hp < sub.maxHp * 0.3 && !sub.isEscaping) {
                sub.protectionNeeds.push('COMMANDER_RETREAT_ORDER');
                sub.executeTacticalRetreat(simulation);
            }
            
            // Issue target assignments to subordinates without targets
            if (!sub.target && this.target && sub.commandAuthority < this.commandAuthority * 0.8) {
                sub.target = this.target;
            }
        }
    }

    calculateSeparationForce(gameContext) {
        let totalSeparationForce = { x: 0, y: 0 };
        const { units } = gameContext.entityManager;
        // Define the radius within which separation from other units is checked.
        const separationRadius = (this.type.size || 10) * COMMAND_CONFIG.NEIGHBOR_RADIUS_FACTOR;

        units.forEach(otherUnit => {
            if (otherUnit === this) return; // Don't compare with self

            const dist = this.getDistance(otherUnit);
            // If the other unit is within the separation radius (but not overlapping exactly at 0 distance)
            if (dist > 0 && dist < separationRadius) {
                // Calculate a force vector pointing away from the neighbor.
                let forceX = this.x - otherUnit.x;
                let forceY = this.y - otherUnit.y;

                // Normalize the force vector and then scale it.
                // The scaling makes the force stronger for closer units (inverse square like or similar).
                // Here, it's scaled by (separationRadius / dist) to make it stronger than linear falloff.
                forceX = (forceX / dist) * (separationRadius / dist);
                forceY = (forceY / dist) * (separationRadius / dist);

                totalSeparationForce.x += forceX;
                totalSeparationForce.y += forceY;
            }
        });
        return totalSeparationForce;
    }

    calculateTerrainAvoidanceForce(gameContext) {
        let totalAvoidanceForce = { x: 0, y: 0 };
        // Feeler length is based on unit size and a configuration factor.
        const feelerLength = (this.type.size || 10) * COMMAND_CONFIG.STEERING_FEELER_LENGTH_FACTOR;

        // Project a feeler straight ahead based on the unit's current orientation (angle).
        const feelerEndX = this.x + Math.cos(this.angle) * feelerLength;
        const feelerEndY = this.y + Math.sin(this.angle) * feelerLength;

        // Convert the feeler's endpoint to grid coordinates to check terrain.
        const gridX = Math.floor(feelerEndX / TILE_SIZE);
        const gridY = Math.floor(feelerEndY / TILE_SIZE);

        // gameContext is the simulation object. isTraversable expects gameContext.gameContext (which holds terrain).
        if (!isTraversable(gridX, gridY, gameContext.gameContext, this.type.movementType)) {
            // Obstacle detected at the feeler's endpoint.
            // Calculate a force to steer the unit away from this point.
            // This simple version pushes the unit directly away from the detected obstacle point.
            let avoidanceX = this.x - feelerEndX; // Vector from obstacle point towards the unit
            let avoidanceY = this.y - feelerEndY;
            const distToObstaclePoint = Math.sqrt(avoidanceX * avoidanceX + avoidanceY * avoidanceY);

            if (distToObstaclePoint > 0) {
                // Normalize the repulsion vector and scale it by maxForce to make it a strong corrective action.
                totalAvoidanceForce.x = (avoidanceX / distToObstaclePoint) * this.maxForce;
                totalAvoidanceForce.y = (avoidanceY / distToObstaclePoint) * this.maxForce;
            }
        }
        // TODO: Implement more sophisticated feeler arrangements (e.g., side feelers) for better obstacle negotiation.
        return totalAvoidanceForce;
    }

    followSuperiorOrders(gameContext) { // Renamed simulation to gameContext for consistency with guide
        const { units } = gameContext.entityManager;

        let commander = null;
        let highestEffectiveAuthority = 0;

        for (const ally of units) {
            if (ally.team === this.team &&
                ally !== this &&
                this.getDistance(ally) < COMMAND_CONFIG.COMMAND_RANGES.TACTICAL) {

                ally.calculateEffectiveAuthority(); // Ensure ally's authority is up-to-date

                if (ally.effectiveAuthority > this.effectiveAuthority && // Must have higher authority than current unit
                    ally.effectiveAuthority > highestEffectiveAuthority && // Must be the highest found so far
                    ally.commandFitness === 'FULL_COMMAND') { // Commander must be fit

                    highestEffectiveAuthority = ally.effectiveAuthority;
                    commander = ally;
                }
            }
        }

        if (commander) {
            this.currentCommander = commander;

            // Follow commander's directives for targeting
            if (commander.target && !this.target &&
                this.getDistance(commander.target) < (this.type.range || 100) * 2) { // type.range might not exist for all, so default
                this.target = commander.target;
            }

            // Maintain formation distance based on effective authority difference
            const authorityGap = commander.effectiveAuthority - this.effectiveAuthority;
            const formationDistance = Math.max(
                COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MIN_DISTANCE,
                Math.min(COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MAX_DISTANCE, COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MIN_DISTANCE + authorityGap * 2)
            );

            const distToCommander = this.getDistance(commander);
            if (distToCommander > formationDistance + 50 && !this.target) { // If too far and not engaging enemy
                this.patrolTarget = { x: commander.x, y: commander.y }; // Simple follow
            }
        } else {
            this.currentCommander = null; // No suitable commander found or in range
        }
    }

    executeGroupMovement(gameContext) { // gameContext is the simulation object
        const { units } = gameContext.entityManager;

        // --- Leader Selection Logic ---
        let groupLeader = null;
        // A unit initially considers its own effective authority as the baseline.
        let highestEffectiveAuthority = this.effectiveAuthority;
        // However, if the unit itself is not in 'FULL_COMMAND', it should prefer a fit leader.
        if (this.commandFitness !== 'FULL_COMMAND') {
             highestEffectiveAuthority = -1; // Prioritize finding any fit leader if this unit is compromised.
        }

        for (const ally of units) {
            if (ally.team === this.team) {
                 if (this.getDistance(ally) < COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC) { // Check units within strategic range
                    ally.calculateEffectiveAuthority();
                    if (ally.effectiveAuthority > highestEffectiveAuthority &&
                        ally.commandFitness === 'FULL_COMMAND') {
                        highestEffectiveAuthority = ally.effectiveAuthority;
                        groupLeader = ally;
                    }
                }
            }
        }
        // If this unit is the most suitable leader found (or no one better was found and it's fit),
        // it effectively becomes the leader of its own "group" of one, or the actual group leader.
        if (groupLeader === this) {
            groupLeader = null; // It doesn't "follow" itself in the context of group movement adjustments.
        }

        // Reset steering forces for the current frame.
        this.steering.x = 0;
        this.steering.y = 0;

        // --- Behavior based on whether a leader is identified ---
        if (this === groupLeader || !groupLeader) {
            // This unit is acting as the LEADER or is an independent unit (no group leader found).
            // Update its own target position based on its current path or patrol target.
            // This is for potential observation by other units or future leader-specific behaviors.
            if (this.path && this.path[this.currentWaypointIndex]) {
                this.leaderTargetPosition = this.path[this.currentWaypointIndex];
            } else if (this.patrolTarget) {
                this.leaderTargetPosition = this.patrolTarget;
            } else {
                this.leaderTargetPosition = null;
            }
            // Clear any follower-specific properties if it was previously a follower.
            this.leaderPredictedPosition = null;
            this.idealFormationSlotWorld = null;
            // The leader's primary movement is driven by defaultMovementAndTargeting (handling A* pathing via patrolTarget).
            // No additional steering forces are typically applied here for the leader itself unless
            // group cohesion forces (not yet implemented) were to influence the leader too.

        } else { // This unit is a FOLLOWER.
            // Followers prioritize formation movement over individual A* pathing or patrol targets.
            this.patrolTarget = null;
            this.path = null;

            // --- Regrouping Behavior: Check for excessive separation from the leader ---
            const distanceToLeader = this.getDistance(groupLeader);
            const maxSeparationDistance = COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC * COMMAND_CONFIG.FORMATION_RULES.MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR;

            if (distanceToLeader > maxSeparationDistance) {
                // Unit is too far; override formation steering and pathfind directly to the leader.
                this.path = findPath({ x: this.x, y: this.y }, { x: groupLeader.x, y: groupLeader.y }, gameContext.gameContext, this.type.movementType);
                this.currentWaypointIndex = 0;
                // Set patrolTarget to leader's current position to engage A* path following via defaultMovementAndTargeting.
                this.patrolTarget = { x: groupLeader.x, y: groupLeader.y };

                this.idealFormationSlotWorld = null; // Clear formation-specific targets
                this.leaderPredictedPosition = null;
                this.steering = { x: 0, y: 0 };      // Reset any accumulated steering forces
                return; // Skip formation steering for this tick; defaultMovementAndTargeting will handle the path.
            }

            // --- Leader Prediction ---
            // Predict the leader's future position based on its current velocity.
            const predictionTime = COMMAND_CONFIG.FORMATION_BEHAVIOR.PREDICTION_TIME_SECONDS;
            this.leaderPredictedPosition = {
                x: groupLeader.x + groupLeader.vx * predictionTime,
                y: groupLeader.y + groupLeader.vy * predictionTime
            };

            // --- Ideal Formation Slot Calculation ---
            // Calculate the follower's ideal slot in the world based on leader's predicted position and unit's formationAngle.
            const baseFormationDistance = COMMAND_CONFIG.FORMATION_BEHAVIOR.MIN_FORMATION_SLOT_DISTANCE;
            // Note: this.formationOffset (a more specific {x,y,angle} offset) is available for future, more complex formations.
            const offsetX = Math.cos(this.formationAngle || 0) * baseFormationDistance;
            const offsetY = Math.sin(this.formationAngle || 0) * baseFormationDistance;

            this.idealFormationSlotWorld = {
                x: this.leaderPredictedPosition.x + offsetX,
                y: this.leaderPredictedPosition.y + offsetY
            };

            // --- Calculate Steering Forces ---
            // 1. Seek/Arrive Force towards idealFormationSlotWorld
            let desiredVelocityX = this.idealFormationSlotWorld.x - this.x;
            let desiredVelocityY = this.idealFormationSlotWorld.y - this.y;
            const distToSlot = Math.sqrt(desiredVelocityX * desiredVelocityX + desiredVelocityY * desiredVelocityY);

            const currentActualSpeed = this.getCurrentSpeed(gameContext);
            // Define an arrival radius where the unit starts to slow down or stop seeking.
            const arrivalRadius = (this.type.size || 10) * COMMAND_CONFIG.FORMATION_BEHAVIOR.ARRIVAL_RADIUS_FACTOR;

            if (distToSlot > arrivalRadius) { // Only apply seek/arrive if further than arrival radius.
                // Normalize the desired velocity vector.
                desiredVelocityX /= distToSlot;
                desiredVelocityY /= distToSlot;

                // Arrival Dampening: Scale speed based on distance to target.
                // This is a simplified "arrive" behavior. A more sophisticated one might use a separate slowingRadius.
                const slowingRadius = (this.type.size || 10) * 2; // Example: Start slowing when within 2x unit size.
                if (distToSlot < slowingRadius) { // If inside slowing radius, scale speed down.
                    desiredVelocityX *= currentActualSpeed * (distToSlot / slowingRadius);
                    desiredVelocityY *= currentActualSpeed * (distToSlot / slowingRadius);
                } else { // Otherwise, aim for full current speed.
                    desiredVelocityX *= currentActualSpeed;
                    desiredVelocityY *= currentActualSpeed;
                }
            } else { // Within arrival radius: effectively stop seeking the slot.
                desiredVelocityX = 0;
                desiredVelocityY = 0;
            }

            // Calculate the steering force for seeking/arriving.
            let seekForceX = desiredVelocityX - this.vx;
            let seekForceY = desiredVelocityY - this.vy;
            this.steering.x += seekForceX;
            this.steering.y += seekForceY;

            // 2. Separation Force: Steer away from nearby units.
            const separationForce = this.calculateSeparationForce(gameContext);
            this.steering.x += separationForce.x * COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;
            this.steering.y += separationForce.y * COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;

            // 3. Terrain Avoidance Force: Steer away from detected terrain obstacles.
            const terrainAvoidanceForce = this.calculateTerrainAvoidanceForce(gameContext);
            this.steering.x += terrainAvoidanceForce.x * COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;
            this.steering.y += terrainAvoidanceForce.y * COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;

            // Conditional logging for debugging formation behavior of this unit.
            if (this.debugFormation && groupLeader) {
                console.log(`Unit ${this.id || this.type.name} (Follower) of Leader ${groupLeader.id || groupLeader.type.name}:
                  IdealSlot: ${this.idealFormationSlotWorld ? `${this.idealFormationSlotWorld.x.toFixed(1)},${this.idealFormationSlotWorld.y.toFixed(1)}` : 'null'}
                  PredictedLeader: ${this.leaderPredictedPosition ? `${this.leaderPredictedPosition.x.toFixed(1)},${this.leaderPredictedPosition.y.toFixed(1)}` : 'null'}
                  Steering (pre-apply): x:${this.steering.x.toFixed(2)}, y:${this.steering.y.toFixed(2)}`);
            }

            // --- Apply Combined Steering Forces ---
            // Truncate the total steering force to the unit's maximum steering force.
            const steerMag = Math.sqrt(this.steering.x * this.steering.x + this.steering.y * this.steering.y);
            if (steerMag > this.maxForce) {
                this.steering.x = (this.steering.x / steerMag) * this.maxForce;
                this.steering.y = (this.steering.y / steerMag) * this.maxForce;
            }

            // Apply the steering force to the unit's velocity.
            this.vx += this.steering.x;
            this.vy += this.steering.y;

            // Truncate the final velocity to the unit's maximum speed.
            const velMag = Math.sqrt(this.vx * this.vx + this.vy * this.vy);
            if (velMag > currentActualSpeed) { // currentActualSpeed already considers terrain & weight
                this.vx = (this.vx / velMag) * currentActualSpeed;
                this.vy = (this.vy / velMag) * currentActualSpeed;
            }

            // Update unit's orientation (angle) based on its new velocity (if moving).
            if (Math.abs(this.vx) > 0.01 || Math.abs(this.vy) > 0.01) { // Threshold to prevent jitter when near stationary
                let targetAngle = Math.atan2(this.vy, this.vx);
                let angleDiff = targetAngle - this.angle;
                // Normalize angle difference to the range [-PI, PI] for shortest turn.
                while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;

                // Apply turn, capped by maxTurnRate.
                const turnThisFrame = Math.min(Math.abs(angleDiff), this.maxTurnRate);
                this.angle += (angleDiff > 0 ? turnThisFrame : -turnThisFrame);

                // Normalize the unit's angle to keep it within [-PI, PI].
                while (this.angle > Math.PI) this.angle -= 2 * Math.PI;
                while (this.angle < -Math.PI) this.angle += 2 * Math.PI;
            }
            // Conditional logging for final velocity and angle.
            if (this.debugFormation) {
                console.log(`Unit ${this.id || this.type.name} (Follower) - Final V: x:${this.vx.toFixed(2)}, y:${this.vy.toFixed(2)}, Angle: ${this.angle.toFixed(2)}`);
            }
        }
    }

    updateStuckDetection(simulation) { // Renamed gameContext
        const dxMoved = this.x - this.lastPositionForStuckCheck.x;
        const dyMoved = this.y - this.lastPositionForStuckCheck.y;
        const distanceMoved = Math.sqrt(dxMoved * dxMoved + dyMoved * dyMoved);

        const wasTryingToMove = (this.target || this.patrolTarget || this.isEscaping || this.vx !== 0 || this.vy !== 0);

        if (wasTryingToMove && distanceMoved < this.significantMoveThreshold) {
            this.stuckFrames++;
        } else {
            this.stuckFrames = 0; // Reset if moved significantly or wasn't trying to move
        }

        this.lastPositionForStuckCheck = { x: this.x, y: this.y };

        if (this.stuckFrames > this.STUCK_FRAMES_THRESHOLD && !this.isEscaping) {
            this.isEscaping = true;
            this.escapeAngle = this.angle + (simulation.seedRandom.random() < 0.5 ? Math.PI / 2 : -Math.PI / 2);
            this.escapeDuration = this.ESCAPE_MODE_DURATION_FRAMES; // This should be time (seconds) not frames
        }
    }

    determineTacticalRole() {
        // Determine tactical role based on unit type and characteristics
        if (this.type.range > 150) {
            return 'sniper';
        } else if (this.type.speed > 60) {
            return 'scout';
        } else if (this.type.maxHp > 200) {
            return 'tank';
        } else if (this.type.support) {
            return 'support';
        } else {
            return 'assault';
        }
    }

    determineMilitaryRank() {
        // Determine military rank based on unit type and tier
        if (this.type === UNIT_TYPES.commander) {
            return 'GENERAL';
        } else if (this.type.tier >= 3) {
            return 'COLONEL';
        } else if (this.type.tier >= 2) {
            return 'MAJOR';
        } else if (this.type.support) {
            return 'LIEUTENANT';
        } else {
            return 'SERGEANT';
        }
    }

    calculateSurvivalPriority() {
        // Calculate survival priority based on unit importance
        let priority = this.type.tier * 10;
        
        if (this.type === UNIT_TYPES.commander) {
            priority += 50;
        }
        
        if (this.type.support) {
            priority += 15;
        }
        
        if (this.type.range > 150) {
            priority += 10; // Snipers are valuable
        }
        
        return priority;
    }

    /**
     * Change the Computronium Core Focus Mode
     * @param {string} newMode - New focus mode (OFFENSIVE, DEFENSIVE, C_C, UTILITY, BALANCED)
     * @returns {boolean} True if mode was changed successfully
     */
    changeCoreFocusMode(newMode) {
        const now = performance.now();
        if (now - this.lastFocusModeChange < this.focusModeCooldown) {
            return false;
        }

        if (!this.focusModeEffects[newMode]) {
            return false;
        }

        this.coreFocusMode = newMode;
        this.lastFocusModeChange = now;
        this.updateComputroniumModifiers();
        return true;
    }

    /**
     * Update Computronium-based modifiers based on core level and focus mode
     */
    updateComputroniumModifiers() {
        if (this.computroniumCoreLevel === 0) {
            this.computroniumAuthorityModifier = 0;
            return;
        }

        const effects = this.focusModeEffects[this.coreFocusMode];
        const baseModifier = this.computroniumCoreLevel * 2; // Base modifier from core level

        // Calculate authority modifier based on focus mode
        if (this.coreFocusMode === 'C_C') {
            this.computroniumAuthorityModifier = baseModifier * effects.c2Efficiency;
        } else {
            this.computroniumAuthorityModifier = baseModifier * 0.5; // Reduced authority for non-C&C modes
        }

        // Apply focus mode effects to other systems
        this.weaponEfficiency = effects.weaponEfficiency;
        this.c2Efficiency = effects.c2Efficiency;
        this.powEfficiency = effects.powEfficiency;
        this.sensorEfficiency = effects.sensorEfficiency;
    }

    /**
     * Calculate base authority
     * @returns {number} Base authority value
     */
    calculateBaseAuthority() {
        // Base authority based on unit type
        switch (this.type) {
            case 'commander':
                return 30;
            case 'elite':
                return 20;
            case 'veteran':
                return 15;
            case 'regular':
                return 10;
            default:
                return 5;
        }
    }

    calculateEffectiveAuthority() {
        const healthRatio = this.hp / this.maxHp;

        // Health bias calculation
        if (healthRatio >= COMMAND_CONFIG.HEALTH_THRESHOLDS.FULL_COMMAND) {
            this.healthAuthorityModifier = 5; // Per guide, direct value, not COMMAND_CONFIG.AUTHORITY_WEIGHTS.HEALTH_MAX_MODIFIER
            this.commandFitness = 'FULL_COMMAND';
        } else if (healthRatio >= COMMAND_CONFIG.HEALTH_THRESHOLDS.REDUCED_AUTHORITY) {
            this.healthAuthorityModifier = 2;
            this.commandFitness = 'REDUCED_AUTHORITY';
        } else if (healthRatio >= COMMAND_CONFIG.HEALTH_THRESHOLDS.COMPROMISED_COMMAND) {
            this.healthAuthorityModifier = -2;
            this.commandFitness = 'COMPROMISED_COMMAND';
        } else if (healthRatio >= COMMAND_CONFIG.HEALTH_THRESHOLDS.CRITICAL_STATUS) {
            this.healthAuthorityModifier = -5;
            this.commandFitness = 'CRITICAL_STATUS';
        } else { // Effectively healthRatio < COMMAND_CONFIG.HEALTH_THRESHOLDS.CRITICAL_STATUS
            this.healthAuthorityModifier = -10;
            this.commandFitness = 'COMBAT_INEFFECTIVE';
        }

        // Veterancy bias calculation
        const experiencePoints = this.combatExperience +
                               (this.survivalTime / 60) +
                               (this.commandExperience * 3) +
                               (this.killCount * 2);

        if (experiencePoints >= COMMAND_CONFIG.VETERANCY_THRESHOLDS.HERO) {
            this.veterancyLevel = 'HERO';
            this.veterancyAuthorityModifier = 15; // Per guide, direct value, not COMMAND_CONFIG.AUTHORITY_WEIGHTS.VETERANCY_MAX_MODIFIER
        } else if (experiencePoints >= COMMAND_CONFIG.VETERANCY_THRESHOLDS.ELITE) {
            this.veterancyLevel = 'ELITE';
            this.veterancyAuthorityModifier = 10;
        } else if (experiencePoints >= COMMAND_CONFIG.VETERANCY_THRESHOLDS.VETERAN) {
            this.veterancyLevel = 'VETERAN';
            this.veterancyAuthorityModifier = 5;
        } else if (experiencePoints >= COMMAND_CONFIG.VETERANCY_THRESHOLDS.REGULAR) {
            this.veterancyLevel = 'REGULAR';
            this.veterancyAuthorityModifier = 2;
        } else {
            this.veterancyLevel = 'GREEN';
            this.veterancyAuthorityModifier = 0;
        }

        // Placeholder for Computronium-based C&C modifier calculation
        this.computroniumAuthorityModifier = 0; // Placeholder for Computronium-based C&C modifier

        // Calculate final effective authority
        this.effectiveAuthority = this.baseAuthority +
                                this.healthAuthorityModifier +
                                this.veterancyAuthorityModifier +
                                this.contextAuthorityModifier +
                                this.computroniumAuthorityModifier;

        return this.effectiveAuthority;
    }

    /**
     * Get veterancy level
     * @returns {string} Veterancy level
     */
    getVeterancyLevel() {
        return this.progression.veterancyLevel;
    }

    /**
     * Get command fitness
     * @returns {string} Command fitness level
     */
    getCommandFitness() {
        return this.progression.commandFitness;
    }

    /**
     * Get effective authority
     * @returns {number} Effective authority value
     */
    getEffectiveAuthority() {
        return this.effectiveAuthority;
    }

    /**
     * Record successful command
     */
    recordCommandSuccess() {
        this.progression.recordCommandSuccess();
    }

    /**
     * Record failed command
     */
    recordCommandFailure() {
        this.progression.recordCommandFailure();
    }

    /**
     * Set promotion callback
     * @param {Function} callback - Callback function
     */
    setPromotionCallback(callback) {
        this.onPromotion = callback;
    }

    /**
     * Initialize autonomous behavior
     * @param {Object} gameContext - Game context object
     */
    initializeAutonomousBehavior(gameContext) {
        this.autonomousBehavior = new AutonomousBehavior(this, gameContext);
    }

    /**
     * Check if unit has explicit orders
     * @returns {boolean} True if unit has explicit orders
     */
    hasExplicitOrders() {
        return this.hasExplicitOrders;
    }

    /**
     * Set explicit orders flag
     * @param {boolean} value - New value for explicit orders flag
     */
    setExplicitOrders(value) {
        this.hasExplicitOrders = value;
    }

    /**
     * Move unit to position
     * @param {number} x - Target x coordinate
     * @param {number} y - Target y coordinate
     */
    moveTo(x, y) {
        this.hasExplicitOrders = true;
        // ... existing moveTo code ...
    }

    /**
     * Attack target
     * @param {Object} target - Target to attack
     */
    attack(target) {
        this.hasExplicitOrders = true;
        // ... existing attack code ...
    }

    /**
     * Build extractor at position
     * @param {Object} position - Position to build extractor
     */
    buildExtractor(position) {
        this.hasExplicitOrders = true;
        // ... existing buildExtractor code ...
    }

    /**
     * Repair target
     * @param {Object} target - Target to repair
     */
    repair(target) {
        this.hasExplicitOrders = true;
        // ... existing repair code ...
    }

    // Placeholder method for command succession (Section 1.3 / 2.1)
    triggerCommandSuccession(gameContext) {
        const { units } = gameContext.entityManager; // Get units from entityManager

        // Find suitable replacement commander
        const nearbyAllies = units.filter(u =>
            u.team === this.team &&
            u !== this &&
            this.getDistance(u) < COMMAND_CONFIG.COMMAND_RANGES.TACTICAL &&
            u.commandFitness === 'FULL_COMMAND'
        );

        if (nearbyAllies.length === 0) return;

        // Select best replacement based on effective authority
        let bestReplacement = null;
        let highestAuthority = 0;

        for (const ally of nearbyAllies) {
            ally.calculateEffectiveAuthority(); // Ensure authority is up-to-date
            if (ally.effectiveAuthority > highestAuthority) {
                highestAuthority = ally.effectiveAuthority;
                bestReplacement = ally;
            }
        }

        if (bestReplacement && bestReplacement.effectiveAuthority > this.effectiveAuthority) {
            this.transferCommand(bestReplacement, gameContext);
        }
    }

    transferCommand(newCommander, gameContext) {
        const { units, addCaption } = gameContext.entityManager; // Get units and addCaption from entityManager
        const { gameState } = gameContext; // Get gameState for addEvent

        // Transfer subordinates
        const subordinates = units.filter(u =>
            u.team === this.team &&
            u.currentCommander === this
        );

        subordinates.forEach(subordinate => {
            subordinate.currentCommander = newCommander;
            subordinate.lastCommandChange = performance.now();
        });

        // Update command experience
        newCommander.commandExperience += subordinates.length;
        this.commandFailures += 1; // Failed to maintain command

        // Visual feedback
        // Caption class is imported at the top of the file
        addCaption(new Caption(
            newCommander.x, newCommander.y,
            `Command transferred to ${newCommander.veterancyLevel} ${newCommander.type.name}`,
            '#ff4', 14
        ));

        // Log succession event
        if (gameState && typeof gameState.addEvent === 'function') {
            gameState.addEvent('command_succession',
                `${this.team} command transferred due to combat ineffectiveness`, 2);
        }
    }

    // Placeholder method for veterancy progress (Section 1.3 / 3.1)
    updateVeterancyProgress(gameContext) {
        const deltaTime = gameContext.gameSpeedManager ? (gameContext.gameSpeedManager.deltaTime || (1/60)) : (1/60);

        // Check if the unit is in combat zones
        // this.isUnderFire(gameContext) is not yet defined, will be falsy
        const inCombat = this.target || this.hp < this.maxHp || (typeof this.isUnderFire === 'function' && this.isUnderFire(gameContext));

        if (inCombat) {
            this.survivalTime += deltaTime;
        }

        // Check for promotion eligibility
        const oldLevel = this.veterancyLevel;
        this.calculateEffectiveAuthority(); // This method updates veterancyLevel based on experience points

        if (oldLevel !== this.veterancyLevel) {
            this.processPromotion(oldLevel, gameContext);
        }
    }

    processPromotion(oldLevel, gameContext) {
<<<<<<< HEAD
        const newLevel = this.getVeterancyLevel();
        if (newLevel !== oldLevel) {
            console.log(`[Unit] ${this.type.name} promoted from ${oldLevel} to ${newLevel}`);
            
            // Apply veterancy benefits based on new level
            this.applyVeterancyBenefits();
            
            // Update command authority
            this.updateVeterancyAuthorityModifier();
            
            // Trigger promotion callback if set
            if (this.onPromotion) {
                this.onPromotion(this, oldLevel, newLevel);
            }
            
            // Update last promotion time
            this.lastPromotionTime = Date.now();
            
            // Show promotion effect
            if (gameContext) {
                const effect = new Effect(this.x, this.y, 'promotion', gameContext);
                gameContext.entityManager.addEffect(effect);
                
                // Show promotion caption
                const caption = new Caption(
                    this.x,
                    this.y - 30,
                    `Promoted to ${newLevel}!`,
                    '#FFD700',
                    2000,
                    gameContext
                );
                gameContext.entityManager.addCaption(caption);
            }
=======
        const { entityManager, gameState } = gameContext;
        const now = performance.now();

        // Prevent spam promotions
        if (now - this.lastPromotionTime < COMMAND_CONFIG.UPDATE_INTERVALS.PROMOTION_COOLDOWN) return;

        this.lastPromotionTime = now;

        // Apply veterancy benefits
        this.applyVeterancyBenefits();

        // Visual feedback (Caption class is imported at the top)
        if (entityManager && typeof entityManager.addCaption === 'function') {
            entityManager.addCaption(new Caption(
                this.x, this.y,
                `${this.type.name} promoted to ${this.veterancyLevel}!`,
                '#4f4', 16
            ));
        }

        // Strategic event
        if (gameState && typeof gameState.addEvent === 'function') {
            gameState.addEvent('promotion',
                `${this.team} ${this.type.name} promoted to ${this.veterancyLevel}`, 2);
>>>>>>> origin/jules_wip_12008771546559725757
        }
    }

    applyVeterancyBenefits() {
<<<<<<< HEAD
        const level = this.getVeterancyLevel();
        
        // Reset benefits
        this.canPromoteSubordinates = false;
        this.provideMoraleBonus = false;
        
        // Apply benefits based on veterancy level
        switch (level) {
            case 'REGULAR':
                // Basic stat improvements
                this.maxHp *= 1.1;
                this.hp = this.maxHp;
                this.speed *= 1.05;
                break;
                
            case 'VETERAN':
                // Enhanced combat capabilities
                this.maxHp *= 1.15;
                this.hp = this.maxHp;
                this.speed *= 1.1;
                this.armorValue *= 1.1;
                this.provideMoraleBonus = true; // Can provide morale bonus to nearby units
                break;
                
            case 'ELITE':
                // Advanced combat and command capabilities
                this.maxHp *= 1.2;
                this.hp = this.maxHp;
                this.speed *= 1.15;
                this.armorValue *= 1.2;
                this.provideMoraleBonus = true;
                this.canPromoteSubordinates = true; // Can promote subordinates
                break;
                
            case 'HERO':
                // Maximum capabilities
                this.maxHp *= 1.3;
                this.hp = this.maxHp;
                this.speed *= 1.2;
                this.armorValue *= 1.3;
                this.provideMoraleBonus = true;
                this.canPromoteSubordinates = true;
                // Additional hero benefits
                this.shieldRegenRate *= 1.5;
                this.coreEfficiency *= 1.2;
                break;
        }
        
        // Update flee threshold based on new maxHp
        this.fleeThreshold = this.maxHp * 0.2;
    }

    updateVeterancyAuthorityModifier() {
        const level = this.getVeterancyLevel();
        let modifier = 0;
        
        switch (level) {
            case 'REGULAR':
                modifier = 2;
                break;
            case 'VETERAN':
                modifier = 5;
                break;
            case 'ELITE':
                modifier = 10;
                break;
            case 'HERO':
                modifier = COMMAND_CONFIG.AUTHORITY_WEIGHTS.VETERANCY_MAX_MODIFIER;
                break;
        }
        
        this.veterancyAuthorityModifier = modifier;
        this.calculateEffectiveAuthority();
    }

    // Add method to apply morale bonus to nearby units
    applyMoraleBonus(gameContext) {
        if (!this.provideMoraleBonus) return;
        
        const units = gameContext.entityManager.units;
        const MORALE_RANGE = 100; // Range for morale bonus effect
        
        for (const unit of units) {
            if (unit.team === this.team && unit !== this) {
                const distance = this.getDistance(unit);
                if (distance <= MORALE_RANGE) {
                    // Apply scaling bonus based on distance
                    const distanceFactor = 1 - (distance / MORALE_RANGE);
                    const bonus = 0.1 * distanceFactor; // 10% max bonus
                    
                    // Apply temporary combat bonuses
                    unit.tempCombatBonus = (unit.tempCombatBonus || 0) + bonus;
                    
                    // Show morale effect
                    if (Math.random() < 0.1) { // 10% chance per frame to show effect
                        const effect = new Effect(unit.x, unit.y, 'morale', gameContext);
                        gameContext.entityManager.addEffect(effect);
                    }
                }
            }
        }
    }

    // Add method to handle subordinate promotions
    handleSubordinatePromotions(gameContext) {
        if (!this.canPromoteSubordinates) return;
        
        const units = gameContext.entityManager.units;
        const PROMOTION_RANGE = 150; // Range for promotion influence
        const PROMOTION_COOLDOWN = COMMAND_CONFIG.UPDATE_INTERVALS.PROMOTION_COOLDOWN;
        
        for (const unit of units) {
            if (unit.team === this.team && 
                unit !== this && 
                unit.currentCommander === this &&
                Date.now() - unit.lastPromotionTime > PROMOTION_COOLDOWN) {
                
                const distance = this.getDistance(unit);
                if (distance <= PROMOTION_RANGE) {
                    // Check if unit is close to next veterancy level
                    const nextThreshold = COMMAND_CONFIG.VETERANCY_THRESHOLDS[unit.getVeterancyLevel()];
                    const currentProgress = unit.combatExperience / nextThreshold;
                    
                    if (currentProgress >= 0.9) { // 90% to next level
                        // Grant bonus experience to push over threshold
                        const bonusExp = nextThreshold - unit.combatExperience + 1;
                        unit.combatExperience += bonusExp;
                        
                        // Show promotion effect
                        const effect = new Effect(unit.x, unit.y, 'promotion', gameContext);
                        gameContext.entityManager.addEffect(effect);
                        
                        // Show promotion caption
                        const caption = new Caption(
                            unit.x,
                            unit.y - 30,
                            'Promoted by Commander!',
                            '#FFD700',
                            2000,
                            gameContext
                        );
                        gameContext.entityManager.addCaption(caption);
                    }
                }
            }
=======
        const baseDamage = this.type.damage;
        const baseSpeed = this.type.speed; // Assuming this.type.speed is the base speed
        const baseRange = this.type.range;

        // It's assumed that this.damage, this.speed, this.range are instance properties
        // that might initially be copies of this.type.damage etc., or are used to store modified values.
        // If not, this logic would need to adjust multipliers or store modifiers.

        switch (this.veterancyLevel) {
            case 'REGULAR':
                this.damage = baseDamage * 1.1;
                // Re-calculate effective speed if baseSpeed is modified
                // For now, directly modifying this.speed. If this.speed is an effective speed already,
                // this needs to apply to the base speed that this.speed is derived from.
                // The current constructor calculates this.speed once.
                // For simplicity, we'll assume this.speed can be directly modified here.
                this.speed = (this.type.speed || DEFAULT_UNIT_SPEED) * 1.05 / (1 + (this.type.unitWeight || DEFAULT_UNIT_WEIGHT) * WEIGHT_SPEED_PENALTY_FACTOR);

                break;
            case 'VETERAN':
                this.damage = baseDamage * 1.2;
                this.speed = (this.type.speed || DEFAULT_UNIT_SPEED) * 1.1 / (1 + (this.type.unitWeight || DEFAULT_UNIT_WEIGHT) * WEIGHT_SPEED_PENALTY_FACTOR);
                this.range = baseRange * 1.1; // Assuming this.range exists and can be modified
                break;
            case 'ELITE':
                this.damage = baseDamage * 1.3;
                this.speed = (this.type.speed || DEFAULT_UNIT_SPEED) * 1.15 / (1 + (this.type.unitWeight || DEFAULT_UNIT_WEIGHT) * WEIGHT_SPEED_PENALTY_FACTOR);
                this.range = baseRange * 1.2;
                this.canPromoteSubordinates = true;
                break;
            case 'HERO':
                this.damage = baseDamage * 1.4;
                this.speed = (this.type.speed || DEFAULT_UNIT_SPEED) * 1.2 / (1 + (this.type.unitWeight || DEFAULT_UNIT_WEIGHT) * WEIGHT_SPEED_PENALTY_FACTOR);
                this.range = baseRange * 1.3;
                this.provideMoraleBonus = true;
                this.canPromoteSubordinates = true;
                break;
        }
        // Ensure speed does not become negative
        if (this.speed < 0) {
            this.speed = 0;
>>>>>>> origin/jules_wip_12008771546559725757
        }
    }
}

export { Unit };

// Debugging and Validation Functions (as per Sections 5.1 and 6.2)

function validateAuthoritySystem(gameContext) {
    const { units } = gameContext.entityManager;
    const testResults = {
        authorityCollisions: 0,
        commandChainBreaks: 0,
        successionFailures: 0,
        // veterancyErrors: 0 // Not specified in this step's prompt
    };

    // Test 1: Authority collision detection
    const authorityMap = new Map();
    units.forEach(unit => {
        unit.calculateEffectiveAuthority(); // Make sure it's up to date
        const authKey = `${unit.team}-${unit.effectiveAuthority.toFixed(2)}`; // Key by team and authority

        if (authorityMap.has(authKey)) {
            const existingUnits = authorityMap.get(authKey);
            // Check for proximity with any of the existing units with same team and authority
            for (const existingUnit of existingUnits) {
                if (existingUnit.team === unit.team && unit.getDistance(existingUnit) < COMMAND_CONFIG.COMMAND_RANGES.TACTICAL) {
                    testResults.authorityCollisions++;
                    console.warn(`Authority collision: Unit ${unit.id || unit.type.name} and Unit ${existingUnit.id || existingUnit.type.name} (team ${unit.team}) both have authority ${unit.effectiveAuthority.toFixed(2)} within tactical range.`);
                    break;
                }
            }
            existingUnits.push(unit);
        } else {
            authorityMap.set(authKey, [unit]);
        }
    });

    // Test 2: Command chain validation
    units.forEach(unit => {
        if (unit.currentCommander) {
            unit.currentCommander.calculateEffectiveAuthority(); // Ensure commander's authority is up-to-date
            unit.calculateEffectiveAuthority(); // Ensure unit's own authority is up-to-date
            if (unit.currentCommander.effectiveAuthority <= unit.effectiveAuthority) {
                testResults.commandChainBreaks++;
                console.warn(`Command chain break: Unit ${unit.id || unit.type.name} (Auth: ${unit.effectiveAuthority.toFixed(2)}) following lower/equal authority commander ${unit.currentCommander.id || unit.currentCommander.type.name} (Auth: ${unit.currentCommander.effectiveAuthority.toFixed(2)})`);
            }
        }
    });

    // Test 3: Succession system validation (Basic Check)
    units.forEach(unit => {
        if (unit.commandFitness === 'COMBAT_INEFFECTIVE') {
            // Check if it still has subordinates (which it shouldn't if succession worked)
            const hasSubordinates = units.some(u => u.currentCommander === unit);
            if (hasSubordinates) {
                 // Now check if a suitable successor was available
                const hasPotentialSuccessor = units.some(ally =>
                    ally.team === unit.team &&
                    ally !== unit &&
                    unit.getDistance(ally) < COMMAND_CONFIG.COMMAND_RANGES.TACTICAL &&
                    ally.commandFitness === 'FULL_COMMAND' &&
                    ally.effectiveAuthority > unit.effectiveAuthority
                );

                if (hasPotentialSuccessor) {
                    // If it has subordinates AND a potential successor was available, it's a potential failure.
                    testResults.successionFailures++;
                    console.warn(`Potential succession failure: Unit ${unit.id || unit.type.name} is COMBAT_INEFFECTIVE but still has subordinates, and a potential successor was available.`);
                }
            }
        }
    });

    console.log("Authority System Validation Results:", testResults);
    return testResults;
}

function renderCommandHierarchyDebug(ctx, gameContext) {
    const { units } = gameContext.entityManager;
    const camera = gameContext.camera; // Assuming camera is on gameContext

    if (!camera || !units) return;

    units.forEach(unit => {
        const screenX = (unit.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
        const screenY = (unit.y - camera.y) * camera.zoom + camera.canvasHeight / 2;

        // Authority and Veterancy display
        ctx.fillStyle = '#fff';
        ctx.font = '10px Arial';
        ctx.textAlign = 'center';
        if (unit.effectiveAuthority !== undefined) {
            ctx.fillText(`Auth: ${unit.effectiveAuthority.toFixed(0)}`, screenX, screenY - 20);
        }
        if (unit.veterancyLevel) {
            ctx.fillText(`${unit.veterancyLevel}`, screenX, screenY - 10);
        }


        // Command lines
        if (unit.currentCommander) {
            const commanderScreenX = (unit.currentCommander.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const commanderScreenY = (unit.currentCommander.y - camera.y) * camera.zoom + camera.canvasHeight / 2;

            ctx.strokeStyle = '#ff0'; // Yellow line
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(screenX, screenY);
            ctx.lineTo(commanderScreenX, commanderScreenY);
            ctx.stroke();
        }

        // Authority radius - using a threshold like VETERANCY_THRESHOLDS.REGULAR (25 points)
        // or an authority value like 20 as suggested in the prompt.
        // Let's use effectiveAuthority > a certain value, e.g., 10 (REGULAR gives +2, VETERAN +5, so base + REGULAR could be ~7-10+)
        // COMMAND_CONFIG.VETERANCY_THRESHOLDS.REGULAR is an XP value, not an authority value.
        // Let's use a simple authority threshold, e.g., > 10 for drawing a radius.
        if (unit.effectiveAuthority > 10) {
            ctx.strokeStyle = 'rgba(0, 255, 0, 0.3)'; // Light green, semi-transparent
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.arc(screenX, screenY, COMMAND_CONFIG.COMMAND_RANGES.SQUAD * camera.zoom, 0, Math.PI * 2);
            ctx.stroke();
        }

        // Visualize idealFormationSlotWorld for followers
        if (unit.idealFormationSlotWorld) {
            const idealSlotScreenX = (unit.idealFormationSlotWorld.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const idealSlotScreenY = (unit.idealFormationSlotWorld.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
            ctx.fillStyle = 'rgba(0, 0, 255, 0.5)'; // Blue circle
            ctx.beginPath();
            ctx.arc(idealSlotScreenX, idealSlotScreenY, 5 * camera.zoom, 0, Math.PI * 2);
            ctx.fill();
        }

        // Visualize leaderPredictedPosition for followers
        if (unit.leaderPredictedPosition) {
            const predLeaderScreenX = (unit.leaderPredictedPosition.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const predLeaderScreenY = (unit.leaderPredictedPosition.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
            ctx.fillStyle = 'rgba(255, 0, 0, 0.7)'; // Red dot/cross
            // Simple dot for now
            ctx.beginPath();
            ctx.arc(predLeaderScreenX, predLeaderScreenY, 3 * camera.zoom, 0, Math.PI * 2);
            ctx.fill();
            // Could draw a small cross instead:
            // ctx.strokeStyle = 'rgba(255, 0, 0, 0.7)';
            // ctx.beginPath();
            // ctx.moveTo(predLeaderScreenX - 3 * camera.zoom, predLeaderScreenY);
            // ctx.lineTo(predLeaderScreenX + 3 * camera.zoom, predLeaderScreenY);
            // ctx.moveTo(predLeaderScreenX, predLeaderScreenY - 3 * camera.zoom);
            // ctx.lineTo(predLeaderScreenX, predLeaderScreenY + 3 * camera.zoom);
            // ctx.stroke();
        }
    });
    ctx.textAlign = 'left'; // Reset text align
}


if (typeof window !== 'undefined') {
    window.debugRTS = {
        validateAuthoritySystem,
        renderCommandHierarchyDebug
    };
}
