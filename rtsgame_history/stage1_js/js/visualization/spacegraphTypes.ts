// rtsgame/js/visualization/spacegraphTypes.ts

export interface SpaceGraphNode {
  id: string;
  label: string;
  type: string; // e.g., 'player', 'mapResourceNode', 'unit', 'building'
  data?: Record<string, any>; // For additional arbitrary data
  color?: string;
  size?: number;
  // Optional: x, y, z for initial positioning if SpaceGraph supports it
  x?: number;
  y?: number;
  z?: number;
}

export interface SpaceGraphEdge {
  id: string; // Unique ID for the edge
  sourceId: string; // ID of the source node
  targetId: string; // ID of the target node
  label?: string;
  type?: string; // e.g., 'extractingFrom', 'ownedBy', 'attacking'
  directed?: boolean;
  color?: string;
  thickness?: number;
}

export interface SpaceGraphData {
  nodes: SpaceGraphNode[];
  edges: SpaceGraphEdge[];
}
