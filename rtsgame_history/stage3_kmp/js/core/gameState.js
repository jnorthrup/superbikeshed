export class GameState {
    constructor() {
        this.gameTime = 0;
        this.winner = null;
        this.paused = false;
        this.isRunning = false;
        
        this.resources = {
            blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
            red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        };
        
        this.units = [];
        this.buildings = [];
        this.terrain = [];
        this.resourceNodes = [];
        this.effects = [];
        this.projectiles = [];
        this.captions = [];
    }

    updateResourceIncome() {
        // Update mass income
        for (const team of ['blue', 'red']) {
            let massIncome = 0;
            let energyIncome = 0;
            
            // Calculate income from extractors
            for (const building of this.buildings) {
                if (building.team === team) {
                    if (building.type === 'massExtractor') {
                        massIncome += building.income;
                    } else if (building.type === 'energyExtractor') {
                        energyIncome += building.income;
                    }
                }
            }
            
            // Update income values
            this.resources[team].massIncome = massIncome;
            this.resources[team].energyIncome = energyIncome;
            
            // Add income to resources
            this.resources[team].mass += massIncome;
            this.resources[team].energy += energyIncome;
        }
    }

    createGameContext() {
        return {
            units: this.units,
            buildings: this.buildings,
            terrain: this.terrain,
            resourceNodes: this.resourceNodes,
            effects: this.effects,
            projectiles: this.projectiles,
            resources: this.resources,
            gameTime: this.gameTime
        };
    }

    reset() {
        this.gameTime = 0;
        this.winner = null;
        this.paused = false;
        this.isRunning = false;
        
        this.resources = {
            blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
            red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        };
        
        this.units = [];
        this.buildings = [];
        this.terrain = [];
        this.resourceNodes = [];
        this.effects = [];
        this.projectiles = [];
        this.captions = [];
    }
} 