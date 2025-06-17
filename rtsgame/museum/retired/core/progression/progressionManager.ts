export class ProgressionManager {
    constructor() {
        this.tierThresholds = {
            TIER_1: { mass: 400, energy: 400, extractors: 2, factories: 1 },
            TIER_2: { mass: 1000, energy: 1000, extractors: 4, factories: 2 },
            TIER_3: { mass: 2000, energy: 2000, extractors: 7, factories: 3 }
        };

        this.veterancyLevels = {
            GREEN: { minPoints: 0, maxPoints: 24, bonus: 0 },
            REGULAR: { minPoints: 25, maxPoints: 74, bonus: 2 },
            VETERAN: { minPoints: 75, maxPoints: 149, bonus: 5 },
            ELITE: { minPoints: 150, maxPoints: 299, bonus: 10 },
            HERO: { minPoints: 300, maxPoints: Infinity, bonus: 15 }
        };
    }

    calculateUnitVeterancy(unit) {
        const experiencePoints = unit.combatExperience + 
                               (unit.survivalTime / 60) + 
                               (unit.commandExperience * 3) + 
                               (unit.killCount * 2);

        for (const [level, data] of Object.entries(this.veterancyLevels)) {
            if (experiencePoints >= data.minPoints && experiencePoints <= data.maxPoints) {
                return {
                    level,
                    bonus: data.bonus,
                    points: experiencePoints
                };
            }
        }
    }

    checkTierProgression(gameContext) {
        const { resources, buildings } = gameContext;
        const currentTier = this.getCurrentTier(gameContext);

        for (const [tier, requirements] of Object.entries(this.tierThresholds)) {
            if (currentTier === tier) continue;

            const canProgress = 
                resources.mass >= requirements.mass &&
                resources.energy >= requirements.energy &&
                buildings.extractors >= requirements.extractors &&
                buildings.factories >= requirements.factories;

            if (canProgress) {
                return {
                    newTier: tier,
                    requirements
                };
            }
        }

        return null;
    }

    getCurrentTier(gameContext) {
        const { resources, buildings } = gameContext;

        if (buildings.factories >= 3 && resources.mass >= 2000) {
            return 'TIER_3';
        } else if (buildings.factories >= 2 && resources.mass >= 1000) {
            return 'TIER_2';
        }
        return 'TIER_1';
    }

    updateUnitProgression(unit, gameContext) {
        const veterancyData = this.calculateUnitVeterancy(unit);
        if (veterancyData && veterancyData.level !== unit.veterancyLevel) {
            this.processPromotion(unit, veterancyData, gameContext);
        }

        // Update survival time in combat zones
        const inCombat = unit.target || 
                        unit.hp < unit.maxHp || 
                        this.isUnderFire(unit, gameContext);
        
        if (inCombat) {
            unit.survivalTime += gameContext.deltaTime;
        }
    }

    processPromotion(unit, veterancyData, gameContext) {
        const oldLevel = unit.veterancyLevel;
        unit.veterancyLevel = veterancyData.level;
        unit.veterancyAuthorityModifier = veterancyData.bonus;

        // Add visual feedback
        if (gameContext.captions) {
            gameContext.captions.push(new gameContext.Caption(
                unit.x, unit.y,
                `Promoted to ${veterancyData.level}`,
                '#ff4', 14
            ));
        }

        // Log promotion event
        if (gameContext.addEvent) {
            gameContext.addEvent(gameContext, 'promotion', 
                `${unit.team} ${unit.type.name} promoted to ${veterancyData.level}`, 2);
        }
    }

    isUnderFire(unit, gameContext) {
        return gameContext.projectiles.some(p => 
            p.target === unit && 
            p.team !== unit.team
        );
    }
} 