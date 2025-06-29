// js/core/trikeshedEntityManager.js - TrikeShed-based Entity Management

// Temporary inline TrikeShed implementation until proper TypeScript support is added
const j = (a, b) => ({ a, b });
const emptySeries = () => j(0, () => { throw new Error("Empty series"); });
const getSeriesValue = (series, i) => {
    if (i < 0 || i >= series.a) throw new RangeError(`Index ${i} out of bounds`);
    return series.b(i);
};
const seriesSize = (series) => series.a;

/**
 * TrikeShed-based Entity Manager using tensor architecture for deterministic state management
 * This replaces the basic array-based EntityManager with proper TrikeShed data structures
 */
export class TrikeShedEntityManager {
    constructor() {
        // Initialize with empty Series for each entity type
        this.unitSeries = emptySeries();
        this.buildingSeries = emptySeries();
        this.projectileSeries = emptySeries();
        this.effectSeries = emptySeries();
        this.captionSeries = emptySeries();
        
        // Maintain ID counters for deterministic entity creation
        this.nextUnitId = 0;
        this.nextBuildingId = 0;
        this.nextProjectileId = 0;
        this.nextEffectId = 0;
        this.nextCaptionId = 0;
        
        // Track active entity count for performance
        this.activeUnitCount = 0;
        this.activeBuildingCount = 0;
        this.activeProjectileCount = 0;
        this.activeEffectCount = 0;
        this.activeCaptionCount = 0;
    }

    /**
     * Convert a JavaScript array to a TrikeShed Series
     * @param {Array} array - JavaScript array to convert
     * @returns {Series} TrikeShed Series representation
     */
    arrayToSeries(array) {
        if (!array || array.length === 0) {
            return emptySeries();
        }
        
        return j(array.length, (index) => {
            if (index < 0 || index >= array.length) {
                throw new RangeError(`Index ${index} out of bounds for array of length ${array.length}`);
            }
            return array[index];
        });
    }

    /**
     * Convert a TrikeShed Series to a JavaScript array (for compatibility)
     * @param {Series} series - TrikeShed Series to convert
     * @returns {Array} JavaScript array representation
     */
    seriesToArray(series) {
        const result = [];
        const size = seriesSize(series);
        
        for (let i = 0; i < size; i++) {
            result.push(getSeriesValue(series, i));
        }
        
        return result;
    }

    /**
     * Add a unit to the TrikeShed-managed unit series
     * @param {Unit} unit - Unit to add
     */
    addUnit(unit) {
        // Assign deterministic ID
        unit.id = this.nextUnitId++;
        
        // Convert current series to array, add unit, convert back
        const currentUnits = this.seriesToArray(this.unitSeries);
        currentUnits.push(unit);
        this.unitSeries = this.arrayToSeries(currentUnits);
        this.activeUnitCount++;
        
        console.log(`[TrikeShed] Added unit ${unit.id} (${unit.type}) to series. Total units: ${this.activeUnitCount}`);
    }

    /**
     * Add a building to the TrikeShed-managed building series
     * @param {Building} building - Building to add
     */
    addBuilding(building) {
        building.id = this.nextBuildingId++;
        
        const currentBuildings = this.seriesToArray(this.buildingSeries);
        currentBuildings.push(building);
        this.buildingSeries = this.arrayToSeries(currentBuildings);
        this.activeBuildingCount++;
        
        console.log(`[TrikeShed] Added building ${building.id} (${building.type}) to series. Total buildings: ${this.activeBuildingCount}`);
    }

    /**
     * Add a projectile to the TrikeShed-managed projectile series
     * @param {Projectile} projectile - Projectile to add
     */
    addProjectile(projectile) {
        projectile.id = this.nextProjectileId++;
        
        const currentProjectiles = this.seriesToArray(this.projectileSeries);
        currentProjectiles.push(projectile);
        this.projectileSeries = this.arrayToSeries(currentProjectiles);
        this.activeProjectileCount++;
    }

    /**
     * Add an effect to the TrikeShed-managed effect series
     * @param {Effect} effect - Effect to add
     */
    addEffect(effect) {
        effect.id = this.nextEffectId++;
        
        const currentEffects = this.seriesToArray(this.effectSeries);
        currentEffects.push(effect);
        this.effectSeries = this.arrayToSeries(currentEffects);
        this.activeEffectCount++;
    }

    /**
     * Add a caption to the TrikeShed-managed caption series
     * @param {Caption} caption - Caption to add
     */
    addCaption(caption) {
        caption.id = this.nextCaptionId++;
        
        const currentCaptions = this.seriesToArray(this.captionSeries);
        currentCaptions.push(caption);
        this.captionSeries = this.arrayToSeries(currentCaptions);
        this.activeCaptionCount++;
    }

    /**
     * Get all units as an iterable for compatibility with existing code
     * @returns {Array} Array of all units
     */
    get units() {
        return this.seriesToArray(this.unitSeries);
    }

    /**
     * Get all buildings as an iterable for compatibility with existing code
     * @returns {Array} Array of all buildings
     */
    get buildings() {
        return this.seriesToArray(this.buildingSeries);
    }

    /**
     * Get all projectiles as an iterable for compatibility with existing code
     * @returns {Array} Array of all projectiles
     */
    get projectiles() {
        return this.seriesToArray(this.projectileSeries);
    }

    /**
     * Get all effects as an iterable for compatibility with existing code
     * @returns {Array} Array of all effects
     */
    get effects() {
        return this.seriesToArray(this.effectSeries);
    }

    /**
     * Get all captions as an iterable for compatibility with existing code
     * @returns {Array} Array of all captions
     */
    get captions() {
        return this.seriesToArray(this.captionSeries);
    }

    /**
     * Update all entities using TrikeShed series processing
     * @param {Simulation} simulation - Simulation context
     * @param {number} deltaTime - Time step
     */
    update(simulation, deltaTime) {
        // Process units using TrikeShed series iteration
        this.updateUnitSeries(simulation, deltaTime);
        this.updateBuildingSeries(simulation, deltaTime);
        this.updateProjectileSeries(simulation, deltaTime);
        this.updateEffectSeries(simulation, deltaTime);
        this.updateCaptionSeries(simulation, deltaTime);
    }

    /**
     * Update units using TrikeShed series processing
     */
    updateUnitSeries(simulation, deltaTime) {
        const units = this.seriesToArray(this.unitSeries);
        const survivingUnits = [];

        // Process each unit deterministically
        for (let i = 0; i < units.length; i++) {
            const unit = units[i];
            unit.update(simulation, deltaTime);
            
            if (unit.hp <= 0 || unit.isDead) {
                // Check for commander death (game over condition)
                if (unit.type === 'commander') {
                    const winner = unit.team === 'blue' ? 'RED' : 'BLUE';
                    simulation.gameState.winner = winner;
                    simulation.gameState.addEvent('game_over', `${winner} team wins! Enemy commander destroyed!`, 3);
                }
                this.activeUnitCount--;
                console.log(`[TrikeShed] Unit ${unit.id} destroyed. Remaining units: ${this.activeUnitCount}`);
            } else {
                survivingUnits.push(unit);
            }
        }

        // Update series with surviving units
        this.unitSeries = this.arrayToSeries(survivingUnits);
    }

    /**
     * Update buildings using TrikeShed series processing
     */
    updateBuildingSeries(simulation, deltaTime) {
        const buildings = this.seriesToArray(this.buildingSeries);
        const survivingBuildings = [];

        for (let i = 0; i < buildings.length; i++) {
            const building = buildings[i];
            building.update(simulation, deltaTime);
            
            if (building.hp <= 0) {
                this.activeBuildingCount--;
                console.log(`[TrikeShed] Building ${building.id} destroyed. Remaining buildings: ${this.activeBuildingCount}`);
            } else {
                survivingBuildings.push(building);
            }
        }

        this.buildingSeries = this.arrayToSeries(survivingBuildings);
    }

    /**
     * Update projectiles using TrikeShed series processing
     */
    updateProjectileSeries(simulation, deltaTime) {
        const projectiles = this.seriesToArray(this.projectileSeries);
        const activeProjectiles = [];

        for (let i = 0; i < projectiles.length; i++) {
            const projectile = projectiles[i];
            projectile.update(simulation, deltaTime);
            
            if (!projectile.shouldDestroy) {
                activeProjectiles.push(projectile);
            } else {
                this.activeProjectileCount--;
            }
        }

        this.projectileSeries = this.arrayToSeries(activeProjectiles);
    }

    /**
     * Update effects using TrikeShed series processing
     */
    updateEffectSeries(simulation, deltaTime) {
        const effects = this.seriesToArray(this.effectSeries);
        const activeEffects = [];

        for (let i = 0; i < effects.length; i++) {
            const effect = effects[i];
            effect.update();
            
            if (effect.life > 0) {
                activeEffects.push(effect);
            } else {
                this.activeEffectCount--;
            }
        }

        this.effectSeries = this.arrayToSeries(activeEffects);
    }

    /**
     * Update captions using TrikeShed series processing
     */
    updateCaptionSeries(simulation, deltaTime) {
        const captions = this.seriesToArray(this.captionSeries);
        const activeCaptions = [];

        for (let i = 0; i < captions.length; i++) {
            const caption = captions[i];
            caption.update();
            
            if (caption.life > 0) {
                activeCaptions.push(caption);
            } else {
                this.activeCaptionCount--;
            }
        }

        this.captionSeries = this.arrayToSeries(activeCaptions);
    }

    /**
     * Get series statistics for debugging and monitoring
     * @returns {Object} Statistics about entity series
     */
    getSeriesStats() {
        return {
            units: {
                seriesSize: seriesSize(this.unitSeries),
                activeCount: this.activeUnitCount,
                nextId: this.nextUnitId
            },
            buildings: {
                seriesSize: seriesSize(this.buildingSeries),
                activeCount: this.activeBuildingCount,
                nextId: this.nextBuildingId
            },
            projectiles: {
                seriesSize: seriesSize(this.projectileSeries),
                activeCount: this.activeProjectileCount,
                nextId: this.nextProjectileId
            },
            effects: {
                seriesSize: seriesSize(this.effectSeries),
                activeCount: this.activeEffectCount,
                nextId: this.nextEffectId
            },
            captions: {
                seriesSize: seriesSize(this.captionSeries),
                activeCount: this.activeCaptionCount,
                nextId: this.nextCaptionId
            }
        };
    }

    /**
     * Clear all entity series (for game reset)
     */
    clear() {
        this.unitSeries = emptySeries();
        this.buildingSeries = emptySeries();
        this.projectileSeries = emptySeries();
        this.effectSeries = emptySeries();
        this.captionSeries = emptySeries();
        
        this.activeUnitCount = 0;
        this.activeBuildingCount = 0;
        this.activeProjectileCount = 0;
        this.activeEffectCount = 0;
        this.activeCaptionCount = 0;
        
        console.log('[TrikeShed] All entity series cleared');
    }
}