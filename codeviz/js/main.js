// Code Visualizer - Main Application Logic
import { SpaceGraph, HtmlNodeElement, NoteNode } from '../lib/spacegraph.js'; // Removed ShapeNode, Edge as they are used internally by SpaceGraph
import { getMockRepoData } from './repoAnalyzer.js';

console.log("Code Visualizer main.js loaded.");

// --- Conceptual Backend/Persistence API Simulation using localStorage ---
const GRAPH_STORAGE_KEY = 'codeviz_graph_data';

/**
 * Simulates saving graph data to a backend. Uses localStorage for demo.
 * @param {object} graphData - The graph data object to save.
 * @returns {Promise<object>} A promise that resolves with a success/failure message.
 */
async function simulateSaveGraph(graphData) {
    console.log("[SimBackend] Attempting to save graph data:", graphData);
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            try {
                localStorage.setItem(GRAPH_STORAGE_KEY, JSON.stringify(graphData));
                console.log("[SimBackend] Graph data saved successfully to localStorage.");
                resolve({ success: true, message: "Context saved successfully (simulated)." });
            } catch (error) {
                console.error("[SimBackend] Error saving graph data to localStorage:", error);
                reject({ success: false, message: "Failed to save context (simulated)." });
            }
        }, 500); // Simulate network delay
    });
}

/**
 * Simulates loading graph data from a backend. Uses localStorage for demo.
 * @returns {Promise<object|null>} A promise that resolves with the loaded graph data or null if not found.
 */
async function simulateLoadGraph() {
    console.log("[SimBackend] Attempting to load graph data.");
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            try {
                const storedData = localStorage.getItem(GRAPH_STORAGE_KEY);
                if (storedData) {
                    console.log("[SimBackend] Graph data loaded successfully from localStorage.");
                    resolve(JSON.parse(storedData));
                } else {
                    console.log("[SimBackend] No saved graph data found in localStorage. Returning null to trigger mock data load.");
                    resolve(null); // Return null if nothing is stored, so mock data can be loaded.
                }
            } catch (error) {
                console.error("[SimBackend] Error loading graph data from localStorage:", error);
                reject({ message: "Failed to load context (simulated)." });
            }
        }, 500); // Simulate network delay
    });
}
// --- End of Conceptual Backend Simulation ---

/**
 * Formats the HTML content for a node based on its data.
 * This is used to prepare the 'content' property for HtmlNodeElement instances.
 * @param {object} node - The node object from graphData (e.g., from repoAnalyzer).
 *                        Expected to have `label`, `type`, and `data.contentDetails`.
 * @returns {object} A data object suitable for the HtmlNodeElement constructor,
 *                   with a `content` property for its HTML.
 */
function formatNodeHTMLContent(node) {
    let htmlContent = `<strong>${node.label || node.id}</strong><br/><em>Type: ${node.type}</em>`;
    if (node.data && node.data.contentDetails) {
        htmlContent += `<hr style="margin: 4px 0;">${node.data.contentDetails}`;
    } else if (node.data && node.data.content) { // Fallback for simple content from direct add
         htmlContent += `<hr style="margin: 4px 0;">${node.data.content}`;
    }

    // Return a new data object suitable for HtmlNodeElement constructor
    // It includes the formatted HTML content and other relevant properties from node.data.
    const constructorData = {
        ...(node.data || {}), // Spread original data first
        content: htmlContent,  // Override/set the content
        label: node.label || node.id // Ensure label is present for SpaceGraph's internal use
    };
    return constructorData;
}


document.addEventListener('DOMContentLoaded', async () => {
    const blackboardContainer = document.getElementById('blackboard-container');
    if (!blackboardContainer) {
        console.error("Blackboard container not found!");
        return;
    }

    blackboardContainer.style.position = 'relative';

    let sgInstance = null;
    try {
        sgInstance = new SpaceGraph(blackboardContainer);
        console.log("SpaceGraph initialized.");
    } catch (error) {
        console.error("Failed to initialize SpaceGraph:", error);
        blackboardContainer.innerHTML = `<p style="color:red;padding:20px;">Error initializing SpaceGraph: ${error.message}. Check console for details.</p>`;
        return;
    }

    /**
     * Monkey-patches the SpaceGraph instance's loadDynamicData method
     * to pre-process node data for richer HTML content display.
     * This ensures that `HtmlNodeElement`s are created with detailed content.
     */
    const originalLoadDynamicData = sgInstance.loadDynamicData.bind(sgInstance);
    sgInstance.loadDynamicData = (graphData) => {
        if (graphData && graphData.nodes) {
            graphData.nodes.forEach(node => {
                if (!node.data?.typeName || node.data.typeName !== 'shape') { // Process for HTML nodes
                    node.data = formatNodeHTMLContent(node);
                }
            });
        }
        originalLoadDynamicData(graphData); // Call original method with processed data
    };

    /**
     * Loads initial graph data, trying persistence first, then mock data.
     */
    async function loadInitialData() {
        try {
            console.log("Attempting to load initial graph data from persistence...");
            const initialData = await simulateLoadGraph();
            if (sgInstance && typeof sgInstance.loadDynamicData === 'function') {
                if (initialData && (initialData.nodes?.length > 0 || initialData.edges?.length > 0)) {
                    sgInstance.loadDynamicData(initialData);
                    console.log("Loaded persisted graph data into SpaceGraph.");
                } else {
                    console.log("No persisted data or empty graph found. Loading mock Nexus context instead.");
                    sgInstance.loadDynamicData(getMockRepoData());
                }
                sgInstance.centerView();
            } else {
                 console.warn("SpaceGraph instance or loadDynamicData function not available for initial load.");
            }
        } catch (error) {
            console.error("Failed to load initial graph data:", error);
            alert("Error loading previously saved context. Check console.");
            if (sgInstance && typeof sgInstance.loadDynamicData === 'function') {
                console.log("Falling back to loading mock Nexus context due to load error.");
                sgInstance.loadDynamicData(getMockRepoData());
                sgInstance.centerView();
            }
        }
    }
    await loadInitialData();

    // --- Button Event Listeners ---
    const addNodeBtn = document.getElementById('addNodeBtn');
    if (addNodeBtn) {
        addNodeBtn.addEventListener('click', () => {
            console.log("Add Test Node clicked.");
            if (sgInstance) {
                const randomX = (Math.random() - 0.5) * 600;
                const randomY = (Math.random() - 0.5) * 400;
                const nodeData = {
                    label: 'Test Note', // Label for the node
                    content: `<strong>Test Note</strong><br/>Created: ${new Date().toLocaleTimeString()}`,
                    backgroundColor: 'rgba(230,230,250,0.85)',
                    width: 180, height: 70, nodeClass: 'type-note'
                };
                // Note: SpaceGraph's HtmlNodeElement constructor takes (id, position, data).
                // The 'data' object should contain the 'content' field for HTML.
                const newNode = new HtmlNodeElement(null, { x: randomX, y: randomY, z: 0 }, nodeData);
                sgInstance.addNode(newNode);
            }
        });
    }

    const saveContextBtn = document.getElementById('saveContextBtn');
    if (saveContextBtn) {
        saveContextBtn.addEventListener('click', async () => {
            console.log("Save Context clicked.");
            if (sgInstance && typeof sgInstance.getGraphData === 'function') {
                const dataToSave = sgInstance.getGraphData();
                console.log("Data to save:", JSON.stringify(dataToSave, null, 2));
                try {
                    const response = await simulateSaveGraph(dataToSave);
                    alert(response.message);
                    console.log("Save response:", response);
                } catch (error) {
                    alert(error.message);
                    console.error("Save error:", error);
                }
            } else {
                console.warn("SpaceGraph instance or getGraphData function not available.");
                alert("Could not get graph data to save.");
            }
        });
    }

    const loadContextBtn = document.getElementById('loadContextBtn');
    if (loadContextBtn) {
        loadContextBtn.addEventListener('click', async () => {
            console.log("Load Saved Context clicked.");
            if (sgInstance && typeof sgInstance.loadDynamicData === 'function') {
                try {
                    const dataToLoad = await simulateLoadGraph();
                     if (dataToLoad && (dataToLoad.nodes?.length > 0 || dataToLoad.edges?.length > 0)) {
                        sgInstance.loadDynamicData(dataToLoad);
                        sgInstance.centerView();
                        alert("Loaded context from (simulated) persistence.");
                    } else {
                        alert("No data in (simulated) persistence or graph is empty. Try saving first or use 'Load Mock Context'.");
                    }
                } catch (error) {
                    alert(error.message);
                    console.error("Load error:", error);
                }
            } else {
                console.warn("SpaceGraph instance or loadDynamicData function not available.");
                alert("Could not load graph data.");
            }
        });
    }

    const loadMockContextBtn = document.getElementById('loadMockContextBtn');
    if (loadMockContextBtn) {
        loadMockContextBtn.addEventListener('click', () => {
            console.log("Load Mock Context clicked.");
            if (sgInstance && typeof sgInstance.loadDynamicData === 'function') {
                const mockData = getMockRepoData();
                console.log("Loading mock Nexus context:", mockData);
                sgInstance.loadDynamicData(mockData);
                sgInstance.centerView();
            } else {
                 console.warn("SpaceGraph instance or loadDynamicData function not available for mock load.");
                alert("Could not load mock context.");
            }
        });
    }

    // Expose sgInstance for debugging
    window.sg = sgInstance;
});
