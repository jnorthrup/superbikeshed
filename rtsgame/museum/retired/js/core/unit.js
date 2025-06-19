"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Unit = void 0;
const unitTypes_js_1 = require("../config/unitTypes.js");
const gameConstants_js_1 = require("../config/gameConstants.js");
const astar_js_1 = require("../pathfinding/astar.js");
const projectile_js_1 = require("./entities/projectile.js");
const effect_js_1 = require("./entities/effect.js");
const caption_js_1 = require("./entities/caption.js");
const building_js_1 = require("./building.js");
const unitProgression_js_1 = require("./unitProgression.js");
const autonomousBehavior_js_1 = require("./autonomousBehavior.js");
const commandConfig_js_1 = require("../config/commandConfig.js");
const astar_js_2 = require("../pathfinding/astar.js");
const alloyTypes_js_1 = require("../config/alloyTypes.js");
const WEIGHT_SPEED_PENALTY_FACTOR = 0.01;
const DEFAULT_UNIT_SPEED = 1.0;
const DEFAULT_UNIT_WEIGHT = 0;
const SHIELD_EFFECTIVENESS_WATTAGE_BASELINE = 20; // Baseline weaponEnergyCost for 1x effectiveness vs shields.
const DEFAULT_SHIELD_REGEN_RATE = 1.0; // Default shield regen per second if not specified.
class Unit {
    constructor(id, simulation, typeDetails) {
        this.id = id;
        this.simulation = simulation;
        this.type = typeDetails; // Store the original type definition (static data)

        // Dynamic state (like hp, x, y, team, angle, shields, currentEnergy, armorValue, etc.)
        // is now managed by EntityManager. This constructor should not initialize them on `this`.
        // Alloy modifications to base stats are assumed to be handled by EntityFactory
        // which then passes these initial values to EntityManager.addUnit.

        this.targetId = null; // Stores ID of the target, not the object
        this.cooldown = 0; // Represents attack cooldown time remaining

        // Transient internal operational state (examples)
        this.vx = 0; // Calculated velocity, might be kept transiently for a frame
        this.vy = 0;
        this.selected = false; // UI state, likely remains on client-side representation or a separate UI state map
        this.task = null; // Generic task, could be expanded

        this.patrolTarget = null; // {x, y} coordinate, not an entity object
        this.lastTargetSwitch = 0;

        // Access seedRandom from the simulation instance's direct property for things not tied to EM state
        this.aggressiveness = 0.7 + (simulation.seedRandom ? simulation.seedRandom.random() * 0.3 : Math.random() * 0.3);
        this.tacticalRole = this.determineTacticalRole(); // Based on static type data
        this.lastFireTime = 0;
        this.preferredRange = this.type.range * 0.8; // Based on static type data
        this.militaryRank = this.determineMilitaryRank(); // Based on static type data
        this.survivalPriority = this.calculateSurvivalPriority(); // Based on static type data
        // commandAuthority might be fetched from EM if it can change, or be static from type. Assuming static for now from type.
        this.commandAuthority = this.type.tier * 10 + (this.type.support ? 5 : 0);
        this.protectionNeeds = [];
        this.lastThreatAssessment = 0;
        // fleeThreshold will be calculated based on maxHp fetched from EM.
        // this.fleeThreshold = (this.simulation.entityManager.getUnitHealth(this.id)?.maxHp || 100) * 0.2;
        this.formation = null; // If this is complex state, might need ECS. If simple (e.g. formation ID), could stay.

        // Computronium core and Command Hierarchy registration are now handled externally,
        // likely when the entity is created by EntityFactory and added to EntityManager.
        // The simulation systems (ComputroniumManager, EnhancedCommandHierarchy) would query
        // EntityManager for new entities with relevant components.
        // this.computroniumCore = null; // No longer managed directly by Unit instance
        // this.commandNode = null; // No longer managed directly by Unit instance

        this.captionCooldown = 0;
        this.constructionTask = null; // Specific for ACU/Engineer type units
        if (this.type.grenadeAbility) {
            this.grenadeCooldown = 0;
        }
        this.stuckFrames = 0;
        this.significantMoveThreshold = (this.type.size / 4) || 2.5; // Min distance in world units
        // this.lastPositionForStuckCheck will be initialized in updateStuckDetection on first run
        this.lastPositionForStuckCheck = null;
        this.isEscaping = false;
        this.escapeAngle = 0;
        this.escapeDuration = 0;
        this.STUCK_FRAMES_THRESHOLD = 30;
        this.ESCAPE_MODE_DURATION_FRAMES = 60; // This should be time based, not frame based.
        this.path = null;
        this.currentWaypointIndex = 0;
        this.pathRequestCooldown = 0; // Time based
        this.PATH_REQUEST_INTERVAL = 0.5; // Seconds
        // Track who this unit is following and last command change
        this.currentCommander = null; // This would store an ID if needed, or be managed by a C&C system
        this.lastCommandChange = 0;

        // Computronium related properties - if these are dynamic and part of unit state, they'd be in EM.
        // If they are configured by type and don't change, they can stay or be derived from this.type.
        // For now, assuming they are more operational/configurable than core dynamic state.
        // this.computroniumCoreLevel = this.type.computroniumCoreLevel || 0; // Read from EM if dynamic
        this.coreFocusMode = this.type.defaultCoreFocusMode || 'BALANCED';
        // this.computroniumAuthorityModifier = 0; // Read from EM if dynamic

        this.lastFocusModeChange = 0;
        this.focusModeCooldown = 10000; // 10 seconds cooldown between mode changes
        // Core Focus Mode effects (static data based on this.type or general config)
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
        this.progression = new unitProgression_js_1.UnitProgression(this);
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
        // Added contextAuthorityModifier and computroniumAuthorityModifier with default 1 or suitable default
        // Based on existing patterns, these are initialized to 0 and updated later.
        // The subtask asks for them to be added "to the constructor".
        // They are already initialized to 0 here.
        // If the intention was constructor parameters, that's a different change.
        // For now, ensuring they exist as properties, initialized.
        // The prompt also says "defaulting to 1 (or another suitable default)".
        // Existing code initializes them to 0. I will keep it 0 as it seems to be an additive modifier.
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
        this.maxForce = commandConfig_js_1.COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_FORCE;
        // Maximum rate (radians per frame) at which the unit can turn.
        this.maxTurnRate = commandConfig_js_1.COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_TURN_RATE_RADIANS_PER_FRAME;
        // Accumulator for steering forces (seek, separation, avoidance) each frame.
        this.steering = { x: 0, y: 0 };
        // Flag to enable detailed console logging for this unit's formation behavior.
        this.debugFormation = false;
    }
    // Removed the duplicate placeholder calculateBaseAuthority() method from here
    getCurrentSpeed(simulation) {
        const terrain = simulation.terrain;
        const currentPosition = this.simulation.entityManager.getUnitPosition(this.id);

        // Base speed from unit type definition. EntityFactory would have applied alloy effects to stats in EM if speed is dynamic.
        // For now, assume this.type.speed is the base, and dynamic speed changes would be reflected by reading a speed component from EM.
        // If speed is purely static (after initial alloy mods), this.type.speed is fine.
        const baseSpeed = this.type.speed || DEFAULT_UNIT_SPEED;
        const weight = this.type.unitWeight || DEFAULT_UNIT_WEIGHT;
        const weightDenominator = Math.max(0.1, 1 + (weight * WEIGHT_SPEED_PENALTY_FACTOR));
        let effectiveBaseSpeed = baseSpeed;

        if (!currentPosition) {
            return effectiveBaseSpeed / weightDenominator;
        }

        const tileX = Math.floor(currentPosition.x / gameConstants_js_1.TILE_SIZE);
        const tileY = Math.floor(currentPosition.y / gameConstants_js_1.TILE_SIZE);

        if (tileX >= 0 && tileX < gameConstants_js_1.GRID_SIZE && tileY >= 0 && tileY < gameConstants_js_1.GRID_SIZE &&
            terrain[tileX] && terrain[tileX][tileY] !== undefined) {
            const terrainTypeInfo = terrain[tileX][tileY];
            const currentTerrainType = terrainTypeInfo.type;

            if (this.type.movementType === 'amphibious') {
                if (currentTerrainType === gameConstants_js_1.TERRAIN_TYPES.WATER && typeof this.type.speedWater === 'number') {
                    effectiveBaseSpeed = this.type.speedWater;
                } else if (currentTerrainType === gameConstants_js_1.TERRAIN_TYPES.LAND && typeof this.type.speedLand === 'number') {
                    effectiveBaseSpeed = this.type.speedLand;
                }
            }
        }
        return effectiveBaseSpeed / weightDenominator;
    }
    update(simulation, deltaTime) {
        const { entityManager, gameState, seedRandom } = simulation;

        // Energy Regeneration
        const unitEnergyComponent = entityManager.getEnergy(this.id); // { current, max }
        if (unitEnergyComponent && typeof this.type.generatorOutput === 'number') {
            // Max energy is defined by the type's batteryCapacity
            const maxEnergyCapacity = this.type.batteryCapacity || unitEnergyComponent.max;
            let currentEnergyVal = unitEnergyComponent.current;
            currentEnergyVal += this.type.generatorOutput * deltaTime;
            if (currentEnergyVal > maxEnergyCapacity) {
                currentEnergyVal = maxEnergyCapacity;
            }
            entityManager.setEnergy(this.id, currentEnergyVal, maxEnergyCapacity);
        }

        // Shield Regeneration
        const unitShieldComponent = entityManager.getShield(this.id); // { current, max }
        // Max shield from type definition, regen rate from type (already alloy modified by factory if applicable)
        const maxShieldCapacity = this.type.maxEnergyShields || (unitShieldComponent ? unitShieldComponent.max : 0);
        const baseShieldRegenRate = this.type.shieldRegenRate || (maxShieldCapacity > 0 ? DEFAULT_SHIELD_REGEN_RATE : 0);

        if (unitShieldComponent && maxShieldCapacity > 0) {
            let currentShieldsVal = unitShieldComponent.current;
            if (currentShieldsVal < maxShieldCapacity) {
                currentShieldsVal += baseShieldRegenRate * deltaTime;
                if (currentShieldsVal > maxShieldCapacity) {
                    currentShieldsVal = maxShieldCapacity;
                }
                entityManager.setShield(this.id, currentShieldsVal, maxShieldCapacity);
            }
        }

        if (this.type.support) {
            this.performSupportRole(simulation, deltaTime);
            if (this.type === unitTypes_js_1.UNIT_TYPES.commander && this.constructionTask) {
                /* Commander busy */
            }
            else if (this.type.name === 'Shield Generator') {
                /* Shield gen busy */
            }
            else if (this.type.name === 'Engineer' && this.target) {
                /* Engineer busy */
            }
            else {
                this.defaultMovementAndTargeting(simulation, deltaTime); // Pass simulation and deltaTime
            }
        }
        else {
            this.defaultMovementAndTargeting(simulation, deltaTime); // Pass simulation and deltaTime
        }
        this.updateStuckDetection(simulation); // Pass simulation
        this.updateTacticalBehavior(simulation); // Pass simulation
        this.updateSurvivalBehaviors(simulation); // Pass simulation
        this.executeCommandHierarchy(simulation); // Pass simulation
        if (this.cooldown > 0)
            this.cooldown -= deltaTime;
        if (this.pathRequestCooldown > 0)
            this.pathRequestCooldown -= deltaTime;
        if (this.type.grenadeAbility && this.grenadeCooldown > 0) {
            this.grenadeCooldown -= deltaTime;
        }
        if (this.captionCooldown > 0)
            this.captionCooldown -= deltaTime;
        // Update progression
        this.progression.update(simulation, deltaTime);
        // Update autonomous behavior if no explicit orders
        if (this.autonomousBehavior && !this.hasExplicitOrders) {
            this.autonomousBehavior.update(deltaTime);
        }
        // Update authority every 5 seconds (as per implementation-guide.md Section 1.3)
        const now = performance.now();
        if (now - this.lastAuthorityUpdate > commandConfig_js_1.COMMAND_CONFIG.UPDATE_INTERVALS.AUTHORITY_RECALC) {
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
        // Apply morale bonus if capable
        if (this.provideMoraleBonus) {
            this.applyMoraleBonus(simulation);
        }
        // Handle subordinate promotions if capable
        if (this.canPromoteSubordinates) {
            this.handleSubordinatePromotions(simulation);
        }
    }
    defaultMovementAndTargeting(simulation, deltaTime) {
        const { entityManager, gameState, seedRandom } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!currentPos) return;

        // this.angle is treated as a transient property for steering calculations this frame.
        // If persistent angle is needed (e.g. for rendering), it should be in EM.
        let frameAngle = this.angle || 0; // Use last frame's angle or default.

        const { resourceNodes } = simulation.gameContext;
        if (this.isEscaping) {
            if (this.escapeDuration > 0) {
                frameAngle = this.escapeAngle;
                const currentSpeed = this.getCurrentSpeed(simulation);
                this.vx = Math.cos(frameAngle) * currentSpeed;
                this.vy = Math.sin(frameAngle) * currentSpeed;
                this.escapeDuration -= deltaTime;
            }
            else {
                this.isEscaping = false;
                this.stuckFrames = 0;
                this.path = null;
            }
        }
        else {
            // Check if targetId is valid and target is alive
            let currentTargetAlive = false;
            if (this.targetId) {
                const targetHealth = entityManager.getUnitHealth(this.targetId); // Assuming target can be unit or building
                if (targetHealth && targetHealth.hp > 0) {
                    currentTargetAlive = true;
                } else {
                    // Check if it's a building (if buildings are also in EM with health)
                    // For now, assume getUnitHealth handles both or buildings have separate getter
                }
            }

            if (!this.targetId || !currentTargetAlive || (Date.now() - this.lastTargetSwitch > 15000 && seedRandom.random() < 0.05)) {
                this.findTarget(simulation); // findTarget will set this.targetId
                this.lastTargetSwitch = Date.now();
                this.path = null;
            }

            if (seedRandom.random() < 0.005 && !this.targetId && !this.patrolTarget) {
                if (resourceNodes && resourceNodes.length > 0) {
                    const targetNode = resourceNodes[Math.floor(seedRandom.random() * resourceNodes.length)];
                    if (targetNode) {
                        this.patrolTarget = { x: targetNode.x, y: targetNode.y };
                        this.path = null;
                    }
                }
            }

            let destination = null;
            if (this.targetId) {
                destination = entityManager.getUnitPosition(this.targetId);
            } else if (this.patrolTarget) {
                destination = this.patrolTarget;
            }

            if (destination) {
                let needsNewPath = false;
                if (!this.path) {
                    needsNewPath = true;
                } else {
                    if (this.pathRequestCooldown <= 0) {
                        const pathEndPoint = this.path[this.path.length - 1];
                        const dxTarget = destination.x - pathEndPoint.x;
                        const dyTarget = destination.y - pathEndPoint.y;
                        if (Math.sqrt(dxTarget * dxTarget + dyTarget * dyTarget) > gameConstants_js_1.TILE_SIZE * 2) {
                            needsNewPath = true;
                        }
                    }
                }

                if (needsNewPath && this.pathRequestCooldown <= 0) {
                    const moveType = this.type.movementType || 'land';
                    this.path = (0, astar_js_1.findPath)({ x: currentPos.x, y: currentPos.y }, { x: destination.x, y: destination.y }, simulation.gameContext, moveType);
                    this.currentWaypointIndex = 0;
                    this.pathRequestCooldown = this.PATH_REQUEST_INTERVAL; // Now in seconds
                    // gameState.addEvent logging...
                }

                if (this.path && this.currentWaypointIndex < this.path.length) {
                    const waypoint = this.path[this.currentWaypointIndex];
                    const dx = waypoint.x - currentPos.x;
                    const dy = waypoint.y - currentPos.y;
                    const distanceToWaypoint = Math.sqrt(dx * dx + dy * dy);
                    const WAYPOINT_REACH_THRESHOLD = Math.max(this.type.size || 10, gameConstants_js_1.TILE_SIZE * 0.75);

                    if (distanceToWaypoint < WAYPOINT_REACH_THRESHOLD) {
                        this.currentWaypointIndex++;
                        if (this.currentWaypointIndex >= this.path.length) {
                            this.path = null;
                            if (this.patrolTarget && Math.abs(currentPos.x - this.patrolTarget.x) < WAYPOINT_REACH_THRESHOLD && Math.abs(currentPos.y - this.patrolTarget.y) < WAYPOINT_REACH_THRESHOLD) {
                                this.patrolTarget = null;
                            }
                        }
                    }

                    if (this.path && this.currentWaypointIndex < this.path.length) {
                        const nextWaypoint = this.path[this.currentWaypointIndex];
                        frameAngle = Math.atan2(nextWaypoint.y - currentPos.y, nextWaypoint.x - currentPos.x);
                        const currentSpeed = this.getCurrentSpeed(simulation);
                        this.vx = Math.cos(frameAngle) * currentSpeed;
                        this.vy = Math.sin(frameAngle) * currentSpeed;
                    }
                    else {
                        this.vx = 0;
                        this.vy = 0;
                    }
                }
                else if (this.target) {
                    const distToTarget = this.getDistance(this.target);
                    this.handleCombatPositioning(distToTarget, simulation);
                }
                else if (this.patrolTarget) {
                    const distToPatrol = Math.sqrt(Math.pow(this.patrolTarget.x - this.x, 2) + Math.pow(this.patrolTarget.y - this.y, 2));
                    if (distToPatrol < gameConstants_js_1.TILE_SIZE * 2) {
                        this.angle = Math.atan2(this.patrolTarget.y - this.y, this.patrolTarget.x - this.x);
                        const currentSpeed = this.getCurrentSpeed(simulation);
                        this.vx = Math.cos(this.angle) * currentSpeed;
                        this.vy = Math.sin(this.angle) * currentSpeed;
                    }
                    else {
                        this.vx = 0;
                        this.vy = 0;
                    }
                    if (distToPatrol < gameConstants_js_1.TILE_SIZE * 0.5) // Uses currentPos implicitly via this.x/y
                        this.patrolTarget = null;
                }
                else { // No target, no path, no patrol target (or too far) -> wander
                    if (seedRandom.random() < 0.02) {
                        frameAngle += (seedRandom.random() - 0.5) * 0.5;
                    }
                    const currentSpeed = this.getCurrentSpeed(simulation);
                    this.vx = Math.cos(frameAngle) * currentSpeed * 0.5;
                    this.vy = Math.sin(frameAngle) * currentSpeed * 0.5;
                }
            }
            else { // No destination (target or patrol) -> wander
                if (seedRandom.random() < 0.02) {
                    frameAngle += (seedRandom.random() - 0.5) * 0.5;
                }
                const currentSpeed = this.getCurrentSpeed(simulation);
                this.vx = Math.cos(frameAngle) * currentSpeed * 0.5;
                this.vy = Math.sin(frameAngle) * currentSpeed * 0.5;
            }
        }
        this.angle = frameAngle; // Store the finally decided angle for this frame if it's used by other systems or next frame's logic start
        this.applyMovement(simulation, deltaTime);
        // Cooldowns are handled in the main update method with deltaTime
    }
    launchGrenade(targetX, targetY, simulation) {
        const { entityManager, gameState, seedRandom } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        if (!currentPos || !ownerTeam) return;

        if (!this.type.grenadeAbility) {
            console.warn(`${this.type.name} does not have grenade ability.`);
            return;
        }
        if (this.grenadeCooldown > 0) {
            console.log(`${this.type.name} grenade is on cooldown: ${this.grenadeCooldown.toFixed(1)}s left.`);
            gameState.addEvent('ui_error', 'Grenade ability on cooldown!', 1);
            return;
        }
        const dx = targetX - currentPos.x;
        const dy = targetY - currentPos.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > this.type.grenadeAbility.range) {
            console.log(`Target out of grenade range. Max: ${this.type.grenadeAbility.range}, Target: ${dist.toFixed(0)}`);
            gameState.addEvent('ui_error', 'Target out of grenade range!', 1);
            return;
        }
        console.log(`${ownerTeam} ${this.type.name} launching grenade at ${targetX.toFixed(0)}, ${targetY.toFixed(0)}`);
        const projectile = new projectile_js_1.GrenadeProjectile(
            currentPos.x, currentPos.y, targetX, targetY, ownerTeam, this.type.grenadeAbility
        );
        entityManager.addProjectile(projectile);
        this.grenadeCooldown = this.type.grenadeAbility.cooldownTime;
        gameState.addEvent('ability_used', `${this.type.name} launched grenade.`, 2, { x: currentPos.x, y: currentPos.y });
    }
    performSupportRole(simulation, deltaTime) {
        const { entityManager, gameState, seedRandom, resources } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        const ownerTeam = entityManager.getOwnerTeam(this.id); // Fetched ownerTeam
        if (!currentPos || !ownerTeam) return;

        const { addEvent } = gameState;
        // const { resourceNodes } = simulation.gameContext; // Not used in commander part

        if (this.type === unitTypes_js_1.UNIT_TYPES.commander) {
            if (!this.constructionTask && this.type.buildList && this.type.buildList.length > 0) {
                let buildingToBuildType = null;
                // const teamPlayerId = ownerTeam === 'blue' ? 0 : 1; // Not directly used for resource check here
                const currentTeamResources = simulation.resources[ownerTeam]; // Direct access to simulation.resources
                if (!currentTeamResources) return;


                // TODO: Query EM for existing buildings of this team to make decisions.
                // For now, simplified decision logic.
                // const teamExtractors = entityManager.countEntities({ team: ownerTeam, typeName: 'Mass Extractor' }); // Conceptual
                let teamExtractors = 0; // Placeholder

                if (teamExtractors === 0) {
                    buildingToBuildType = this.type.buildList.find(bt =>
                        bt.name === 'Mass Extractor' &&
                        currentTeamResources.mass >= (bt.cost.mass || 0) &&
                        currentTeamResources.energy >= (bt.cost.energy || 0)
                    );
                }
                // ... (similar resource checks & logic for other buildings, e.g. Energy Plant, Factory) ...
                if (buildingToBuildType) {
                    const buildX = currentPos.x + (seedRandom.random() * 100 - 50) + 100; // Example offset with some variation
                    const buildY = currentPos.y + (seedRandom.random() * 100 - 50);
                    // TODO: Spot clear check using EM to query for entities at buildX, buildY
                    this.constructionTask = { targetX: buildX, targetY: buildY, type: buildingToBuildType, progress: 0, buildingStarted: false };
                }
            }
            if (this.constructionTask) {
                // Simplified movement part: if not at site, move towards it.
                // Actual movement would use pathfinding from defaultMovementAndTargeting.
                // For now, just focus on the construction part once at site.
                if (!this.constructionTask.buildingStarted) {
                     // Assume unit is at build site for simplicity of this refactor part
                    this.constructionTask.buildingStarted = true;
                    // Resource deduction is handled by EntityFactory for units. Buildings might need similar.
                    // For now, assuming building costs are checked but not re-deducted here if factory handles it.
                    const captionMsg = `Building ${this.constructionTask.type.name}`;
                    entityManager.addCaption(new caption_js_1.Caption(currentPos.x, currentPos.y, captionMsg, '#0f0', 120));
                } else {
                    this.constructionTask.progress += (this.type.buildRate || 1.0) * deltaTime;
                    if (this.constructionTask.progress >= this.constructionTask.type.buildTime) {
                        // Building creation itself should be done via EntityFactory, which calls EntityManager
                        // simulation.entityFactory.createBuilding(this.constructionTask.type, ownerTeam, this.constructionTask.targetX, this.constructionTask.targetY);
                        console.log(`TODO: Unit ${this.id} attempting to complete building ${this.constructionTask.type.name}. Delegate to EntityFactory.`);
                        addEvent('build', `${ownerTeam.toUpperCase()} ACU completed ${this.constructionTask.type.name}!`, 2, { x: this.constructionTask.targetX, y: this.constructionTask.targetY });
                        this.constructionTask = null;
                    }
                }
                return; // Commander is busy constructing
            }
            this.defaultMovementAndTargeting(simulation, deltaTime);
        }
        // TODO: Refactor Engineer and Shield Generator logic similarly, using currentPos and ownerTeam from EM.
    }
    moveTowards(targetPos, simulation) { // targetPos should be {x,y}
        const currentPos = this.simulation.entityManager.getUnitPosition(this.id);
        if(!currentPos || !targetPos) return;

        const dx = targetPos.x - currentPos.x;
        const dy = targetPos.y - currentPos.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        // const moveThreshold = targetEntityData ? (targetEntityData.type.size / 2) : 50; // If target is an entity
        const moveThreshold = 50; // Simplified for now

        if (dist > moveThreshold) {
            this.angle = Math.atan2(dy, dx); // this.angle is transient
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed;
            this.vy = Math.sin(this.angle) * currentSpeed;
        } else {
            this.vx = 0;
            this.vy = 0;
        }
        this.applyMovement(simulation, simulation.deltaTime || (1/60)); // Pass deltaTime
    }
    applyMovement(simulation, deltaTime) { // deltaTime must be passed or accessed from simulation
        const { entityManager } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!currentPos) return;

        let newX = currentPos.x + this.vx * deltaTime;
        let newY = currentPos.y + this.vy * deltaTime;

        // TODO: Collision detection with other entities from EM
        // const allUnitPositions = entityManager.getAllUnitPositions(); // Needs method in EM
        // for (const otherPos of allUnitPositions) { if (this.id !== otherPos.id && /* collision */ ) { ... } }

        newX = Math.max(0, Math.min(gameConstants_js_1.WORLD_SIZE, newX));
        newY = Math.max(0, Math.min(gameConstants_js_1.WORLD_SIZE, newY));

        entityManager.setUnitPosition(this.id, newX, newY);
        // If this.angle (orientation) is a persistent state, it should be set via EM too.
        // e.g., if (this.vx !== 0 || this.vy !== 0) entityManager.setAngle(this.id, Math.atan2(this.vy, this.vx));
        // For now, this.angle is treated as transient for steering.
    }
    findTarget(simulation) {
        const { entityManager } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!currentPos) return;

        const ownTeam = entityManager.getOwnerTeam(this.id);
        if (!ownTeam) return;

        let closestTargetId = null;
        let closestDistance = Infinity;

        // TODO: This needs a proper way to iterate ALL active entities from EntityManager
        // and get their relevant components (id, team, position, health, type for range).
        // For now, this is a conceptual placeholder.
        const activeEntities = entityManager.getAllActiveEntityDataForTargeting(); // Hypothetical method

        for (const entityData of activeEntities) { // entityData = { id, team, position, health, type }
            if (entityData.id === this.id || entityData.team === ownTeam || entityData.health <= 0) {
                continue;
            }
            const distance = this.getDistanceToCoords(currentPos, entityData.position);
            if (distance <= (this.type.range || 0) && distance < closestDistance) {
                closestTargetId = entityData.id;
                closestDistance = distance;
            }
        }
        this.targetId = closestTargetId;
    }

    getDistanceToCoords(pos1, pos2) { // Utility, doesn't use 'this' state other than type for range
        if (!pos1 || !pos2) return Infinity;
        const dx = pos2.x - pos1.x;
        const dy = pos2.y - pos1.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // getDistance(targetId) is effectively replaced by getDistanceToCoords after fetching target's position.
    // If a method specifically needs distance to a targetId, it should fetch currentPos and targetPos first.
    // For internal logic that might still pass an object (like in original executeGroupMovement),
    // this temporary version can stay, but it's not ideal.
    getDistance(targetEntityOrId) {
        const currentPos = this.simulation.entityManager.getUnitPosition(this.id);
        if (!currentPos) return Infinity;

        let targetPosX, targetPosY;
        if (typeof targetEntityOrId === 'string') {
            const targetPosData = this.simulation.entityManager.getUnitPosition(targetEntityOrId);
            if (!targetPosData) return Infinity;
            targetPosX = targetPosData.x;
            targetPosY = targetPosData.y;
        } else if (targetEntityOrId && typeof targetEntityOrId.x === 'number' && typeof targetEntityOrId.y === 'number') {
            targetPosX = targetEntityOrId.x;
            targetPosY = targetEntityOrId.y;
        } else {
            return Infinity; // Invalid target
        }

        const dx = targetPosX - currentPos.x;
        const dy = targetPosY - currentPos.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    showStateCaption(simulation) {
        const { entityManager, seedRandom } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!currentPos) return;

        if (this.captionCooldown <= 0 && seedRandom.random() < 0.01) {
            // Example: Add actual state-based caption logic here if needed
            // For instance, if unit is stuck (this.stuckFrames > threshold), show "Stuck!"
            // For now, it's just a placeholder for a random caption.
            // entityManager.addCaption(new Caption(currentPos.x, currentPos.y, "Reporting!", "#FFF", 10));
            this.captionCooldown = 2.0; // Approx 2 seconds
        }
    }
    attack(targetId, simulation) { // targetId is a string ID
        const { entityManager, gameState } = simulation;
        const attackerPos = entityManager.getUnitPosition(this.id);

        // Fetch attacker's damage from EntityManager or static type definition
        const attackerDamageValue = entityManager.getAttackDamage(this.id);
        // If getAttackDamage returns an object like {value: X}, extract value. Otherwise, use it directly.
        // Fallback to static type damage if not found in EM (e.g., if not set or unit has no dynamic damage component)
        let actualAttackerDamage = 0;
        if (attackerDamageValue !== null && attackerDamageValue !== undefined) {
            actualAttackerDamage = typeof attackerDamageValue === 'object' ? attackerDamageValue.value : attackerDamageValue;
        } else {
            actualAttackerDamage = this.type.damage || 0; // Fallback to static type damage
        }

        if (actualAttackerDamage === null || actualAttackerDamage === undefined) {
            console.error(`Attacker ${this.id} damage not found or is undefined.`);
            return;
        }
        if (actualAttackerDamage === 0) return; // No damage to deal

        const attackerTeam = entityManager.getOwnerTeam(this.id);
        if (!attackerPos || !attackerTeam) return;

        const targetPos = entityManager.getUnitPosition(targetId);
        if (!targetPos) return; // Target may have been destroyed and removed

        // Delegate damage application to the Simulation method
        simulation.handleDamageApplication(this.id, targetId, actualAttackerDamage);

        entityManager.addEffect(new effect_js_1.Effect(attackerPos.x, attackerPos.y, targetPos.x, targetPos.y, this.type.effectColor));

        // Event logging and progression can remain, but ensure they use IDs and EM where appropriate.
        // Note: Kill confirmation should ideally happen after handleDamageApplication if it's synchronous,
        // or be handled by an event/system that processes unit deaths.
        // For now, this local tracking is an approximation.
        if (actualAttackerDamage > 0) {
            this.combatExperience += 1;
            this.damageDelt += actualAttackerDamage;
            this.progression.recordDamage(actualAttackerDamage);
        }

        // This kill check is speculative as handleDamageApplication now handles deactivation.
        // A more robust system would use events or check isActive after simulation tick.
        // const targetHealthAfterAttack = entityManager.getUnitHealth(targetId);
        // if (targetHealthAfterAttack && targetHealthAfterAttack.hp <= 0) { // Or check !entityManager.getUnitIsActive(targetId)
        //     this.killCount += 1;
        //     // this.progression.recordKill(targetId);
        // }
    }

    /**
     * @deprecated Direct damage application to a Unit instance is deprecated.
     * Use Simulation.handleDamageApplication(attackerId, targetId, baseDamage) instead.
     * This method's logic has been largely moved to Simulation.handleDamageApplication.
     * It might be kept for specific direct damage scenarios not originating from a Unit.attack,
     * but its direct use in unit-to-unit combat is replaced.
     */
    takeDamage(damageAmount, sourceUnitId, simulation, damageType = null) {
        const { entityManager, seedRandom } = simulation;

        let currentHealthState = entityManager.getUnitHealth(this.id);
        let currentShieldState = entityManager.getShield(this.id);
        let armor = entityManager.getArmor(this.id) || 0;

        if (!currentHealthState) return;

        let remainingDamage = damageAmount;

        // Energy Shield Logic
        if (currentShieldState && currentShieldState.current > 0) {
            // To get attacker's weaponEnergyCost, we need its type definition.
            // This requires mapping sourceUnitId to its type string, then to the type object.
            let attackerWeaponEnergyCost = SHIELD_EFFECTIVENESS_WATTAGE_BASELINE; // Default
            if (sourceUnitId) {
                const sourceUnitTypeString = entityManager.getUnitType(sourceUnitId);
                if (sourceUnitTypeString && unitTypes_js_1.UNIT_TYPES[sourceUnitTypeString] && unitTypes_js_1.UNIT_TYPES[sourceUnitTypeString].weaponEnergyCost) {
                    attackerWeaponEnergyCost = unitTypes_js_1.UNIT_TYPES[sourceUnitTypeString].weaponEnergyCost;
                }
            }

            let shieldEffectiveness = attackerWeaponEnergyCost / SHIELD_EFFECTIVENESS_WATTAGE_BASELINE;
            shieldEffectiveness = Math.max(0.1, shieldEffectiveness);
            const damageToDealToShields = remainingDamage * shieldEffectiveness;

            let newShieldHP = currentShieldState.current;
            if (damageToDealToShields >= newShieldHP) {
                remainingDamage -= newShieldHP / shieldEffectiveness;
                newShieldHP = 0;
            } else {
                newShieldHP -= damageToDealToShields;
                remainingDamage = 0;
            }
            entityManager.setShield(this.id, newShieldHP, currentShieldState.max);
        }

        if (remainingDamage > 0 && armor > 0) {
            remainingDamage = Math.max(0, remainingDamage - armor);
        }

        let newHp = currentHealthState.hp;
        if (remainingDamage > 0) {
            newHp -= remainingDamage;
        }

        if (newHp <= 0) {
            newHp = 0;
            entityManager.setUnitIsActive(this.id, false);
            // gameState.addEvent('death', `${this.type.name} destroyed!`); // Event for death
        }
        entityManager.setUnitHealth(this.id, newHp, currentHealthState.maxHp);

        const currentPos = entityManager.getUnitPosition(this.id);
        if (currentPos && damageAmount > 0 && seedRandom && seedRandom.random() < 0.2) {
            if (newHp <= 0) {
                // Death caption/effect handled by observing systems
            } else if (newHp < currentHealthState.maxHp * 0.3) {
                entityManager.addCaption(new caption_js_1.Caption(currentPos.x, currentPos.y, 'Critical damage!', '#f00', 10));
            } else if (damageAmount > 30) {
                entityManager.addCaption(new caption_js_1.Caption(currentPos.x, currentPos.y, `${Math.floor(damageAmount)}!`, '#f88', 9));
            }
        }
    }
    // draw method does not use gameContext/simulation for sim logic, only for camera/canvas info. No change needed.
    // draw(ctx, camera, gameContext) { ... }
    handleCombatPositioning(distToTarget, simulation) { // distToTarget is a number
        const { entityManager } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        const targetPos = this.targetId ? entityManager.getUnitPosition(this.targetId) : null;

        if (!currentPos || !targetPos) { // If current unit or target is gone, stop.
            this.vx = 0; this.vy = 0;
            return;
        }
        const currentEnergyState = entityManager.getEnergy(this.id); // Fetch current energy for weapon cost check
        if(!currentEnergyState) return; // No energy component

        let frameAngle = this.angle || 0; // this.angle is transient

        const inRange = distToTarget <= (this.type.range || 0); // Use static type range
        const preferredRange = (this.type.range || 0) * 0.8; // Use static type range
        const tooClose = distToTarget < preferredRange * 0.5;

        if (tooClose && this.tacticalRole === 'sniper') {
            frameAngle = Math.atan2(currentPos.y - targetPos.y, currentPos.x - targetPos.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(frameAngle) * currentSpeed * 0.5;
            this.vy = Math.sin(frameAngle) * currentSpeed * 0.5;
        } else if (!inRange) {
            frameAngle = Math.atan2(targetPos.y - currentPos.y, targetPos.x - currentPos.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(frameAngle) * currentSpeed;
            this.vy = Math.sin(frameAngle) * currentSpeed;
        } else { // In range
            this.vx = 0;
            this.vy = 0;
            const targetHealth = entityManager.getUnitHealth(this.targetId);
            if (this.cooldown <= 0 && this.targetId && targetHealth && targetHealth.hp > 0) {
                const energyCost = this.type.weaponEnergyCost || 0; // static type
                if (currentEnergyState.current >= energyCost) {
                    this.attack(this.targetId, simulation);
                    entityManager.setEnergy(this.id, currentEnergyState.current - energyCost, currentEnergyState.max);
                    this.cooldown = this.type.attackSpeed || 1.0; // static type
                    this.lastFireTime = Date.now();
                }
                else {
                    // Insufficient energy, cannot fire. Cooldown does not reset.
                    // Optional: log low energy: console.log(`Unit ${this.id} low energy for weapon.`);
                }
            }
        }
    }
    updateTacticalBehavior(simulation) { /* Renamed gameContext */ this.executeAgglomeration(simulation); this.executeGroupMovement(simulation); this.executeUnitInteractions(simulation); this.executeRepositioning(simulation); }
    executeAgglomeration(simulation) {
        const units = simulation.entityManager.units;
        if (!units || !Array.isArray(units))
            return;
        const teamUnits = units.filter(u => u.team === this.team);
        if (teamUnits.length <= 1)
            return;
        // Calculate center of mass for team units
        let centerX = 0, centerY = 0;
        for (const unit of teamUnits) {
            centerX += unit.x;
            centerY += unit.y;
        }
        centerX /= teamUnits.length;
        centerY /= teamUnits.length;
        const distToCenter = Math.sqrt(Math.pow((this.x - centerX), 2) + Math.pow((this.y - centerY), 2));
        if (!this.target && !this.isEscaping && distToCenter > 100) {
            this.angle = Math.atan2(centerY - this.y, centerX - this.x);
            const currentSpeed = this.getCurrentSpeed(simulation);
            this.vx = Math.cos(this.angle) * currentSpeed * 0.3; // Slower movement toward group
            this.vy = Math.sin(this.angle) * currentSpeed * 0.3;
        }
    }
    executeGroupMovement(simulation) {
        const units = simulation.entityManager.units;
        if (!units || !Array.isArray(units))
            return;
        const teamUnits = units.filter(u => u.team === this.team);
        if (teamUnits.length <= 1)
            return;
        // Find group leader (highest authority unit in vicinity)
        const nearbyUnits = teamUnits.filter(u => {
            const dist = Math.sqrt(Math.pow((u.x - this.x), 2) + Math.pow((u.y - this.y), 2));
            return dist < 200 && u !== this;
        });
        const groupLeader = nearbyUnits.reduce((leader, unit) => {
            return (!leader || unit.commandAuthority > leader.commandAuthority) ? unit : leader;
        }, null);
        if (groupLeader && groupLeader !== this) {
            const leaderDist = Math.sqrt(Math.pow((groupLeader.x - this.x), 2) + Math.pow((groupLeader.y - this.y), 2));
            const currentSpeed = this.getCurrentSpeed(simulation);
            if (leaderDist > 80) {
                // Move towards leader if too far
                this.angle = Math.atan2(groupLeader.y - this.y, groupLeader.x - this.x);
                this.vx = Math.cos(this.angle) * currentSpeed * 0.8;
                this.vy = Math.sin(this.angle) * currentSpeed * 0.8;
            }
            else if (leaderDist < 60 && leaderDist > 20) {
                // Maintain formation spacing
                const spreadAngle = this.angle + (simulation.seedRandom.random() - 0.5) * Math.PI / 2;
                this.vx = Math.cos(spreadAngle) * currentSpeed * 0.4;
                this.vy = Math.sin(spreadAngle) * currentSpeed * 0.4;
            }
        }
    }
    executeUnitInteractions(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeRepositioning(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    updateSurvivalBehaviors(simulation) { /* Renamed gameContext */ if (Date.now() - this.lastThreatAssessment > 2000) {
        this.assessThreats(simulation);
        this.lastThreatAssessment = Date.now();
    } if (this.hp < this.fleeThreshold && !this.isEscaping) {
        this.executeTacticalRetreat(simulation);
    } if (this.type.support && this.shields < this.maxShields * 0.5) {
        this.seekProtection(simulation);
    } }
    assessThreats(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeTacticalRetreat(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    seekProtection(simulation) { /* Renamed gameContext */ const units = simulation.entityManager.units; /* ... */ }
    executeCommandHierarchy(simulation) { /* Renamed gameContext */ if (this.militaryRank === 'GENERAL' || this.militaryRank === 'COLONEL') {
        this.issueStrategicOrders(simulation);
    } if (this.commandAuthority < 50) {
        this.followSuperiorOrders(simulation);
    } }
    issueStrategicOrders(simulation) {
        const { entityManager } = simulation;
        const currentPos = entityManager.getUnitPosition(this.id);
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        if (!currentPos || !ownerTeam) return;

        // TODO: Needs EM iteration for units of the same team
        // const teamUnits = entityManager.getEntitiesByFilter(e => e.team === ownerTeam && e.id !== this.id);
        const teamUnits = []; // Placeholder

        const subordinates = teamUnits.filter(unitData => { // unitData from EM {id, position, ...}
            if (!unitData.position) return false;
            const distance = this.getDistanceToCoords(currentPos, unitData.position);
            // Assuming commandAuthority is a static type property or fetched if dynamic
            const subCommandAuthority = unitData.type ? unitData.type.commandRank : 0; // Conceptual
            return distance < 300 && subCommandAuthority < this.commandAuthority;
        });

        for (const subData of subordinates) { // subData from EM
            const subHealth = entityManager.getUnitHealth(subData.id);
            // sub.isEscaping would need to be a component in EM or a transient state on Unit objects (if they exist)
            if (subHealth && subHealth.hp < subHealth.maxHp * 0.3 /* && !subData.isEscaping */) {
                // subData.protectionNeeds.push('COMMANDER_RETREAT_ORDER'); // Needs component or system
                // subData.executeTacticalRetreat(simulation); // Would be a system call: simulation.systems.ai.orderRetreat(subData.id)
            }

            const subTargetId = entityManager.getTargetId(subData.id); // Conceptual getter
            if (!subTargetId && this.targetId) {
                 // simulation.systems.ai.orderAttackTarget(subData.id, this.targetId);
            }
        }
    }
    calculateSeparationForce(gameContext) { // gameContext is simulation
        let totalSeparationForce = { x: 0, y: 0 };
        const { entityManager } = gameContext;
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!currentPos) return totalSeparationForce;

        // TODO: Needs EM iteration for ALL other units
        // const otherUnits = entityManager.getEntitiesByFilter(e => e.id !== this.id);
        const otherUnits = []; // Placeholder

        const separationRadius = (this.type.size || 10) * commandConfig_js_1.COMMAND_CONFIG.NEIGHBOR_RADIUS_FACTOR;

        otherUnits.forEach(otherUnitData => { // otherUnitData from EM {id, position}
            if (!otherUnitData.position) return;
            const dist = this.getDistanceToCoords(currentPos, otherUnitData.position);
            if (dist > 0 && dist < separationRadius) {
                let forceX = currentPos.x - otherUnitData.position.x;
                let forceY = currentPos.y - otherUnitData.position.y;
                forceX = (forceX / dist) * (separationRadius / dist);
                forceY = (forceY / dist) * (separationRadius / dist);
                totalSeparationForce.x += forceX;
                totalSeparationForce.y += forceY;
            }
        });
        return totalSeparationForce;
    }
    calculateTerrainAvoidanceForce(gameContext) { // gameContext is simulation
        let totalAvoidanceForce = { x: 0, y: 0 };
        const { entityManager } = gameContext;
        const currentPos = entityManager.getUnitPosition(this.id);
        // this.angle is transient, representing current frame's intended direction
        const frameAngle = this.angle || 0;
        if (!currentPos) return totalAvoidanceForce;

        const feelerLength = (this.type.size || 10) * commandConfig_js_1.COMMAND_CONFIG.STEERING_FEELER_LENGTH_FACTOR;
        const feelerEndX = currentPos.x + Math.cos(frameAngle) * feelerLength;
        const feelerEndY = currentPos.y + Math.sin(frameAngle) * feelerLength;

        const gridX = Math.floor(feelerEndX / gameConstants_js_1.TILE_SIZE);
        const gridY = Math.floor(feelerEndY / gameConstants_js_1.TILE_SIZE);

        if (!(0, astar_js_2.isTraversable)(gridX, gridY, gameContext.gameContext, this.type.movementType)) {
            let avoidanceX = currentPos.x - feelerEndX;
            let avoidanceY = currentPos.y - feelerEndY;
            const distToObstaclePoint = Math.sqrt(avoidanceX * avoidanceX + avoidanceY * avoidanceY);
            if (distToObstaclePoint > 0) {
                totalAvoidanceForce.x = (avoidanceX / distToObstaclePoint) * this.maxForce;
                totalAvoidanceForce.y = (avoidanceY / distToObstaclePoint) * this.maxForce;
            }
        }
        return totalAvoidanceForce;
    }
    followSuperiorOrders(gameContext) { // gameContext is simulation
        const { entityManager } = gameContext;
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        if (!ownerTeam) return;

        let commanderId = null; // Store ID
        let highestEffectiveAuthority = 0;

        // TODO: Needs EM iteration for ALL other units to find allies
        // const allies = entityManager.getEntitiesByFilter(e => e.team === ownerTeam && e.id !== this.id);
        const allies = []; // Placeholder

        for (const allyData of allies) { // allyData from EM {id, position, effectiveAuthority, commandFitness}
            if (!allyData.position) continue;
            // Assuming effectiveAuthority and commandFitness are fetched/calculated for allyData
            // For now, these would be undefined or need to be fetched if not part of basic allyData from EM.
            // This part requires ally's effective authority and command fitness.
            // Let's assume they are available on allyData for conceptual progress.
            // allyData.effectiveAuthority = ... (fetch or pre-calculated by a system)
            // allyData.commandFitness = ... (fetch or pre-calculated)

            if (this.getDistanceToCoords(entityManager.getUnitPosition(this.id), allyData.position) < commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.TACTICAL) {
                if (allyData.effectiveAuthority > this.effectiveAuthority &&
                    allyData.effectiveAuthority > highestEffectiveAuthority &&
                    allyData.commandFitness === 'FULL_COMMAND') {
                    highestEffectiveAuthority = allyData.effectiveAuthority;
                    commanderId = allyData.id;
                }
            }
        }

        if (commanderId) {
            this.currentCommander = commanderId; // Store ID
            const commanderTargetId = entityManager.getTargetId(commanderId); // Conceptual: get commander's target
            const commanderPos = entityManager.getUnitPosition(commanderId);

            if (commanderTargetId && !this.targetId) {
                const commanderTargetPos = entityManager.getUnitPosition(commanderTargetId);
                if (commanderTargetPos && this.getDistanceToCoords(entityManager.getUnitPosition(this.id), commanderTargetPos) < (this.type.range || 100) * 2) {
                    // this.targetId = commanderTargetId; // Set own target to commander's target
                    // This should be an order from a command system:
                    // gameContext.systems.command.orderAttackTarget(this.id, commanderTargetId);
                }
            }

            if (commanderPos) {
                const commanderEffectiveAuthority = highestEffectiveAuthority; // Already found
                const authorityGap = commanderEffectiveAuthority - this.effectiveAuthority;
                const formationDistance = Math.max(commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MIN_DISTANCE, Math.min(commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MAX_DISTANCE, commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.FORMATION_MIN_DISTANCE + authorityGap * 2));
                if (this.getDistanceToCoords(entityManager.getUnitPosition(this.id), commanderPos) > formationDistance + 50 && !this.targetId) {
                    this.patrolTarget = { x: commanderPos.x, y: commanderPos.y };
                }
            }
        } else {
            this.currentCommander = null;
        }
    }
    executeGroupMovement(gameContext) { // gameContext is simulation
        const { entityManager } = gameContext;
        const currentPos = entityManager.getUnitPosition(this.id);
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        if (!currentPos || !ownerTeam) return;

        let frameAngle = this.angle || 0; // Transient angle for steering

        let groupLeaderId = null;
        let highestEffectiveAuthority = this.effectiveAuthority;
        if (this.commandFitness !== 'FULL_COMMAND') {
            highestEffectiveAuthority = -1;
        }

        // TODO: Needs EM iteration for allies
        // const allies = entityManager.getEntitiesByFilter(e => e.team === ownerTeam);
        const allies = []; // Placeholder

        for (const allyData of allies) { // allyData from EM {id, position, effectiveAuthority, commandFitness}
            if (!allyData.position) continue;
            // Assuming effectiveAuthority & commandFitness are available on allyData
            if (this.getDistanceToCoords(currentPos, allyData.position) < commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC) {
                if (allyData.effectiveAuthority > highestEffectiveAuthority && allyData.commandFitness === 'FULL_COMMAND') {
                    highestEffectiveAuthority = allyData.effectiveAuthority;
                    groupLeaderId = allyData.id;
                }
            }
        }

        if (groupLeaderId === this.id) {
            groupLeaderId = null;
        }

        this.steering.x = 0; this.steering.y = 0;

        if (!groupLeaderId) { // This unit is a LEADER or independent
            if (this.path && this.path[this.currentWaypointIndex]) {
                this.leaderTargetPosition = this.path[this.currentWaypointIndex];
            } else if (this.patrolTarget) {
                this.leaderTargetPosition = this.patrolTarget;
            } else {
                this.leaderTargetPosition = null;
            }
            this.leaderPredictedPosition = null;
            this.idealFormationSlotWorld = null;
        } else { // This unit is a FOLLOWER
            this.patrolTarget = null; this.path = null;
            const groupLeaderPos = entityManager.getUnitPosition(groupLeaderId);
            const groupLeaderVelocity = entityManager.getVelocity(groupLeaderId); // Conceptual: {vx, vy}
            if(!groupLeaderPos || !groupLeaderVelocity) return;


            const distanceToLeader = this.getDistanceToCoords(currentPos, groupLeaderPos);
            const maxSeparationDistance = commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC * commandConfig_js_1.COMMAND_CONFIG.FORMATION_RULES.MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR;

            if (distanceToLeader > maxSeparationDistance) {
                this.path = (0, astar_js_1.findPath)(currentPos, groupLeaderPos, gameContext.gameContext, this.type.movementType);
                this.currentWaypointIndex = 0;
                this.patrolTarget = { x: groupLeaderPos.x, y: groupLeaderPos.y };
                this.idealFormationSlotWorld = null; this.leaderPredictedPosition = null; this.steering = { x: 0, y: 0 };
                return;
            }

            const predictionTime = commandConfig_js_1.COMMAND_CONFIG.FORMATION_BEHAVIOR.PREDICTION_TIME_SECONDS;
            this.leaderPredictedPosition = {
                x: groupLeaderPos.x + groupLeaderVelocity.vx * predictionTime,
                y: groupLeaderPos.y + groupLeaderVelocity.vy * predictionTime
            };

            const baseFormationDistance = commandConfig_js_1.COMMAND_CONFIG.FORMATION_BEHAVIOR.MIN_FORMATION_SLOT_DISTANCE;
            const offsetX = Math.cos(this.formationAngle || 0) * baseFormationDistance;
            const offsetY = Math.sin(this.formationAngle || 0) * baseFormationDistance;
            this.idealFormationSlotWorld = {
                x: this.leaderPredictedPosition.x + offsetX,
                y: this.leaderPredictedPosition.y + offsetY
            };

            let desiredVelocityX = this.idealFormationSlotWorld.x - currentPos.x;
            let desiredVelocityY = this.idealFormationSlotWorld.y - currentPos.y;
            const distToSlot = Math.sqrt(desiredVelocityX * desiredVelocityX + desiredVelocityY * desiredVelocityY);
            const currentActualSpeed = this.getCurrentSpeed(gameContext);
            const arrivalRadius = (this.type.size || 10) * commandConfig_js_1.COMMAND_CONFIG.FORMATION_BEHAVIOR.ARRIVAL_RADIUS_FACTOR;

            if (distToSlot > arrivalRadius) {
                desiredVelocityX /= distToSlot; desiredVelocityY /= distToSlot;
                const slowingRadius = (this.type.size || 10) * 2;
                if (distToSlot < slowingRadius) {
                    desiredVelocityX *= currentActualSpeed * (distToSlot / slowingRadius);
                    desiredVelocityY *= currentActualSpeed * (distToSlot / slowingRadius);
                } else {
                    desiredVelocityX *= currentActualSpeed; desiredVelocityY *= currentActualSpeed;
                }
            } else {
                desiredVelocityX = 0; desiredVelocityY = 0;
            }

            // Read current velocity (vx, vy) from EM if it's stored, or use transient this.vx, this.vy
            // Assuming this.vx, this.vy are updated each frame and represent current velocity for steering.
            let currentVx = this.vx;
            let currentVy = this.vy;

            let seekForceX = desiredVelocityX - currentVx;
            let seekForceY = desiredVelocityY - currentVy;
            this.steering.x += seekForceX; this.steering.y += seekForceY;

            const separationForce = this.calculateSeparationForce(gameContext);
            this.steering.x += separationForce.x * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;
            this.steering.y += separationForce.y * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.SEPARATION;

            const terrainAvoidanceForce = this.calculateTerrainAvoidanceForce(gameContext);
            this.steering.x += terrainAvoidanceForce.x * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;
            this.steering.y += terrainAvoidanceForce.y * commandConfig_js_1.COMMAND_CONFIG.STEERING_WEIGHTS.TERRAIN_AVOIDANCE;

            const steerMag = Math.sqrt(this.steering.x * this.steering.x + this.steering.y * this.steering.y);
            if (steerMag > this.maxForce) {
                this.steering.x = (this.steering.x / steerMag) * this.maxForce;
                this.steering.y = (this.steering.y / steerMag) * this.maxForce;
            }

            this.vx += this.steering.x; this.vy += this.steering.y; // Update transient vx, vy

            const velMag = Math.sqrt(this.vx * this.vx + this.vy * this.vy);
            if (velMag > currentActualSpeed) {
                this.vx = (this.vx / velMag) * currentActualSpeed;
                this.vy = (this.vy / velMag) * currentActualSpeed;
            }

            if (Math.abs(this.vx) > 0.01 || Math.abs(this.vy) > 0.01) {
                let targetAngle = Math.atan2(this.vy, this.vx); // Calculated from new vx, vy
                let angleDiff = targetAngle - frameAngle; // frameAngle is previous orientation
                while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
                while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
                const turnThisFrame = Math.min(Math.abs(angleDiff), this.maxTurnRate);
                frameAngle += (angleDiff > 0 ? turnThisFrame : -turnThisFrame);
                while (frameAngle > Math.PI) frameAngle -= 2 * Math.PI;
                while (frameAngle < -Math.PI) frameAngle += 2 * Math.PI;
            }
             this.angle = frameAngle; // Update transient angle for next frame or rendering
        }
    }
    updateStuckDetection(simulation) {
        const currentPos = simulation.entityManager.getUnitPosition(this.id);
        if (!currentPos) return;

        if (!this.lastPositionForStuckCheck) { // Initialize on first run
            this.lastPositionForStuckCheck = { x: currentPos.x, y: currentPos.y };
        }

        const dxMoved = currentPos.x - this.lastPositionForStuckCheck.x;
        const dyMoved = currentPos.y - this.lastPositionForStuckCheck.y;
        const distanceMoved = Math.sqrt(dxMoved * dxMoved + dyMoved * dyMoved);

        // targetId is used instead of this.target
        const wasTryingToMove = (this.targetId || this.patrolTarget || this.isEscaping || this.vx !== 0 || this.vy !== 0);

        if (wasTryingToMove && distanceMoved < this.significantMoveThreshold) {
            this.stuckFrames++;
        } else {
            this.stuckFrames = 0;
        }
        this.lastPositionForStuckCheck = { x: currentPos.x, y: currentPos.y };

        if (this.stuckFrames > this.STUCK_FRAMES_THRESHOLD && !this.isEscaping) {
            this.isEscaping = true;
            // this.angle is transient, if angle needs to be read from EM, it should be fetched.
            // For escapeAngle calculation, using the transient this.angle (last calculated orientation) is acceptable.
            this.escapeAngle = (this.angle || 0) + (simulation.seedRandom.random() < 0.5 ? Math.PI / 2 : -Math.PI / 2);
            this.escapeDuration = this.ESCAPE_MODE_DURATION_FRAMES / 60; // Convert frames to seconds assuming 60fps baseline for original logic
        }
    }
    determineTacticalRole() {
        // Determine tactical role based on unit type and characteristics
        if (this.type.range > 150) {
            return 'sniper';
        }
        else if (this.type.speed > 60) {
            return 'scout';
        }
        else if (this.type.maxHp > 200) {
            return 'tank';
        }
        else if (this.type.support) {
            return 'support';
        }
        else {
            return 'assault';
        }
    }
    determineMilitaryRank() {
        // Determine military rank based on unit type and tier
        if (this.type === unitTypes_js_1.UNIT_TYPES.commander) {
            return 'GENERAL';
        }
        else if (this.type.tier >= 3) {
            return 'COLONEL';
        }
        else if (this.type.tier >= 2) {
            return 'MAJOR';
        }
        else if (this.type.support) {
            return 'LIEUTENANT';
        }
        else {
            return 'SERGEANT';
        }
    }
    calculateSurvivalPriority() {
        // Calculate survival priority based on unit importance
        let priority = this.type.tier * 10;
        if (this.type === unitTypes_js_1.UNIT_TYPES.commander) {
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
        }
        else {
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
        const healthState = this.simulation.entityManager.getUnitHealth(this.id);
        if (!healthState || healthState.maxHp === 0) { // Prevent division by zero if maxHp is 0
            this.healthAuthorityModifier = -10; // Or some other default for invalid health state
            this.commandFitness = 'COMBAT_INEFFECTIVE';
            // Set effectiveAuthority to a baseline or minimum
            this.effectiveAuthority = this.baseAuthority + this.healthAuthorityModifier + this.veterancyAuthorityModifier + this.contextAuthorityModifier + this.computroniumAuthorityModifier;
            return this.effectiveAuthority;
        }
        const healthRatio = healthState.hp / healthState.maxHp;

        if (healthRatio >= commandConfig_js_1.COMMAND_CONFIG.HEALTH_THRESHOLDS.FULL_COMMAND) {
            this.healthAuthorityModifier = 5; // Per guide, direct value, not COMMAND_CONFIG.AUTHORITY_WEIGHTS.HEALTH_MAX_MODIFIER
            this.commandFitness = 'FULL_COMMAND';
        }
        else if (healthRatio >= commandConfig_js_1.COMMAND_CONFIG.HEALTH_THRESHOLDS.REDUCED_AUTHORITY) {
            this.healthAuthorityModifier = 2;
            this.commandFitness = 'REDUCED_AUTHORITY';
        }
        else if (healthRatio >= commandConfig_js_1.COMMAND_CONFIG.HEALTH_THRESHOLDS.COMPROMISED_COMMAND) {
            this.healthAuthorityModifier = -2;
            this.commandFitness = 'COMPROMISED_COMMAND';
        }
        else if (healthRatio >= commandConfig_js_1.COMMAND_CONFIG.HEALTH_THRESHOLDS.CRITICAL_STATUS) {
            this.healthAuthorityModifier = -5;
            this.commandFitness = 'CRITICAL_STATUS';
        }
        else { // Effectively healthRatio < COMMAND_CONFIG.HEALTH_THRESHOLDS.CRITICAL_STATUS
            this.healthAuthorityModifier = -10;
            this.commandFitness = 'COMBAT_INEFFECTIVE';
        }
        // Veterancy bias calculation
        const experiencePoints = this.combatExperience +
            (this.survivalTime / 60) +
            (this.commandExperience * 3) +
            (this.killCount * 2);
        if (experiencePoints >= commandConfig_js_1.COMMAND_CONFIG.VETERANCY_THRESHOLDS.HERO) {
            this.veterancyLevel = 'HERO';
            this.veterancyAuthorityModifier = 15; // Per guide, direct value, not COMMAND_CONFIG.AUTHORITY_WEIGHTS.VETERANCY_MAX_MODIFIER
        }
        else if (experiencePoints >= commandConfig_js_1.COMMAND_CONFIG.VETERANCY_THRESHOLDS.ELITE) {
            this.veterancyLevel = 'ELITE';
            this.veterancyAuthorityModifier = 10;
        }
        else if (experiencePoints >= commandConfig_js_1.COMMAND_CONFIG.VETERANCY_THRESHOLDS.VETERAN) {
            this.veterancyLevel = 'VETERAN';
            this.veterancyAuthorityModifier = 5;
        }
        else if (experiencePoints >= commandConfig_js_1.COMMAND_CONFIG.VETERANCY_THRESHOLDS.REGULAR) {
            this.veterancyLevel = 'REGULAR';
            this.veterancyAuthorityModifier = 2;
        }
        else {
            this.veterancyLevel = 'GREEN';
            this.veterancyAuthorityModifier = 0;
        }
        // Placeholder for Computronium-based C&C modifier calculation
        // this.computroniumAuthorityModifier = 0; // This is already set in the constructor and updated by updateComputroniumModifiers
        // Calculate final effective authority
        // Incorporate contextAuthorityModifier and computroniumAuthorityModifier into the calculation
        this.effectiveAuthority = this.baseAuthority +
            this.healthAuthorityModifier +
            this.veterancyAuthorityModifier +
            this.contextAuthorityModifier + // Now included
            this.computroniumAuthorityModifier; // Now included
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
        this.autonomousBehavior = new autonomousBehavior_js_1.AutonomousBehavior(this, gameContext);
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
        const nearbyAllies = units.filter(u => u.team === this.team &&
            u !== this &&
            this.getDistance(u) < commandConfig_js_1.COMMAND_CONFIG.COMMAND_RANGES.TACTICAL &&
            u.commandFitness === 'FULL_COMMAND');
        if (nearbyAllies.length === 0)
            return;
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
        const subordinates = units.filter(u => u.team === this.team &&
            u.currentCommander === this);
        subordinates.forEach(subordinate => {
            subordinate.currentCommander = newCommander;
            subordinate.lastCommandChange = performance.now();
        });
        // Update command experience
        newCommander.commandExperience += subordinates.length;
        this.commandFailures += 1; // Failed to maintain command
        // Visual feedback
        // Caption class is imported at the top of the file
        addCaption(new caption_js_1.Caption(newCommander.x, newCommander.y, `Command transferred to ${newCommander.veterancyLevel} ${newCommander.type.name}`, '#ff4', 14));
        // Log succession event
        if (gameState && typeof gameState.addEvent === 'function') {
            gameState.addEvent('command_succession', `${this.team} command transferred due to combat ineffectiveness`, 2);
        }
    }
    // Placeholder method for veterancy progress (Section 1.3 / 3.1)
    updateVeterancyProgress(gameContext) { // gameContext is simulation
        const { entityManager } = gameContext;
        const deltaTime = gameContext.gameSpeedManager ? (gameContext.gameSpeedManager.deltaTime || (1 / 60)) : (1 / 60);

        const healthState = entityManager.getUnitHealth(this.id);
        // isUnderFire would need to be a component or a system query
        // const isUnderFire = entityManager.getUnitComponent(this.id, 'isUnderFire');
        const inCombat = this.targetId || (healthState && healthState.hp < healthState.maxHp) /* || isUnderFire */;

        if (inCombat) {
            this.survivalTime += deltaTime; // survivalTime is instance specific tracking
        }

        const oldLevel = this.veterancyLevel; // Instance specific tracking
        this.calculateEffectiveAuthority();
        if (oldLevel !== this.veterancyLevel) {
            this.processPromotion(oldLevel, gameContext);
        }
    }
    processPromotion(oldLevel, gameContext) { // gameContext is simulation
        const { entityManager, gameState } = gameContext;
        const currentPos = entityManager.getUnitPosition(this.id);
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        if(!currentPos || !ownerTeam) return;

        const now = performance.now();
        if (now - this.lastPromotionTime < commandConfig_js_1.COMMAND_CONFIG.UPDATE_INTERVALS.PROMOTION_COOLDOWN)
            return;
        this.lastPromotionTime = now;

        const newLevel = this.getVeterancyLevel(); // Based on instance's combatExperience etc.
        if (newLevel !== oldLevel) {
            console.log(`[Unit] ${this.type.name} ${this.id} promoted from ${oldLevel} to ${newLevel}`);
            this.applyVeterancyBenefits(); // This will now internally use EM for stat changes
            this.updateVeterancyAuthorityModifier(); // This updates instance props, then calculateEffectiveAuthority uses EM

            if (this.onPromotion) {
                this.onPromotion(this.id, oldLevel, newLevel); // Pass ID instead of `this`
            }

            entityManager.addCaption(new caption_js_1.Caption(currentPos.x, currentPos.y, `${this.type.name} promoted to ${this.veterancyLevel}!`, '#4f4', 16));
            gameState.addEvent('promotion', `${ownerTeam} ${this.type.name} promoted to ${this.veterancyLevel}`, 2);
        }
    }
    applyVeterancyBenefits() {
        // These benefits should modify stats in EntityManager, not local 'this' properties.
        // For example, instead of this.maxHp *= 1.1, it should be:
        // const currentHealth = entityManager.getUnitHealth(this.id);
        // const newMaxHp = (currentHealth?.maxHp || this.type.maxHp) * 1.1; // Base on current max or type default
        // entityManager.setUnitHealth(this.id, newMaxHp, newMaxHp); // Assuming HP is set to new maxHp
        // Similar for damage, speed (if dynamic), range (if dynamic), armor.
        // This requires these stats to be components in EntityManager.
        // For this subtask, I will comment out direct modifications and note where EM calls would go.

        // const baseDamage = this.type.damage; // Static
        // const baseSpeed = this.type.speed || DEFAULT_UNIT_SPEED; // Static
        // const baseRange = this.type.range; // Static

        this.canPromoteSubordinates = false; // This is a flag, can remain on instance if not needed by other systems
        this.provideMoraleBonus = false;   // Same as above

        let hpFactor = 1.0;
        // let damageFactor = 1.0; // If damage is read from EM, set it there
        // let speedFactor = 1.0; // If speed is read from EM, set it there
        // let rangeFactor = 1.0; // If range is read from EM, set it there
        // let armorFactor = 1.0; // If armor is read from EM, set it there
        // let shieldRegenFactor = 1.0; // If shieldRegen is read from EM, set it there
        // let coreEfficiencyFactor = 1.0; // If coreEfficiency is read from EM, set it there

        switch (this.veterancyLevel) {
            case 'REGULAR':
                hpFactor = 1.1;
                // damageFactor = 1.1; speedFactor = 1.05;
                break;
            case 'VETERAN':
                hpFactor = 1.15;
                // damageFactor = 1.2; speedFactor = 1.1; rangeFactor = 1.1; armorFactor = 1.1;
                this.provideMoraleBonus = true;
                break;
            case 'ELITE':
                hpFactor = 1.2;
                // damageFactor = 1.3; speedFactor = 1.15; rangeFactor = 1.2; armorFactor = 1.2;
                this.provideMoraleBonus = true;
                this.canPromoteSubordinates = true;
                break;
            case 'HERO':
                hpFactor = 1.3;
                // damageFactor = 1.4; speedFactor = 1.2; rangeFactor = 1.3; armorFactor = 1.3;
                // shieldRegenFactor = 1.5; coreEfficiencyFactor = 1.2;
                this.provideMoraleBonus = true;
                this.canPromoteSubordinates = true;
                break;
        }

        const currentHealth = this.simulation.entityManager.getUnitHealth(this.id);
        const oldMaxHp = currentHealth ? currentHealth.maxHp : (this.type.maxHp || 100); // Fallback to type default
        const newMaxHp = Math.round(oldMaxHp * hpFactor); // Apply factor to existing maxHp from EM or type default
                                                          // This assumes EntityFactory set initial maxHp correctly.
                                                          // If veterancy is reapplied, it should apply to the *base* maxHp from type.
                                                          // For simplicity now, applying to current maxHp.
        if (currentHealth) {
             this.simulation.entityManager.setUnitHealth(this.id, newMaxHp, newMaxHp); // Set current HP to new maxHP
        }

        // TODO: Apply damageFactor, speedFactor etc. to corresponding components in EntityManager
        // Example: this.simulation.entityManager.setAttackDamage(this.id, (this.type.damage || 0) * damageFactor);
        //          this.simulation.entityManager.setArmor(this.id, (this.type.armorValue || 0) * armorFactor);
        //          This assumes these stats are now stored in EM.

        // Update flee threshold based on new maxHp from EM
        const updatedHealth = this.simulation.entityManager.getUnitHealth(this.id);
        this.fleeThreshold = (updatedHealth ? updatedHealth.maxHp : oldMaxHp) * 0.2;
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
                modifier = commandConfig_js_1.COMMAND_CONFIG.AUTHORITY_WEIGHTS.VETERANCY_MAX_MODIFIER;
                break;
        }
        this.veterancyAuthorityModifier = modifier;
        this.calculateEffectiveAuthority();
    }
    // Add method to apply morale bonus to nearby units
    applyMoraleBonus(gameContext) { // gameContext is simulation
        if (!this.provideMoraleBonus) return;
        const { entityManager } = gameContext;
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        const currentPos = entityManager.getUnitPosition(this.id);
        if (!ownerTeam || !currentPos) return;

        // TODO: Needs EM iteration for allies
        // const teamUnits = entityManager.getEntitiesByFilter(e => e.team === ownerTeam && e.id !== this.id);
        const teamUnits = []; // Placeholder

        const MORALE_RANGE = 100;
        for (const allyData of teamUnits) { // allyData from EM {id, position}
            if (!allyData.position) continue;
            const distance = this.getDistanceToCoords(currentPos, allyData.position);
            if (distance <= MORALE_RANGE) {
                const distanceFactor = 1 - (distance / MORALE_RANGE);
                const bonus = 0.1 * distanceFactor;
                // Applying tempCombatBonus to another unit's instance is not stateless.
                // This should be a component on the ally unit, set via EM, or an effect.
                // For now, this part remains conceptual:
                // entityManager.setUnitComponent(allyData.id, 'tempCombatBonus', (entityManager.getUnitComponent(allyData.id, 'tempCombatBonus') || 0) + bonus);
                // entityManager.addEffect(new Effect(allyData.position.x, allyData.position.y, 'morale_buff_visual', gameContext));
            }
        }
    }
    // Add method to handle subordinate promotions
    handleSubordinatePromotions(gameContext) { // gameContext is simulation
        if (!this.canPromoteSubordinates) return;
        const { entityManager } = gameContext;
        const ownerTeam = entityManager.getOwnerTeam(this.id);
        const currentPos = entityManager.getUnitPosition(this.id);
         if (!ownerTeam || !currentPos) return;

        // TODO: Needs EM iteration for subordinates
        // const subordinates = entityManager.getEntitiesByFilter(e => e.team === ownerTeam && e.currentCommanderId === this.id);
        const subordinates = []; // Placeholder

        const PROMOTION_RANGE = 150;
        const PROMOTION_COOLDOWN = commandConfig_js_1.COMMAND_CONFIG.UPDATE_INTERVALS.PROMOTION_COOLDOWN;

        for (const subData of subordinates) { // subData from EM {id, position, lastPromotionTime, veterancyLevel, combatExperience}
             if (!subData.position) continue;
            // Assuming lastPromotionTime, veterancyLevel, combatExperience are available on subData (fetched from EM)
            if (Date.now() - (subData.lastPromotionTime || 0) > PROMOTION_COOLDOWN) {
                const distance = this.getDistanceToCoords(currentPos, subData.position);
                if (distance <= PROMOTION_RANGE) {
                    const nextThreshold = commandConfig_js_1.COMMAND_CONFIG.VETERANCY_THRESHOLDS[subData.veterancyLevel];
                    if (nextThreshold && subData.combatExperience / nextThreshold >= 0.9) {
                        const bonusExp = nextThreshold - subData.combatExperience + 1;
                        // This should be a call to a progression system that uses EM
                        // simulation.systems.progression.addExperience(subData.id, bonusExp);
                        // entityManager.addEffect(new Effect(subData.position.x, subData.position.y, 'promotion_visual', gameContext));
                        // entityManager.addCaption(new Caption(subData.position.x, subData.position.y - 30, 'Promoted by Commander!', '#FFD700', 2000, gameContext));
                    }
                }
            }
        }
    }
}
exports.Unit = Unit;

// --- Debugging and Validation Functions ---
// These functions operate on collections of Unit instances. They will need significant refactoring
// to work with an EntityManager by querying component data instead of direct property access.
// For this subtask, the focus is on refactoring the Unit class itself.
// These debug functions are indicative of how systems would interact with an EM.

function validateAuthoritySystem(gameContext) { // gameContext is simulation
    const { entityManager } = gameContext;
    // TODO: Refactor to use EM queries. For example, get all entities with AuthorityComponent.
    // const units = entityManager.getAllUnitsWithData(['id', 'team', 'effectiveAuthority', 'position', 'currentCommanderId', 'commandFitness']);
    console.warn("validateAuthoritySystem needs full refactor to use EntityManager queries.");
    return {};
}

function renderCommandHierarchyDebug(ctx, gameContext) { // gameContext is simulation
    const { entityManager } = gameContext;
    const camera = gameContext.camera;
    if (!camera) return;

    // TODO: Refactor to use EM queries. Get all entities with relevant components for rendering.
    // const renderableUnits = entityManager.getAllUnitsWithData(['id', 'position', 'effectiveAuthority', 'veterancyLevel', 'currentCommanderId', 'idealFormationSlotWorld', 'leaderPredictedPosition']);
    console.warn("renderCommandHierarchyDebug needs full refactor to use EntityManager queries.");
}

if (typeof window !== 'undefined') {
    window.debugRTS = {
        validateAuthoritySystem,
        renderCommandHierarchyDebug
    };
}
