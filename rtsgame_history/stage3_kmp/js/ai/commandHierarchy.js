// js/ai/commandHierarchy.js
// Command hierarchy and squad management system

import { calculateAssetScore, getUnitMaxHP, calculateRelativeHealth } from './strategicAI.js';
// calculateBasePower is used by calculateAssetScore, so it doesn't need to be explicitly imported.
import { UNIT_TYPES } from '../config/unitTypes.js'; // Added for UNIT_TYPES.commander access
import { calculateBasePower } from './strategicAI.js'; // Needed for groupPower calculation

const LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK = 0.3; // Target below 30% health
const MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT = 4;  // Only adjust if group has at least this many members
const NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET = 2;       // Number of units to assign to a weak target
const MIN_POWER_RATIO_FOR_GROUP_COMMITMENT = 0.4; // Group power must be at least 40% of target's asset score to commit.

export const CommandRank = {
    COMMANDER: 5,      // ACU - Supreme commander
    COLONEL: 4,        // High-tier units, experimental units
    MAJOR: 3,          // Mid-tier units, factory commanders
    CAPTAIN: 2,        // Low-tier combat units, squad leaders
    LIEUTENANT: 1,     // Basic units, support units
    PRIVATE: 0         // Scout units, basic infantry
};

export const MissionType = {
    SEARCH: 'search',           // Reconnaissance and scouting
    HUNT: 'hunt',              // Seek and destroy enemy units
    COLLECT: 'collect',        // Resource gathering and protection
    DEFEND: 'defend',          // Area defense
    ASSAULT: 'assault',        // Coordinated attacks
    SUPPORT: 'support',        // Engineering and repair
    SORTIE: 'sortie'           // Air missions - strike and return
};

export const UnitRole = {
    ANTI_AIR: 'anti_air',      // Counters air units (rock beats scissors)
    AIR: 'air',                // Air units (scissors beats paper)
    GROUND: 'ground',          // Ground units (paper beats rock)
    AMPHIBIOUS: 'amphibious',  // Can fight both land and sea
    SUPPORT: 'support'         // Engineering and support
};

export class CommandGroup {
    constructor(leader, mission = MissionType.SEARCH, priority = 1) {
        this.id = Math.random().toString(36).substring(2, 8);
        this.leader = leader;
        this.members = [leader];
        this.mission = mission;
        this.priority = priority;
        this.target = null;
        this.rallyPoint = null;
        this.formation = 'loose';
        this.status = 'forming';
        this.lastUpdate = Date.now();
        this.maxSize = this.getMaxSizeForMission(mission);
        
        // Mission-specific parameters
        this.searchRadius = 300;
        this.engagementRange = 200;
        this.cohesionRadius = 150;
        
        // Sortie-specific parameters
        this.sortiePhase = 'staging';      // staging -> approach -> strike -> return
        this.homeBase = null;
        this.strikeTarget = null;
        this.sortieTimeout = 30000;        // 30 seconds max sortie time
        this.sortieStartTime = 0;
        
        // Tactical diversity requirements
        this.roleComposition = new Map();
        this.diversityRequired = this.requiresDiversity(mission);
        this.optimalComposition = this.getOptimalComposition(mission);
        
        // Performance tracking
        this.kills = 0;
        this.losses = 0;
        this.resourcesGathered = 0;
        this.efficiency = 1.0;
    }

    getMaxSizeForMission(mission) {
        switch (mission) {
            case MissionType.SEARCH: return 3;      // Small, fast scout groups
            case MissionType.HUNT: return 6;        // Medium attack squads
            case MissionType.COLLECT: return 8;     // Large resource protection
            case MissionType.DEFEND: return 10;     // Large defensive formations
            case MissionType.ASSAULT: return 12;    // Major attack force
            case MissionType.SUPPORT: return 4;     // Engineering teams
            case MissionType.SORTIE: return 5;      // Air strike squadrons
            default: return 5;
        }
    }

    requiresDiversity(mission) {
        // Missions that benefit from rock-paper-scissors balance
        return [MissionType.DEFEND, MissionType.ASSAULT, MissionType.HUNT].includes(mission);
    }

    getOptimalComposition(mission) {
        // Define optimal unit role distribution for different missions
        const compositions = {
            [MissionType.DEFEND]: {
                [UnitRole.ANTI_AIR]: 0.3,    // Strong AA defense
                [UnitRole.GROUND]: 0.4,      // Ground defense backbone
                [UnitRole.AIR]: 0.2,         // Air patrol
                [UnitRole.SUPPORT]: 0.1      // Repairs and support
            },
            [MissionType.ASSAULT]: {
                [UnitRole.AIR]: 0.3,         // Air superiority
                [UnitRole.GROUND]: 0.5,      // Main assault force
                [UnitRole.ANTI_AIR]: 0.2     // AA protection
            },
            [MissionType.HUNT]: {
                [UnitRole.AIR]: 0.4,         // Fast pursuit
                [UnitRole.GROUND]: 0.4,      // Ground pursuit
                [UnitRole.ANTI_AIR]: 0.2     // Counter enemy air
            },
            [MissionType.SORTIE]: {
                [UnitRole.AIR]: 1.0          // Pure air mission
            },
            [MissionType.SEARCH]: {
                [UnitRole.AIR]: 0.6,         // Fast reconnaissance
                [UnitRole.GROUND]: 0.4       // Ground confirmation
            }
        };
        
        return compositions[mission] || {
            [UnitRole.GROUND]: 0.6,
            [UnitRole.AIR]: 0.2,
            [UnitRole.ANTI_AIR]: 0.2
        };
    }

    getUnitRole(unit) {
        if (!unit.type) return UnitRole.GROUND;
        
        if (unit.type.domain === 'air') {
            return UnitRole.AIR;
        } else if (unit.type.name.includes('AA') || unit.type.name.includes('Anti-Air')) {
            return UnitRole.ANTI_AIR;
        } else if (unit.type.movementType === 'amphibious') {
            return UnitRole.AMPHIBIOUS;
        } else if (unit.type.support) {
            return UnitRole.SUPPORT;
        } else {
            return UnitRole.GROUND;
        }
    }

    addMember(unit) {
        if (this.members.length >= this.maxSize) return false;
        if (this.members.includes(unit)) return false;
        
        // Check diversity requirements
        if (this.diversityRequired && !this.canAddUnitRole(unit)) {
            return false;
        }
        
        this.members.push(unit);
        unit.commandGroup = this;
        
        // Update role composition
        const role = this.getUnitRole(unit);
        this.roleComposition.set(role, (this.roleComposition.get(role) || 0) + 1);
        
        return true;
    }

    canAddUnitRole(unit) {
        const role = this.getUnitRole(unit);
        const currentCount = this.roleComposition.get(role) || 0;
        const totalMembers = this.members.length;
        const optimalRatio = this.optimalComposition[role] || 0;
        
        // Allow if we're below optimal ratio or if we have very few members
        if (totalMembers < 3) return true;
        
        const currentRatio = currentCount / totalMembers;
        const newRatio = (currentCount + 1) / (totalMembers + 1);
        
        // Allow if new ratio doesn't exceed optimal by more than 50%
        return newRatio <= optimalRatio * 1.5;
    }

    removeMember(unit) {
        const index = this.members.indexOf(unit);
        if (index > -1) {
            this.members.splice(index, 1);
            unit.commandGroup = null;
            
            // If leader was removed, promote next highest rank
            if (unit === this.leader && this.members.length > 0) {
                this.promoteNewLeader();
            }
            
            return true;
        }
        return false;
    }

    promoteNewLeader() {
        // Find highest ranking member
        let newLeader = this.members[0];
        let highestRank = this.getRank(newLeader);
        
        for (const member of this.members) {
            const rank = this.getRank(member);
            if (rank > highestRank) {
                highestRank = rank;
                newLeader = member;
            }
        }
        
        this.leader = newLeader;
    }

    getRank(unit) {
        if (!unit.type) return CommandRank.PRIVATE;
        
        if (unit.type.name === 'Commander' || unit.type.name === 'ACU') {
            return CommandRank.COMMANDER;
        } else if (unit.type.tier >= 3) {
            return CommandRank.COLONEL;
        } else if (unit.type.tier >= 2) {
            return CommandRank.MAJOR;
        } else if (unit.type.damage > 30 || unit.type.support) {
            return CommandRank.CAPTAIN;
        } else if (unit.type.speed > 2.5) {
            return CommandRank.LIEUTENANT;
        } else {
            return CommandRank.PRIVATE;
        }
    }

    getRankName(rank) {
        const names = ['Private', 'Lieutenant', 'Captain', 'Major', 'Colonel', 'Commander'];
        return names[rank] || 'Unknown';
    }

    update(gameContext) {
        if (this.members.length === 0) return false; // Disband empty group
        
        // Remove dead members
        this.members = this.members.filter(member => member.hp > 0);
        if (this.members.length === 0) return false;
        
        // Update leader if dead
        if (!this.members.includes(this.leader)) {
            this.promoteNewLeader();
        }

        // Execute mission
        this.executeMission(gameContext);
        
        // Update cohesion
        this.maintainFormation(gameContext);
        
        this.lastUpdate = Date.now();
        return true;
    }

    executeMission(gameContext) {
        switch (this.mission) {
            case MissionType.SEARCH:
                this.executeSearch(gameContext);
                break;
            case MissionType.HUNT:
                this.executeHunt(gameContext);
                break;
            case MissionType.COLLECT:
                this.executeCollect(gameContext);
                break;
            case MissionType.DEFEND:
                this.executeDefend(gameContext);
                break;
            case MissionType.ASSAULT:
                this.executeAssault(gameContext);
                break;
            case MissionType.SUPPORT:
                this.executeSupport(gameContext);
                break;
            case MissionType.SORTIE:
                this.executeSortie(gameContext);
                break;
        }
    }

    executeSearch(gameContext) {
        // Scout for enemies and resources
        const leader = this.leader;
        if (!leader.target && !leader.patrolTarget) {
            // Find unexplored area or enemy contact
            const searchPoint = this.findSearchTarget(gameContext);
            if (searchPoint) {
                this.members.forEach(member => {
                    member.patrolTarget = {
                        x: searchPoint.x + (Math.random() - 0.5) * 100,
                        y: searchPoint.y + (Math.random() - 0.5) * 100
                    };
                });
            }
        }
    }

    executeHunt(gameContext) {
        // Seek and destroy enemy units
        const nearestEnemy = this.findNearestEnemy(gameContext);
        this.target = nearestEnemy; // Set group target

        if (this.target) {
            const targetAssetScore = calculateAssetScore(this.target);
            const groupPower = this.members.reduce((sum, member) => sum + calculateBasePower(member), 0);

            // Avoid pointless attack if group is significantly weaker than the target, unless target is Commander
            if (this.target.type !== UNIT_TYPES.commander &&
                groupPower < targetAssetScore * MIN_POWER_RATIO_FOR_GROUP_COMMITMENT &&
                this.members.length > 0) {

                // console.log(`Group ${this.id} avoiding pointless attack on ${this.target.type.name} (${this.target.id}). Group Power: ${groupPower}, Target Score: ${targetAssetScore}`);
                this.target = null; // Clear the target
                this.mission = MissionType.SEARCH; // Revert to search to find a more suitable engagement
            }

            // Proceed with target assignment if target is still valid (not cleared by pointless attack check)
            if (this.target) {
                const targetMaxHP = getUnitMaxHP(this.target);
                const targetRH = calculateRelativeHealth(this.target, targetMaxHP);

                if (this.members.length >= MIN_GROUP_SIZE_FOR_OVERKILL_ADJUSTMENT &&
                    targetRH < LOW_HEALTH_THRESHOLD_FOR_OVERKILL_CHECK &&
                    targetRH > 0 && // Target is weak but not dead
                    this.target.type && this.target.type.name !== UNIT_TYPES.commander.name // Avoid overkill logic for commanders
                ) {
                    // Overkill avoidance for weak target
                    // Sort members by distance to assign closest units
                const sortedMembers = [...this.members].sort((a, b) => {
                    const distA = Math.sqrt(Math.pow(a.x - this.target.x, 2) + Math.pow(a.y - this.target.y, 2));
                    const distB = Math.sqrt(Math.pow(b.x - this.target.x, 2) + Math.pow(b.y - this.target.y, 2));
                    return distA - distB;
                });

                for (let i = 0; i < sortedMembers.length; i++) {
                    const member = sortedMembers[i];
                    if (i < NUM_UNITS_TO_FOCUS_ON_WEAK_TARGET) {
                        member.target = this.target; // Focus fire with a few units
                    } else {
                        member.target = null; // Other units can look for other targets or move
                        // Set a patrol point near the current engagement or leader
                        member.patrolTarget = {
                            x: this.target.x + (Math.random() - 0.5) * 50,
                            y: this.target.y + (Math.random() - 0.5) * 50
                        };
                    }
                }
                    // console.log(`Group ${this.id} applying overkill avoidance for target ${this.target.id}`);
                } else {
                    // Standard assignment for non-weak target, small group, or commander
                    this.members.forEach(member => {
                        member.target = this.target;
                    });
                }
            } else {
                // Target was cleared by pointless attack check, ensure members also have no target
                this.members.forEach(member => {
                    member.target = null;
                 });
            }
        } else {
            // No target found by findNearestEnemy, clear existing targets for members of this hunt group
            this.members.forEach(member => {
               member.target = null;
            });
        }
    }

    executeCollect(gameContext) {
        // Protect resource gathering operations
        const resourceNodes = gameContext.resourceNodes || [];
        const friendlyNodes = resourceNodes.filter(node => 
            node.occupied && this.isNearFriendlyBase(node, gameContext)
        );
        
        if (friendlyNodes.length > 0) {
            const centerX = friendlyNodes.reduce((sum, node) => sum + node.x, 0) / friendlyNodes.length;
            const centerY = friendlyNodes.reduce((sum, node) => sum + node.y, 0) / friendlyNodes.length;
            
            this.rallyPoint = { x: centerX, y: centerY };
            this.patrolAroundPoint(this.rallyPoint, 200);
        }
    }

    executeDefend(gameContext) {
        // Defend important structures
        const importantBuildings = gameContext.buildings.filter(b => 
            b.team === this.leader.team && 
            (b.type.name.includes('Factory') || b.type.name.includes('Commander'))
        );
        
        if (importantBuildings.length > 0) {
            const building = importantBuildings[0];
            this.rallyPoint = { x: building.x, y: building.y };
            this.patrolAroundPoint(this.rallyPoint, 250);
        }
    }

    executeAssault(gameContext) {
        // Coordinated attack on enemy base
        const enemyBuildings = gameContext.buildings.filter(b => b.team !== this.leader.team);
        if (enemyBuildings.length > 0) {
            const target = enemyBuildings[0];
            this.target = target;
            
            // Coordinate attack timing
            this.members.forEach((member, index) => {
                member.target = target;
                // Stagger arrival slightly for tactical effect
                setTimeout(() => {
                    if (member.hp > 0) member.target = target;
                }, index * 500);
            });
        }
    }

    executeSupport(gameContext) {
        // Engineering and support operations
        const damagedBuildings = gameContext.buildings.filter(b => 
            b.team === this.leader.team && b.hp < b.maxHp
        );
        
        if (damagedBuildings.length > 0) {
            const building = damagedBuildings[0];
            this.members.forEach(member => {
                if (member.type.support) {
                    member.target = building;
                }
            });
        }
    }

    executeSortie(gameContext) {
        const now = Date.now();
        
        // Initialize sortie if needed
        if (this.sortiePhase === 'staging') {
            this.initializeSortie(gameContext);
        }
        
        // Check for sortie timeout
        if (now - this.sortieStartTime > this.sortieTimeout) {
            this.sortiePhase = 'return';
        }
        
        switch (this.sortiePhase) {
            case 'staging':
                this.sortieStagingPhase(gameContext);
                break;
            case 'approach':
                this.sortieApproachPhase(gameContext);
                break;
            case 'strike':
                this.sortieStrikePhase(gameContext);
                break;
            case 'return':
                this.sortieReturnPhase(gameContext);
                break;
        }
    }

    initializeSortie(gameContext) {
        // Find home base (nearest friendly air factory or main base)
        this.homeBase = this.findAirBase(gameContext);
        if (!this.homeBase) {
            // Use leader's current position as fallback
            this.homeBase = { x: this.leader.x, y: this.leader.y };
        }
        
        // Find strike target
        this.strikeTarget = this.findStrikeTarget(gameContext);
        
        if (this.strikeTarget) {
            this.sortieStartTime = Date.now();
            this.sortiePhase = 'approach';
            this.status = 'sortie_approach';
        } else {
            // No targets, return to search or patrol
            this.mission = MissionType.SEARCH;
        }
    }

    sortieStagingPhase(gameContext) {
        // Move to home base for coordination
        this.members.forEach(member => {
            if (member.type.domain === 'air') {
                member.patrolTarget = {
                    x: this.homeBase.x + (Math.random() - 0.5) * 100,
                    y: this.homeBase.y + (Math.random() - 0.5) * 100
                };
            }
        });
        
        // Check if all units are ready (near home base)
        const allReady = this.members.every(member => {
            const distance = Math.sqrt(
                (member.x - this.homeBase.x) ** 2 + (member.y - this.homeBase.y) ** 2
            );
            return distance < 150;
        });
        
        if (allReady) {
            this.sortiePhase = 'approach';
            this.status = 'sortie_approach';
        }
    }

    sortieApproachPhase(gameContext) {
        // Fly to strike target in formation
        this.members.forEach((member, index) => {
            if (member.type.domain === 'air') {
                const formationOffset = this.getFormationOffset(index, this.members.length);
                member.target = null; // Clear any existing targets
                member.patrolTarget = {
                    x: this.strikeTarget.x + formationOffset.x,
                    y: this.strikeTarget.y + formationOffset.y
                };
            }
        });
        
        // Check if we're in strike range
        const leaderDistance = Math.sqrt(
            (this.leader.x - this.strikeTarget.x) ** 2 + 
            (this.leader.y - this.strikeTarget.y) ** 2
        );
        
        if (leaderDistance < this.engagementRange) {
            this.sortiePhase = 'strike';
            this.status = 'sortie_strike';
        }
    }

    sortieStrikePhase(gameContext) {
        // Engage targets and assess situation
        this.members.forEach(member => {
            if (member.type.domain === 'air') {
                // Prioritize high-value targets
                const target = this.findBestTargetForAirUnit(member, gameContext);
                if (target) {
                    member.target = target;
                } else {
                    // No targets, begin return
                    this.sortiePhase = 'return';
                }
            }
        });
        
        // Check if we should retreat (heavy losses or mission complete)
        const airUnits = this.members.filter(m => m.type.domain === 'air');
        if (airUnits.length < this.members.length * 0.5) {
            this.sortiePhase = 'return';
            this.status = 'sortie_return';
        }
    }

    sortieReturnPhase(gameContext) {
        // Return to base
        this.members.forEach(member => {
            if (member.type.domain === 'air') {
                member.target = null; // Disengage
                member.patrolTarget = {
                    x: this.homeBase.x + (Math.random() - 0.5) * 50,
                    y: this.homeBase.y + (Math.random() - 0.5) * 50
                };
            }
        });
        
        // Check if returned to base
        const allReturned = this.members.every(member => {
            const distance = Math.sqrt(
                (member.x - this.homeBase.x) ** 2 + (member.y - this.homeBase.y) ** 2
            );
            return distance < 200;
        });
        
        if (allReturned) {
            // Sortie complete, reassign mission
            this.mission = Math.random() < 0.6 ? MissionType.SEARCH : MissionType.HUNT;
            this.sortiePhase = 'staging';
            this.status = 'reformed';
        }
    }

    findAirBase(gameContext) {
        const airFactories = gameContext.buildings.filter(b => 
            b.team === this.leader.team && b.type.name.includes('Air')
        );
        
        if (airFactories.length > 0) {
            // Find closest air factory to leader
            let closest = airFactories[0];
            let closestDistance = Math.sqrt(
                (closest.x - this.leader.x) ** 2 + (closest.y - this.leader.y) ** 2
            );
            
            for (const factory of airFactories) {
                const distance = Math.sqrt(
                    (factory.x - this.leader.x) ** 2 + (factory.y - this.leader.y) ** 2
                );
                if (distance < closestDistance) {
                    closest = factory;
                    closestDistance = distance;
                }
            }
            
            return { x: closest.x, y: closest.y };
        }
        
        return null;
    }

    findStrikeTarget(gameContext) {
        // Prioritize high-value enemy targets
        const enemies = [
            ...gameContext.units.filter(u => u.team !== this.leader.team),
            ...gameContext.buildings.filter(b => b.team !== this.leader.team)
        ];
        
        if (enemies.length === 0) return null;
        
        // Score targets based on value and threat
        let bestTarget = null;
        let bestScore = 0;
        
        for (const target of enemies) {
            const score = this.scoreStrikeTarget(target, gameContext);
            if (score > bestScore) {
                bestScore = score;
                bestTarget = target;
            }
        }
        
        return bestTarget;
    }

    scoreStrikeTarget(target, gameContext) {
        let newScore = calculateAssetScore(target);
        
        // Penalty for distance
        const distance = Math.sqrt(
            (target.x - this.leader.x) ** 2 + (target.y - this.leader.y) ** 2
        );
        newScore -= distance / 20; // Keep existing distance penalty factor
        
        // Penalty for anti-air threats nearby
        const aaThreats = gameContext.units.filter(u => 
            u.team !== this.leader.team && 
            this.getUnitRole(u) === UnitRole.ANTI_AIR &&
            Math.sqrt((u.x - target.x) ** 2 + (u.y - target.y) ** 2) < 300 // AA check radius
        ).length;
        
        newScore -= aaThreats * 15; // Keep existing AA threat penalty
        
        return Math.max(0, newScore);
    }

    findBestTargetForAirUnit(airUnit, gameContext) {
        const enemies = gameContext.units.filter(u => 
            u.team !== airUnit.team && 
            u.hp > 0 && // Ensure target is alive
            Math.sqrt((u.x - airUnit.x) ** 2 + (u.y - airUnit.y) ** 2) < (airUnit.type.range || 100) * 1.5 // Use unit's range
        );
        
        if (enemies.length === 0) return null;
        
        let bestTarget = null;
        let bestScore = -Infinity; // Initialize with a very low score
        
        for (const enemy of enemies) {
            let newScore = calculateAssetScore(enemy);
            const enemyRole = this.getUnitRole(enemy);
            
            // Apply role-based adjustments
            if (enemyRole === UnitRole.GROUND) {
                // This case might be less relevant if this function is strictly air-to-air,
                // but if air units can target ground, this is a bonus.
                // If only air targets are expected, this might be removed or adjusted.
                newScore += 30; // Bonus for attacking ground (if applicable)
            } else if (enemyRole === UnitRole.ANTI_AIR) {
                newScore *= 0.5; // Significantly reduce score for AA threats
            } else if (enemyRole === UnitRole.AIR) {
                newScore *= 1.1; // Slight bonus for engaging other air units
            }
            
            // Distance penalty
            const distance = Math.sqrt((enemy.x - airUnit.x) ** 2 + (enemy.y - airUnit.y) ** 2);
            newScore -= distance / 10; // Keep existing distance penalty factor
            
            if (newScore > bestScore) {
                bestScore = newScore;
                bestTarget = enemy;
            }
        }
        
        return bestTarget;
    }

    getFormationOffset(index, totalUnits) {
        // V-formation for air units
        const spacing = 50;
        const rows = Math.ceil(Math.sqrt(totalUnits));
        const row = Math.floor(index / rows);
        const col = index % rows;
        
        return {
            x: (col - rows / 2) * spacing,
            y: row * spacing
        };
    }

    findSearchTarget(gameContext) {
        // Simple search pattern - could be more sophisticated
        const mapCenter = { x: gameContext.WORLD_SIZE / 2, y: gameContext.WORLD_SIZE / 2 };
        const angle = Math.random() * Math.PI * 2;
        const distance = 200 + Math.random() * 300;
        
        return {
            x: mapCenter.x + Math.cos(angle) * distance,
            y: mapCenter.y + Math.sin(angle) * distance
        };
    }

    findNearestEnemy(gameContext) {
        let nearest = null;
        let nearestDistance = Infinity;
        
        const leaderPos = this.leader;
        for (const unit of gameContext.units) {
            if (unit.team !== this.leader.team) {
                const distance = Math.sqrt(
                    (unit.x - leaderPos.x) ** 2 + (unit.y - leaderPos.y) ** 2
                );
                if (distance < nearestDistance && distance < this.searchRadius) {
                    nearest = unit;
                    nearestDistance = distance;
                }
            }
        }
        
        return nearest;
    }

    isNearFriendlyBase(node, gameContext) {
        const friendlyBuildings = gameContext.buildings.filter(b => b.team === this.leader.team);
        return friendlyBuildings.some(building => {
            const distance = Math.sqrt((building.x - node.x) ** 2 + (building.y - node.y) ** 2);
            return distance < 300;
        });
    }

    patrolAroundPoint(point, radius) {
        this.members.forEach((member, index) => {
            const angle = (index / this.members.length) * Math.PI * 2;
            member.patrolTarget = {
                x: point.x + Math.cos(angle) * radius,
                y: point.y + Math.sin(angle) * radius
            };
        });
    }

    maintainFormation(gameContext) {
        if (this.formation === 'loose') return;
        
        // Keep members within cohesion radius of leader
        const leaderPos = this.leader;
        this.members.forEach(member => {
            if (member === this.leader) return;
            
            const distance = Math.sqrt(
                (member.x - leaderPos.x) ** 2 + (member.y - leaderPos.y) ** 2
            );
            
            if (distance > this.cohesionRadius) {
                // Move towards leader
                const angle = Math.atan2(leaderPos.y - member.y, leaderPos.x - member.x);
                member.patrolTarget = {
                    x: leaderPos.x + Math.cos(angle) * (this.cohesionRadius * 0.8),
                    y: leaderPos.y + Math.sin(angle) * (this.cohesionRadius * 0.8)
                };
            }
        });
    }

    getInfo() {
        return {
            id: this.id,
            mission: this.mission,
            size: this.members.length,
            leader: this.leader.type.name,
            leaderRank: this.getRankName(this.getRank(this.leader)),
            status: this.status,
            efficiency: this.efficiency.toFixed(2),
            kills: this.kills,
            losses: this.losses
        };
    }
}

export class CommandHierarchy {
    constructor(team) {
        this.team = team;
        this.commandGroups = [];
        this.unassignedUnits = [];
        this.lastReorganization = 0;
        this.reorganizationInterval = 5000; // 5 seconds
        
        // Mission distribution weights
        this.missionWeights = {
            [MissionType.SEARCH]: 0.25,
            [MissionType.HUNT]: 0.35,
            [MissionType.COLLECT]: 0.15,
            [MissionType.DEFEND]: 0.15,
            [MissionType.ASSAULT]: 0.10
        };
    }

    update(gameContext) {
        const now = Date.now();
        
        // Update existing command groups
        this.commandGroups = this.commandGroups.filter(group => group.update(gameContext));
        
        // Reorganize periodically
        if (now - this.lastReorganization > this.reorganizationInterval) {
            this.reorganizeUnits(gameContext);
            this.lastReorganization = now;
        }
        
        // Balance mission distribution
        this.balanceMissionDistribution(gameContext);
    }

    reorganizeUnits(gameContext) {
        // Collect all units for this team
        const allUnits = gameContext.units.filter(unit => unit.team === this.team);
        
        // Find unassigned units
        this.unassignedUnits = allUnits.filter(unit => !unit.commandGroup);
        
        // Try to fill existing groups first
        this.fillExistingGroups();
        
        // Create new groups for remaining units
        this.createNewGroups(gameContext);
    }

    fillExistingGroups() {
        for (const group of this.commandGroups) {
            while (group.members.length < group.maxSize && this.unassignedUnits.length > 0) {
                // Find best candidate for this group
                const candidate = this.findBestCandidate(group, this.unassignedUnits);
                if (candidate) {
                    group.addMember(candidate);
                    this.unassignedUnits = this.unassignedUnits.filter(u => u !== candidate);
                } else {
                    break;
                }
            }
        }
    }

    createNewGroups(gameContext) {
        while (this.unassignedUnits.length >= 2) { // Minimum group size
            // Select leader (highest rank available)
            const leader = this.selectLeader(this.unassignedUnits);
            if (!leader) break;
            
            // Determine mission based on leader type and current needs
            const mission = this.selectMission(leader, gameContext);
            
            // Create new group
            const group = new CommandGroup(leader, mission);
            this.unassignedUnits = this.unassignedUnits.filter(u => u !== leader);
            
            // Add suitable members
            while (group.members.length < group.maxSize && this.unassignedUnits.length > 0) {
                const candidate = this.findBestCandidate(group, this.unassignedUnits);
                if (candidate) {
                    group.addMember(candidate);
                    this.unassignedUnits = this.unassignedUnits.filter(u => u !== candidate);
                } else {
                    break;
                }
            }
            
            this.commandGroups.push(group);
        }
    }

    selectLeader(candidates) {
        if (candidates.length === 0) return null;
        
        // Sort by rank (highest first)
        candidates.sort((a, b) => {
            const rankA = this.getRank(a);
            const rankB = this.getRank(b);
            return rankB - rankA;
        });
        
        return candidates[0];
    }

    selectMission(leader, gameContext) {
        const rank = this.getRank(leader);
        
        // Mission selection based on rank and unit type
        if (leader.type.name === 'Commander') {
            return MissionType.ASSAULT;
        } else if (leader.type.support) {
            return MissionType.SUPPORT;
        } else if (rank >= CommandRank.MAJOR) {
            return Math.random() < 0.6 ? MissionType.HUNT : MissionType.ASSAULT;
        } else if (leader.type.speed > 2.5) {
            return MissionType.SEARCH;
        } else {
            // Random based on current needs
            const missions = [MissionType.HUNT, MissionType.COLLECT, MissionType.DEFEND];
            return missions[Math.floor(Math.random() * missions.length)];
        }
    }

    findBestCandidate(group, candidates) {
        if (candidates.length === 0) return null;
        
        let bestScore = -1;
        let bestCandidate = null;
        
        for (const candidate of candidates) {
            const score = this.scoreCandidateForGroup(candidate, group);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        
        return bestCandidate;
    }

    scoreCandidateForGroup(unit, group) {
        let score = 0;
        
        // Mission compatibility
        switch (group.mission) {
            case MissionType.SEARCH:
                score += unit.type.speed > 2.5 ? 3 : 1;
                break;
            case MissionType.HUNT:
                score += unit.type.damage > 20 ? 3 : 1;
                break;
            case MissionType.COLLECT:
                score += unit.type.support ? 3 : 2;
                break;
            case MissionType.SUPPORT:
                score += unit.type.support ? 5 : 0;
                break;
            default:
                score += 1;
        }
        
        // Rank compatibility (prefer diverse ranks)
        const unitRank = this.getRank(unit);
        const hasRank = group.members.some(member => this.getRank(member) === unitRank);
        if (!hasRank) score += 2;
        
        // Distance to leader (prefer closer units)
        const distance = Math.sqrt(
            (unit.x - group.leader.x) ** 2 + (unit.y - group.leader.y) ** 2
        );
        score += Math.max(0, 3 - distance / 100);
        
        return score;
    }

    balanceMissionDistribution(gameContext) {
        const missionCounts = {};
        Object.values(MissionType).forEach(mission => missionCounts[mission] = 0);
        
        // Count current distribution
        this.commandGroups.forEach(group => {
            missionCounts[group.mission]++;
        });
        
        const totalGroups = this.commandGroups.length;
        if (totalGroups === 0) return;
        
        // Check if rebalancing is needed
        for (const [mission, weight] of Object.entries(this.missionWeights)) {
            const expectedCount = Math.floor(totalGroups * weight);
            const actualCount = missionCounts[mission];
            
            if (actualCount > expectedCount + 1) {
                // Too many groups with this mission, reassign some
                this.reassignMission(mission, actualCount - expectedCount);
            }
        }
    }

    reassignMission(fromMission, count) {
        const candidates = this.commandGroups.filter(group => group.mission === fromMission);
        candidates.sort((a, b) => a.efficiency - b.efficiency); // Reassign least efficient first
        
        for (let i = 0; i < Math.min(count, candidates.length); i++) {
            const group = candidates[i];
            const newMission = this.selectAlternateMission(fromMission);
            group.mission = newMission;
            group.maxSize = group.getMaxSizeForMission(newMission);
        }
    }

    selectAlternateMission(excludeMission) {
        const alternatives = Object.keys(this.missionWeights).filter(m => m !== excludeMission);
        return alternatives[Math.floor(Math.random() * alternatives.length)];
    }

    getRank(unit) {
        if (!unit.type) return CommandRank.PRIVATE;
        
        if (unit.type.name === 'Commander' || unit.type.name === 'ACU') {
            return CommandRank.COMMANDER;
        } else if (unit.type.tier >= 3) {
            return CommandRank.COLONEL;
        } else if (unit.type.tier >= 2) {
            return CommandRank.MAJOR;
        } else if (unit.type.damage > 30 || unit.type.support) {
            return CommandRank.CAPTAIN;
        } else if (unit.type.speed > 2.5) {
            return CommandRank.LIEUTENANT;
        } else {
            return CommandRank.PRIVATE;
        }
    }

    getDistribution() {
        const distribution = {
            total: this.commandGroups.length,
            missions: {},
            ranks: {},
            efficiency: 0
        };
        
        // Initialize counters
        Object.values(MissionType).forEach(mission => distribution.missions[mission] = 0);
        Object.values(CommandRank).forEach(rank => distribution.ranks[rank] = 0);
        
        let totalEfficiency = 0;
        
        this.commandGroups.forEach(group => {
            distribution.missions[group.mission]++;
            distribution.ranks[this.getRank(group.leader)]++;
            totalEfficiency += group.efficiency;
        });
        
        distribution.efficiency = this.commandGroups.length > 0 ? 
            (totalEfficiency / this.commandGroups.length).toFixed(2) : 0;
        
        return distribution;
    }

    getDetailedInfo() {
        return {
            team: this.team,
            distribution: this.getDistribution(),
            groups: this.commandGroups.map(group => group.getInfo()),
            unassigned: this.unassignedUnits.length
        };
    }
}