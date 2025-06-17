import React from 'react';

function ResourceDisplay({ resources }) {
  if (!resources || !resources.blue || !resources.red) {
    return <div id="resource-display-react">Loading resources...</div>;
  }

  return (
    <div id="resource-display-react" style={{ border: '1px solid #ccc', padding: '10px', margin: '10px' }}>
      <h2>Resources (React)</h2>
      <div className="team-resources blue" style={{ marginBottom: '10px' }}>
        <h3 style={{ color: 'blue', margin: '0 0 5px 0' }}>Blue Team</h3>
        <p style={{ margin: '2px 0' }}>Mass: <span id="react-blueMass">{Math.floor(resources.blue.mass)}</span> (Income: {resources.blue.massIncome || 0}/s)</p>
        <p style={{ margin: '2px 0' }}>Energy: <span id="react-blueEnergy">{Math.floor(resources.blue.energy)}</span> (Income: {resources.blue.energyIncome || 0}/s)</p>
      </div>
      <div className="team-resources red">
        <h3 style={{ color: 'red', margin: '0 0 5px 0' }}>Red Team</h3>
        <p style={{ margin: '2px 0' }}>Mass: <span id="react-redMass">{Math.floor(resources.red.mass)}</span> (Income: {resources.red.massIncome || 0}/s)</p>
        <p style={{ margin: '2px 0' }}>Energy: <span id="react-redEnergy">{Math.floor(resources.red.energy)}</span> (Income: {resources.red.energyIncome || 0}/s)</p>
      </div>
    </div>
  );
}

export default ResourceDisplay;
