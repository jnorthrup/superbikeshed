import { forceSimulation, forceManyBody, forceCenter, forceLink, Simulation } from 'd3-force';
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
  private simulation: Simulation<WikipediaNode, WikipediaEdge>;

  constructor(config: Partial<FractalineConfig> = {}) {
    this.config = {
      centerForce: 0.05,
      repelForce: -100,
      linkForce: 0.05,
      depthSpacing: 15.0,
      spiralTightness: 2.0,
      levelVariation: 0.5,
      ...config
    };
  }

  private calculateLevels(nodes: WikipediaNode[], edges: WikipediaEdge[], startNodeId: string): Map<string, number> {
    const levels = new Map<string, number>();
    const adj = new Map<string, string[]>();

    edges.forEach(edge => {
      const sourceId = typeof edge.source === 'string' ? edge.source : edge.source.id;
      const targetId = typeof edge.target === 'string' ? edge.target : edge.target.id;
      if (!adj.has(sourceId)) adj.set(sourceId, []);
      if (!adj.has(targetId)) adj.set(targetId, []);
      adj.get(sourceId)!.push(targetId);
      adj.get(targetId)!.push(sourceId);
    });

    const queue: [string, number][] = [[startNodeId, 0]];
    const visited = new Set<string>([startNodeId]);
    levels.set(startNodeId, 0);

    while (queue.length > 0) {
      const [u, level] = queue.shift()!;
      (adj.get(u) || []).forEach(v => {
        if (!visited.has(v)) {
          visited.add(v);
          levels.set(v, level + 1);
          queue.push([v, level + 1]);
        }
      });
    }

    nodes.forEach(node => {
        if(!levels.has(node.id)) {
            levels.set(node.id, Infinity);
        }
    })

    return levels;
  }

  public computeFractalineLayout(nodes: WikipediaNode[], edges: WikipediaEdge[]): WikipediaNode[] {
    if (nodes.length === 0) return [];

    const layoutNodes = nodes.map(node => ({ ...node, x: 0, y: 0, z: 0 }));
    const startNodeId = nodes[0].id;
    const levels = this.calculateLevels(layoutNodes, edges, startNodeId);

    layoutNodes.forEach(node => {
      const level = levels.get(node.id) || 0;
      node.level = level;

      const angle = Math.random() * 2 * Math.PI;
      const radius = level * this.config.spiralTightness + (Math.random() - 0.5);
      
      node.x = Math.cos(angle) * radius;
      node.y = Math.sin(angle) * radius;
      node.z = -level * this.config.depthSpacing + (Math.random() - 0.5) * this.config.levelVariation;
    });

    this.setupSimulation(layoutNodes, edges);
    
    return layoutNodes;
  }

  private setupSimulation(nodes: WikipediaNode[], edges: WikipediaEdge[]) {
    this.simulation = forceSimulation(nodes)
      .force('charge', forceManyBody().strength(this.config.repelForce))
      .force('center', forceCenter(0, 0).strength(this.config.centerForce))
      .force('link', forceLink(edges).id((d: WikipediaNode) => d.id).strength(this.config.linkForce))
      .on('tick', () => {
        nodes.forEach(node => {
          node.position = { x: node.x!, y: node.y!, z: node.z! };
        });
      });
  }

  public updateLayout(nodes: WikipediaNode[]): void {
    if (this.simulation) {
      this.simulation.nodes(nodes);
      this.simulation.alpha(0.3).restart();
    }
  }

  public stop(): void {
    if (this.simulation) {
      this.simulation.stop();
    }
  }
}

export function createSemanticClusters(nodes: WikipediaNode[]): Map<string, WikipediaNode[]> {
    const clusters = new Map<string, WikipediaNode[]>();
    nodes.forEach(node => {
        const domain = node.domain || 'Other';
        if (!clusters.has(domain)) {
            clusters.set(domain, []);
        }
        clusters.get(domain)!.push(node);
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