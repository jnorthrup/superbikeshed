import { useState } from 'react';
import { GraphVisualization } from './components/GraphVisualization';
import { useWikipediaGraph } from './hooks/useWikipediaGraph';
import './App.css';

function App() {
  const [startArticle, setStartArticle] = useState<string>('React (software)');
  const [inputArticle, setInputArticle] = useState<string>('React (software)');
  const { graph, loading, error, progress } = useWikipediaGraph(startArticle, 2, 100);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setStartArticle(inputArticle);
  };

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
          Dynamic Wikipedia Graph
        </h1>
        <p style={{ margin: '0', fontSize: '0.9em', opacity: 0.8 }}>
          {graph.nodes.length} nodes, {graph.edges.length} edges
        </p>
        <p style={{ margin: '5px 0 0 0', fontSize: '0.8em', opacity: 0.6 }}>
          Enter a Wikipedia article to start exploring
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
        minWidth: '250px'
      }}>
        <h3 style={{ margin: '0 0 15px 0', fontSize: '1em' }}>Controls</h3>
        <form onSubmit={handleSearch}>
          <input
            type="text"
            value={inputArticle}
            onChange={(e) => setInputArticle(e.target.value)}
            placeholder="Start article"
            style={{
              width: '100%',
              padding: '8px',
              boxSizing: 'border-box',
              borderRadius: '4px',
              border: '1px solid #555',
              backgroundColor: '#333',
              color: 'white'
            }}
          />
          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '10px',
              marginTop: '10px',
              backgroundColor: loading ? '#555' : '#4ecdc4',
              color: 'white',
              border: 'none',
              borderRadius: '5px',
              cursor: 'pointer'
            }}
          >
            {loading ? 'Loading...' : 'Generate Graph'}
          </button>
        </form>
        {loading && (
          <div style={{ marginTop: '10px' }}>
            <p>Loading: {progress.loaded} / {progress.total} nodes</p>
            <div style={{ width: '100%', backgroundColor: '#555', borderRadius: '4px' }}>
              <div style={{ 
                width: `${(progress.loaded / progress.total) * 100}%`,
                height: '5px',
                backgroundColor: '#4ecdc4',
                borderRadius: '4px'
              }}></div>
            </div>
          </div>
        )}
        {error && <p style={{ color: '#ff6b6b', marginTop: '10px' }}>Error: {error}</p>}
      </div>

      <GraphVisualization nodes={graph.nodes} edges={graph.edges} />
    </div>
  );
}

export default App;
