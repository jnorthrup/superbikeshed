"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.NUM_MAP_NODE_COLS = exports.MAP_NODE_MAX_AMOUNT_COL = exports.MAP_NODE_CURRENT_AMOUNT_COL = exports.MAP_NODE_TYPE_COL = exports.MAP_NODE_Y_COL = exports.MAP_NODE_X_COL = exports.MAX_MAP_NODES = exports.MAX_PLAYERS = exports.NUM_MAP_RESOURCE_TYPES = exports.MapResourceType = exports.NUM_PLAYER_RESOURCE_TYPES = exports.PlayerResourceType = void 0;
exports.initPlayerResources = initPlayerResources;
exports.getPlayerResources = getPlayerResources;
exports.getPlayerResource = getPlayerResource;
exports.updatePlayerResource = updatePlayerResource;
exports.addPlayerResource = addPlayerResource;
exports.initMapResourceNodes = initMapResourceNodes;
exports.getMapResourceNode = getMapResourceNode;
exports.updateMapResourceNodeAmount = updateMapResourceNodeAmount;
exports.extractFromMapResourceNode = extractFromMapResourceNode;
// rtsgame/js/core/resourceManager.ts
const trikeshed_ts_1 = require("trikeshed-ts"); // Assuming Series/createSeries might be used later or by other functions.
var PlayerResourceType;
(function (PlayerResourceType) {
    PlayerResourceType[PlayerResourceType["Mass"] = 0] = "Mass";
    PlayerResourceType[PlayerResourceType["Energy"] = 1] = "Energy";
    PlayerResourceType[PlayerResourceType["Computronium"] = 2] = "Computronium";
    PlayerResourceType[PlayerResourceType["Ferrite"] = 3] = "Ferrite";
    PlayerResourceType[PlayerResourceType["Crylithium"] = 4] = "Crylithium";
})(PlayerResourceType || (exports.PlayerResourceType = PlayerResourceType = {}));
// NUM_PLAYER_RESOURCE_TYPES will be 5 (0 to 4)
exports.NUM_PLAYER_RESOURCE_TYPES = Object.keys(PlayerResourceType).length / 2;
var MapResourceType;
(function (MapResourceType) {
    MapResourceType[MapResourceType["MassDeposit"] = 0] = "MassDeposit";
    MapResourceType[MapResourceType["FerriteVein"] = 1] = "FerriteVein";
    MapResourceType[MapResourceType["CrylithiumVein"] = 2] = "CrylithiumVein";
})(MapResourceType || (exports.MapResourceType = MapResourceType = {}));
// NUM_MAP_RESOURCE_TYPES will be 3 (0 to 2)
exports.NUM_MAP_RESOURCE_TYPES = Object.keys(MapResourceType).length / 2;
// Assuming a maximum number of players (e.g., 2 for now)
exports.MAX_PLAYERS = 2;
// Assuming a maximum number of distinct map resource nodes
exports.MAX_MAP_NODES = 10;
// Placeholder for initial amounts - these would come from config or game setup
const initialPlayerAmounts = [
    // Player 0 (index 0)
    [1000, 1000, 100, 50, 20], // Mass, Energy, Computronium, Ferrite, Crylithium
    // Player 1 (index 1)
    [1000, 1000, 100, 50, 20],
];
const initialMapNodesData = [
    // x, y, type (MapResourceType enum value), currentAmount, initialMaxAmount
    [100, 100, MapResourceType.MassDeposit, 5000, 5000],
    [200, 200, MapResourceType.FerriteVein, 2000, 2000],
    [150, 150, MapResourceType.CrylithiumVein, 1000, 1000],
    // ... more nodes up to MAX_MAP_NODES can be added here
];
// --- Player Resource Management ---
function initPlayerResources() {
    return (0, trikeshed_ts_1.createCursor)(exports.MAX_PLAYERS, exports.NUM_PLAYER_RESOURCE_TYPES, (playerIdx, resourceIdx) => { var _a, _b; return (_b = (_a = initialPlayerAmounts[playerIdx]) === null || _a === void 0 ? void 0 : _a[resourceIdx]) !== null && _b !== void 0 ? _b : 0; });
}
function getPlayerResources(playerResources, playerId) {
    if (playerId < 0 || playerId >= playerResources.rows) {
        throw new Error(`Invalid playerId: ${playerId}`);
    }
    const resources = [];
    for (let i = 0; i < playerResources.cols; i++) {
        resources.push(playerResources.get([playerId, i]));
    }
    return Object.freeze(resources);
}
function getPlayerResource(playerResources, playerId, resourceType) {
    if (playerId < 0 || playerId >= playerResources.rows || resourceType < 0 || resourceType >= playerResources.cols) {
        throw new Error(`Invalid playerId (${playerId}) or resourceType (${resourceType})`);
    }
    return playerResources.get([playerId, resourceType]);
}
function updatePlayerResource(playerResources, playerId, resourceType, newAmount) {
    if (playerId < 0 || playerId >= playerResources.rows || resourceType < 0 || resourceType >= playerResources.cols) {
        throw new Error(`Invalid playerId (${playerId}) or resourceType (${resourceType}) for update`);
    }
    return playerResources.alpha((currentValue, coords) => {
        if (coords[0] === playerId && coords[1] === resourceType) {
            return newAmount < 0 ? 0 : newAmount; // Ensure non-negative
        }
        return currentValue;
    });
}
function addPlayerResource(playerResources, playerId, resourceType, amountToAdd) {
    const currentAmount = getPlayerResource(playerResources, playerId, resourceType);
    const newAmount = currentAmount + amountToAdd;
    return updatePlayerResource(playerResources, playerId, resourceType, newAmount < 0 ? 0 : newAmount);
}
// --- Map Resource Node Management ---
// Define column indices for map resource nodes for clarity
exports.MAP_NODE_X_COL = 0;
exports.MAP_NODE_Y_COL = 1;
exports.MAP_NODE_TYPE_COL = 2;
exports.MAP_NODE_CURRENT_AMOUNT_COL = 3;
exports.MAP_NODE_MAX_AMOUNT_COL = 4;
exports.NUM_MAP_NODE_COLS = 5;
function initMapResourceNodes() {
    const numNodesToInit = Math.min(exports.MAX_MAP_NODES, initialMapNodesData.length);
    return (0, trikeshed_ts_1.createCursor)(exports.MAX_MAP_NODES, // Max possible nodes
    exports.NUM_MAP_NODE_COLS, (nodeIdx, colIdx) => {
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
        if (colIdx === exports.MAP_NODE_TYPE_COL)
            return -1; // Example: -1 for "no type"
        return 0;
    });
}
function getMapResourceNode(mapResourceNodes, nodeIdx) {
    if (nodeIdx < 0 || nodeIdx >= mapResourceNodes.rows) {
        return null;
    }
    // A simple check if the node is "active" (e.g. has a max amount > 0, or type is valid)
    const nodeType = mapResourceNodes.get([nodeIdx, exports.MAP_NODE_TYPE_COL]);
    if (nodeType < 0 || mapResourceNodes.get([nodeIdx, exports.MAP_NODE_MAX_AMOUNT_COL]) === 0) {
        // Consider type -1 or some other indicator as "inactive" or "uninitialized"
        return null;
    }
    return {
        x: mapResourceNodes.get([nodeIdx, exports.MAP_NODE_X_COL]),
        y: mapResourceNodes.get([nodeIdx, exports.MAP_NODE_Y_COL]),
        type: nodeType, // Cast to enum; ensure valid enum values are stored
        currentAmount: mapResourceNodes.get([nodeIdx, exports.MAP_NODE_CURRENT_AMOUNT_COL]),
        maxAmount: mapResourceNodes.get([nodeIdx, exports.MAP_NODE_MAX_AMOUNT_COL]),
    };
}
function updateMapResourceNodeAmount(mapResourceNodes, nodeIdx, newAmount) {
    if (nodeIdx < 0 || nodeIdx >= mapResourceNodes.rows) {
        throw new Error(`Invalid nodeIdx (${nodeIdx}) for update`);
    }
    return mapResourceNodes.alpha((currentValue, coords) => {
        if (coords[0] === nodeIdx && coords[1] === exports.MAP_NODE_CURRENT_AMOUNT_COL) {
            const maxAmount = mapResourceNodes.get([nodeIdx, exports.MAP_NODE_MAX_AMOUNT_COL]);
            // Ensure newAmount is not less than 0 and not more than maxAmount
            const finalAmount = Math.max(0, Math.min(newAmount, maxAmount));
            return finalAmount;
        }
        return currentValue;
    });
}
function extractFromMapResourceNode(mapResourceNodes, nodeIdx, amountToExtract) {
    const node = getMapResourceNode(mapResourceNodes, nodeIdx);
    if (!node || amountToExtract <= 0) {
        return { newNodesState: mapResourceNodes, extractedAmount: 0 };
    }
    const actualExtractedAmount = Math.min(node.currentAmount, amountToExtract);
    const newCurrentAmount = node.currentAmount - actualExtractedAmount;
    const newNodesState = updateMapResourceNodeAmount(mapResourceNodes, nodeIdx, newCurrentAmount);
    return { newNodesState, extractedAmount: actualExtractedAmount };
}
