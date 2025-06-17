// js/core/simulationManager.js
import { makeStrategicDecisions, coordinateAttacks } from '../ai/strategicAI.js';

export class SimulationManager {
    constructor(gameContext) {
        this.gameContext = gameContext;
        this.simulationSpeed = 1.0;
    }

    update(deltaTime) {
        if (this.gameContext.gameState.paused) return;

        // Update game time
        this.gameContext.gameState.gameTime += this.simulationSpeed;

        // Update resource income
        this.updateResourceIncome();

        // Update units
        for (const unit of this.gameContext.units) {
            unit.update(this.createGameContext());
        }

        // Update buildings
        for (const building of this.gameContext.buildings) {
            building.update(this.createGameContext());
        }

        // Update effects
        this.gameContext.effects = this.gameContext.effects.filter(effect => {
            effect.update();
            return !effect.isFinished();
        });

        // Update projectiles
        this.gameContext.projectiles = this.gameContext.projectiles.filter(projectile => {
            projectile.update();
            return !projectile.isFinished();
        });

        // Update captions
        this.gameContext.captions = this.gameContext.captions.filter(caption => {
            caption.update();
            return !caption.isFinished();
        });

        // AI decisions
        if (this.gameContext.gameState.gameTime % 60 === 0) {
            makeStrategicDecisions(this.createGameContext());
            coordinateAttacks(this.createGameContext());
        }

        // Check for game end
        this.checkGameEnd();
    }

    updateResourceIncome() {
        // Process resource income
        ['blue', 'red'].forEach(team => {
            const teamResources = this.gameContext.resources[team];
            teamResources.mass += teamResources.massIncome;
            teamResources.energy += teamResources.energyIncome;
        });

        // Update resource production
        for (const building of this.gameContext.buildings) {
            if (building.type === 'extractor') {
                this.gameContext.resources.mass += building.productionRate;
            } else if (building.type === 'powerPlant') {
                this.gameContext.resources.energy += building.productionRate;
            }
        }
    }

    createGameContext() {
        return {
            units: this.gameContext.units,
            buildings: this.gameContext.buildings,
            projectiles: this.gameContext.projectiles,
            effects: this.gameContext.effects,
            resources: this.gameContext.resources,
            gameState: this.gameContext.gameState,
            terrain: this.gameContext.terrain,
            resourceNodes: this.gameContext.resourceNodes,
            worldSize: this.gameContext.worldSize
        };
    }

    checkGameEnd() {
        const blueCommander = this.gameContext.units.find(u => u.type === 'commander' && u.team === 'blue');
        const redCommander = this.gameContext.units.find(u => u.type === 'commander' && u.team === 'red');

        if (!blueCommander) {
            this.endSimulation('red');
        } else if (!redCommander) {
            this.endSimulation('blue');
        }
    }

    endSimulation(winner) {
        this.gameContext.gameState.isRunning = false;
        this.gameContext.gameState.winner = winner;
        this.gameContext.battleLogger.addEntry('system', `${winner.toUpperCase()} team wins!`);
    }

    setSimulationSpeed(speed) {
        this.simulationSpeed = speed;
    }

    togglePause() {
        this.gameContext.gameState.paused = !this.gameContext.gameState.paused;
    }

    reset() {
        this.gameContext.gameState.gameTime = 0;
        this.gameContext.gameState.winner = null;
        this.gameContext.gameState.paused = false;
        this.gameContext.gameState.isRunning = false;
        
        this.gameContext.units = [];
        this.gameContext.buildings = [];
        this.gameContext.effects = [];
        this.gameContext.projectiles = [];
        this.gameContext.captions = [];
        
        this.gameContext.resources = {
            blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
            red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        };
    }
} 