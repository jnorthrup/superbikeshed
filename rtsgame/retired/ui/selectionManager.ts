class SelectionManager {
    constructor() {
        this._selectedEntity = null;
    }

    /**
     * Sets the currently selected entity.
     * @param {object} entity - The entity (unit or building) to select.
     */
    setSelected(entity) {
        if (this._selectedEntity === entity) {
            return; // No change
        }

        // Deselect previous entity if it has a 'selected' property (e.g., units)
        if (this._selectedEntity && typeof this._selectedEntity === 'object' && 'selected' in this._selectedEntity) {
            this._selectedEntity.selected = false;
        }

        this._selectedEntity = entity;

        // Mark new entity as selected if it has a 'selected' property (for visual feedback)
        if (this._selectedEntity && typeof this._selectedEntity === 'object' && 'selected' in this._selectedEntity) {
            this._selectedEntity.selected = true;
        }

        // console.log("Selected:", entity ? (entity.type ? entity.type.name : "Unknown Type") : "None");
    }

    /**
     * Returns the currently selected entity.
     * @returns {object|null} The selected entity or null if none.
     */
    getSelected() {
        return this._selectedEntity;
    }

    /**
     * Clears the current selection.
     */
    clearSelection() {
        if (this._selectedEntity && typeof this._selectedEntity === 'object' && 'selected' in this._selectedEntity) {
            this._selectedEntity.selected = false;
        }
        this._selectedEntity = null;
        // console.log("Selection cleared.");
    }
}

export default SelectionManager;

