// rtsgame/js/visualization/spacegraphExporter.ts
import { SpaceGraphData, SpaceGraphNode, SpaceGraphEdge } from './spacegraphTypes';
import { GameState } from '../core/gameState'; // Adjust path as needed
import { EntityManager } from '../core/simulation'; // Adjust path for EntityManager
import { Unit } from '../core/unit';
import { Building } from '../core/building';
import {
    PlayerResourceType,
    MapResourceType,
    getPlayerResource,
    getMapResourceNode,
    MAX_PLAYERS,
    MAX_MAP_NODES,
} from '../core/resourceManager'; // Adjust path as needed
import { Cursor } from 'trikeshed-ts'; // Adjust path as needed for trikeshed-ts

const VISUALIZED_STRUCTURE_TYPES = ['MassExtractor', 'EnergyExtractor', 'ComputroniumSynthesizer', 'LandFactory', 'AirFactory', 'NavalFactory', 'MassFabricator', 'EnergyConverter', 'ComputroniumCore', 'AdvancedComputroniumCore']; // Match actual types

// Helper to get player color (example)
function getPlayerColor(playerId: number): string {
    return playerId === 0 ? 'blue' : (playerId === 1 ? 'red' : 'grey');
}

export function exportGameStateToSpaceGraphData(gameState: GameState, entityManager?: EntityManager): SpaceGraphData {
    const nodes: SpaceGraphNode[] = [];
    const edges: SpaceGraphEdge[] = [];

    // 1. Player Resource Nodes
    const playerResourcesCursor = gameState.getPlayerResourcesState();
    if (playerResourcesCursor) {
        for (let i = 0; i < MAX_PLAYERS; i++) {
            try {
                const mass = getPlayerResource(playerResourcesCursor, i, PlayerResourceType.Mass);
                const energy = getPlayerResource(playerResourcesCursor, i, PlayerResourceType.Energy);
                const computronium = getPlayerResource(playerResourcesCursor, i, PlayerResourceType.Computronium);
                const ferrite = getPlayerResource(playerResourcesCursor, i, PlayerResourceType.Ferrite);
                const crylithium = getPlayerResource(playerResourcesCursor, i, PlayerResourceType.Crylithium);

                nodes.push({
                    id: `player_${i}`,
                    label: `Player ${i} Resources`,
                    type: 'playerResources',
                    data: { playerId: i, mass, energy, computronium, ferrite, crylithium },
                    color: getPlayerColor(i),
                    size: 15
                });
            } catch (e) {
                console.warn(`Could not retrieve resources for player ${i}: ${(e as Error).message}`);
            }
        }
    } else {
        console.warn("Player resources cursor is not available in gameState.");
    }

    // 2. Map Resource Node Nodes
    const mapResourceNodesCursor = gameState.getMapResourceNodesState();
    if (mapResourceNodesCursor) {
        for (let i = 0; i < MAX_MAP_NODES; i++) {
            const nodeData = getMapResourceNode(mapResourceNodesCursor, i);
            if (nodeData && nodeData.maxAmount > 0) {
                let resourceLabel = MapResourceType[nodeData.type] || 'UnknownResource';
                let label = `${resourceLabel} (${nodeData.currentAmount}/${nodeData.maxAmount})`;
                let color = 'grey';
                switch(nodeData.type) {
                    case MapResourceType.MassDeposit: color = 'saddlebrown'; break;
                    case MapResourceType.FerriteVein: color = 'darkslateblue'; break;
                    case MapResourceType.CrylithiumVein: color = 'mediumpurple'; break;
                }

                nodes.push({
                    id: `mapNode_${i}`,
                    label: label,
                    type: 'mapResourceNode',
                    data: { nodeId: i, resourceType: resourceLabel, currentAmount: nodeData.currentAmount, maxAmount: nodeData.maxAmount, x: nodeData.x, y: nodeData.y },
                    color: color,
                    size: 8 + Math.sqrt(nodeData.currentAmount) / 15,
                    x: nodeData.x,
                    y: nodeData.y
                });
            }
        }
    } else {
        console.warn("Map resource nodes cursor is not available in gameState.");
    }

    // 3. Key Economic Structures (Extractors, Factories) and other Entities
    // This part now uses the passed `entityManager`
    if (entityManager) {
        // Assuming entityManager.units and entityManager.buildings are arrays of entity objects
        const allEntities = [...(entityManager.units || []), ...(entityManager.buildings || [])];

        allEntities.forEach(entity => {
            if (!entity || !entity.id || !entity.type) return; // Basic check for valid entity

            let entityTypeString: string = typeof entity.type === 'object' ? entity.type.name : entity.type; // Handle if type is an object with a name
            let ownerPlayerId: string | undefined = undefined;
            const entityIdString: string = `entity_${entity.id}`;

            if (typeof entity.team === 'string') { // Assuming team is "blue" or "red"
                const playerIndex = entity.team.toLowerCase() === 'blue' ? 0 : (entity.team.toLowerCase() === 'red' ? 1 : -1);
                if (playerIndex !== -1) {
                    ownerPlayerId = `player_${playerIndex}`;
                }
            }

            // Check if it's a structure we want to visualize or any unit/building
            if (VISUALIZED_STRUCTURE_TYPES.includes(entityTypeString) || entity instanceof Unit || entity instanceof Building) {
                if (!nodes.find(n => n.id === entityIdString)) {
                    let nodeType = 'entity'; // Generic entity
                    if (VISUALIZED_STRUCTURE_TYPES.includes(entityTypeString)) {
                        nodeType = 'structure';
                    } else if (entity instanceof Unit) {
                        nodeType = 'unit';
                    } else if (entity instanceof Building) {
                        nodeType = 'building'; // Could be a non-economic building
                    }

                    nodes.push({
                      id: entityIdString,
                      label: `${ownerPlayerId ? ownerPlayerId + "'s " : ''}${entityTypeString} ${entity.id.slice(0,4)}`,
                      type: nodeType,
                      data: { ownerPlayerId, entityType: entityTypeString, hp: entity.hp, maxHp: entity.maxHp },
                      x: entity.x,
                      y: entity.y,
                      color: ownerPlayerId ? getPlayerColor(ownerPlayerId === 'player_0' ? 0 : 1) : 'grey',
                      size: entity instanceof Unit ? 6 : 9
                    });
                }

                if (ownerPlayerId) {
                    edges.push({
                      id: `edge_owner_${entityIdString}_${ownerPlayerId}`,
                      sourceId: entityIdString,
                      targetId: ownerPlayerId,
                      label: 'owned by',
                      type: 'ownership',
                      directed: true
                    });
                }

                if (entityTypeString && entityTypeString.includes('Extractor')) {
                    // TODO: Logic to find nearest map_resource_node and link
                    // For now, conceptually linking to player it produces for
                     edges.push({
                       id: `edge_prod_${entityIdString}_${ownerPlayerId}`,
                       sourceId: entityIdString,
                       targetId: ownerPlayerId!, // Assert ownerPlayerId is defined here
                       label: 'produces for',
                       type: 'production_link',
                       directed: true
                     });
                } else if (entityTypeString && entityTypeString.includes('Factory')) {
                     edges.push({
                       id: `edge_cons_${entityIdString}_${ownerPlayerId}`,
                       sourceId: ownerPlayerId!, // Assert ownerPlayerId is defined here
                       targetId: entityIdString,
                       label: 'supplies',
                       type: 'consumption_link',
                       directed: true
                     });
                }
            }

            // Conceptual: Add 'attacking' edges if entity.target is set
            if (entity instanceof Unit && entity.target && entity.target.id) {
                const targetIdString = `entity_${entity.target.id}`;
                 // Ensure target node exists before creating edge
                if (nodes.find(n => n.id === targetIdString)) {
                    edges.push({
                        id: `edge_attacks_${entityIdString}_${targetIdString}`,
                        sourceId: entityIdString,
                        targetId: targetIdString,
                        label: 'attacking',
                        type: 'combat',
                        directed: true,
                        color: 'orange'
                    });
                }
            }
        });
    } else {
        console.warn("EntityManager not provided to exportGameStateToSpaceGraphData. Entity nodes and edges will be omitted.");
    }

    return { nodes, edges };
}
