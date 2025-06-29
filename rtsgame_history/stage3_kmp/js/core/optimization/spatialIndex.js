// Spatial Index System - High locality spatial partitioning for efficient collision detection
// Uses grid-based spatial hashing with memory-efficient data structures

export class SpatialIndex {
    constructor(cellSize = 64) {
        this.cellSize = cellSize;
        this.unitCells = new Map();
        this.buildingCells = new Map();
        this.entityCount = new Map();
    }
    
    getCellKey(x, y) {
        const cellX = Math.floor(x / this.cellSize);
        const cellY = Math.floor(y / this.cellSize);
        return `${cellX},${cellY}`;
    }
    
    update(units, buildings) {
        // Clear existing data
        this.unitCells.clear();
        this.buildingCells.clear();
        this.entityCount.clear();
        
        // Index units
        for (const unit of units.values()) {
            const key = this.getCellKey(unit.x, unit.y);
            
            if (!this.unitCells.has(key)) {
                this.unitCells.set(key, []);
            }
            this.unitCells.get(key).push(unit);
            
            this.entityCount.set(key, (this.entityCount.get(key) || 0) + 1);
        }
        
        // Index buildings
        for (const building of buildings.values()) {
            const key = this.getCellKey(building.x, building.y);
            
            if (!this.buildingCells.has(key)) {
                this.buildingCells.set(key, []);
            }
            this.buildingCells.get(key).push(building);
            
            this.entityCount.set(key, (this.entityCount.get(key) || 0) + 1);
        }
    }
    
    getNearbyUnits(position, radius) {
        const centerX = position.x;
        const centerY = position.y;
        const cellRadius = Math.ceil(radius / this.cellSize);
        
        const centerCellX = Math.floor(centerX / this.cellSize);
        const centerCellY = Math.floor(centerY / this.cellSize);
        
        const nearbyUnits = [];
        
        // Check cells in radius
        for (let dx = -cellRadius; dx <= cellRadius; dx++) {
            for (let dy = -cellRadius; dy <= cellRadius; dy++) {
                const cellX = centerCellX + dx;
                const cellY = centerCellY + dy;
                const key = `${cellX},${cellY}`;
                
                const cellUnits = this.unitCells.get(key) || [];
                
                // Filter units within actual radius
                for (const unit of cellUnits) {
                    const dx = unit.x - centerX;
                    const dy = unit.y - centerY;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance <= radius) {
                        nearbyUnits.push(unit);
                    }
                }
            }
        }
        
        return nearbyUnits;
    }
    
    getNearbyBuildings(position, radius) {
        const centerX = position.x;
        const centerY = position.y;
        const cellRadius = Math.ceil(radius / this.cellSize);
        
        const centerCellX = Math.floor(centerX / this.cellSize);
        const centerCellY = Math.floor(centerY / this.cellSize);
        
        const nearbyBuildings = [];
        
        // Check cells in radius
        for (let dx = -cellRadius; dx <= cellRadius; dx++) {
            for (let dy = -cellRadius; dy <= cellRadius; dy++) {
                const cellX = centerCellX + dx;
                const cellY = centerCellY + dy;
                const key = `${cellX},${cellY}`;
                
                const cellBuildings = this.buildingCells.get(key) || [];
                
                // Filter buildings within actual radius
                for (const building of cellBuildings) {
                    const dx = building.x - centerX;
                    const dy = building.y - centerY;
                    const distance = Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance <= radius) {
                        nearbyBuildings.push(building);
                    }
                }
            }
        }
        
        return nearbyBuildings;
    }
    
    isCellOccupied(x, y) {
        const key = this.getCellKey(x, y);
        return (this.entityCount.get(key) || 0) > 0;
    }

    // Get debug statistics
    getStats() {
        let totalEntities = 0;
        let maxEntitiesPerCell = 0;
        let occupiedCells = 0;
        
        for (let i = 0; i < this.totalCells; i++) {
            const count = this.entityCount[i];
            totalEntities += count;
            if (count > 0) occupiedCells++;
            if (count > maxEntitiesPerCell) maxEntitiesPerCell = count;
        }
        
        return {
            totalCells: this.totalCells,
            occupiedCells,
            totalEntities,
            maxEntitiesPerCell,
            avgEntitiesPerOccupiedCell: occupiedCells > 0 ? (totalEntities / occupiedCells).toFixed(2) : 0,
            cellSize: this.cellSize,
            gridDimensions: `${this.gridWidth}x${this.gridHeight}`
        };
    }
}