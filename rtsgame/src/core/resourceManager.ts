import { RESOURCE_TYPES, ECONOMIC_PHASES } from '../config/gameConstants.js';

export class ResourceManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.resourceNodes = new Map(); // Map of resource node positions to their data
        this.extractors = new Map(); // Map of extractor positions to their data
        this.energyPlants = new Map(); // Map of energy plant positions to their data
        this.factories = new Map(); // Map of factory positions to their data
        
        // Economic state
        this.massIncome = 0;
        this.energyIncome = 0;
        this.massStorage = 0;
        this.energyStorage = 0;
        this.economicPhase = ECONOMIC_PHASES.SURVIVAL;
        
        // Resource node generation
        this.generateResourceNodes();
    }
    
    /**
     * Generate resource nodes on the map
     */
    generateResourceNodes() {
        const { terrain, seedRandom } = this.gameContext;
        const nodeCount = Math.floor(seedRandom.random() * 10) + 15; // 15-25 nodes
        
        for (let i = 0; i < nodeCount; i++) {
            const position = this.findResourceNodePosition();
            if (position) {
                const type = this.determineResourceType(position, terrain);
                const yield = this.calculateResourceYield(type);
                
                this.resourceNodes.set(`${position.x},${position.y}`, {
                    position,
                    type,
                    yield,
                    isOccupied: false,
                    extractor: null
                });
            }
        }
    }
    
    /**
     * Find valid position for resource node
     * @returns {Object|null} Position object or null if no valid position found
     */
    findResourceNodePosition() {
        const { terrain, seedRandom } = this.gameContext;
        const maxAttempts = 50;
        
        for (let i = 0; i < maxAttempts; i++) {
            const x = Math.floor(seedRandom.random() * terrain[0].length);
            const y = Math.floor(seedRandom.random() * terrain.length);
            
            // Check if position is valid for resource node
            if (this.isValidResourceNodePosition(x, y)) {
                return { x, y };
            }
        }
        
        return null;
    }
    
    /**
     * Check if position is valid for resource node
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {boolean} Whether position is valid
     */
    isValidResourceNodePosition(x, y) {
        const { terrain } = this.gameContext;
        
        // Check if position is within bounds
        if (x < 0 || x >= terrain[0].length || y < 0 || y >= terrain.length) {
            return false;
        }
        
        // Check if position is already occupied
        if (this.resourceNodes.has(`${x},${y}`)) {
            return false;
        }
        
        // Check if terrain is suitable
        const terrainType = terrain[y][x];
        return terrainType === 3 || terrainType === 4 || terrainType === 6; // Grass, Forest, Hill
    }
    
    /**
     * Determine resource type based on position and terrain
     * @param {Object} position - Position object
     * @param {Array} terrain - Terrain grid
     * @returns {string} Resource type
     */
    determineResourceType(position, terrain) {
        const terrainType = terrain[position.y][position.x];
        
        switch (terrainType) {
            case 3: // Grass
                return RESOURCE_TYPES.MASS;
            case 4: // Forest
                return RESOURCE_TYPES.ENERGY;
            case 6: // Hill
                return Math.random() < 0.7 ? RESOURCE_TYPES.MASS : RESOURCE_TYPES.ENERGY;
            default:
                return RESOURCE_TYPES.MASS;
        }
    }
    
    /**
     * Calculate resource yield based on type
     * @param {string} type - Resource type
     * @returns {number} Resource yield
     */
    calculateResourceYield(type) {
        const baseYield = type === RESOURCE_TYPES.MASS ? 2 : 3;
        const variance = this.gameContext.seedRandom.random() * 0.4 + 0.8; // 0.8-1.2
        return Math.floor(baseYield * variance);
    }
    
    /**
     * Update resource management
     * @param {number} deltaTime - Time since last update
     */
    update(deltaTime) {
        // Update resource income
        this.updateResourceIncome(deltaTime);
        
        // Update economic phase
        this.updateEconomicPhase();
        
        // Check for resource node depletion
        this.checkResourceDepletion();
    }
    
    /**
     * Update resource income
     * @param {number} deltaTime - Time since last update
     */
    updateResourceIncome(deltaTime) {
        // Calculate mass income from extractors
        let massIncome = 0;
        this.extractors.forEach(extractor => {
            if (extractor.isActive) {
                massIncome += extractor.yield;
            }
        });
        
        // Calculate energy income from plants
        let energyIncome = 0;
        this.energyPlants.forEach(plant => {
            if (plant.isActive) {
                energyIncome += plant.yield;
            }
        });
        
        // Apply income
        this.massIncome = massIncome;
        this.energyIncome = energyIncome;
        this.massStorage += massIncome * deltaTime;
        this.energyStorage += energyIncome * deltaTime;
    }
    
    /**
     * Update economic phase based on infrastructure
     */
    updateEconomicPhase() {
        const extractorCount = this.extractors.size;
        const factoryCount = this.factories.size;
        
        if (extractorCount >= 7) {
            this.economicPhase = ECONOMIC_PHASES.EXPERIMENTAL;
        } else if (extractorCount >= 5) {
            this.economicPhase = ECONOMIC_PHASES.ADVANCED;
        } else if (extractorCount >= 3) {
            this.economicPhase = ECONOMIC_PHASES.EXPANSION;
        } else {
            this.economicPhase = ECONOMIC_PHASES.SURVIVAL;
        }
    }
    
    /**
     * Check for resource node depletion
     */
    checkResourceDepletion() {
        this.resourceNodes.forEach((node, key) => {
            if (node.extractor) {
                node.yield -= 0.001; // Gradual depletion
                if (node.yield <= 0) {
                    this.depleteResourceNode(key);
                }
            }
        });
    }
    
    /**
     * Deplete a resource node
     * @param {string} nodeKey - Resource node key
     */
    depleteResourceNode(nodeKey) {
        const node = this.resourceNodes.get(nodeKey);
        if (node && node.extractor) {
            // Remove extractor
            this.extractors.delete(`${node.position.x},${node.position.y}`);
            node.extractor = null;
            node.isOccupied = false;
            
            // Notify game of depletion
            if (this.gameContext.addEvent) {
                this.gameContext.addEvent('resource_depletion', 
                    `Resource node depleted at (${node.position.x}, ${node.position.y})`, 1);
            }
        }
    }
    
    /**
     * Find nearest unoccupied resource node
     * @param {Object} position - Position to search from
     * @param {string} type - Resource type to find
     * @returns {Object|null} Resource node or null if none found
     */
    findNearestResourceNode(position, type) {
        let nearestNode = null;
        let minDistance = Infinity;
        
        this.resourceNodes.forEach(node => {
            if (!node.isOccupied && (!type || node.type === type)) {
                const distance = this.calculateDistance(position, node.position);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestNode = node;
                }
            }
        });
        
        return nearestNode;
    }
    
    /**
     * Calculate distance between two positions
     * @param {Object} pos1 - First position
     * @param {Object} pos2 - Second position
     * @returns {number} Distance
     */
    calculateDistance(pos1, pos2) {
        const dx = pos2.x - pos1.x;
        const dy = pos2.y - pos1.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * Place extractor at resource node
     * @param {Object} position - Position to place extractor
     * @param {Object} unit - Unit placing the extractor
     * @returns {boolean} Whether placement was successful
     */
    placeExtractor(position, unit) {
        const nodeKey = `${position.x},${position.y}`;
        const node = this.resourceNodes.get(nodeKey);
        
        if (node && !node.isOccupied) {
            node.isOccupied = true;
            node.extractor = {
                unit,
                yield: node.yield,
                isActive: true,
                lastMaintenance: performance.now()
            };
            
            this.extractors.set(nodeKey, node.extractor);
            return true;
        }
        
        return false;
    }
    
    /**
     * Place energy plant
     * @param {Object} position - Position to place plant
     * @param {Object} unit - Unit placing the plant
     * @returns {boolean} Whether placement was successful
     */
    placeEnergyPlant(position, unit) {
        const plantKey = `${position.x},${position.y}`;
        
        if (!this.energyPlants.has(plantKey)) {
            this.energyPlants.set(plantKey, {
                unit,
                yield: 3,
                isActive: true,
                lastMaintenance: performance.now()
            });
            return true;
        }
        
        return false;
    }
    
    /**
     * Place factory
     * @param {Object} position - Position to place factory
     * @param {Object} unit - Unit placing the factory
     * @param {string} type - Factory type
     * @returns {boolean} Whether placement was successful
     */
    placeFactory(position, unit, type) {
        const factoryKey = `${position.x},${position.y}`;
        
        if (!this.factories.has(factoryKey)) {
            this.factories.set(factoryKey, {
                unit,
                type,
                isActive: true,
                lastMaintenance: performance.now(),
                productionQueue: []
            });
            return true;
        }
        
        return false;
    }
    
    /**
     * Get economic phase
     * @returns {string} Current economic phase
     */
    getEconomicPhase() {
        return this.economicPhase;
    }
    
    /**
     * Get resource income
     * @returns {Object} Resource income rates
     */
    getResourceIncome() {
        return {
            mass: this.massIncome,
            energy: this.energyIncome
        };
    }
    
    /**
     * Get resource storage
     * @returns {Object} Resource storage amounts
     */
    getResourceStorage() {
        return {
            mass: this.massStorage,
            energy: this.energyStorage
        };
    }
    
    /**
     * Consume resources
     * @param {number} mass - Mass to consume
     * @param {number} energy - Energy to consume
     * @returns {boolean} Whether consumption was successful
     */
    consumeResources(mass, energy) {
        if (this.massStorage >= mass && this.energyStorage >= energy) {
            this.massStorage -= mass;
            this.energyStorage -= energy;
            return true;
        }
        return false;
    }
} 