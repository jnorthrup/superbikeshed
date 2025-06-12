// js/input/inputManager.js

import { GameCommandTypes } from '../core/commands.js';

export class InputManager {
    constructor(simulation) {
        this.simulation = simulation; // To dispatch commands
        this.selectedUnitIds = new Set(); // Basic selection state
        // TODO: Potentially integrate with a more formal SelectionManager instance if needed
    }

    /**
     * Handles left mouse clicks, attempting to select units or buildings.
     * @param {number} worldX - The x-coordinate in world space.
     * @param {number} worldY - The y-coordinate in world space.
     * @param {boolean} shiftKey - Whether the shift key was pressed.
     */
    handleLeftClick(worldX, worldY, shiftKey = false) {
        console.log(`InputManager: Left click at (${worldX.toFixed(0)}, ${worldY.toFixed(0)}), Shift: ${shiftKey}`);
        
        const clickedEntity = this._findClickedEntity(worldX, worldY);

        if (clickedEntity) {
            // For simplicity, assume entities have a unique 'id' property
            // In a real ECS, this might be an entity ID number.
            const entityId = clickedEntity.id || (clickedEntity.type ? `${clickedEntity.type.name}_${Math.random().toString(36).substr(2,5)}` : 'unknown_entity');
            if (!clickedEntity.id) clickedEntity.id = entityId; // Assign temporary ID if missing for this example

            const command = {
                type: GameCommandTypes.PLAYER_COMMAND_SELECT_UNIT,
                payload: { 
                    unitId: entityId, 
                    isMultiSelect: shiftKey 
                }
            };
            this.simulation.dispatchCommand(command); 

            // Update internal selection state for command generation
            // This is a simplified local tracking. The authoritative selection state
            // would live in the game state, updated by a reducer.
            if (!shiftKey) {
                this.selectedUnitIds.clear();
                this.selectedUnitIds.add(entityId);
            } else {
                if (this.selectedUnitIds.has(entityId)) {
                    this.selectedUnitIds.delete(entityId); // Toggle off if shift-clicking already selected
                } else {
                    this.selectedUnitIds.add(entityId);
                }
            }
            console.log('InputManager: Selected IDs:', Array.from(this.selectedUnitIds));

        } else {
            if (!shiftKey) {
                const command = { type: GameCommandTypes.PLAYER_COMMAND_DESELECT_ALL, payload: {} };
                this.simulation.dispatchCommand(command);
                this.selectedUnitIds.clear();
                console.log('InputManager: Dispatched PLAYER_COMMAND_DESELECT_ALL.');
            }
        }
    }

    /**
     * Handles right mouse clicks, typically for issuing commands to selected units.
     * @param {number} worldX - The x-coordinate in world space.
     * @param {number} worldY - The y-coordinate in world space.
     */
    handleRightClick(worldX, worldY) {
        console.log(`InputManager: Right click at (${worldX.toFixed(0)}, ${worldY.toFixed(0)}) for units:`, Array.from(this.selectedUnitIds));

        if (this.selectedUnitIds.size > 0) {
            // TODO: Add logic to check if clicking on an enemy unit for an ATTACK command.
            // For now, always issue a MOVE command.
            const command = {
                type: GameCommandTypes.PLAYER_COMMAND_MOVE_UNITS, // Changed from PLAYER_COMMAND_UNIT_MOVE
                payload: {
                    unitIds: Array.from(this.selectedUnitIds),
                    targetPosition: { x: worldX, y: worldY }
                }
            };
            this.simulation.dispatchCommand(command);
            console.log('InputManager: Dispatched PLAYER_COMMAND_MOVE_UNITS to', command.payload.targetPosition);
        }
    }

    /**
     * Handles key presses for game actions.
     * @param {string} key - The key that was pressed (e.g., 'p', 'Escape').
     */
    handleKeyPress(key) {
        console.log(`InputManager: Key press: ${key}`);
        switch (key.toLowerCase()) {
            case 'p': // Example: Toggle Pause
                // In a real system, we might need to know current pause state
                // or the command itself is just a toggle.
                // For SET_PAUSED, the payload should indicate the new desired state.
                // This requires this.simulation.gameState to be accessible or passed.
                // For simplicity, let's assume it toggles.
                const currentPauseState = this.simulation.gameState ? this.simulation.gameState.paused : false;
                this.simulation.dispatchCommand({
                    type: GameCommandTypes.SET_PAUSED,
                    payload: { isPaused: !currentPauseState }
                });
                console.log('Dispatching SET_PAUSED to toggle. Current known state:', currentPauseState);
                break;
            // Add more key handlers here, e.g., for build menus, unit abilities
            // case 'b': // Example for build
            //   this.handleBuildCommand();
            //   break;
        }
    }

    // Placeholder for finding an entity at a point
    // In a real implementation, this would query the simulation's entity manager more robustly.
    _findClickedEntity(worldX, worldY) {
        // Basic search through units, then buildings. Prioritizes units.
        // Assumes entities have x, y, and type.size properties.
        // Note: This uses TILE_SIZE as a fallback if type.size is missing, which might not be accurate.
        // It also uses direct access to entityManager.units/buildings.
        const entities = [...this.simulation.entityManager.units, ...this.simulation.entityManager.buildings];
        
        for (const entity of entities) {
            const size = entity.type?.size || 20; // Default size if not specified
            // Simple circular click detection
            const dist = Math.sqrt(Math.pow(entity.x - worldX, 2) + Math.pow(entity.y - worldY, 2));
            if (dist < size / 2) { // Assuming size is diameter-like
                return entity;
            }
        }
        return null;
    }
}
