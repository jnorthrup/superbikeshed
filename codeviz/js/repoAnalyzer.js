// Code Visualizer - Nexus Context Analyzer Logic (Placeholder)
console.log("Code Visualizer repoAnalyzer.js (Nexus Context Analyzer) loaded.");

/**
 * @file Provides mock data representing a "Nexus context" for visualization.
 * This includes conceptual modules, components, code files, services, data topics,
 * and their relationships.
 */

/**
 * Generates mock data for visualizing a Nexus context.
 *
 * Node data structure for SpaceGraph's `loadDynamicData`:
 *  - id: {string} Unique ID for the node.
 *  - type: {string} A category for the node (e.g., 'module', 'codeFile', 'service'). Used for styling.
 *  - label: {string} Display label for the node.
 *  - data: {object} Contains detailed properties for the node.
 *      - contentDetails: {string} HTML string for rich display within an HtmlNodeElement.
 *      - backgroundColor: {string} CSS color for HtmlNodeElement background.
 *      - nodeClass: {string} Additional CSS classes for styling.
 *      - width, height: {number} Optional dimensions for HtmlNodeElement.
 *      - typeName: {'shape'|undefined} If 'shape', this node will be a ShapeNode.
 *      - shape: {string} ('box', 'sphere', etc.) For ShapeNode.
 *      - color: {number} Hex color for ShapeNode.
 *      - size: {number} Size for ShapeNode.
 *      - ...other custom properties (version, path, lines, status, endpoint, schema, priority).
 *  - x, y, z: {number} Initial position coordinates.
 *
 * Edge data structure:
 *  - id: {string} Unique ID for the edge.
 *  - sourceId: {string} ID of the source node.
 *  - targetId: {string} ID of the target node.
 *  - label: {string} Display label for the edge.
 *  - data: {object} Contains properties for the edge.
 *      - type: {string} Category of the relationship (e.g., 'dependsOn', 'calls').
 *      - couplingStrength: {number} (0.0-1.0) Conceptual strength of connection.
 *      - magnitude: {number} For attention links.
 *      - color: {number} Hex color for the edge line.
 *      - thickness: {number} Thickness of the edge line.
 *      - style: {'solid'|'dashed'} Line style.
 *      - constraintParams: {object} For ForceLayout (e.g., { stiffness, idealLength }).
 *
 * @returns {{nodes: Array<object>, edges: Array<object>}} Mock graph data.
 */
export function getMockRepoData() {
    const nodes = [
        // Core Nexus Modules
        {
            id: 'nexusCore', type: 'module', label: 'Nexus Core',
            data: {
                contentDetails: '<strong>Version:</strong> 1.2.0<br/>Main orchestrator and core services.',
                backgroundColor: 'rgba(180,180,220,0.85)',
                typeName: 'shape', shape: 'box', color: 0xb4b4dc, size: 35
            },
            x: 0, y: 0, z: 0
        },
        {
            id: 'attentionOntology', type: 'codeFile', label: 'AttentionOntology.kt',
            data: {
                contentDetails: '<strong>Path:</strong> nexus/ontology/<br/><strong>Lines:</strong> 150<br/>Defines attention flow and causality.',
                backgroundColor: 'rgba(200,255,200,0.85)',
                width: 220, height: 100
            },
            x: -250, y: -180, z: 0
        },
        {
            id: 'ideAdapter', type: 'component', label: 'IDE Adapter',
            data: {
                contentDetails: '<strong>Status:</strong> active<br/>IntelliJ telemetry and context provider.',
                backgroundColor: 'rgba(255,200,200,0.85)',
                width: 200, height: 90
            },
            x: 250, y: -180, z: 0
        },

        // Services
        {
            id: 'persistenceService', type: 'service', label: 'Persistence Service',
            data: {
                contentDetails: '<strong>Endpoint:</strong> /api/data<br/>Handles data storage via CouchDB & IPFS.',
                backgroundColor: 'rgba(200,220,255,0.85)',
                typeName: 'shape', shape: 'box', color: 0xCADCFF, size: 30
            },
            x: 0, y: 250, z: 0
        },
        {
            id: 'couchDbApi', type: 'codeFile', label: 'CouchDbApi.kt',
            data: {
                contentDetails: '<strong>Path:</strong> nexus/api/<br/><strong>Lines:</strong> 250<br/>Actual CouchDB client.',
                backgroundColor: 'rgba(200,255,200,0.85)',
                width: 200, height: 90
            },
            x: -200, y: 380, z: 0
        },
        {
            id: 'ipfsBridge', type: 'codeFile', label: 'IpfsBridge.kt',
            data: {
                contentDetails: '<strong>Path:</strong> nexus/bridge/<br/><strong>Lines:</strong> 180<br/>IPFS interaction layer.',
                backgroundColor: 'rgba(200,255,200,0.85)',
                width: 200, height: 90
            },
            x: 200, y: 380, z: 0
        },

        // Data Topics & Communication
        {
            id: 'telemetryTopic', type: 'dataTopic', label: 'Telemetry Events',
            data: {
                contentDetails: '<strong>Schema:</strong> K2ScriptTelemetryEvent<br/><strong>Subscribers:</strong> 2<br/>Topic for system telemetry.',
                backgroundColor: 'rgba(255,255,200,0.85)',
                typeName: 'shape', shape: 'sphere', color: 0xFFFFCC, size: 25
            },
            x: 400, y: 0, z: 0
        },
        {
            id: 'ircService', type: 'service', label: 'IRC Service',
            data: {
                contentDetails: '<strong>Status:</strong> planned<br/>Handles IRC communication.',
                backgroundColor: 'rgba(220,220,220,0.85)', nodeClass: 'type-service type-planned', // Keep specific class for planned
                typeName: 'shape', shape: 'box', color: 0xDDDDDD, size: 28
            },
            x: -400, y: 0, z: 0
        },

        // Attention Focus example (could be dynamic)
        {
            id: 'currentFocus', type: 'attentionFocus', label: 'Focus: Data Persistence',
            data: {
                contentDetails: '<strong>Priority:</strong> 0.8<br/>Currently optimizing CouchDB interactions.',
                backgroundColor: 'rgba(255,180,180,0.9)',
                width: 280, height: 90
            },
            x: 0, y: -300, z: 0
        },
    ];

    const edges = [
        // Nexus Core dependencies and components
        { id: 'e1', sourceId: 'nexusCore', targetId: 'attentionOntology', label: 'uses',
          data: { type: 'dependsOn', couplingStrength: 0.9, color: 0x8888ff, constraintParams: { stiffness: 0.005, idealLength: 150 } } },
        { id: 'e2', sourceId: 'nexusCore', targetId: 'ideAdapter', label: 'integrates',
          data: { type: 'contains', couplingStrength: 0.7, color: 0x88aaff, thickness: 2.5, constraintParams: { stiffness: 0.003, idealLength: 180 } } },
        { id: 'e3', sourceId: 'nexusCore', targetId: 'persistenceService', label: 'manages',
          data: { type: 'calls', couplingStrength: 0.8, color: 0x88ccff, constraintParams: { stiffness: 0.004, idealLength: 160 } } },

        // Persistence Service details
        { id: 'e4', sourceId: 'persistenceService', targetId: 'couchDbApi', label: 'uses',
          data: { type: 'dependsOn', couplingStrength: 1.0, color: 0xaaaaff, thickness: 3, constraintParams: { stiffness: 0.01, idealLength: 100 } } },
        { id: 'e5', sourceId: 'persistenceService', targetId: 'ipfsBridge', label: 'uses',
          data: { type: 'dependsOn', couplingStrength: 0.6, color: 0xaaaaff, constraintParams: { stiffness: 0.002, idealLength: 120 } } },

        // Telemetry flow
        { id: 'e6', sourceId: 'ideAdapter', targetId: 'telemetryTopic', label: 'publishesTo',
          data: { type: 'publishesTo', messageType: 'K2ScriptTelemetryEvent', color: 0xffaa00, constraintParams: { idealLength: 250 } } },
        { id: 'e7', sourceId: 'nexusCore', targetId: 'telemetryTopic', label: 'subscribesTo',
          data: { type: 'subscribesTo', color: 0xffaa88, constraintParams: { idealLength: 250 } } },

        // IRC Service conceptual link
        { id: 'e8', sourceId: 'nexusCore', targetId: 'ircService', label: 'will use',
          data: { type: 'plannedDependency', couplingStrength: 0.3, color: 0xbbbbbb, style: 'dashed', constraintParams: { stiffness: 0.0005, idealLength: 220 } } },

        // Attention links (inspired by AttentionOntology)
        { id: 'e9', sourceId: 'currentFocus', targetId: 'persistenceService', label: 'attention',
          data: { type: 'attentionLink', magnitude: 80, color: 0xff0000, thickness: 3.5, constraintParams: { stiffness: 0.008, idealLength: 80 } } },
        { id: 'e10', sourceId: 'currentFocus', targetId: 'couchDbApi', label: 'detail',
          data: { type: 'attentionLink', magnitude: 60, color: 0xff4444, thickness: 2.0, style: 'dashed', constraintParams: { stiffness: 0.005, idealLength: 100 } } },
    ];

    // Add nodeClass to data for easier CSS styling if it's an HtmlNodeElement
    nodes.forEach(node => {
        // Ensure data object exists
        node.data = node.data || {};
        if (!node.data.typeName || node.data.typeName !== 'shape') { // Default to HtmlNodeElement
            const baseClass = `type-${node.type}`;
            node.data.nodeClass = node.data.nodeClass ? `${node.data.nodeClass} ${baseClass}` : baseClass;
        }
    });

    return { nodes, edges };
}
