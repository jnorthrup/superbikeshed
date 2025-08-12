import { useState, useEffect, useCallback } from 'react';
import { fetchWikipediaLinks, fetchArticleDetails } from '../utils/wikipediaApi';
import type { WikipediaGraph, WikipediaNode, Edge } from '../types/graph';

export function useWikipediaGraph(
  startTitle: string | null,
  depth: number = 1,
  maxNodes: number = 50
) {
  const [graph, setGraph] = useState<WikipediaGraph>({ nodes: [], edges: [] });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState({ loaded: 0, total: 0 });

  const generateGraph = useCallback(async () => {
    if (!startTitle) return;

    setLoading(true);
    setError(null);
    setGraph({ nodes: [], edges: [] });
    setProgress({ loaded: 0, total: 0 });

    try {
      const nodesToProcess = new Set<string>([startTitle]);
      const processedNodes = new Set<string>();
      const allNodes = new Map<string, WikipediaNode>();
      const allEdges: Edge[] = [];

      let currentDepth = 0;

      while (currentDepth < depth && nodesToProcess.size > 0 && allNodes.size < maxNodes) {
        const currentLevelNodes = Array.from(nodesToProcess);
        nodesToProcess.clear();

        const newNodesDetails = await fetchArticleDetails(currentLevelNodes);

        for (const node of newNodesDetails) {
          if (!allNodes.has(node.id)) {
            allNodes.set(node.id, node);
            processedNodes.add(node.id);
            setProgress(prev => ({ ...prev, loaded: allNodes.size }));
          }
        }

        setProgress(prev => ({ ...prev, total: prev.total + currentLevelNodes.length }));

        for (const title of currentLevelNodes) {
          if (allNodes.size >= maxNodes) break;

          const links = await fetchWikipediaLinks(title);
          const validLinks = links.slice(0, Math.min(links.length, 5)); // Limit links per node

          for (const link of validLinks) {
            allEdges.push({ source: title, target: link });
            if (!processedNodes.has(link) && allNodes.size < maxNodes) {
              nodesToProcess.add(link);
            }
          }
        }
        currentDepth++;
      }

      const finalNodes = Array.from(allNodes.values());
      const finalNodeIds = new Set(finalNodes.map(n => n.id));
      const finalEdges = allEdges.filter(e => finalNodeIds.has(e.source) && finalNodeIds.has(e.target));

      setGraph({ nodes: finalNodes, edges: finalEdges });

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate graph');
    } finally {
      setLoading(false);
    }
  }, [startTitle, depth, maxNodes]);

  useEffect(() => {
    if (startTitle) {
      generateGraph();
    }
  }, [generateGraph, startTitle]);

  return { graph, loading, error, progress, regenerate: generateGraph };
}
