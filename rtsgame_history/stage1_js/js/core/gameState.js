import { initPlayerResources, initMapResourceNodes } from './resourceManager.js'; // Adjusted path

export class GameState {
    constructor() {
        this.gameTime = 0;
        this.winner = null;
        this.paused = false;
        this.isRunning = false;
        
        // Old resources object - will be superseded by playerResources Cursor
        // this.resources = {
        //     blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
        //     red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        // };

        // New resource management using TrikeShed-TS Cursors
        this.playerResources = initPlayerResources();
        this.mapResourceNodes = initMapResourceNodes();
        
        this.units = [];
        this.buildings = [];
        this.terrain = [];
        this.resourceNodes = [];
        this.effects = [];
        this.projectiles = [];
        this.captions = [];
    }

    updateResourceIncome() {
        // This method will need to be updated to use the new resourceManager functions
        // For now, it might conflict or be non-functional with the new system.
        // console.warn("GameState.updateResourceIncome() needs refactoring for new resource system.");

        // Example of how it might look (conceptual, needs player ID mapping and resourceManager):
        // for (let playerId = 0; playerId < MAX_PLAYERS; playerId++) {
        //    let massIncome = 0; let energyIncome = 0;
        //    // Calculate income based on buildings owned by playerId
        //    this.buildings.filter(b => b.getPlayerId() === playerId).forEach(b => { ... });
        //    this.playerResources = addPlayerResource(this.playerResources, playerId, PlayerResourceType.Mass, massIncome * deltaTime);
        //    this.playerResources = addPlayerResource(this.playerResources, playerId, PlayerResourceType.Energy, energyIncome * deltaTime);
        // }
    }

    // --- New methods for TrikeShed-TS resource management ---
    getPlayerResourcesState() {
        return this.playerResources;
    }

    getMapResourceNodesState() {
        return this.mapResourceNodes;
    }

    setPlayerResourcesState(newPlayerResources) {
        this.playerResources = newPlayerResources;
    }

    setMapResourceNodesState(newMapResourceNodes) {
        this.mapResourceNodes = newMapResourceNodes;
    }
    // --- End of new methods ---


    createGameContext() {
        // resources property here might need to point to a getter that
        // converts playerResources Cursor to the old object format if other parts of the game still expect it.
        // Or, update all consumers of gameContext.resources.
        return {
            units: this.units,
            buildings: this.buildings,
            terrain: this.terrain,
            // resourceNodes: this.resourceNodes, // This is now this.mapResourceNodes
            mapResourceNodesCursor: this.mapResourceNodes, // Expose new cursor
            // playerResourcesCursor: this.playerResources, // Expose new cursor
            // resources: this.resources, // Old resources object
            gameTime: this.gameTime
        };
    }

    reset() {
        this.gameTime = 0;
        this.winner = null;
        this.paused = false;
        this.isRunning = false;
        
        // Reset to initial TrikeShed-TS resource states
        this.playerResources = initPlayerResources();
        this.mapResourceNodes = initMapResourceNodes();

        // this.resources = { // Old system
        //     blue: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 },
        //     red: { mass: 100, energy: 150, massIncome: 0, energyIncome: 0 }
        // };
        
        this.units = [];
        this.buildings = [];
        this.terrain = [];
        this.resourceNodes = [];
        this.effects = [];
        this.projectiles = [];
        this.captions = [];
    }
} 