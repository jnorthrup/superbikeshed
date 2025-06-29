# Code Visualizer Application

## Overview

The Code Visualizer is a web-based application designed to provide an "attractive visual code repo cloud blackboard". It aims to represent components of a software system (conceptually, the "Nexus context") as an interactive 3D graph. Users can visualize relationships, manage items on a virtual blackboard, and interact via a conceptual chat client.

This application utilizes the `spacegraph.js` library (originally from the `rtsgame` module) for the 3D graph visualization and leverages browser localStorage to simulate persistence of the blackboard state ("cloud" aspect).

## Features

1.  **Visual Blackboard (`spacegraph.js`):**
    *   Displays a 3D force-directed graph of nodes and edges.
    *   Nodes can represent various system entities like modules, components, code files, services, data topics, and attention focus areas.
    *   Nodes can be HTML-based (displaying rich content) or simple 3D shapes.
    *   Edges represent relationships like dependencies, communication paths, or attention links, with visual cues for strength/type (color, thickness).
    *   Users can drag nodes, and the layout adjusts dynamically.
    *   Context menus provide options for interacting with nodes and the graph.

2.  **Nexus Context Visualization (Conceptual):**
    *   Loads mock data representing a "Nexus context" from `js/repoAnalyzer.js`.
    *   This data includes different types of system components and their relationships, demonstrating how Nexus might visualize its operational context.
    *   Node content is formatted to show relevant details (e.g., version, path, status, schema).

3.  **Chat Client Console (Conceptual):**
    *   A simple chat interface is provided.
    *   It uses a conceptual `IRCClient` (`js/ircClient.js`) that simulates connecting to an IRC server, joining a channel, and sending/receiving messages.
    *   Actual IRC network communication is not implemented; interactions are logged to the console and displayed in the UI with simulated delays and responses.

4.  **Data Persistence (Simulated "Cloud"):**
    *   The state of the visual blackboard (nodes and edges) can be "saved" and "loaded".
    *   This persistence is simulated using the browser's `localStorage`.
    *   The application attempts to load any saved state upon startup.

5.  **User Interface:**
    *   A split layout with the main blackboard area and a side panel for the chat client.
    *   Control buttons to:
        *   Add test nodes.
        *   Save the current context (to localStorage).
        *   Load the saved context (from localStorage).
        *   Load the mock Nexus context data.

## Setup and Running

1.  **Dependencies:**
    *   This application relies on `three.js` and `gsap` (GreenSock Animation Platform), which are loaded via CDN in `index.html`. Ensure you have an internet connection for these to load.
    *   `spacegraph.js` and `spacegraph.css` are included in the `lib/` directory.

2.  **Serving the Application:**
    *   Due to the use of ES Modules (`import`/`export` syntax in JavaScript), you need to serve these files from a local web server. Opening `index.html` directly from the file system (`file:///...`) will likely result in CORS errors or module loading failures.
    *   Any simple HTTP server will do. For example, if you have Python installed:
        ```bash
        # Navigate to the codeviz/ directory in your terminal
        cd path/to/your/project/codeviz
        # For Python 3
        python -m http.server
        # For Python 2
        # python -m SimpleHTTPServer
        ```
    *   Then, open your browser and go to `http://localhost:8000` (or the port specified by your server).

3.  **Using the Application:**
    *   The blackboard will initialize, attempting to load data from localStorage or falling back to mock Nexus context data.
    *   Use the buttons in the bottom-left control panel to interact with the graph.
    *   Use the chat panel on the right to send (simulated) messages.

## Files and Structure

*   `index.html`: The main HTML page for the application.
*   `css/style.css`: Custom styles for the application, supplementing `spacegraph.css`.
*   `lib/`: Contains the `spacegraph.js` library and its CSS.
    *   `spacegraph.js`: Core 3D graph visualization library.
    *   `spacegraph.css`: Styles for `spacegraph.js` elements.
*   `js/`: Contains the application-specific JavaScript.
    *   `main.js`: Initializes `SpaceGraph`, handles blackboard interactions, and simulated persistence.
    *   `chat.js`: Manages the chat UI and interacts with `ircClient.js`.
    *   `ircClient.js`: Conceptual IRC client for simulating chat functionality.
    *   `repoAnalyzer.js`: Provides mock data representing a "Nexus context".

## Known Limitations

*   **Conceptual IRC:** The IRC client does not connect to a real IRC network. All chat interactions are simulated.
*   **Conceptual Backend:** Data persistence is simulated using `localStorage`. There is no actual backend server or CouchDB integration in this frontend prototype.
*   **Limited Code Analysis:** The "Nexus context" data is mock data. No actual code analysis or context introspection is performed by this frontend application.
*   **Edge Styling:** While edge data includes `style: 'dashed'`, `spacegraph.js` has not been modified to render dashed lines; they will appear solid. Color and thickness are supported.
*   **Error Handling:** Basic error handling is in place, but it could be more robust for a production application.

## Future Development Ideas

*   Integrate a real JavaScript IRC library.
*   Develop a backend server (e.g., using Ktor/Nexus) with CouchDB integration for true cloud persistence.
*   Implement actual "Nexus context" analysis to feed real data into the visualizer.
*   Enhance `spacegraph.js` to support more visual customization for nodes and edges (e.g., icons, different shapes based on data, dashed/dotted lines).
*   Add more sophisticated UI controls for filtering, searching, and navigating the graph.
*   Implement features from `spacegraph/docs/conceptual_ui_outline.md` like "Live Portals" or "Data Weaving".

This application serves as a proof-of-concept for the requested features.
