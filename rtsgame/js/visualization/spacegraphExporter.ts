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
    if (entityManager) {
        const allEntities = [...(entityManager.units || []), ...(entityManager.buildings || [])];

        allEntities.forEach(entity => {
            if (!entity || !entity.id || !entity.type) return;

            let entityTypeString: string = typeof entity.type === 'object' ? (entity.type as any).name : entity.type;
            let ownerPlayerId: string | undefined = undefined;
            const entityIdString: string = `entity_${entity.id}`;

            if (typeof entity.team === 'string') {
                const playerIndex = entity.team.toLowerCase() === 'blue' ? 0 : (entity.team.toLowerCase() === 'red' ? 1 : -1);
                if (playerIndex !== -1) {
                    ownerPlayerId = `player_${playerIndex}`;
                }
            }

            if (VISUALIZED_STRUCTURE_TYPES.includes(entityTypeString) || entity instanceof Unit || entity instanceof Building) {
                let nodeType = 'entity';
                if (VISUALIZED_STRUCTURE_TYPES.includes(entityTypeString)) {
                    nodeType = 'structure';
                } else if (entity instanceof Unit) {
                    nodeType = 'unit';
                } else if (entity instanceof Building) {
                    nodeType = 'building';
                }

                // Prepare data for the node, including new combat-related properties for units
                const commonNodeData: any = {
                    ownerPlayerId,
                    entityType: entityTypeString,
                    hp: entity.hp,
                    maxHp: entity.maxHp
                };

                if (entity instanceof Unit) {
                    commonNodeData.isAttacking = !!entity.isAttacking; // Coerce to boolean
                    commonNodeData.currentTargetId = entity.currentTargetId || null;
                    commonNodeData.recentDamageDealt = entity.recentDamageDealt || 0;
                    // Example simple efficiency: (kills * 100) / (cost or maxHp)
                    // commonNodeData.combatEfficiencyScore = (entity.kills || 0) * 100 / (entity.type?.cost?.mass || entity.maxHp || 1);
                    commonNodeData.combatEfficiencyScore = entity.combatEfficiencyScore || 0;
                }

                if (!nodes.find(n => n.id === entityIdString)) {
                    nodes.push({
                      id: entityIdString,
                      label: `${ownerPlayerId ? ownerPlayerId + "'s " : ''}${entityTypeString} ${entity.id.slice(0,4)}`,
                      type: nodeType,
                      data: commonNodeData,
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
                     edges.push({
                       id: `edge_prod_${entityIdString}_${ownerPlayerId}`,
                       sourceId: entityIdString,
                       targetId: ownerPlayerId!,
                       label: 'produces for',
                       type: 'production_link',
                       directed: true
                     });
                } else if (entityTypeString && entityTypeString.includes('Factory')) {
                     edges.push({
                       id: `edge_cons_${entityIdString}_${ownerPlayerId}`,
                       sourceId: ownerPlayerId!,
                       targetId: entityIdString,
                       label: 'supplies',
                       type: 'consumption_link',
                       directed: true
                     });
                }

                // Updated "attacking" edge creation
                if (entity instanceof Unit && commonNodeData.isAttacking && commonNodeData.currentTargetId) {
                    const targetNodeId = `entity_${commonNodeData.currentTargetId}`;
                    if (nodes.find(n => n.id === targetNodeId)) { // Check if target node exists
                        edges.push({
                            id: `edge_attack_${entityIdString}_${targetNodeId}`,
                            sourceId: entityIdString,
                            targetId: targetNodeId,
                            label: `DMG: ${commonNodeData.recentDamageDealt.toFixed(0)}`,
                            type: 'attacking',
                            directed: true,
                            color: '#FF0000', // Red for attack
                            thickness: Math.max(1, Math.min(5, 1 + Math.log10(commonNodeData.recentDamageDealt + 1))),
                            data: {
                                damageDealt: commonNodeData.recentDamageDealt
                            }
                        });
                    }
                }
            }
        });
    } else {
        console.warn("EntityManager not provided to exportGameStateToSpaceGraphData. Entity nodes and edges will be omitted.");
    }

    return { nodes, edges };
}
