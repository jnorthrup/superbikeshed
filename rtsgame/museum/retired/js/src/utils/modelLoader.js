"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.modelLoader = void 0;
const GLTFLoader_js_1 = require("three/examples/jsm/loaders/GLTFLoader.js");
const DRACOLoader_js_1 = require("three/examples/jsm/loaders/DRACOLoader.js");
class ModelLoader {
    constructor() {
        this.cache = new Map();
        this.loadingPromises = new Map();
        this.manifest = null;
        // Initialize loaders
        this.gltfLoader = new GLTFLoader_js_1.GLTFLoader();
        // Setup DRACO loader for compression
        const dracoLoader = new DRACOLoader_js_1.DRACOLoader();
        dracoLoader.setDecoderPath('/draco/');
        this.gltfLoader.setDRACOLoader(dracoLoader);
    }
    loadManifest() {
        return __awaiter(this, void 0, void 0, function* () {
            if (this.manifest)
                return this.manifest;
            const response = yield fetch('/assets/model-manifest.json');
            this.manifest = yield response.json();
            return this.manifest;
        });
    }
    loadModel(modelName) {
        return __awaiter(this, void 0, void 0, function* () {
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
                yield this.loadManifest();
            }
            // Get model info from manifest
            const modelInfo = this.manifest[modelName];
            if (!modelInfo) {
                throw new Error(`Model ${modelName} not found in manifest`);
            }
            // Create loading promise
            const loadPromise = new Promise((resolve, reject) => {
                this.gltfLoader.load(modelInfo.path, (gltf) => {
                    // Cache the loaded model
                    this.cache.set(modelName, gltf);
                    this.loadingPromises.delete(modelName);
                    resolve(gltf);
                }, undefined, (error) => {
                    this.loadingPromises.delete(modelName);
                    reject(error);
                });
            });
            this.loadingPromises.set(modelName, loadPromise);
            return loadPromise;
        });
    }
    preloadModels(modelNames) {
        return __awaiter(this, void 0, void 0, function* () {
            const promises = modelNames.map(name => this.loadModel(name));
            return Promise.all(promises);
        });
    }
    clearCache() {
        this.cache.clear();
        this.loadingPromises.clear();
    }
}
// Export singleton instance
exports.modelLoader = new ModelLoader();
