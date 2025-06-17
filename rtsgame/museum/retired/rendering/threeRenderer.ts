// js_rewritten/rendering/threeRenderer.js - Simplified and perfected Three.js renderer

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { WORLD_SIZE, TILE_SIZE, GRID_SIZE } from '../config/gameConstants.js'; // Added GRID_SIZE
import { UNIT_MODELS, BUILDING_MODELS } from '../config/modelDefaults.js';

export class ThreeRenderer {
    constructor(canvas) {
        this.canvas = canvas;
        this.modelManifest = null;
        this.gltfLoader = new GLTFLoader();
        this.textureLoader = new THREE.TextureLoader(); // For potential later use with glTF materials if needed
        
        // Simple but effective mesh caching system
        this.entityMeshes = new Map(); // Maps entity.id to mesh instance
        this.lastEntityIds = new Set(); // Track which entities existed last frame
        
        // Scene setup
        this.scene = new THREE.Scene();
        this.scene.background = new THREE.Color(0x1a1a1a); // Dark grey background for better contrast
        
        // Camera setup for true 16-bit style top-down RTS view
        const frustumSize = WORLD_SIZE * 1.2; // Tighter viewing area for pixel-perfect rendering
        const aspect = canvas.width / canvas.height;
        this.camera = new THREE.OrthographicCamera(
            -frustumSize * aspect / 2, // left
            frustumSize * aspect / 2,  // right
            frustumSize / 2,           // top
            -frustumSize / 2,          // bottom
            1,                         // near plane
            WORLD_SIZE * 4             // far plane - large enough to see entire world
        );
        
        // Position camera high overhead for perfect top-down RTS view
        this.camera.position.set(0, 0, WORLD_SIZE * 2); // High enough to see everything
        this.camera.lookAt(0, 0, 0); // Look straight down
        this.camera.up.set(0, 1, 0); // Standard orientation
        
        // Renderer setup
        this.renderer = new THREE.WebGLRenderer({
            canvas: canvas,
            antialias: true
        });
        this.renderer.setSize(canvas.width, canvas.height);
        this.renderer.shadowMap.enabled = true;
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        
        // Enhanced RTS lighting setup for better visual quality
        const ambientLight = new THREE.AmbientLight(0xffffff, 0.6); // Softer ambient for better contrast
        this.scene.add(ambientLight);
        
        // Main directional light (sun)
        const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
        directionalLight.position.set(100, 200, 100); // Slightly angled for better depth
        directionalLight.target.position.set(0, 0, 0);
        directionalLight.castShadow = true;
        directionalLight.shadow.mapSize.width = 2048;
        directionalLight.shadow.mapSize.height = 2048;
        directionalLight.shadow.camera.near = 0.5;
        directionalLight.shadow.camera.far = WORLD_SIZE * 3;
        directionalLight.shadow.camera.left = -WORLD_SIZE;
        directionalLight.shadow.camera.right = WORLD_SIZE;
        directionalLight.shadow.camera.top = WORLD_SIZE;
        directionalLight.shadow.camera.bottom = -WORLD_SIZE;
        this.scene.add(directionalLight);
        this.scene.add(directionalLight.target);
        
        // Secondary fill light for softer shadows
        const fillLight = new THREE.DirectionalLight(0x8899ff, 0.3);
        fillLight.position.set(-50, 100, -50);
        this.scene.add(fillLight);
        
        // Groups for organizing scene objects
        this.terrainGroup = new THREE.Group();
        this.entityGroup = new THREE.Group();
        this.scene.add(this.terrainGroup);
        this.scene.add(this.entityGroup);

        // Disable right-click context menu to prevent default browser behavior
        this.canvas.addEventListener('contextmenu', (event) => {
            event.preventDefault();
        });
        
        // No initial terrain creation here; it will be created dynamically when data is ready.
        // Manifest loading is deliberately not awaited in constructor to keep constructor synchronous.
        // The caller (initThreeRenderer) should handle the promise.
        this.manifestPromise = this.loadModelManifest();
        console.log('ThreeRenderer initialized, manifest loading started.');
    }

    async loadModelManifest() {
        try {
            const response = await fetch('assets/model-manifest.json');
            if (!response.ok) {
                throw new Error(`Failed to fetch model manifest: ${response.statusText}`);
            }
            this.modelManifest = await response.json();
            // Convert array to object for easier lookup
            this.modelManifest = this.modelManifest.reduce((acc, model) => {
                acc[model.id] = model;
                return acc;
            }, {});
            console.log('Model manifest loaded and processed successfully.');
        } catch (error) {
            console.error('Error loading model manifest:', error);
            this.modelManifest = {}; // Ensure it's an object to prevent errors
        }
    }
    
    // Enhanced terrain generation with proper RTS features
    updateTerrain(terrainData) {
        if (!terrainData || Object.keys(terrainData).length === 0) {
            console.error("Terrain data is invalid or not provided to updateTerrain.");
            return;
        }

        // Clear existing terrain objects
        this.terrainGroup.clear();

        const segments = GRID_SIZE;
        const geometry = new THREE.PlaneGeometry(WORLD_SIZE, WORLD_SIZE, segments - 1, segments - 1);
        geometry.rotateX(-Math.PI / 2); // Orient the plane horizontally

        const vertices = geometry.attributes.position.array;
        const colors = new Float32Array(vertices.length); // Color array for vertex colors
        const elevationScaleFactor = TILE_SIZE * 8; // Reduced for more reasonable terrain height

        // Adjust vertices and assign colors based on terrain type
        for (let i = 0; i < vertices.length; i += 3) {
            const x = Math.floor((vertices[i] + WORLD_SIZE / 2) / TILE_SIZE);
            const y = Math.floor((vertices[i + 1] + WORLD_SIZE / 2) / TILE_SIZE);

            if (terrainData[x] && terrainData[x][y] !== undefined) {
                const tile = terrainData[x][y];
                vertices[i + 2] = tile.elevation * elevationScaleFactor;
                
                // Assign colors based on terrain type
                let color = new THREE.Color();
                switch(tile.type) {
                    case 0: // WATER
                        color.setHex(0x1E90FF); // Deep sky blue
                        vertices[i + 2] = Math.min(vertices[i + 2], -TILE_SIZE); // Water below ground
                        break;
                    case 1: // LAND
                        color.setHex(0x228B22); // Forest green
                        break;
                    case 2: // MOUNTAIN
                        color.setHex(0x8B4513); // Saddle brown
                        vertices[i + 2] += TILE_SIZE * 4; // Mountains higher
                        break;
                    case 3: // RESOURCE
                        color.setHex(0xFFD700); // Gold
                        break;
                    default:
                        color.setHex(0x228B22); // Default green
                }
                
                colors[i] = color.r;
                colors[i + 1] = color.g;
                colors[i + 2] = color.b;
            }
        }
        
        geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
        geometry.computeVertexNormals();
        
        const material = new THREE.MeshLambertMaterial({
            vertexColors: true,
            side: THREE.DoubleSide
        });
        
        this.terrainMesh = new THREE.Mesh(geometry, material);
        this.terrainMesh.position.set(0, 0, 0);
        this.terrainMesh.receiveShadow = true;
        this.terrainGroup.add(this.terrainMesh);
        
        // Add environmental objects
        this.addEnvironmentalObjects(terrainData);
        
        console.log('Enhanced RTS terrain generated with colors and environmental objects');
    }
    
    // Add trees, rocks, debris, and resource nodes
    addEnvironmentalObjects(terrainData) {
        const objectCount = {trees: 0, rocks: 0, debris: 0, resources: 0};
        
        for (let x = 0; x < GRID_SIZE; x += 2) { // Sample every other tile for performance
            for (let y = 0; y < GRID_SIZE; y += 2) {
                if (terrainData[x] && terrainData[x][y]) {
                    const tile = terrainData[x][y];
                    const worldX = x * TILE_SIZE - WORLD_SIZE / 2;
                    const worldY = y * TILE_SIZE - WORLD_SIZE / 2;
                    const worldZ = tile.elevation * TILE_SIZE * 8;
                    
                    // Add trees on land (15% chance)
                    if (tile.type === 1 && Math.random() < 0.15) {
                        this.addTree(worldX, worldY, worldZ);
                        objectCount.trees++;
                    }
                    
                    // Add rocks on mountains (25% chance)
                    if (tile.type === 2 && Math.random() < 0.25) {
                        this.addRock(worldX, worldY, worldZ + TILE_SIZE * 4);
                        objectCount.rocks++;
                    }
                    
                    // Add debris scattered around (5% chance)
                    if (tile.type === 1 && Math.random() < 0.05) {
                        this.addDebris(worldX, worldY, worldZ);
                        objectCount.debris++;
                    }
                    
                    // Add resource nodes (already marked in terrain data)
                    if (tile.type === 3) {
                        this.addResourceNode(worldX, worldY, worldZ, Math.random() < 0.5 ? 'metal' : 'energy');
                        objectCount.resources++;
                    }
                }
            }
        }
        
        console.log(`Added environmental objects: ${objectCount.trees} trees, ${objectCount.rocks} rocks, ${objectCount.debris} debris, ${objectCount.resources} resources`);
    }
    
    addTree(x, y, z) {
        // Simple tree: brown trunk + green top
        const trunkGeometry = new THREE.CylinderGeometry(TILE_SIZE * 0.3, TILE_SIZE * 0.5, TILE_SIZE * 2);
        const trunkMaterial = new THREE.MeshLambertMaterial({color: 0x8B4513}); // Brown
        const trunk = new THREE.Mesh(trunkGeometry, trunkMaterial);
        
        const leavesGeometry = new THREE.SphereGeometry(TILE_SIZE * 1.2);
        const leavesMaterial = new THREE.MeshLambertMaterial({color: 0x228B22}); // Green
        const leaves = new THREE.Mesh(leavesGeometry, leavesMaterial);
        leaves.position.y = TILE_SIZE * 1.5;
        
        const tree = new THREE.Group();
        tree.add(trunk);
        tree.add(leaves);
        tree.position.set(x, y, z + TILE_SIZE);
        tree.castShadow = true;
        
        this.terrainGroup.add(tree);
    }
    
    addRock(x, y, z) {
        const geometry = new THREE.SphereGeometry(TILE_SIZE * 0.8, 6, 4); // Low-poly rock
        const material = new THREE.MeshLambertMaterial({color: 0x696969}); // Dark gray
        const rock = new THREE.Mesh(geometry, material);
        rock.position.set(x, y, z + TILE_SIZE * 0.4);
        rock.castShadow = true;
        this.terrainGroup.add(rock);
    }
    
    addDebris(x, y, z) {
        const geometry = new THREE.BoxGeometry(TILE_SIZE * 0.3, TILE_SIZE * 0.3, TILE_SIZE * 0.2);
        const material = new THREE.MeshLambertMaterial({color: 0x4A4A4A}); // Dark gray
        const debris = new THREE.Mesh(geometry, material);
        debris.position.set(x, y, z + TILE_SIZE * 0.1);
        debris.rotation.y = Math.random() * Math.PI;
        this.terrainGroup.add(debris);
    }
    
    addResourceNode(x, y, z, type) {
        const geometry = new THREE.CylinderGeometry(TILE_SIZE * 0.8, TILE_SIZE * 0.8, TILE_SIZE * 1.5);
        const color = type === 'metal' ? 0xC0C0C0 : 0x0000FF; // Silver for metal, blue for energy
        const material = new THREE.MeshLambertMaterial({color: color, emissive: color, emissiveIntensity: 0.2});
        const resource = new THREE.Mesh(geometry, material);
        resource.position.set(x, y, z + TILE_SIZE * 0.75);
        resource.castShadow = true;
        this.terrainGroup.add(resource);
    }
    
    updateCamera(cameraData) {
        if (!cameraData) return;
        
        // Supreme Commander-style camera with rotation and 3D support
        const zoom = Math.max(0.1, cameraData.zoom || 1.0);
        const frustumSize = (WORLD_SIZE * 1.5) / zoom;
        const aspect = this.canvas.width / this.canvas.height;
        
        // Update orthographic camera frustum for zoom
        this.camera.left = -frustumSize * aspect / 2;
        this.camera.right = frustumSize * aspect / 2;
        this.camera.top = frustumSize / 2;
        this.camera.bottom = -frustumSize / 2;
        
        // Transform camera position to match entity coordinate system (centered at 0,0,0)
        const camX = (cameraData.x || 0) - WORLD_SIZE / 2;
        const camY = (cameraData.y || 0) - WORLD_SIZE / 2;
        
        // Handle rotation and 3D tilt
        const rotation = (cameraData.rotation || 0) * Math.PI / 180; // Convert to radians
        const tiltAngle = (cameraData.angle || 0) * Math.PI / 180; // Convert to radians
        
        // Calculate camera height based on tilt
        const baseHeight = WORLD_SIZE * 2;
        const tiltDistance = WORLD_SIZE * 0.5; // Distance from target when tilted
        
        if (tiltAngle > 0) {
            // 3D perspective mode
            const height = Math.cos(tiltAngle) * baseHeight + Math.sin(tiltAngle) * tiltDistance;
            const offset = Math.sin(tiltAngle) * tiltDistance;
            
            // Position camera with tilt offset
            const offsetX = Math.sin(rotation) * offset;
            const offsetY = -Math.cos(rotation) * offset; // Negative because of coordinate system
            
            this.camera.position.set(
                camX + offsetX,
                camY + offsetY,
                height
            );
            
            // Look at the target point
            this.camera.lookAt(camX, camY, 0);
        } else {
            // Top-down mode with rotation
            this.camera.position.set(camX, camY, baseHeight);
            this.camera.lookAt(camX, camY, 0);
            
            // Apply rotation around the Z-axis (up vector)
            if (rotation !== 0) {
                const upVector = new THREE.Vector3(
                    Math.sin(rotation),
                    Math.cos(rotation),
                    0
                );
                this.camera.up.copy(upVector);
            } else {
                this.camera.up.set(0, 1, 0); // Standard up vector
            }
        }
        
        this.camera.updateProjectionMatrix();
    }
    
    async createUnitMesh(unit) {
        // Try to load actual GLB model first
        if (this.modelManifest) {
            const modelId = this.getModelIdForUnit(unit);
            if (modelId && this.modelManifest[modelId]) {
                try {
                    const gltf = await this.gltfLoader.loadAsync(this.modelManifest[modelId].path);
                    const mesh = gltf.scene.clone();
                    this.applyTeamColor(mesh, unit.team);
                    this.setMeshPosition(mesh, unit);
                    mesh.userData = { unit: unit, animationPhase: Math.random() * Math.PI * 2 };
                    return mesh;
                } catch (error) {
                    console.warn(`Failed to load model for ${unit.type.name}, falling back to procedural:`, error);
                }
            }
        }
        
        // Fallback to procedural mesh
        return this.createProceduralUnitMesh(unit);
    }

    getModelIdForUnit(unit) {
        const typeMap = {
            'Commander': 'commander',
            'Tank': 'tank',
            'Artillery': 'artillery'
        };
        return typeMap[unit.type.name];
    }
    
    createProceduralUnitMesh(unit, modelConfig = null) {
        const mesh = new THREE.Group();
        const size = unit.type.size || 10;
        const teamColor = unit.team === 'blue' ? 0x0088FF : 0xFF2200;
        
        // Create flatter, more sprite-like models for 16-bit style while keeping 3D positioning
        let geometry, material;
        
        if (unit.type.name === 'Commander') {
            // Commander: Flat diamond shape with central core
            geometry = new THREE.BoxGeometry(size * 1.8, size * 1.8, 3); // Very flat
            material = new THREE.MeshLambertMaterial({
                color: teamColor,
                emissive: new THREE.Color(teamColor).multiplyScalar(0.2)
            });
            const body = new THREE.Mesh(geometry, material);
            body.rotation.z = Math.PI / 4; // Rotate 45 degrees for diamond look
            
            // Small antenna on top
            const antennaGeometry = new THREE.BoxGeometry(2, 2, 8);
            const antennaMaterial = new THREE.MeshLambertMaterial({ color: 0xFFD700 });
            const antenna = new THREE.Mesh(antennaGeometry, antennaMaterial);
            antenna.position.z = 6;
            
            mesh.add(body);
            mesh.add(antenna);
        } else if (unit.type.name === 'Tank') {
            // Tank: Flat rectangular body with small turret
            geometry = new THREE.BoxGeometry(size * 1.6, size * 1.2, 4); // Very flat
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const body = new THREE.Mesh(geometry, material);
            
            // Small flat turret
            const turretGeometry = new THREE.BoxGeometry(size * 0.8, size * 0.8, 2);
            const turret = new THREE.Mesh(turretGeometry, material);
            turret.position.z = 3;
            
            mesh.add(body);
            mesh.add(turret);
        } else if (unit.type.name === 'Artillery') {
            // Artillery: Long flat rectangle
            geometry = new THREE.BoxGeometry(size * 2.2, size * 0.8, 3);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const body = new THREE.Mesh(geometry, material);
            
            mesh.add(body);
        } else if (unit.type.domain === 'air') {
            // Air units: Flat cross shape for aircraft
            geometry = new THREE.BoxGeometry(size * 1.8, size * 0.6, 2);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const body = new THREE.Mesh(geometry, material);
            
            // Wings as flat rectangles
            const wingGeometry = new THREE.BoxGeometry(size * 0.4, size * 1.4, 1);
            const leftWing = new THREE.Mesh(wingGeometry, material);
            const rightWing = new THREE.Mesh(wingGeometry, material);
            leftWing.position.x = size * 0.7;
            rightWing.position.x = -size * 0.7;
            
            mesh.add(body);
            mesh.add(leftWing);
            mesh.add(rightWing);
        } else if (unit.type.domain === 'sea') {
            // Naval units: Flat boat shape
            geometry = new THREE.BoxGeometry(size * 2.2, size * 0.8, 3);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const hull = new THREE.Mesh(geometry, material);
            
            mesh.add(hull);
        } else {
            // Default: Small flat square
            geometry = new THREE.BoxGeometry(size * 0.9, size * 0.9, 3);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const body = new THREE.Mesh(geometry, material);
            mesh.add(body);
        }
        
        // Set all meshes in the group to cast shadows
        mesh.traverse((child) => {
            if (child.isMesh) {
                child.castShadow = true;
                child.receiveShadow = true;
            }
        });
        
        this.setMeshPosition(mesh, unit);
        
        // Store animation data
        mesh.userData = {
            unit: unit,
            animationPhase: Math.random() * Math.PI * 2,
            originalY: mesh.position.y
        };
        
        return mesh;
    }

    applyTeamColor(mesh, team) {
        const teamColor = team === 'blue' ? new THREE.Color(0x0088FF) : new THREE.Color(0xFF2200);
        
        mesh.traverse((child) => {
            if (child.isMesh && child.material) {
                // Clone material to avoid affecting other instances
                if (Array.isArray(child.material)) {
                    child.material = child.material.map(mat => {
                        const newMat = mat.clone();
                        if (newMat.color) newMat.color.copy(teamColor);
                        return newMat;
                    });
                } else {
                    child.material = child.material.clone();
                    if (child.material.color) {
                        child.material.color.copy(teamColor);
                    }
                }
                child.castShadow = true;
                child.receiveShadow = true;
            }
        });
    }

    setMeshPosition(mesh, entity) {
        let terrainZ = 0;
        if (this.terrainMesh) {
            const worldX = entity.x - WORLD_SIZE / 2;
            const worldY = entity.y - WORLD_SIZE / 2;
            const halfWorld = WORLD_SIZE / 2;
            const segmentSize = WORLD_SIZE / (GRID_SIZE - 1);
            const xIndex = Math.floor((worldX + halfWorld) / segmentSize);
            const yIndex = Math.floor((worldY + halfWorld) / segmentSize);
            const vertexIndex = (yIndex * GRID_SIZE + xIndex) * 3;
            if (this.terrainMesh.geometry.attributes.position.array[vertexIndex + 2] !== undefined) {
                terrainZ = this.terrainMesh.geometry.attributes.position.array[vertexIndex + 2];
            } else {
                console.warn(`Could not get terrain Z for entity at (${entity.x}, ${entity.y}).`);
            }
        }
        mesh.position.set(
            entity.x - WORLD_SIZE / 2,
            entity.y - WORLD_SIZE / 2,
            terrainZ
        );
    }
    
    createBuildingMesh(building) {
        // Find the building type name by checking which BUILDING_MODELS entry matches
        let buildingTypeName = null;
        for (const [key, value] of Object.entries(BUILDING_MODELS)) {
            if (value.name === building.type.name) {
                buildingTypeName = key;
                break;
            }
        }
        
        return this.createProceduralBuildingMesh(building, buildingTypeName);
    }
    
    createProceduralBuildingMesh(building, buildingTypeName = null) {
        const mesh = new THREE.Group();
        const size = building.type.size || 20;
        const teamColor = building.team === 'blue' ? 0x0044AA : 0xAA2200;
        
        // Create different procedural models based on building type
        let geometry, material;
        
        if (building.type.name === 'Land Factory') {
            // Factory: Industrial look with assembly line
            geometry = new THREE.BoxGeometry(size * 2, size * 1.5, size);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const main = new THREE.Mesh(geometry, material);
            
            // Smokestack
            const stackGeometry = new THREE.CylinderGeometry(size * 0.15, size * 0.15, size * 2);
            const stackMaterial = new THREE.MeshLambertMaterial({ color: 0x444444 });
            const stack = new THREE.Mesh(stackGeometry, stackMaterial);
            stack.position.x = size * 0.8;
            stack.position.y = size * 1.5;
            
            // Assembly doors
            const doorGeometry = new THREE.BoxGeometry(size * 0.8, size * 0.1, size * 1.2);
            const doorMaterial = new THREE.MeshLambertMaterial({ color: 0x222222 });
            const door = new THREE.Mesh(doorGeometry, doorMaterial);
            door.position.x = -size;
            
            mesh.add(main);
            mesh.add(stack);
            mesh.add(door);
        } else if (building.type.name === 'Air Factory') {
            // Air Factory: Hangar-like with runway
            geometry = new THREE.BoxGeometry(size * 2.5, size, size * 1.5);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const hangar = new THREE.Mesh(geometry, material);
            
            // Control tower
            const towerGeometry = new THREE.BoxGeometry(size * 0.5, size * 2, size * 0.5);
            const tower = new THREE.Mesh(towerGeometry, material);
            tower.position.x = size * 1.2;
            tower.position.y = size * 1.25;
            
            // Radar dish
            const dishGeometry = new THREE.CylinderGeometry(size * 0.3, size * 0.1, size * 0.1);
            const dish = new THREE.Mesh(dishGeometry, new THREE.MeshLambertMaterial({ color: 0xCCCCCC }));
            dish.position.x = size * 1.2;
            dish.position.y = size * 2.5;
            
            mesh.add(hangar);
            mesh.add(tower);
            mesh.add(dish);
        } else if (building.type.name === 'Naval Factory') {
            // Naval Factory: Shipyard with cranes
            geometry = new THREE.BoxGeometry(size * 2, size, size * 2);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const shipyard = new THREE.Mesh(geometry, material);
            
            // Crane structure
            const craneGeometry = new THREE.BoxGeometry(size * 0.2, size * 3, size * 0.2);
            const crane = new THREE.Mesh(craneGeometry, new THREE.MeshLambertMaterial({ color: 0x666666 }));
            crane.position.y = size * 1.75;
            
            // Crane arm
            const armGeometry = new THREE.BoxGeometry(size * 1.5, size * 0.15, size * 0.15);
            const arm = new THREE.Mesh(armGeometry, new THREE.MeshLambertMaterial({ color: 0x666666 }));
            arm.position.x = size * 0.6;
            arm.position.y = size * 3;
            
            mesh.add(shipyard);
            mesh.add(crane);
            mesh.add(arm);
        } else if (building.type.name === 'Mass Extractor') {
            // Mass Extractor: Mining rig with drill
            geometry = new THREE.CylinderGeometry(size * 0.8, size * 1, size * 0.8);
            material = new THREE.MeshLambertMaterial({ color: 0x888888 });
            const base = new THREE.Mesh(geometry, material);
            
            // Drill bit
            const drillGeometry = new THREE.ConeGeometry(size * 0.3, size * 1.5);
            const drill = new THREE.Mesh(drillGeometry, new THREE.MeshLambertMaterial({ color: 0x333333 }));
            drill.position.y = -size * 1.1;
            
            // Support structure
            const supportGeometry = new THREE.BoxGeometry(size * 0.2, size * 2, size * 0.2);
            const supports = [];
            for (let i = 0; i < 3; i++) {
                const support = new THREE.Mesh(supportGeometry, material);
                const angle = (i / 3) * Math.PI * 2;
                support.position.x = Math.cos(angle) * size * 0.6;
                support.position.z = Math.sin(angle) * size * 0.6;
                support.position.y = size;
                supports.push(support);
                mesh.add(support);
            }
            
            mesh.add(base);
            mesh.add(drill);
        } else if (building.type.name === 'Energy Plant') {
            // Energy Plant: Power generation with cooling towers
            geometry = new THREE.BoxGeometry(size * 1.5, size, size * 1.5);
            material = new THREE.MeshLambertMaterial({
                color: teamColor,
                emissive: new THREE.Color(0xFFFF00).multiplyScalar(0.1)
            });
            const reactor = new THREE.Mesh(geometry, material);
            
            // Cooling towers
            const towerGeometry = new THREE.CylinderGeometry(size * 0.4, size * 0.5, size * 1.8);
            const tower1 = new THREE.Mesh(towerGeometry, new THREE.MeshLambertMaterial({ color: 0xCCCCCC }));
            const tower2 = new THREE.Mesh(towerGeometry, new THREE.MeshLambertMaterial({ color: 0xCCCCCC }));
            tower1.position.x = size * 0.8;
            tower1.position.y = size * 1.4;
            tower2.position.x = -size * 0.8;
            tower2.position.y = size * 1.4;
            
            mesh.add(reactor);
            mesh.add(tower1);
            mesh.add(tower2);
        } else {
            // Default: Simple building structure
            geometry = new THREE.BoxGeometry(size * 1.5, size, size * 1.5);
            material = new THREE.MeshLambertMaterial({ color: teamColor });
            const building = new THREE.Mesh(geometry, material);
            mesh.add(building);
        }
        
        // Set all meshes in the group to cast shadows
        mesh.traverse((child) => {
            if (child.isMesh) {
                child.castShadow = true;
                child.receiveShadow = true;
            }
        });
        
        this.setMeshPosition(mesh, building);
        
        // Store animation data
        mesh.userData = {
            building: building,
            animationPhase: Math.random() * Math.PI * 2,
            originalY: mesh.position.y
        };
        
        return mesh;
    }
    
    async render(simulation, gameContext) {
        const time = Date.now() * 0.001; // Time for animations
        
        // Track current entities
        const currentEntityIds = new Set();
        let unitCount = 0;
        let buildingCount = 0;

        // Create terrain only once
        if (!this.terrainMesh && simulation?.terrain) {
            this.updateTerrain(simulation.terrain);
        }
        
        // Handle units with proper caching
        if (simulation?.entityManager?.units) {
            for (const unit of simulation.entityManager.units) {
                const entityId = `unit_${unit.id}`;
                currentEntityIds.add(entityId);
                
                let mesh = this.entityMeshes.get(entityId);
                if (!mesh) {
                    // Create new mesh only if it doesn't exist
                    mesh = await this.createUnitMesh(unit);
                    this.entityMeshes.set(entityId, mesh);
                    this.entityGroup.add(mesh);
                } else {
                    // Update existing mesh position
                    this.setMeshPosition(mesh, unit);
                }
                
                this.animateUnit(mesh, unit, time);
                unitCount++;
            }
        }
        
        // Handle buildings with proper caching
        if (simulation?.entityManager?.buildings) {
            for (const building of simulation.entityManager.buildings) {
                const entityId = `building_${building.id}`;
                currentEntityIds.add(entityId);
                
                let mesh = this.entityMeshes.get(entityId);
                if (!mesh) {
                    // Create new mesh only if it doesn't exist
                    mesh = this.createBuildingMesh(building);
                    this.entityMeshes.set(entityId, mesh);
                    this.entityGroup.add(mesh);
                } else {
                    // Update existing mesh position
                    this.setMeshPosition(mesh, building);
                }
                
                this.animateBuilding(mesh, building, time);
                buildingCount++;
            }
        }
        
        // Remove meshes for entities that no longer exist
        for (const entityId of this.lastEntityIds) {
            if (!currentEntityIds.has(entityId)) {
                const mesh = this.entityMeshes.get(entityId);
                if (mesh) {
                    this.entityGroup.remove(mesh);
                    this.entityMeshes.delete(entityId);
                    
                    // Dispose of geometry and materials to prevent memory leaks
                    mesh.traverse((child) => {
                        if (child.geometry) child.geometry.dispose();
                        if (child.material) {
                            if (Array.isArray(child.material)) {
                                child.material.forEach(mat => mat.dispose());
                            } else {
                                child.material.dispose();
                            }
                        }
                    });
                }
            }
        }
        
        // Update tracking set for next frame
        this.lastEntityIds = currentEntityIds;
        
        // Only log occasionally to avoid spam
        if (Math.random() < 0.01) {
            console.log(`Scene contains: ${unitCount} units, ${buildingCount} buildings (${this.entityMeshes.size} cached meshes)`);
        }
        
        this.renderer.render(this.scene, this.camera);
    }
    
    animateUnit(mesh, unit, time) {
        if (!mesh || !mesh.userData) return;
        
        const animationPhase = mesh.userData.animationPhase + time;
        
        // Air units hover up and down
        if (unit.type.domain === 'air') {
            mesh.position.y += Math.sin(animationPhase * 2) * 3;
            mesh.rotation.z = Math.sin(animationPhase) * 0.05; // Slight banking
        }
        
        // Moving units bounce slightly
        if (unit.dx !== 0 || unit.dy !== 0) {
            mesh.position.y += Math.abs(Math.sin(animationPhase * 8)) * 2;
        }
        
        // Attacking units have more dramatic animation
        if (unit.state === 'attacking') {
            mesh.rotation.y += 0.05;
            mesh.scale.setScalar(1 + Math.sin(animationPhase * 10) * 0.1);
        }
        
        // Commanders pulse with energy
        if (unit.type.name === 'Commander') {
            const pulse = 1 + Math.sin(animationPhase * 3) * 0.05;
            mesh.scale.setScalar(pulse);
            
            // Antenna rotation for commanders
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'CylinderGeometry' && child.position.y > 0) {
                    child.rotation.y = animationPhase * 0.5;
                }
            });
        }
        
        // Tank barrels track targets
        if (unit.type.name === 'Tank' && unit.target) {
            const targetAngle = Math.atan2(unit.target.y - unit.y, unit.target.x - unit.x);
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'CylinderGeometry' && child.position.x > 0) {
                    child.rotation.z = Math.PI / 2;
                    child.rotation.y = targetAngle;
                }
            });
        }
    }
    
    animateBuilding(mesh, building, time) {
        if (!mesh || !mesh.userData) return;
        
        const animationPhase = mesh.userData.animationPhase + time;
        
        // Resource buildings pulse
        if (building.type.resourceGeneration) {
            const pulse = 1 + Math.sin(animationPhase * 2) * 0.03;
            mesh.scale.setScalar(pulse);
        }
        
        // Factories have working parts
        if (building.type.name === 'Land Factory') {
            // Smokestack smoke effect (slight movement)
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'CylinderGeometry' && child.position.y > 0) {
                    child.position.y += Math.sin(animationPhase * 4) * 0.5;
                }
            });
        }
        
        // Air factories have rotating radar
        if (building.type.name === 'Air Factory') {
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'CylinderGeometry' && child.position.y > building.type.size) {
                    child.rotation.y = animationPhase * 2;
                }
            });
        }
        
        // Naval factories have swinging crane arms
        if (building.type.name === 'Naval Factory') {
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'BoxGeometry' && child.position.x > 0 && child.position.y > building.type.size * 2) {
                    child.rotation.z = Math.sin(animationPhase * 0.5) * 0.3;
                }
            });
        }
        
        // Mass extractors have spinning drills
        if (building.type.name === 'Mass Extractor') {
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'ConeGeometry') {
                    child.rotation.y = animationPhase * 5;
                    child.position.y = -building.type.size * 1.1 + Math.sin(animationPhase * 3) * 1;
                }
            });
        }
        
        // Energy plants have cooling tower steam effects
        if (building.type.name === 'Energy Plant') {
            const energyPulse = 1 + Math.sin(animationPhase * 4) * 0.02;
            mesh.scale.setScalar(energyPulse);
            
            // Cooling towers emit "steam" (slight scale animation)
            mesh.traverse((child) => {
                if (child.geometry && child.geometry.type === 'CylinderGeometry' && child.position.y > building.type.size) {
                    const steamEffect = 1 + Math.sin(animationPhase * 6 + child.position.x) * 0.05;
                    child.scale.y = steamEffect;
                }
            });
        }
    }
    
    resize(width, height) {
        // Update orthographic camera aspect ratio
        const aspect = width / height;
        const frustumSize = WORLD_SIZE * 1.5;
        this.camera.left = -frustumSize * aspect / 2;
        this.camera.right = frustumSize * aspect / 2;
        this.camera.top = frustumSize / 2;
        this.camera.bottom = -frustumSize / 2;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(width, height);
    }
}

export async function initThreeRenderer(canvas) {
    const renderer = new ThreeRenderer(canvas);
    await renderer.manifestPromise; // Ensure manifest is loaded before renderer is considered fully initialized
    return renderer;
}