export interface Position3D {
  x: number;
  y: number;
  z: number;
}

export interface WikipediaNode {
  id: string;
  title: string;
  text: string;
  categories: string[];
  position: Position3D;
  level: number;
  // Add D3 simulation properties
  x?: number;
  y?: number;
  vx?: number;
  vy?: number;
  fx?: number | null;
  fy?: number | null;
}

export interface WikipediaEdge {
  source: string | WikipediaNode;
  target: string | WikipediaNode;
  weight: number;
}

export interface WikipediaGraph {
  nodes: WikipediaNode[];
  edges: WikipediaEdge[];
}

export interface VisualizationConfig {
  nodeRadius: number;
  edgeOpacity: number;
  levelSpacing: number;
  enableAnimation: boolean;
  showLabels: boolean;
}