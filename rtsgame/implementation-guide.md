# Implementation Guide: Enhanced Command Hierarchy & Progression Systems

## Overview

This implementation guide provides detailed technical specifications for enhancing the RTS game's command hierarchy and player progression systems, addressing authority calculation overlaps, delegation depth limitations, and health/veterancy-based dynamic command structures.

## Phase 1: Core Authority System Refactoring

### 1.1 Enhanced Unit Properties

**File**: [`js/core/unit.js`](js/core/unit.js)

**Add New Properties to Unit Constructor**:

```javascript
// Enhanced authority properties
this.baseAuthority = this.calculateBaseAuthority();
this.healthAuthorityModifier = 0;
this.veterancyAuthorityModifier = 0;
this.contextAuthorityModifier = 0; // For situational bonuses like defensive stances, mission objectives
this.computroniumAuthorityModifier = 0; // For C&C capabilities derived from Computronium core level/focus
this.effectiveAuthority = this.baseAuthority;

// Veterancy tracking
this.combatExperience = 0;
this.survivalTime = 0;
this.commandExperience = 0;
this.killCount = 0;
this.damageDelt = 0;
this.lastPromotionTime = 0;
this.veterancyLevel = 'GREEN';

// Health-based command fitness
this.commandFitness = 'FULL_COMMAND';
this.lastAuthorityUpdate = 0;
this.commandSuccesses = 0;
this.commandFailures = 0;
```

### 1.2 Multi-Dimensional Authority Calculation

**New Method**: `calculateEffectiveAuthority()`

```javascript
calculateEffectiveAuthority() {
    const healthRatio = this.hp / this.maxHp;
    
    // Health bias calculation
    if (healthRatio >= 0.8) {
        this.healthAuthorityModifier = 5;
        this.commandFitness = 'FULL_COMMAND';
    } else if (healthRatio >= 0.6) {
        this.healthAuthorityModifier = 2;
        this.commandFitness = 'REDUCED_AUTHORITY';
    } else if (healthRatio >= 0.4) {
        this.healthAuthorityModifier = -2;
        this.commandFitness = 'COMPROMISED_COMMAND';
    } else if (healthRatio >= 0.2) {
        this.healthAuthorityModifier = -5;
        this.commandFitness = 'CRITICAL_STATUS';
    } else {
        this.healthAuthorityModifier = -10;
        this.commandFitness = 'COMBAT_INEFFECTIVE';
    }
    
    // Veterancy bias calculation
    const experiencePoints = this.combatExperience + 
                           (this.survivalTime / 60) + 
                           (this.commandExperience * 3) + 
                           (this.killCount * 2);
    
    if (experiencePoints >= 300) {
        this.veterancyLevel = 'HERO';
        this.veterancyAuthorityModifier = 15;
    } else if (experiencePoints >= 150) {
        this.veterancyLevel = 'ELITE';
        this.veterancyAuthorityModifier = 10;
    } else if (experiencePoints >= 75) {
        this.veterancyLevel = 'VETERAN';
        this.veterancyAuthorityModifier = 5;
    } else if (experiencePoints >= 25) {
        this.veterancyLevel = 'REGULAR';
        this.veterancyAuthorityModifier = 2;
    } else {
        this.veterancyLevel = 'GREEN';
        this.veterancyAuthorityModifier = 0;
    }
    
    // Placeholder for Computronium-based C&C modifier calculation
    // This would depend on this.computroniumCoreLevel and potentially this.coreFocusMode (see GDD 6.4)
    // Example: if (this.coreFocusMode === 'C&C_MERCURY') this.computroniumAuthorityModifier = 5;
    // else this.computroniumAuthorityModifier = this.computroniumCoreLevel * 1; // Simplified example

    // Calculate final effective authority
    this.effectiveAuthority = this.baseAuthority +
                            this.healthAuthorityModifier +
                            this.veterancyAuthorityModifier +
                            this.contextAuthorityModifier +
                            this.computroniumAuthorityModifier; // Added Computronium modifier
    
    return this.effectiveAuthority;
}
```

### 1.3 Real-Time Authority Updates

**Enhanced Update Method**:

```javascript
update(gameContext) {
    // ... existing update logic ...
    
    // Update authority every 5 seconds
    const now = performance.now();
    if (now - this.lastAuthorityUpdate > 5000) {
        this.calculateEffectiveAuthority();
        this.lastAuthorityUpdate = now;
        
        // Check for command succession needs
        if (this.commandFitness === 'COMBAT_INEFFECTIVE' || 
            this.commandFitness === 'CRITICAL_STATUS') {
            this.triggerCommandSuccession(gameContext);
        }
    }
    
    // Update veterancy tracking
    this.updateVeterancyProgress(gameContext);
    
    // ... rest of existing update logic ...
}
```

## Phase 2: Command Succession System

### 2.1 Command Succession Protocol

**New Method**: `triggerCommandSuccession(gameContext)`

```javascript
triggerCommandSuccession(gameContext) {
    const { units } = gameContext;
    
    // Find suitable replacement commander
    const nearbyAllies = units.filter(u => 
        u.team === this.team && 
        u !== this && 
        this.getDistance(u) < 300 &&
        u.commandFitness === 'FULL_COMMAND'
    );
    
    if (nearbyAllies.length === 0) return;
    
    // Select best replacement based on effective authority
    let bestReplacement = null;
    let highestAuthority = 0;
    
    for (const ally of nearbyAllies) {
        ally.calculateEffectiveAuthority();
        if (ally.effectiveAuthority > highestAuthority) {
            highestAuthority = ally.effectiveAuthority;
            bestReplacement = ally;
        }
    }
    
    if (bestReplacement && bestReplacement.effectiveAuthority > this.effectiveAuthority) {
        this.transferCommand(bestReplacement, gameContext);
    }
}
```

### 2.2 Command Transfer Implementation

**New Method**: `transferCommand(newCommander, gameContext)`

```javascript
transferCommand(newCommander, gameContext) {
    const { units, captions, Caption } = gameContext;
    
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
    captions.push(new Caption(
        newCommander.x, newCommander.y,
        `Command transferred to ${newCommander.veterancyLevel} ${newCommander.type.name}`,
        '#ff4', 14
    ));
    
    // Log succession event
    if (gameContext.addEvent) {
        gameContext.addEvent(gameContext, 'command_succession', 
            `${this.team} command transferred due to combat ineffectiveness`, 2);
    }
}
```

## Phase 3: Veterancy Progression System

### 3.1 Experience Gain Mechanics

**New Method**: `updateVeterancyProgress(gameContext)`

```javascript
updateVeterancyProgress(gameContext) {
    const deltaTime = gameContext.deltaTime || (1/60);
    
    // Survival time in combat zones
    const inCombat = this.target || 
                    this.hp < this.maxHp || 
                    this.isUnderFire(gameContext);
    
    if (inCombat) {
        this.survivalTime += deltaTime;
    }
    
    // Check for promotion eligibility
    const oldLevel = this.veterancyLevel;
    this.calculateEffectiveAuthority(); // Updates veterancy level
    
    if (oldLevel !== this.veterancyLevel) {
        this.processPromotion(oldLevel, gameContext);
    }
}
```

### 3.2 Combat Experience Tracking

**Enhanced Attack Method**:

```javascript
attack(target, gameContext) {
    // ... existing attack logic ...
    
    // Track combat experience
    if (target.hp > 0) {
        this.combatExperience += 1;
        this.damageDelt += damage;
        
        // Track kills
        if (target.hp <= 0) {
            this.killCount += 1;
            
            // Bonus experience for high-value targets
            if (target.type === UNIT_TYPES.commander) {
                this.combatExperience += 10;
            } else if (target.type.tier >= 2) {
                this.combatExperience += 3;
            }
        }
    }
    
    // ... rest of existing attack logic ...
}
```

### 3.3 Promotion System

**New Method**: `processPromotion(oldLevel, gameContext)`

```javascript
processPromotion(oldLevel, gameContext) {
    const { captions, Caption, addEvent } = gameContext;
    
    // Prevent spam promotions
    const now = performance.now();
    if (now - this.lastPromotionTime < 30000) return; // 30 second cooldown
    
    this.lastPromotionTime = now;
    
    // Apply veterancy benefits
    this.applyVeterancyBenefits();
    
    // Visual feedback
    captions.push(new Caption(
        this.x, this.y,
        `${this.type.name} promoted to ${this.veterancyLevel}!`,
        '#4f4', 16
    ));
    
    // Strategic event
    if (addEvent) {
        addEvent(gameContext, 'promotion', 
            `${this.team} ${this.type.name} promoted to ${this.veterancyLevel}`, 2);
    }
}
```

### 3.4 Veterancy Benefits Application

**New Method**: `applyVeterancyBenefits()`

```javascript
applyVeterancyBenefits() {
    const baseDamage = this.type.damage;
    const baseSpeed = this.type.speed;
    const baseRange = this.type.range;
    
    switch (this.veterancyLevel) {
        case 'REGULAR':
            this.damage = baseDamage * 1.1;
            this.speed = baseSpeed * 1.05;
            break;
        case 'VETERAN':
            this.damage = baseDamage * 1.2;
            this.speed = baseSpeed * 1.1;
            this.range = baseRange * 1.1;
            break;
        case 'ELITE':
            this.damage = baseDamage * 1.3;
            this.speed = baseSpeed * 1.15;
            this.range = baseRange * 1.2;
            this.canPromoteSubordinates = true;
            break;
        case 'HERO':
            this.damage = baseDamage * 1.4;
            this.speed = baseSpeed * 1.2;
            this.range = baseRange * 1.3;
            this.provideMoraleBonus = true;
            this.canPromoteSubordinates = true;
            break;
    }
}
```

## Phase 4: Enhanced Command Hierarchy Logic

### 4.1 Authority-Based Command Selection

**Enhanced Method**: `followSuperiorOrders(gameContext)`

```javascript
followSuperiorOrders(gameContext) {
    const { units } = gameContext;
    
    // Find commanding officer with improved selection
    let commander = null;
    let highestEffectiveAuthority = 0;
    
    for (const ally of units) {
        if (ally.team === this.team && 
            ally !== this &&
            this.getDistance(ally) < 300) {
            
            // Calculate effective authority for comparison
            ally.calculateEffectiveAuthority();
            
            if (ally.effectiveAuthority > this.effectiveAuthority &&
                ally.effectiveAuthority > highestEffectiveAuthority &&
                ally.commandFitness === 'FULL_COMMAND') {
                
                highestEffectiveAuthority = ally.effectiveAuthority;
                commander = ally;
            }
        }
    }
    
    if (commander) {
        this.currentCommander = commander;
        
        // Follow commander's directives
        if (commander.target && !this.target && 
            this.getDistance(commander.target) < this.type.range * 2) {
            this.target = commander.target;
        }
        
        // Maintain formation distance based on effective authority difference
        const authorityGap = commander.effectiveAuthority - this.effectiveAuthority;
        const formationDistance = Math.max(80, Math.min(150, 80 + authorityGap * 2));
        
        const distToCommander = this.getDistance(commander);
        if (distToCommander > formationDistance + 50 && !this.target) {
            this.patrolTarget = { x: commander.x, y: commander.y };
        }
    }
}
```

### 4.2 Dynamic Formation Management

**Enhanced Method**: `executeGroupMovement(gameContext)`

```javascript
executeGroupMovement(gameContext) {
    const { units } = gameContext;
    
    // Find group leader based on effective authority
    let groupLeader = null;
    let highestEffectiveAuthority = this.effectiveAuthority;
    
    for (const ally of units) {
        if (ally.team === this.team && 
            this.getDistance(ally) < 400) {
            
            ally.calculateEffectiveAuthority();
            if (ally.effectiveAuthority > highestEffectiveAuthority &&
                ally.commandFitness === 'FULL_COMMAND') {
                highestEffectiveAuthority = ally.effectiveAuthority;
                groupLeader = ally;
            }
        }
    }
    
    if (groupLeader && groupLeader !== this) {
        const leaderDist = this.getDistance(groupLeader);
        
        // Dynamic formation distance based on veterancy and authority
        const baseDistance = 80;
        const veterancyModifier = groupLeader.veterancyLevel === 'HERO' ? 20 : 
                                groupLeader.veterancyLevel === 'ELITE' ? 15 : 10;
        const idealDistance = baseDistance + veterancyModifier;
        
        if (leaderDist > idealDistance + 30) {
            // Enhanced pathfinding integration
            this.patrolTarget = { 
                x: groupLeader.x + Math.cos(this.formationAngle || 0) * idealDistance,
                y: groupLeader.y + Math.sin(this.formationAngle || 0) * idealDistance
            };
            
            // Clear conflicting combat orders for formation movement
            if (this.target && this.getDistance(this.target) > this.type.range * 1.5) {
                this.target = null;
            }
        }
    }
}
```

## Phase 5: Integration Testing Framework

### 5.1 Authority Validation Tests

**Test Suite**: `validateAuthoritySystem(gameContext)`

```javascript
function validateAuthoritySystem(gameContext) {
    const { units } = gameContext;
    const testResults = {
        authorityCollisions: 0,
        commandChainBreaks: 0,
        successionFailures: 0,
        veterancyErrors: 0
    };
    
    // Test 1: Authority collision detection
    const authorityMap = new Map();
    units.forEach(unit => {
        unit.calculateEffectiveAuthority();
        const auth = unit.effectiveAuthority;
        
        if (authorityMap.has(auth)) {
            const existingUnit = authorityMap.get(auth);
            if (existingUnit.team === unit.team && 
                existingUnit.getDistance(unit) < 300) {
                testResults.authorityCollisions++;
                console.warn(`Authority collision: ${unit.type.name} and ${existingUnit.type.name} both have authority ${auth}`);
            }
        } else {
            authorityMap.set(auth, unit);
        }
    });
    
    // Test 2: Command chain validation
    units.forEach(unit => {
        if (unit.currentCommander) {
            unit.currentCommander.calculateEffectiveAuthority();
            if (unit.currentCommander.effectiveAuthority <= unit.effectiveAuthority) {
                testResults.commandChainBreaks++;
                console.warn(`Command chain break: ${unit.type.name} following lower authority commander`);
            }
        }
    });
    
    // Test 3: Succession system validation
    units.forEach(unit => {
        if (unit.commandFitness === 'COMBAT_INEFFECTIVE') {
            const hasValidSuccessor = units.some(ally => 
                ally.team === unit.team && 
                ally !== unit &&
                ally.effectiveAuthority > unit.effectiveAuthority &&
                ally.commandFitness === 'FULL_COMMAND' &&
                ally.getDistance(unit) < 300
            );
            
            if (!hasValidSuccessor) {
                testResults.successionFailures++;
                console.warn(`Succession failure: No valid replacement for ${unit.type.name}`);
            }
        }
    });
    
    return testResults;
}
```

### 5.2 Performance Benchmarking

**Benchmark Suite**: `benchmarkAuthoritySystem(gameContext)`

```javascript
function benchmarkAuthoritySystem(gameContext) {
    const { units } = gameContext;
    const startTime = performance.now();
    
    // Benchmark authority calculations
    units.forEach(unit => {
        unit.calculateEffectiveAuthority();
    });
    
    const authorityTime = performance.now() - startTime;
    
    // Benchmark command hierarchy updates
    const hierarchyStart = performance.now();
    units.forEach(unit => {
        if (unit.effectiveAuthority > 15) {
            unit.issueStrategicOrders(gameContext);
        } else {
            unit.followSuperiorOrders(gameContext);
        }
    });
    
    const hierarchyTime = performance.now() - hierarchyStart;
    
    return {
        authorityCalculationTime: authorityTime,
        hierarchyUpdateTime: hierarchyTime,
        totalTime: authorityTime + hierarchyTime,
        unitsProcessed: units.length,
        averageTimePerUnit: (authorityTime + hierarchyTime) / units.length
    };
}
```

## Phase 6: Configuration & Tuning

### 6.1 Configurable Parameters

**New Configuration File**: `js/config/commandConfig.js`

```javascript
export const COMMAND_CONFIG = {
    // Authority calculation weights
    AUTHORITY_WEIGHTS: {
        BASE_TIER_MULTIPLIER: 10,
        SUPPORT_BONUS: 5,
        HEALTH_MAX_MODIFIER: 5,
        VETERANCY_MAX_MODIFIER: 15,
        CONTEXT_MAX_MODIFIER: 3
    },
    
    // Veterancy thresholds
    VETERANCY_THRESHOLDS: {
        REGULAR: 25,
        VETERAN: 75,
        ELITE: 150,
        HERO: 300
    },
    
    // Command ranges
    COMMAND_RANGES: {
        STRATEGIC: 400,
        TACTICAL: 200,
        SQUAD: 120,
        INDIVIDUAL: 80
    },
    
    // Update intervals (milliseconds)
    UPDATE_INTERVALS: {
        AUTHORITY_RECALC: 5000,
        VETERANCY_CHECK: 1000,
        COMMAND_VALIDATION: 10000
    },
    
    // Health-based command fitness thresholds
    HEALTH_THRESHOLDS: {
        FULL_COMMAND: 0.8,
        REDUCED_AUTHORITY: 0.6,
        COMPROMISED_COMMAND: 0.4,
        CRITICAL_STATUS: 0.2,
        COMBAT_INEFFECTIVE: 0.0
    }
};
```

### 6.2 Debug and Monitoring Tools

**Debug Overlay**: `renderCommandHierarchyDebug(gameContext)`

```javascript
function renderCommandHierarchyDebug(ctx, gameContext) {
    const { units, camera } = gameContext;
    
    units.forEach(unit => {
        const screenX = (unit.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
        const screenY = (unit.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
        
        // Authority display
        ctx.fillStyle = '#fff';
        ctx.font = '10px Arial';
        ctx.fillText(`Auth: ${unit.effectiveAuthority}`, screenX, screenY - 20);
        ctx.fillText(`${unit.veterancyLevel}`, screenX, screenY - 10);
        
        // Command lines
        if (unit.currentCommander) {
            const commanderScreenX = (unit.currentCommander.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
            const commanderScreenY = (unit.currentCommander.y - camera.y) * camera.zoom + camera.canvasHeight / 2;
            
            ctx.strokeStyle = '#ff0';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(screenX, screenY);
            ctx.lineTo(commanderScreenX, commanderScreenY);
            ctx.stroke();
        }
        
        // Authority radius
        if (unit.effectiveAuthority > 20) {
            ctx.strokeStyle = '#0f0';
            ctx.globalAlpha = 0.3;
            ctx.beginPath();
            ctx.arc(screenX, screenY, 200 * camera.zoom, 0, Math.PI * 2);
            ctx.stroke();
            ctx.globalAlpha = 1;
        }
    });
}
```

## Implementation Timeline

### Week 1: Foundation (Authority System)

- **Day 1-2**: Implement enhanced unit properties and authority calculation
- **Day 3-4**: Add health bias and command fitness assessment
- **Day 5-7**: Testing and debugging authority system

### Week 2: Veterancy & Progression

- **Day 8-10**: Implement veterancy tracking and progression
- **Day 11-12**: Add promotion system and benefits
- **Day 13-14**: Integration testing with authority system

### Week 3: Command Hierarchy

- **Day 15-17**: Enhance command succession and transfer protocols
- **Day 18-19**: Implement dynamic formation management
- **Day 20-21**: Testing large-scale army scenarios

### Week 4: Optimization & Polish

- **Day 22-24**: Performance optimization and configuration tuning
- **Day 25-26**: Debug tools and monitoring systems
- **Day 27-28**: Final integration testing and documentation

## Success Criteria

### Performance Targets

- Authority calculation: <2ms for 100 units
- Command hierarchy update: <5ms for complex armies
- Veterancy progression: <1ms per unit per second
- Memory overhead: <10% increase for tracking data

### Functional Requirements

- Zero authority collisions in same-team proximity
- Automatic command succession within 5 seconds
- Veterancy progression visible within 1 minute of qualifying actions
- Command hierarchy depth scaling to 500+ units

### Quality Metrics

- 95% reduction in command conflicts
- 80% improvement in large army management
- 60% reduction in player micromanagement requirements
- 90% player satisfaction with promotion system

This implementation guide provides the technical foundation for creating a sophisticated, scalable command hierarchy system that addresses the critical structural issues identified in the strategic analysis.
