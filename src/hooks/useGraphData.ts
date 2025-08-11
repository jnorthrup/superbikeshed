import { useState, useEffect } from 'react';
import type { WikipediaGraph } from '../types/graph';

export const useGraphData = (dataUrl: string) => {
  const [graph, setGraph] = useState<WikipediaGraph | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadGraph = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const response = await fetch(dataUrl);
        if (!response.ok) {
          throw new Error(`Failed to fetch graph data: ${response.statusText}`);
        }
        
        const data = await response.json();
        setGraph(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error occurred');
        console.error('Error loading graph data:', err);
      } finally {
        setLoading(false);
      }
    };

    loadGraph();
  }, [dataUrl]);

  return { graph, loading, error };
};