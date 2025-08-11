import { GraphVisualization } from './components/GraphVisualization';
import { useGraphData } from './hooks/useGraphData';
import './App.css';

function App() {
  const { graph, loading, error } = useGraphData('/data/sample-wiki-graph.json');

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

  if (error) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        color: '#ff6b6b',
        fontSize: '1.2em'
      }}>
        Error: {error}
      </div>
    );
  }

  if (!graph) {
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
          {graph.nodes.length} nodes, {graph.edges.length} edges
        </p>
        <p style={{ margin: '5px 0 0 0', fontSize: '0.8em', opacity: 0.6 }}>
          Click nodes to select • Drag to rotate • Scroll to zoom
        </p>
      </div>
      <GraphVisualization nodes={graph.nodes} edges={graph.edges} />
    </div>
  );
}

export default App;
