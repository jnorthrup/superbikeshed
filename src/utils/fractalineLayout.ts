import { forceSimulation, forceManyBody, forceCenter, forceLink } from 'd3-force';
import type { WikipediaNode, WikipediaEdge, Position3D } from '../types/graph';

export interface FractalineConfig {
  centerForce: number;
  repelForce: number;
  linkForce: number;
  depthSpacing: number;
  spiralTightness: number;
  levelVariation: number;
}

export class FractalineLayoutEngine {
  private config: FractalineConfig;

  constructor(config: Partial<FractalineConfig> = {}) {
    this.config = {
      centerForce: 0.1,
      repelForce: -300,
      linkForce: 0.1,
      depthSpacing: 2.0,
      spiralTightness: 0.5,
      levelVariation: 0.3,
      ...config
    };
  }

  public computeFractalineLayout(nodes: WikipediaNode[], edges: WikipediaEdge[]): WikipediaNode[] {
    // Create a copy of nodes to work with
    const layoutNodes = nodes.map(node => ({ ...node }));
    
    // Calculate hierarchy levels and connections
    const connections = new Map<string, string[]>();
    
    // Build connection map
    edges.forEach(edge => {
      const sourceId = typeof edge.source === 'string' ? edge.source : edge.source.id;
      const targetId = typeof edge.target === 'string' ? edge.target : edge.target.id;
      
      if (!connections.has(sourceId)) connections.set(sourceId, []);
      if (!connections.has(targetId)) connections.set(targetId, []);
      connections.get(sourceId)?.push(targetId);
      connections.get(targetId)?.push(sourceId);
    });

    // Apply fractaline positioning
    layoutNodes.forEach((node, index) => {
      const level = node.level || 0;
      const totalNodesAtLevel = layoutNodes.filter(n => n.level === level).length;
      const indexAtLevel = layoutNodes.filter(n => n.level === level).indexOf(node);
      
      // Create spiral pattern with depth variation
      const angle = (indexAtLevel / totalNodesAtLevel) * 2 * Math.PI;
      const radius = level * this.config.spiralTightness + 1;
      const depth = level * this.config.depthSpacing;
      
      // Add variation to prevent perfect geometric patterns
      const variation = (Math.sin(index * 0.7) + Math.cos(index * 1.3)) * this.config.levelVariation;
      
      node.position = {
        x: Math.cos(angle) * radius + variation,
        y: Math.sin(angle) * radius + variation * 0.5,
        z: depth + variation * 0.3
      };
      
      // Set D3 simulation properties
      node.x = node.position.x;
      node.y = node.position.y;
    });

    // Apply physics simulation for fine-tuning
    this.setupSimulation(layoutNodes, edges);
    
    return layoutNodes;
  }

  private setupSimulation(nodes: WikipediaNode[], edges: WikipediaEdge[]) {
    const simulation = forceSimulation(nodes)
      .force('charge', forceManyBody().strength(this.config.repelForce))
      .force('center', forceCenter().strength(this.config.centerForce))
      .force('link', forceLink(edges)
        .id((d: any) => d.id)
        .strength(this.config.linkForce))
      .alphaDecay(0.1)
      .velocityDecay(0.8);

    // Run simulation for a few iterations
    for (let i = 0; i < 100; i++) {
      simulation.tick();
    }
    
    // Update positions from simulation
    nodes.forEach(node => {
      if (node.x !== undefined && node.y !== undefined) {
        node.position.x = node.x;
        node.position.y = node.y;
      }
    });
    
    simulation.stop();
  }

  public updateLayout(nodes: WikipediaNode[]): void {
    // For this implementation, we'll just recompute the layout
    console.log('Layout updated for', nodes.length, 'nodes');
  }

  public stop(): void {
    // Simulation is stopped after computation
  }
}

// Helper function to create semantic clustering
export function createSemanticClusters(nodes: WikipediaNode[]): Map<string, WikipediaNode[]> {
  const clusters = new Map<string, WikipediaNode[]>();
  
  nodes.forEach(node => {
    // Group by primary category
    const primaryCategory = node.categories[0] || 'Uncategorized';
    if (!clusters.has(primaryCategory)) {
      clusters.set(primaryCategory, []);
    }
    clusters.get(primaryCategory)?.push(node);
  });
  
  return clusters;
}

// Generate interpolated positions for smooth animations
export function interpolatePosition(start: Position3D, end: Position3D, t: number): Position3D {
  return {
    x: start.x + (end.x - start.x) * t,
    y: start.y + (end.y - start.y) * t,
    z: start.z + (end.z - start.z) * t
  };
}