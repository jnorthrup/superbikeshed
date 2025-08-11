import { useState, useEffect } from 'react';
import { GraphVisualization } from './components/GraphVisualization';
import { useGraphData } from './hooks/useGraphData';
import { WikipediaParser } from './utils/wikipediaParser';
import type { WikipediaGraph } from './types/graph';
import './App.css';

function App() {
  const { graph: sampleGraph, loading, error } = useGraphData('/data/sample-wiki-graph.json');
  const [currentGraph, setCurrentGraph] = useState<WikipediaGraph | null>(null);
  const [dataSource, setDataSource] = useState<'sample' | 'enhanced'>('sample');
  
  // Initialize enhanced data
  useEffect(() => {
    if (dataSource === 'enhanced') {
      const parser = new WikipediaParser();
      const enhancedGraph = parser.createEnhancedSampleData();
      setCurrentGraph(enhancedGraph);
    } else if (sampleGraph) {
      setCurrentGraph(sampleGraph);
    }
  }, [dataSource, sampleGraph]);

  const handleDataSourceChange = (source: 'sample' | 'enhanced') => {
    setDataSource(source);
  };

  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        color: 'white',
        fontSize: '1.2em'
      }}>
        Loading Wikipedia Graph...
      </div>
    );
  }

  if (error && dataSource === 'sample') {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        color: '#ff6b6b',
        fontSize: '1.2em'
      }}>
        Error loading sample data: {error}
        <button 
          onClick={() => handleDataSourceChange('enhanced')}
          style={{
            marginLeft: '20px',
            padding: '10px 20px',
            backgroundColor: '#4ecdc4',
            color: 'white',
            border: 'none',
            borderRadius: '5px',
            cursor: 'pointer'
          }}
        >
          Use Enhanced Data
        </button>
      </div>
    );
  }

  if (!currentGraph) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        color: 'white',
        fontSize: '1.2em'
      }}>
        No graph data available
      </div>
    );
  }

  return (
    <div style={{ width: '100vw', height: '100vh', background: '#1a1a1a' }}>
      {/* Header UI */}
      <div style={{
        position: 'absolute',
        top: '20px',
        left: '20px',
        zIndex: 1000,
        color: 'white',
        fontFamily: 'Arial, sans-serif'
      }}>
        <h1 style={{ margin: '0 0 10px 0', fontSize: '1.5em' }}>
          2.5D Fractaline Wikipedia Graph
        </h1>
        <p style={{ margin: '0', fontSize: '0.9em', opacity: 0.8 }}>
          {currentGraph.nodes.length} nodes, {currentGraph.edges.length} edges
        </p>
        <p style={{ margin: '5px 0 0 0', fontSize: '0.8em', opacity: 0.6 }}>
          Click nodes to select • Drag to rotate • Scroll to zoom
        </p>
      </div>

      {/* Controls Panel */}
      <div style={{
        position: 'absolute',
        top: '20px',
        right: '20px',
        zIndex: 1000,
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        padding: '15px',
        borderRadius: '8px',
        color: 'white',
        fontFamily: 'Arial, sans-serif',
        minWidth: '200px'
      }}>
        <h3 style={{ margin: '0 0 15px 0', fontSize: '1em' }}>Data Source</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
            <input
              type="radio"
              name="dataSource"
              value="sample"
              checked={dataSource === 'sample'}
              onChange={() => handleDataSourceChange('sample')}
              style={{ marginRight: '8px' }}
            />
            Sample (6 nodes)
          </label>
          <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
            <input
              type="radio"
              name="dataSource"
              value="enhanced"
              checked={dataSource === 'enhanced'}
              onChange={() => handleDataSourceChange('enhanced')}
              style={{ marginRight: '8px' }}
            />
            Enhanced ({new WikipediaParser().createEnhancedSampleData().nodes.length} nodes)
          </label>
        </div>
        
        <div style={{ marginTop: '15px', paddingTop: '15px', borderTop: '1px solid #444' }}>
          <h4 style={{ margin: '0 0 10px 0', fontSize: '0.9em' }}>Legend</h4>
          <div style={{ fontSize: '0.8em', lineHeight: '1.4' }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '5px' }}>
              <div style={{ 
                width: '12px', 
                height: '12px', 
                borderRadius: '50%', 
                backgroundColor: '#ffe66d', 
                marginRight: '8px' 
              }}></div>
              Mathematics
            </div>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '5px' }}>
              <div style={{ 
                width: '12px', 
                height: '12px', 
                borderRadius: '50%', 
                backgroundColor: '#ff8b94', 
                marginRight: '8px' 
              }}></div>
              Physics
            </div>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '5px' }}>
              <div style={{ 
                width: '12px', 
                height: '12px', 
                borderRadius: '50%', 
                backgroundColor: '#a8e6cf', 
                marginRight: '8px' 
              }}></div>
              Computer Science
            </div>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <div style={{ 
                width: '12px', 
                height: '12px', 
                borderRadius: '50%', 
                backgroundColor: '#dda0dd', 
                marginRight: '8px' 
              }}></div>
              Other
            </div>
          </div>
        </div>
      </div>

      {/* Info Panel */}
      <div style={{
        position: 'absolute',
        bottom: '20px',
        left: '20px',
        zIndex: 1000,
        backgroundColor: 'rgba(0, 0, 0, 0.7)',
        padding: '15px',
        borderRadius: '8px',
        color: 'white',
        fontFamily: 'Arial, sans-serif',
        maxWidth: '300px'
      }}>
        <h4 style={{ margin: '0 0 10px 0', fontSize: '0.9em' }}>Features</h4>
        <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '0.8em', lineHeight: '1.4' }}>
          <li>Fractaline recursive layout with depth spacing</li>
          <li>Semantic clustering by knowledge domain</li>
          <li>2.5D depth cues with fog and opacity</li>
          <li>Dynamic animations and trails</li>
          <li>Cross-disciplinary connection mapping</li>
        </ul>
      </div>

      <GraphVisualization nodes={currentGraph.nodes} edges={currentGraph.edges} />
    </div>
  );
}

export default App;
