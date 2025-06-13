export class FormationSystem {
    constructor() {
        this.formations = new Map();
    }

    createFormation(units, type = 'line') {
        const formation = {
            type,
            units,
            center: this.calculateCenter(units),
            spacing: 50 // Default spacing between units
        };
        
        this.formations.set(formation.id, formation);
        return formation;
    }

    calculateCenter(units) {
        if (units.length === 0) return { x: 0, y: 0 };
        
        const sum = units.reduce((acc, unit) => ({
            x: acc.x + unit.x,
            y: acc.y + unit.y
        }), { x: 0, y: 0 });
        
        return {
            x: sum.x / units.length,
            y: sum.y / units.length
        };
    }

    updateFormation(formation, targetPosition) {
        if (!formation || !formation.units.length) return;
        
        const positions = this.calculateFormationPositions(
            formation.type,
            targetPosition,
            formation.units.length,
            formation.spacing
        );
        
        formation.units.forEach((unit, index) => {
            if (positions[index]) {
                unit.targetPosition = positions[index];
            }
        });
    }

    calculateFormationPositions(type, center, unitCount, spacing) {
        const positions = [];
        
        switch (type) {
            case 'line':
                const startX = center.x - (unitCount - 1) * spacing / 2;
                for (let i = 0; i < unitCount; i++) {
                    positions.push({
                        x: startX + i * spacing,
                        y: center.y
                    });
                }
                break;
                
            case 'circle':
                const radius = spacing * Math.sqrt(unitCount / Math.PI);
                for (let i = 0; i < unitCount; i++) {
                    const angle = (i / unitCount) * Math.PI * 2;
                    positions.push({
                        x: center.x + Math.cos(angle) * radius,
                        y: center.y + Math.sin(angle) * radius
                    });
                }
                break;
                
            case 'wedge':
                const rows = Math.ceil(Math.sqrt(unitCount));
                let unitIndex = 0;
                for (let row = 0; row < rows; row++) {
                    const unitsInRow = row + 1;
                    const rowWidth = unitsInRow * spacing;
                    const startX = center.x - rowWidth / 2;
                    for (let col = 0; col < unitsInRow && unitIndex < unitCount; col++) {
                        positions.push({
                            x: startX + col * spacing,
                            y: center.y + row * spacing
                        });
                        unitIndex++;
                    }
                }
                break;
                
            default:
                // Default to line formation
                return this.calculateFormationPositions('line', center, unitCount, spacing);
        }
        
        return positions;
    }

    removeFormation(formationId) {
        this.formations.delete(formationId);
    }
} 