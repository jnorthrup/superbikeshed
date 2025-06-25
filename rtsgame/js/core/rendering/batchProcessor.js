// Batch Processor - High locality batch processing for game entities
// Processes entities in groups to maximize cache efficiency and reduce overhead

export class BatchProcessor {
    constructor(batchSize = 64) {
        this.batchSize = batchSize;
        this.tempBatch = [];
        this.processingQueues = {
            units: [],
            buildings: [],
            projectiles: [],
            effects: []
        };
        
        // Pre-allocated arrays for batch processing
        this.unitBatch = new Array(this.batchSize);
        this.buildingBatch = new Array(this.batchSize);
        this.updateResults = {
            unitsToRemove: [],
            buildingsToRemove: [],
            projectilesToRemove: [],
            effectsToRemove: []
        };

        // Performance tracking
        this.stats = {
            frameCount: 0,
            totalUpdates: 0,
            batchUpdates: 0,
            avgBatchSize: 0
        };
    }

    processUnits(units, deltaTime, spatialIndex) {
        const unitArray = Array.from(units.values());
        
        // Process units in batches
        for (let i = 0; i < unitArray.length; i += this.batchSize) {
            const batch = unitArray.slice(i, i + this.batchSize);
            this.processUnitBatch(batch, deltaTime, spatialIndex);
        }
    }
    
    processUnitBatch(batch, deltaTime, spatialIndex) {
        // Phase 1: Movement/Position Updates
        for (const unit of batch) {
            if (unit.target) {
                const dx = unit.target.x - unit.x;
                const dy = unit.target.y - unit.y;
                const distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance > 1) {
                    const speed = unit.speed * deltaTime;
                    unit.x += (dx / distance) * speed;
                    unit.y += (dy / distance) * speed;
                }
            }
            
            // Update cooldowns
            if (unit.cooldown > 0) {
                unit.cooldown -= deltaTime;
            }
        }
        
        // Phase 2: Combat/Target Acquisition
        for (const unit of batch) {
            if (unit.cooldown <= 0) {
                const nearbyEnemies = spatialIndex.getNearbyUnits(
                    { x: unit.x, y: unit.y },
                    unit.range
                ).filter(u => u.team !== unit.team);
                
                if (nearbyEnemies.length > 0) {
                    // Find closest enemy
                    let closestEnemy = null;
                    let closestDistance = Infinity;
                    
                    for (const enemy of nearbyEnemies) {
                        const dx = enemy.x - unit.x;
                        const dy = enemy.y - unit.y;
                        const distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestEnemy = enemy;
                        }
                    }
                    
                    if (closestEnemy) {
                        unit.attack(closestEnemy);
                    }
                }
            }
        }
        
        // Phase 3: Health/Cleanup
        for (const unit of batch) {
            if (unit.hp <= 0) {
                unit.die();
            }
        }
    }

    // Process buildings in batches
    processBuildings(buildings, deltaTime) {
        const buildingArray = Array.from(buildings.values());
        
        // Process buildings in batches
        for (let i = 0; i < buildingArray.length; i += this.batchSize) {
            const batch = buildingArray.slice(i, i + this.batchSize);
            this.processBuildingBatch(batch, deltaTime);
        }
    }
    
    processBuildingBatch(batch, deltaTime) {
        // Phase 1: Production Updates
        for (const building of batch) {
            if (building.productionQueue.length > 0) {
                const currentItem = building.productionQueue[0];
                currentItem.progress += deltaTime;
                
                if (currentItem.progress >= currentItem.time) {
                    building.completeProduction();
                }
            }
        }
        
        // Phase 2: Resource Generation
        for (const building of batch) {
            if (building.type === 'extractor' || 
                building.type === 'powerPlant' || 
                building.type === 'computroniumCore') {
                building.generateResources(deltaTime);
            }
        }
        
        // Phase 3: Health/Cleanup
        for (const building of batch) {
            if (building.hp <= 0) {
                building.destroy();
            }
        }
    }

    // Get processing statistics
    getStats() {
        this.stats.frameCount++;
        
        if (this.stats.batchUpdates > 0) {
            this.stats.avgBatchSize = this.stats.totalUpdates / this.stats.batchUpdates;
        }
        
        return { ...this.stats };
    }

    // Reset statistics
    resetStats() {
        this.stats.frameCount = 0;
        this.stats.totalUpdates = 0;
        this.stats.batchUpdates = 0;
        this.stats.avgBatchSize = 0;
    }
}