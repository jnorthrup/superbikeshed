export class ResourceManager {
    constructor() {
        this.resources = {
            mass: 100,
            energy: 150
        };

        this.income = {
            mass: 0,
            energy: 0
        };

        this.buildings = {
            extractors: 0,
            energyPlants: 0,
            factories: 0
        };

        this.resourceNodes = [];
    }

    initialize(gameContext) {
        this.resourceNodes = gameContext.terrain
            .flatMap((row, y) => 
                row.map((tile, x) => 
                    tile === 'RESOURCE' ? { x, y, type: 'mass' } : null
                )
            )
            .filter(node => node !== null);
    }

    update(gameContext) {
        const deltaTime = gameContext.deltaTime || (1/60);

        // Update resource income
        this.resources.mass += this.income.mass * deltaTime;
        this.resources.energy += this.income.energy * deltaTime;

        // Update building counts
        this.updateBuildingCounts(gameContext);

        // Check for resource node development
        this.checkResourceNodeDevelopment(gameContext);
    }

    updateBuildingCounts(gameContext) {
        this.buildings = {
            extractors: gameContext.buildings.filter(b => b.type === 'extractor').length,
            energyPlants: gameContext.buildings.filter(b => b.type === 'energyPlant').length,
            factories: gameContext.buildings.filter(b => b.type === 'factory').length
        };
    }

    checkResourceNodeDevelopment(gameContext) {
        const { units } = gameContext;
        const engineers = units.filter(u => u.type === 'engineer');

        for (const engineer of engineers) {
            if (engineer.isConstructing) continue;

            const nearestNode = this.findNearestResourceNode(engineer);
            if (nearestNode && this.canBuildExtractor(engineer, nearestNode)) {
                this.buildExtractor(engineer, nearestNode, gameContext);
            }
        }
    }

    findNearestResourceNode(unit) {
        return this.resourceNodes
            .filter(node => !node.hasExtractor)
            .reduce((nearest, node) => {
                const distance = this.getDistance(unit, node);
                return !nearest || distance < nearest.distance
                    ? { node, distance }
                    : nearest;
            }, null)?.node;
    }

    canBuildExtractor(engineer, node) {
        const distance = this.getDistance(engineer, node);
        return distance <= 100 && 
               this.resources.mass >= 50 && 
               this.resources.energy >= 25;
    }

    buildExtractor(engineer, node, gameContext) {
        this.resources.mass -= 50;
        this.resources.energy -= 25;
        this.income.mass += 2;

        node.hasExtractor = true;
        engineer.isConstructing = true;

        // Add building to game context
        gameContext.buildings.push({
            x: node.x,
            y: node.y,
            type: 'extractor',
            hp: 100,
            maxHp: 100,
            team: engineer.team
        });

        // Add visual feedback
        if (gameContext.captions) {
            gameContext.captions.push(new gameContext.Caption(
                node.x, node.y,
                'Extractor Built',
                '#4f4', 12
            ));
        }
    }

    getDistance(unit, target) {
        const dx = unit.x - target.x;
        const dy = unit.y - target.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    canAfford(cost) {
        return this.resources.mass >= cost.mass && 
               this.resources.energy >= cost.energy;
    }

    spend(cost) {
        if (!this.canAfford(cost)) return false;
        
        this.resources.mass -= cost.mass;
        this.resources.energy -= cost.energy;
        return true;
    }
} 