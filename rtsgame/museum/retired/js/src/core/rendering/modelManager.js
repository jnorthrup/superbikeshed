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
exports.modelManager = void 0;
const three_1 = require("three");
const modelLoader_js_1 = require("../../utils/modelLoader.js");
class ModelManager {
    constructor() {
        this.modelGroups = new Map();
        this.instancedMeshes = new Map();
        this.instanceMatrices = new Map();
        this.instanceCounts = new Map();
    }
    loadModel(modelName) {
        return __awaiter(this, void 0, void 0, function* () {
            const gltf = yield modelLoader_js_1.modelLoader.loadModel(modelName);
            const group = new three_1.Group();
            // Clone the model
            gltf.scene.traverse((child) => {
                if (child.isMesh) {
                    const clone = child.clone();
                    group.add(clone);
                }
            });
            this.modelGroups.set(modelName, group);
            return group;
        });
    }
    createInstance(modelName, maxInstances = 1000) {
        if (this.instancedMeshes.has(modelName)) {
            return;
        }
        const group = this.modelGroups.get(modelName);
        if (!group) {
            throw new Error(`Model ${modelName} not loaded`);
        }
        // Find the first mesh in the group
        let mesh = null;
        group.traverse((child) => {
            if (child.isMesh && !mesh) {
                mesh = child;
            }
        });
        if (!mesh) {
            throw new Error(`No mesh found in model ${modelName}`);
        }
        // Create instanced mesh
        const instancedMesh = new three_1.InstancedMesh(mesh.geometry, mesh.material, maxInstances);
        // Initialize matrices
        const matrices = new Float32Array(maxInstances * 16);
        const matrix = new three_1.Matrix4();
        this.instancedMeshes.set(modelName, instancedMesh);
        this.instanceMatrices.set(modelName, matrices);
        this.instanceCounts.set(modelName, 0);
        return instancedMesh;
    }
    addInstance(modelName, position, rotation, scale) {
        const count = this.instanceCounts.get(modelName);
        const matrices = this.instanceMatrices.get(modelName);
        const instancedMesh = this.instancedMeshes.get(modelName);
        if (!instancedMesh || count >= instancedMesh.count) {
            throw new Error(`Maximum instance count reached for ${modelName}`);
        }
        // Create transformation matrix
        const matrix = new three_1.Matrix4();
        matrix.compose(new three_1.Vector3(position.x, position.y, position.z), rotation, new three_1.Vector3(scale.x, scale.y, scale.z));
        // Store matrix
        matrix.toArray(matrices, count * 16);
        // Update instance count
        this.instanceCounts.set(modelName, count + 1);
        // Update instanced mesh
        instancedMesh.instanceMatrix.needsUpdate = true;
        return count;
    }
    removeInstance(modelName, index) {
        const count = this.instanceCounts.get(modelName);
        const matrices = this.instanceMatrices.get(modelName);
        const instancedMesh = this.instancedMeshes.get(modelName);
        if (!instancedMesh || index >= count) {
            return false;
        }
        // Move last instance to this position
        if (index < count - 1) {
            matrices.copyWithin(index * 16, (count - 1) * 16, count * 16);
        }
        // Update instance count
        this.instanceCounts.set(modelName, count - 1);
        // Update instanced mesh
        instancedMesh.instanceMatrix.needsUpdate = true;
        return true;
    }
    updateInstance(modelName, index, position, rotation, scale) {
        const count = this.instanceCounts.get(modelName);
        const matrices = this.instanceMatrices.get(modelName);
        const instancedMesh = this.instancedMeshes.get(modelName);
        if (!instancedMesh || index >= count) {
            return false;
        }
        // Create transformation matrix
        const matrix = new three_1.Matrix4();
        matrix.compose(new three_1.Vector3(position.x, position.y, position.z), rotation, new three_1.Vector3(scale.x, scale.y, scale.z));
        // Update matrix
        matrix.toArray(matrices, index * 16);
        // Update instanced mesh
        instancedMesh.instanceMatrix.needsUpdate = true;
        return true;
    }
    getInstanceCount(modelName) {
        return this.instanceCounts.get(modelName) || 0;
    }
    clearInstances(modelName) {
        this.instanceCounts.set(modelName, 0);
        const instancedMesh = this.instancedMeshes.get(modelName);
        if (instancedMesh) {
            instancedMesh.instanceMatrix.needsUpdate = true;
        }
    }
}
// Export singleton instance
exports.modelManager = new ModelManager();
