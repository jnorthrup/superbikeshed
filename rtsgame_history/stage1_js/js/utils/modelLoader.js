import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';

class ModelLoader {
    constructor() {
        this.cache = new Map();
        this.loadingPromises = new Map();
        this.manifest = null;
        
        // Initialize loaders
        this.gltfLoader = new GLTFLoader();
        
        // Setup DRACO loader for compression
        const dracoLoader = new DRACOLoader();
        dracoLoader.setDecoderPath('/draco/');
        this.gltfLoader.setDRACOLoader(dracoLoader);
    }
    
    async loadManifest() {
        if (this.manifest) return this.manifest;
        
        const response = await fetch('/assets/model-manifest.json');
        this.manifest = await response.json();
        return this.manifest;
    }
    
    async loadModel(modelName) {
        // Check cache first
        if (this.cache.has(modelName)) {
            return this.cache.get(modelName);
        }
        
        // Check if already loading
        if (this.loadingPromises.has(modelName)) {
            return this.loadingPromises.get(modelName);
        }
        
        // Load manifest if needed
        if (!this.manifest) {
            await this.loadManifest();
        }
        
        // Get model info from manifest
        const modelInfo = this.manifest[modelName];
        if (!modelInfo) {
            throw new Error(`Model ${modelName} not found in manifest`);
        }
        
        // Create loading promise
        const loadPromise = new Promise((resolve, reject) => {
            this.gltfLoader.load(
                modelInfo.path,
                (gltf) => {
                    // Cache the loaded model
                    this.cache.set(modelName, gltf);
                    this.loadingPromises.delete(modelName);
                    resolve(gltf);
                },
                undefined,
                (error) => {
                    this.loadingPromises.delete(modelName);
                    reject(error);
                }
            );
        });
        
        this.loadingPromises.set(modelName, loadPromise);
        return loadPromise;
    }
    
    async preloadModels(modelNames) {
        const promises = modelNames.map(name => this.loadModel(name));
        return Promise.all(promises);
    }
    
    clearCache() {
        this.cache.clear();
        this.loadingPromises.clear();
    }
}

// Export singleton instance
export const modelLoader = new ModelLoader(); 