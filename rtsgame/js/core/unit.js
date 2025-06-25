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
        this.type = type;
        this.hp = type.maxHp;
        this.maxHp = type.maxHp;
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
        this.fleeThreshold = this.maxHp * 0.2;
        this.formation = null;
        
        // Add Computronium core if this unit type has one
        if (type.hasComputroniumCore && simulation && simulation.computroniumManagers) {
            const manager = simulation.computroniumManagers[team];
            if (manager) {
                this.computroniumCore = manager.addCore(this, type.coreEfficiency || 1.0);
                console.log(`[Unit] Added Computronium core to ${type.name}`);
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

        // Speed calculation considering weight
        const baseSpeed = this.type.speed || DEFAULT_UNIT_SPEED;
        const weight = this.type.unitWeight || DEFAULT_UNIT_WEIGHT;
        let speedDenominator = 1 + (weight * WEIGHT_SPEED_PENALTY_FACTOR);
        if (speedDenominator <= 0.1) {
            speedDenominator = 0.1;
        }
        this.speed = baseSpeed / speedDenominator;
        if (this.speed < 0) {
            this.speed = 0;
        }

        // Enhanced authority properties
        this.baseAuthority = this.commandAuthority; // Use original for base
        this.healthAuthorityModifier = 0;
        this.veterancyAuthorityModifier = 0;
        this.contextAuthorityModifier = 0;
        this.effectiveAuthority = this.baseAuthority;

        // Veterancy tracking
        this.combatExperience = 0;        // Successful attacks landed
        this.survivalTime = 0;            // Time alive in combat zones
        this.commandExperience = 0;       // Subordinates successfully commanded
        this.killCount = 0;               // Enemy units destroyed
        this.damageDelt = 0;              // Total damage inflicted
        this.lastPromotionTime = 0;       // Prevents spam promotions
        this.veterancyLevel = 'GREEN';

        // Health-based command fitness
        this.commandFitness = 'FULL_COMMAND';
        this.lastAuthorityUpdate = 0;
        this.commandSuccesses = 0;
        this.commandFailures = 0;
        this.currentCommander = null; // Track who this unit is following
        this.lastCommandChange = 0; // Timestamp of last command transfer

        // Computronium integration
        this.computroniumCoreLevel = type.computroniumCoreLevel || 0;
        this.coreFocusMode = type.defaultCoreFocusMode || 'BALANCED';
        this.computroniumAuthorityModifier = 0;
        this.computroniumCore = null;
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
        this.baseAuthority = this.calculateBaseAuthority();
        this.effectiveAuthority = this.baseAuthority;
        
        // Add promotion callback
        this.onPromotion = null;

        // Add autonomous behavior
        this.autonomousBehavior = null;
        this.hasExplicitOrders = false;
    }

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
        if (this.type.maxEnergyShields > 0) {
            if (this.currentEnergyShields < this.type.maxEnergyShields) {
                const regenRate = this.type.shieldRegenRate || DEFAULT_SHIELD_REGEN_RATE;
                this.currentEnergyShields += regenRate * deltaTime;
                if (this.currentEnergyShields > this.type.maxEnergyShields) {
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

        // Record damage and experience
        if (damage > 0) {
            this.progression.recordDamage(damage);
        }
        
        // Record kill
        if (target.hp <= 0) {
            this.progression.recordKill(target);
        }
    }

    takeDamage(damage, sourceUnit, simulation) {
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
    followSuperiorOrders(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
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
}

export { Unit };
