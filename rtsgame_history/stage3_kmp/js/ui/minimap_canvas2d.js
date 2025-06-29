// js/ui/minimap_canvas2d.js - Simple 2D Canvas Minimap from scratch

import { TERRAIN_TYPES, WORLD_SIZE, GRID_SIZE } from '../config/gameConstants.js';
import { UNIT_TYPES } from '../config/unitTypes.js';

export function drawMinimap(minimapRenderContext) {
    const { minimapCtx, terrain, resourceNodes, units, buildings, camera } = minimapRenderContext;

    if (!minimapCtx) {
        return;
    }

    const MINIMAP_SIZE = 200;
    const scale = MINIMAP_SIZE / WORLD_SIZE;

    // Clear and set background
    minimapCtx.fillStyle = '#000033'; // Dark blue background
    minimapCtx.fillRect(0, 0, MINIMAP_SIZE, MINIMAP_SIZE);

    // Draw terrain grid
    drawTerrainGrid(minimapCtx, terrain, scale, MINIMAP_SIZE);
    
    // Draw resource nodes
    drawResourceNodes(minimapCtx, resourceNodes, scale);
    
    // Draw buildings
    drawBuildings(minimapCtx, buildings, scale);
    
    // Draw units
    drawUnits(minimapCtx, units, scale);
    
    // Draw camera indicator
    drawCameraIndicator(minimapCtx, camera, scale, MINIMAP_SIZE);
    
    // Draw border
    minimapCtx.strokeStyle = '#FFFFFF';
    minimapCtx.lineWidth = 2;
    minimapCtx.strokeRect(0, 0, MINIMAP_SIZE, MINIMAP_SIZE);
}

function drawTerrainGrid(ctx, terrain, scale, size) {
    if (!terrain || Object.keys(terrain).length === 0) {
        // No terrain data - draw default grid
        ctx.fillStyle = '#004400';
        ctx.fillRect(0, 0, size, size);
        return;
    }

    const gridStep = Math.max(1, Math.floor(GRID_SIZE / 50)); // Optimize drawing
    
    for (let x = 0; x < GRID_SIZE; x += gridStep) {
        for (let y = 0; y < GRID_SIZE; y += gridStep) {
            if (!terrain[x] || terrain[x][y] === undefined) continue;
            
            const terrainType = terrain[x][y].type || terrain[x][y];
            
            switch (terrainType) {
                case TERRAIN_TYPES.WATER:
                    ctx.fillStyle = '#001166';
                    break;
                case TERRAIN_TYPES.LAND:
                    ctx.fillStyle = '#004400';
                    break;
                case TERRAIN_TYPES.MOUNTAIN:
                    ctx.fillStyle = '#333333';
                    break;
                default:
                    ctx.fillStyle = '#002200';
            }
            
            const pixelX = (x / GRID_SIZE) * size;
            const pixelY = (y / GRID_SIZE) * size;
            const pixelSize = (gridStep / GRID_SIZE) * size;
            
            ctx.fillRect(pixelX, pixelY, pixelSize, pixelSize);
        }
    }
}

function drawResourceNodes(ctx, resourceNodes, scale) {
    if (!resourceNodes) return;
    
    ctx.fillStyle = '#FFFF00'; // Yellow for all resources
    
    for (const node of resourceNodes) {
        if (node.amount > 0) {
            const x = node.x * scale;
            const y = node.y * scale;
            
            ctx.fillRect(x - 1, y - 1, 3, 3);
        }
    }
}

function drawBuildings(ctx, buildings, scale) {
    if (!buildings) return;
    
    for (const building of buildings) {
        ctx.fillStyle = building.team === 'blue' ? '#6666FF' : '#FF6666';
        
        const x = building.x * scale;
        const y = building.y * scale;
        
        ctx.fillRect(x - 2, y - 2, 4, 4);
        
        // Border for buildings
        ctx.strokeStyle = building.team === 'blue' ? '#AAAAFF' : '#FFAAAA';
        ctx.lineWidth = 1;
        ctx.strokeRect(x - 2, y - 2, 4, 4);
    }
}

function drawUnits(ctx, units, scale) {
    if (!units) return;
    
    for (const unit of units) {
        ctx.fillStyle = unit.team === 'blue' ? '#3399FF' : '#FF3399';
        
        const x = unit.x * scale;
        const y = unit.y * scale;
        
        // Regular units
        ctx.fillRect(x - 1, y - 1, 2, 2);
        
        // Highlight commanders
        if (unit.type === UNIT_TYPES.commander) {
            ctx.strokeStyle = unit.team === 'blue' ? '#00FFFF' : '#FFFF00';
            ctx.lineWidth = 1;
            ctx.strokeRect(x - 2, y - 2, 4, 4);
        }
    }
}

function drawCameraIndicator(ctx, camera, scale, size) {
    if (!camera) return;
    
    // Simple camera position dot
    const x = (camera.x || 0) * scale + size / 2;
    const y = (camera.y || 0) * scale + size / 2;
    
    // Clamp to minimap bounds
    const clampedX = Math.max(2, Math.min(size - 2, x));
    const clampedY = Math.max(2, Math.min(size - 2, y));
    
    ctx.fillStyle = '#00FFFF';
    ctx.fillRect(clampedX - 2, clampedY - 2, 4, 4);
    
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 1;
    ctx.strokeRect(clampedX - 2, clampedY - 2, 4, 4);
}
