// js_rewritten/rendering/webglRenderer.js

/**
 * WebGL Renderer for the RTS Game
 * Rewritten from first principles to utilize WebGL for enhanced rendering performance.
 * Handles rendering of game entities, terrain, and UI elements in a 3D context with systematic precision,
 * integrating 3D models inspired by large-scale mechanized warfare themes.
 */

import { mat4, vec3 } from 'gl-matrix'; // Import gl-matrix
import { TERRAIN_TYPES, TILE_SIZE, GRID_SIZE, WORLD_SIZE } from '../config/gameConstants.js';
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { UNIT_TYPES } from '../config/unitTypes.js';
import { UNIT_MODELS, BUILDING_MODELS, TERRAIN_MODELS, TERRAIN_TEXTURES, EFFECT_TEXTURES, SEA_LEVEL } from '../config/modelDefaults.js';

// WebGL Renderer Class
export class WebGLRenderer {
    constructor(canvas) {
        this.canvas = canvas;
        this.gl = this.initWebGL();
        this.attributes = {}; // Initialize attributes before initShaders
        this.uniforms = {}; // Initialize uniforms before initShaders
        this.shaderProgram = this.initShaders();
        this.buffers = this.initBuffers();
        this.textures = {};
        this.models = {};
        this.camera = { x: 2500, y: 2500, zoom: 1.0, canvasWidth: canvas.width, canvasHeight: canvas.height, angle: 30, rotation: 0 };
        this.initializeModels();
    }

    initWebGL() {
        const gl = this.canvas.getContext('webgl') || this.canvas.getContext('experimental-webgl');
        if (!gl) {
            console.error('WebGL not supported or failed to initialize.');
            return null;
        }
        gl.clearColor(0.2, 0.2, 0.2, 1.0);
        gl.enable(gl.DEPTH_TEST);
        gl.enable(gl.CULL_FACE); // Enable back-face culling
        gl.enable(gl.BLEND);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        return gl;
    }

    initShaders() {
        const vertexShaderSource = `
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute vec3 aNormal;
            attribute vec4 aColor; // For vertex colors on terrain
            uniform mat4 uModelViewMatrix;
            uniform mat4 uProjectionMatrix;
            uniform mat4 uNormalMatrix; // New uniform for normal transformation

            varying vec2 vTexCoord;
            varying vec3 vNormal; // Transformed normal
            varying vec4 vColor; // Passed-through vertex color
            varying vec3 vPosition; // World position for lighting

            void main() {
                vec4 worldPos = vec4(aPosition, 1.0);
                gl_Position = uProjectionMatrix * uModelViewMatrix * worldPos;
                vPosition = (uModelViewMatrix * worldPos).xyz; // Position in eye coordinates

                vTexCoord = aTexCoord;
                vNormal = normalize(vec3(uNormalMatrix * vec4(aNormal, 0.0))); // Transform normal
                vColor = aColor;
            }
        `;
        const fragmentShaderSource = `
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec3 vNormal;
            varying vec4 vColor;
            varying vec3 vPosition; // World position for lighting

            uniform sampler2D uTexture;
            uniform bool uUseTexture;
            uniform bool uUseVertexColor;
            uniform vec3 uLightDirection; // Directional light
            uniform vec4 uObjectColor; // Base color for objects

            void main() {
                // Normalize the interpolated normal
                vec3 normal = normalize(vNormal);

                // Directional light calculation
                vec3 lightDirection = normalize(uLightDirection);
                float diffuse = max(dot(normal, lightDirection), 0.0);

                vec4 finalColor;
                if (uUseTexture) {
                    finalColor = texture2D(uTexture, vTexCoord);
                } else if (uUseVertexColor) {
                    finalColor = vColor;
                } else {
                    finalColor = uObjectColor;
                }
                
                // Apply diffuse lighting (simple ambient + diffuse)
                vec3 ambientColor = finalColor.rgb * 0.3; // Simple ambient light
                vec3 diffuseColor = finalColor.rgb * diffuse * 0.7; // Diffuse light
                gl_FragColor = vec4(ambientColor + diffuseColor, finalColor.a);
            }
        `;

        const vertexShader = this.compileShader(this.gl.VERTEX_SHADER, vertexShaderSource);
        const fragmentShader = this.compileShader(this.gl.FRAGMENT_SHADER, fragmentShaderSource);
        const shaderProgram = this.gl.createProgram();
        this.gl.attachShader(shaderProgram, vertexShader);
        this.gl.attachShader(shaderProgram, fragmentShader);
        this.gl.linkProgram(shaderProgram);

        if (!this.gl.getProgramParameter(shaderProgram, this.gl.LINK_STATUS)) {
            console.error('Shader program linking failed:', this.gl.getProgramInfoLog(shaderProgram));
            return null;
        }

        // Cache attribute and uniform locations
        this.attributes.position = this.gl.getAttribLocation(shaderProgram, 'aPosition');
        this.attributes.texCoord = this.gl.getAttribLocation(shaderProgram, 'aTexCoord');
        this.attributes.normal = this.gl.getAttribLocation(shaderProgram, 'aNormal');
        this.attributes.color = this.gl.getAttribLocation(shaderProgram, 'aColor'); // Get location for vertex color

        this.uniforms.modelViewMatrix = this.gl.getUniformLocation(shaderProgram, 'uModelViewMatrix');
        this.uniforms.projectionMatrix = this.gl.getUniformLocation(shaderProgram, 'uProjectionMatrix');
        this.uniforms.normalMatrix = this.gl.getUniformLocation(shaderProgram, 'uNormalMatrix'); // New uniform
        this.uniforms.texture = this.gl.getUniformLocation(shaderProgram, 'uTexture');
        this.uniforms.useTexture = this.gl.getUniformLocation(shaderProgram, 'uUseTexture');
        this.uniforms.useVertexColor = this.gl.getUniformLocation(shaderProgram, 'uUseVertexColor');
        this.uniforms.lightDirection = this.gl.getUniformLocation(shaderProgram, 'uLightDirection');
        this.uniforms.objectColor = this.gl.getUniformLocation(shaderProgram, 'uObjectColor');

        return shaderProgram;
    }

    compileShader(type, source) {
        const shader = this.gl.createShader(type);
        this.gl.shaderSource(shader, source);
        this.gl.compileShader(shader);
        if (!this.gl.getShaderParameter(shader, this.gl.COMPILE_STATUS)) {
            console.error('Shader compilation failed:', this.gl.getShaderInfoLog(shader));
            this.gl.deleteShader(shader);
            return null;
        }
        return shader;
    }

    initBuffers() {
        const buffers = {
            terrain: {
                vertices: this.gl.createBuffer(),
                indices: this.gl.createBuffer(),
                texCoords: this.gl.createBuffer(),
                colors: this.gl.createBuffer(),
                normals: this.gl.createBuffer()
            },
            entities: {
                vertices: this.gl.createBuffer(),
                indices: this.gl.createBuffer(),
                texCoords: this.gl.createBuffer(),
                normals: this.gl.createBuffer()
            }
        };
        return buffers;
    }

    createTexture(image) {
        const texture = this.gl.createTexture();
        this.gl.bindTexture(this.gl.TEXTURE_2D, texture);
        this.gl.texImage2D(this.gl.TEXTURE_2D, 0, this.gl.RGBA, this.gl.RGBA, this.gl.UNSIGNED_BYTE, image);
        this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_WRAP_S, this.gl.CLAMP_TO_EDGE);
        this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_WRAP_T, this.gl.CLAMP_TO_EDGE);
        this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_MIN_FILTER, this.gl.LINEAR);
        this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_MAG_FILTER, this.gl.LINEAR);
        return texture;
    }

    async initializeModels() { // Made async
        this.models.units = {};
        this.models.buildings = {};
        this.models.terrainModels = {}; // Add terrainModels to this.models

        const loadPromises = [];

        // Initialize and load unit models
        for (const [key, modelConfig] of Object.entries(UNIT_MODELS)) {
            const model = {
                modelPath: modelConfig.modelPath,
                scale: modelConfig.scale,
                size: modelConfig.size,
                loaded: false,
                geometry: null
            };
            this.models.units[key] = model;

            loadPromises.push(
                this.loadModel(modelConfig.modelPath)
                    .then(geometry => {
                        model.geometry = geometry;
                        model.loaded = true;
                        console.log(`Loaded unit model: ${key}`);
                    })
                    .catch(error => {
                        console.error(`Failed to load unit model ${key}:`, error);
                        // Ensure model is still marked as loaded (even if empty) to prevent infinite waiting
                        model.loaded = true;
                    })
            );
        }

        // Initialize and load building models
        for (const [key, modelConfig] of Object.entries(BUILDING_MODELS)) {
            const model = {
                modelPath: modelConfig.modelPath,
                scale: modelConfig.scale,
                size: modelConfig.size,
                loaded: false,
                geometry: null
            };
            this.models.buildings[key] = model;

            loadPromises.push(
                this.loadModel(modelConfig.modelPath)
                    .then(geometry => {
                        model.geometry = geometry;
                        model.loaded = true;
                        console.log(`Loaded building model: ${key}`);
                    })
                    .catch(error => {
                        console.error(`Failed to load building model ${key}:`, error);
                        model.loaded = true;
                    })
            );
        }

        // Initialize and load terrain models (e.g., resources)
        for (const [key, modelConfig] of Object.entries(TERRAIN_MODELS)) {
            const model = {
                modelPath: modelConfig.modelPath,
                scale: modelConfig.scale,
                size: modelConfig.size,
                loaded: false,
                geometry: null
            };
            this.models.terrainModels[key] = model;

            loadPromises.push(
                this.loadModel(modelConfig.modelPath)
                    .then(geometry => {
                        model.geometry = geometry;
                        model.loaded = true;
                        console.log(`Loaded terrain model: ${key}`);
                    })
                    .catch(error => {
                        console.error(`Failed to load terrain model ${key}:`, error);
                        model.loaded = true;
                    })
            );
        }

        await Promise.all(loadPromises);
        console.log('All 3D models initialized and loaded.');
    }

    async loadModel(modelPath) { // Made async
        console.log(`Loading model from ${modelPath}`);
        try {
            const response = await fetch(modelPath);
            if (!response.ok) {
                throw new Error(`Failed to load OBJ: ${response.statusText}`);
            }
            const text = await response.text();
            return this.parseOBJ(text);
        } catch (error) {
            console.error(`Error loading model ${modelPath}:`, error);
            // Return an empty geometry object on error to prevent crashes
            return {
                vertices: new Float32Array([]),
                indices: new Uint16Array([]),
                texCoords: new Float32Array([]),
                normals: new Float32Array([])
            };
        }
    }

    parseOBJ(objText) {
        const lines = objText.split('\n');
        const rawVertices = [];
        const rawNormals = [];
        const rawTexCoords = [];
        const faces = []; // Stores arrays of [vIndex, vtIndex, vnIndex] for each vertex of a face

        for (const line of lines) {
            const parts = line.trim().split(/\s+/);
            const type = parts[0];

            switch (type) {
                case 'v':
                    rawVertices.push(parseFloat(parts[1]), parseFloat(parts[2]), parseFloat(parts[3]));
                    break;
                case 'vn':
                    rawNormals.push(parseFloat(parts[1]), parseFloat(parts[2]), parseFloat(parts[3]));
                    break;
                case 'vt':
                    rawTexCoords.push(parseFloat(parts[1]), parseFloat(parts[2]));
                    break;
                case 'f':
                    const faceVertices = [];
                    for (let i = 1; i < parts.length; i++) {
                        const indices = parts[i].split('/').map(s => parseInt(s, 10));
                        faceVertices.push(indices);
                    }
                    faces.push(faceVertices);
                    break;
            }
        }

        const vertices = [];
        const normals = [];
        const texCoords = [];
        const indices = [];
        const vertexMap = new Map(); // Map from "v/vt/vn" string to new index

        const getOrAddVertex = (v, vt, vn) => {
            const key = `${v}/${vt}/${vn}`;
            if (vertexMap.has(key)) {
                return vertexMap.get(key);
            }

            const newIndex = vertices.length / 3;
            vertexMap.set(key, newIndex);

            // OBJ indices are 1-based, convert to 0-based
            vertices.push(rawVertices[(v - 1) * 3], rawVertices[(v - 1) * 3 + 1], rawVertices[(v - 1) * 3 + 2]);
            
            if (vn && rawNormals.length > 0 && !isNaN(vn)) { // Check for valid vn index
                normals.push(rawNormals[(vn - 1) * 3], rawNormals[(vn - 1) * 3 + 1], rawNormals[(vn - 1) * 3 + 2]);
            } else {
                // If no normal, push a default (0,0,1)
                normals.push(0, 0, 1);
            }

            if (vt && rawTexCoords.length > 0 && !isNaN(vt)) { // Check for valid vt index
                texCoords.push(rawTexCoords[(vt - 1) * 2], rawTexCoords[(vt - 1) * 2 + 1]);
            } else {
                texCoords.push(0, 0); // Default if no tex coord
            }

            return newIndex;
        };

        for (const face of faces) {
            // Triangulate n-gons (for quads, create two triangles)
            if (face.length === 3) { // Triangle
                indices.push(
                    getOrAddVertex(face[0][0], face[0][1], face[0][2]),
                    getOrAddVertex(face[1][0], face[1][1], face[1][2]),
                    getOrAddVertex(face[2][0], face[2][1], face[2][2])
                );
            } else if (face.length === 4) { // Quad
                const i0 = getOrAddVertex(face[0][0], face[0][1], face[0][2]);
                const i1 = getOrAddVertex(face[1][0], face[1][1], face[1][2]);
                const i2 = getOrAddVertex(face[2][0], face[2][1], face[2][2]);
                const i3 = getOrAddVertex(face[3][0], face[3][1], face[3][2]);
                indices.push(i0, i1, i2);
                indices.push(i0, i2, i3);
            }
            // Other n-gons would require more complex triangulation
        }

        return {
            vertices: new Float32Array(vertices),
            indices: new Uint16Array(indices),
            texCoords: new Float32Array(texCoords),
            normals: new Float32Array(normals)
        };
    }

    render(simulation, gameContext) {
        if (!this.gl || !this.shaderProgram) {
            console.error('WebGL or shaders not initialized.');
            return;
        }

        // Set viewport to match canvas size
        this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
        
        // Check for WebGL errors
        const error = this.gl.getError();
        if (error !== this.gl.NO_ERROR) {
            console.error('WebGL error before rendering:', error);
        }

        this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT);
        this.gl.useProgram(this.shaderProgram);

        // Calculate visible world bounds for frustum culling - make bounds much more generous
        const visibleWorldLeft = this.camera.x - this.camera.canvasWidth / this.camera.zoom;
        const visibleWorldRight = this.camera.x + this.camera.canvasWidth / this.camera.zoom;
        const visibleWorldTop = this.camera.y - this.camera.canvasHeight / this.camera.zoom;
        const visibleWorldBottom = this.camera.y + this.camera.canvasHeight / this.camera.zoom;

        // Set up projection and model-view matrices
        const viewMatrix = this.setupMatrices();

        // Set light direction for basic lighting
        this.gl.uniform3f(this.uniforms.lightDirection, 0.2, 0.8, -0.5);

        // Get terrain data - check multiple possible locations
        let terrainData = simulation.terrain || simulation.gameContext?.terrain || gameContext.terrain;
        
        if (!terrainData || Object.keys(terrainData).length === 0) {
            console.warn('No terrain data available for WebGL rendering');
            return;
        }

        // Render terrain
        this.renderTerrain(terrainData, visibleWorldLeft, visibleWorldRight, visibleWorldTop, visibleWorldBottom, viewMatrix);

        // Render entities - create a context object with simulation data
        const renderContext = {
            terrain: simulation.terrain,
            units: simulation.entityManager.units,
            buildings: simulation.entityManager.buildings,
            resourceNodes: simulation.gameContext.resourceNodes || []
        };
        this.renderEntities(renderContext, visibleWorldLeft, visibleWorldRight, visibleWorldTop, visibleWorldBottom, viewMatrix);

        // Render UI elements like minimap and captions if applicable
        // Placeholder for UI rendering logic
    }

    setupMatrices() {
        const projectionMatrix = mat4.create();
        const fieldOfViewY = 45 * Math.PI / 180;
        const aspect = this.canvas.width / this.canvas.height;
        const zNear = 1.0;
        const zFar = 50000;
        mat4.perspective(projectionMatrix, fieldOfViewY, aspect, zNear, zFar);

        const viewMatrix = mat4.create();
        const cam = this.camera;

        // Proper RTS camera: distance, zoom, and azimuth
        const baseDistance = 1500; // Base camera distance
        const distance = baseDistance / cam.zoom; // Zoom affects distance
        const azimuthRad = cam.rotation * Math.PI / 180; // Azimuth angle in radians
        const elevationRad = (cam.angle || 30) * Math.PI / 180; // Elevation angle in radians
        
        // Calculate camera position using spherical coordinates
        const eyeX = cam.x + distance * Math.cos(elevationRad) * Math.cos(azimuthRad);
        const eyeY = cam.y + distance * Math.cos(elevationRad) * Math.sin(azimuthRad);
        const eyeZ = distance * Math.sin(elevationRad);
        
        const eye = vec3.fromValues(eyeX, eyeY, eyeZ);
        const center = vec3.fromValues(cam.x, cam.y, 0); // Look at target point
        const up = vec3.fromValues(0, 0, 1); // Z is up

        mat4.lookAt(viewMatrix, eye, center, up);
        
        this.gl.uniformMatrix4fv(this.uniforms.projectionMatrix, false, projectionMatrix);
        this.gl.uniformMatrix4fv(this.uniforms.modelViewMatrix, false, viewMatrix);

        const globalNormalMatrix = mat4.create();
        mat4.invert(globalNormalMatrix, viewMatrix);
        mat4.transpose(globalNormalMatrix, globalNormalMatrix);
        this.gl.uniformMatrix4fv(this.uniforms.normalMatrix, false, globalNormalMatrix);

        return viewMatrix;
    }
    
    // Custom math helper functions (multiplyMatrices, get3x3, inverseMatrix, transposeMatrix, mat3ToMat4)
    // are now removed as their functionality will be replaced by gl-matrix.

    renderTerrain(terrainData, left, right, top, bottom, viewMatrix) {
        // Adjust start/end coordinates to ensure we iterate through the entire terrain grid,
        // as the rendering logic will now determine visibility based on actual camera frustum.
        const startX = 0;
        const endX = GRID_SIZE;
        const startY = 0;
        const endY = GRID_SIZE;

        let vertices = [];
        let indices = [];
        let colors = [];
        let texCoords = [];
        let normals = [];

        console.log('Rendering terrain with bounds:', {startX, endX, startY, endY});
        console.log('Terrain data sample:', terrainData[0] ? terrainData[0][0] : 'no data');

        // Function to get height at a given grid coordinate, handling boundaries
        const getHeight = (x, y) => {
            if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
                return 0; // Or some default "out of bounds" height
            }
            return terrainData[x][y].elevation * 20; // Scale elevation for better visual
        };

        // Function to calculate normal for a vertex at (x, y) in grid coordinates
        const calculateNormal = (gx, gy) => {
            // Get heights of surrounding points (using finite differences)
            const hC = getHeight(gx, gy); // Center
            const hL = getHeight(gx - 1, gy); // Left
            const hR = getHeight(gx + 1, gy); // Right
            const hB = getHeight(gx, gy - 1); // Back
            const hF = getHeight(gx, gy + 1); // Forward

            // Calculate tangents (simplified for grid alignment)
            const dX = (hR - hL);
            const dY = (hF - hB);

            // Cross product of tangents to get normal
            // Tangent X: (TILE_SIZE * 2, 0, dX)
            // Tangent Y: (0, TILE_SIZE * 2, dY)
            const normalUnnormalized = vec3.fromValues(dX, dY, TILE_SIZE * 2); // Z-up normal
            const normal = vec3.create();
            vec3.normalize(normal, normalUnnormalized);

            return normal;
        };

        let indexOffset = 0;

        for (let x = startX; x < endX; x++) {
            for (let y = startY; y < endY; y++) {
                if (!terrainData[x] || terrainData[x][y] === undefined) continue;

                // Skip quad rendering for resource tiles, as they will be models
                if (terrainData[x][y].type === TERRAIN_TYPES.RESOURCE) {
                    continue;
                }

                const posX = x * TILE_SIZE;
                const posY = y * TILE_SIZE;
                const size = TILE_SIZE;
                const elevation = terrainData[x][y].elevation;
                // let height = elevation * 20; // This line is not needed anymore as getHeight is used

                // Vertices for the quad, now using terrain elevation
                // Each vertex will have its own calculated normal
                // V0: Top-left
                vertices.push(posX, posY, getHeight(x, y));
                normals.push(...calculateNormal(x, y));
                texCoords.push(0, 0);

                // V1: Top-right
                vertices.push(posX + size, posY, getHeight(x + 1, y));
                normals.push(...calculateNormal(x + 1, y));
                texCoords.push(1, 0);

                // V2: Bottom-left
                vertices.push(posX, posY + size, getHeight(x, y + 1));
                normals.push(...calculateNormal(x, y + 1));
                texCoords.push(0, 1);

                // V3: Bottom-right
                vertices.push(posX + size, posY + size, getHeight(x + 1, y + 1));
                normals.push(...calculateNormal(x + 1, y + 1));
                texCoords.push(1, 1);

                // Indices for two triangles forming a quad
                indices.push(indexOffset, indexOffset + 1, indexOffset + 2);
                indices.push(indexOffset + 1, indexOffset + 3, indexOffset + 2);
                indexOffset += 4;

                // Determine terrain type based on elevation relative to sea level
                let terrainType = terrainData[x][y].type;
                if (elevation < SEA_LEVEL) {
                    terrainType = TERRAIN_TYPES.WATER;
                }

                let color;
                switch (terrainType) {
                    case TERRAIN_TYPES.WATER:
                        // Water colors - deeper blues for lower elevations
                        const waterDepth = SEA_LEVEL - elevation;
                        const blueIntensity = 0.5 + waterDepth * 1.0;
                        const greenIntensity = 0.2 + waterDepth * 0.5;
                        color = [0.0, Math.min(greenIntensity, 0.7), Math.min(blueIntensity, 1.0), 1.0];
                        break;
                    case TERRAIN_TYPES.LAND:
                        // Land colors - greener for higher elevations
                        const landHeight = elevation - SEA_LEVEL;
                        color = [0.2 + landHeight * 0.2, 0.6 + landHeight * 0.1, 0.2 + landHeight * 0.2, 1.0];
                        break;
                    case TERRAIN_TYPES.MOUNTAIN:
                        // Mountain colors - grayer for higher elevations
                        const mountainHeight = elevation - SEA_LEVEL;
                        const grayValue = 0.4 + mountainHeight * 0.4;
                        color = [grayValue, grayValue, grayValue, 1.0];
                        break;
                    default:
                        color = [0.1, 0.1, 0.1, 1.0]; // Default dark gray
                }
                // Push color for each vertex
                colors.push(...color, ...color, ...color, ...color);
            }
        }

        console.log('Generated terrain mesh:', {
            vertexCount: vertices.length / 3,
            indexCount: indices.length,
            triangleCount: indices.length / 3,
            sampleVertices: vertices.slice(0, 9),
            sampleIndices: indices.slice(0, 9)
        });

        // Bind and upload data to buffers
        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.vertices);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(vertices), this.gl.STATIC_DRAW);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.colors);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(colors), this.gl.STATIC_DRAW);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.texCoords);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(texCoords), this.gl.STATIC_DRAW);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.normals);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(normals), this.gl.STATIC_DRAW);

        this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.buffers.terrain.indices);
        this.gl.bufferData(this.gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(indices), this.gl.STATIC_DRAW);

        // Set up vertex attributes
        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.vertices);
        this.gl.vertexAttribPointer(this.attributes.position, 3, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.position);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.colors);
        this.gl.vertexAttribPointer(this.attributes.color, 4, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.color);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.texCoords);
        this.gl.vertexAttribPointer(this.attributes.texCoord, 2, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.texCoord);

        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.terrain.normals);
        this.gl.vertexAttribPointer(this.attributes.normal, 3, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.normal);

        // Set uniforms
        this.gl.uniform1i(this.uniforms.useTexture, 0); // Not using texture for terrain initially
        this.gl.uniform1i(this.uniforms.useVertexColor, 1); // Use vertex colors for terrain
        this.gl.uniform4f(this.uniforms.objectColor, 1.0, 1.0, 1.0, 1.0); // Default white for terrain

        // Draw terrain
        this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.buffers.terrain.indices);
        this.gl.drawElements(this.gl.TRIANGLES, indices.length, this.gl.UNSIGNED_SHORT, 0);

        // Re-enable color attribute for subsequent rendering if it was disabled (if not -1)
        if (this.attributes.color !== -1 && this.gl.getVertexAttrib(this.attributes.color, this.gl.VERTEX_ATTRIB_ARRAY_ENABLED)) {
            this.gl.enableVertexAttribArray(this.attributes.color);
        }

        // Render terrain models (like resources)
        this.renderTerrainModels(terrainData, left, right, top, bottom, viewMatrix);
    }

    renderTerrainModels(terrain, left, right, top, bottom, viewMatrix) {
        // Disable color attribute for models as we are using a uniform objectColor
        if (this.attributes.color !== -1) {
            this.gl.disableVertexAttribArray(this.attributes.color);
        }

        for (let x = 0; x < GRID_SIZE; x++) {
            for (let y = 0; y < GRID_SIZE; y++) {
                if (terrain[x] && terrain[x][y].type === TERRAIN_TYPES.RESOURCE) { // Changed to .type
                    const modelInfo = this.models.terrainModels.resource; // Assuming only one resource model
                    if (!modelInfo || !modelInfo.loaded || !modelInfo.geometry || modelInfo.geometry.indices.length === 0) {
                        continue;
                    }

                    const geometry = modelInfo.geometry;
                    const effectiveSize = modelInfo.size * modelInfo.scale;

                    const objWorldX = x * TILE_SIZE;
                    const objWorldY = y * TILE_SIZE;

                    // Visibility check for resource models
                    if (!(objWorldX + effectiveSize > left && objWorldX - effectiveSize < right &&
                          objWorldY + effectiveSize > top && objWorldY - effectiveSize < bottom)) {
                        continue;
                    }

                    // Bind buffers for the resource model
                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
                    this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.vertices, this.gl.DYNAMIC_DRAW);

                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
                    this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.normals, this.gl.DYNAMIC_DRAW);

                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
                    this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.texCoords, this.gl.DYNAMIC_DRAW);

                    this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.buffers.entities.indices);
                    this.gl.bufferData(this.gl.ELEMENT_ARRAY_BUFFER, geometry.indices, this.gl.DYNAMIC_DRAW);

                    // Set up vertex attributes
                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
                    this.gl.vertexAttribPointer(this.attributes.position, 3, this.gl.FLOAT, false, 0, 0);
                    this.gl.enableVertexAttribArray(this.attributes.position);

                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
                    this.gl.vertexAttribPointer(this.attributes.normal, 3, this.gl.FLOAT, false, 0, 0);
                    this.gl.enableVertexAttribArray(this.attributes.normal);

                    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
                    this.gl.vertexAttribPointer(this.attributes.texCoord, 2, this.gl.FLOAT, false, 0, 0);
                    this.gl.enableVertexAttribArray(this.attributes.texCoord);

                    this.gl.uniform1i(this.uniforms.useTexture, 0);
                    this.gl.uniform1i(this.uniforms.useVertexColor, 0);
                    this.gl.uniform4f(this.uniforms.objectColor, 0.8, 0.8, 0.2, 1.0); // Yellow for resources

                    // Apply transformation
                    const scaleFactor = modelInfo.scale * 0.1; // Scale down models
                    const zOffset = 0; // Default Z offset for terrain models

                    // For resource models, elevate them slightly based on terrain elevation
                    const terrainElevation = terrain[x][y].elevation;
                    const modelZ = terrainElevation * 20 + zOffset; // Match terrain height scaling

                    // Translation matrix for the model's world position
                    const translationMatrix = [
                        1, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 1, 0,
                        objWorldX, objWorldY, modelZ, 1
                    ];

                    // Scaling matrix
                    const scalingMatrix = [
                        scaleFactor, 0, 0, 0,
                        0, scaleFactor, 0, 0,
                        0, 0, scaleFactor, 0,
                        0, 0, 0, 1
                    ];

                    // Combine with view matrix 
                    let modelMatrix = mat4.create();
                    // Create translation matrix
                    const translateVec = vec3.fromValues(objWorldX, objWorldY, modelZ);
                    mat4.fromTranslation(modelMatrix, translateVec);
                    // Create scaling matrix and apply it
                    const scaleVec = vec3.fromValues(scaleFactor, scaleFactor, scaleFactor);
                    mat4.scale(modelMatrix, modelMatrix, scaleVec); // Scale existing modelMatrix
                    
                    let modelViewMatrix = mat4.create();
                    mat4.multiply(modelViewMatrix, viewMatrix, modelMatrix);

                    this.gl.uniformMatrix4fv(this.uniforms.modelViewMatrix, false, modelViewMatrix);
                    
                    const normalMatrixRender = mat4.create(); // Renamed to avoid conflict with 'normalMatrix' in parent scope
                    mat4.invert(normalMatrixRender, modelViewMatrix);
                    mat4.transpose(normalMatrixRender, normalMatrixRender);
                    this.gl.uniformMatrix4fv(this.uniforms.normalMatrix, false, normalMatrixRender);

                    this.gl.drawElements(this.gl.TRIANGLES, geometry.indices.length, this.gl.UNSIGNED_SHORT, 0);
                }
            }
        }
    }

    // normalizeVector removed, use vec3.normalize from gl-matrix

    renderEntities(renderContext, left, right, top, bottom, viewMatrix) {
        // Helper function for visibility check - make it much more permissive for debugging
        const isVisible = (obj, modelSize) => {
            const effectiveSize = modelSize || 50; // Use modelSize if available, else default
            const objWorldX = obj.x * TILE_SIZE;
            const objWorldY = obj.y * TILE_SIZE;
            
            return objWorldX + effectiveSize > left && objWorldX - effectiveSize < right &&
                   objWorldY + effectiveSize > top && objWorldY - effectiveSize < bottom;
        };
        
        // Disable the color attribute for entities since we're using a uniform color
        if (this.attributes.color !== -1 && this.gl.getVertexAttrib(this.attributes.color, this.gl.VERTEX_ATTRIB_ARRAY_ENABLED)) {
            this.gl.disableVertexAttribArray(this.attributes.color);
        }

        // Render units
        for (const [modelKey, modelInfo] of Object.entries(this.models.units)) {
            if (!modelInfo.loaded || !modelInfo.geometry || modelInfo.geometry.indices.length === 0) {
                continue;
            }

            const geometry = modelInfo.geometry;

            // Bind buffers for the current model
            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.vertices, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.normals, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.texCoords, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.buffers.entities.indices);
            this.gl.bufferData(this.gl.ELEMENT_ARRAY_BUFFER, geometry.indices, this.gl.DYNAMIC_DRAW);

            // Set up vertex attributes - bind each buffer before setting up its attribute
            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
            this.gl.vertexAttribPointer(this.attributes.position, 3, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.position);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
            this.gl.vertexAttribPointer(this.attributes.normal, 3, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.normal);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
            this.gl.vertexAttribPointer(this.attributes.texCoord, 2, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.texCoord);

            this.gl.uniform1i(this.uniforms.useTexture, 0); // Not using texture for entities for now
            this.gl.uniform1i(this.uniforms.useVertexColor, 0); // Use uniform color for entities

            // Render instances of this unit type
            renderContext.units.forEach(unit => {
                let unitModelKey = Object.keys(UNIT_MODELS).find(key => UNIT_MODELS[key].name === unit.type.name);
                if (!unitModelKey) unitModelKey = 'scout'; // Default fallback
                
                console.log(`Trying to render unit ${unit.id} (${unit.team}): type.name="${unit.type.name}", unitModelKey="${unitModelKey}", currentModelKey="${modelKey}"`);
                
                if (unitModelKey !== modelKey) return; // Only render units of the current model type

                const effectiveSize = modelInfo.size * modelInfo.scale;
                if (!isVisible(unit, effectiveSize)) {
                    console.log(`Unit ${unit.id} not visible: position=(${unit.x},${unit.y}), effectiveSize=${effectiveSize}`);
                    return;
                }

                console.log(`Rendering unit ${unit.id} (${unit.team}) with model ${modelKey} at position (${unit.x}, ${unit.y})`);
                
                const teamColor = unit.team === 'blue' ? [0.3, 0.5, 1.0, 1.0] : [0.8, 0.1, 0.1, 1.0];
                this.gl.uniform4f(this.uniforms.objectColor, teamColor[0], teamColor[1], teamColor[2], teamColor[3]);

                // Scale factor for units
                const unitScale = modelInfo.scale * 0.1; // Make units visible
                const posZ = renderContext.terrain[unit.x][unit.y].elevation * 20; // Align unit Z to terrain height

                // Translation matrix for the unit's world position
                const translationMatrix = [
                    1, 0, 0, unit.x * TILE_SIZE,
                    0, 1, 0, unit.y * TILE_SIZE,
                    0, 0, 1, posZ,
                    0, 0, 0, 1
                ];

                // Scaling matrix
                const scalingMatrix = [
                    unitScale, 0, 0, 0,
                    0, unitScale, 0, 0,
                    0, 0, unitScale, 0,
                    0, 0, 0, 1
                ];

                // Combine with view matrix
                let modelMatrix = mat4.create();
                const translateVecUnit = vec3.fromValues(unit.x * TILE_SIZE, unit.y * TILE_SIZE, posZ);
                mat4.fromTranslation(modelMatrix, translateVecUnit);
                // TODO: Add unit rotation based on unit.angle (e.g., around Z axis)
                // mat4.rotateZ(modelMatrix, modelMatrix, unit.angleRadians); // Assuming unit.angle is in radians
                const scaleVecUnit = vec3.fromValues(unitScale, unitScale, unitScale);
                mat4.scale(modelMatrix, modelMatrix, scaleVecUnit);

                let modelViewMatrix = mat4.create();
                mat4.multiply(modelViewMatrix, viewMatrix, modelMatrix);

                this.gl.uniformMatrix4fv(this.uniforms.modelViewMatrix, false, modelViewMatrix);
                
                const normalMatrixRender = mat4.create();
                mat4.invert(normalMatrixRender, modelViewMatrix);
                mat4.transpose(normalMatrixRender, normalMatrixRender);
                this.gl.uniformMatrix4fv(this.uniforms.normalMatrix, false, normalMatrixRender);

                this.gl.drawElements(this.gl.TRIANGLES, geometry.indices.length, this.gl.UNSIGNED_SHORT, 0);
            });
        }
        // Render buildings (similar logic)
        for (const [modelKey, modelInfo] of Object.entries(this.models.buildings)) {
            if (!modelInfo.loaded || !modelInfo.geometry || modelInfo.geometry.indices.length === 0) {
                // console.warn(`Model ${modelKey} not loaded or has no geometry, skipping rendering.`);
                continue;
            }

            const geometry = modelInfo.geometry;

            // Bind buffers for the current model
            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.vertices, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.normals, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
            this.gl.bufferData(this.gl.ARRAY_BUFFER, geometry.texCoords, this.gl.DYNAMIC_DRAW);

            this.gl.bindBuffer(this.gl.ELEMENT_ARRAY_BUFFER, this.buffers.entities.indices);
            this.gl.bufferData(this.gl.ELEMENT_ARRAY_BUFFER, geometry.indices, this.gl.DYNAMIC_DRAW);

            // Set up vertex attributes - bind each buffer before setting up its attribute
            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.vertices);
            this.gl.vertexAttribPointer(this.attributes.position, 3, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.position);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.normals);
            this.gl.vertexAttribPointer(this.attributes.normal, 3, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.normal);

            this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffers.entities.texCoords);
            this.gl.vertexAttribPointer(this.attributes.texCoord, 2, this.gl.FLOAT, false, 0, 0);
            this.gl.enableVertexAttribArray(this.attributes.texCoord);

            this.gl.uniform1i(this.uniforms.useTexture, 0);
            this.gl.uniform1i(this.uniforms.useVertexColor, 0); // Use uniform color for buildings

            // Render instances of this building type
            renderContext.buildings.forEach(building => {
                let buildingModelKey = Object.keys(BUILDING_MODELS).find(key => BUILDING_MODELS[key].name === building.type.name);
                if (!buildingModelKey) buildingModelKey = 'landFactory'; // Default fallback
                if (buildingModelKey !== modelKey) return;

                const effectiveSize = modelInfo.size * modelInfo.scale;
                if (!isVisible(building, effectiveSize)) return;

                const teamColor = building.team === 'blue' ? [0.3, 0.5, 1.0, 1.0] : [1.0, 0.3, 0.3, 1.0];
                this.gl.uniform4f(this.uniforms.objectColor, teamColor[0], teamColor[1], teamColor[2], teamColor[3]);

                // Scale factor for buildings
                const buildingScale = modelInfo.scale * 0.1; // Make buildings larger
                const posZ = renderContext.terrain[building.x][building.y].elevation * 20; // Align building Z to terrain height

                // Translation matrix for the building's world position
                const translationMatrix = [
                    1, 0, 0, building.x * TILE_SIZE,
                    0, 1, 0, building.y * TILE_SIZE,
                    0, 0, 1, posZ,
                    0, 0, 0, 1
                ];

                // Scaling matrix
                const scalingMatrix = [
                    buildingScale, 0, 0, 0,
                    0, buildingScale, 0, 0,
                    0, 0, buildingScale, 0,
                    0, 0, 0, 1
                ];

                // Combine with view matrix
                let modelMatrix = mat4.create();
                const translateVecBuilding = vec3.fromValues(building.x * TILE_SIZE, building.y * TILE_SIZE, posZ);
                mat4.fromTranslation(modelMatrix, translateVecBuilding);
                // TODO: Add building rotation if applicable
                const scaleVecBuilding = vec3.fromValues(buildingScale, buildingScale, buildingScale);
                mat4.scale(modelMatrix, modelMatrix, scaleVecBuilding);
                
                let modelViewMatrix = mat4.create();
                mat4.multiply(modelViewMatrix, viewMatrix, modelMatrix);

                this.gl.uniformMatrix4fv(this.uniforms.modelViewMatrix, false, modelViewMatrix);
                
                const normalMatrixRender = mat4.create();
                mat4.invert(normalMatrixRender, modelViewMatrix);
                mat4.transpose(normalMatrixRender, normalMatrixRender);
                this.gl.uniformMatrix4fv(this.uniforms.normalMatrix, false, normalMatrixRender);

                this.gl.drawElements(this.gl.TRIANGLES, geometry.indices.length, this.gl.UNSIGNED_SHORT, 0);
            });
        }
    }

    updateCamera(cameraData) {
        this.camera.x = cameraData.x;
        this.camera.y = cameraData.y;
        this.camera.zoom = cameraData.zoom;
        this.camera.canvasWidth = cameraData.canvasWidth;
        this.camera.canvasHeight = cameraData.canvasHeight;
        this.camera.angle = cameraData.angle || 0; // New: Camera pitch angle
        this.camera.rotation = cameraData.rotation || 0; // New: Camera yaw rotation
    }
}

// Utility function to initialize the renderer
export function initRenderer(canvas) {
    return new WebGLRenderer(canvas);
}