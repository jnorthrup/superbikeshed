// rtsgame/js/core/resourceManager.ts
import { createCursor, Cursor, Series, createSeries } from 'trikeshed-ts'; // Assuming Series/createSeries might be used later or by other functions.

export enum PlayerResourceType { Mass, Energy, Computronium, Ferrite, Crylithium }
// NUM_PLAYER_RESOURCE_TYPES will be 5 (0 to 4)
export const NUM_PLAYER_RESOURCE_TYPES = Object.keys(PlayerResourceType).length / 2;

export enum MapResourceType { MassDeposit, FerriteVein, CrylithiumVein }
// NUM_MAP_RESOURCE_TYPES will be 3 (0 to 2)
export const NUM_MAP_RESOURCE_TYPES = Object.keys(MapResourceType).length / 2;

// Assuming a maximum number of players (e.g., 2 for now)
export const MAX_PLAYERS = 2;
// Assuming a maximum number of distinct map resource nodes
export const MAX_MAP_NODES = 10;

// Placeholder for initial amounts - these would come from config or game setup
const initialPlayerAmounts = [
  // Player 0 (index 0)
  [1000, 1000, 100, 50, 20], // Mass, Energy, Computronium, Ferrite, Crylithium
  // Player 1 (index 1)
  [1000, 1000, 100, 50, 20],
];

const initialMapNodesData: Array<[number, number, MapResourceType, number, number]> = [
  // x, y, type (MapResourceType enum value), currentAmount, initialMaxAmount
  [100, 100, MapResourceType.MassDeposit, 5000, 5000],
  [200, 200, MapResourceType.FerriteVein, 2000, 2000],
  [150, 150, MapResourceType.CrylithiumVein, 1000, 1000],
  // ... more nodes up to MAX_MAP_NODES can be added here
];

// --- Player Resource Management ---

export function initPlayerResources(): Cursor<number> {
  return createCursor<number>(
    MAX_PLAYERS,
    NUM_PLAYER_RESOURCE_TYPES,
    (playerIdx, resourceIdx) => initialPlayerAmounts[playerIdx]?.[resourceIdx] ?? 0
  );
}

export function getPlayerResources(playerResources: Cursor<number>, playerId: number): ReadonlyArray<number> {
  if (playerId < 0 || playerId >= playerResources.rows) {
    throw new Error(`Invalid playerId: ${playerId}`);
  }
  const resources: number[] = [];
  for (let i = 0; i < playerResources.cols; i++) {
    resources.push(playerResources.get([playerId, i]));
  }
  return Object.freeze(resources);
}

export function getPlayerResource(playerResources: Cursor<number>, playerId: number, resourceType: PlayerResourceType): number {
  if (playerId < 0 || playerId >= playerResources.rows || resourceType < 0 || resourceType >= playerResources.cols) {
    throw new Error(`Invalid playerId (${playerId}) or resourceType (${resourceType})`);
  }
  return playerResources.get([playerId, resourceType as number]);
}

export function updatePlayerResource(
  playerResources: Cursor<number>,
  playerId: number,
  resourceType: PlayerResourceType,
  newAmount: number
): Cursor<number> {
  if (playerId < 0 || playerId >= playerResources.rows || resourceType < 0 || resourceType >= playerResources.cols) {
    throw new Error(`Invalid playerId (${playerId}) or resourceType (${resourceType}) for update`);
  }
  return playerResources.alpha((currentValue, coords) => {
    if (coords[0] === playerId && coords[1] === (resourceType as number)) {
      return newAmount < 0 ? 0 : newAmount; // Ensure non-negative
    }
    return currentValue;
  });
}

export function addPlayerResource(
  playerResources: Cursor<number>,
  playerId: number,
  resourceType: PlayerResourceType,
  amountToAdd: number
): Cursor<number> {
  const currentAmount = getPlayerResource(playerResources, playerId, resourceType);
  const newAmount = currentAmount + amountToAdd;
  return updatePlayerResource(playerResources, playerId, resourceType, newAmount < 0 ? 0 : newAmount);
}

// --- Map Resource Node Management ---

// Define column indices for map resource nodes for clarity
export const MAP_NODE_X_COL = 0;
export const MAP_NODE_Y_COL = 1;
export const MAP_NODE_TYPE_COL = 2;
export const MAP_NODE_CURRENT_AMOUNT_COL = 3;
export const MAP_NODE_MAX_AMOUNT_COL = 4;
export const NUM_MAP_NODE_COLS = 5;

export function initMapResourceNodes(): Cursor<number> { // Store all as numbers for simplicity
  const numNodesToInit = Math.min(MAX_MAP_NODES, initialMapNodesData.length);
  return createCursor<number>(
    MAX_MAP_NODES, // Max possible nodes
    NUM_MAP_NODE_COLS,
    (nodeIdx, colIdx) => {
      if (nodeIdx < numNodesToInit) {
        const nodeData = initialMapNodesData[nodeIdx];
        // Ensure the column index is valid for the source data
        if (colIdx < nodeData.length) {
            return nodeData[colIdx];
        }
        return 0; // Default if colIdx is out of bounds for this specific node's data
      }
      // For nodes beyond initial data, or non-existent nodes, initialize with 0 or appropriate placeholder
      // e.g. for type, you might want -1 or a specific "empty" type if 0 is a valid MapResourceType
      if (colIdx === MAP_NODE_TYPE_COL) return -1; // Example: -1 for "no type"
      return 0;
    }
  );
}

export interface MapResourceNodeData {
  x: number;
  y: number;
  type: MapResourceType;
  currentAmount: number;
  maxAmount: number;
}

export function getMapResourceNode(mapResourceNodes: Cursor<number>, nodeIdx: number): MapResourceNodeData | null {
  if (nodeIdx < 0 || nodeIdx >= mapResourceNodes.rows) {
    return null;
  }
  // A simple check if the node is "active" (e.g. has a max amount > 0, or type is valid)
  const nodeType = mapResourceNodes.get([nodeIdx, MAP_NODE_TYPE_COL]);
  if (nodeType < 0 || mapResourceNodes.get([nodeIdx, MAP_NODE_MAX_AMOUNT_COL]) === 0) {
      // Consider type -1 or some other indicator as "inactive" or "uninitialized"
      return null;
  }

  return {
    x: mapResourceNodes.get([nodeIdx, MAP_NODE_X_COL]),
    y: mapResourceNodes.get([nodeIdx, MAP_NODE_Y_COL]),
    type: nodeType as MapResourceType, // Cast to enum; ensure valid enum values are stored
    currentAmount: mapResourceNodes.get([nodeIdx, MAP_NODE_CURRENT_AMOUNT_COL]),
    maxAmount: mapResourceNodes.get([nodeIdx, MAP_NODE_MAX_AMOUNT_COL]),
  };
}

export function updateMapResourceNodeAmount(
  mapResourceNodes: Cursor<number>,
  nodeIdx: number,
  newAmount: number
): Cursor<number> {
  if (nodeIdx < 0 || nodeIdx >= mapResourceNodes.rows) {
    throw new Error(`Invalid nodeIdx (${nodeIdx}) for update`);
  }
  return mapResourceNodes.alpha((currentValue, coords) => {
    if (coords[0] === nodeIdx && coords[1] === MAP_NODE_CURRENT_AMOUNT_COL) {
      const maxAmount = mapResourceNodes.get([nodeIdx, MAP_NODE_MAX_AMOUNT_COL]);
      // Ensure newAmount is not less than 0 and not more than maxAmount
      const finalAmount = Math.max(0, Math.min(newAmount, maxAmount));
      return finalAmount;
    }
    return currentValue;
  });
}

export interface ExtractionResult {
  newNodesState: Cursor<number>;
  extractedAmount: number;
}

export function extractFromMapResourceNode(
  mapResourceNodes: Cursor<number>,
  nodeIdx: number,
  amountToExtract: number
): ExtractionResult {
  const node = getMapResourceNode(mapResourceNodes, nodeIdx);
  if (!node || amountToExtract <= 0) {
    return { newNodesState: mapResourceNodes, extractedAmount: 0 };
  }
  const actualExtractedAmount = Math.min(node.currentAmount, amountToExtract);
  const newCurrentAmount = node.currentAmount - actualExtractedAmount;
  const newNodesState = updateMapResourceNodeAmount(mapResourceNodes, nodeIdx, newCurrentAmount);
  return { newNodesState, extractedAmount: actualExtractedAmount };
}
