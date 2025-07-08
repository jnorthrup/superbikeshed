// Enhanced Strategic AI - Aggressive economic expansion and tactical coordination
// Designed to create engaging battles with lots of action for replay recording

const ASSET_SCORE_HEALTH_K_FACTOR = 0.2;
const TARGET_SCORE_DISTANCE_DIVISOR = 50;
const MIN_POWER_RATIO_TO_ENGAGE_ENEMY = 0.5; // Group power should be at least 50% of target's asset score.

import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { UNIT_TYPES } from '../config/unitTypes.js';
import { findLandPosition } from '../core/terrain.js';
import { Building } from '../core/building.js';
import { Caption } from '../core/entities/caption.js';
// Removed incorrect import of battleJournal. It will be accessed via gameContext (simulation instance).

// AI personality profiles for varied gameplay
const AI_PERSONALITIES = {
    AGGRESSIVE: {
        economicPriority: 0.3,
        militaryPriority: 0.7,
        raidFrequency: 0.15,
        expansionRate: 0.4,
        attackThreshold: 5
    },
    ECONOMIC: {
        economicPriority: 0.7,
        militaryPriority: 0.3,
        raidFrequency: 0.05,
        expansionRate: 0.8,
        attackThreshold: 12
    },
    BALANCED: {
        economicPriority: 0.5,
        militaryPriority: 0.5,
        raidFrequency: 0.1,
        expansionRate: 0.6,
        attackThreshold: 8
    }
};

// Global AI state tracking
const aiState = {
    blue: {
        personality: AI_PERSONALITIES.BALANCED,
        lastMajorDecision: 0,
        economicPhase: 'bootstrap',
        militaryStrategy: 'defensive',
        targetPriorities: [],
        expansionTargets: [],
        raidCooldown: 0
    },
    red: {
        personality: AI_PERSONALITIES.AGGRESSIVE,
        lastMajorDecision: 0,
        economicPhase: 'bootstrap',
        militaryStrategy: 'aggressive',
        targetPriorities: [],
        expansionTargets: [],
        raidCooldown: 0
    }
};

export function makeStrategicDecisions(gameContext) {
    // Add null checks for units and buildings arrays
    if (!gameContext.units || !Array.isArray(gameContext.units) ||
        !gameContext.buildings || !Array.isArray(gameContext.buildings)) {
        return; // Exit early if arrays are not properly initialized
    }
    
    for (const team of ['blue', 'red']) {
        const ai = aiState[team];
        const teamBuildings = gameContext.buildings.filter(b => b.team === team);
        const teamUnits = gameContext.units.filter(u => u.team === team);
        const resources = gameContext.resources[team];
        const gameTime = gameContext.gameState?.gameTime || 0;
        
        // Determine current economic phase
        ai.economicPhase = determineEconomicPhase(teamBuildings, resources, gameTime);
        
        // Record strategic decision for replay
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'STRATEGIC_ANALYSIS', { // Pass gameContext
            phase: ai.economicPhase,
            resources: { mass: Math.floor(resources.mass), energy: Math.floor(resources.energy) },
            units: teamUnits.length,
            buildings: teamBuildings.length
        });
        
        // Execute strategic decisions based on economic phase
        switch (ai.economicPhase) {
            case 'bootstrap':
                executeBootstrapStrategy(team, gameContext, ai);
                break;
            case 'expansion':
                executeExpansionStrategy(team, gameContext, ai);
                break;
            case 'military':
                executeMilitaryStrategy(team, gameContext, ai);
                break;
            case 'advanced':
                executeAdvancedStrategy(team, gameContext, ai);
                break;
        }
        
        // Adaptive AI personality based on game state
        adaptPersonality(team, gameContext, ai);
        
        // Coordinate major strategic movements
        coordinateStrategicMovements(team, gameContext, ai);
    }
}

function determineEconomicPhase(buildings, resources, gameTime) {
    const extractors = buildings.filter(b => b.type.resourceGeneration).length;
    const factories = buildings.filter(b => b.type.produces).length;
    const totalResources = resources.mass + resources.energy;
    
    if (gameTime < 60 || extractors < 2) return 'bootstrap';
    if (gameTime < 180 || totalResources < 500 || factories < 2) return 'expansion';
    if (gameTime < 300 || totalResources < 1000) return 'military';
    return 'advanced';
}

function executeBootstrapStrategy(team, gameContext, ai) {
    const commander = gameContext.units.find(u => u.team === team && u.type === UNIT_TYPES.commander);
    if (!commander) return;
    
    const nearbyResources = findNearbyResourceNodes(gameContext, commander.x, commander.y, 200);
    const teamBuildings = gameContext.buildings.filter(b => b.team === team);
    const extractors = teamBuildings.filter(b => b.type.resourceGeneration);
    
    // Aggressive early expansion
    if (extractors.length < 3 && nearbyResources.length > 0) {
        const targetResource = nearbyResources[0];
        const buildingType = targetResource.type === 'mass' ? BUILDING_TYPES.massExtractor : BUILDING_TYPES.energyExtractor;
        
        if (canAffordBuilding(gameContext.resources[team], buildingType)) {
            buildExtractorNear(gameContext, team, targetResource, buildingType);
            // Pass gameContext to recordAIDecision
            recordAIDecision(gameContext, team, 'BUILD_EXTRACTOR', { // Pass gameContext
                type: buildingType.name,
                position: { x: targetResource.x, y: targetResource.y },
                resourcesAfter: gameContext.resources[team]
            });
        }
    }
    
    // Build first factory quickly
    const factories = teamBuildings.filter(b => b.type.produces);
    if (factories.length === 0 && gameContext.resources[team].mass > 200) {
        buildFactoryNear(gameContext, team, commander);
    }
}

function executeExpansionStrategy(team, gameContext, ai) {
    const resources = gameContext.resources[team];
    const teamBuildings = gameContext.buildings.filter(b => b.team === team);
    const factories = teamBuildings.filter(b => b.type.produces);
    
    // Rapid economic expansion
    if (resources.mass > 300 && gameContext.seedRandom.random() < ai.personality.expansionRate) { // Use seeded random
        expandEconomicBase(gameContext, team, ai);
    }
    
    // Build multiple factories for unit production
    if (factories.length < 3 && resources.mass > 400) {
        const commander = gameContext.units.find(u => u.team === team && u.type === UNIT_TYPES.commander);
        if (commander) {
            buildFactoryNear(gameContext, team, commander);
        }
    }
    
    // Start military buildup
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    if (teamUnits.length < ai.personality.attackThreshold) {
        factories.forEach(factory => {
            if (factory.productionQueue && factory.productionQueue.length < 3) {
                queueUnitProduction(factory, UNIT_TYPES.tank);
            }
        });
    }
}

function executeMilitaryStrategy(team, gameContext, ai) {
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    const enemyTeam = team === 'blue' ? 'red' : 'blue';
    const enemyBuildings = gameContext.buildings.filter(b => b.team === enemyTeam);
    
    // Continuous unit production
    const factories = gameContext.buildings.filter(b => b.team === team && b.type.produces);
    factories.forEach(factory => {
        if (factory.productionQueue && factory.productionQueue.length < 2) {
            const unitType = chooseUnitType(gameContext, team, ai);
            queueUnitProduction(factory, unitType);
        }
    });
    
    // Launch coordinated attacks
    if (teamUnits.length >= ai.personality.attackThreshold) {
        launchCoordinatedAttack(gameContext, team, ai, enemyBuildings);
    }
    
    // Economic harassment raids
    if (ai.raidCooldown <= 0 && gameContext.seedRandom.random() < ai.personality.raidFrequency) { // Use seeded random
        launchEconomicRaid(gameContext, team, ai);
        ai.raidCooldown = 300; // 5 second cooldown
    }
    
    if (ai.raidCooldown > 0) ai.raidCooldown--;
}

function executeAdvancedStrategy(team, gameContext, ai) {
    const teamBuildings = gameContext.buildings.filter(b => b.team === team);
    const hasAdvanced = teamBuildings.some(b => b.type.name === 'Advanced Land Factory');
    
    // Build advanced facilities
    if (!hasAdvanced && gameContext.resources[team].mass > 800) {
        buildAdvancedFactory(gameContext, team);
    }
    
    // Tech-based unit production
    const advancedFactories = teamBuildings.filter(b => b.type.name === 'Advanced Land Factory');
    advancedFactories.forEach(factory => {
        if (factory.productionQueue && factory.productionQueue.length < 2) {
            queueUnitProduction(factory, UNIT_TYPES.artillery || UNIT_TYPES.tank);
        }
    });
    
    // Mass coordinated assaults
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    if (teamUnits.length > 15) {
        launchMassAssault(gameContext, team, ai);
    }
}

export function coordinateAttacks(gameContext) {
    // Add null checks for units and buildings arrays
    if (!gameContext.units || !Array.isArray(gameContext.units) ||
        !gameContext.buildings || !Array.isArray(gameContext.buildings)) {
        return; // Exit early if arrays are not properly initialized
    }
    
    for (const team of ['blue', 'red']) {
        const ai = aiState[team];
        const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
        
        // Enhanced tactical coordination
        coordinateTacticalGroups(gameContext, team, teamUnits, ai);
        
        // Formation-based attacks
        executeFormationAttacks(gameContext, team, teamUnits, ai);
        
        // Flanking maneuvers
        executeFlankingManeuvers(gameContext, team, teamUnits, ai);
    }
}

function coordinateTacticalGroups(gameContext, team, teamUnits, ai) {
    const processed = new Set();
    const groups = [];
    
    // Form tactical groups based on proximity and unit types
    for (const unit of teamUnits) {
        if (processed.has(unit)) continue;
        
        const nearby = teamUnits.filter(u => {
            if (processed.has(u)) return false;
            const distance = Math.sqrt((u.x - unit.x) ** 2 + (u.y - unit.y) ** 2);
            return distance < 120;
        });
        
        if (nearby.length >= 3) {
            const group = {
                units: [unit, ...nearby],
                center: calculateGroupCenter([unit, ...nearby]),
                strength: calculateGroupStrength([unit, ...nearby]),
                role: determineGroupRole([unit, ...nearby])
            };
            
            groups.push(group);
            [unit, ...nearby].forEach(u => processed.add(u));
        }
    }
    
    // Coordinate group attacks
    groups.forEach(group => {
        const target = selectOptimalTarget(gameContext, team, group);
        if (target) {
            assignGroupTarget(group, target);
            
            // Pass gameContext to recordAIDecision
            recordAIDecision(gameContext, team, 'COORDINATE_ATTACK', { // Pass gameContext
                groupSize: group.units.length,
                groupRole: group.role,
                targetType: target.type?.name || 'unknown',
                targetPosition: { x: target.x, y: target.y }
            });
            
            // Visual feedback
            gameContext.entityManager.addCaption(new Caption(
                group.center.x, group.center.y,
                `${group.role} assault!`, '#ff4', 16
            ));
        }
    });
}

// --- Asset Scoring Functions ---

export function getUnitMaxHP(unit) {
    if (unit && unit.type && typeof unit.type.hp === 'number' && unit.type.hp > 0) {
        return unit.type.hp;
    }
    // console.warn(`Unit type ${unit?.type?.name || 'unknown'} missing valid max HP (unit.type.hp). Falling back.`);
    return 100; // Default fallback
}

export function calculateRelativeHealth(unit, maxHP) {
    if (!unit || typeof unit.hp !== 'number' || maxHP <= 0) {
        return 0; // Dead, invalid, or maxHP is zero/negative
    }
    return Math.max(0, Math.min(1, unit.hp / maxHP));
}

export function calculateBasePower(unit) { // gameContext removed as UNIT_TYPES is globally available via import
    if (!unit || !unit.type) return 10; // Default for unknown type

    let bp = unit.type.damage || 10;
    if (unit.type.tier) {
        bp *= (1 + (unit.type.tier - 1) * 0.5); // T1=1x, T2=1.5x, T3=2x
    }

    // Commander check - UNIT_TYPES should be in scope from file imports
    if (UNIT_TYPES.commander && unit.type.name === UNIT_TYPES.commander.name) {
        bp *= 5.0;
    } else if (unit.type.isExperimental) { // Hypothetical flag
        bp *= 3.0;
    }
    // Add other role-specific multipliers if needed in future iterations
    return Math.max(1, bp); // Ensure power is at least 1
}

export function calculateAssetScore(targetUnit) { // gameContext removed as helpers don't need it directly now
    if (!targetUnit || typeof targetUnit.hp !== 'number' || targetUnit.hp <= 0) {
        return 0; // Dead or invalid target
    }
    const maxHP = getUnitMaxHP(targetUnit);
    const rh = calculateRelativeHealth(targetUnit, maxHP);
    const bp = calculateBasePower(targetUnit); // Pass gameContext if calculateBasePower needs it

    const assetScoreValue = bp / (rh + ASSET_SCORE_HEALTH_K_FACTOR); // Renamed to avoid conflict

    return Math.max(0, assetScoreValue); // Use renamed variable
}

// --- End Asset Scoring Functions ---

function executeFormationAttacks(gameContext, team, teamUnits, ai) {
    if (teamUnits.length < 8) return;
    
    const enemies = [...gameContext.units.filter(u => u.team !== team),
                    ...gameContext.buildings.filter(b => b.team !== team)];
    
    if (enemies.length === 0) return;
    
    // Create attack formations
    const formations = createAttackFormations(teamUnits, 6);
    
    formations.forEach((formation, index) => {
        const target = enemies[index % enemies.length];
        
        // Calculate formation positions
        const formationPositions = calculateFormationPositions(
            target.x, target.y, formation.length, 'line'
        );
        
        formation.forEach((unit, i) => {
            const pos = formationPositions[i];
            unit.patrolTarget = { x: pos.x, y: pos.y };
            unit.target = target;
            unit.aggressiveness = 0.9;
        });
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'FORMATION_ATTACK', { // Pass gameContext
            formationSize: formation.length,
            formationType: 'line',
            targetPosition: { x: target.x, y: target.y }
        });
    });
}

function executeFlankingManeuvers(gameContext, team, teamUnits, ai) {
    const enemyCommander = gameContext.units.find(u => 
        u.team !== team && u.type === UNIT_TYPES.commander
    );
    
    if (!enemyCommander || teamUnits.length < 6) return;
    
    // Select fast units for flanking
    const flankingUnits = teamUnits
        .filter(u => (u.type.speed || 0) > 2)
        .slice(0, 4);
    
    if (flankingUnits.length >= 3) {
        // Calculate flanking positions using seeded random
        const angle = gameContext.seedRandom.random() * Math.PI * 2; // Use seeded random
        const flankDistance = 150;
        
        flankingUnits.forEach((unit, i) => {
            const offsetAngle = angle + (i * Math.PI / 4);
            const flankX = enemyCommander.x + Math.cos(offsetAngle) * flankDistance;
            const flankY = enemyCommander.y + Math.sin(offsetAngle) * flankDistance;
            
            unit.patrolTarget = { x: flankX, y: flankY };
            unit.target = enemyCommander;
            unit.aggressiveness = 1.0;
        });
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'FLANKING_MANEUVER', { // Pass gameContext
            flankingUnits: flankingUnits.length,
            targetCommander: enemyCommander.team
        });
        
        gameContext.entityManager.addCaption(new Caption(
            enemyCommander.x, enemyCommander.y,
            `Flanking maneuver!`, '#f44', 14
        ));
    }
}

// Helper functions for enhanced AI
function findNearbyResourceNodes(gameContext, x, y, radius) {
    if (!gameContext.resourceNodes) return [];
    
    return gameContext.resourceNodes.filter(node => {
        const distance = Math.sqrt((node.x - x) ** 2 + (node.y - y) ** 2);
        return distance <= radius && !node.occupied;
    }).sort((a, b) => {
        const distA = Math.sqrt((a.x - x) ** 2 + (a.y - y) ** 2);
        const distB = Math.sqrt((b.x - x) ** 2 + (b.y - y) ** 2);
        return distA - distB;
    });
}

function canAffordBuilding(resources, buildingType) {
    return resources.mass >= (buildingType.cost?.mass || 0) &&
           resources.energy >= (buildingType.cost?.energy || 0);
}

function buildExtractorNear(gameContext, team, resource, buildingType) {
    const pos = findLandPosition(gameContext, resource.x, resource.y, 20);
    if (pos) {
        const building = new Building(pos.x, pos.y, team, buildingType, gameContext);
        gameContext.buildings.push(building);
        resource.occupied = true;
        
        gameContext.captions.push(new Caption(pos.x, pos.y,
            `${buildingType.name} constructed!`, '#4f4', 12));
    }
}

function buildFactoryNear(gameContext, team, commander) {
    // Use seeded random for position offset
    const pos = findLandPosition(gameContext, 
        commander.x + (gameContext.seedRandom.random() - 0.5) * 100, // Use seeded random
        commander.y + (gameContext.seedRandom.random() - 0.5) * 100, 30); // Use seeded random
    
    if (pos) {
        const factory = new Building(pos.x, pos.y, team, BUILDING_TYPES.landFactory, gameContext);
        gameContext.buildings.push(factory);
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'BUILD_FACTORY', { // Pass gameContext
            position: { x: pos.x, y: pos.y },
            type: 'landFactory'
        });
    }
}

function expandEconomicBase(gameContext, team, ai) {
    const commander = gameContext.units.find(u => u.team === team && u.type === UNIT_TYPES.commander);
    if (!commander) return;
    
    const nearbyResources = findNearbyResourceNodes(gameContext, commander.x, commander.y, 300);
    
    if (nearbyResources.length > 0) {
        const resource = nearbyResources[0];
        const buildingType = resource.type === 'mass' ? 
            BUILDING_TYPES.massExtractor : BUILDING_TYPES.energyExtractor;
        
        buildExtractorNear(gameContext, team, resource, buildingType);
    }
}

function chooseUnitType(gameContext, team, ai) {
    const enemyUnits = gameContext.units.filter(u => u.team !== team);
    const enemyTanks = enemyUnits.filter(u => u.type.name === 'Tank').length;
    
    // Counter-strategy
    if (enemyTanks > 3 && UNIT_TYPES.artillery) {
        return UNIT_TYPES.artillery;
    }
    
    return UNIT_TYPES.tank || UNIT_TYPES.infantry;
}

function queueUnitProduction(factory, unitType) {
    if (!factory.productionQueue) factory.productionQueue = [];
    factory.productionQueue.push(unitType);
}

function launchCoordinatedAttack(gameContext, team, ai, enemyBuildings) {
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    const attackForce = teamUnits.slice(0, Math.floor(teamUnits.length * 0.7));
    
    if (attackForce.length < 5) return;
    
    // Target priority: Commander > Factories > Extractors
    const targets = [
        ...gameContext.units.filter(u => u.team !== team && u.type === UNIT_TYPES.commander),
        ...enemyBuildings.filter(b => b.type.produces),
        ...enemyBuildings.filter(b => b.type.resourceGeneration)
    ];
    
    if (targets.length > 0) {
        const primaryTarget = targets[0];
        
        attackForce.forEach(unit => {
            unit.target = primaryTarget;
            unit.aggressiveness = 0.95;
        });
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'LAUNCH_ATTACK', { // Pass gameContext
            attackForceSize: attackForce.length,
            targetType: primaryTarget.type?.name || 'unknown',
            targetPosition: { x: primaryTarget.x, y: primaryTarget.y }
        });
        
        gameContext.entityManager.addCaption(new Caption(
            primaryTarget.x, primaryTarget.y,
            `${team.toUpperCase()} ASSAULT!`, '#f00', 18
        ));
    }
}

function launchEconomicRaid(gameContext, team, ai) {
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    const raiders = teamUnits.slice(0, 4);
    
    const enemyExtractors = gameContext.buildings.filter(b => 
        b.team !== team && b.type.resourceGeneration
    );
    
    if (raiders.length >= 2 && enemyExtractors.length > 0) {
        // Use seeded random for target selection
        const target = enemyExtractors[Math.floor(gameContext.seedRandom.random() * enemyExtractors.length)]; // Use seeded random
        
        raiders.forEach(unit => {
            unit.patrolTarget = { x: target.x, y: target.y };
            unit.aggressiveness = 0.9;
        });
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'ECONOMIC_RAID', { // Pass gameContext
            raiderCount: raiders.length,
            targetType: target.type.name,
            targetPosition: { x: target.x, y: target.y }
        });
    }
}

function buildAdvancedFactory(gameContext, team) {
    const commander = gameContext.units.find(u => u.team === team && u.type === UNIT_TYPES.commander);
    if (!commander) return;
    
    // Use seeded random for position offset
    const pos = findLandPosition(gameContext,
        commander.x + (gameContext.seedRandom.random() - 0.5) * 200, // Use seeded random
        commander.y + (gameContext.seedRandom.random() - 0.5) * 200, 40); // Use seeded random
    
    if (pos) {
        const factory = new Building(pos.x, pos.y, team, BUILDING_TYPES.advancedLandFactory, gameContext);
        gameContext.buildings.push(factory);
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'BUILD_ADVANCED', { // Pass gameContext
            type: 'advancedLandFactory',
            position: { x: pos.x, y: pos.y }
        });
    }
}

function launchMassAssault(gameContext, team, ai) {
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    const enemyCommander = gameContext.units.find(u => 
        u.team !== team && u.type === UNIT_TYPES.commander
    );
    
    if (enemyCommander && teamUnits.length > 12) {
        teamUnits.forEach(unit => {
            unit.target = enemyCommander;
            unit.aggressiveness = 1.0;
        });
        
        // Pass gameContext to recordAIDecision
        recordAIDecision(gameContext, team, 'MASS_ASSAULT', { // Pass gameContext
            assaultForceSize: teamUnits.length,
            targetCommander: enemyCommander.team
        });
        
        gameContext.entityManager.addCaption(new Caption(
            enemyCommander.x, enemyCommander.y,
            `FINAL ASSAULT!`, '#ff0000', 20
        ));
    }
}

function adaptPersonality(team, gameContext, ai) {
    const enemyTeam = team === 'blue' ? 'red' : 'blue';
    const myUnits = gameContext.units.filter(u => u.team === team).length;
    const enemyUnits = gameContext.units.filter(u => u.team === enemyTeam).length;
    
    // Adapt based on relative strength
    if (myUnits > enemyUnits * 1.5) {
        ai.personality = AI_PERSONALITIES.AGGRESSIVE;
    } else if (myUnits < enemyUnits * 0.7) {
        ai.personality = AI_PERSONALITIES.ECONOMIC;
    } else {
        ai.personality = AI_PERSONALITIES.BALANCED;
    }
}

function coordinateStrategicMovements(team, gameContext, ai) {
    // Large-scale strategic coordination
    const teamUnits = gameContext.units.filter(u => u.team === team && !u.type.support);
    const gameTime = gameContext.gameState?.gameTime || 0;
    
    // Every 30 seconds, reassess strategy
    if (gameTime - ai.lastMajorDecision > 30) {
        ai.lastMajorDecision = gameTime;
        
        // Global unit repositioning based on strategy
        if (ai.militaryStrategy === 'aggressive' && teamUnits.length > 8) {
            repositionForOffensive(teamUnits, gameContext, team);
        } else if (ai.militaryStrategy === 'defensive') {
            repositionForDefense(teamUnits, gameContext, team);
        }
    }
}

function repositionForOffensive(units, gameContext, team) {
    const enemyCommander = gameContext.units.find(u => 
        u.team !== team && u.type === UNIT_TYPES.commander
    );
    
    if (enemyCommander) {
        units.forEach(unit => {
            // Move towards enemy commander area
            const angle = Math.atan2(enemyCommander.y - unit.y, enemyCommander.x - unit.x);
            const distance = 100 + gameContext.seedRandom.random() * 50; // Use seeded random
            
            unit.patrolTarget = {
                x: enemyCommander.x - Math.cos(angle) * distance,
                y: enemyCommander.y - Math.sin(angle) * distance
            };
        });
    }
}

function repositionForDefense(units, gameContext, team) {
    const myCommander = gameContext.units.find(u => 
        u.team === team && u.type === UNIT_TYPES.commander
    );
    
    if (myCommander) {
        units.forEach((unit, index) => {
            // Defensive perimeter
            const angle = (index / units.length) * Math.PI * 2; // This is deterministic, no random needed
            const radius = 80 + gameContext.seedRandom.random() * 40; // Use seeded random
            
            unit.patrolTarget = {
                x: myCommander.x + Math.cos(angle) * radius,
                y: myCommander.y + Math.sin(angle) * radius
            };
        });
    }
}

// Helper functions for tactical coordination
function calculateGroupCenter(units) {
    const x = units.reduce((sum, u) => sum + u.x, 0) / units.length;
    const y = units.reduce((sum, u) => sum + u.y, 0) / units.length;
    return { x, y };
}

function calculateGroupStrength(units) {
    return units.reduce((sum, u) => sum + (u.type.damage || 10), 0);
}

function determineGroupRole(units) {
    const hasHeavy = units.some(u => u.type.name === 'Tank');
    const hasArtillery = units.some(u => u.type.name === 'Artillery');
    
    if (hasArtillery) return 'Artillery';
    if (hasHeavy) return 'Assault';
    return 'Skirmish';
}

function selectOptimalTarget(gameContext, team, group) {
    const enemies = [...gameContext.units.filter(u => u.team !== team),
                    ...gameContext.buildings.filter(b => b.team !== team)];
    
    let bestTarget = null;
    let bestScore = -Infinity;
    
    for (const enemy of enemies) {
        const dist = Math.sqrt(
            (enemy.x - group.center.x) ** 2 + (enemy.y - group.center.y) ** 2
        );
        
        const baseAssetScore = calculateAssetScore(enemy);
        let currentScore = baseAssetScore / (dist + TARGET_SCORE_DISTANCE_DIVISOR);

        // Avoid pointless attacks: Penalize score if group is too weak for the target, unless it's a commander
        // Assuming group.strength is a reasonable proxy for the group's collective power.
        // A more accurate sum of calculateBasePower for group members could be used if available on 'group'.
        const groupEffectivePower = group.strength;
        if (enemy.type !== UNIT_TYPES.commander && groupEffectivePower < baseAssetScore * MIN_POWER_RATIO_TO_ENGAGE_ENEMY) {
            currentScore *= 0.1; // Heavily penalize if group is much weaker and target isn't commander
        }
        
        // Additional prioritization can be added here if needed,
        // though calculateBasePower already handles commanders and experimentals.
        // Example: if (enemy.type?.produces) currentScore *= 1.2; // Slightly boost factories if not covered enough

        if (currentScore > bestScore) {
            bestScore = currentScore;
            bestTarget = enemy;
        }
    }
    
    return bestTarget;
}

function assignGroupTarget(group, target) {
    group.units.forEach(unit => {
        unit.target = target;
        unit.lastTargetSwitch = Date.now();
    });
}

function createAttackFormations(units, formationSize) {
    const formations = [];
    for (let i = 0; i < units.length; i += formationSize) {
        formations.push(units.slice(i, i + formationSize));
    }
    return formations;
}

function calculateFormationPositions(centerX, centerY, unitCount, formation) {
    const positions = [];
    const spacing = 32;
    
    for (let i = 0; i < unitCount; i++) {
        switch (formation) {
            case 'line':
                positions.push({
                    x: centerX + (i - unitCount/2) * spacing,
                    y: centerY
                });
                break;
            case 'column':
                positions.push({
                    x: centerX,
                    y: centerY + (i - unitCount/2) * spacing
                });
                break;
            default:
                positions.push({ x: centerX, y: centerY });
        }
    }
    
    return positions;
}

// Updated to receive gameContext
function recordAIDecision(gameContext, team, decisionType, data) { // gameContext is the simulation instance
    // Access battleJournal from the simulation instance
    if (gameContext.battleJournal && gameContext.battleJournal.isRecording) { 
        // New API: recordEvent(type, message, data = null)
        // gameTime is handled implicitly by the new battleJournal.
        gameContext.battleJournal.recordEvent(decisionType, 
            `AI decision for ${team}: ${decisionType}`, 
            { // data payload
                ...data,
                aiTeam: team
            }
        );
    }
}

// Export AI state for debugging
export function getAIState() {
    return aiState;
}

// Reset AI state (This function's random calls are only for AI personalities, not game logic)
export function resetAIState() {
    Object.keys(aiState).forEach(team => {
        aiState[team].lastMajorDecision = 0;
        aiState[team].economicPhase = 'bootstrap';
        aiState[team].militaryStrategy = team === 'red' ? 'aggressive' : 'balanced';
        aiState[team].targetPriorities = [];
        aiState[team].expansionTargets = [];
        aiState[team].raidCooldown = 0;
    });
}
