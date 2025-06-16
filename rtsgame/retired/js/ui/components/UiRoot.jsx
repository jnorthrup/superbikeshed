import React from 'react';

import ResourceDisplay from './ResourceDisplay.jsx'; // Import the new component

function UiRoot({ gameState }) { // Accept gameState as a prop
  return (
    <div>
      <h1>Hello from React! This is the new UI Root.</h1>
      <p>Game UI will be built here.</p>
      
      {/* Render ResourceDisplay if gameState and gameState.resources are available */}
      {gameState && gameState.resources ? (
        <ResourceDisplay resources={gameState.resources} />
      ) : (
        <p>Waiting for game state resources...</p>
      )}

      {/* Future UI elements will replace the old DOM-based UI and minimap */}
      <div id="minimap-container-react"> {/* Placeholder for React-based minimap if needed */}
        {/* The old minimap is still drawn on #minimapCanvas by main.js for now */}
      </div>
      <div id="game-events-react"></div>
      <div id="status-updates-react"></div>
    </div>
  );
}

export default UiRoot;
