"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BatchProcessor = exports.ModelManager = exports.Camera = exports.Renderer = void 0;
// js/core/rendering/index.js
var renderer_js_1 = require("./renderer.js");
Object.defineProperty(exports, "Renderer", { enumerable: true, get: function () { return renderer_js_1.Renderer; } });
var camera_js_1 = require("./camera.js");
Object.defineProperty(exports, "Camera", { enumerable: true, get: function () { return camera_js_1.Camera; } });
var modelManager_js_1 = require("./modelManager.js");
Object.defineProperty(exports, "ModelManager", { enumerable: true, get: function () { return modelManager_js_1.modelManager; } });
var batchProcessor_js_1 = require("./batchProcessor.js");
Object.defineProperty(exports, "BatchProcessor", { enumerable: true, get: function () { return batchProcessor_js_1.BatchProcessor; } });
