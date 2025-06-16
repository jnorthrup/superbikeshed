import { produce } from 'immer';

export class StateManager {
    constructor() {
        this.state = {
            gameTime: 0,
            paused: false,
            winner: null,
            resources: {
                mass: 100,
                energy: 150
            },
            income: {
                mass: 0,
                energy: 0
            },
            units: [],
            buildings: [],
            projectiles: [],
            effects: [],
            captions: [],
            terrain: [],
            resourceNodes: []
        };

        this.history = [];
        this.historyLimit = 100;
    }

    update(deltaTime) {
        this.state = produce(this.state, draft => {
            if (!draft.paused) {
                draft.gameTime += deltaTime;
            }
        });

        // Add to history
        this.history.push(this.state);
        if (this.history.length > this.historyLimit) {
            this.history.shift();
        }
    }

    addUnit(unit) {
        this.state = produce(this.state, draft => {
            draft.units.push(unit);
        });
    }

    removeUnit(unitId) {
        this.state = produce(this.state, draft => {
            draft.units = draft.units.filter(u => u.id !== unitId);
        });
    }

    addBuilding(building) {
        this.state = produce(this.state, draft => {
            draft.buildings.push(building);
        });
    }

    removeBuilding(buildingId) {
        this.state = produce(this.state, draft => {
            draft.buildings = draft.buildings.filter(b => b.id !== buildingId);
        });
    }

    addProjectile(projectile) {
        this.state = produce(this.state, draft => {
            draft.projectiles.push(projectile);
        });
    }

    removeProjectile(projectileId) {
        this.state = produce(this.state, draft => {
            draft.projectiles = draft.projectiles.filter(p => p.id !== projectileId);
        });
    }

    addEffect(effect) {
        this.state = produce(this.state, draft => {
            draft.effects.push(effect);
        });
    }

    removeEffect(effectId) {
        this.state = produce(this.state, draft => {
            draft.effects = draft.effects.filter(e => e.id !== effectId);
        });
    }

    addCaption(caption) {
        this.state = produce(this.state, draft => {
            draft.captions.push(caption);
        });
    }

    updateResources(mass, energy) {
        this.state = produce(this.state, draft => {
            draft.resources.mass += mass;
            draft.resources.energy += energy;
        });
    }

    updateIncome(mass, energy) {
        this.state = produce(this.state, draft => {
            draft.income.mass = mass;
            draft.income.energy = energy;
        });
    }

    setTerrain(terrain) {
        this.state = produce(this.state, draft => {
            draft.terrain = terrain;
        });
    }

    setResourceNodes(nodes) {
        this.state = produce(this.state, draft => {
            draft.resourceNodes = nodes;
        });
    }

    setPaused(paused) {
        this.state = produce(this.state, draft => {
            draft.paused = paused;
        });
    }

    setWinner(team) {
        this.state = produce(this.state, draft => {
            draft.winner = team;
        });
    }

    getState() {
        return this.state;
    }

    getHistory() {
        return this.history;
    }

    // For debugging and replays
    getStateAtTime(time) {
        return this.history.find(state => state.gameTime >= time) || this.state;
    }
} 