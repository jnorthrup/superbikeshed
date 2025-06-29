"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.ForceLayout = exports.CameraController = exports.UIManager = exports.Edge = exports.ShapeNode = exports.NoteNode = exports.HtmlNodeElement = exports.SpaceGraph = exports.DEG2RAD = exports.generateId = exports.lerp = exports.clamp = exports.$$ = exports.$ = void 0;
const THREE = __importStar(require("three"));
const CSS3DRenderer_js_1 = require("three/addons/renderers/CSS3DRenderer.js");
const gsap_1 = require("gsap");
const $ = (selector, context = document) => context.querySelector(selector);
exports.$ = $;
const $$ = (selector, context = document) => Array.from(context.querySelectorAll(selector));
exports.$$ = $$;
const clamp = (val, min, max) => Math.max(min, Math.min(max, val));
exports.clamp = clamp;
const lerp = (a, b, t) => a + (b - a) * t;
exports.lerp = lerp;
const generateId = (prefix = 'id') => `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`;
exports.generateId = generateId;
exports.DEG2RAD = Math.PI / 180;
class SpaceGraph {
    constructor(containerElement, uiElements = {}) {
        this.nodes = new Map();
        this.edges = new Map();
        this.selectedNode = null;
        this.selectedEdge = null;
        this.isLinking = false;
        this.linkSourceNode = null;
        this.tempLinkLine = null;
        this.uiManager = null;
        this.cameraController = null;
        this.layoutEngine = null;
        this.background = { color: 0x000000, alpha: 0.0 };
        this.getNodeById = (id) => this.nodes.get(id);
        this.getEdgeById = (id) => this.edges.get(id);
        if (!containerElement)
            throw new Error("SpaceGraph requires a container element.");
        this.container = containerElement;
        this.scene = new THREE.Scene();
        this.cssScene = new THREE.Scene();
        this._camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 1, 20000);
        this._camera.position.z = 700;
        this._setupRenderers();
        this.setBackground(this.background.color, this.background.alpha);
        this.cameraController = new CameraController(this._camera, this.container);
        this.layoutEngine = new ForceLayout(this);
        this.uiManager = new UIManager(this, uiElements);
        this._setupLighting();
        this.centerView(null, 0);
        this.cameraController.setInitialState();
        window.addEventListener('resize', this._onWindowResize.bind(this), false);
        this._animate();
        this.layoutEngine.start();
    }
    _setupRenderers() {
        this.webglCanvas = (0, exports.$)('#webgl-canvas', this.container);
        if (!this.webglCanvas) {
            this.webglCanvas = document.createElement('canvas');
            this.webglCanvas.id = 'webgl-canvas';
            this.container.appendChild(this.webglCanvas);
        }
        this.webglRenderer = new THREE.WebGLRenderer({
            canvas: this.webglCanvas,
            antialias: true,
            alpha: true
        });
        this.webglRenderer.setSize(window.innerWidth, window.innerHeight);
        this.webglRenderer.setPixelRatio(window.devicePixelRatio);
        this.css3dContainer = (0, exports.$)('#css3d-container', this.container);
        if (!this.css3dContainer) {
            this.css3dContainer = document.createElement('div');
            this.css3dContainer.id = 'css3d-container';
            this.container.appendChild(this.css3dContainer);
        }
        Object.assign(this.css3dContainer.style, {
            position: 'absolute', inset: '0', width: '100%',
            height: '100%', pointerEvents: 'none', zIndex: '2'
        });
        this.cssRenderer = new CSS3DRenderer_js_1.CSS3DRenderer();
        this.cssRenderer.setSize(window.innerWidth, window.innerHeight);
        this.css3dContainer.appendChild(this.cssRenderer.domElement);
        this.webglCanvas.style.position = 'absolute';
        this.webglCanvas.style.inset = '0';
        this.webglCanvas.style.zIndex = '1';
    }
    _setupLighting() {
        const ambientLight = new THREE.AmbientLight(0xffffff, 0.8);
        this.scene.add(ambientLight);
        const directionalLight = new THREE.DirectionalLight(0xffffff, 0.7);
        directionalLight.position.set(0.5, 1, 0.75);
        this.scene.add(directionalLight);
    }
    setBackground(color = 0x000000, alpha = 0.0) {
        this.background = { color, alpha };
        this.webglRenderer.setClearColor(color, alpha);
        this.webglCanvas.style.backgroundColor = alpha === 0 ? 'transparent' : `#${color.toString(16).padStart(6, '0')}`;
    }
    addNode(nodeInstance) {
        var _a;
        if (!nodeInstance.id)
            nodeInstance.id = (0, exports.generateId)('node');
        if (this.nodes.has(nodeInstance.id))
            return this.nodes.get(nodeInstance.id);
        this.nodes.set(nodeInstance.id, nodeInstance);
        nodeInstance.spaceGraph = this;
        if (nodeInstance.cssObject)
            this.cssScene.add(nodeInstance.cssObject);
        if (nodeInstance.mesh)
            this.scene.add(nodeInstance.mesh);
        if (nodeInstance.labelObject)
            this.cssScene.add(nodeInstance.labelObject);
        (_a = this.layoutEngine) === null || _a === void 0 ? void 0 : _a.addNode(nodeInstance);
        return nodeInstance;
    }
    removeNode(nodeId) {
        var _a, _b;
        const node = this.nodes.get(nodeId);
        if (!node)
            return;
        if (this.selectedNode === node)
            this.setSelectedNode(null);
        if (this.linkSourceNode === node)
            (_a = this.uiManager) === null || _a === void 0 ? void 0 : _a.cancelLinking();
        const edgesToRemove = [...this.edges.values()].filter(edge => edge.source === node || edge.target === node);
        edgesToRemove.forEach(edge => this.removeEdge(edge.id));
        node.dispose();
        this.nodes.delete(nodeId);
        (_b = this.layoutEngine) === null || _b === void 0 ? void 0 : _b.removeNode(node);
    }
    addEdge(sourceNode, targetNode, data = {}) {
        var _a;
        if (!sourceNode || !targetNode || sourceNode === targetNode)
            return null;
        if ([...this.edges.values()].some(e => (e.source === sourceNode && e.target === targetNode) || (e.source === targetNode && e.target === sourceNode))) {
            console.warn("Duplicate edge ignored:", sourceNode.id, targetNode.id);
            return null;
        }
        const edgeId = (0, exports.generateId)('edge');
        const edge = new Edge(edgeId, sourceNode, targetNode, data);
        edge.spaceGraph = this;
        this.edges.set(edgeId, edge);
        if (edge.threeObject)
            this.scene.add(edge.threeObject);
        (_a = this.layoutEngine) === null || _a === void 0 ? void 0 : _a.addEdge(edge);
        return edge;
    }
    removeEdge(edgeId) {
        var _a;
        const edge = this.edges.get(edgeId);
        if (!edge)
            return;
        if (this.selectedEdge === edge)
            this.setSelectedEdge(null);
        edge.dispose();
        this.edges.delete(edgeId);
        (_a = this.layoutEngine) === null || _a === void 0 ? void 0 : _a.removeEdge(edge);
    }
    _updateNodesAndEdges() {
        var _a;
        this.nodes.forEach(node => node.update(this));
        this.edges.forEach(edge => edge.update(this));
        (_a = this.uiManager) === null || _a === void 0 ? void 0 : _a.updateEdgeMenuPosition();
    }
    _render() {
        this.webglRenderer.render(this.scene, this._camera);
        this.cssRenderer.render(this.cssScene, this._camera);
    }
    _animate() {
        this._updateNodesAndEdges();
        this._render();
        requestAnimationFrame(this._animate.bind(this));
    }
    _onWindowResize() {
        this._camera.aspect = window.innerWidth / window.innerHeight;
        this._camera.updateProjectionMatrix();
        this.webglRenderer.setSize(window.innerWidth, window.innerHeight);
        this.cssRenderer.setSize(window.innerWidth, window.innerHeight);
    }
    centerView(targetPosition = null, duration = 0.7) {
        let targetPos;
        if (targetPosition instanceof THREE.Vector3) {
            targetPos = targetPosition.clone();
        }
        else if (this.nodes.size > 0) {
            targetPos = new THREE.Vector3();
            this.nodes.forEach(node => targetPos.add(node.position));
            targetPos.divideScalar(this.nodes.size);
        }
        else {
            targetPos = new THREE.Vector3(0, 0, 0);
        }
        const distance = this.nodes.size > 1 ? 700 : 400;
        this.cameraController.moveTo(targetPos.x, targetPos.y, targetPos.z + distance, duration, targetPos);
    }
    focusOnNode(node, duration = 0.6, pushHistory = false) {
        if (!node || !this.cameraController || !this._camera)
            return;
        const targetPos = node.position.clone();
        const fov = this._camera.fov * exports.DEG2RAD;
        const aspect = this._camera.aspect;
        let nodeSize = 100;
        if (node.getBoundingSphereRadius) {
            nodeSize = node.getBoundingSphereRadius() * 2;
        }
        else if (node.size) {
            nodeSize = Math.max(node.size.width / aspect, node.size.height) * 1.2;
        }
        const distance = (nodeSize / (2 * Math.tan(fov / 2))) + 50;
        if (pushHistory)
            this.cameraController.pushState();
        this.cameraController.moveTo(targetPos.x, targetPos.y, targetPos.z + distance, duration, targetPos);
    }
    autoZoom(node) {
        if (!node || !this.cameraController)
            return;
        const currentTargetNodeId = this.cameraController.getCurrentTargetNodeId();
        if (currentTargetNodeId === node.id) {
            this.cameraController.popState();
        }
        else {
            this.cameraController.pushState();
            this.cameraController.setCurrentTargetNodeId(node.id);
            this.focusOnNode(node, 0.6, false);
        }
    }
    screenToWorld(screenX, screenY, targetZ = 0) {
        const vec = new THREE.Vector3((screenX / window.innerWidth) * 2 - 1, -(screenY / window.innerHeight) * 2 + 1, 0.5);
        const raycaster = new THREE.Raycaster();
        raycaster.setFromCamera(vec, this._camera);
        const plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -targetZ);
        const intersectPoint = new THREE.Vector3();
        return raycaster.ray.intersectPlane(plane, intersectPoint) ? intersectPoint : null;
    }
    setSelectedNode(node) {
        var _a, _b, _c, _d;
        if (this.selectedNode === node)
            return;
        (_a = this.selectedNode) === null || _a === void 0 ? void 0 : _a.setSelectedStyle(false);
        if ((_b = this.selectedNode) === null || _b === void 0 ? void 0 : _b.htmlElement)
            this.selectedNode.htmlElement.classList.remove('selected');
        this.selectedNode = node;
        (_c = this.selectedNode) === null || _c === void 0 ? void 0 : _c.setSelectedStyle(true);
        if ((_d = this.selectedNode) === null || _d === void 0 ? void 0 : _d.htmlElement)
            this.selectedNode.htmlElement.classList.add('selected');
        if (node)
            this.setSelectedEdge(null);
    }
    setSelectedEdge(edge) {
        var _a, _b, _c, _d;
        if (this.selectedEdge === edge)
            return;
        (_a = this.selectedEdge) === null || _a === void 0 ? void 0 : _a.setHighlight(false);
        (_b = this.uiManager) === null || _b === void 0 ? void 0 : _b.hideEdgeMenu();
        this.selectedEdge = edge;
        (_c = this.selectedEdge) === null || _c === void 0 ? void 0 : _c.setHighlight(true);
        if (edge) {
            this.setSelectedNode(null);
            (_d = this.uiManager) === null || _d === void 0 ? void 0 : _d.showEdgeMenu(edge);
        }
    }
    intersectedObject(screenX, screenY) {
        const vec = new THREE.Vector2((screenX / window.innerWidth) * 2 - 1, -(screenY / window.innerHeight) * 2 + 1);
        const raycaster = new THREE.Raycaster();
        raycaster.setFromCamera(vec, this._camera);
        raycaster.params.Line.threshold = 0.1; // Smaller threshold for more precise line intersection
        const nodeMeshes = [...this.nodes.values()].map(n => n.mesh).filter(Boolean);
        if (nodeMeshes.length > 0) {
            const intersects = raycaster.intersectObjects(nodeMeshes);
            if (intersects.length > 0) {
                const intersectedMesh = intersects[0].object;
                return [...this.nodes.values()].find(n => n.mesh === intersectedMesh);
            }
        }
        const edgeObjects = [...this.edges.values()].map(e => e.threeObject).filter(Boolean);
        if (edgeObjects.length > 0) {
            const intersects = raycaster.intersectObjects(edgeObjects);
            if (intersects.length > 0) {
                const intersectedLine = intersects[0].object;
                return [...this.edges.values()].find(edge => edge.threeObject === intersectedLine);
            }
        }
        return null;
    }
    dispose() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k;
        (_a = this.cameraController) === null || _a === void 0 ? void 0 : _a.dispose();
        (_b = this.layoutEngine) === null || _b === void 0 ? void 0 : _b.stop();
        this.nodes.forEach(node => node.dispose());
        this.edges.forEach(edge => edge.dispose());
        this.nodes.clear();
        this.edges.clear();
        (_c = this.scene) === null || _c === void 0 ? void 0 : _c.clear();
        (_d = this.cssScene) === null || _d === void 0 ? void 0 : _d.clear();
        (_e = this.webglRenderer) === null || _e === void 0 ? void 0 : _e.dispose();
        (_g = (_f = this.cssRenderer) === null || _f === void 0 ? void 0 : _f.domElement) === null || _g === void 0 ? void 0 : _g.remove();
        (_h = this.css3dContainer) === null || _h === void 0 ? void 0 : _h.remove();
        (_j = this.webglCanvas) === null || _j === void 0 ? void 0 : _j.remove();
        window.removeEventListener('resize', this._onWindowResize);
        (_k = this.uiManager) === null || _k === void 0 ? void 0 : _k.dispose();
        console.log("SpaceGraph disposed.");
    }
    clearGraph() {
        // Remove all edges first
        const edgeIds = Array.from(this.edges.keys());
        edgeIds.forEach(edgeId => this.removeEdge(edgeId));
        // Remove all nodes
        const nodeIds = Array.from(this.nodes.keys());
        nodeIds.forEach(nodeId => this.removeNode(nodeId));
        if (this.nodes.size !== 0 || this.edges.size !== 0) {
            console.warn("clearGraph: Not all nodes/edges were cleared effectively from maps. Forcing clear.");
            this.nodes.clear();
            this.edges.clear();
        }
        // Also clear from layout engine if possible - this part is crucial
        if (this.layoutEngine) {
            this.layoutEngine.nodes = []; // Reset internal arrays
            this.layoutEngine.edges = [];
            if (this.layoutEngine.velocities)
                this.layoutEngine.velocities.clear();
            if (this.layoutEngine.fixedNodes)
                this.layoutEngine.fixedNodes.clear();
            console.log("ForceLayout arrays cleared.");
        }
        console.log("Graph cleared.");
    }
    loadDynamicData(graphData) {
        var _a, _b;
        this.clearGraph();
        if (!graphData || !graphData.nodes) {
            console.warn("loadDynamicData: No data provided or data is malformed.");
            (_a = this.layoutEngine) === null || _a === void 0 ? void 0 : _a.kick(); // Kick even if empty to reset layout
            return;
        }
        // Create a map to store initial positions from graphData if provided
        const initialPositions = new Map();
        graphData.nodes.forEach(nodeData => {
            var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m;
            let nodeInstance;
            // Use provided x,y,z or default to random small values to avoid all nodes at 0,0,0 before layout
            const position = {
                x: (_a = nodeData.x) !== null && _a !== void 0 ? _a : (Math.random() - 0.5) * 200,
                y: (_b = nodeData.y) !== null && _b !== void 0 ? _b : (Math.random() - 0.5) * 200,
                z: (_c = nodeData.z) !== null && _c !== void 0 ? _c : (Math.random() - 0.5) * 50
            };
            initialPositions.set(nodeData.id, new THREE.Vector3(position.x, position.y, position.z));
            const constructorData = Object.assign(Object.assign({}, nodeData.data), { label: nodeData.label, type: nodeData.type, color: nodeData.color, size: nodeData.size }); // Pass all data from our exporter
            // Customize content for HtmlNodeElement based on type
            let content = `<strong>${nodeData.label || nodeData.id}</strong><br/>Type: ${nodeData.type}`;
            if (nodeData.type === 'playerResources') { // Matched type from exporter
                content = `<strong>Player ${nodeData.data.playerId}</strong>
                       <br/>Mass: ${(_d = nodeData.data.mass) === null || _d === void 0 ? void 0 : _d.toFixed(0)}
                       <br/>Energy: ${(_e = nodeData.data.energy) === null || _e === void 0 ? void 0 : _e.toFixed(0)}
                       <br/>Comp: ${(_f = nodeData.data.computronium) === null || _f === void 0 ? void 0 : _f.toFixed(0)}
                       <br/>Ferrite: ${(_g = nodeData.data.ferrite) === null || _g === void 0 ? void 0 : _g.toFixed(0)}
                       <br/>Crylithium: ${(_h = nodeData.data.crylithium) === null || _h === void 0 ? void 0 : _h.toFixed(0)}`;
                constructorData.width = nodeData.width || 220;
                constructorData.height = nodeData.height || 120; // Adjusted height for more resources
                constructorData.backgroundColor = nodeData.color || '#333355';
            }
            else if (nodeData.type === 'mapResourceNode') {
                content = `<strong>${nodeData.data.resourceType}</strong>
                       <br/>(${nodeData.id})
                       <br/>Amt: ${(_j = nodeData.data.currentAmount) === null || _j === void 0 ? void 0 : _j.toFixed(0)}/${(_k = nodeData.data.maxAmount) === null || _k === void 0 ? void 0 : _k.toFixed(0)}`;
                constructorData.width = nodeData.width || 200;
                constructorData.height = nodeData.height || 90;
                constructorData.backgroundColor = nodeData.color || '#553333';
            }
            else if (nodeData.type === 'structure' || nodeData.type === 'unit' || nodeData.type === 'building') {
                content = `<strong>${nodeData.label || nodeData.id}</strong>
                       <br/>Owner: ${nodeData.data.ownerPlayerId || 'N/A'}
                       <br/>Type: ${nodeData.data.entityType || nodeData.data.structureType || nodeData.type}
                       <br/>HP: ${(_l = nodeData.data.hp) === null || _l === void 0 ? void 0 : _l.toFixed(0)}/${(_m = nodeData.data.maxHp) === null || _m === void 0 ? void 0 : _m.toFixed(0)}`;
                constructorData.width = nodeData.width || (nodeData.type === 'unit' ? 180 : 200);
                constructorData.height = nodeData.height || (nodeData.type === 'unit' ? 80 : 100);
                constructorData.backgroundColor = nodeData.color || (nodeData.type === 'unit' ? '#555533' : '#335533');
            }
            else { // Default generic node
                content = `<strong>${nodeData.label || nodeData.id}</strong><br/>Type: ${nodeData.type}`;
                if (nodeData.data) { // Append any other data
                    Object.entries(nodeData.data).forEach(([key, value]) => {
                        if (typeof value === 'number')
                            value = value.toFixed(0);
                        if (key !== 'playerId' && key !== 'mass' && key !== 'energy' && key !== 'computronium' && key !== 'ferrite' && key !== 'crylithium' &&
                            key !== 'resourceType' && key !== 'currentAmount' && key !== 'maxAmount' && key !== 'x' && key !== 'y' &&
                            key !== 'ownerPlayerId' && key !== 'entityType' && key !== 'structureType' && key !== 'hp' && key !== 'maxHp' && key !== 'nodeId') {
                            content += `<br/>${key}: ${value}`;
                        }
                    });
                }
                constructorData.width = nodeData.width || 160;
                constructorData.height = nodeData.height || 70;
                constructorData.backgroundColor = nodeData.color || '#444444';
            }
            constructorData.content = content; // Set the composed HTML content
            // For now, all nodes are HtmlNodeElement. Could extend to use ShapeNode based on a property.
            nodeInstance = new HtmlNodeElement(nodeData.id, position, constructorData);
            if (nodeInstance) {
                this.addNode(nodeInstance);
            }
        });
        if (graphData.edges) {
            graphData.edges.forEach(edgeData => {
                const sourceNode = this.getNodeById(edgeData.sourceId);
                const targetNode = this.getNodeById(edgeData.targetId);
                if (sourceNode && targetNode) {
                    const edgeConstructorData = {
                        color: edgeData.color ? (typeof edgeData.color === 'string' ? parseInt(edgeData.color.replace('#', ''), 16) : edgeData.color) : undefined,
                        thickness: edgeData.thickness,
                        label: edgeData.label, // Pass label, Edge class can decide what to do
                        type: edgeData.type, // Pass type
                        directed: edgeData.directed
                    };
                    this.addEdge(sourceNode, targetNode, edgeConstructorData);
                }
                else {
                    console.warn(`Edge creation failed: Source (${edgeData.sourceId}) or Target (${edgeData.targetId}) node not found.`);
                }
            });
        }
        // Restore positions if layout is not immediately run, or to give layout good start points
        this.nodes.forEach(node => {
            const pos = initialPositions.get(node.id);
            if (pos)
                node.setPosition(pos.x, pos.y, pos.z);
        });
        (_b = this.layoutEngine) === null || _b === void 0 ? void 0 : _b.kick(1.5); // Stronger kick for new data
        // A brief delay before centering might allow layout to settle a bit
        // setTimeout(() => this.centerView(), 100);
    }
}
exports.SpaceGraph = SpaceGraph;
class BaseNode {
    constructor(id, position = { x: 0, y: 0, z: 0 }, data = {}, mass = 1.0) {
        this.id = null;
        this.spaceGraph = null;
        this.position = new THREE.Vector3();
        this.data = {};
        this.mass = 1.0;
        this.mesh = null;
        this.cssObject = null;
        this.htmlElement = null;
        this.labelObject = null;
        this.id = id !== null && id !== void 0 ? id : (0, exports.generateId)('node');
        this.position.set(position.x, position.y, position.z);
        this.data = Object.assign(Object.assign({}, this.getDefaultData()), data);
        this.mass = Math.max(0.1, mass);
    }
    getDefaultData() { return { label: this.id }; }
    setPosition(x, y, z) { this.position.set(x, y, z); }
    update(spaceGraphInstance) { }
    dispose() { }
    getBoundingSphereRadius() { return 10; }
    setSelectedStyle(selected) { }
    startDrag() {
        var _a, _b, _c;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.classList.add('dragging');
        (_c = (_b = this.spaceGraph) === null || _b === void 0 ? void 0 : _b.layoutEngine) === null || _c === void 0 ? void 0 : _c.fixNode(this);
    }
    drag(newPosition) {
        this.setPosition(newPosition.x, newPosition.y, newPosition.z);
    }
    endDrag() {
        var _a, _b, _c, _d, _e;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.classList.remove('dragging');
        (_c = (_b = this.spaceGraph) === null || _b === void 0 ? void 0 : _b.layoutEngine) === null || _c === void 0 ? void 0 : _c.releaseNode(this);
        (_e = (_d = this.spaceGraph) === null || _d === void 0 ? void 0 : _d.layoutEngine) === null || _e === void 0 ? void 0 : _e.kick();
    }
}
class HtmlNodeElement extends BaseNode {
    constructor(id, position = { x: 0, y: 0, z: 0 }, data = {}) {
        var _a, _b, _c, _d, _e;
        super(id, position, data, (_a = data.mass) !== null && _a !== void 0 ? _a : 1.0);
        this.size = { width: 160, height: 70 };
        this.billboard = true;
        this.size.width = (_b = data.width) !== null && _b !== void 0 ? _b : this.size.width;
        this.size.height = (_c = data.height) !== null && _c !== void 0 ? _c : this.size.height;
        this.billboard = (_d = data.billboard) !== null && _d !== void 0 ? _d : this.billboard;
        this.htmlElement = this._createHtmlElement();
        this.cssObject = new CSS3DRenderer_js_1.CSS3DObject(this.htmlElement);
        this.cssObject.userData = { nodeId: this.id, type: 'html-node' };
        this.update();
        this.setContentScale((_e = this.data.contentScale) !== null && _e !== void 0 ? _e : 1.0);
        if (this.data.backgroundColor) {
            this.setBackgroundColor(this.data.backgroundColor);
        }
    }
    getDefaultData() {
        return {
            label: '', content: '', type: 'html',
            width: 160, height: 70, contentScale: 1.0,
            backgroundColor: 'var(--node-bg-default)',
            editable: false
        };
    }
    _createHtmlElement() {
        const el = document.createElement('div');
        el.className = 'node-html';
        if (this.data.type === 'note')
            el.classList.add('note-node');
        el.id = `node-html-${this.id}`;
        el.dataset.nodeId = this.id;
        el.style.width = `${this.size.width}px`;
        el.style.height = `${this.size.height}px`;
        el.innerHTML = `
          <div class="node-inner-wrapper">
              <div class="node-content" spellcheck="false" style="transform: scale(${this.data.contentScale});">${this.data.label || this.data.content || ''}</div>
              <div class="node-controls">
                  <button class="node-quick-button node-content-zoom-in" title="Zoom In Content (+)">+</button>
                  <button class="node-quick-button node-content-zoom-out" title="Zoom Out Content (-)">-</button>
                  <button class="node-quick-button node-grow" title="Grow Node (Ctrl++)">➚</button>
                  <button class="node-quick-button node-shrink" title="Shrink Node (Ctrl+-)">➘</button>
                  <button class="node-quick-button delete-button node-delete" title="Delete Node (Del)">×</button>
              </div>
          </div>
          <div class="resize-handle" title="Resize Node"></div>
      `;
        if (this.data.editable)
            this._initContentEditable(el);
        return el;
    }
    _initContentEditable(element) {
        const contentDiv = (0, exports.$)('.node-content', element);
        if (contentDiv) {
            contentDiv.contentEditable = "true";
            let debounceTimer;
            contentDiv.addEventListener('input', () => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => {
                    this.data.content = contentDiv.innerHTML;
                    this.data.label = contentDiv.textContent || '';
                }, 300);
            });
            contentDiv.addEventListener('pointerdown', e => e.stopPropagation());
            contentDiv.addEventListener('touchstart', e => e.stopPropagation(), { passive: true });
            contentDiv.addEventListener('wheel', e => {
                if (contentDiv.scrollHeight > contentDiv.clientHeight || contentDiv.scrollWidth > contentDiv.clientWidth) {
                    e.stopPropagation();
                }
            }, { passive: false });
        }
    }
    setPosition(x, y, z) {
        super.setPosition(x, y, z);
        if (this.cssObject)
            this.cssObject.position.copy(this.position);
    }
    setSize(width, height, scaleContent = false) {
        var _a, _b;
        const oldWidth = this.size.width;
        const oldHeight = this.size.height;
        this.size.width = Math.max(80, width);
        this.size.height = Math.max(40, height);
        if (this.htmlElement) {
            this.htmlElement.style.width = `${this.size.width}px`;
            this.htmlElement.style.height = `${this.size.height}px`;
        }
        if (scaleContent && oldWidth > 0 && oldHeight > 0) {
            const scaleFactor = Math.sqrt((this.size.width * this.size.height) / (oldWidth * oldHeight));
            this.setContentScale(this.data.contentScale * scaleFactor);
        }
        this.data.width = this.size.width;
        this.data.height = this.size.height;
        (_b = (_a = this.spaceGraph) === null || _a === void 0 ? void 0 : _a.layoutEngine) === null || _b === void 0 ? void 0 : _b.kick();
    }
    setContentScale(scale) {
        var _a;
        this.data.contentScale = (0, exports.clamp)(scale, 0.3, 3.0);
        const contentEl = (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.querySelector('.node-content');
        if (contentEl)
            contentEl.style.transform = `scale(${this.data.contentScale})`;
    }
    setBackgroundColor(color) {
        var _a;
        this.data.backgroundColor = color;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.style.setProperty('--node-bg', this.data.backgroundColor);
    }
    adjustContentScale(deltaFactor) {
        this.setContentScale(this.data.contentScale * deltaFactor);
    }
    adjustNodeSize(factor) {
        this.setSize(this.size.width * factor, this.size.height * factor, false);
    }
    update(spaceGraphInstance) {
        if (this.cssObject) {
            this.cssObject.position.copy(this.position);
            if (this.billboard && (spaceGraphInstance === null || spaceGraphInstance === void 0 ? void 0 : spaceGraphInstance._camera)) {
                this.cssObject.quaternion.copy(spaceGraphInstance._camera.quaternion);
            }
        }
    }
    dispose() {
        var _a, _b, _c;
        super.dispose();
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.remove();
        (_c = (_b = this.cssObject) === null || _b === void 0 ? void 0 : _b.parent) === null || _c === void 0 ? void 0 : _c.remove(this.cssObject);
        this.htmlElement = null;
        this.cssObject = null;
    }
    getBoundingSphereRadius() {
        var _a;
        return Math.sqrt(Math.pow(this.size.width, 2) + Math.pow(this.size.height, 2)) / 2 * ((_a = this.data.contentScale) !== null && _a !== void 0 ? _a : 1.0);
    }
    setSelectedStyle(selected) {
        var _a;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.classList.toggle('selected', selected);
    }
    startResize() {
        var _a, _b, _c;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.classList.add('resizing');
        (_c = (_b = this.spaceGraph) === null || _b === void 0 ? void 0 : _b.layoutEngine) === null || _c === void 0 ? void 0 : _c.fixNode(this);
    }
    resize(newWidth, newHeight) { this.setSize(newWidth, newHeight); }
    endResize() {
        var _a, _b, _c;
        (_a = this.htmlElement) === null || _a === void 0 ? void 0 : _a.classList.remove('resizing');
        (_c = (_b = this.spaceGraph) === null || _b === void 0 ? void 0 : _b.layoutEngine) === null || _c === void 0 ? void 0 : _c.releaseNode(this);
    }
}
exports.HtmlNodeElement = HtmlNodeElement;
class NoteNode extends HtmlNodeElement {
    constructor(id, pos = { x: 0, y: 0, z: 0 }, data = { content: '' }) {
        const mergedData = Object.assign(Object.assign({}, data), { type: 'note', editable: true, label: data.content || data.label });
        super(id, pos, mergedData);
    }
}
exports.NoteNode = NoteNode;
class ShapeNode extends BaseNode {
    constructor(id, position, data = {}, mass = 1.5) {
        var _a, _b, _c;
        super(id, position, data, mass);
        this.shape = 'sphere';
        this.size = 50;
        this.color = 0xffffff;
        this.shape = (_a = this.data.shape) !== null && _a !== void 0 ? _a : this.shape;
        this.size = (_b = this.data.size) !== null && _b !== void 0 ? _b : this.size;
        this.color = (_c = this.data.color) !== null && _c !== void 0 ? _c : this.color;
        this.mesh = this._createMesh();
        this.mesh.userData = { nodeId: this.id, type: 'shape-node' };
        if (this.data.label) {
            this.labelObject = this._createLabel();
            this.labelObject.userData = { nodeId: this.id, type: 'shape-label' };
        }
        this.update();
    }
    getDefaultData() {
        return { label: '', shape: 'sphere', size: 50, color: 0xffffff, type: 'shape' };
    }
    _createMesh() {
        let geometry;
        const effectiveSize = Math.max(10, this.size);
        switch (this.shape) {
            case 'box':
                geometry = new THREE.BoxGeometry(effectiveSize, effectiveSize, effectiveSize);
                break;
            case 'sphere':
            default:
                geometry = new THREE.SphereGeometry(effectiveSize / 2, 16, 12);
                break;
        }
        const material = new THREE.MeshStandardMaterial({
            color: this.color,
            roughness: 0.6,
            metalness: 0.2,
        });
        return new THREE.Mesh(geometry, material);
    }
    _createLabel() {
        const div = document.createElement('div');
        div.className = 'node-label-3d';
        div.textContent = this.data.label;
        div.dataset.nodeId = this.id;
        Object.assign(div.style, {
            color: 'white', backgroundColor: 'rgba(0,0,0,0.6)', padding: '3px 6px',
            borderRadius: '4px', fontSize: '14px', pointerEvents: 'none',
            textAlign: 'center',
        });
        return new CSS3DRenderer_js_1.CSS3DObject(div);
    }
    update(spaceGraphInstance) {
        if (this.mesh)
            this.mesh.position.copy(this.position);
        if (this.labelObject) {
            const offset = this.getBoundingSphereRadius() * 1.1 + 10;
            this.labelObject.position.copy(this.position).y += offset;
            if (spaceGraphInstance === null || spaceGraphInstance === void 0 ? void 0 : spaceGraphInstance._camera) {
                this.labelObject.quaternion.copy(spaceGraphInstance._camera.quaternion);
            }
        }
    }
    dispose() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k;
        super.dispose();
        (_b = (_a = this.mesh) === null || _a === void 0 ? void 0 : _a.geometry) === null || _b === void 0 ? void 0 : _b.dispose();
        (_d = (_c = this.mesh) === null || _c === void 0 ? void 0 : _c.material) === null || _d === void 0 ? void 0 : _d.dispose();
        (_f = (_e = this.mesh) === null || _e === void 0 ? void 0 : _e.parent) === null || _f === void 0 ? void 0 : _f.remove(this.mesh);
        this.mesh = null;
        (_h = (_g = this.labelObject) === null || _g === void 0 ? void 0 : _g.element) === null || _h === void 0 ? void 0 : _h.remove();
        (_k = (_j = this.labelObject) === null || _j === void 0 ? void 0 : _j.parent) === null || _k === void 0 ? void 0 : _k.remove(this.labelObject);
        this.labelObject = null;
    }
    getBoundingSphereRadius() {
        switch (this.shape) {
            case 'box': return Math.sqrt(3 * Math.pow((this.size / 2), 2));
            case 'sphere':
            default: return this.size / 2;
        }
    }
    setSelectedStyle(selected) {
        var _a, _b, _c, _d;
        if ((_a = this.mesh) === null || _a === void 0 ? void 0 : _a.material) {
            (_b = this.mesh.material.emissive) === null || _b === void 0 ? void 0 : _b.setHex(selected ? 0x888800 : 0x000000);
        }
        (_d = (_c = this.labelObject) === null || _c === void 0 ? void 0 : _c.element) === null || _d === void 0 ? void 0 : _d.classList.toggle('selected', selected);
    }
}
exports.ShapeNode = ShapeNode;
class Edge {
    constructor(id, sourceNode, targetNode, data = {}) {
        this.spaceGraph = null;
        this.threeObject = null;
        this.data = {
            color: 0x00d0ff,
            thickness: 1.5,
            style: 'solid',
            constraintType: 'elastic',
            constraintParams: { stiffness: 0.001, idealLength: 200 }
        };
        if (!sourceNode || !targetNode)
            throw new Error("Edge requires valid source and target nodes.");
        this.id = id;
        this.source = sourceNode;
        this.target = targetNode;
        const defaultConstraintParams = Object.assign({}, (this.data.constraintType === 'elastic' ? { stiffness: 0.001, idealLength: 200 } :
            this.data.constraintType === 'rigid' ? { distance: sourceNode.position.distanceTo(targetNode.position), stiffness: 0.1 } :
                this.data.constraintType === 'weld' ? { distance: sourceNode.getBoundingSphereRadius() + targetNode.getBoundingSphereRadius(), stiffness: 0.5 } :
                    {}));
        this.data = Object.assign(Object.assign(Object.assign({}, this.data), data), { constraintParams: Object.assign(Object.assign({}, defaultConstraintParams), (data.constraintParams || {})) });
        this.threeObject = this._createThreeObject();
        this.update();
    }
    _createThreeObject() {
        const material = new THREE.LineBasicMaterial({
            color: this.data.color,
            linewidth: this.data.thickness,
            transparent: true,
            opacity: 0.6,
            depthTest: false,
        });
        const points = [this.source.position.clone(), this.target.position.clone()];
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        const line = new THREE.Line(geometry, material);
        line.renderOrder = -1;
        line.userData.edgeId = this.id;
        return line;
    }
    update() {
        if (!this.threeObject || !this.source || !this.target)
            return;
        const positions = this.threeObject.geometry.attributes.position;
        positions.setXYZ(0, this.source.position.x, this.source.position.y, this.source.position.z);
        positions.setXYZ(1, this.target.position.x, this.target.position.y, this.target.position.z);
        positions.needsUpdate = true;
        this.threeObject.geometry.computeBoundingSphere();
    }
    setHighlight(highlight) {
        var _a;
        if (!((_a = this.threeObject) === null || _a === void 0 ? void 0 : _a.material))
            return;
        this.threeObject.material.opacity = highlight ? 1.0 : 0.6;
        this.threeObject.material.color.set(highlight ? 0x00ffff : this.data.color);
        this.threeObject.material.needsUpdate = true;
    }
    dispose() {
        var _a, _b, _c;
        if (this.threeObject) {
            (_a = this.threeObject.geometry) === null || _a === void 0 ? void 0 : _a.dispose();
            (_b = this.threeObject.material) === null || _b === void 0 ? void 0 : _b.dispose();
            (_c = this.threeObject.parent) === null || _c === void 0 ? void 0 : _c.remove(this.threeObject);
            this.threeObject = null;
        }
        this.source = null;
        this.target = null;
        this.spaceGraph = null;
    }
}
exports.Edge = Edge;
class UIManager {
    constructor(spaceGraph, uiElements = {}) {
        this.spaceGraph = null;
        this.container = null;
        this.contextMenuElement = null;
        this.confirmDialogElement = null;
        this.edgeMenuObject = null;
        this.draggedNode = null;
        this.resizedNode = null;
        this.hoveredEdge = null;
        this.resizeStartPos = { x: 0, y: 0 };
        this.resizeStartSize = { width: 0, height: 0 };
        this.dragOffset = new THREE.Vector3();
        this.pointerState = {
            down: false, primary: false, secondary: false, middle: false,
            potentialClick: true, lastPos: { x: 0, y: 0 }, startPos: { x: 0, y: 0 }
        };
        this.confirmCallback = null;
        this.statusIndicatorElement = null;
        this._hideContextMenu = () => { if (this.contextMenuElement)
            this.contextMenuElement.style.display = 'none'; };
        this._hideConfirm = () => { this.confirmDialogElement.style.display = 'none'; this.confirmCallback = null; };
        this._onConfirmYes = () => { var _a; (_a = this.confirmCallback) === null || _a === void 0 ? void 0 : _a.call(this); this._hideConfirm(); };
        this._onConfirmNo = () => { this._hideConfirm(); };
        this._onWheel = (e) => {
            var _a;
            const targetInfo = this._getTargetInfo(e);
            if (e.target.closest('.node-controls, .edge-menu-frame') || targetInfo.contentEditable)
                return;
            if (e.ctrlKey || e.metaKey) {
                if (targetInfo.node instanceof HtmlNodeElement) {
                    e.preventDefault();
                    e.stopPropagation();
                    targetInfo.node.adjustContentScale(e.deltaY < 0 ? 1.1 : (1 / 1.1));
                }
            }
            else {
                e.preventDefault();
                (_a = this.spaceGraph.cameraController) === null || _a === void 0 ? void 0 : _a.zoom(e);
            }
        };
        if (!spaceGraph) {
            throw new Error("UIManager requires a SpaceGraph instance.");
        }
        this.spaceGraph = spaceGraph;
        this.container = spaceGraph.container;
        this.contextMenuElement = uiElements.contextMenuEl || S.$('#context-menu');
        if (!this.contextMenuElement || !document.body.contains(this.contextMenuElement)) {
            this.contextMenuElement = document.createElement('div');
            this.contextMenuElement.id = 'context-menu';
            this.contextMenuElement.className = 'context-menu';
            document.body.appendChild(this.contextMenuElement);
        }
        this.confirmDialogElement = uiElements.confirmDialogEl || S.$('#confirm-dialog');
        if (!this.confirmDialogElement || !document.body.contains(this.confirmDialogElement)) {
            this.confirmDialogElement = document.createElement('div');
            this.confirmDialogElement.id = 'confirm-dialog';
            this.confirmDialogElement.className = 'dialog';
            this.confirmDialogElement.innerHTML = '<p id="confirm-message">Are you sure?</p><button id="confirm-yes">Yes</button><button id="confirm-no">No</button>';
            document.body.appendChild(this.confirmDialogElement);
        }
        this.statusIndicatorElement = uiElements.statusIndicatorEl || S.$('#status-indicator');
        if (!this.statusIndicatorElement || !document.body.contains(this.statusIndicatorElement)) {
            this.statusIndicatorElement = document.createElement('div');
            this.statusIndicatorElement.id = 'status-indicator';
            // CSS typically handles initial visibility/fade-in, so no 'hidden' class added by default.
            document.body.appendChild(this.statusIndicatorElement);
        }
        this._bindEvents();
    }
    _bindEvents() {
        var _a, _b;
        const opts = { passive: false };
        this.container.addEventListener('pointerdown', this._onPointerDown.bind(this), false);
        window.addEventListener('pointermove', this._onPointerMove.bind(this), false);
        window.addEventListener('pointerup', this._onPointerUp.bind(this), false);
        this.container.addEventListener('contextmenu', this._onContextMenu.bind(this), opts);
        document.addEventListener('click', this._onDocumentClick.bind(this), true);
        this.contextMenuElement.addEventListener('click', this._onContextMenuClick.bind(this), false);
        (_a = (0, exports.$)('#confirm-yes', this.confirmDialogElement)) === null || _a === void 0 ? void 0 : _a.addEventListener('click', this._onConfirmYes.bind(this), false);
        (_b = (0, exports.$)('#confirm-no', this.confirmDialogElement)) === null || _b === void 0 ? void 0 : _b.addEventListener('click', this._onConfirmNo.bind(this), false);
        window.addEventListener('keydown', this._onKeyDown.bind(this), false);
        this.container.addEventListener('wheel', this._onWheel.bind(this), opts);
    }
    _updatePointerState(e, isDown) {
        this.pointerState.down = isDown;
        this.pointerState.primary = isDown && e.button === 0;
        this.pointerState.secondary = isDown && e.button === 2;
        this.pointerState.middle = isDown && e.button === 1;
        if (isDown) {
            this.pointerState.potentialClick = true;
            this.pointerState.startPos = { x: e.clientX, y: e.clientY };
        }
        this.pointerState.lastPos = { x: e.clientX, y: e.clientY };
    }
    _getTargetInfo(event) {
        const element = document.elementFromPoint(event.clientX, event.clientY);
        const nodeHtmlElement = element === null || element === void 0 ? void 0 : element.closest('.node-html');
        const resizeHandle = element === null || element === void 0 ? void 0 : element.closest('.resize-handle');
        const nodeControlsButton = element === null || element === void 0 ? void 0 : element.closest('.node-controls button');
        const contentEditable = element === null || element === void 0 ? void 0 : element.closest('[contenteditable="true"]');
        const interactiveInNode = element === null || element === void 0 ? void 0 : element.closest('.node-content button, .node-content input, .node-content a');
        let node = null;
        if (nodeHtmlElement) {
            node = this.spaceGraph.getNodeById(nodeHtmlElement.dataset.nodeId);
        }
        let intersectedEdge = null;
        let intersectedShapeNode = null;
        const isDirectHtmlInteraction = nodeHtmlElement && (resizeHandle || nodeControlsButton || contentEditable || interactiveInNode);
        if (!isDirectHtmlInteraction) {
            const intersected = this.spaceGraph.intersectedObject(event.clientX, event.clientY);
            if (intersected) {
                if (intersected instanceof Edge) {
                    intersectedEdge = intersected;
                }
                else if (intersected instanceof ShapeNode) {
                    intersectedShapeNode = intersected;
                    if (!node)
                        node = intersectedShapeNode;
                }
            }
        }
        return {
            element, nodeHtmlElement, resizeHandle, nodeControlsButton, contentEditable, interactiveInNode,
            node: node || intersectedShapeNode,
            intersectedEdge
        };
    }
    _onPointerDown(e) {
        var _a;
        this._updatePointerState(e, true);
        const targetInfo = this._getTargetInfo(e);
        if (targetInfo.nodeControlsButton && targetInfo.node instanceof HtmlNodeElement) {
            e.preventDefault();
            e.stopPropagation();
            this._handleNodeControlButtonClick(targetInfo.nodeControlsButton, targetInfo.node);
            this._hideContextMenu();
            return;
        }
        if (targetInfo.resizeHandle && targetInfo.node instanceof HtmlNodeElement) {
            e.preventDefault();
            e.stopPropagation();
            this.resizedNode = targetInfo.node;
            this.resizedNode.startResize();
            this.resizeStartPos = { x: e.clientX, y: e.clientY };
            this.resizeStartSize = Object.assign({}, this.resizedNode.size);
            this.container.style.cursor = 'nwse-resize';
            this._hideContextMenu();
            return;
        }
        if (targetInfo.node) {
            if (targetInfo.interactiveInNode || targetInfo.contentEditable) {
                e.stopPropagation();
                if (this.spaceGraph.selectedNode !== targetInfo.node)
                    this.spaceGraph.setSelectedNode(targetInfo.node);
                this._hideContextMenu();
            }
            else {
                e.preventDefault();
                this.draggedNode = targetInfo.node;
                this.draggedNode.startDrag();
                const worldPos = this.spaceGraph.screenToWorld(e.clientX, e.clientY, this.draggedNode.position.z);
                this.dragOffset = worldPos ? worldPos.sub(this.draggedNode.position) : new THREE.Vector3();
                this.container.style.cursor = 'grabbing';
                if (this.spaceGraph.selectedNode !== targetInfo.node)
                    this.spaceGraph.setSelectedNode(targetInfo.node);
                this._hideContextMenu();
                return;
            }
        }
        else if (targetInfo.intersectedEdge) {
            e.preventDefault();
            this.spaceGraph.setSelectedEdge(targetInfo.intersectedEdge);
            this._hideContextMenu();
            return;
        }
        else {
            this._hideContextMenu();
            if (this.spaceGraph.selectedNode || this.spaceGraph.selectedEdge) {
            }
            else if (this.pointerState.primary) {
                (_a = this.spaceGraph.cameraController) === null || _a === void 0 ? void 0 : _a.startPan(e);
            }
        }
    }
    _handleNodeControlButtonClick(button, node) {
        if (!(node instanceof HtmlNodeElement))
            return;
        const actionMap = {
            'node-delete': () => this._showConfirm(`Delete node "${node.id.substring(0, 10)}..."?`, () => this.spaceGraph.removeNode(node.id)),
            'node-content-zoom-in': () => node.adjustContentScale(1.15),
            'node-content-zoom-out': () => node.adjustContentScale(1 / 1.15),
            'node-grow': () => node.adjustNodeSize(1.2),
            'node-shrink': () => node.adjustNodeSize(0.8)
        };
        for (const cls of button.classList) {
            if (actionMap[cls]) {
                actionMap[cls]();
                break;
            }
        }
    }
    _onPointerMove(e) {
        var _a;
        const dx = e.clientX - this.pointerState.lastPos.x;
        const dy = e.clientY - this.pointerState.lastPos.y;
        if (dx !== 0 || dy !== 0)
            this.pointerState.potentialClick = false;
        this.pointerState.lastPos = { x: e.clientX, y: e.clientY };
        if (this.resizedNode) {
            e.preventDefault();
            const newWidth = this.resizeStartSize.width + (e.clientX - this.resizeStartPos.x);
            const newHeight = this.resizeStartSize.height + (e.clientY - this.resizeStartPos.y);
            this.resizedNode.resize(newWidth, newHeight);
            return;
        }
        if (this.draggedNode) {
            e.preventDefault();
            const worldPos = this.spaceGraph.screenToWorld(e.clientX, e.clientY, this.draggedNode.position.z);
            if (worldPos)
                this.draggedNode.drag(worldPos.sub(this.dragOffset));
            return;
        }
        if (this.spaceGraph.isLinking) {
            e.preventDefault();
            this._updateTempLinkLine(e.clientX, e.clientY);
            const { node } = this._getTargetInfo(e);
            (0, exports.$$)('.node-html.linking-target', this.container).forEach(el => el.classList.remove('linking-target'));
            if (node && node !== this.spaceGraph.linkSourceNode && node.htmlElement) {
                node.htmlElement.classList.add('linking-target');
            }
            return;
        }
        if (this.pointerState.primary && ((_a = this.spaceGraph.cameraController) === null || _a === void 0 ? void 0 : _a.isPanning)) {
            this.spaceGraph.cameraController.pan(e);
        }
        if (!this.pointerState.down && !this.resizedNode && !this.draggedNode && !this.spaceGraph.isLinking) {
            const { intersectedEdge } = this._getTargetInfo(e);
            if (this.hoveredEdge !== intersectedEdge) {
                if (this.hoveredEdge && this.hoveredEdge !== this.spaceGraph.selectedEdge) {
                    this.hoveredEdge.setHighlight(false);
                }
                this.hoveredEdge = intersectedEdge;
                if (this.hoveredEdge && this.hoveredEdge !== this.spaceGraph.selectedEdge) {
                    this.hoveredEdge.setHighlight(true);
                }
            }
        }
    }
    _onPointerUp(e) {
        var _a, _b;
        this.container.style.cursor = this.spaceGraph.isLinking ? 'crosshair' : 'grab';
        if (this.resizedNode) {
            this.resizedNode.endResize();
            this.resizedNode = null;
        }
        else if (this.draggedNode) {
            this.draggedNode.endDrag();
            this.draggedNode = null;
        }
        else if (this.spaceGraph.isLinking && e.button === 0) {
            this._completeLinking(e);
        }
        else if (e.button === 1 && this.pointerState.potentialClick) {
            const { node } = this._getTargetInfo(e);
            if (node) {
                this.spaceGraph.autoZoom(node);
                e.preventDefault();
            }
        }
        else if (e.button === 0 && this.pointerState.potentialClick) {
            const targetInfo = this._getTargetInfo(e);
            if (!targetInfo.node && !targetInfo.intersectedEdge && !((_a = this.spaceGraph.cameraController) === null || _a === void 0 ? void 0 : _a.isPanning)) {
                this.spaceGraph.setSelectedNode(null);
                this.spaceGraph.setSelectedEdge(null);
            }
        }
        (_b = this.spaceGraph.cameraController) === null || _b === void 0 ? void 0 : _b.endPan();
        this._updatePointerState(e, false);
        (0, exports.$$)('.node-html.linking-target', this.container).forEach(el => el.classList.remove('linking-target'));
    }
    _onContextMenu(e) {
        e.preventDefault();
        this._hideContextMenu();
        const targetInfo = this._getTargetInfo(e);
        let items = [];
        if (targetInfo.node) {
            if (this.spaceGraph.selectedNode !== targetInfo.node)
                this.spaceGraph.setSelectedNode(targetInfo.node);
            items = this._getContextMenuItemsNode(targetInfo.node);
        }
        else if (targetInfo.intersectedEdge) {
            if (this.spaceGraph.selectedEdge !== targetInfo.intersectedEdge)
                this.spaceGraph.setSelectedEdge(targetInfo.intersectedEdge);
            items = this._getContextMenuItemsEdge(targetInfo.intersectedEdge);
        }
        else {
            this.spaceGraph.setSelectedNode(null);
            this.spaceGraph.setSelectedEdge(null);
            const worldPos = this.spaceGraph.screenToWorld(e.clientX, e.clientY, 0);
            items = this._getContextMenuItemsBackground(worldPos);
        }
        if (items.length > 0)
            this._showContextMenu(e.clientX, e.clientY, items);
    }
    _onDocumentClick(e) {
        var _a, _b;
        const clickedContextMenu = this.contextMenuElement.contains(e.target);
        const clickedEdgeMenu = (_b = (_a = this.edgeMenuObject) === null || _a === void 0 ? void 0 : _a.element) === null || _b === void 0 ? void 0 : _b.contains(e.target);
        const clickedConfirmDialog = this.confirmDialogElement.contains(e.target);
        if (!clickedContextMenu)
            this._hideContextMenu();
        if (!clickedEdgeMenu && this.edgeMenuObject) {
            const targetInfo = this._getTargetInfo(e);
            if (this.spaceGraph.selectedEdge !== targetInfo.intersectedEdge) {
                this.spaceGraph.setSelectedEdge(null);
            }
        }
    }
    _getContextMenuItemsNode(node) {
        const items = [];
        if (node instanceof HtmlNodeElement && node.data.editable) {
            items.push({ label: "Edit Content 📝", action: "edit-node", nodeId: node.id });
        }
        else if (node instanceof ShapeNode && node.labelObject) {
        }
        items.push({ label: "Start Link ✨", action: "start-link", nodeId: node.id });
        items.push({ label: "Auto Zoom / Back 🖱️", action: "autozoom-node", nodeId: node.id });
        items.push({ type: 'separator' });
        items.push({ label: "Delete Node 🗑️", action: "delete-node", nodeId: node.id, class: 'delete-action' });
        return items;
    }
    _getContextMenuItemsEdge(edge) {
        return [
            { label: "Edit Edge Style...", action: "edit-edge", edgeId: edge.id },
            { label: "Reverse Edge Direction", action: "reverse-edge", edgeId: edge.id },
            { type: 'separator' },
            { label: "Delete Edge 🗑️", action: "delete-edge", edgeId: edge.id, class: 'delete-action' },
        ];
    }
    _getContextMenuItemsBackground(worldPos) {
        const items = [];
        if (worldPos) {
            const posStr = JSON.stringify({ x: worldPos.x, y: worldPos.y, z: worldPos.z });
            items.push({ label: "Create Note Here 📝", action: "create-note", position: posStr });
            items.push({ label: "Create Box Here 📦", action: "create-box", position: posStr });
            items.push({ label: "Create Sphere Here 🌐", action: "create-sphere", position: posStr });
        }
        items.push({ type: 'separator' });
        items.push({ label: "Center View 🧭", action: "center-view" });
        items.push({ label: "Reset Zoom & Pan", action: "reset-view" });
        items.push({
            label: this.spaceGraph.background.alpha === 0 ? "Set Dark Background" : "Set Transparent BG",
            action: "toggle-background"
        });
        return items;
    }
    _onContextMenuClick(event) {
        var _a, _b;
        const li = event.target.closest('li');
        if (!li || !li.dataset.action)
            return;
        const data = li.dataset;
        const action = data.action;
        this._hideContextMenu();
        const actions = {
            'edit-node': () => {
                var _a, _b;
                const node = this.spaceGraph.getNodeById(data.nodeId);
                if (node instanceof HtmlNodeElement && node.data.editable) {
                    (_b = (_a = node.htmlElement) === null || _a === void 0 ? void 0 : _a.querySelector('.node-content')) === null || _b === void 0 ? void 0 : _b.focus();
                }
            },
            'delete-node': () => this._showConfirm(`Delete node "${data.nodeId.substring(0, 10)}..."?`, () => this.spaceGraph.removeNode(data.nodeId)),
            'delete-edge': () => this._showConfirm(`Delete edge "${data.edgeId.substring(0, 10)}..."?`, () => this.spaceGraph.removeEdge(data.edgeId)),
            'autozoom-node': () => { const n = this.spaceGraph.getNodeById(data.nodeId); if (n)
                this.spaceGraph.autoZoom(n); },
            'create-note': () => this._createNodeFromMenu(data.position, NoteNode, { content: 'New Note ✨' }),
            'create-box': () => this._createNodeFromMenu(data.position, ShapeNode, { label: 'Box', shape: 'box', color: Math.random() * 0xffffff }),
            'create-sphere': () => this._createNodeFromMenu(data.position, ShapeNode, { label: 'Sphere', shape: 'sphere', color: Math.random() * 0xffffff }),
            'center-view': () => this.spaceGraph.centerView(),
            'reset-view': () => { var _a; return (_a = this.spaceGraph.cameraController) === null || _a === void 0 ? void 0 : _a.resetView(); },
            'start-link': () => { const n = this.spaceGraph.getNodeById(data.nodeId); if (n)
                this._startLinking(n); },
            'reverse-edge': () => {
                var _a;
                const edge = this.spaceGraph.getEdgeById(data.edgeId);
                if (edge) {
                    [edge.source, edge.target] = [edge.target, edge.source];
                    edge.update();
                    (_a = this.spaceGraph.layoutEngine) === null || _a === void 0 ? void 0 : _a.kick();
                }
            },
            'edit-edge': () => { const e = this.spaceGraph.getEdgeById(data.edgeId); if (e)
                this.spaceGraph.setSelectedEdge(e); },
            'toggle-background': () => this.spaceGraph.setBackground(this.spaceGraph.background.alpha === 0 ? 0x101018 : 0x000000, this.spaceGraph.background.alpha === 0 ? 1.0 : 0.0),
        };
        (_b = (_a = actions[action]) === null || _a === void 0 ? void 0 : _a.call(actions)) !== null && _b !== void 0 ? _b : console.warn("Unknown context menu action:", action);
    }
    _createNodeFromMenu(positionData, NodeTypeClass, nodeDataParams) {
        var _a;
        if (!positionData) {
            console.error("Position data missing for node creation");
            return;
        }
        try {
            const pos = JSON.parse(positionData);
            const newNode = this.spaceGraph.addNode(new NodeTypeClass(null, pos, nodeDataParams));
            (_a = this.spaceGraph.layoutEngine) === null || _a === void 0 ? void 0 : _a.kick();
            setTimeout(() => {
                var _a, _b;
                this.spaceGraph.focusOnNode(newNode, 0.6, true);
                this.spaceGraph.setSelectedNode(newNode);
                if (newNode instanceof NoteNode) {
                    (_b = (_a = newNode.htmlElement) === null || _a === void 0 ? void 0 : _a.querySelector('.node-content')) === null || _b === void 0 ? void 0 : _b.focus();
                }
            }, 100);
        }
        catch (err) {
            console.error("Failed to create node from menu:", err);
        }
    }
    _showContextMenu(x, y, items) {
        this.contextMenuElement.innerHTML = '';
        const ul = document.createElement('ul');
        items.forEach(item => {
            if (item.type === 'separator') {
                const li = document.createElement('li');
                li.className = 'separator';
                ul.appendChild(li);
                return;
            }
            if (item.disabled)
                return;
            const li = document.createElement('li');
            li.textContent = item.label;
            if (item.class)
                li.classList.add(item.class);
            Object.entries(item).forEach(([key, value]) => {
                if (value !== undefined && value !== null && key !== 'type' && key !== 'label' && key !== 'class') {
                    li.dataset[key] = typeof value === 'object' ? JSON.stringify(value) : value;
                }
            });
            ul.appendChild(li);
        });
        this.contextMenuElement.appendChild(ul);
        const menuWidth = this.contextMenuElement.offsetWidth;
        const menuHeight = this.contextMenuElement.offsetHeight;
        let finalX = x + 5;
        let finalY = y + 5;
        if (finalX + menuWidth > window.innerWidth)
            finalX = x - menuWidth - 5;
        if (finalY + menuHeight > window.innerHeight)
            finalY = y - menuHeight - 5;
        finalX = Math.max(5, finalX);
        finalY = Math.max(5, finalY);
        this.contextMenuElement.style.left = `${finalX}px`;
        this.contextMenuElement.style.top = `${finalY}px`;
        this.contextMenuElement.style.display = 'block';
    }
    _showConfirm(message, onConfirm) {
        (0, exports.$)('#confirm-message', this.confirmDialogElement).textContent = message;
        this.confirmCallback = onConfirm;
        this.confirmDialogElement.style.display = 'block';
    }
    _startLinking(sourceNode) {
        if (!sourceNode)
            return;
        this.spaceGraph.isLinking = true;
        this.spaceGraph.linkSourceNode = sourceNode;
        this.container.style.cursor = 'crosshair';
        this._createTempLinkLine(sourceNode);
    }
    _createTempLinkLine(sourceNode) {
        this._removeTempLinkLine();
        const material = new THREE.LineDashedMaterial({
            color: 0xffaa00, linewidth: 2, dashSize: 8, gapSize: 4,
            transparent: true, opacity: 0.9, depthTest: false
        });
        const points = [sourceNode.position.clone(), sourceNode.position.clone()];
        const geometry = new THREE.BufferGeometry().setFromPoints(points);
        this.spaceGraph.tempLinkLine = new THREE.Line(geometry, material);
        this.spaceGraph.tempLinkLine.computeLineDistances();
        this.spaceGraph.tempLinkLine.renderOrder = 1;
        this.spaceGraph.scene.add(this.spaceGraph.tempLinkLine);
    }
    _updateTempLinkLine(screenX, screenY) {
        if (!this.spaceGraph.tempLinkLine || !this.spaceGraph.linkSourceNode)
            return;
        const targetPos = this.spaceGraph.screenToWorld(screenX, screenY, this.spaceGraph.linkSourceNode.position.z);
        if (targetPos) {
            const positions = this.spaceGraph.tempLinkLine.geometry.attributes.position;
            positions.setXYZ(1, targetPos.x, targetPos.y, targetPos.z);
            positions.needsUpdate = true;
            this.spaceGraph.tempLinkLine.geometry.computeBoundingSphere();
            this.spaceGraph.tempLinkLine.computeLineDistances();
        }
    }
    _removeTempLinkLine() {
        var _a, _b;
        if (this.spaceGraph.tempLinkLine) {
            (_a = this.spaceGraph.tempLinkLine.geometry) === null || _a === void 0 ? void 0 : _a.dispose();
            (_b = this.spaceGraph.tempLinkLine.material) === null || _b === void 0 ? void 0 : _b.dispose();
            this.spaceGraph.scene.remove(this.spaceGraph.tempLinkLine);
            this.spaceGraph.tempLinkLine = null;
        }
    }
    _completeLinking(event) {
        this._removeTempLinkLine();
        const { node: targetNode } = this._getTargetInfo(event);
        if (targetNode && targetNode !== this.spaceGraph.linkSourceNode) {
            this.spaceGraph.addEdge(this.spaceGraph.linkSourceNode, targetNode);
        }
        this.cancelLinking();
    }
    cancelLinking() {
        this._removeTempLinkLine();
        this.spaceGraph.isLinking = false;
        this.spaceGraph.linkSourceNode = null;
        this.container.style.cursor = 'grab';
        (0, exports.$$)('.node-html.linking-target', this.container).forEach(el => el.classList.remove('linking-target'));
    }
    _onKeyDown(event) {
        var _a, _b, _c, _d;
        const activeEl = document.activeElement;
        const isEditing = activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA' || activeEl.isContentEditable);
        if (isEditing && event.key !== 'Escape')
            return;
        const selectedNode = this.spaceGraph.selectedNode;
        const selectedEdge = this.spaceGraph.selectedEdge;
        let handled = false;
        switch (event.key) {
            case 'Delete':
            case 'Backspace':
                if (selectedNode) {
                    this._showConfirm(`Delete node "${selectedNode.id.substring(0, 10)}..."?`, () => this.spaceGraph.removeNode(selectedNode.id));
                    handled = true;
                }
                else if (selectedEdge) {
                    this._showConfirm(`Delete edge "${selectedEdge.id.substring(0, 10)}..."?`, () => this.spaceGraph.removeEdge(selectedEdge.id));
                    handled = true;
                }
                break;
            case 'Escape':
                if (this.spaceGraph.isLinking) {
                    this.cancelLinking();
                    handled = true;
                }
                else if (this.contextMenuElement.style.display === 'block') {
                    this._hideContextMenu();
                    handled = true;
                }
                else if (this.confirmDialogElement.style.display === 'block') {
                    this._hideConfirm();
                    handled = true;
                }
                else if (this.edgeMenuObject) {
                    this.spaceGraph.setSelectedEdge(null);
                    handled = true;
                }
                else if (selectedNode || selectedEdge) {
                    this.spaceGraph.setSelectedNode(null);
                    this.spaceGraph.setSelectedEdge(null);
                    handled = true;
                }
                break;
            case 'Enter':
                if (selectedNode instanceof NoteNode) {
                    (_b = (_a = selectedNode.htmlElement) === null || _a === void 0 ? void 0 : _a.querySelector('.node-content')) === null || _b === void 0 ? void 0 : _b.focus();
                    handled = true;
                }
                break;
            case '+':
            case '=':
                if (selectedNode instanceof HtmlNodeElement) {
                    event.ctrlKey || event.metaKey ? selectedNode.adjustNodeSize(1.2) : selectedNode.adjustContentScale(1.15);
                    handled = true;
                }
                break;
            case '-':
            case '_':
                if (selectedNode instanceof HtmlNodeElement) {
                    event.ctrlKey || event.metaKey ? selectedNode.adjustNodeSize(0.8) : selectedNode.adjustContentScale(0.85);
                    handled = true;
                }
                break;
            case ' ':
                if (selectedNode) {
                    this.spaceGraph.focusOnNode(selectedNode, 0.5, true);
                    handled = true;
                }
                else if (selectedEdge) {
                    const midPoint = new THREE.Vector3().lerpVectors(selectedEdge.source.position, selectedEdge.target.position, 0.5);
                    const dist = selectedEdge.source.position.distanceTo(selectedEdge.target.position);
                    (_c = this.spaceGraph.cameraController) === null || _c === void 0 ? void 0 : _c.pushState();
                    (_d = this.spaceGraph.cameraController) === null || _d === void 0 ? void 0 : _d.moveTo(midPoint.x, midPoint.y, midPoint.z + dist * 0.6 + 100, 0.5, midPoint);
                    handled = true;
                }
                else {
                    this.spaceGraph.centerView();
                    handled = true;
                }
                break;
        }
        if (handled)
            event.preventDefault();
    }
    showEdgeMenu(edge) {
        if (!edge || this.edgeMenuObject)
            return;
        this.hideEdgeMenu();
        const menuElement = document.createElement('div');
        menuElement.className = 'edge-menu-frame';
        menuElement.dataset.edgeId = edge.id;
        menuElement.innerHTML = `
          <button title="Color (NYI)" data-action="color">🎨</button>
          <button title="Thickness (NYI)" data-action="thickness">➖</button>
          <button title="Style (NYI)" data-action="style">〰️</button>
          <button title="Constraint (NYI)" data-action="constraint">🔗</button>
          <button title="Delete Edge" class="delete" data-action="delete">×</button>
      `;
        menuElement.addEventListener('click', (e) => {
            const button = e.target.closest('button');
            if (!button)
                return;
            const action = button.dataset.action;
            e.stopPropagation();
            switch (action) {
                case 'delete':
                    this._showConfirm(`Delete edge "${edge.id.substring(0, 10)}..."?`, () => this.spaceGraph.removeEdge(edge.id));
                    break;
                case 'color':
                case 'thickness':
                case 'style':
                case 'constraint':
                    console.warn(`Edge menu action '${action}' not fully implemented.`);
                    break;
            }
        });
        menuElement.addEventListener('pointerdown', e => e.stopPropagation());
        menuElement.addEventListener('wheel', e => e.stopPropagation());
        this.edgeMenuObject = new CSS3DRenderer_js_1.CSS3DObject(menuElement);
        this.spaceGraph.cssScene.add(this.edgeMenuObject);
        this.updateEdgeMenuPosition();
    }
    hideEdgeMenu() {
        var _a, _b;
        if (this.edgeMenuObject) {
            (_a = this.edgeMenuObject.element) === null || _a === void 0 ? void 0 : _a.remove();
            (_b = this.edgeMenuObject.parent) === null || _b === void 0 ? void 0 : _b.remove(this.edgeMenuObject);
            this.edgeMenuObject = null;
        }
    }
    updateEdgeMenuPosition() {
        if (!this.edgeMenuObject || !this.spaceGraph.selectedEdge)
            return;
        const edge = this.spaceGraph.selectedEdge;
        const midPoint = new THREE.Vector3().lerpVectors(edge.source.position, edge.target.position, 0.5);
        this.edgeMenuObject.position.copy(midPoint);
        if (this.spaceGraph._camera) {
            this.edgeMenuObject.quaternion.copy(this.spaceGraph._camera.quaternion);
        }
    }
    dispose() {
        var _a, _b, _c, _d, _e, _f;
        this.container.removeEventListener('pointerdown', this._onPointerDown);
        window.removeEventListener('pointermove', this._onPointerMove);
        window.removeEventListener('pointerup', this._onPointerUp);
        this.container.removeEventListener('contextmenu', this._onContextMenu);
        document.removeEventListener('click', this._onDocumentClick, true);
        (_a = this.contextMenuElement) === null || _a === void 0 ? void 0 : _a.removeEventListener('click', this._onContextMenuClick);
        (_b = (0, exports.$)('#confirm-yes', this.confirmDialogElement)) === null || _b === void 0 ? void 0 : _b.removeEventListener('click', this._onConfirmYes);
        (_c = (0, exports.$)('#confirm-no', this.confirmDialogElement)) === null || _c === void 0 ? void 0 : _c.removeEventListener('click', this._onConfirmNo);
        window.removeEventListener('keydown', this._onKeyDown);
        this.container.removeEventListener('wheel', this._onWheel);
        this.hideEdgeMenu();
        (_d = this.contextMenuElement) === null || _d === void 0 ? void 0 : _d.remove();
        (_e = this.confirmDialogElement) === null || _e === void 0 ? void 0 : _e.remove();
        (_f = this.statusIndicatorElement) === null || _f === void 0 ? void 0 : _f.remove();
        this.spaceGraph = null;
        this.container = null;
        this.contextMenuElement = null;
        this.confirmDialogElement = null;
        this.statusIndicatorElement = null;
        this.draggedNode = null;
        this.resizedNode = null;
        this.hoveredEdge = null;
        this.confirmCallback = null;
        console.log("UIManager disposed.");
    }
}
exports.UIManager = UIManager;
class CameraController {
    constructor(threeCamera, domElement) {
        this.camera = null;
        this.domElement = null;
        this.isPanning = false;
        this.panStart = new THREE.Vector2();
        this.targetPosition = new THREE.Vector3();
        this.targetLookAt = new THREE.Vector3();
        this.currentLookAt = new THREE.Vector3();
        this.zoomSpeed = 0.0015;
        this.panSpeed = 0.8;
        this.minZoom = 20;
        this.maxZoom = 15000;
        this.dampingFactor = 0.12;
        this.animationFrameId = null;
        this.viewHistory = [];
        this.maxHistory = 20;
        this.currentTargetNodeId = null;
        this.initialState = null;
        this.getCurrentTargetNodeId = () => this.currentTargetNodeId;
        this.setCurrentTargetNodeId = (nodeId) => { this.currentTargetNodeId = nodeId; };
        this._updateLoop = () => {
            const deltaPos = this.targetPosition.distanceTo(this.camera.position);
            const deltaLookAt = this.targetLookAt.distanceTo(this.currentLookAt);
            if (deltaPos > 0.01 || deltaLookAt > 0.01 || this.isPanning) {
                this.camera.position.lerp(this.targetPosition, this.dampingFactor);
                this.currentLookAt.lerp(this.targetLookAt, this.dampingFactor);
                this.camera.lookAt(this.currentLookAt);
            }
            else if (!gsap_1.gsap.isTweening(this.targetPosition) && !gsap_1.gsap.isTweening(this.targetLookAt)) {
                if (deltaPos > 0 || deltaLookAt > 0) {
                    this.camera.position.copy(this.targetPosition);
                    this.currentLookAt.copy(this.targetLookAt);
                    this.camera.lookAt(this.currentLookAt);
                }
            }
            this.animationFrameId = requestAnimationFrame(this._updateLoop);
        };
        this.camera = threeCamera;
        this.domElement = domElement;
        this.targetPosition.copy(this.camera.position);
        this.targetLookAt.copy(new THREE.Vector3(0, 0, 0));
        this.currentLookAt.copy(this.targetLookAt);
        this._updateLoop();
    }
    setInitialState() {
        if (!this.initialState) {
            this.initialState = {
                position: this.targetPosition.clone(),
                lookAt: this.targetLookAt.clone()
            };
        }
    }
    startPan(event) {
        if (event.button !== 0 || this.isPanning)
            return;
        this.isPanning = true;
        this.panStart.set(event.clientX, event.clientY);
        this.domElement.classList.add('panning');
        gsap_1.gsap.killTweensOf(this.targetPosition);
        gsap_1.gsap.killTweensOf(this.targetLookAt);
        this.currentTargetNodeId = null;
    }
    pan(event) {
        if (!this.isPanning)
            return;
        const deltaX = event.clientX - this.panStart.x;
        const deltaY = event.clientY - this.panStart.y;
        const cameraDist = this.camera.position.distanceTo(this.currentLookAt);
        const vFOV = this.camera.fov * exports.DEG2RAD;
        const viewHeight = this.domElement.clientHeight || window.innerHeight;
        const height = 2 * Math.tan(vFOV / 2) * Math.max(1, cameraDist);
        const panXAmount = -(deltaX / viewHeight) * height * this.panSpeed;
        const panYAmount = (deltaY / viewHeight) * height * this.panSpeed;
        const right = new THREE.Vector3().setFromMatrixColumn(this.camera.matrixWorld, 0);
        const up = new THREE.Vector3().setFromMatrixColumn(this.camera.matrixWorld, 1);
        const panOffset = right.multiplyScalar(panXAmount).add(up.multiplyScalar(panYAmount));
        this.targetPosition.add(panOffset);
        this.targetLookAt.add(panOffset);
        this.panStart.set(event.clientX, event.clientY);
    }
    endPan() {
        if (this.isPanning) {
            this.isPanning = false;
            this.domElement.classList.remove('panning');
        }
    }
    zoom(event) {
        gsap_1.gsap.killTweensOf(this.targetPosition);
        gsap_1.gsap.killTweensOf(this.targetLookAt);
        this.currentTargetNodeId = null;
        const delta = -event.deltaY * this.zoomSpeed;
        const currentDist = this.targetPosition.distanceTo(this.targetLookAt);
        let newDist = currentDist * Math.pow(0.95, delta * 12);
        newDist = (0, exports.clamp)(newDist, this.minZoom, this.maxZoom);
        const zoomFactorAmount = (newDist - currentDist);
        const mouseWorldPos = this._getLookAtPlaneIntersection(event.clientX, event.clientY);
        const direction = new THREE.Vector3();
        if (mouseWorldPos) {
            direction.copy(mouseWorldPos).sub(this.targetPosition).normalize();
        }
        else {
            this.camera.getWorldDirection(direction);
        }
        this.targetPosition.addScaledVector(direction, zoomFactorAmount);
    }
    _getLookAtPlaneIntersection(screenX, screenY) {
        const vec = new THREE.Vector3((screenX / window.innerWidth) * 2 - 1, -(screenY / window.innerHeight) * 2 + 1, 0.5);
        const raycaster = new THREE.Raycaster();
        raycaster.setFromCamera(vec, this.camera);
        const camDir = new THREE.Vector3();
        this.camera.getWorldDirection(camDir);
        const plane = new THREE.Plane().setFromNormalAndCoplanarPoint(camDir.negate(), this.targetLookAt);
        const intersectPoint = new THREE.Vector3();
        return raycaster.ray.intersectPlane(plane, intersectPoint) ? intersectPoint : null;
    }
    moveTo(x, y, z, duration = 0.7, lookAtTarget = null) {
        this.setInitialState();
        const targetPosVec = new THREE.Vector3(x, y, z);
        const targetLookVec = lookAtTarget ? lookAtTarget.clone() : new THREE.Vector3(x, y, 0);
        gsap_1.gsap.killTweensOf(this.targetPosition);
        gsap_1.gsap.killTweensOf(this.targetLookAt);
        gsap_1.gsap.to(this.targetPosition, { x: targetPosVec.x, y: targetPosVec.y, z: targetPosVec.z, duration, ease: "power3.out", overwrite: true });
        gsap_1.gsap.to(this.targetLookAt, { x: targetLookVec.x, y: targetLookVec.y, z: targetLookVec.z, duration, ease: "power3.out", overwrite: true });
    }
    resetView(duration = 0.7) {
        if (this.initialState) {
            this.moveTo(this.initialState.position.x, this.initialState.position.y, this.initialState.position.z, duration, this.initialState.lookAt);
        }
        else {
            this.moveTo(0, 0, 700, duration, new THREE.Vector3(0, 0, 0));
        }
        this.viewHistory = [];
        this.currentTargetNodeId = null;
    }
    pushState() {
        if (this.viewHistory.length >= this.maxHistory)
            this.viewHistory.shift();
        this.viewHistory.push({
            position: this.targetPosition.clone(),
            lookAt: this.targetLookAt.clone(),
            targetNodeId: this.currentTargetNodeId
        });
    }
    popState(duration = 0.6) {
        if (this.viewHistory.length > 0) {
            const prevState = this.viewHistory.pop();
            this.moveTo(prevState.position.x, prevState.position.y, prevState.position.z, duration, prevState.lookAt);
            this.currentTargetNodeId = prevState.targetNodeId;
        }
        else {
            this.resetView(duration);
        }
    }
    dispose() {
        if (this.animationFrameId)
            cancelAnimationFrame(this.animationFrameId);
        gsap_1.gsap.killTweensOf(this.targetPosition);
        gsap_1.gsap.killTweensOf(this.targetLookAt);
        this.camera = null;
        this.domElement = null;
        this.viewHistory = [];
        console.log("CameraController disposed.");
    }
}
exports.CameraController = CameraController;
class ForceLayout {
    constructor(spaceGraphInstance, config = {}) {
        this.spaceGraph = null;
        this.nodes = [];
        this.edges = [];
        this.velocities = new Map();
        this.fixedNodes = new Set();
        this.isRunning = false;
        this.animationFrameId = null;
        this.energy = Infinity;
        this.lastKickTime = 0;
        this.autoStopTimeout = null;
        this.settings = {
            repulsion: 3000,
            attraction: 0.001,
            idealEdgeLength: 200,
            centerStrength: 0.0005,
            damping: 0.92,
            minEnergyThreshold: 0.1,
            gravityCenter: new THREE.Vector3(0, 0, 0),
            zSpreadFactor: 0.15,
            autoStopDelay: 4000,
            nodePadding: 1.2,
            defaultElasticStiffness: 0.001,
            defaultElasticIdealLength: 200,
            defaultRigidStiffness: 0.1,
            defaultWeldStiffness: 0.5,
        };
        this.spaceGraph = spaceGraphInstance;
        this.settings = Object.assign(Object.assign({}, this.settings), config);
        this.settings.defaultElasticStiffness = this.settings.attraction;
        this.settings.defaultElasticIdealLength = this.settings.idealEdgeLength;
    }
    addNode(node) {
        if (!this.velocities.has(node.id)) {
            this.nodes.push(node);
            this.velocities.set(node.id, new THREE.Vector3());
            this.kick();
        }
    }
    removeNode(node) {
        this.nodes = this.nodes.filter(n => n !== node);
        this.velocities.delete(node.id);
        this.fixedNodes.delete(node);
        if (this.nodes.length < 2 && this.isRunning)
            this.stop();
        else
            this.kick();
    }
    addEdge(edge) { if (!this.edges.includes(edge)) {
        this.edges.push(edge);
        this.kick();
    } }
    removeEdge(edge) { this.edges = this.edges.filter(e => e !== edge); this.kick(); }
    fixNode(node) { var _a; this.fixedNodes.add(node); (_a = this.velocities.get(node.id)) === null || _a === void 0 ? void 0 : _a.set(0, 0, 0); }
    releaseNode(node) { this.fixedNodes.delete(node); }
    runOnce(steps = 100) {
        console.log(`ForceLayout: Running ${steps} initial steps...`);
        let i = 0;
        for (; i < steps; i++) {
            if (this._calculateStep() < this.settings.minEnergyThreshold)
                break;
        }
        console.log(`ForceLayout: Initial steps completed after ${i} iterations.`);
        this.spaceGraph._updateNodesAndEdges();
    }
    start() {
        if (this.isRunning || this.nodes.length < 2)
            return;
        console.log("ForceLayout: Starting simulation.");
        this.isRunning = true;
        this.lastKickTime = Date.now();
        const loop = () => {
            if (!this.isRunning)
                return;
            this.energy = this._calculateStep();
            if (this.energy < this.settings.minEnergyThreshold && Date.now() - this.lastKickTime > this.settings.autoStopDelay) {
                this.stop();
            }
            else {
                this.animationFrameId = requestAnimationFrame(loop);
            }
        };
        this.animationFrameId = requestAnimationFrame(loop);
    }
    stop() {
        var _a;
        if (!this.isRunning)
            return;
        this.isRunning = false;
        if (this.animationFrameId)
            cancelAnimationFrame(this.animationFrameId);
        clearTimeout(this.autoStopTimeout);
        this.animationFrameId = null;
        this.autoStopTimeout = null;
        console.log("ForceLayout: Simulation stopped. Energy:", (_a = this.energy) === null || _a === void 0 ? void 0 : _a.toFixed(4));
    }
    kick(intensity = 1) {
        if (this.nodes.length === 0)
            return;
        this.lastKickTime = Date.now();
        this.energy = Infinity;
        this.nodes.forEach(node => {
            var _a;
            if (!this.fixedNodes.has(node)) {
                (_a = this.velocities.get(node.id)) === null || _a === void 0 ? void 0 : _a.add(new THREE.Vector3(Math.random() - 0.5, Math.random() - 0.5, (Math.random() - 0.5) * this.settings.zSpreadFactor)
                    .normalize().multiplyScalar(intensity * (1 + Math.random() * 2)));
            }
        });
        if (!this.isRunning)
            this.start();
        clearTimeout(this.autoStopTimeout);
        this.autoStopTimeout = setTimeout(() => {
            if (this.isRunning && this.energy < this.settings.minEnergyThreshold)
                this.stop();
        }, this.settings.autoStopDelay);
    }
    setSettings(newSettings) {
        this.settings = Object.assign(Object.assign({}, this.settings), newSettings);
        this.settings.defaultElasticStiffness = this.settings.attraction;
        this.settings.defaultElasticIdealLength = this.settings.idealEdgeLength;
        console.log("ForceLayout settings updated:", this.settings);
        this.kick();
    }
    _calculateStep() {
        var _a, _b;
        if (this.nodes.length < 2 && this.edges.length === 0)
            return 0;
        let totalSystemEnergy = 0;
        const forces = new Map(this.nodes.map(node => [node.id, new THREE.Vector3()]));
        const { repulsion, centerStrength, gravityCenter, zSpreadFactor, damping, nodePadding } = this.settings;
        const tempDelta = new THREE.Vector3();
        for (let i = 0; i < this.nodes.length; i++) {
            const nodeA = this.nodes[i];
            for (let j = i + 1; j < this.nodes.length; j++) {
                const nodeB = this.nodes[j];
                tempDelta.subVectors(nodeB.position, nodeA.position);
                let distSq = tempDelta.lengthSq();
                if (distSq < 1e-4) {
                    distSq = 1e-4;
                    tempDelta.randomDirection().multiplyScalar(0.01);
                }
                const dist = Math.sqrt(distSq);
                const radiusA = nodeA.getBoundingSphereRadius() * nodePadding;
                const radiusB = nodeB.getBoundingSphereRadius() * nodePadding;
                const combinedRadius = radiusA + radiusB;
                const overlap = combinedRadius - dist;
                let forceMag = -repulsion / distSq;
                if (overlap > 0) {
                    forceMag -= (repulsion * Math.pow(overlap, 2) * 0.01) / dist;
                }
                const forceVec = tempDelta.normalize().multiplyScalar(forceMag);
                forceVec.z *= zSpreadFactor;
                if (!this.fixedNodes.has(nodeA))
                    (_a = forces.get(nodeA.id)) === null || _a === void 0 ? void 0 : _a.add(forceVec);
                if (!this.fixedNodes.has(nodeB))
                    (_b = forces.get(nodeB.id)) === null || _b === void 0 ? void 0 : _b.sub(forceVec);
            }
        }
        this.edges.forEach(edge => {
            var _a, _b, _c, _d, _e, _f, _g, _h;
            const { source, target, data: edgeData } = edge;
            if (!source || !target || !this.velocities.has(source.id) || !this.velocities.has(target.id))
                return;
            tempDelta.subVectors(target.position, source.position);
            const distance = tempDelta.length() + 1e-6;
            let forceMag = 0;
            const params = edgeData.constraintParams || {};
            const type = edgeData.constraintType || 'elastic';
            switch (type) {
                case 'rigid':
                    const targetDist = (_a = params.distance) !== null && _a !== void 0 ? _a : source.position.distanceTo(target.position);
                    const rStiffness = (_b = params.stiffness) !== null && _b !== void 0 ? _b : this.settings.defaultRigidStiffness;
                    forceMag = rStiffness * (distance - targetDist);
                    break;
                case 'weld':
                    const weldDist = (_c = params.distance) !== null && _c !== void 0 ? _c : (source.getBoundingSphereRadius() + target.getBoundingSphereRadius());
                    const wStiffness = (_d = params.stiffness) !== null && _d !== void 0 ? _d : this.settings.defaultWeldStiffness;
                    forceMag = wStiffness * (distance - weldDist);
                    break;
                case 'elastic':
                default:
                    const idealLen = (_e = params.idealLength) !== null && _e !== void 0 ? _e : this.settings.defaultElasticIdealLength;
                    const eStiffness = (_f = params.stiffness) !== null && _f !== void 0 ? _f : this.settings.defaultElasticStiffness;
                    forceMag = eStiffness * (distance - idealLen);
                    break;
            }
            const forceVec = tempDelta.normalize().multiplyScalar(forceMag);
            forceVec.z *= zSpreadFactor;
            if (!this.fixedNodes.has(source))
                (_g = forces.get(source.id)) === null || _g === void 0 ? void 0 : _g.add(forceVec);
            if (!this.fixedNodes.has(target))
                (_h = forces.get(target.id)) === null || _h === void 0 ? void 0 : _h.sub(forceVec);
        });
        if (centerStrength > 0) {
            this.nodes.forEach(node => {
                var _a;
                if (this.fixedNodes.has(node))
                    return;
                const forceVec = tempDelta.subVectors(gravityCenter, node.position).multiplyScalar(centerStrength);
                forceVec.z *= zSpreadFactor * 0.5;
                (_a = forces.get(node.id)) === null || _a === void 0 ? void 0 : _a.add(forceVec);
            });
        }
        this.nodes.forEach(node => {
            if (this.fixedNodes.has(node))
                return;
            const force = forces.get(node.id);
            const velocity = this.velocities.get(node.id);
            if (!force || !velocity)
                return;
            const mass = node.mass || 1.0;
            const acceleration = tempDelta.copy(force).divideScalar(mass);
            velocity.add(acceleration).multiplyScalar(damping);
            const speed = velocity.length();
            if (speed > 50)
                velocity.multiplyScalar(50 / speed);
            node.position.add(velocity);
            totalSystemEnergy += 0.5 * mass * velocity.lengthSq();
        });
        return totalSystemEnergy;
    }
    dispose() {
        this.stop();
        this.nodes = [];
        this.edges = [];
        this.velocities.clear();
        this.fixedNodes.clear();
        this.spaceGraph = null;
        console.log("ForceLayout disposed.");
    }
}
exports.ForceLayout = ForceLayout;
