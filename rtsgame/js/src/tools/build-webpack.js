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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const webpack_1 = __importDefault(require("webpack"));
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const url_1 = require("url");
const __filename = (0, url_1.fileURLToPath)(import.meta.url);
const __dirname = path_1.default.dirname(__filename);
// Import webpack config
const webpackConfig = await Promise.resolve().then(() => __importStar(require('./webpack.config.js'))).then(module => module.default);
console.log('🔨 Starting Webpack build...');
console.log('Entry points:', Object.keys(webpackConfig.entry));
const startTime = Date.now();
const compiler = (0, webpack_1.default)(webpackConfig);
compiler.run((err, stats) => {
    var _a, _b;
    const buildTime = Date.now() - startTime;
    if (err || stats.hasErrors()) {
        console.error('❌ Webpack build failed!');
        if (err) {
            console.error('Compilation error:', err);
        }
        if (stats && stats.hasErrors()) {
            console.error('Stats errors:', stats.toJson().errors);
        }
        process.exit(1);
    }
    const info = stats.toJson();
    console.log('✅ Webpack build completed successfully!');
    console.log(`⏱️  Build time: ${buildTime}ms`);
    // Get bundle information
    const assets = info.assets || [];
    const mainAsset = assets.find(asset => asset.name === 'game-bundle.js');
    if (mainAsset) {
        console.log(`📦 Bundle size: ${(mainAsset.size / 1024).toFixed(2)} KB`);
    }
    // Save build stats
    const webpackStats = {
        bundler: 'webpack',
        buildTime,
        assets: assets.map(asset => ({
            name: asset.name,
            size: asset.size,
            sizeKB: (asset.size / 1024).toFixed(2)
        })),
        totalSize: assets.reduce((sum, asset) => sum + asset.size, 0),
        warnings: ((_a = info.warnings) === null || _a === void 0 ? void 0 : _a.length) || 0,
        errors: ((_b = info.errors) === null || _b === void 0 ? void 0 : _b.length) || 0
    };
    fs_1.default.writeFileSync(path_1.default.join(__dirname, 'dist', 'webpack-stats.json'), JSON.stringify(webpackStats, null, 2));
    console.log('📊 Build stats saved to dist/webpack-stats.json');
    // Show warnings if any
    if (info.warnings && info.warnings.length > 0) {
        console.warn(`⚠️  ${info.warnings.length} warning(s):`);
        info.warnings.forEach(warning => console.warn(warning));
    }
    compiler.close((closeErr) => {
        if (closeErr) {
            console.error('Error closing compiler:', closeErr);
        }
    });
});
